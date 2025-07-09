package com.instser.chat.util;

import com.instser.chat.service.ChatInterruptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatSessionManager单元测试
 * 验证聊天会话状态的自动管理功能
 * 
 * @author instser
 * @since 2025-07-09
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionManagerTest {

    @Mock
    private ChatInterruptService interruptService;

    private String sessionId = "session-12345";
    private Long userId = 12345L;

    @BeforeEach
    void setUp() {
        // 每个测试前重置mock
        reset(interruptService);
    }

    @Test
    void testConstructorWithValidParameters() {
        // 测试正常构造
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        
        assertNotNull(manager);
        assertEquals(sessionId, manager.getSessionId());
        assertEquals(userId, manager.getUserId());
        assertFalse(manager.isRegistered());
    }

    @Test
    void testConstructorWithNullService() {
        // 测试空服务参数
        assertThrows(IllegalArgumentException.class, () -> {
            new ChatSessionManager(null, sessionId, userId);
        });
    }

    @Test
    void testConstructorWithNullSessionId() {
        // 测试空会话ID参数（应该允许）
        ChatSessionManager manager = new ChatSessionManager(interruptService, null, userId);
        
        assertNotNull(manager);
        assertNull(manager.getSessionId());
        assertEquals(userId, manager.getUserId());
    }

    @Test
    void testConstructorWithNullUserId() {
        // 测试空用户ID参数（应该允许）
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, null);
        
        assertNotNull(manager);
        assertEquals(sessionId, manager.getSessionId());
        assertNull(manager.getUserId());
    }

    @Test
    void testRegisterSuccess() {
        // 测试成功注册会话
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        boolean result = manager.register();

        assertTrue(result);
        assertTrue(manager.isRegistered());
        verify(interruptService, times(1)).registerActiveSession(sessionId, userId);
    }

    @Test
    void testRegisterWithNullSessionId() {
        // 测试空会话ID时注册
        ChatSessionManager manager = new ChatSessionManager(interruptService, null, userId);
        boolean result = manager.register();

        assertFalse(result);
        assertFalse(manager.isRegistered());
        verify(interruptService, never()).registerActiveSession(any(), any());
    }

    @Test
    void testRegisterWithNullUserId() {
        // 测试空用户ID时注册
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, null);
        boolean result = manager.register();

        assertFalse(result);
        assertFalse(manager.isRegistered());
        verify(interruptService, never()).registerActiveSession(any(), any());
    }

    @Test
    void testRegisterException() {
        // 模拟注册时发生异常
        doThrow(new RuntimeException("注册失败")).when(interruptService).registerActiveSession(sessionId, userId);

        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        boolean result = manager.register();

        assertFalse(result);
        assertFalse(manager.isRegistered());
        verify(interruptService, times(1)).registerActiveSession(sessionId, userId);
    }

    @Test
    void testTryWithResourcesAutoClose() throws Exception {
        // 使用try-with-resources测试自动清理
        try (ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId)) {
            manager.register();
            assertTrue(manager.isRegistered());
        }

        // 验证清理方法被调用
        verify(interruptService, times(1)).registerActiveSession(sessionId, userId);
        verify(interruptService, times(1)).markSessionCompleted(sessionId);
    }

    @Test
    void testManualMarkCompleted() {
        // 测试手动标记完成
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        manager.register();
        assertTrue(manager.isRegistered());

        // 手动标记完成
        manager.markCompleted();
        assertFalse(manager.isRegistered());

        verify(interruptService, times(1)).registerActiveSession(sessionId, userId);
        verify(interruptService, times(1)).markSessionCompleted(sessionId);
    }

    @Test
    void testMarkCompletedWithoutRegister() {
        // 测试未注册时标记完成
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        
        // 应该不会调用清理方法
        manager.markCompleted();
        
        verify(interruptService, never()).markSessionCompleted(sessionId);
    }

    @Test
    void testDoubleRegister() {
        // 测试重复注册
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        manager.register();
        
        // 第二次注册应该直接返回true，不再调用服务
        boolean result = manager.register();
        assertTrue(result);
        
        // 验证只调用了一次注册方法
        verify(interruptService, times(1)).registerActiveSession(sessionId, userId);
    }

    @Test
    void testIsInterrupted() {
        // 模拟中断检查
        when(interruptService.isInterrupted(sessionId)).thenReturn(true);

        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        boolean result = manager.isInterrupted();

        assertTrue(result);
        verify(interruptService, times(1)).isInterrupted(sessionId);
    }

    @Test
    void testIsInterruptedWithNullSessionId() {
        // 测试空会话ID时检查中断
        ChatSessionManager manager = new ChatSessionManager(interruptService, null, userId);
        boolean result = manager.isInterrupted();

        assertFalse(result);
        verify(interruptService, never()).isInterrupted(any());
    }

    @Test
    void testIsInterruptedException() {
        // 模拟检查中断时发生异常
        when(interruptService.isInterrupted(sessionId)).thenThrow(new RuntimeException("检查失败"));

        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        boolean result = manager.isInterrupted();

        assertFalse(result);
        verify(interruptService, times(1)).isInterrupted(sessionId);
    }

    @Test
    void testToString() {
        ChatSessionManager manager = new ChatSessionManager(interruptService, sessionId, userId);
        String result = manager.toString();
        
        assertTrue(result.contains("ChatSessionManager"));
        assertTrue(result.contains(sessionId));
        assertTrue(result.contains(userId.toString()));
        assertTrue(result.contains("registered=false"));
    }
}
