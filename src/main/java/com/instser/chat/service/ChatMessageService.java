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
}
