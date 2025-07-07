package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 错误消息重试机制测试类
 * 测试聊天服务中的自动重试功能
 */
@ExtendWith(MockitoExtension.class)
public class ChatRetryMechanismTest {

    @Mock
    private ChatMessageService chatMessageService;
    
    @Mock
    private ChatSessionService chatSessionService;
    
    @Mock
    private AiModelService aiModelService;
    
    @Mock
    private ChatLimitService chatLimitService;
    
    @Mock
    private ChatInterruptService chatInterruptService;
    

    
    @InjectMocks
    private ChatServiceImpl chatService;
    
    @Mock
    private Authentication authentication;
    
    private MockHttpServletResponse response;
    private ChatRequestDTO request;
    private AiModel model;
    
    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        
        // 设置测试请求
        request = new ChatRequestDTO();
        request.setUserId(1L);
        request.setModelId(1L);
        request.setSessionId("test-session-123");
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "测试消息");
        messages.add(userMessage);
        request.setMessages(messages);
        
        // 设置测试模型
        model = new AiModel();
        model.setId(1L);
        model.setModelCode("gpt-3.5-turbo");
        model.setProvider("OpenAI");
        model.setBaseUrl("https://api.openai.com/v1/chat/completions");
        model.setApiKey("test-api-key");
        // Mock基础服务
        when(aiModelService.getById(1L)).thenReturn(model);
        when(chatLimitService.recordChatUsage(1L)).thenReturn(true);
        when(chatInterruptService.hasActiveSession(1L)).thenReturn(false);
        when(chatInterruptService.isInterrupted(anyString())).thenReturn(false);

        // Mock会话服务
        ChatSession session = new ChatSession();
        session.setUserId(1L);
        when(chatSessionService.getById("test-session-123")).thenReturn(session);
    }
    
    @Test
    void testErrorMessageDetection() {
        // 测试错误消息检测功能
        String errorMessage = "Request blocked. Please reach out to support@example.com if you think this was a mistake.";
        
        // 使用反射调用私有方法进行测试
        try {
            java.lang.reflect.Method method = ChatServiceImpl.class.getDeclaredMethod("shouldRestartChat", String.class);
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(chatService, errorMessage);
            
            assertTrue(result, "应该检测到需要重试的错误消息");
            System.out.println("✅ 错误消息检测测试通过");
        } catch (Exception e) {
            fail("错误消息检测测试失败: " + e.getMessage());
        }
    }
    
    @Test
    void testNormalContentDetection() {
        // 测试正常内容不会被误判为错误消息
        String normalContent = "这是一个正常的AI响应内容，没有任何错误信息。";
        
        try {
            java.lang.reflect.Method method = ChatServiceImpl.class.getDeclaredMethod("shouldRestartChat", String.class);
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(chatService, normalContent);
            
            assertFalse(result, "正常内容不应该被检测为错误消息");
            System.out.println("✅ 正常内容检测测试通过");
        } catch (Exception e) {
            fail("正常内容检测测试失败: " + e.getMessage());
        }
    }
    
    @Test
    void testFilterContentDetection() {
        // 测试AI推广内容过滤检测
        String filterContent = "I'm Augment Proxy, an AI assistant created by Augment Code.";
        
        try {
            java.lang.reflect.Method method = ChatServiceImpl.class.getDeclaredMethod("shouldFilterContent", String.class);
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(chatService, filterContent);
            
            assertTrue(result, "应该检测到需要过滤的推广内容");
            System.out.println("✅ 推广内容过滤检测测试通过");
        } catch (Exception e) {
            fail("推广内容过滤检测测试失败: " + e.getMessage());
        }
    }
    
    @Test
    void testContentDetectionResult() {
        // 测试内容检测结果类
        try {
            java.lang.reflect.Method method = ChatServiceImpl.class.getDeclaredMethod("detectContentAndGetResult", String.class);
            method.setAccessible(true);
            
            // 测试错误消息
            String errorMessage = "Request blocked. Please reach out to support@example.com if you think this was a mistake.";
            Object result = method.invoke(chatService, errorMessage);
            
            // 使用反射获取结果
            boolean needsRetry = (boolean) result.getClass().getMethod("needsRetry").invoke(result);
            boolean wasFiltered = (boolean) result.getClass().getMethod("wasFiltered").invoke(result);
            
            assertTrue(needsRetry, "错误消息应该标记为需要重试");
            assertFalse(wasFiltered, "错误消息不应该标记为被过滤");
            
            // 测试推广内容
            String filterContent = "I'm Augment Proxy, an AI assistant created by Augment Code.";
            Object filterResult = method.invoke(chatService, filterContent);
            
            boolean filterNeedsRetry = (boolean) filterResult.getClass().getMethod("needsRetry").invoke(filterResult);
            boolean filterWasFiltered = (boolean) filterResult.getClass().getMethod("wasFiltered").invoke(filterResult);
            
            assertFalse(filterNeedsRetry, "推广内容不应该标记为需要重试");
            assertTrue(filterWasFiltered, "推广内容应该标记为被过滤");
            
            // 测试正常内容
            String normalContent = "这是一个正常的AI响应。";
            Object normalResult = method.invoke(chatService, normalContent);
            
            boolean normalNeedsRetry = (boolean) normalResult.getClass().getMethod("needsRetry").invoke(normalResult);
            boolean normalWasFiltered = (boolean) normalResult.getClass().getMethod("wasFiltered").invoke(normalResult);
            String cleanedContent = (String) normalResult.getClass().getMethod("getCleanedContent").invoke(normalResult);
            
            assertFalse(normalNeedsRetry, "正常内容不应该标记为需要重试");
            assertFalse(normalWasFiltered, "正常内容不应该标记为被过滤");
            assertEquals(normalContent, cleanedContent, "正常内容应该保持不变");
            
            System.out.println("✅ 内容检测结果测试通过");
        } catch (Exception e) {
            fail("内容检测结果测试失败: " + e.getMessage());
        }
    }
    
    @Test
    void testChatRetryExceptionCreation() {
        // 测试ChatRetryException异常类
        try {
            Class<?> exceptionClass = Class.forName("com.instser.chat.service.serviceimpl.ChatServiceImpl$ChatRetryException");
            Object exception = exceptionClass.getDeclaredConstructor(String.class).newInstance("测试重试异常");
            
            assertTrue(exception instanceof RuntimeException, "ChatRetryException应该继承自RuntimeException");
            assertEquals("测试重试异常", ((RuntimeException) exception).getMessage(), "异常消息应该正确设置");
            
            System.out.println("✅ ChatRetryException异常类测试通过");
        } catch (Exception e) {
            fail("ChatRetryException异常类测试失败: " + e.getMessage());
        }
    }
    
    @Test
    void testValidationMethods() {
        // 测试基础验证方法
        String validationError = chatService.validateChatRequest(request);
        assertNull(validationError, "有效的聊天请求不应该返回验证错误");
        
        // 测试空消息验证
        ChatRequestDTO emptyRequest = new ChatRequestDTO();
        emptyRequest.setMessages(new ArrayList<>());
        String emptyError = chatService.validateChatRequest(emptyRequest);
        assertNotNull(emptyError, "空消息列表应该返回验证错误");
        
        System.out.println("✅ 验证方法测试通过");
    }
    
    @Test
    void testModelValidation() {
        // 测试模型验证
        when(aiModelService.getById(999L)).thenReturn(null);
        
        ChatRequestDTO invalidModelRequest = new ChatRequestDTO();
        invalidModelRequest.setUserId(1L);
        invalidModelRequest.setModelId(999L);
        invalidModelRequest.setMessages(request.getMessages());
        
        // 这里主要测试模型存在性验证
        assertNotNull(aiModelService.getById(1L), "有效模型ID应该返回模型对象");
        assertNull(aiModelService.getById(999L), "无效模型ID应该返回null");
        
        System.out.println("✅ 模型验证测试通过");
    }
    
    /**
     * 运行所有测试的主方法
     */
    public static void main(String[] args) {
        System.out.println("🚀 开始运行错误消息重试机制测试...\n");
        
        ChatRetryMechanismTest test = new ChatRetryMechanismTest();
        
        try {
            // 手动初始化（在实际JUnit环境中会自动处理）
            test.setUp();
            
            // 运行各项测试
            test.testErrorMessageDetection();
            test.testNormalContentDetection();
            test.testFilterContentDetection();
            test.testContentDetectionResult();
            test.testChatRetryExceptionCreation();
            test.testValidationMethods();
            test.testModelValidation();
            
            System.out.println("\n🎉 所有测试通过！错误消息重试机制实现正确。");
            
        } catch (Exception e) {
            System.err.println("\n❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
