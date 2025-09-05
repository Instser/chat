package com.instser.chat.integration;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.cache.RequestValidationCache;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import com.instser.chat.vo.ChatLimitVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 系统集成测试
 * 验证优化后的系统在各种场景下的正确性
 * 
 * @author instser
 * @since 2025-09-05
 */
public class ChatOptimizationIntegrationTest {

    @Mock
    private ChatLimitService chatLimitService;
    
    @Mock
    private AiModelService aiModelService;
    
    private ChatServiceImpl chatService;
    private RequestValidationCache realCache;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        chatService = new ChatServiceImpl(
            null, null, aiModelService, chatLimitService, null, null, null, 
            null, null, null, null, null, null
        );
        
        realCache = new RequestValidationCache();
    }

    @Test
    @DisplayName("集成测试：缓存命中减少重复调用")
    void testCacheHitReducesDuplicateCalls() {
        // 安排：启用真实缓存
        ReflectionTestUtils.setField(chatService, "requestValidationCache", realCache);
        
        ChatLimitVO mockLimitVO = new ChatLimitVO();
        mockLimitVO.setCanChat(true);
        mockLimitVO.setPackageName("test-package");
        mockLimitVO.setLimitType("daily");
        
        AiModel mockModel = new AiModel();
        mockModel.setId(1L);
        mockModel.setModelCode("test-model");
        
        ChatLimitVO mockModelLimitVO = new ChatLimitVO();
        mockModelLimitVO.setCanChat(true);
        
        when(chatLimitService.validateChatPermission(12345L)).thenReturn(mockLimitVO);
        when(aiModelService.getById(1L)).thenReturn(mockModel);
        when(chatLimitService.validateModelChatPermission(12345L, "1")).thenReturn(mockModelLimitVO);
        
        // 执行：多次调用相同的验证
        String result1 = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", 12345L, "session1", 1L
        );
        
        String result2 = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", 12345L, "session2", 1L
        );
        
        // 验证：两次调用结果一致
        assertNull(result1);
        assertNull(result2);
        
        // 验证：由于缓存，服务只被调用一次
        verify(chatLimitService, times(1)).validateChatPermission(12345L);
        verify(aiModelService, times(1)).getById(1L);
        verify(chatLimitService, times(1)).validateModelChatPermission(12345L, "1");
        
        // 验证缓存统计
        assertEquals(3, realCache.getStats().getSize(), "应该缓存了3个条目");
    }

    @Test
    @DisplayName("集成测试：缓存不影响不同用户的验证")
    void testCacheDoesNotAffectDifferentUsers() {
        // 安排：启用真实缓存
        ReflectionTestUtils.setField(chatService, "requestValidationCache", realCache);
        
        // 用户1的权限
        ChatLimitVO user1LimitVO = new ChatLimitVO();
        user1LimitVO.setCanChat(true);
        user1LimitVO.setPackageName("package1");
        
        // 用户2的权限
        ChatLimitVO user2LimitVO = new ChatLimitVO();
        user2LimitVO.setCanChat(false);
        user2LimitVO.setLimitReason("PACKAGE_EXPIRED");
        
        when(chatLimitService.validateChatPermission(12345L)).thenReturn(user1LimitVO);
        when(chatLimitService.validateChatPermission(67890L)).thenReturn(user2LimitVO);
        
        // 执行：验证两个不同用户
        String result1 = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", 12345L, "session1", 1L
        );
        
        String result2 = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", 67890L, "session2", 1L
        );
        
        // 验证：不同用户得到不同结果
        assertNull(result1, "用户1应该验证通过");
        assertEquals("PACKAGE_EXPIRED", result2, "用户2应该验证失败");
        
        // 验证：每个用户的权限都被检查了
        verify(chatLimitService, times(1)).validateChatPermission(12345L);
        verify(chatLimitService, times(1)).validateChatPermission(67890L);
    }

    @Test
    @DisplayName("集成测试：缓存自动清理功能")
    void testCacheAutoCleanup() {
        // 安排：使用真实缓存并添加一些数据
        realCache.put("key1", "value1");
        realCache.put("key2", "value2");
        assertEquals(2, realCache.getStats().getSize());
        
        // 执行：模拟Spring的@PreDestroy调用
        realCache.cleanup();
        
        // 验证：缓存已清理
        assertEquals(0, realCache.getStats().getSize());
        assertNull(realCache.get("key1"));
        assertNull(realCache.get("key2"));
    }

    @Test
    @DisplayName("集成测试：系统在缓存异常时的健壮性")
    void testSystemRobustnessWithCacheException() {
        // 安排：创建会抛出异常的缓存mock
        RequestValidationCache faultyCache = mock(RequestValidationCache.class);
        when(faultyCache.getOrCompute(anyString(), any())).thenThrow(new RuntimeException("缓存异常"));
        
        ReflectionTestUtils.setField(chatService, "requestValidationCache", faultyCache);
        
        ChatLimitVO mockLimitVO = new ChatLimitVO();
        mockLimitVO.setCanChat(true);
        when(chatLimitService.validateChatPermission(12345L)).thenReturn(mockLimitVO);
        
        // 执行：即使缓存异常，系统应该能够处理
        String result = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", 12345L, "session1", 1L
        );
        
        // 验证：系统应该返回通用错误消息，而不是崩溃
        assertEquals("Request failed. Please try again later.", result);
    }

    @Test
    @DisplayName("性能测试：验证缓存确实减少了服务调用")
    void testPerformanceImprovementWithCache() {
        // 安排：启用真实缓存
        ReflectionTestUtils.setField(chatService, "requestValidationCache", realCache);
        
        ChatLimitVO mockLimitVO = new ChatLimitVO();
        mockLimitVO.setCanChat(true);
        when(chatLimitService.validateChatPermission(12345L)).thenReturn(mockLimitVO);
        
        AiModel mockModel = new AiModel();
        mockModel.setId(1L);
        when(aiModelService.getById(1L)).thenReturn(mockModel);
        
        ChatLimitVO mockModelLimitVO = new ChatLimitVO();
        mockModelLimitVO.setCanChat(true);
        when(chatLimitService.validateModelChatPermission(12345L, "1")).thenReturn(mockModelLimitVO);
        
        // 执行：连续10次相同的验证请求
        for (int i = 0; i < 10; i++) {
            String result = (String) ReflectionTestUtils.invokeMethod(
                chatService, "validateChatPermissionOptimized", 12345L, "session" + i, 1L
            );
            assertNull(result);
        }
        
        // 验证：尽管调用了10次，但由于缓存，服务只被调用了一次
        verify(chatLimitService, times(1)).validateChatPermission(12345L);
        verify(aiModelService, times(1)).getById(1L);
        verify(chatLimitService, times(1)).validateModelChatPermission(12345L, "1");
        
        // 验证缓存大小
        assertEquals(3, realCache.getStats().getSize());
    }
}