package com.instser.chat.service;

import com.instser.chat.vo.ChatLimitVO;

/**
 * 聊天限制验证服务接口
 *
 * @author instser
 * @since 2025-06-18
 */
public interface ChatLimitService {

    /**
     * 验证用户是否可以发起聊天
     *
     * @param userId 用户ID
     * @return 验证结果
     */
    ChatLimitVO validateChatPermission(Long userId);

    /**
     * 记录用户聊天使用
     *
     * @param userId 用户ID
     * @return true-记录成功，false-记录失败
     */
    boolean recordChatUsage(Long userId);

    /**
     * 获取用户聊天限制信息
     *
     * @param userId 用户ID
     * @return 限制信息
     */
    ChatLimitVO getChatLimitInfo(Long userId);

    /**
     * 验证用户是否可以使用指定模型进行聊天
     *
     * @param userId    用户ID
     * @param modelName 模型名称
     * @return 验证结果
     */
    ChatLimitVO validateModelChatPermission(Long userId, String modelName);

    /**
     * 记录用户模型聊天使用
     *
     * @param userId    用户ID
     * @param modelName 模型名称
     * @return true-记录成功，false-记录失败
     */
    boolean recordModelChatUsage(Long userId, String modelName);

    /**
     * 记录Token使用量（扣减用户Token余额）
     *
     * @param userId     用户ID
     * @param tokenCount Token数量
     * @return true-记录成功，false-记录失败或余额不足
     */
    boolean recordTokenUsage(Long userId, Integer tokenCount);

    /**
     * 检查用户Token余额是否充足
     *
     * @param userId     用户ID
     * @param tokenCount 需要的Token数量
     * @return true-余额充足，false-余额不足
     */
    boolean hasEnoughTokens(Long userId, Integer tokenCount);
}
