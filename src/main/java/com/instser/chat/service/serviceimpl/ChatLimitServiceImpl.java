package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.entity.UserDailyUsage;
import com.instser.chat.dto.PackageCacheDTO;
import com.instser.chat.service.*;
import com.instser.chat.service.cache.HybridCacheManager;
import com.instser.chat.service.cache.CachedUsageService;
import com.instser.chat.vo.ChatLimitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.Map;

import com.instser.chat.common.util.LogUtils;
/**
 * 聊天限制验证服务实现类
 *
 * @author instser
 * @since 2025-06-18

 */
@Slf4j
@Service
public class ChatLimitServiceImpl implements ChatLimitService {

    /**
     * 业务时区 - 统一使用上海时区
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PackageService packageService;

    @Autowired
    private UserDailyUsageService userDailyUsageService;

    @Autowired
    private HybridCacheManager hybridCacheManager;

    @Autowired
    private CachedUsageService cachedUsageService;

    private static final String PACKAGE_CONFIG_CACHE_PREFIX = "package:config:";
    private static final String MODEL_PERMISSION_CACHE_PREFIX = "model:permission:";
    private static final String DAILY_USAGE_CACHE_PREFIX = "daily:usage:";
    
    private static final Duration PACKAGE_CONFIG_TTL = Duration.ofHours(1);
    private static final Duration MODEL_PERMISSION_TTL = Duration.ofMinutes(30);

    /**
     * 获取业务时间（上海时区）
     */
    private LocalDateTime getBusinessDateTime() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    @Override
    public ChatLimitVO validateChatPermission(Long userId) {
        try {
            // 使用预加载上下文，减少重复查询
            UserChatContext context = preloadUserContext(userId);
            if (context == null) {
                return ChatLimitVO.failed("用户账户不存在或未购买套餐");
            }

            UserAccount userAccount = context.getUserAccount();
            PackageCacheDTO packageInfo = context.getPackageInfo();

            // 检查套餐是否过期（使用业务时区）
            LocalDateTime currentTime = getBusinessDateTime();
            if (context.isPackageExpired(currentTime)) {
                LogUtils.logUserWarning(log, userId, "聊天权限验证", "套餐已过期，套餐ID：{}，套餐名称：{}，过期时间：{}，当前时间：{}",
                    userAccount.getPackageId(), packageInfo.getPackageName(),
                    userAccount.getPackageExpireTime(), currentTime);
                return ChatLimitVO.failed("PACKAGE_EXPIRED");
            }

            // 根据限制类型进行验证
            ChatLimitVO result = new ChatLimitVO();
            result.setCanChat(true);
            result.setPackageName(packageInfo.getPackageName());
            result.setLimitType(packageInfo.getLimitType());

            if (userAccount.getPackageExpireTime() != null) {
                result.setPackageExpireTime(
                    userAccount.getPackageExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
            }

            if ("daily".equals(packageInfo.getLimitType())) {
                // 每日限制模式
                return validateDailyLimit(userId, userAccount.getPackageId(), packageInfo, result);
            } else if ("total".equals(packageInfo.getLimitType())) {
                // 总次数限制模式
                return validateTotalLimit(userAccount, packageInfo, result);
            } else {
                // 无限制模式
                result.setLimitType("unlimited");
                return result;
            }

        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "聊天权限验证", "验证聊天权限时发生异常", e);
            return ChatLimitVO.failed("系统异常，请稍后重试");
        }
    }

