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
            log.warn("注册活跃会话失败：sessionId或userId为空");
            return;
        }
        
        activeSessions.put(sessionId, new SessionInfo(userId));
        interruptFlags.put(sessionId, false);
        
        log.info("注册活跃聊天会话：sessionId={}, userId={}, 当前活跃会话数={}", 
                sessionId, userId, activeSessions.size());
    }
    
    @Override
    public boolean interruptChat(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            log.warn("中断聊天失败：sessionId或userId为空");
            return false;
        }
        
        SessionInfo sessionInfo = activeSessions.get(sessionId);
        if (sessionInfo == null) {
            log.warn("中断聊天失败：会话不存在或已完成，sessionId={}", sessionId);
            return false;
        }
        
        // 验证用户权限
        if (!sessionInfo.getUserId().equals(userId)) {
            log.warn("中断聊天失败：用户权限不匹配，sessionId={}, requestUserId={}, sessionUserId={}", 
                    sessionId, userId, sessionInfo.getUserId());
            return false;
        }
        
        // 设置中断标志
        interruptFlags.put(sessionId, true);
        
        log.info("用户{}成功中断聊天会话：sessionId={}", userId, sessionId);
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
            log.info("聊天会话已完成并清理：sessionId={}, userId={}, 剩余活跃会话数={}", 
                    sessionId, sessionInfo.getUserId(), activeSessions.size());
        }
    }
    
    @Override
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    @Override
    public void cleanupTimeoutSessions() {
        int cleanedCount = 0;
        
        for (String sessionId : activeSessions.keySet()) {
            SessionInfo sessionInfo = activeSessions.get(sessionId);
            if (sessionInfo != null && sessionInfo.isTimeout()) {
                activeSessions.remove(sessionId);
                interruptFlags.remove(sessionId);
                cleanedCount++;
                
                log.info("清理超时会话：sessionId={}, userId={}, 开始时间={}", 
                        sessionId, sessionInfo.getUserId(), sessionInfo.getStartTime());
            }
        }
        
        if (cleanedCount > 0) {
            log.info("清理超时会话完成：清理数量={}, 剩余活跃会话数={}", cleanedCount, activeSessions.size());
        }
    }
}
