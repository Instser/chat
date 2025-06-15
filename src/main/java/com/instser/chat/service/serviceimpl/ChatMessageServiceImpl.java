package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.ChatMessageDao;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
}