    @Override
    public boolean recordChatUsage(Long userId) {
        try {
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                LogUtils.logUserWarning(log, userId, "记录聊天使用", "没有有效套餐，无法记录使用");
                return false;
            }

            Package packageInfo = packageService.getPackageById(userAccount.getPackageId());
            if (packageInfo == null) {
                LogUtils.logUserWarning(log, userId, "记录聊天使用", "套餐{}不存在", userAccount.getPackageId());
                return false;
            }

            if ("daily".equals(packageInfo.getLimitType())) {
                // 每日限制模式，使用模型ID 1
                return userDailyUsageService.recordModelChatUsage(userId, userAccount.getPackageId(), "1");
            } else if ("total".equals(packageInfo.getLimitType())) {
                // 总次数限制模式
                return recordTotalUsage(userAccount);
            } else {
                // 无限制模式
                return true;
            }

        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "记录聊天使用", "记录聊天使用时发生异常", e);
            return false;
        }
    }

    @Override
    public ChatLimitVO getChatLimitInfo(Long userId) {
        return validateChatPermission(userId);
    }

    /**
     * 验证每日限制
     */
    private ChatLimitVO validateDailyLimit(Long userId, Long packageId, PackageCacheDTO packageInfo, ChatLimitVO result) {
        // 注意：现在使用模型维度的限制检查，这里为了向后兼容使用模型ID 1（STANDARD模型）
        if (!userDailyUsageService.canChatWithModel(userId, packageId, "1")) {
            result.setCanChat(false);
            result.setLimitReason("MODEL_LIMIT_EXCEEDED");
        }

        int remaining = userDailyUsageService.getRemainingModelChats(userId, packageId, "1");
        UserDailyUsage todayUsage = userDailyUsageService.getTodayModelUsage(userId, packageId, "1");

        // 从缓存DTO中获取模型ID 1的限制
        int dailyLimit = packageInfo.getModelDailyLimit("1");

        result.setDailyLimit(dailyLimit);
        result.setRemainingToday(remaining);
        result.setUsedToday(todayUsage != null ? todayUsage.getChatCountUsed() : 0);

        return result;
    }

    /**
     * 验证总次数限制
     */
    private ChatLimitVO validateTotalLimit(UserAccount userAccount, PackageCacheDTO packageInfo, ChatLimitVO result) {
        // 如果是每日限制模式，总次数不限制
        if ("daily".equals(packageInfo.getLimitType())) {
            result.setTotalRemaining(-1); // -1表示无限制
            return result;
        }

        int remaining = userAccount.getChatCountRemaining() != null ? userAccount.getChatCountRemaining() : 0;

        if (remaining <= 0) {
            result.setCanChat(false);
            result.setLimitReason("PACKAGE_EXPIRED");
        }

        result.setTotalRemaining(remaining);
        return result;
    }

    /**
     * 记录总次数使用（使用原子性操作）
     */
    private boolean recordTotalUsage(UserAccount userAccount) {
        // 获取套餐信息检查限制类型
        Package packageInfo = packageService.getPackageById(userAccount.getPackageId());
        if (packageInfo != null && "daily".equals(packageInfo.getLimitType())) {
            // 每日限制模式：不扣减总次数，直接返回成功
            LogUtils.logUserDebug(log, userAccount.getUserId(), "记录总次数使用", "使用每日限制套餐，不扣减总次数");
            return true;
        }

        // 其他模式：使用UserAccountService的原子性操作来减少剩余次数
        boolean success = userAccountService.updateUsage(userAccount.getUserId(), 1);

        if (success) {
            LogUtils.logUserDebug(log, userAccount.getUserId(), "记录总次数使用", "总次数使用记录成功");
        } else {
            LogUtils.logUserWarning(log, userAccount.getUserId(), "记录总次数使用", "总次数使用记录失败，可能次数不足或套餐已过期");
        }

        return success;
    }

    @Override
    public ChatLimitVO validateModelChatPermission(Long userId, String modelId) {
        try {
            // 1. 一次性获取用户账户信息（避免重复调用）
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null) {
                return ChatLimitVO.failed("用户账户不存在");
            }

            // 检查是否有有效套餐
            if (userAccount.getPackageId() == null) {
                return ChatLimitVO.failed("用户未购买套餐");
            }

            // 2. 一次性获取套餐配置信息（避免重复调用）
            PackageCacheDTO packageInfo = getCachedPackageConfig(userAccount.getPackageId());
            if (packageInfo == null) {
                return ChatLimitVO.failed("套餐不存在");
            }

            // 3. 检查套餐是否过期
            if (userAccount.getPackageExpireTime() != null &&
                userAccount.getPackageExpireTime().isBefore(getBusinessDateTime())) {
                LogUtils.logUserWarning(log, userId, "模型权限验证", "套餐已过期，套餐ID：{}，套餐名称：{}，过期时间：{}，当前时间：{}",
                    userAccount.getPackageId(), packageInfo.getPackageName(),
                    userAccount.getPackageExpireTime(), getBusinessDateTime());
                return ChatLimitVO.failed("PACKAGE_EXPIRED");
            }

            // 4. 使用缓存次数服务进行模型权限检查
            boolean canUse = cachedUsageService.canUseModel(userId, modelId, userAccount.getPackageId());
            if (!canUse) {
                return ChatLimitVO.failed("MODEL_LIMIT_EXCEEDED");
            }

            // 5. 构建成功响应
            ChatLimitVO result = new ChatLimitVO();
            result.setCanChat(true);
            result.setPackageName(packageInfo.getPackageName());
            result.setLimitType(packageInfo.getLimitType());
            result.setModelName(modelId);

            if (userAccount.getPackageExpireTime() != null) {
                result.setPackageExpireTime(
                    userAccount.getPackageExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
            }

            // 获取剩余次数用于显示（可选，如果需要显示给用户）
            try {
                int remaining = userDailyUsageService.getRemainingModelChats(userId, userAccount.getPackageId(), modelId);
                result.setModelRemainingToday(remaining);
                
                // 获取今日模型使用情况
                UserDailyUsage todayModelUsage = userDailyUsageService.getTodayModelUsage(userId, userAccount.getPackageId(), modelId);
                result.setModelUsedToday(todayModelUsage != null ? todayModelUsage.getChatCountUsed() : 0);
            } catch (Exception e) {
                // 获取显示信息失败不影响权限验证结果
                LogUtils.logUserWarning(log, userId, "获取使用统计", "获取模型{}使用统计失败", modelId, e);
            }

            return result;

        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "模型权限验证", "验证模型ID{}聊天权限异常", e, modelId);
            return ChatLimitVO.failed("权限验证失败，请稍后重试");
        }
    }

    @Override
    public boolean recordModelChatUsage(Long userId, String modelId) {
        try {
            // 1. 获取用户账户信息
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                LogUtils.logUserError(log, userId, "记录模型使用", "账户或套餐信息不存在，无法记录模型使用", null);
                return false;
            }

            // 2. 使用新的缓存次数服务记录使用
            boolean success = cachedUsageService.recordModelUsage(userId, modelId, userAccount.getPackageId());

            if (success) {
                LogUtils.logUserOperation(log, userId, "记录模型使用", "模型ID{}使用记录成功（缓存次数）", modelId);
            } else {
                LogUtils.logUserWarning(log, userId, "记录模型使用", "模型ID{}使用记录失败，可能已达到限制", modelId);
            }

            return success;

        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "记录模型使用", "记录模型ID{}使用异常", e, modelId);
            return false;
        }
    }

    @Override
    public boolean recordTokenUsage(Long userId, Integer tokenCount) {
        if (userId == null || tokenCount == null || tokenCount <= 0) {
            LogUtils.logUserError(log, userId, "记录Token使用", "记录Token使用参数无效: tokenCount={}", null, tokenCount);
            return false;
        }

        try {
            // 只记录Token使用统计，不扣减余额，不检查限制
            boolean success = userAccountService.recordTokenUsageOnly(userId, tokenCount);

            if (success) {
                LogUtils.logUserDebug(log, userId, "记录Token使用", "Token使用记录成功，使用了{}个Token（仅统计，不扣减余额）", tokenCount);
            } else {
                LogUtils.logUserWarning(log, userId, "记录Token使用", "Token使用记录失败");
            }

            return success;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "记录Token使用", "记录Token使用异常", e);
            return false;
        }
    }
    
    /**
     * 缓存化套餐配置获取 - 使用精简DTO
     */
    private PackageCacheDTO getCachedPackageConfig(Long packageId) {
        String cacheKey = PACKAGE_CONFIG_CACHE_PREFIX + packageId;
        return hybridCacheManager.get(cacheKey, () -> {
            Package packageConfig = packageService.getPackageById(packageId);
            if (packageConfig != null) {
                log.debug("缓存未命中，从数据库加载套餐配置: {}", packageId);
                return PackageCacheDTO.fromPackage(packageConfig);
            }
            return null;
        }, PACKAGE_CONFIG_TTL);
    }

    /**
     * 缓存化模型权限验证
     */
    private String getCachedModelPermission(Long userId, String modelId, Long packageId) {
        String cacheKey = MODEL_PERMISSION_CACHE_PREFIX + userId + ":" + modelId;
        return hybridCacheManager.get(cacheKey, () -> {
            String result = validateModelPermissionInternal(userId, modelId, packageId);
            LogUtils.logUserDebug(log, userId, "缓存未命中", "从数据库验证模型{}权限", modelId);
            return result;
        }, MODEL_PERMISSION_TTL);
    }

    /**
     * 内部模型权限验证逻辑
     */
    private String validateModelPermissionInternal(Long userId, String modelId, Long packageId) {
        if (!userDailyUsageService.canChatWithModel(userId, packageId, modelId)) {
            return "MODEL_LIMIT_EXCEEDED";
        }
        return "PERMISSION_GRANTED"; // 使用明确的成功标识，避免缓存null值
    }
    
    /**
     * 清除模型权限缓存
     */
    private void evictModelPermissionCache(Long userId, String modelId) {
        String cacheKey = MODEL_PERMISSION_CACHE_PREFIX + userId + ":" + modelId;
        hybridCacheManager.evict(cacheKey);
    }
    
    /**
     * 批量预热用户相关缓存 - 减少重复查询
     * 
     * @param userId 用户ID
     * @return 用户上下文信息
     */
    private UserChatContext preloadUserContext(Long userId) {
        try {
            // 一次性加载用户账户信息
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                return null;
            }

            // 一次性加载套餐配置信息
            PackageCacheDTO packageInfo = getCachedPackageConfig(userAccount.getPackageId());
            if (packageInfo == null) {
                return null;
            }

            return new UserChatContext(userAccount, packageInfo);
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "预加载上下文", "预加载用户聊天上下文异常", e);
            return null;
        }
    }

    /**
     * 用户聊天上下文
     */
    private static class UserChatContext {
        private final UserAccount userAccount;
        private final PackageCacheDTO packageInfo;

        public UserChatContext(UserAccount userAccount, PackageCacheDTO packageInfo) {
            this.userAccount = userAccount;
            this.packageInfo = packageInfo;
        }

        public UserAccount getUserAccount() {
            return userAccount;
        }

        public PackageCacheDTO getPackageInfo() {
            return packageInfo;
        }

        public boolean isPackageExpired(LocalDateTime currentTime) {
            return userAccount.getPackageExpireTime() != null &&
                   userAccount.getPackageExpireTime().isBefore(currentTime);
        }
    }


}
