package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.ChatMessageDao;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageDao, ChatMessage> implements ChatMessageService {

    private final ChatSessionService chatSessionService;
    private final FileUploadService fileUploadService;

    @Override
    public Long sendMessage(String sessionId, String content, Long userId) {
        // 默认为用户消息
        return sendMessage(sessionId, "user", content, userId);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId) {
        // 从会话中获取modelId，如果获取失败则使用默认值1L
        Long modelId = getModelIdFromSession(sessionId);
        return sendMessage(sessionId, role, content, userId, modelId);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId, Long modelId) {
        return sendMessage(sessionId, role, content, userId, modelId, 0);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId, Long modelId, Integer hasAttachments) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(java.util.UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(role); // 使用传入的角色信息
        message.setContent(content);
        message.setMessageType("text");
        message.setHasAttachments(hasAttachments != null ? hasAttachments : 0); // 设置附件标识
        message.setUserId(userId);
        message.setModelId(modelId != null ? modelId : 1L); // 使用传入的modelId，如果为null则使用默认值
        message.setTokenCount(content.length() / 4); // 简单估算token数
        message.setStatus(1); // 成功状态
        message.setCreatedTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        this.save(message);
        log.info("{}消息保存成功，消息ID：{}，角色：{}，模型ID：{}，附件：{}",
                "assistant".equals(role) ? "AI" : "用户", message.getId(), role, modelId,
                hasAttachments != null && hasAttachments > 0 ? "有" : "无");

        return message.getId();
    }

    @Override
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return baseMapper.selectBySessionId(sessionId);
    }

    @Override
    public List<ChatMessage> getRecentMessages(Long userId, Integer limit) {
        return baseMapper.selectRecentByUserId(userId, limit);
    }

    @Override
    public void updateMessageStatus(Long messageId, Integer status, String errorMessage) {
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setStatus(status);
        message.setErrorMessage(errorMessage);
        message.setUpdateTime(LocalDateTime.now());

        this.updateById(message);
        log.info("消息状态更新成功，消息ID：{}，状态：{}", messageId, status);
    }

    @Override
    public ChatMessage getByMessageId(String messageId) {
        return baseMapper.selectByMessageId(messageId);
    }

    @Override
    public long getMessageCountBySession(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        return this.count(wrapper);
    }

    @Override
    public int deleteMessagesFromPoint(String messageId, String sessionId) {
        try {
            // 获取指定消息
            ChatMessage targetMessage = getByMessageId(messageId);
            if (targetMessage == null) {
                log.warn("未找到要删除的消息，messageId：{}", messageId);
                return 0;
            }

            if (!targetMessage.getSessionId().equals(sessionId)) {
                log.warn("消息不属于指定会话，messageId：{}，sessionId：{}", messageId, sessionId);
                return 0;
            }

            // 获取该会话的所有消息，按创建时间排序
            List<ChatMessage> allMessages = getSessionMessages(sessionId);

            // 找到目标消息的位置，删除该消息及之后的所有消息
            boolean foundTarget = false;
            List<Long> messagesToDelete = new ArrayList<>();
            List<String> messageIdsToDelete = new ArrayList<>();

            for (ChatMessage message : allMessages) {
                if (message.getMessageId().equals(messageId)) {
                    foundTarget = true;
                }
                if (foundTarget) {
                    messagesToDelete.add(message.getId());
                    messageIdsToDelete.add(message.getMessageId());
                }
            }

            if (messagesToDelete.isEmpty()) {
                log.warn("未找到要删除的消息范围，messageId：{}", messageId);
                return 0;
            }

            // 先删除关联的文件
            int deletedFilesCount = 0;
            try {
                deletedFilesCount = fileUploadService.deleteFilesByMessageIds(messageIdsToDelete, targetMessage.getUserId());
                log.info("删除消息关联文件完成，文件数量：{}，消息数量：{}", deletedFilesCount, messageIdsToDelete.size());
            } catch (Exception e) {
                log.error("删除消息关联文件失败，继续删除消息", e);
            }

            // 执行删除消息
            boolean deleted = this.removeByIds(messagesToDelete);
            if (deleted) {
                log.info("成功删除{}条消息和{}个关联文件，起始messageId：{}，sessionId：{}",
                        messagesToDelete.size(), deletedFilesCount, messageId, sessionId);
                return messagesToDelete.size();
            } else {
                log.error("删除消息失败，messageId：{}，sessionId：{}", messageId, sessionId);
                return 0;
            }

        } catch (Exception e) {
            log.error("删除指定消息及之后消息失败，messageId：{}，sessionId：{}", messageId, sessionId, e);
            return 0;
        }
    }

    /**
     * 从会话中获取模型ID
     *
     * @param sessionId 会话ID
     * @return 模型ID，如果获取失败则返回1L
     */
    private Long getModelIdFromSession(String sessionId) {
        try {
            if (sessionId != null) {
                ChatSession session = chatSessionService.getSessionInfo(sessionId);
                if (session != null && session.getModelId() != null) {
                    return session.getModelId();
                }
            }
        } catch (Exception e) {
            log.warn("从会话{}获取模型ID失败，使用默认值", sessionId, e);
        }
        return 1L; // 默认模型ID
    }
}
