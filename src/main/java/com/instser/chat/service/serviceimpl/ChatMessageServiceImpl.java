package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.ChatMessageDao;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.service.ChatMessageService;
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
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageDao, ChatMessage> implements ChatMessageService {

    @Override
    public Long sendMessage(String sessionId, String content, Long userId) {
        // 默认为用户消息
        return sendMessage(sessionId, "user", content, userId);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(java.util.UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(role); // 使用传入的角色信息
        message.setContent(content);
        message.setMessageType("text");
        message.setUserId(userId);
        message.setModelId(1L); // 默认模型ID，后续可以从会话中获取
        message.setTokenCount(content.length() / 4); // 简单估算token数
        message.setStatus(1); // 成功状态
        message.setCreatedTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        this.save(message);
        log.info("{}消息保存成功，消息ID：{}，角色：{}",
                "assistant".equals(role) ? "AI" : "用户", message.getId(), role);

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

            for (ChatMessage message : allMessages) {
                if (message.getMessageId().equals(messageId)) {
                    foundTarget = true;
                }
                if (foundTarget) {
                    messagesToDelete.add(message.getId());
                }
            }

            if (messagesToDelete.isEmpty()) {
                log.warn("未找到要删除的消息范围，messageId：{}", messageId);
                return 0;
            }

            // 执行删除
            boolean deleted = this.removeByIds(messagesToDelete);
            if (deleted) {
                log.info("成功删除{}条消息，起始messageId：{}，sessionId：{}",
                        messagesToDelete.size(), messageId, sessionId);
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
}
