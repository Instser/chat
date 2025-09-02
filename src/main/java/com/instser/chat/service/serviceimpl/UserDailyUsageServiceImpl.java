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
import java.util.Map;

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
        // 注意：此方法已废弃，因为新表结构不再存储package_id和daily_limit
        // 建议使用 initTodayModelUsage(userId, modelName) 替代
        log.warn("initTodayUsage方法已废弃，建议使用initTodayModelUsage替代");

        UserDailyUsage usage = new UserDailyUsage();
        usage.setUserId(userId);
        usage.setModelName("STANDARD"); // 默认使用STANDARD模型
        usage.setUsageDate(getBusinessDate());
        usage.setChatCountUsed(0);
        usage.setCreatedTime(getBusinessDateTime());
        usage.setUpdateTime(getBusinessDateTime());

        if (this.save(usage)) {
            log.info("用户{}今日使用记录初始化成功，模型：STANDARD，业务日期：{}",
                    userId, getBusinessDate());
            return usage;
        }

        log.error("用户{}今日使用记录初始化失败", userId);
        return null;
    }

    /**
     * 初始化用户今日模型使用记录
     */
    @Transactional
    public UserDailyUsage initTodayModelUsage(Long userId, String modelName) {
        UserDailyUsage usage = new UserDailyUsage();
        usage.setUserId(userId);
        usage.setModelName(modelName);
        usage.setUsageDate(getBusinessDate());
        usage.setChatCountUsed(0);
        usage.setCreatedTime(getBusinessDateTime());
        usage.setUpdateTime(getBusinessDateTime());

        if (this.save(usage)) {
            log.info("用户{}模型{}今日使用记录初始化成功，业务日期：{}",
                    userId, modelName, getBusinessDate());
            return usage;
        }

        log.error("用户{}模型{}今日使用记录初始化失败", userId, modelName);
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

    // ==================== 模型维度的使用限制方法实现 ====================

    @Override
    public boolean canChatWithModel(Long userId, Long packageId, String modelId) {
        // 1. 获取套餐信息和模型权限配置
        Package packageInfo = packageService.getPackageById(packageId);
        if (packageInfo == null) {
            log.warn("套餐{}不存在", packageId);
            return false;
        }

        Map<String, Package.ModelPermission> modelPermissions = packageInfo.getModelPermissionsMap();
        Package.ModelPermission permission = modelPermissions.get(modelId);

        if (permission == null) {
            log.warn("套餐{}不支持模型ID{}", packageId, modelId);
            return false;
        }

        // 2. 如果是无限制，直接允许
        if (permission.getDailyLimit() == -1) {
            return true;
        }

        // 3. 检查今日使用情况
        UserDailyUsage todayUsage = getTodayModelUsage(userId, modelId);
        if (todayUsage == null) {
            return true; // 今日未使用
        }

        return todayUsage.getChatCountUsed() < permission.getDailyLimit();
    }

    @Override
    @Transactional
    public boolean recordModelChatUsage(Long userId, Long packageId, String modelId) {
        // 1. 获取套餐模型权限配置
        Package packageInfo = packageService.getPackageById(packageId);
        if (packageInfo == null) {
            log.error("套餐{}不存在，无法记录模型使用", packageId);
            return false;
        }

        Map<String, Package.ModelPermission> modelPermissions = packageInfo.getModelPermissionsMap();
        Package.ModelPermission permission = modelPermissions.get(modelId);

        if (permission == null) {
            log.error("套餐{}不支持模型ID{}", packageId, modelId);
            return false;
        }

        // 2. 检查当前使用情况（非无限制套餐）
        if (permission.getDailyLimit() != -1) {
            UserDailyUsage todayUsage = getTodayModelUsage(userId, modelId);
            if (todayUsage != null && todayUsage.getChatCountUsed() >= permission.getDailyLimit()) {
                log.warn("用户{}模型ID{}今日使用次数已达限制：{}/{}",
                        userId, modelId, todayUsage.getChatCountUsed(), permission.getDailyLimit());
                return false;
            }
        }

        // 3. 使用原子性操作记录使用
        LocalDate businessDate = getBusinessDate();
        LocalDateTime businessDateTime = getBusinessDateTime();
        int dailyLimit = permission.getDailyLimit();

        int updated = baseMapper.insertOrIncrementModelUsage(userId, modelId, businessDate, businessDateTime);

        if (updated > 0) {
            // 重新获取更新后的记录用于日志
            UserDailyUsage updatedUsage = getTodayModelUsage(userId, modelId);
            if (updatedUsage != null) {
                log.info("用户{}模型ID{}使用记录成功，当前使用：{}/{}，业务日期：{}",
                        userId, modelId, updatedUsage.getChatCountUsed(),
                        dailyLimit, businessDate);
            }
            return true;
        }

        log.error("用户{}模型ID{}使用记录失败", userId, modelId);
        return false;
    }

    @Override
    public int getRemainingModelChats(Long userId, Long packageId, String modelId) {
        // 1. 获取套餐模型权限配置
        Package packageInfo = packageService.getPackageById(packageId);
        if (packageInfo == null) {
            return 0;
        }

        Map<String, Package.ModelPermission> modelPermissions = packageInfo.getModelPermissionsMap();
        Package.ModelPermission permission = modelPermissions.get(modelId);

        if (permission == null) {
            return 0;
        }

        // 2. 无限制套餐
        if (permission.getDailyLimit() == -1) {
            return -1;
        }

        // 3. 计算剩余次数
        UserDailyUsage todayUsage = getTodayModelUsage(userId, modelId);
        if (todayUsage == null) {
            return permission.getDailyLimit();
        }

        return Math.max(0, permission.getDailyLimit() - todayUsage.getChatCountUsed());
    }

    @Override
    public UserDailyUsage getTodayModelUsage(Long userId, Long packageId, String modelId) {
        // 注意：packageId参数已废弃，但为了向后兼容保留此方法
        return getTodayModelUsage(userId, modelId);
    }

    /**
     * 获取用户今日模型使用记录（新方法，不依赖packageId）
     */
    public UserDailyUsage getTodayModelUsage(Long userId, String modelId) {
        return baseMapper.selectByUserModelDate(userId, modelId, getBusinessDate());
    }

    @Override
    public List<UserDailyUsage> getUserModelUsageToday(Long userId) {
        return baseMapper.selectRecentUsage(userId, 1);
    }
}
