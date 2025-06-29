package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.vo.ChatLimitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI聊天错误处理测试类
 * 测试各种错误场景下的错误处理机制
 */
@ExtendWith(MockitoExtension.class)
class ChatErrorHandlingTest {

    @Mock
    private AiModelService aiModelService;

    @Mock
    private ChatLimitService chatLimitService;

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatInterruptService chatInterruptService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatServiceImpl chatService;

    private MockHttpServletResponse response;
    private ChatRequestDTO chatRequest;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        
        // 构建测试用的聊天请求
        chatRequest = new ChatRequestDTO();
        chatRequest.setUserId(1L);
        chatRequest.setModelId(1L);
        chatRequest.setSessionId("test-session-123");
        
        List<Map<String, String>> messages = Arrays.asList(
            createMessage("user", "Hello, this is a test message")
        );
        chatRequest.setMessages(messages);
    }

    /**
     * 测试AI模型不可用错误处理
     */
    @Test
    void testAiModelUnavailableError() throws Exception {
        // 模拟AI模型不存在
        when(aiModelService.getById(1L)).thenReturn(null);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证结果
        assertFalse(result.isSuccess());
        assertFalse(result.isRealAiResponse());
        assertTrue(result.isHasContentSent());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());

        // 验证错误响应保存到数据库
        verify(chatMessageService).sendMessage(
            eq("test-session-123"),
            eq("assistant"),
            eq("Request failed. Please try again later."),
            eq(1L),
            eq(1L)
        );

        // 验证响应内容
        String responseContent = response.getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试AI模型不可用错误处理（模型存在但不可用）
     */
    @Test
    void testAiModelNotAvailableError() throws Exception {
        // 模拟AI模型存在但不可用
        AiModel model = new AiModel();
        model.setId(1L);
        model.setIsAvailable(0); // 不可用
        when(aiModelService.getById(1L)).thenReturn(model);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证结果
        assertFalse(result.isSuccess());
        assertFalse(result.isRealAiResponse());
        assertTrue(result.isHasContentSent());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());

        // 验证错误响应保存到数据库，使用实际模型ID
        verify(chatMessageService).sendMessage(
            eq("test-session-123"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );
    }

    /**
     * 测试聊天权限验证失败错误处理
     */
    @Test
    void testChatPermissionFailedError() throws Exception {
        // 模拟AI模型可用
        AiModel model = new AiModel();
        model.setId(1L);
        model.setIsAvailable(1);
        when(aiModelService.getById(1L)).thenReturn(model);

        // 模拟聊天权限验证失败
        ChatLimitVO limitCheck = new ChatLimitVO();
        limitCheck.setCanChat(false);
        limitCheck.setLimitReason("用户套餐已过期");
        when(chatLimitService.validateChatPermission(1L)).thenReturn(limitCheck);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证结果
        assertFalse(result.isSuccess());
        assertFalse(result.isRealAiResponse());
        assertTrue(result.isHasContentSent());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());

        // 验证错误响应保存到数据库
        verify(chatMessageService).sendMessage(
            eq("test-session-123"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );
    }

    /**
     * 测试聊天权限验证失败错误处理（无具体原因）
     */
    @Test
    void testChatPermissionFailedWithoutReasonError() throws Exception {
        // 模拟AI模型可用
        AiModel model = new AiModel();
        model.setId(1L);
        model.setIsAvailable(1);
        when(aiModelService.getById(1L)).thenReturn(model);

        // 模拟聊天权限验证失败，无具体原因
        ChatLimitVO limitCheck = new ChatLimitVO();
        limitCheck.setCanChat(false);
        limitCheck.setLimitReason(null);
        when(chatLimitService.validateChatPermission(1L)).thenReturn(limitCheck);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证结果
        assertFalse(result.isSuccess());
        assertFalse(result.isRealAiResponse());
        assertTrue(result.isHasContentSent());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());
    }

    /**
     * 测试重试功能中的AI模型不可用错误
     */
    @Test
    void testRetryWithModelUnavailableError() throws Exception {
        // 模拟会话存在
        ChatSession chatSession = new ChatSession();
        chatSession.setUserId(1L);
        chatSession.setModelId(1L);
        when(chatSessionService.getSessionInfo("test-session-123")).thenReturn(chatSession);

        // 模拟AI模型不存在
        when(aiModelService.getById(1L)).thenReturn(null);

        // 模拟用户认证
        when(authentication.isAuthenticated()).thenReturn(true);

        // 执行重试请求
        chatService.processRetryChat("test-session-123", "msg-123", 1L, authentication, response);

        // 验证错误响应保存到数据库
        verify(chatMessageService).sendMessage(
            eq("test-session-123"),
            eq("assistant"),
            eq("Request failed. Please try again later."),
            eq(1L),
            eq(1L)
        );

        // 验证响应内容
        String responseContent = response.getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试编辑重发功能中的AI模型不可用错误
     */
    @Test
    void testEditResendWithModelUnavailableError() throws Exception {
        // 模拟会话存在
        ChatSession chatSession = new ChatSession();
        chatSession.setUserId(1L);
        chatSession.setModelId(1L);
        when(chatSessionService.getSessionInfo("test-session-123")).thenReturn(chatSession);

        // 模拟AI模型不存在
        when(aiModelService.getById(1L)).thenReturn(null);

        // 模拟用户认证
        when(authentication.isAuthenticated()).thenReturn(true);

        // 执行编辑重发请求
        chatService.processEditResendChat("test-session-123", "msg-123", "New content", null, 1L, authentication, response);

        // 验证错误响应保存到数据库
        verify(chatMessageService).sendMessage(
            eq("test-session-123"),
            eq("assistant"),
            eq("Request failed. Please try again later."),
            eq(1L),
            eq(1L)
        );

        // 验证响应内容
        String responseContent = response.getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试请求验证失败
     */
    @Test
    void testRequestValidationError() {
        // 测试空消息列表
        chatRequest.setMessages(null);
        String error = chatService.validateChatRequest(chatRequest);
        assertEquals("消息列表不能为空", error);

        // 测试空用户ID
        chatRequest.setMessages(Arrays.asList(createMessage("user", "test")));
        chatRequest.setUserId(null);
        error = chatService.validateChatRequest(chatRequest);
        assertEquals("用户ID不能为空", error);

        // 测试空模型ID
        chatRequest.setUserId(1L);
        chatRequest.setModelId(null);
        error = chatService.validateChatRequest(chatRequest);
        assertEquals("AI模型ID不能为空", error);

        // 测试最后一条消息不是用户消息
        chatRequest.setModelId(1L);
        chatRequest.setMessages(Arrays.asList(createMessage("assistant", "test")));
        error = chatService.validateChatRequest(chatRequest);
        assertEquals("最后一条消息必须是用户消息", error);
    }

    /**
     * 测试用户权限验证失败
     */
    @Test
    void testUserPermissionValidationError() {
        // 测试未认证用户
        String error = chatService.validateUserPermission(null, 1L);
        assertEquals("用户未认证", error);

        // 测试未认证的Authentication
        when(authentication.isAuthenticated()).thenReturn(false);
        error = chatService.validateUserPermission(authentication, 1L);
        assertEquals("用户未认证", error);

        // 测试认证通过
        when(authentication.isAuthenticated()).thenReturn(true);
        error = chatService.validateUserPermission(authentication, 1L);
        assertNull(error);
    }

    /**
     * 测试错误响应保存功能
     */
    @Test
    void testSaveErrorResponse() {
        // 测试正常保存
        chatService.saveErrorResponse("test-session", 1L, 1L, "Request failed. Please try again later.");
        
        verify(chatMessageService).sendMessage(
            eq("test-session"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );

        // 测试参数为null的情况
        chatService.saveErrorResponse(null, 1L, 1L, "error");
        chatService.saveErrorResponse("test-session", null, 1L, "error");
        chatService.saveErrorResponse("test-session", 1L, null, "error");
        
        // 验证只调用了一次（第一次正常调用）
        verify(chatMessageService, times(1)).sendMessage(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    /**
     * 创建测试消息的辅助方法
     */
    private Map<String, String> createMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
}
