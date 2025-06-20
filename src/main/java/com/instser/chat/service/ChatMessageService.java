package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.ChatMessage;

import java.util.List;

/**
 * 聊天消息服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 发送消息
     *
     * @param sessionId 会话ID
     * @param content   消息内容
     * @param userId    用户ID
     * @return 消息ID
     */
    Long sendMessage(String sessionId, String content, Long userId);

    /**
     * 发送消息（支持指定角色）
     *
     * @param sessionId 会话ID
     * @param role      消息角色（user/assistant）
     * @param content   消息内容
     * @param userId    用户ID
     * @return 消息ID
     */
    Long sendMessage(String sessionId, String role, String content, Long userId);

    /**
     * 获取会话消息列表
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> getSessionMessages(String sessionId);

    /**
     * 获取用户最近消息
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 消息列表
     */
    List<ChatMessage> getRecentMessages(Long userId, Integer limit);

    /**
     * 更新消息状态
     *
     * @param messageId 消息ID
     * @param status    状态
     * @param errorMessage 错误信息
     */
    void updateMessageStatus(Long messageId, Integer status, String errorMessage);

    /**
     * 根据消息ID查询消息
     *
     * @param messageId 消息ID（UUID）
     * @return 消息信息
     */
    ChatMessage getByMessageId(String messageId);

    /**
     * 获取会话消息数量
     *
     * @param sessionId 会话ID
     * @return 消息数量
     */
    long getMessageCountBySession(String sessionId);
}
