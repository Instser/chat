package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.dto.MessageSaveResult;

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
     * 发送消息（支持指定角色和模型ID）
     *
     * @param sessionId 会话ID
     * @param role      消息角色（user/assistant）
     * @param content   消息内容
     * @param userId    用户ID
     * @param modelId   模型ID
     * @return 消息ID
     */
    Long sendMessage(String sessionId, String role, String content, Long userId, Long modelId);

    /**
     * 发送消息（支持指定角色、模型ID和附件标识）
     *
     * @param sessionId 会话ID
     * @param role      消息角色（user/assistant）
     * @param content   消息内容
     * @param userId    用户ID
     * @param modelId   模型ID
     * @param hasAttachments 是否包含附件
     * @return 消息ID
     */
    Long sendMessage(String sessionId, String role, String content, Long userId, Long modelId, Integer hasAttachments);

    /**
     * 发送错误响应消息（状态为3）
     *
     * <p>兼容旧路径：不区分 content 与根因，error_message 留空。</p>
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param modelId   模型ID
     * @param errorContent 错误响应内容
     * @return 消息ID
     */
    Long sendErrorResponseMessage(String sessionId, Long userId, Long modelId, String errorContent);

    /**
     * 发送错误响应消息（状态为3），并写入真正的错误根因到 error_message 字段。
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param modelId   模型ID
     * @param errorContent 错误响应内容（写入 content 字段，面向用户的通用文案）
     * @param errorMessage 错误根因（写入 error_message 字段，真实的报错信息）
     * @return 消息ID
     */
    Long sendErrorResponseMessage(String sessionId, Long userId, Long modelId, String errorContent, String errorMessage);

    /**
     * 发送消息并返回Token信息（优化版本，避免额外数据库查询）
     *
     * @param sessionId 会话ID
     * @param role      消息角色（user/assistant）
     * @param content   消息内容
     * @param userId    用户ID
     * @param modelId   模型ID
     * @return 消息保存结果（包含消息ID和Token数量）
     */
    MessageSaveResult sendMessageWithTokenInfo(String sessionId, String role, String content, Long userId, Long modelId);

    /**
     * 发送消息并返回 Token 信息（支持 reasoning）。
     * token 仍按 content 计；reasoning 仅展示/审计。
     */
    MessageSaveResult sendMessageWithTokenInfo(String sessionId, String role, String content,
                                               String reasoning, Long userId, Long modelId);

    /**
     * 获取会话消息列表
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> getSessionMessages(String sessionId);

    /**
     * 获取会话消息列表（用于AI上下文）
     *
     * @param sessionId 会话ID
     * @return 消息列表（排除status=3的错误响应）
     */
    List<ChatMessage> getSessionMessagesForAi(String sessionId);

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

    /**
     * 删除指定消息及之后的所有消息
     *
     * @param messageId 起始消息ID（UUID）
     * @param sessionId 会话ID
     * @return 删除的消息数量
     */
    int deleteMessagesFromPoint(String messageId, String sessionId);

    /**
     * 删除指定消息及之后的所有消息（用于edit-resend场景，保留指定文件）
     *
     * @param messageId 起始消息ID（UUID）
     * @param sessionId 会话ID
     * @param preserveFileIds 需要保留的文件ID列表（不删除这些文件）
     * @return 删除的消息数量
     */
    int deleteMessagesFromPointWithFilePreservation(String messageId, String sessionId, List<String> preserveFileIds);

    /**
     * 更新消息的联网查询标记
     *
     * @param messageId     消息ID
     * @param hasWebSearch  是否使用联网查询
     * @return 是否更新成功
     */
    boolean updateWebSearchFlag(String messageId, boolean hasWebSearch);

    /**
     * 删除指定会话的所有消息及其关联数据
     *
     * @param sessionId 会话ID
     * @param userId    用户ID（用于验证权限）
     * @return 删除的消息数量
     */
    int deleteAllBySessionId(String sessionId, Long userId);

    /**
     * 批量删除指定会话的所有消息及其关联数据
     *
     * @param sessionIds 会话ID列表
     * @param userId     用户ID（用于验证权限）
     * @return 删除的消息数量
     */
    int deleteAllBySessionIds(List<String> sessionIds, Long userId);
}
