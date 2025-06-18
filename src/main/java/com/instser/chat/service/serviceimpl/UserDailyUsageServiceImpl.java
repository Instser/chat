package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.UserDailyUsageDao;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.entity.UserDailyUsage;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserDailyUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 用户每日使用记录服务实现类
 *
 * @author instser
 * @since 2025-06-18
 */
@Slf4j
@Service
public class UserDailyUsageServiceImpl extends ServiceImpl<UserDailyUsageDao, UserDailyUsage>
        implements UserDailyUsageService {

    /**
     * 业务时区 - 统一使用上海时区
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PackageService packageService;

    @Autowired
    private UserAccountService userAccountService;

    /**
     * 获取业务日期（上海时区）
     *
     * @return 当前业务日期
     */
    private LocalDate getBusinessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * 获取业务时间（上海时区）
     *
     * @return 当前业务时间
     */
    private LocalDateTime getBusinessDateTime() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    @Override
    public boolean canChatToday(Long userId, Long packageId) {
        UserDailyUsage todayUsage = getTodayUsage(userId, packageId);
        
        if (todayUsage == null) {
            // 没有今日记录，需要检查用户是否有有效套餐
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                log.warn("用户{}没有有效套餐", userId);
                return false;
            }
            
            if (!userAccount.getPackageId().equals(packageId)) {
                log.warn("用户{}的套餐ID不匹配，当前套餐：{}，请求套餐：{}", 
                        userId, userAccount.getPackageId(), packageId);
                return false;
            }
            
            // 检查套餐是否过期
            if (userAccount.getPackageExpireTime() != null &&
                userAccount.getPackageExpireTime().isBefore(getBusinessDateTime())) {
                log.warn("用户{}的套餐已过期", userId);
                return false;
            }
            
            return true; // 可以创建新的使用记录
        }
        
        return todayUsage.getChatCountUsed() < todayUsage.getDailyLimit();
    }

    @Override
    public int getRemainingChatsToday(Long userId, Long packageId) {
        UserDailyUsage todayUsage = getTodayUsage(userId, packageId);
        
        if (todayUsage == null) {
            // 获取套餐的每日限制
            Package packageInfo = packageService.getPackageById(packageId);
            if (packageInfo != null && "daily".equals(packageInfo.getLimitType())) {
                return packageInfo.getDailyChatLimit();
            }
            return 0;
        }
        
        return Math.max(0, todayUsage.getDailyLimit() - todayUsage.getChatCountUsed());
    }

    @Override
    @Transactional
    public boolean recordChatUsage(Long userId, Long packageId) {
        // 获取套餐信息
        Package packageInfo = packageService.getPackageById(packageId);
        if (packageInfo == null || !"daily".equals(packageInfo.getLimitType())) {
            log.error("套餐{}不支持每日限制模式", packageId);
            return false;
        }

        LocalDate businessDate = getBusinessDate();

        // 先检查当前使用情况，避免超限
        UserDailyUsage todayUsage = getTodayUsage(userId, packageId);
        if (todayUsage != null && todayUsage.getChatCountUsed() >= todayUsage.getDailyLimit()) {
            log.warn("用户{}今日聊天次数已达到限制：{}/{}",
                    userId, todayUsage.getChatCountUsed(), todayUsage.getDailyLimit());
            return false;
        }

        // 使用原子性操作插入或更新使用记录
        int updated = baseMapper.insertOrIncrementUsage(userId, packageId, businessDate,
                packageInfo.getDailyChatLimit());

        if (updated > 0) {
            // 重新获取更新后的记录用于日志
            UserDailyUsage updatedUsage = getTodayUsage(userId, packageId);
            if (updatedUsage != null) {
                log.info("用户{}聊天使用记录更新成功，当前使用：{}/{}，业务日期：{}",
                        userId, updatedUsage.getChatCountUsed(), updatedUsage.getDailyLimit(), businessDate);

                // 再次检查是否超限（防止并发情况下超限）
                if (updatedUsage.getChatCountUsed() > updatedUsage.getDailyLimit()) {
                    log.warn("用户{}使用次数超限，当前：{}/{}，可能存在并发问题",
                            userId, updatedUsage.getChatCountUsed(), updatedUsage.getDailyLimit());
                }
            }
            return true;
        }

        log.error("用户{}聊天使用记录更新失败", userId);
        return false;
    }

    @Override
    public UserDailyUsage getTodayUsage(Long userId, Long packageId) {
        return baseMapper.selectByUserAndDate(userId, packageId, getBusinessDate());
    }

    @Override
    public List<UserDailyUsage> getRecentUsage(Long userId, Integer days) {
        return baseMapper.selectRecentUsage(userId, days);
    }

    @Override
    @Transactional
    public UserDailyUsage initTodayUsage(Long userId, Long packageId, Integer dailyLimit) {
        UserDailyUsage usage = new UserDailyUsage();
        usage.setUserId(userId);
        usage.setPackageId(packageId);
        usage.setUsageDate(getBusinessDate());
        usage.setChatCountUsed(0);
        usage.setDailyLimit(dailyLimit);
        usage.setCreatedTime(getBusinessDateTime());
        usage.setUpdateTime(getBusinessDateTime());

        if (this.save(usage)) {
            log.info("用户{}今日使用记录初始化成功，每日限制：{}，业务日期：{}",
                    userId, dailyLimit, getBusinessDate());
            return usage;
        }

        log.error("用户{}今日使用记录初始化失败", userId);
        return null;
    }

    @Override
    @Transactional
    public int cleanExpiredRecords(Integer retentionDays) {
        LocalDate beforeDate = getBusinessDate().minusDays(retentionDays);
        int deleted = baseMapper.deleteExpiredRecords(beforeDate);
        log.info("清理{}天前的使用记录，共清理{}条，基准日期：{}",
                retentionDays, deleted, getBusinessDate());
        return deleted;
    }
}
