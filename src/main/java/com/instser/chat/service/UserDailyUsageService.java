package com.instser.chat.service;

import com.instser.chat.entity.UserDailyUsage;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户每日使用记录服务接口
 *
 * @author instser
 * @since 2025-06-18
 */
public interface UserDailyUsageService {

    /**
     * 检查用户今日是否还能聊天
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @return true-可以聊天，false-已达到限制
     */
    boolean canChatToday(Long userId, Long packageId);

    /**
     * 获取用户今日剩余聊天次数
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @return 剩余次数
     */
    int getRemainingChatsToday(Long userId, Long packageId);

    /**
     * 记录用户聊天使用
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @return true-记录成功，false-已达到限制
     */
    boolean recordChatUsage(Long userId, Long packageId);

    /**
     * 获取用户今日使用记录
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @return 使用记录
     */
    UserDailyUsage getTodayUsage(Long userId, Long packageId);

    /**
     * 获取用户最近几天的使用记录
     *
     * @param userId 用户ID
     * @param days   天数
     * @return 使用记录列表
     */
    List<UserDailyUsage> getRecentUsage(Long userId, Integer days);

    /**
     * 初始化用户今日使用记录
     *
     * @param userId      用户ID
     * @param packageId   套餐ID
     * @param dailyLimit  每日限制次数
     * @return 使用记录
     */
    UserDailyUsage initTodayUsage(Long userId, Long packageId, Integer dailyLimit);

    /**
     * 清理过期的使用记录
     *
     * @param retentionDays 保留天数
     * @return 清理的记录数
     */
    int cleanExpiredRecords(Integer retentionDays);

    // ==================== 模型维度的使用限制方法 ====================

    /**
     * 检查用户是否可以使用指定模型
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @param modelName 模型名称
     * @return true-可以使用，false-已达到限制
     */
    boolean canChatWithModel(Long userId, Long packageId, String modelName);

    /**
     * 记录用户模型使用
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @param modelName 模型名称
     * @return true-记录成功，false-已达到限制
     */
    boolean recordModelChatUsage(Long userId, Long packageId, String modelName);

    /**
     * 获取用户模型剩余使用次数
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @param modelName 模型名称
     * @return 剩余次数，-1表示无限制
     */
    int getRemainingModelChats(Long userId, Long packageId, String modelName);

    /**
     * 获取用户今日模型使用记录
     *
     * @param userId    用户ID
     * @param packageId 套餐ID
     * @param modelName 模型名称
     * @return 使用记录
     */
    UserDailyUsage getTodayModelUsage(Long userId, Long packageId, String modelName);

    /**
     * 获取用户所有模型的今日使用情况
     *
     * @param userId 用户ID
     * @return 模型使用情况列表
     */
    List<UserDailyUsage> getUserModelUsageToday(Long userId);
}
