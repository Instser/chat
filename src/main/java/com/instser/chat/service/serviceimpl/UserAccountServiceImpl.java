package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.UserAccountDao;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.cache.HybridCacheManager;
import com.instser.chat.event.UserPackageChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;

import com.instser.chat.common.util.LogUtils;
/**
 * 用户账户服务实现类
 *
 * @author instser
 * @since 2025-06-18

 */
@Slf4j
@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountDao, UserAccount>
        implements UserAccountService {

    /**
     * 业务时区 - 统一使用上海时区
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PackageService packageService;

    @Autowired
    private HybridCacheManager hybridCacheManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final String USER_ACCOUNT_CACHE_PREFIX = "user:account:";
    private static final String PACKAGE_CONFIG_CACHE_PREFIX = "package:config:";
    private static final Duration USER_ACCOUNT_TTL = Duration.ofMinutes(15);

    /**
     * 获取业务时间（上海时区）
     */
    private LocalDateTime getBusinessDateTime() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    @Override
    public UserAccount getUserAccount(Long userId) {
        if (userId == null) {
            return null;
        }
        
        String cacheKey = USER_ACCOUNT_CACHE_PREFIX + userId;
        return hybridCacheManager.get(cacheKey, () -> {
            UserAccount account = baseMapper.selectByUserId(userId);
            if (account != null) {
                LogUtils.logUserDebug(log, userId, "缓存未命中", "从数据库加载用户账户信息");
            }
            return account;
        }, USER_ACCOUNT_TTL);
    }

    @Override
    @Transactional
    public boolean activatePackage(Long userId, Long packageId, Integer validityDays, Integer totalChatCount) {
        if (userId == null || packageId == null || validityDays == null) {
            LogUtils.logUserError(log, userId, "激活套餐", "激活套餐参数不能为空: packageId={}, validityDays={}", null, packageId, validityDays);
            return false;
        }

        try {
            // 获取套餐信息以确定限制类型
            Package packageInfo = packageService.getPackageById(packageId);
            if (packageInfo == null) {
                LogUtils.logUserError(log, userId, "激活套餐", "套餐{}不存在", null, packageId);
                return false;
            }

            // 计算过期时间：-1表示永久有效
            LocalDateTime expireTime;
            if (validityDays == -1) {
                // 永久有效：设置为null或很远的未来时间
                expireTime = null;
                LogUtils.logUserOperation(log, userId, "激活套餐", "套餐{}为永久有效套餐，过期时间设置为null", packageId);
            } else {
                expireTime = getBusinessDateTime().plusDays(validityDays);
                LogUtils.logUserDebug(log, userId, "激活套餐", "套餐{}有效期{}天，过期时间：{}", packageId, validityDays, expireTime);
            }

            Integer chatCount;

            // 根据套餐限制类型设置总次数
            if ("daily".equals(packageInfo.getLimitType())) {
                // 每日限制模式：总次数不限制
                chatCount = -1;
                LogUtils.logUserOperation(log, userId, "激活套餐", "套餐{}为每日限制模式，总次数设置为无限制", packageId);
            } else {
                // 其他模式：使用传入的总次数或从套餐配置获取
                chatCount = totalChatCount != null ? totalChatCount : packageInfo.getChatCount();
            }

            // 使用原子性操作创建或更新账户信息，同时更新会员等级
            LocalDateTime currentTime = getBusinessDateTime();
            String membershipLevel = getMembershipLevelFromPackage(packageInfo);
            int updated = baseMapper.insertOrUpdateAccount(userId, packageId, expireTime, chatCount, membershipLevel, currentTime);

            if (updated > 0) {
                // 激活成功后清除相关缓存
                evictRelatedCaches(userId, packageId);
                
                // 发布套餐变更事件
                eventPublisher.publishEvent(UserPackageChangeEvent.createActivateEvent(this, userId, packageId));
                
                LogUtils.logUserOperation(log, userId, "激活套餐", "套餐激活成功，套餐ID：{}，有效期：{}天，总次数：{}，限制类型：{}",
                        packageId, validityDays, chatCount, packageInfo.getLimitType());
                return true;
            }

            LogUtils.logUserError(log, userId, "激活套餐", "套餐激活失败", null);
            return false;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "激活套餐", "激活套餐异常: packageId={}", e, packageId);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean updateUsage(Long userId, Integer usedCount) {
        if (userId == null || usedCount == null || usedCount <= 0) {
            LogUtils.logUserError(log, userId, "更新使用次数", "更新使用次数参数无效: usedCount={}", null, usedCount);
            return false;
        }

        try {
            // 简化版乐观锁重试机制
            boolean result = performOptimisticLockUpdate(userId, usedCount);
            if (result) {
                // 更新成功后清除相关缓存
                evictUserAccountCache(userId);
                LogUtils.logUserDebug(log, userId, "更新使用次数", "使用次数更新成功，已清除缓存");
            }
            return result;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "更新使用次数", "更新使用次数异常", e);
            return false;
        }
    }

    /**
     * 简化版乐观锁更新 - 重试机制
     */
    private boolean performOptimisticLockUpdate(Long userId, Integer usedCount) {
        int maxRetries = 3;
        LocalDateTime updateTime = getBusinessDateTime();
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // 获取当前用户账户（包含版本号）
                UserAccount currentAccount = baseMapper.selectByUserId(userId);
                if (currentAccount == null) {
                    LogUtils.logUserWarning(log, userId, "乐观锁更新", "用户账户不存在");
                    return false;
                }
                
                // 使用版本控制进行原子更新
                int updated = baseMapper.decrementChatCountWithVersion(
                    userId, usedCount, currentAccount.getVersion(), updateTime);
                
                if (updated > 0) {
                    // 更新成功，增加总次数统计
                    baseMapper.incrementTotalChatCount(userId, usedCount, updateTime);
                    LogUtils.logUserDebug(log, userId, "乐观锁更新", 
                        "第{}次尝试成功，版本号：{}", attempt + 1, currentAccount.getVersion());
                    return true;
                }
                
                // 版本号冲突或其他原因导致更新失败
                if (attempt < maxRetries - 1) {
                    LogUtils.logUserDebug(log, userId, "乐观锁更新", 
                        "第{}次尝试失败，准备重试", attempt + 1);
                    // 短暂等待后重试
                    Thread.sleep(10 + attempt * 5); // 10ms, 15ms, 20ms
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogUtils.logUserError(log, userId, "乐观锁更新", "重试被中断", e);
                return false;
            } catch (Exception e) {
                LogUtils.logUserError(log, userId, "乐观锁更新", 
                    "第{}次尝试异常", e, attempt + 1);
                if (attempt == maxRetries - 1) {
                    throw e; // 最后一次重试失败时抛出异常
                }
            }
        }
        
        LogUtils.logUserWarning(log, userId, "乐观锁更新", 
            "经过{}次重试仍然失败，可能并发冲突严重", maxRetries);
        return false;
    }

    private void evictUserAccountCache(Long userId) {
        String cacheKey = USER_ACCOUNT_CACHE_PREFIX + userId;
        hybridCacheManager.evict(cacheKey);
    }

    private void evictPackageConfigCache(Long packageId) {
        if (packageId != null) {
            String cacheKey = PACKAGE_CONFIG_CACHE_PREFIX + packageId;
            hybridCacheManager.evict(cacheKey);
        }
    }

    private void evictRelatedCaches(Long userId, Long packageId) {
        evictUserAccountCache(userId);
        evictPackageConfigCache(packageId);
    }

    @Override
    public boolean hasValidPackage(Long userId) {
        UserAccount account = getUserAccount(userId);
        return account != null && account.hasValidPackage();
    }

    @Override
    public boolean isPackageExpired(Long userId) {
        UserAccount account = getUserAccount(userId);
        return account != null && account.isPackageExpired();
    }

    @Override
    public int getRemainingChatCount(Long userId) {
        UserAccount account = getUserAccount(userId);
        if (account == null || account.isPackageExpired()) {
            return 0;
        }
        return account.getChatCountRemaining() != null ? account.getChatCountRemaining() : 0;
    }

    @Override
    @Transactional
    public boolean renewPackage(Long userId, Long packageId, Integer validityDays, Integer additionalChatCount) {
        if (userId == null || packageId == null || validityDays == null) {
            LogUtils.logUserError(log, userId, "续费套餐", "续费套餐参数不能为空: packageId={}, validityDays={}", null,
                    packageId, validityDays);
            return false;
        }

        try {
            UserAccount account = getUserAccount(userId);
            if (account == null) {
                LogUtils.logUserError(log, userId, "续费套餐", "用户账户不存在，无法续费", null);
                return false;
            }

            // 计算新的过期时间
            LocalDateTime currentExpireTime = account.getPackageExpireTime();
            LocalDateTime newExpireTime;

            if (currentExpireTime == null || currentExpireTime.isBefore(getBusinessDateTime())) {
                // 如果已过期或无过期时间，从当前时间开始计算
                newExpireTime = getBusinessDateTime().plusDays(validityDays);
            } else {
                // 如果未过期，从原过期时间开始延长
                newExpireTime = currentExpireTime.plusDays(validityDays);
            }

            // 计算新的剩余次数
            Integer currentRemaining = account.getChatCountRemaining() != null ? account.getChatCountRemaining() : 0;
            Integer newRemaining = currentRemaining + (additionalChatCount != null ? additionalChatCount : 0);

            // 获取新套餐信息以确定会员等级
            Package newPackageInfo = packageService.getPackageById(packageId);
            String membershipLevel = getMembershipLevelFromPackage(newPackageInfo);

            int updated = baseMapper.updatePackageInfo(userId, packageId, newExpireTime, newRemaining, membershipLevel, getBusinessDateTime());

            if (updated > 0) {
                // 续费成功后清除相关缓存
                evictRelatedCaches(userId, packageId);
                
                // 发布套餐变更事件
                eventPublisher.publishEvent(UserPackageChangeEvent.createRenewEvent(this, userId, packageId));
                
                LogUtils.logUserOperation(log, userId, "续费套餐", "套餐续费成功，延长{}天，增加{}次聊天",
                        validityDays, additionalChatCount);
                return true;
            }

            LogUtils.logUserError(log, userId, "续费套餐", "套餐续费失败", null);
            return false;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "续费套餐", "续费套餐异常: packageId={}", e, packageId);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean upgradePackage(Long userId, Long newPackageId, Integer validityDays, Integer totalChatCount) {
        // 升级套餐本质上就是激活新套餐，覆盖原有套餐信息
        return activatePackage(userId, newPackageId, validityDays, totalChatCount);
    }

    @Override
    @Transactional
    public int cleanExpiredPackages() {
        try {
            LocalDateTime expiredTime = getBusinessDateTime().minusDays(7); // 清理7天前过期的记录
            LocalDateTime updateTime = getBusinessDateTime();
            int cleaned = baseMapper.cleanExpiredPackages(expiredTime, updateTime);
            LogUtils.logAdminOperation(log, "清理过期套餐", "清理过期套餐完成，共清理{}条记录", cleaned);
            return cleaned;
        } catch (Exception e) {
            LogUtils.logAdminOperation(log, "清理过期套餐", "清理过期套餐异常", e);
            return 0;
        }
    }

    @Override
    @Transactional
    public boolean initUserAccount(Long userId) {
        if (userId == null) {
            LogUtils.logUserError(log, null, "初始化账户", "初始化用户账户失败，用户ID不能为空", null);
            return false;
        }

        try {
            // 检查是否已存在账户
            if (baseMapper.countByUserId(userId) > 0) {
                LogUtils.logUserDebug(log, userId, "初始化账户", "用户账户已存在，无需重复初始化");
                return true;
            }

            UserAccount account = new UserAccount();
            account.setUserId(userId);
            account.setPackageId(null);
            account.setPackageExpireTime(null);
            account.setChatCountRemaining(0);
            account.setTotalChatCount(0);
            account.setTotalAmountSpent(BigDecimal.ZERO);
            account.setTokenBalance(0L);
            account.setTokenUsed(0L);
            account.setMembershipLevel("free");
            account.setMembershipExpireTime(null);
            // 移除手动设置时间，使用MyBatis-Plus自动填充
            // account.setCreateTime(getBusinessDateTime());
            // account.setUpdateTime(getBusinessDateTime());
            account.setDeleted(0);

            boolean saved = this.save(account);
            if (saved) {
                LogUtils.logUserOperation(log, userId, "初始化账户", "用户账户初始化成功");
            } else {
                LogUtils.logUserError(log, userId, "初始化账户", "用户账户初始化失败", null);
            }
            return saved;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "初始化账户", "初始化用户账户异常", e);
            return false;
        }
    }

    @Override
    public long getRemainingDays(Long userId) {
        UserAccount account = getUserAccount(userId);
        if (account == null) {
            return 0;
        }
        return account.getRemainingDays();
    }

    @Override
    public boolean canChatWithTotalLimit(Long userId) {
        UserAccount account = getUserAccount(userId);
        if (account == null || account.isPackageExpired()) {
            return false;
        }

        Integer remaining = account.getChatCountRemaining();
        return remaining != null && remaining > 0;
    }

    @Override
    @Transactional
    public boolean updateTokenUsage(Long userId, Integer tokenCount) {
        if (userId == null || tokenCount == null || tokenCount <= 0) {
            LogUtils.logUserError(log, userId, "更新Token使用量", "更新Token使用量参数无效: tokenCount={}", null, tokenCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            // 原子性扣减Token余额
            int updated = baseMapper.decrementTokenBalance(userId, tokenCount.longValue(), updateTime);

            if (updated > 0) {
                // 增加已使用Token统计
                baseMapper.incrementTokenUsed(userId, tokenCount.longValue(), updateTime);
                // Token使用后清除缓存
                evictUserAccountCache(userId);
                LogUtils.logUserDebug(log, userId, "更新Token使用量", "Token使用量更新成功，使用了{}个Token", tokenCount);
                return true;
            }

            LogUtils.logUserWarning(log, userId, "更新Token使用量", "Token使用量更新失败，可能余额不足");
            return false;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "更新Token使用量", "更新Token使用量异常: tokenCount={}", e, tokenCount);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean recordTokenUsageOnly(Long userId, Integer tokenCount) {
        if (userId == null || tokenCount == null || tokenCount <= 0) {
            LogUtils.logUserError(log, userId, "记录Token使用统计", "记录Token使用统计参数无效: tokenCount={}", null, tokenCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            // 只增加已使用Token统计，不扣减余额
            int updated = baseMapper.incrementTokenUsed(userId, tokenCount.longValue(), updateTime);

            if (updated > 0) {
                LogUtils.logUserDebug(log, userId, "记录Token使用统计", "Token使用统计记录成功，使用了{}个Token（仅统计）", tokenCount);
                return true;
            }

            LogUtils.logUserWarning(log, userId, "记录Token使用统计", "Token使用统计记录失败");
            return false;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "记录Token使用统计", "记录Token使用统计异常: tokenCount={}", e, tokenCount);
            return false;
        }
    }



    @Override
    public Long getTokenBalance(Long userId) {
        if (userId == null) {
            return 0L;
        }

        UserAccount account = getUserAccount(userId);
        if (account == null) {
            return 0L;
        }

        Long balance = account.getTokenBalance();
        return balance != null ? balance : 0L;
    }

    @Override
    @Transactional
    public boolean addTokenBalance(Long userId, Long tokenCount) {
        if (userId == null || tokenCount == null || tokenCount <= 0) {
            LogUtils.logUserError(log, userId, "增加Token余额", "增加Token余额参数无效: tokenCount={}", null, tokenCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            int updated = baseMapper.incrementTokenBalance(userId, tokenCount, updateTime);

            if (updated > 0) {
                // Token余额增加后清除缓存
                evictUserAccountCache(userId);
                LogUtils.logUserOperation(log, userId, "增加Token余额", "Token余额增加成功，增加了{}个Token", tokenCount);
                return true;
            }

            LogUtils.logUserWarning(log, userId, "增加Token余额", "Token余额增加失败");
            return false;
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "增加Token余额", "增加Token余额异常: tokenCount={}", e, tokenCount);
            return false;
        }
    }

    /**
     * 根据套餐信息获取对应的会员等级
     *
     * @param packageInfo 套餐信息
     * @return 会员等级
     */
    private String getMembershipLevelFromPackage(Package packageInfo) {
        if (packageInfo == null || packageInfo.getPackageType() == null) {
            return "free";
        }

        // 根据套餐类型映射会员等级
        switch (packageInfo.getPackageType().toLowerCase()) {
            case "basic":
                return "basic";
            case "medium":
                return "premium";
            case "high":
                return "enterprise";
            case "premium":
                return "premium";
            case "enterprise":
                return "enterprise";
            case "vip":
                return "vip";
            default:
                return "free";
        }
    }

    @Override
    public java.util.List<Long> getActiveUserIds(int limit) {
        try {
            // 由于Mapper方法可能不存在，先使用简单的查询获取有有效套餐的用户
            // 实际应该在UserAccountDao中添加selectActiveUserIds方法
            return baseMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserAccount>lambdaQuery()
                    .isNotNull(UserAccount::getPackageId)
                    .gt(UserAccount::getPackageExpireTime, getBusinessDateTime())
                    .orderByDesc(UserAccount::getUpdateTime)
                    .last("LIMIT " + limit)
            ).stream().map(UserAccount::getUserId).collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "获取活跃用户", "获取活跃用户列表异常", e);
            return java.util.Collections.emptyList();
        }
    }


}
