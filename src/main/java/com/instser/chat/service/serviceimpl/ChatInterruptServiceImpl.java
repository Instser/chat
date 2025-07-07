package com.instser.chat.service.serviceimpl;

import com.instser.chat.service.ChatInterruptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天中断服务实现类
 * 
 * @author instser
 * @since 2025-06-26
 */
@Slf4j
@Service
public class ChatInterruptServiceImpl implements ChatInterruptService {
    
    /**
     * 活跃会话映射：sessionId -> 会话信息
     */
    private final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * 中断标志映射：sessionId -> 是否中断
     */
    private final ConcurrentHashMap<String, Boolean> interruptFlags = new ConcurrentHashMap<>();
    
    /**
     * 会话信息内部类
     */
    private static class SessionInfo {
        private final Long userId;
        private final LocalDateTime startTime;
        
        public SessionInfo(Long userId) {
            this.userId = userId;
            this.startTime = LocalDateTime.now();
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
        
        public boolean isTimeout() {
            // 会话超时时间：30分钟
            return startTime.plusMinutes(30).isBefore(LocalDateTime.now());
        }
    }
    
    @Override
    public void registerActiveSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            log.warn("【会话管理】注册活跃会话失败：参数为空，sessionId={}, userId={}", sessionId, userId);
            return;
        }

        activeSessions.put(sessionId, new SessionInfo(userId));
        interruptFlags.put(sessionId, false);

        log.info("【会话管理】注册活跃会话成功，用户ID：{}，会话ID：{}，当前活跃会话数：{}",
                userId, sessionId, activeSessions.size());
        log.debug("【会话管理】会话详细信息 - 开始时间：{}", LocalDateTime.now());
    }

    @Override
    public boolean interruptChat(String sessionId, Long userId) {
        log.debug("【会话中断】收到中断请求，用户ID：{}，会话ID：{}", userId, sessionId);

        if (sessionId == null || userId == null) {
            log.warn("【会话中断】中断失败：参数为空，sessionId={}, userId={}", sessionId, userId);
            return false;
        }

        SessionInfo sessionInfo = activeSessions.get(sessionId);
        if (sessionInfo == null) {
            log.warn("【会话中断】中断失败：会话不存在或已完成，用户ID：{}，会话ID：{}", userId, sessionId);
            return false;
        }

        // 验证用户权限
        if (!sessionInfo.getUserId().equals(userId)) {
            log.warn("【会话中断】中断失败：用户权限不匹配，请求用户ID：{}，会话用户ID：{}，会话ID：{}",
                    userId, sessionInfo.getUserId(), sessionId);
            return false;
        }

        // 设置中断标志
        interruptFlags.put(sessionId, true);

        log.info("【会话中断】中断成功，用户ID：{}，会话ID：{}", userId, sessionId);
        return true;
    }
    
    @Override
    public boolean isInterrupted(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return interruptFlags.getOrDefault(sessionId, false);
    }
    
    @Override
    public void markSessionCompleted(String sessionId) {
        if (sessionId == null) {
            return;
        }

        SessionInfo sessionInfo = activeSessions.remove(sessionId);
        interruptFlags.remove(sessionId);

        if (sessionInfo != null) {
            log.info("【会话管理】会话完成并清理，用户ID：{}，会话ID：{}，剩余活跃会话数：{}",
                    sessionInfo.getUserId(), sessionId, activeSessions.size());
            log.debug("【会话管理】会话持续时间：{}分钟",
                    java.time.Duration.between(sessionInfo.getStartTime(), LocalDateTime.now()).toMinutes());
        } else {
            log.debug("【会话管理】会话已不存在，会话ID：{}", sessionId);
        }
    }

    @Override
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    @Override
    public void cleanupTimeoutSessions() {
        log.debug("【会话清理】开始清理超时会话，当前活跃会话数：{}", activeSessions.size());
        int cleanedCount = 0;

        for (String sessionId : activeSessions.keySet()) {
            SessionInfo sessionInfo = activeSessions.get(sessionId);
            if (sessionInfo != null && sessionInfo.isTimeout()) {
                activeSessions.remove(sessionId);
                interruptFlags.remove(sessionId);
                cleanedCount++;

                log.debug("【会话清理】清理超时会话，用户ID：{}，会话ID：{}，开始时间：{}",
                        sessionInfo.getUserId(), sessionId, sessionInfo.getStartTime());
            }
        }

        if (cleanedCount > 0) {
            log.info("【会话清理】清理超时会话完成，清理数量：{}，剩余活跃会话数：{}", cleanedCount, activeSessions.size());
        } else {
            log.debug("【会话清理】无超时会话需要清理");
        }
    }

    @Override
    public boolean hasActiveSession(Long userId) {
        if (userId == null) {
            return false;
        }

        // 遍历所有活跃会话，检查是否有该用户的会话
        for (SessionInfo sessionInfo : activeSessions.values()) {
            if (sessionInfo != null && userId.equals(sessionInfo.getUserId())) {
                // 检查会话是否超时，如果超时则不算活跃
                if (!sessionInfo.isTimeout()) {
                    log.debug("用户{}已有活跃会话", userId);
                    return true;
                }
            }
        }

        log.debug("用户{}没有活跃会话", userId);
        return false;
    }
}
