package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.UserAccountDao;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
        return baseMapper.selectByUserId(userId);
    }

    @Override
    @Transactional
    public boolean activatePackage(Long userId, Long packageId, Integer validityDays, Integer totalChatCount) {
        if (userId == null || packageId == null || validityDays == null) {
            log.error("激活套餐参数不能为空: userId={}, packageId={}, validityDays={}",
                    userId, packageId, validityDays);
            return false;
        }

        try {
            // 获取套餐信息以确定限制类型
            Package packageInfo = packageService.getPackageById(packageId);
            if (packageInfo == null) {
                log.error("套餐{}不存在", packageId);
                return false;
            }

            // 计算过期时间：-1表示永久有效
            LocalDateTime expireTime;
            if (validityDays == -1) {
                // 永久有效：设置为null或很远的未来时间
                expireTime = null;
                log.info("套餐{}为永久有效套餐，过期时间设置为null", packageId);
            } else {
                expireTime = getBusinessDateTime().plusDays(validityDays);
                log.debug("套餐{}有效期{}天，过期时间：{}", packageId, validityDays, expireTime);
            }

            Integer chatCount;

            // 根据套餐限制类型设置总次数
            if ("daily".equals(packageInfo.getLimitType())) {
                // 每日限制模式：总次数不限制
                chatCount = -1;
                log.info("套餐{}为每日限制模式，总次数设置为无限制", packageId);
            } else {
                // 其他模式：使用传入的总次数或从套餐配置获取
                chatCount = totalChatCount != null ? totalChatCount : packageInfo.getChatCount();
            }

            // 使用原子性操作创建或更新账户信息，同时更新会员等级
            LocalDateTime currentTime = getBusinessDateTime();
            String membershipLevel = getMembershipLevelFromPackage(packageInfo);
            int updated = baseMapper.insertOrUpdateAccount(userId, packageId, expireTime, chatCount, membershipLevel, currentTime);

            if (updated > 0) {
                log.info("用户{}套餐激活成功，套餐ID：{}，有效期：{}天，总次数：{}，限制类型：{}",
                        userId, packageId, validityDays, chatCount, packageInfo.getLimitType());
                return true;
            }

            log.error("用户{}套餐激活失败", userId);
            return false;
        } catch (Exception e) {
            log.error("激活套餐异常: userId={}, packageId={}", userId, packageId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean updateUsage(Long userId, Integer usedCount) {
        if (userId == null || usedCount == null || usedCount <= 0) {
            log.error("更新使用次数参数无效: userId={}, usedCount={}", userId, usedCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            // 原子性减少剩余次数
            int updated = baseMapper.decrementChatCount(userId, usedCount, updateTime);

            if (updated > 0) {
                // 增加总使用次数统计
                baseMapper.incrementTotalChatCount(userId, usedCount, updateTime);
                log.debug("用户{}使用次数更新成功，使用了{}次", userId, usedCount);
                return true;
            }
            
            log.warn("用户{}使用次数更新失败，可能次数不足", userId);
            return false;
        } catch (Exception e) {
            log.error("更新使用次数异常: userId={}, usedCount={}", userId, usedCount, e);
            return false;
        }
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
            log.error("续费套餐参数不能为空: userId={}, packageId={}, validityDays={}", 
                    userId, packageId, validityDays);
            return false;
        }

        try {
            UserAccount account = getUserAccount(userId);
            if (account == null) {
                log.error("用户{}账户不存在，无法续费", userId);
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
                log.info("用户{}套餐续费成功，延长{}天，增加{}次聊天", 
                        userId, validityDays, additionalChatCount);
                return true;
            }
            
            log.error("用户{}套餐续费失败", userId);
            return false;
        } catch (Exception e) {
            log.error("续费套餐异常: userId={}, packageId={}", userId, packageId, e);
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
            log.info("清理过期套餐完成，共清理{}条记录", cleaned);
            return cleaned;
        } catch (Exception e) {
            log.error("清理过期套餐异常", e);
            return 0;
        }
    }

    @Override
    @Transactional
    public boolean initUserAccount(Long userId) {
        if (userId == null) {
            log.error("初始化用户账户失败，用户ID不能为空");
            return false;
        }

        try {
            // 检查是否已存在账户
            if (baseMapper.countByUserId(userId) > 0) {
                log.debug("用户{}账户已存在，无需重复初始化", userId);
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
                log.info("用户{}账户初始化成功", userId);
            } else {
                log.error("用户{}账户初始化失败", userId);
            }
            return saved;
        } catch (Exception e) {
            log.error("初始化用户账户异常: userId={}", userId, e);
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
            log.error("更新Token使用量参数无效: userId={}, tokenCount={}", userId, tokenCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            // 原子性扣减Token余额
            int updated = baseMapper.decrementTokenBalance(userId, tokenCount.longValue(), updateTime);

            if (updated > 0) {
                // 增加已使用Token统计
                baseMapper.incrementTokenUsed(userId, tokenCount.longValue(), updateTime);
                log.debug("用户{}Token使用量更新成功，使用了{}个Token", userId, tokenCount);
                return true;
            }

            log.warn("用户{}Token使用量更新失败，可能余额不足", userId);
            return false;
        } catch (Exception e) {
            log.error("更新Token使用量异常: userId={}, tokenCount={}", userId, tokenCount, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean recordTokenUsageOnly(Long userId, Integer tokenCount) {
        if (userId == null || tokenCount == null || tokenCount <= 0) {
            log.error("记录Token使用统计参数无效: userId={}, tokenCount={}", userId, tokenCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            // 只增加已使用Token统计，不扣减余额
            int updated = baseMapper.incrementTokenUsed(userId, tokenCount.longValue(), updateTime);

            if (updated > 0) {
                log.debug("用户{}Token使用统计记录成功，使用了{}个Token（仅统计）", userId, tokenCount);
                return true;
            }

            log.warn("用户{}Token使用统计记录失败", userId);
            return false;
        } catch (Exception e) {
            log.error("记录Token使用统计异常: userId={}, tokenCount={}", userId, tokenCount, e);
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
            log.error("增加Token余额参数无效: userId={}, tokenCount={}", userId, tokenCount);
            return false;
        }

        try {
            LocalDateTime updateTime = getBusinessDateTime();
            int updated = baseMapper.incrementTokenBalance(userId, tokenCount, updateTime);

            if (updated > 0) {
                log.info("用户{}Token余额增加成功，增加了{}个Token", userId, tokenCount);
                return true;
            }

            log.warn("用户{}Token余额增加失败", userId);
            return false;
        } catch (Exception e) {
            log.error("增加Token余额异常: userId={}, tokenCount={}", userId, tokenCount, e);
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


}
