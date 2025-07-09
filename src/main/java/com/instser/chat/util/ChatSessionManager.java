package com.instser.chat.util;

import com.instser.chat.service.ChatInterruptService;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天会话管理器
 * 实现AutoCloseable接口，提供自动资源管理功能
 * 确保聊天会话状态在异常情况下正确清理
 * 
 * @author instser
 * @since 2025-07-09
 */
@Slf4j
public class ChatSessionManager implements AutoCloseable {
    
    private final ChatInterruptService interruptService;
    private final String sessionId;
    private final Long userId;
    private boolean registered = false;
    
    /**
     * 构造函数
     * 
     * @param interruptService 聊天中断服务
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public ChatSessionManager(ChatInterruptService interruptService, String sessionId, Long userId) {
        this.interruptService = interruptService;
        this.sessionId = sessionId;
        this.userId = userId;
        
        if (interruptService == null) {
            throw new IllegalArgumentException("ChatInterruptService不能为空");
        }
        
        log.debug("创建聊天会话管理器，用户ID: {}, 会话ID: {}", userId, sessionId);
    }
    
    /**
     * 注册活跃会话
     * 如果sessionId和userId都不为空，则注册会话
     * 
     * @return 是否成功注册会话
     */
    public boolean register() {
        if (registered) {
            log.warn("聊天会话已注册，不能重复注册，用户ID: {}, 会话ID: {}", userId, sessionId);
            return true;
        }
        
        if (sessionId != null && userId != null) {
            try {
                interruptService.registerActiveSession(sessionId, userId);
                registered = true;
                log.info("成功注册聊天会话，用户ID: {}, 会话ID: {}", userId, sessionId);
                return true;
            } catch (Exception e) {
                log.error("注册聊天会话异常，用户ID: {}, 会话ID: {}", userId, sessionId, e);
                return false;
            }
        } else {
            log.debug("会话ID或用户ID为空，跳过会话注册，用户ID: {}, 会话ID: {}", userId, sessionId);
            return false;
        }
    }
    
    /**
     * 检查是否已注册会话
     * 
     * @return 是否已注册会话
     */
    public boolean isRegistered() {
        return registered;
    }
    
    /**
     * 手动标记会话完成
     * 通常不需要手动调用，close()方法会自动处理
     */
    public void markCompleted() {
        if (registered && sessionId != null) {
            try {
                interruptService.markSessionCompleted(sessionId);
                registered = false;
                log.info("手动标记聊天会话完成，用户ID: {}, 会话ID: {}", userId, sessionId);
            } catch (Exception e) {
                log.error("手动标记聊天会话完成异常，用户ID: {}, 会话ID: {}", userId, sessionId, e);
            }
        } else {
            log.debug("聊天会话未注册或会话ID为空，无需标记完成，用户ID: {}, 会话ID: {}", userId, sessionId);
        }
    }
    
    /**
     * 自动清理资源
     * 实现AutoCloseable接口，支持try-with-resources语法
     */
    @Override
    public void close() {
        if (registered && sessionId != null) {
            try {
                interruptService.markSessionCompleted(sessionId);
                registered = false;
                log.info("聊天会话已自动清理，用户ID: {}, 会话ID: {}", userId, sessionId);
            } catch (Exception e) {
                log.error("自动清理聊天会话异常，用户ID: {}, 会话ID: {}", userId, sessionId, e);
                // 即使清理失败，也要标记为未注册状态，避免重复清理
                registered = false;
            }
        } else {
            log.debug("聊天会话未注册或会话ID为空，无需自动清理，用户ID: {}, 会话ID: {}", userId, sessionId);
        }
    }
    
    /**
     * 获取会话ID
     * 
     * @return 会话ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public Long getUserId() {
        return userId;
    }
    
    /**
     * 检查会话是否被中断
     * 
     * @return 是否被中断
     */
    public boolean isInterrupted() {
        if (sessionId == null) {
            return false;
        }
        try {
            return interruptService.isInterrupted(sessionId);
        } catch (Exception e) {
            log.error("检查会话中断状态异常，会话ID: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * 重写toString方法，便于调试
     */
    @Override
    public String toString() {
        return String.format("ChatSessionManager{userId=%d, sessionId='%s', registered=%s}", 
                           userId, sessionId, registered);
    }
}
