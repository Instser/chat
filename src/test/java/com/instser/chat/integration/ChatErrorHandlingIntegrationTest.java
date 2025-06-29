package com.instser.chat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI聊天错误处理集成测试
 * 测试完整的错误处理流程，包括数据库保存
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class ChatErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatSessionService chatSessionService;

    @MockBean
    private AiModelService aiModelService;

    @MockBean
    private UserAccountService userAccountService;

    private ObjectMapper objectMapper;
    private String testSessionId;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // 创建测试会话
        testSessionId = chatSessionService.createSession(testUserId, 1L);
        
        // 模拟用户账户
        UserAccount userAccount = new UserAccount();
        userAccount.setUserId(testUserId);
        userAccount.setPackageId(1L);
        when(userAccountService.getUserAccount(testUserId)).thenReturn(userAccount);
    }

    /**
     * 测试AI模型不可用时的完整错误处理流程
     */
    @Test
    @WithMockUser(username = "testuser")
    void testCompleteErrorHandlingFlowForUnavailableModel() throws Exception {
        // 模拟AI模型不可用
        when(aiModelService.getById(1L)).thenReturn(null);

        // 构建聊天请求
        Map<String, Object> chatRequest = createChatRequest();

        // 发送聊天请求
        MvcResult result = mockMvc.perform(post("/chat")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        // 验证响应内容
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));

        // 验证错误消息已保存到数据库
        List<ChatMessage> messages = chatMessageService.getSessionMessages(testSessionId);
        
        // 应该有用户消息和AI错误响应
        assertEquals(2, messages.size());
        
        // 验证用户消息
        ChatMessage userMessage = messages.get(0);
        assertEquals("user", userMessage.getRole());
        assertEquals("Hello, this is a test message", userMessage.getContent());
        
        // 验证AI错误响应
        ChatMessage aiErrorMessage = messages.get(1);
        assertEquals("assistant", aiErrorMessage.getRole());
        assertEquals("Request failed. Please try again later.", aiErrorMessage.getContent());
        assertEquals(testUserId, aiErrorMessage.getUserId());
        assertEquals(Long.valueOf(1L), aiErrorMessage.getModelId()); // 默认模型ID
    }

    /**
     * 测试重试功能的错误处理和数据库保存
     */
    @Test
    @WithMockUser(username = "testuser")
    void testRetryErrorHandlingWithDatabaseSave() throws Exception {
        // 先创建一个用户消息和AI响应
        Long userMessageId = chatMessageService.sendMessage(testSessionId, "user", "Original message", testUserId, 1L);
        Long aiMessageId = chatMessageService.sendMessage(testSessionId, "assistant", "Original AI response", testUserId, 1L);

        // 模拟AI模型不可用
        when(aiModelService.getById(1L)).thenReturn(null);

        // 构建重试请求
        Map<String, Object> retryRequest = new HashMap<>();
        retryRequest.put("chatId", testSessionId);
        retryRequest.put("messageId", aiMessageId.toString());
        retryRequest.put("modelId", 1);

        // 发送重试请求
        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(retryRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        // 验证响应内容
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));

        // 验证错误消息已保存到数据库
        List<ChatMessage> messages = chatMessageService.getSessionMessages(testSessionId);
        
        // 应该有原始用户消息和新的AI错误响应（原始AI响应被删除）
        assertTrue(messages.size() >= 2);
        
        // 查找最新的AI消息
        ChatMessage latestAiMessage = messages.stream()
                .filter(msg -> "assistant".equals(msg.getRole()))
                .reduce((first, second) -> second) // 获取最后一个
                .orElse(null);
        
        assertNotNull(latestAiMessage);
        assertEquals("Request failed. Please try again later.", latestAiMessage.getContent());
        assertEquals(testUserId, latestAiMessage.getUserId());
    }

    /**
     * 测试编辑重发功能的错误处理和数据库保存
     */
    @Test
    @WithMockUser(username = "testuser")
    void testEditResendErrorHandlingWithDatabaseSave() throws Exception {
        // 先创建一个用户消息
        Long userMessageId = chatMessageService.sendMessage(testSessionId, "user", "Original message", testUserId, 1L);

        // 模拟AI模型不可用
        when(aiModelService.getById(1L)).thenReturn(null);

        // 构建编辑重发请求
        Map<String, Object> editResendRequest = new HashMap<>();
        editResendRequest.put("chatId", testSessionId);
        editResendRequest.put("messageId", userMessageId.toString());
        editResendRequest.put("newContent", "Edited message content");
        editResendRequest.put("modelId", 1);

        // 发送编辑重发请求
        MvcResult result = mockMvc.perform(post("/chat/edit-resend")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editResendRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        // 验证响应内容
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));

        // 验证错误消息已保存到数据库
        List<ChatMessage> messages = chatMessageService.getSessionMessages(testSessionId);
        
        // 应该有编辑后的用户消息和AI错误响应
        assertTrue(messages.size() >= 2);
        
        // 查找最新的AI消息
        ChatMessage latestAiMessage = messages.stream()
                .filter(msg -> "assistant".equals(msg.getRole()))
                .reduce((first, second) -> second) // 获取最后一个
                .orElse(null);
        
        assertNotNull(latestAiMessage);
        assertEquals("Request failed. Please try again later.", latestAiMessage.getContent());
        assertEquals(testUserId, latestAiMessage.getUserId());
    }

    /**
     * 测试参数验证错误的响应格式
     */
    @Test
    @WithMockUser(username = "testuser")
    void testParameterValidationErrorResponseFormat() throws Exception {
        // 构建无效的重试请求（空chatId）
        Map<String, Object> invalidRequest = new HashMap<>();
        invalidRequest.put("chatId", "");
        invalidRequest.put("messageId", "msg-123");
        invalidRequest.put("modelId", 1);

        // 发送请求
        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("Connection", "keep-alive"))
                .andReturn();

        // 验证响应格式符合Vercel AI SDK规范
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.startsWith("3:\""));
        assertTrue(responseContent.contains("Request failed. Please try again later."));
        assertTrue(responseContent.endsWith("\"\n"));
    }

    /**
     * 测试错误消息的一致性
     */
    @Test
    @WithMockUser(username = "testuser")
    void testErrorMessageConsistency() throws Exception {
        // 模拟AI模型不可用
        when(aiModelService.getById(1L)).thenReturn(null);

        // 测试聊天接口
        Map<String, Object> chatRequest = createChatRequest();
        MvcResult chatResult = mockMvc.perform(post("/chat")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 测试重试接口
        Map<String, Object> retryRequest = new HashMap<>();
        retryRequest.put("chatId", testSessionId);
        retryRequest.put("messageId", "msg-123");
        retryRequest.put("modelId", 1);
        
        MvcResult retryResult = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(retryRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 验证所有接口返回相同的错误消息
        String chatResponse = chatResult.getResponse().getContentAsString();
        String retryResponse = retryResult.getResponse().getContentAsString();
        
        assertTrue(chatResponse.contains("Request failed. Please try again later."));
        assertTrue(retryResponse.contains("Request failed. Please try again later."));
    }

    /**
     * 创建测试聊天请求的辅助方法
     */
    private Map<String, Object> createChatRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", testUserId);
        request.put("modelId", 1);
        request.put("sessionId", testSessionId);
        
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "Hello, this is a test message");
        
        request.put("messages", Arrays.asList(message));
        
        return request;
    }
}
