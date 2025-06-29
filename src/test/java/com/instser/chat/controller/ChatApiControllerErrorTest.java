package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.service.ChatService;
import com.instser.chat.service.ChatInterruptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ChatApiController错误处理测试类
 * 测试控制器层的参数验证和错误处理
 */
@WebMvcTest(ChatApiController.class)
class ChatApiControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatInterruptService chatInterruptService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    /**
     * 测试重试接口的chatId参数验证
     */
    @Test
    @WithMockUser
    void testRetryWithEmptyChatId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "");
        request.put("messageId", "msg-123");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试重试接口的chatId参数为null
     */
    @Test
    @WithMockUser
    void testRetryWithNullChatId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", null);
        request.put("messageId", "msg-123");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试重试接口的messageId参数验证
     */
    @Test
    @WithMockUser
    void testRetryWithEmptyMessageId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "chat-123");
        request.put("messageId", "");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试重试接口的messageId参数为null
     */
    @Test
    @WithMockUser
    void testRetryWithNullMessageId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "chat-123");
        request.put("messageId", null);
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试编辑重发接口的chatId参数验证
     */
    @Test
    @WithMockUser
    void testEditResendWithEmptyChatId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "");
        request.put("messageId", "msg-123");
        request.put("newContent", "New content");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/edit-resend")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试编辑重发接口的messageId参数验证
     */
    @Test
    @WithMockUser
    void testEditResendWithEmptyMessageId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "chat-123");
        request.put("messageId", "");
        request.put("newContent", "New content");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/edit-resend")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试编辑重发接口的newContent参数验证
     */
    @Test
    @WithMockUser
    void testEditResendWithEmptyNewContent() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "chat-123");
        request.put("messageId", "msg-123");
        request.put("newContent", "");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/edit-resend")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试编辑重发接口的newContent参数为null
     */
    @Test
    @WithMockUser
    void testEditResendWithNullNewContent() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "chat-123");
        request.put("messageId", "msg-123");
        request.put("newContent", null);
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/edit-resend")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试modelId格式错误
     */
    @Test
    @WithMockUser
    void testRetryWithInvalidModelIdFormat() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "chat-123");
        request.put("messageId", "msg-123");
        request.put("modelId", "invalid-format");

        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("Request failed. Please try again later."));
    }

    /**
     * 测试响应头设置
     */
    @Test
    @WithMockUser
    void testErrorResponseHeaders() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "");
        request.put("messageId", "msg-123");
        request.put("modelId", 1);

        mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("Connection", "keep-alive"));
    }

    /**
     * 测试错误响应格式符合Vercel AI SDK规范
     */
    @Test
    @WithMockUser
    void testErrorResponseFormat() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", "");
        request.put("messageId", "msg-123");
        request.put("modelId", 1);

        MvcResult result = mockMvc.perform(post("/chat/retry")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        
        // 验证响应格式符合Vercel AI SDK规范
        assertTrue(responseContent.startsWith("3:\""));
        assertTrue(responseContent.contains("Request failed. Please try again later."));
        assertTrue(responseContent.endsWith("\"\n"));
    }
}
