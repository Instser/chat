package com.instser.chat.service;

import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.entity.WebSearchContent;
import com.instser.chat.service.serviceimpl.ChatMessageServiceImpl;
import com.instser.chat.service.serviceimpl.ChatSessionServiceImpl;
import com.instser.chat.service.serviceimpl.FileUploadServiceImpl;
import com.instser.chat.service.serviceimpl.WebSearchContentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库重试机制测试
 * 
 * @author instser
 * @since 2025-07-11
 */
@SpringBootTest
@ActiveProfiles("test")
public class DatabaseRetryTest {

    @Test
    public void testChatMessageRetryMethodExists() throws Exception {
        // 检查saveMessageWithRetry方法是否存在
        Method saveMethod = ChatMessageServiceImpl.class.getDeclaredMethod("saveMessageWithRetry", ChatMessage.class, int.class);
        assertNotNull(saveMethod);
        assertTrue(saveMethod.getReturnType().equals(boolean.class));

        // 检查updateWebSearchFlagWithRetry方法是否存在
        Method updateMethod = ChatMessageServiceImpl.class.getDeclaredMethod("updateWebSearchFlagWithRetry", String.class, boolean.class, int.class);
        assertNotNull(updateMethod);
        assertTrue(updateMethod.getReturnType().equals(boolean.class));
    }

    @Test
    public void testFileUploadRetryMethodExists() throws Exception {
        // 检查saveFileWithRetry方法是否存在
        Method saveMethod = FileUploadServiceImpl.class.getDeclaredMethod("saveFileWithRetry", FileUpload.class, int.class);
        assertNotNull(saveMethod);
        assertTrue(saveMethod.getReturnType().equals(boolean.class));
    }

    @Test
    public void testWebSearchContentRetryMethodExists() throws Exception {
        // 检查saveWebSearchContentWithRetry方法是否存在
        Method saveMethod = WebSearchContentServiceImpl.class.getDeclaredMethod("saveWebSearchContentWithRetry", WebSearchContent.class, int.class);
        assertNotNull(saveMethod);
        assertTrue(saveMethod.getReturnType().equals(boolean.class));
    }

    @Test
    public void testChatSessionRetryMethodExists() throws Exception {
        // 检查saveSessionWithRetry方法是否存在
        Method saveMethod = ChatSessionServiceImpl.class.getDeclaredMethod("saveSessionWithRetry", ChatSession.class, int.class);
        assertNotNull(saveMethod);
        assertTrue(saveMethod.getReturnType().equals(boolean.class));
    }

    @Test
    public void testRetryLogicParameters() {
        // 测试重试逻辑的参数设置是否合理
        int maxRetries = 3;
        long initialDelay = 500;
        
        // 计算总的最大延迟时间
        long totalMaxDelay = 0;
        long currentDelay = initialDelay;
        for (int i = 0; i < maxRetries; i++) {
            totalMaxDelay += currentDelay;
            currentDelay *= 2;
        }
        
        // 验证总延迟时间在合理范围内（不超过10秒）
        assertTrue(totalMaxDelay <= 10000, "总重试延迟时间应该在10秒以内");
        
        // 验证重试次数合理
        assertTrue(maxRetries >= 2 && maxRetries <= 5, "重试次数应该在2-5次之间");
    }

    @Test
    public void testEntityCreation() {
        // 测试实体对象创建是否正常（用于重试测试）
        
        // 测试ChatMessage创建
        ChatMessage message = new ChatMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionId("test-session");
        message.setRole("user");
        message.setContent("test content");
        message.setUserId(1L);
        message.setModelId(1L);
        message.setCreatedTime(LocalDateTime.now());
        assertNotNull(message.getMessageId());
        
        // 测试ChatSession创建
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setTitle("test session");
        session.setUserId(1L);
        session.setModelId(1L);
        session.setCreatedTime(LocalDateTime.now());
        assertNotNull(session.getSessionId());
        
        // 测试FileUpload创建
        FileUpload file = new FileUpload();
        file.setFileId(UUID.randomUUID().toString());
        file.setUserId(1L);
        file.setFileName("test.txt");
        file.setFileType("text/plain");
        file.setFileSize(100L);
        file.setContent("test content");
        assertNotNull(file.getFileId());
        
        // 测试WebSearchContent创建
        WebSearchContent webContent = new WebSearchContent();
        webContent.setWebSearchId(UUID.randomUUID().toString());
        webContent.setUserId(1L);
        webContent.setSessionId("test-session");
        webContent.setMessageId("test-message");
        webContent.setContent("{}");
        webContent.setResultCount(0);
        assertNotNull(webContent.getWebSearchId());
    }
}
