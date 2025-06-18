package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.UserAccountDao;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.UserAccountService;
import lombok.extern.slf4j.Slf4j;
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
            LocalDateTime expireTime = getBusinessDateTime().plusDays(validityDays);
            Integer chatCount = totalChatCount != null ? totalChatCount : 0;
            
            // 使用原子性操作创建或更新账户信息
            int updated = baseMapper.insertOrUpdateAccount(userId, packageId, expireTime, chatCount);
            
            if (updated > 0) {
                log.info("用户{}套餐激活成功，套餐ID：{}，有效期：{}天，总次数：{}", 
                        userId, packageId, validityDays, chatCount);
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
            // 原子性减少剩余次数
            int updated = baseMapper.decrementChatCount(userId, usedCount);
            
            if (updated > 0) {
                // 增加总使用次数统计
                baseMapper.incrementTotalChatCount(userId, usedCount);
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

            int updated = baseMapper.updatePackageInfo(userId, packageId, newExpireTime, newRemaining);
            
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
            int cleaned = baseMapper.cleanExpiredPackages(expiredTime);
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
            account.setCreateTime(getBusinessDateTime());
            account.setUpdateTime(getBusinessDateTime());
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
}
