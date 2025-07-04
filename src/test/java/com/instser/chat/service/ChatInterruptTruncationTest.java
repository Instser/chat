package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.serviceimpl.ChatInterruptServiceImpl;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import com.instser.chat.service.serviceimpl.TruncationDetectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 聊天中断和截断检测优化测试
 * 
 * @author instser
 * @since 2025-07-04
 */
@ExtendWith(MockitoExtension.class)
public class ChatInterruptTruncationTest {

    @Mock
    private TruncationDetectionService truncationDetectionService;
    
    @Mock
    private ChatInterruptService chatInterruptService;
    
    @Mock
    private AiModelService aiModelService;
    
    @InjectMocks
    private ChatServiceImpl chatService;

    private ChatRequestDTO request;
    private AiModel model;
    private MockHttpServletResponse response;
    private StringBuilder aiResponseContent;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        request = new ChatRequestDTO();
        request.setSessionId("test-session-123");
        request.setUserId(1L);
        request.setMessages(new ArrayList<>());
        
        model = new AiModel();
        model.setId(1L);
        model.setModelName("test-model");
        
        response = new MockHttpServletResponse();
        aiResponseContent = new StringBuilder("这是一个测试AI响应内容，用于验证截断检测逻辑。");
    }

    @Test
    void testHandleStreamCompletion_WhenUserManuallyInterrupted_ShouldSkipTruncationDetection() throws Exception {
        // 模拟用户手动中断的情况
        when(truncationDetectionService.shouldApplyTruncationDetection("chat")).thenReturn(true);
        when(chatInterruptService.isInterrupted("test-session-123")).thenReturn(true);
        
        // 使用反射调用私有方法
        Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
            "handleStreamCompletion",
            HttpServletResponse.class,
            boolean.class,
            boolean.class,
            ChatRequestDTO.class,
            StringBuilder.class,
            AiModel.class,
            String.class
        );
        handleStreamCompletionMethod.setAccessible(true);
        
        // 执行方法
        handleStreamCompletionMethod.invoke(
            chatService, 
            response, 
            false, // willGenerateTitle
            true,  // isRealAiResponse
            request, 
            aiResponseContent, 
            model, 
            "chat"
        );
        
        // 验证：应该检查中断状态
        verify(chatInterruptService, times(1)).isInterrupted("test-session-123");
        
        // 验证：不应该调用截断检测
        verify(truncationDetectionService, never()).isContentComplete(anyString(), anyInt());
        verify(truncationDetectionService, never()).isContentComplete(anyString());
        
        // 验证：应该调用shouldApplyTruncationDetection来检查是否应用截断检测
        verify(truncationDetectionService, times(1)).shouldApplyTruncationDetection("chat");
    }

    @Test
    void testHandleStreamCompletion_WhenNormalCompletion_ShouldApplyTruncationDetection() throws Exception {
        // 模拟正常完成的情况
        when(truncationDetectionService.shouldApplyTruncationDetection("chat")).thenReturn(true);
        when(chatInterruptService.isInterrupted("test-session-123")).thenReturn(false);
        when(truncationDetectionService.isContentComplete(anyString(), anyInt())).thenReturn(true);
        
        // 使用反射调用私有方法
        Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
            "handleStreamCompletion",
            HttpServletResponse.class,
            boolean.class,
            boolean.class,
            ChatRequestDTO.class,
            StringBuilder.class,
            AiModel.class,
            String.class
        );
        handleStreamCompletionMethod.setAccessible(true);
        
        // 执行方法
        handleStreamCompletionMethod.invoke(
            chatService, 
            response, 
            false, // willGenerateTitle
            true,  // isRealAiResponse
            request, 
            aiResponseContent, 
            model, 
            "chat"
        );
        
        // 验证：应该检查中断状态
        verify(chatInterruptService, times(1)).isInterrupted("test-session-123");
        
        // 验证：应该调用截断检测
        verify(truncationDetectionService, times(1)).isContentComplete(anyString(), anyInt());
        
        // 验证：应该调用shouldApplyTruncationDetection来检查是否应用截断检测
        verify(truncationDetectionService, times(1)).shouldApplyTruncationDetection("chat");
    }

    @Test
    void testHandleStreamCompletion_WhenSessionIdIsNull_ShouldNotCheckInterrupt() throws Exception {
        // 模拟sessionId为null的情况
        request.setSessionId(null);
        when(truncationDetectionService.shouldApplyTruncationDetection("chat")).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString(), anyInt())).thenReturn(true);
        
        // 使用反射调用私有方法
        Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
            "handleStreamCompletion",
            HttpServletResponse.class,
            boolean.class,
            boolean.class,
            ChatRequestDTO.class,
            StringBuilder.class,
            AiModel.class,
            String.class
        );
        handleStreamCompletionMethod.setAccessible(true);
        
        // 执行方法
        handleStreamCompletionMethod.invoke(
            chatService, 
            response, 
            false, // willGenerateTitle
            true,  // isRealAiResponse
            request, 
            aiResponseContent, 
            model, 
            "chat"
        );
        
        // 验证：不应该检查中断状态（因为sessionId为null）
        verify(chatInterruptService, never()).isInterrupted(any());
        
        // 验证：应该正常调用截断检测
        verify(truncationDetectionService, times(1)).isContentComplete(anyString(), anyInt());
    }

    @Test
    void testHandleStreamCompletion_WhenInterfaceTypeNotSupported_ShouldSkipTruncationDetection() throws Exception {
        // 模拟不支持截断检测的接口类型
        when(truncationDetectionService.shouldApplyTruncationDetection("unsupported")).thenReturn(false);
        when(chatInterruptService.isInterrupted("test-session-123")).thenReturn(false);
        
        // 使用反射调用私有方法
        Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
            "handleStreamCompletion",
            HttpServletResponse.class,
            boolean.class,
            boolean.class,
            ChatRequestDTO.class,
            StringBuilder.class,
            AiModel.class,
            String.class
        );
        handleStreamCompletionMethod.setAccessible(true);
        
        // 执行方法
        handleStreamCompletionMethod.invoke(
            chatService, 
            response, 
            false, // willGenerateTitle
            true,  // isRealAiResponse
            request, 
            aiResponseContent, 
            model, 
            "unsupported"
        );
        
        // 验证：应该检查中断状态
        verify(chatInterruptService, times(1)).isInterrupted("test-session-123");
        
        // 验证：不应该调用截断检测（因为接口类型不支持）
        verify(truncationDetectionService, never()).isContentComplete(anyString(), anyInt());
        verify(truncationDetectionService, never()).isContentComplete(anyString());
        
        // 验证：应该调用shouldApplyTruncationDetection来检查是否应用截断检测
        verify(truncationDetectionService, times(1)).shouldApplyTruncationDetection("unsupported");
    }
}
