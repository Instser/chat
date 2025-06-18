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
            if (userAccount.getPackageExpireTime() != null &&
                userAccount.getPackageExpireTime().isBefore(getBusinessDateTime())) {
                return ChatLimitVO.failed("套餐已过期");
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
            log.error("验证用户{}聊天权限时发生异常", userId, e);
            return ChatLimitVO.failed("系统异常，请稍后重试");
        }
    }

    @Override
    public boolean recordChatUsage(Long userId) {
        try {
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                log.warn("用户{}没有有效套餐，无法记录使用", userId);
                return false;
            }

            Package packageInfo = packageService.getPackageById(userAccount.getPackageId());
            if (packageInfo == null) {
                log.warn("用户{}的套餐{}不存在", userId, userAccount.getPackageId());
                return false;
            }

            if ("daily".equals(packageInfo.getLimitType())) {
                // 每日限制模式
                return userDailyUsageService.recordChatUsage(userId, userAccount.getPackageId());
            } else if ("total".equals(packageInfo.getLimitType())) {
                // 总次数限制模式
                return recordTotalUsage(userAccount);
            } else {
                // 无限制模式
                return true;
            }

        } catch (Exception e) {
            log.error("记录用户{}聊天使用时发生异常", userId, e);
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
        if (!userDailyUsageService.canChatToday(userId, packageId)) {
            result.setCanChat(false);
            result.setLimitReason("今日聊天次数已达到限制");
        }

        int remaining = userDailyUsageService.getRemainingChatsToday(userId, packageId);
        UserDailyUsage todayUsage = userDailyUsageService.getTodayUsage(userId, packageId);

        result.setDailyLimit(packageInfo.getDailyChatLimit());
        result.setRemainingToday(remaining);
        result.setUsedToday(todayUsage != null ? todayUsage.getChatCountUsed() : 0);

        return result;
    }

    /**
     * 验证总次数限制
     */
    private ChatLimitVO validateTotalLimit(UserAccount userAccount, Package packageInfo, ChatLimitVO result) {
        int remaining = userAccount.getChatCountRemaining() != null ? userAccount.getChatCountRemaining() : 0;
        
        if (remaining <= 0) {
            result.setCanChat(false);
            result.setLimitReason("套餐聊天次数已用完");
        }

        result.setTotalRemaining(remaining);
        return result;
    }

    /**
     * 记录总次数使用（使用原子性操作）
     */
    private boolean recordTotalUsage(UserAccount userAccount) {
        // 使用UserAccountService的原子性操作来减少剩余次数
        boolean success = userAccountService.updateUsage(userAccount.getUserId(), 1);

        if (success) {
            log.debug("用户{}总次数使用记录成功", userAccount.getUserId());
        } else {
            log.warn("用户{}总次数使用记录失败，可能次数不足或套餐已过期", userAccount.getUserId());
        }

        return success;
    }
}
