package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.ChatMessageDao;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.dto.MessageSaveResult;
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
        message.setTokenCount(calculateTokenCount(content)); // 使用优化的token计算方法
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

    @Override
    public int deleteMessagesFromPointWithFilePreservation(String messageId, String sessionId, List<String> preserveFileIds) {
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

            // 先删除关联的文件，但跳过需要保留的文件
            int deletedFilesCount = 0;
            try {
                deletedFilesCount = fileUploadService.deleteFilesByMessageIds(messageIdsToDelete, targetMessage.getUserId(), preserveFileIds);
                log.info("删除消息关联文件完成（edit-resend场景），文件数量：{}，消息数量：{}，保留文件数量：{}",
                        deletedFilesCount, messageIdsToDelete.size(), preserveFileIds != null ? preserveFileIds.size() : 0);
            } catch (Exception e) {
                log.error("删除消息关联文件失败，继续删除消息", e);
            }

            // 执行删除消息
            boolean deleted = this.removeByIds(messagesToDelete);
            if (deleted) {
                log.info("成功删除{}条消息和{}个关联文件（edit-resend场景），起始messageId：{}，sessionId：{}",
                        messagesToDelete.size(), deletedFilesCount, messageId, sessionId);
                return messagesToDelete.size();
            } else {
                log.error("删除消息失败，messageId：{}，sessionId：{}", messageId, sessionId);
                return 0;
            }

        } catch (Exception e) {
            log.error("删除指定消息及之后消息失败（edit-resend场景），messageId：{}，sessionId：{}", messageId, sessionId, e);
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

    /**
     * 计算文本的token数量
     * 使用更准确的算法，区分中英文字符
     *
     * @param content 文本内容
     * @return token数量
     */
    private Integer calculateTokenCount(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int englishCount = 0;
        int digitCount = 0;
        int punctuationCount = 0;
        int whitespaceCount = 0;

        for (char c : content.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) {
                // 中文字符（包括中文标点）
                chineseCount++;
            } else if (Character.isLetter(c)) {
                // 英文字母
                englishCount++;
            } else if (Character.isDigit(c)) {
                // 数字
                digitCount++;
            } else if (Character.isWhitespace(c)) {
                // 空白字符
                whitespaceCount++;
            } else {
                // 标点符号和其他字符
                punctuationCount++;
            }
        }

        // Token计算规则（调整后更合理）：
        // - 中文字符：每个字符约1.2个token
        // - 英文字母：每4个字符约1个token
        // - 数字：每4个字符约1个token
        // - 标点符号：每3个字符约1个token
        // - 空白字符：每8个字符约1个token
        int totalTokens = (int) Math.ceil(
            chineseCount * 1.2 +
            englishCount / 4.0 +
            digitCount / 4.0 +
            punctuationCount / 3.0 +
            whitespaceCount / 8.0
        );

        // 最小值为1，避免返回0
        return Math.max(1, totalTokens);
    }

    @Override
    public MessageSaveResult sendMessageWithTokenInfo(String sessionId, String role, String content, Long userId, Long modelId) {
        try {
            // 计算Token数量
            Integer tokenCount = calculateTokenCount(content);

            // 创建消息对象
            ChatMessage message = new ChatMessage();
            message.setMessageId(java.util.UUID.randomUUID().toString());
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setMessageType("text");
            message.setHasAttachments(0);
            message.setUserId(userId);
            message.setModelId(modelId != null ? modelId : 1L);
            message.setTokenCount(tokenCount);
            message.setStatus(1);
            message.setCreatedTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());

            // 保存消息
            boolean saved = this.save(message);

            if (saved) {
                log.info("{}消息保存成功，消息ID：{}，角色：{}，模型ID：{}，Token数：{}",
                        "assistant".equals(role) ? "AI" : "用户", message.getId(), role, modelId, tokenCount);
                return MessageSaveResult.success(message.getId(), tokenCount);
            } else {
                log.error("消息保存失败，会话ID：{}，角色：{}", sessionId, role);
                return MessageSaveResult.failure();
            }
        } catch (Exception e) {
            log.error("保存消息异常，会话ID：{}，角色：{}", sessionId, role, e);
            return MessageSaveResult.failure();
        }
    }
}
