package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.ChatInterruptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天中断服务测试类
 * 测试中断逻辑对活跃会话检查的影响
 * 
 * @author instser
 * @since 2025-07-27
 */
class ChatInterruptServiceTest {

    private ChatInterruptService chatInterruptService;

    @BeforeEach
    void setUp() {
        chatInterruptService = new ChatInterruptServiceImpl();
    }

    @Test
    @DisplayName("测试用户无活跃会话时的检查")
    void testHasActiveSession_NoActiveSession() {
        Long userId = 1L;
        
        // 用户没有任何会话时，应该返回false
        assertFalse(chatInterruptService.hasActiveSession(userId));
    }

    @Test
    @DisplayName("测试用户有正常活跃会话时的检查")
    void testHasActiveSession_WithActiveSession() {
        Long userId = 1L;
        String sessionId = "test-session-1";
        
        // 注册活跃会话
        chatInterruptService.registerActiveSession(sessionId, userId);
        
        // 应该检测到活跃会话
        assertTrue(chatInterruptService.hasActiveSession(userId));
    }

    @Test
    @DisplayName("测试用户会话被中断后的活跃会话检查")
    void testHasActiveSession_WithInterruptedSession() {
        Long userId = 1L;
        String sessionId = "test-session-2";
        
        // 注册活跃会话
        chatInterruptService.registerActiveSession(sessionId, userId);
        
        // 验证会话是活跃的
        assertTrue(chatInterruptService.hasActiveSession(userId));
        
        // 中断会话
        boolean interruptResult = chatInterruptService.interruptChat(sessionId, userId);
        assertTrue(interruptResult, "中断操作应该成功");
        
        // 中断后，应该不再检测到活跃会话（这是修复的关键点）
        assertFalse(chatInterruptService.hasActiveSession(userId), 
                   "中断后的会话不应该被认为是活跃会话");
    }

    @Test
    @DisplayName("测试用户有多个会话，部分中断的情况")
    void testHasActiveSession_MultipleSessionsPartiallyInterrupted() {
        Long userId = 1L;
        String sessionId1 = "test-session-3";
        String sessionId2 = "test-session-4";

        // 注册两个活跃会话
        chatInterruptService.registerActiveSession(sessionId1, userId);
        chatInterruptService.registerActiveSession(sessionId2, userId);

        // 应该检测到活跃会话
        assertTrue(chatInterruptService.hasActiveSession(userId));

        // 中断第一个会话（会话会被立即移除）
        chatInterruptService.interruptChat(sessionId1, userId);

        // 仍然应该检测到活跃会话（因为第二个会话还活跃）
        assertTrue(chatInterruptService.hasActiveSession(userId));

        // 中断第二个会话（会话会被立即移除）
        chatInterruptService.interruptChat(sessionId2, userId);

        // 现在应该没有活跃会话了
        assertFalse(chatInterruptService.hasActiveSession(userId));
    }

    @Test
    @DisplayName("测试不同用户的会话互不影响")
    void testHasActiveSession_DifferentUsers() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        String sessionId1 = "test-session-5";
        String sessionId2 = "test-session-6";
        
        // 为两个用户分别注册会话
        chatInterruptService.registerActiveSession(sessionId1, userId1);
        chatInterruptService.registerActiveSession(sessionId2, userId2);
        
        // 两个用户都应该有活跃会话
        assertTrue(chatInterruptService.hasActiveSession(userId1));
        assertTrue(chatInterruptService.hasActiveSession(userId2));
        
        // 中断用户1的会话
        chatInterruptService.interruptChat(sessionId1, userId1);
        
        // 用户1不应该有活跃会话，但用户2仍然有
        assertFalse(chatInterruptService.hasActiveSession(userId1));
        assertTrue(chatInterruptService.hasActiveSession(userId2));
    }

    @Test
    @DisplayName("测试会话完成后的清理")
    void testHasActiveSession_AfterSessionCompleted() {
        Long userId = 1L;
        String sessionId = "test-session-7";
        
        // 注册活跃会话
        chatInterruptService.registerActiveSession(sessionId, userId);
        assertTrue(chatInterruptService.hasActiveSession(userId));
        
        // 标记会话完成
        chatInterruptService.markSessionCompleted(sessionId);
        
        // 完成后应该没有活跃会话
        assertFalse(chatInterruptService.hasActiveSession(userId));
    }

    @Test
    @DisplayName("测试空用户ID的处理")
    void testHasActiveSession_NullUserId() {
        // 空用户ID应该返回false
        assertFalse(chatInterruptService.hasActiveSession(null));
    }
}
