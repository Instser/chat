package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.ChatSessionDao;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 聊天会话服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionDao, ChatSession> implements ChatSessionService {

    @Override
    public String createSession(Long userId, Long modelId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setTitle("新对话");
        session.setUserId(userId);
        session.setModelId(modelId);
        session.setStatus(0); // 进行中
        session.setTokenCount(0);
        session.setLastActiveTime(LocalDateTime.now());
        session.setCreatedTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        this.save(session);
        log.info("新会话创建成功，会话ID：{}", sessionId);

        return sessionId;
    }

    @Override
    public List<ChatSession> getUserSessions(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public ChatSession getSessionInfo(String sessionId) {
        return baseMapper.selectBySessionId(sessionId);
    }

    @Override
    public boolean updateSessionTitle(String sessionId, String title) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);

        ChatSession session = new ChatSession();
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());

        boolean result = this.update(session, wrapper);
        if (result) {
            log.info("会话标题更新成功，会话ID：{}，标题：{}", sessionId, title);
        } else {
            log.warn("会话标题更新失败，会话ID：{}，标题：{}", sessionId, title);
        }
        return result;
    }

    @Override
    public void updateLastActiveTime(String sessionId, String lastMessage) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);

        ChatSession session = new ChatSession();
        session.setLastMessage(lastMessage);
        session.setLastActiveTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        this.update(session, wrapper);
        log.info("会话活跃时间更新成功，会话ID：{}", sessionId);
    }

    @Override
    public boolean deleteSession(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);

        boolean result = this.remove(wrapper);
        if (result) {
            log.info("会话删除成功，会话ID：{}", sessionId);
        } else {
            log.warn("会话删除失败，会话ID：{}", sessionId);
        }
        return result;
    }
}
