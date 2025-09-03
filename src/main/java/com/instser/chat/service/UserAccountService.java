package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.UserAccount;

/**
 * 用户账户服务接口
 *
 * @author instser
 * @since 2025-06-18
 */
public interface UserAccountService extends IService<UserAccount> {

    /**
     * 获取用户账户信息
     *
     * @param userId 用户ID
     * @return 用户账户信息，如果不存在返回null
     */
    UserAccount getUserAccount(Long userId);

    /**
     * 激活用户套餐
     * 用于用户购买套餐后激活使用权限
     *
     * @param userId        用户ID
     * @param packageId     套餐ID
     * @param validityDays  有效期天数
     * @param totalChatCount 总聊天次数（用于总次数限制模式）
     * @return true-激活成功，false-激活失败
     */
    boolean activatePackage(Long userId, Long packageId, Integer validityDays, Integer totalChatCount);

    /**
     * 更新用户使用统计（用于总次数限制模式）
     * 注意：每日限制模式使用UserDailyUsageService的原子性操作
     *
     * @param userId    用户ID
     * @param usedCount 使用的次数
     * @return true-更新成功，false-更新失败或次数不足
     */
    boolean updateUsage(Long userId, Integer usedCount);

    /**
     * 检查用户是否有有效套餐
     *
     * @param userId 用户ID
     * @return true-有有效套餐，false-无套餐或已过期
     */
    boolean hasValidPackage(Long userId);

    /**
     * 检查用户套餐是否过期
     *
     * @param userId 用户ID
     * @return true-已过期，false-未过期或无套餐
     */
    boolean isPackageExpired(Long userId);

    /**
     * 获取用户剩余聊天次数（用于总次数限制模式）
     *
     * @param userId 用户ID
     * @return 剩余次数，如果无套餐或已过期返回0
     */
    int getRemainingChatCount(Long userId);

    /**
     * 更新用户Token使用量（原子性操作）
     *
     * @param userId     用户ID
     * @param tokenCount 使用的Token数量
     * @return true-更新成功，false-更新失败或余额不足
     */
    boolean updateTokenUsage(Long userId, Integer tokenCount);

    /**
     * 仅记录Token使用统计（不扣减余额）
     *
     * @param userId     用户ID
     * @param tokenCount 使用的Token数量
     * @return true-记录成功，false-记录失败
     */
    boolean recordTokenUsageOnly(Long userId, Integer tokenCount);

    /**
     * 获取用户Token余额
     *
     * @param userId 用户ID
     * @return Token余额，如果用户不存在返回0
     */
    Long getTokenBalance(Long userId);

    /**
     * 增加用户Token余额（充值）
     *
     * @param userId     用户ID
     * @param tokenCount 增加的Token数量
     * @return true-增加成功，false-增加失败
     */
    boolean addTokenBalance(Long userId, Long tokenCount);

    /**
     * 续费套餐
     * 延长现有套餐的有效期
     *
     * @param userId       用户ID
     * @param packageId    套餐ID
     * @param validityDays 延长的天数
     * @param additionalChatCount 额外增加的聊天次数
     * @return true-续费成功，false-续费失败
     */
    boolean renewPackage(Long userId, Long packageId, Integer validityDays, Integer additionalChatCount);

    /**
     * 升级套餐
     * 更换为更高级的套餐
     *
     * @param userId        用户ID
     * @param newPackageId  新套餐ID
     * @param validityDays  有效期天数
     * @param totalChatCount 总聊天次数
     * @return true-升级成功，false-升级失败
     */
    boolean upgradePackage(Long userId, Long newPackageId, Integer validityDays, Integer totalChatCount);

    /**
     * 清理过期套餐
     * 定时任务调用，清理过期的套餐信息
     *
     * @return 清理的记录数
     */
    int cleanExpiredPackages();

    /**
     * 初始化用户账户
     * 用户首次注册时创建账户记录
     *
     * @param userId 用户ID
     * @return true-初始化成功，false-初始化失败
     */
    boolean initUserAccount(Long userId);

    /**
     * 获取用户套餐剩余天数
     *
     * @param userId 用户ID
     * @return 剩余天数，如果无套餐或已过期返回0
     */
    long getRemainingDays(Long userId);

    /**
     * 检查用户是否可以聊天（总次数限制模式专用）
     *
     * @param userId 用户ID
     * @return true-可以聊天，false-不能聊天
     */
    boolean canChatWithTotalLimit(Long userId);

    /**
     * 获取活跃用户ID列表（缓存预热用）
     *
     * @param limit 限制数量
     * @return 活跃用户ID列表
     */
    java.util.List<Long> getActiveUserIds(int limit);
}
