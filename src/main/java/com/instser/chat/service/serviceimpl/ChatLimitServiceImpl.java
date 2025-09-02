package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.entity.UserDailyUsage;
import com.instser.chat.service.*;
import com.instser.chat.vo.ChatLimitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    /**
     * 获取业务时间（上海时区）
     */
    private LocalDateTime getBusinessDateTime() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    @Override
    public ChatLimitVO validateChatPermission(Long userId) {
        try {
            // 获取用户账户信息
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null) {
                return ChatLimitVO.failed("用户账户不存在");
            }

            // 检查是否有有效套餐
            if (userAccount.getPackageId() == null) {
                return ChatLimitVO.failed("用户未购买套餐");
            }

            // 获取套餐信息
            Package packageInfo = packageService.getPackageById(userAccount.getPackageId());
            if (packageInfo == null) {
                return ChatLimitVO.failed("套餐不存在");
            }

            // 注意：即使套餐被下架，已购买的用户在有效期内仍可继续使用
            // 只有在套餐完全不存在时才拒绝服务
            // if (packageInfo.getIsEnabled() != 1) {
            //     return ChatLimitVO.failed("套餐已下架");
            // }

            // 检查套餐是否过期（使用业务时区）
            // 注意：过期时间为null表示永久有效
            if (userAccount.getPackageExpireTime() != null &&
                userAccount.getPackageExpireTime().isBefore(getBusinessDateTime())) {
                LogUtils.logUserWarning(log, userId, "聊天权限验证", "套餐已过期，套餐ID：{}，套餐名称：{}，过期时间：{}，当前时间：{}",
                    userAccount.getPackageId(), packageInfo.getPackageName(),
                    userAccount.getPackageExpireTime(), getBusinessDateTime());
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
    private ChatLimitVO validateDailyLimit(Long userId, Long packageId, Package packageInfo, ChatLimitVO result) {
        // 注意：现在使用模型维度的限制检查，这里为了向后兼容使用模型ID 1（STANDARD模型）
        if (!userDailyUsageService.canChatWithModel(userId, packageId, "1")) {
            result.setCanChat(false);
            result.setLimitReason("MODEL_LIMIT_EXCEEDED");
        }

        int remaining = userDailyUsageService.getRemainingModelChats(userId, packageId, "1");
        UserDailyUsage todayUsage = userDailyUsageService.getTodayModelUsage(userId, packageId, "1");

        // 从模型权限配置中获取模型ID 1的限制
        Map<String, Package.ModelPermission> modelPermissions = packageInfo.getModelPermissionsMap();
        Package.ModelPermission standardPermission = modelPermissions.get("1");
        int dailyLimit = standardPermission != null ? standardPermission.getDailyLimit() : 0;

        result.setDailyLimit(dailyLimit);
        result.setRemainingToday(remaining);
        result.setUsedToday(todayUsage != null ? todayUsage.getChatCountUsed() : 0);

        return result;
    }

    /**
     * 验证总次数限制
     */
    private ChatLimitVO validateTotalLimit(UserAccount userAccount, Package packageInfo, ChatLimitVO result) {
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
            // 1. 基础权限检查
            ChatLimitVO baseResult = validateChatPermission(userId);
            if (!baseResult.isCanChat()) {
                return baseResult; // 基础检查失败，直接返回
            }

            // 2. 获取用户账户信息
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                return ChatLimitVO.failed("用户账户或套餐信息不存在");
            }

            // 3. 模型权限检查
            if (!userDailyUsageService.canChatWithModel(userId, userAccount.getPackageId(), modelId)) {
                return ChatLimitVO.failed("MODEL_LIMIT_EXCEEDED");
            }

            // 4. 构建成功响应，包含模型使用信息
            ChatLimitVO result = baseResult; // 复用基础检查结果
            result.setModelName(modelId);

            int remaining = userDailyUsageService.getRemainingModelChats(userId, userAccount.getPackageId(), modelId);
            result.setModelRemainingToday(remaining);

            // 获取今日模型使用情况
            UserDailyUsage todayModelUsage = userDailyUsageService.getTodayModelUsage(userId, userAccount.getPackageId(), modelId);
            result.setModelUsedToday(todayModelUsage != null ? todayModelUsage.getChatCountUsed() : 0);

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

            // 2. 记录模型使用
            boolean success = userDailyUsageService.recordModelChatUsage(userId, userAccount.getPackageId(), modelId);

            if (success) {
                LogUtils.logUserOperation(log, userId, "记录模型使用", "模型ID{}使用记录成功", modelId);
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


}
