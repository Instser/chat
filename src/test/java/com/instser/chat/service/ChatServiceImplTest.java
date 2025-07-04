package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatServiceImpl测试类
 * 专门测试续写完成后标题生成功能
 *
 * @author Augment Agent
 * @since 2025-07-04
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
public class ChatServiceImplTest {

    @InjectMocks
    private ChatServiceImpl chatService;

    @Mock
    private TruncationDetectionService truncationDetectionService;

    @Mock
    private Executor continuationExecutor;

    private MockHttpServletResponse response;
    private StringWriter responseWriter;
    private ChatRequestDTO request;
    private AiModel model;

    @BeforeEach
    void setUp() throws IOException {
        // 设置Mock响应
        response = new MockHttpServletResponse();
        responseWriter = new StringWriter();

        // 设置测试请求
        request = new ChatRequestDTO();
        request.setSessionId("test-session-123");
        request.setUserId(1L);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "测试消息");
        messages.add(userMessage);
        request.setMessages(messages);

        // 设置测试模型
        model = new AiModel();
        model.setId(1L);
        model.setModelName("test-model");
        model.setModelCode("test-model-code");
        model.setBaseUrl("http://test.api.com");
        model.setApiKey("test-api-key");
    }

    /**
     * 测试续写成功且需要生成标题的场景
     */
    @Test
    void testHandleStreamCompletion_ContinuationSuccessWithTitleGeneration() throws Exception {
        log.info("测试续写成功且需要生成标题的场景");

        // 模拟截断检测返回false（需要续写）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(false);

        // 模拟续写成功
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = mockStatic(CompletableFuture.class)) {
            CompletableFuture<Boolean> mockContinuationFuture = mock(CompletableFuture.class);
            when(mockContinuationFuture.join()).thenReturn(true); // 续写成功
            
            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any(), eq(continuationExecutor)))
                    .thenReturn(mockContinuationFuture);

            // 模拟标题生成
            @SuppressWarnings("unchecked")
            CompletableFuture<String> mockTitleFuture = mock(CompletableFuture.class);
            when(mockTitleFuture.get(5, TimeUnit.SECONDS)).thenReturn("测试生成的标题");
            
            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any()))
                    .thenReturn(mockTitleFuture);

            // 调用私有方法进行测试
            Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                    "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                    ChatRequestDTO.class, String.class, AiModel.class, String.class);
            handleStreamCompletionMethod.setAccessible(true);

            boolean result = (boolean) handleStreamCompletionMethod.invoke(
                    chatService, response, true, true, request, "测试AI回复内容", model, "chat");

            // 验证结果
            assertTrue(result, "续写成功应该返回true");
            
            // 验证响应内容包含标题
            String responseContent = responseWriter.toString();
            assertTrue(responseContent.contains("测试生成的标题"), "响应应该包含生成的标题");
            
            log.info("测试通过：续写成功且标题生成正常");
        }
    }

    /**
     * 测试续写成功但不需要生成标题的场景
     */
    @Test
    void testHandleStreamCompletion_ContinuationSuccessWithoutTitleGeneration() throws Exception {
        log.info("测试续写成功但不需要生成标题的场景");

        // 模拟截断检测返回false（需要续写）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(false);

        // 模拟续写成功
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = mockStatic(CompletableFuture.class)) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> mockContinuationFuture = mock(CompletableFuture.class);
            when(mockContinuationFuture.join()).thenReturn(true); // 续写成功
            
            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any(), eq(continuationExecutor)))
                    .thenReturn(mockContinuationFuture);

            // 调用私有方法进行测试（willGenerateTitle = false）
            Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                    "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                    ChatRequestDTO.class, String.class, AiModel.class, String.class);
            handleStreamCompletionMethod.setAccessible(true);

            boolean result = (boolean) handleStreamCompletionMethod.invoke(
                    chatService, response, false, true, request, "测试AI回复内容", model, "chat");

            // 验证结果
            assertTrue(result, "续写成功应该返回true");
            
            // 验证响应内容不包含标题
            String responseContent = responseWriter.toString();
            assertFalse(responseContent.contains("title"), "响应不应该包含标题字段");
            
            log.info("测试通过：续写成功且正确跳过标题生成");
        }
    }

    /**
     * 测试续写成功但标题生成超时的场景
     */
    @Test
    void testHandleStreamCompletion_ContinuationSuccessWithTitleTimeout() throws Exception {
        log.info("测试续写成功但标题生成超时的场景");

        // 模拟截断检测返回false（需要续写）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(false);

        // 模拟续写成功
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = mockStatic(CompletableFuture.class)) {
            CompletableFuture<Boolean> mockContinuationFuture = mock(CompletableFuture.class);
            when(mockContinuationFuture.join()).thenReturn(true); // 续写成功
            
            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any(), eq(continuationExecutor)))
                    .thenReturn(mockContinuationFuture);

            // 模拟标题生成超时
            CompletableFuture<String> mockTitleFuture = mock(CompletableFuture.class);
            when(mockTitleFuture.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException("标题生成超时"));
            
            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any()))
                    .thenReturn(mockTitleFuture);

            // 调用私有方法进行测试
            Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                    "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                    ChatRequestDTO.class, String.class, AiModel.class, String.class);
            handleStreamCompletionMethod.setAccessible(true);

            boolean result = (boolean) handleStreamCompletionMethod.invoke(
                    chatService, response, true, true, request, "测试AI回复内容", model, "chat");

            // 验证结果
            assertTrue(result, "续写成功应该返回true");
            
            // 验证响应内容包含完成标记但不包含标题
            String responseContent = responseWriter.toString();
            assertTrue(responseContent.contains("data:"), "响应应该包含完成标记");
            assertFalse(responseContent.contains("title"), "响应不应该包含标题字段");
            
            log.info("测试通过：续写成功且正确处理标题生成超时");
        }
    }

    /**
     * 测试续写失败的场景
     */
    @Test
    void testHandleStreamCompletion_ContinuationFailure() throws Exception {
        log.info("测试续写失败的场景");

        // 模拟截断检测返回false（需要续写）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(false);

        // 模拟续写失败
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = mockStatic(CompletableFuture.class)) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> mockContinuationFuture = mock(CompletableFuture.class);
            when(mockContinuationFuture.join()).thenReturn(false); // 续写失败
            
            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any(), eq(continuationExecutor)))
                    .thenReturn(mockContinuationFuture);

            // 调用私有方法进行测试
            Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                    "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                    ChatRequestDTO.class, String.class, AiModel.class, String.class);
            handleStreamCompletionMethod.setAccessible(true);

            boolean result = (boolean) handleStreamCompletionMethod.invoke(
                    chatService, response, true, true, request, "测试AI回复内容", model, "chat");

            // 验证结果
            assertFalse(result, "续写失败应该返回false，继续正常流程");
            
            log.info("测试通过：续写失败正确返回false");
        }
    }

    /**
     * 测试续写成功但标题生成异常的场景
     */
    @Test
    void testHandleStreamCompletion_ContinuationSuccessWithTitleException() throws Exception {
        log.info("测试续写成功但标题生成异常的场景");

        // 模拟截断检测返回false（需要续写）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(false);

        // 模拟续写成功
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = mockStatic(CompletableFuture.class)) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> mockContinuationFuture = mock(CompletableFuture.class);
            when(mockContinuationFuture.join()).thenReturn(true); // 续写成功

            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any(), eq(continuationExecutor)))
                    .thenReturn(mockContinuationFuture);

            // 模拟标题生成异常
            CompletableFuture<String> mockTitleFuture = mock(CompletableFuture.class);
            when(mockTitleFuture.get(5, TimeUnit.SECONDS)).thenThrow(new RuntimeException("标题生成异常"));

            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any()))
                    .thenReturn(mockTitleFuture);

            // 调用私有方法进行测试
            Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                    "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                    ChatRequestDTO.class, String.class, AiModel.class, String.class);
            handleStreamCompletionMethod.setAccessible(true);

            boolean result = (boolean) handleStreamCompletionMethod.invoke(
                    chatService, response, true, true, request, "测试AI回复内容", model, "chat");

            // 验证结果
            assertTrue(result, "续写成功应该返回true");

            // 验证响应内容包含完成标记但不包含标题
            String responseContent = responseWriter.toString();
            assertTrue(responseContent.contains("data:"), "响应应该包含完成标记");
            assertFalse(responseContent.contains("title"), "响应不应该包含标题字段");

            log.info("测试通过：续写成功且正确处理标题生成异常");
        }
    }

    /**
     * 测试续写成功但标题生成返回默认标题的场景
     */
    @Test
    void testHandleStreamCompletion_ContinuationSuccessWithDefaultTitle() throws Exception {
        log.info("测试续写成功但标题生成返回默认标题的场景");

        // 模拟截断检测返回false（需要续写）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(false);

        // 模拟续写成功
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = mockStatic(CompletableFuture.class)) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> mockContinuationFuture = mock(CompletableFuture.class);
            when(mockContinuationFuture.join()).thenReturn(true); // 续写成功

            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any(), eq(continuationExecutor)))
                    .thenReturn(mockContinuationFuture);

            // 模拟标题生成返回默认标题
            CompletableFuture<String> mockTitleFuture = mock(CompletableFuture.class);
            when(mockTitleFuture.get(5, TimeUnit.SECONDS)).thenReturn("New Chat");

            mockedCompletableFuture.when(() -> CompletableFuture.supplyAsync(any()))
                    .thenReturn(mockTitleFuture);

            // 调用私有方法进行测试
            Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                    "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                    ChatRequestDTO.class, String.class, AiModel.class, String.class);
            handleStreamCompletionMethod.setAccessible(true);

            boolean result = (boolean) handleStreamCompletionMethod.invoke(
                    chatService, response, true, true, request, "测试AI回复内容", model, "chat");

            // 验证结果
            assertTrue(result, "续写成功应该返回true");

            // 验证响应内容包含完成标记但不包含标题（因为是默认标题）
            String responseContent = responseWriter.toString();
            assertTrue(responseContent.contains("data:"), "响应应该包含完成标记");
            assertFalse(responseContent.contains("New Chat"), "响应不应该包含默认标题");

            log.info("测试通过：续写成功且正确处理默认标题");
        }
    }

    /**
     * 测试不需要截断检测的场景
     */
    @Test
    void testHandleStreamCompletion_NoTruncationDetection() throws Exception {
        log.info("测试不需要截断检测的场景");

        // 模拟不需要截断检测
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(false);

        // 调用私有方法进行测试
        Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                ChatRequestDTO.class, String.class, AiModel.class, String.class);
        handleStreamCompletionMethod.setAccessible(true);

        boolean result = (boolean) handleStreamCompletionMethod.invoke(
                chatService, response, true, true, request, "测试AI回复内容", model, "chat");

        // 验证结果
        assertFalse(result, "不需要截断检测应该返回false，继续正常流程");

        log.info("测试通过：不需要截断检测正确返回false");
    }

    /**
     * 测试内容完整不需要续写的场景
     */
    @Test
    void testHandleStreamCompletion_ContentComplete() throws Exception {
        log.info("测试内容完整不需要续写的场景");

        // 模拟截断检测返回true（内容完整）
        when(truncationDetectionService.shouldApplyTruncationDetection(anyString())).thenReturn(true);
        when(truncationDetectionService.isContentComplete(anyString())).thenReturn(true);

        // 调用私有方法进行测试
        Method handleStreamCompletionMethod = ChatServiceImpl.class.getDeclaredMethod(
                "handleStreamCompletion", HttpServletResponse.class, boolean.class, boolean.class,
                ChatRequestDTO.class, String.class, AiModel.class, String.class);
        handleStreamCompletionMethod.setAccessible(true);

        boolean result = (boolean) handleStreamCompletionMethod.invoke(
                chatService, response, true, true, request, "测试AI回复内容", model, "chat");

        // 验证结果
        assertFalse(result, "内容完整不需要续写应该返回false，继续正常流程");

        log.info("测试通过：内容完整不需要续写正确返回false");
    }
}
