package com.instser.chat.service;

/**
 * 聊天中断服务接口
 * 
 * @author instser
 * @since 2025-06-26
 */
public interface ChatInterruptService {
    
    /**
     * 注册活跃的聊天会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    void registerActiveSession(String sessionId, Long userId);
    
    /**
     * 中断指定会话的聊天
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 是否成功中断
     */
    boolean interruptChat(String sessionId, Long userId);
    
    /**
     * 检查会话是否被中断
     * 
     * @param sessionId 会话ID
     * @return 是否被中断
     */
    boolean isInterrupted(String sessionId);
    
    /**
     * 标记会话完成并清理
     * 
     * @param sessionId 会话ID
     */
    void markSessionCompleted(String sessionId);
    
    /**
     * 获取活跃会话数量
     * 
     * @return 活跃会话数量
     */
    int getActiveSessionCount();
    
    /**
     * 清理超时的会话（防止内存泄漏）
     */
    void cleanupTimeoutSessions();
}
