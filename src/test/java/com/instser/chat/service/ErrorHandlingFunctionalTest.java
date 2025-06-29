package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
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
 * 错误处理功能验证测试
 * 专门验证我们修改的错误处理逻辑是否正确工作
 */
@ExtendWith(MockitoExtension.class)
class ErrorHandlingFunctionalTest {

    @Mock
    private AiModelService aiModelService;

    @Mock
    private ChatLimitService chatLimitService;

    @Mock
    private ChatMessageService chatMessageService;

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
     * 测试AI模型不存在时的错误处理
     * 验证：返回统一错误消息，保存到数据库，响应格式正确
     */
    @Test
    void testModelNotFoundErrorHandling() throws Exception {
        // 模拟AI模型不存在
        when(aiModelService.getById(1L)).thenReturn(null);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证返回结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.isHasContentSent());
        assertFalse(result.isRealAiResponse());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());

        // 验证错误响应保存到数据库（使用默认模型ID 1）
        verify(chatMessageService).sendMessage(
            eq("test-session-123"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(1L)
        );

        // 验证响应内容包含错误消息
        String responseContent = response.getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试AI模型不可用时的错误处理
     * 验证：返回统一错误消息，保存到数据库，使用实际模型ID
     */
    @Test
    void testModelUnavailableErrorHandling() throws Exception {
        // 模拟AI模型存在但不可用
        AiModel model = new AiModel();
        model.setId(2L);
        model.setIsAvailable(0); // 不可用
        when(aiModelService.getById(1L)).thenReturn(model);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证返回结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.isHasContentSent());
        assertFalse(result.isRealAiResponse());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());

        // 验证错误响应保存到数据库（使用实际模型ID）
        verify(chatMessageService).sendMessage(
            eq("test-session-123"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(2L)
        );
    }

    /**
     * 测试聊天权限验证失败时的错误处理
     * 验证：返回统一错误消息，保存到数据库，不暴露具体原因
     */
    @Test
    void testChatPermissionFailedErrorHandling() throws Exception {
        // 模拟AI模型可用
        AiModel model = new AiModel();
        model.setId(3L);
        model.setIsAvailable(1);
        when(aiModelService.getById(1L)).thenReturn(model);

        // 模拟聊天权限验证失败
        ChatLimitVO limitCheck = new ChatLimitVO();
        limitCheck.setCanChat(false);
        limitCheck.setLimitReason("用户套餐已过期");
        when(chatLimitService.validateChatPermission(1L)).thenReturn(limitCheck);

        // 执行聊天请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证返回结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.isHasContentSent());
        assertFalse(result.isRealAiResponse());
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());

        // 验证错误响应保存到数据库
        verify(chatMessageService).sendMessage(
            eq("test-session-123"), 
            eq("assistant"), 
            eq("Request failed. Please try again later."), 
            eq(1L), 
            eq(3L)
        );

        // 验证响应内容不包含具体错误原因
        String responseContent = response.getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
        assertFalse(responseContent.contains("用户套餐已过期"));
    }

    /**
     * 测试错误消息的一致性
     * 验证：不同错误场景都返回相同的错误消息
     */
    @Test
    void testErrorMessageConsistency() throws Exception {
        // 测试场景1：模型不存在
        when(aiModelService.getById(1L)).thenReturn(null);
        ChatResponseDTO result1 = chatService.processStreamChat(chatRequest, authentication, response);
        
        // 测试场景2：模型不可用
        response = new MockHttpServletResponse(); // 重置响应
        AiModel unavailableModel = new AiModel();
        unavailableModel.setId(1L);
        unavailableModel.setIsAvailable(0);
        when(aiModelService.getById(1L)).thenReturn(unavailableModel);
        ChatResponseDTO result2 = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证错误消息一致性
        assertEquals(result1.getAiResponseContent(), result2.getAiResponseContent());
        assertEquals("Request failed. Please try again later.", result1.getAiResponseContent());
        assertEquals("Request failed. Please try again later.", result2.getAiResponseContent());
    }

    /**
     * 测试错误响应不影响正常流程
     * 验证：错误处理不会抛出异常，不会中断程序执行
     */
    @Test
    void testErrorHandlingDoesNotThrowExceptions() {
        // 模拟各种异常情况
        when(aiModelService.getById(anyLong())).thenThrow(new RuntimeException("Database error"));

        // 验证不会抛出异常
        assertDoesNotThrow(() -> {
            ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);
            assertNotNull(result);
            assertFalse(result.isSuccess());
        });
    }

    /**
     * 测试saveErrorResponse方法的直接调用
     * 验证：方法能够正确处理各种参数组合
     */
    @Test
    void testSaveErrorResponseDirectCall() {
        // 测试正常调用
        chatService.saveErrorResponse("session-1", 1L, 1L, "Request failed. Please try again later.");
        verify(chatMessageService).sendMessage("session-1", "assistant", "Request failed. Please try again later.", 1L, 1L);

        // 测试null参数（应该不调用sendMessage）
        reset(chatMessageService);
        chatService.saveErrorResponse(null, 1L, 1L, "error");
        chatService.saveErrorResponse("session", null, 1L, "error");
        chatService.saveErrorResponse("session", 1L, null, "error");
        verify(chatMessageService, never()).sendMessage(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    /**
     * 测试响应格式的正确性
     * 验证：错误响应遵循正确的格式
     */
    @Test
    void testErrorResponseFormat() throws Exception {
        // 模拟错误场景
        when(aiModelService.getById(1L)).thenReturn(null);

        // 执行请求
        ChatResponseDTO result = chatService.processStreamChat(chatRequest, authentication, response);

        // 验证响应格式
        assertNotNull(result);
        assertTrue(result.isHasContentSent()); // 有内容发送
        assertFalse(result.isRealAiResponse()); // 不是真实AI响应
        assertFalse(result.isSuccess()); // 请求失败
        assertFalse(result.isShouldRecordUsage()); // 不应记录使用次数
        assertEquals("Request failed. Please try again later.", result.getAiResponseContent());
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
