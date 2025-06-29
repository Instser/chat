package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import com.instser.chat.service.ChatMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI聊天错误处理简单测试类
 * 专注于测试核心的错误处理逻辑
 */
@ExtendWith(MockitoExtension.class)
class ChatErrorHandlingSimpleTest {

    @Mock
    private ChatMessageService chatMessageService;

    @InjectMocks
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        // 基础设置
    }

    /**
     * 测试错误响应保存功能 - 正常情况
     */
    @Test
    void testSaveErrorResponse_Success() {
        // 测试正常保存
        chatService.saveErrorResponse("test-session", 1L, 1L, "Request failed. Please try again later.");
        
        // 验证调用
        verify(chatMessageService).sendMessage(
            eq("test-session"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );
    }

    /**
     * 测试错误响应保存功能 - sessionId为null
     */
    @Test
    void testSaveErrorResponse_NullSessionId() {
        // 测试sessionId为null的情况
        chatService.saveErrorResponse(null, 1L, 1L, "error");
        
        // 验证没有调用sendMessage
        verify(chatMessageService, never()).sendMessage(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    /**
     * 测试错误响应保存功能 - userId为null
     */
    @Test
    void testSaveErrorResponse_NullUserId() {
        // 测试userId为null的情况
        chatService.saveErrorResponse("test-session", null, 1L, "error");
        
        // 验证没有调用sendMessage
        verify(chatMessageService, never()).sendMessage(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    /**
     * 测试错误响应保存功能 - modelId为null
     */
    @Test
    void testSaveErrorResponse_NullModelId() {
        // 测试modelId为null的情况
        chatService.saveErrorResponse("test-session", 1L, null, "error");
        
        // 验证没有调用sendMessage
        verify(chatMessageService, never()).sendMessage(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    /**
     * 测试错误响应保存功能 - 多次调用
     */
    @Test
    void testSaveErrorResponse_MultipleCalls() {
        // 测试多次保存
        chatService.saveErrorResponse("session-1", 1L, 1L, "Request failed. Please try again later.");
        chatService.saveErrorResponse("session-2", 2L, 2L, "Request failed. Please try again later.");
        
        // 验证调用次数
        verify(chatMessageService, times(2)).sendMessage(anyString(), eq("assistant"), eq("Request failed. Please try again later."), anyLong(), anyLong());
        
        // 验证具体调用
        verify(chatMessageService).sendMessage("session-1", "assistant", "Request failed. Please try again later.", 1L, 1L);
        verify(chatMessageService).sendMessage("session-2", "assistant", "Request failed. Please try again later.", 2L, 2L);
    }

    /**
     * 测试错误响应保存功能 - 异常处理
     */
    @Test
    void testSaveErrorResponse_ExceptionHandling() {
        // 模拟sendMessage抛出异常
        doThrow(new RuntimeException("Database error")).when(chatMessageService)
            .sendMessage(anyString(), anyString(), anyString(), anyLong(), anyLong());
        
        // 测试异常不会传播
        chatService.saveErrorResponse("test-session", 1L, 1L, "Request failed. Please try again later.");
        
        // 验证调用了sendMessage
        verify(chatMessageService).sendMessage("test-session", "assistant", "Request failed. Please try again later.", 1L, 1L);
    }

    /**
     * 测试错误响应保存功能 - 不同错误内容
     */
    @Test
    void testSaveErrorResponse_DifferentErrorContent() {
        String customError = "Custom error message";
        
        // 测试自定义错误内容
        chatService.saveErrorResponse("test-session", 1L, 1L, customError);
        
        // 验证调用
        verify(chatMessageService).sendMessage(
            eq("test-session"), 
            eq("assistant"), 
            eq(customError), 
            eq(1L), 
            eq(1L)
        );
    }

    /**
     * 测试错误响应保存功能 - 不同模型ID
     */
    @Test
    void testSaveErrorResponse_DifferentModelIds() {
        // 测试不同模型ID
        chatService.saveErrorResponse("test-session", 1L, 5L, "Request failed. Please try again later.");
        
        // 验证调用
        verify(chatMessageService).sendMessage(
            eq("test-session"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(5L)
        );
    }

    /**
     * 测试错误响应保存功能 - 长会话ID
     */
    @Test
    void testSaveErrorResponse_LongSessionId() {
        String longSessionId = "very-long-session-id-with-many-characters-12345678901234567890";
        
        // 测试长会话ID
        chatService.saveErrorResponse(longSessionId, 1L, 1L, "Request failed. Please try again later.");
        
        // 验证调用
        verify(chatMessageService).sendMessage(
            eq(longSessionId), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );
    }

    /**
     * 测试错误响应保存功能 - 空字符串参数
     */
    @Test
    void testSaveErrorResponse_EmptyStringParameters() {
        // 测试空字符串sessionId（应该被当作有效值）
        chatService.saveErrorResponse("", 1L, 1L, "Request failed. Please try again later.");
        
        // 验证调用
        verify(chatMessageService).sendMessage(
            eq(""), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );
    }
}
