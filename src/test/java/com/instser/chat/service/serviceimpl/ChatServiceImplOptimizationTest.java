package com.instser.chat.service.serviceimpl;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.*;
import com.instser.chat.service.cache.RequestValidationCache;
import com.instser.chat.vo.ChatLimitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatServiceImpl 优化验证测试
 * 确保优化后的逻辑与原有逻辑完全一致
 * 
 * @author instser
 * @since 2025-09-05
 */
class ChatServiceImplOptimizationTest {

    @Mock
    private ChatLimitService chatLimitService;
    
    @Mock
    private AiModelService aiModelService;
    
    @Mock
    private RequestValidationCache requestValidationCache;
    
    private ChatServiceImpl chatService;
    
    // 测试数据
    private static final Long TEST_USER_ID = 12345L;
    private static final String TEST_SESSION_ID = "test-session-123";
    private static final Long TEST_MODEL_ID = 1L;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 创建ChatServiceImpl实例（只需要测试相关的依赖）
        chatService = new ChatServiceImpl(
            null, null, aiModelService, chatLimitService, null, null, null, 
            null, null, null, null, null, null
        );
        
        // 注入requestValidationCache
        ReflectionTestUtils.setField(chatService, "requestValidationCache", null);
    }

    @Test
    @DisplayName("测试原有逻辑完整性 - 缓存关闭状态")
    void testOriginalLogicIntegrity_CacheDisabled() {
        // 安排：缓存为null（关闭状态）
        ReflectionTestUtils.setField(chatService, "requestValidationCache", null);
        
        // 模拟chatLimitService.validateChatPermission返回成功
        ChatLimitVO mockLimitVO = new ChatLimitVO();
        mockLimitVO.setCanChat(true);
        mockLimitVO.setPackageName("test-package");
        mockLimitVO.setLimitType("daily");
        when(chatLimitService.validateChatPermission(TEST_USER_ID)).thenReturn(mockLimitVO);
        
        // 模拟aiModelService.getById返回模型
        AiModel mockModel = new AiModel();
        mockModel.setId(TEST_MODEL_ID);
        mockModel.setModelCode("test-model");
        when(aiModelService.getById(TEST_MODEL_ID)).thenReturn(mockModel);
        
        // 模拟模型权限检查
        ChatLimitVO mockModelLimitVO = new ChatLimitVO();
        mockModelLimitVO.setCanChat(true);
        when(chatLimitService.validateModelChatPermission(TEST_USER_ID, TEST_MODEL_ID.toString()))
            .thenReturn(mockModelLimitVO);
        
        // 执行：调用原有的验证方法
        String result = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermission", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 验证：结果应该为null（验证通过）
        assertNull(result, "原有逻辑应该验证通过");
        
        // 验证调用次数：应该直接调用服务，没有缓存
        verify(chatLimitService, times(1)).validateChatPermission(TEST_USER_ID);
        verify(aiModelService, times(1)).getById(TEST_MODEL_ID);
        verify(chatLimitService, times(1)).validateModelChatPermission(TEST_USER_ID, TEST_MODEL_ID.toString());
    }

    @Test
    @DisplayName("测试优化逻辑正确性 - 缓存开启状态")
    void testOptimizedLogicCorrectness_CacheEnabled() {
        // 安排：注入缓存实例
        RequestValidationCache mockCache = mock(RequestValidationCache.class);
        ReflectionTestUtils.setField(chatService, "requestValidationCache", mockCache);
        
        // 模拟缓存行为
        ChatLimitVO mockLimitVO = new ChatLimitVO();
        mockLimitVO.setCanChat(true);
        mockLimitVO.setPackageName("test-package");
        mockLimitVO.setLimitType("daily");
        
        AiModel mockModel = new AiModel();
        mockModel.setId(TEST_MODEL_ID);
        mockModel.setModelCode("test-model");
        
        ChatLimitVO mockModelLimitVO = new ChatLimitVO();
        mockModelLimitVO.setCanChat(true);
        
        // 模拟缓存的getOrCompute行为
        when(mockCache.getOrCompute(eq("chat_permission:" + TEST_USER_ID), any()))
            .thenAnswer(invocation -> {
                // 第一次调用，执行供应商函数
                return mockLimitVO;
            });
            
        when(mockCache.getOrCompute(eq("ai_model:" + TEST_MODEL_ID), any()))
            .thenAnswer(invocation -> {
                return mockModel;
            });
            
        when(mockCache.getOrCompute(eq("model_permission:" + TEST_USER_ID + ":" + TEST_MODEL_ID), any()))
            .thenAnswer(invocation -> {
                return mockModelLimitVO;
            });
        
        // 设置实际服务调用的返回值（供缓存的supplier使用）
        when(chatLimitService.validateChatPermission(TEST_USER_ID)).thenReturn(mockLimitVO);
        when(aiModelService.getById(TEST_MODEL_ID)).thenReturn(mockModel);
        when(chatLimitService.validateModelChatPermission(TEST_USER_ID, TEST_MODEL_ID.toString()))
            .thenReturn(mockModelLimitVO);
        
        // 执行：调用优化版本的验证方法
        String result = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 验证：结果应该为null（验证通过）
        assertNull(result, "优化逻辑应该验证通过");
        
        // 验证缓存调用：应该通过缓存调用服务
        verify(mockCache, times(1)).getOrCompute(eq("chat_permission:" + TEST_USER_ID), any());
        verify(mockCache, times(1)).getOrCompute(eq("ai_model:" + TEST_MODEL_ID), any());
        verify(mockCache, times(1)).getOrCompute(eq("model_permission:" + TEST_USER_ID + ":" + TEST_MODEL_ID), any());
    }

    @Test
    @DisplayName("测试错误场景一致性 - 套餐过期")
    void testErrorScenarioConsistency_PackageExpired() {
        // 准备失败的ChatLimitVO
        ChatLimitVO failedLimitVO = new ChatLimitVO();
        failedLimitVO.setCanChat(false);
        failedLimitVO.setLimitReason("PACKAGE_EXPIRED");
        
        when(chatLimitService.validateChatPermission(TEST_USER_ID)).thenReturn(failedLimitVO);
        
        // 测试原有逻辑
        ReflectionTestUtils.setField(chatService, "requestValidationCache", null);
        String originalResult = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermission", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 测试优化逻辑
        RequestValidationCache mockCache = mock(RequestValidationCache.class);
        when(mockCache.getOrCompute(eq("chat_permission:" + TEST_USER_ID), any()))
            .thenAnswer(invocation -> failedLimitVO);
        
        ReflectionTestUtils.setField(chatService, "requestValidationCache", mockCache);
        String optimizedResult = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 验证：两个结果应该完全一致
        assertEquals(originalResult, optimizedResult, "原有逻辑和优化逻辑的错误处理应该一致");
        assertEquals("PACKAGE_EXPIRED", originalResult, "应该返回正确的错误原因");
    }

    @Test
    @DisplayName("测试异常处理一致性")
    void testExceptionHandlingConsistency() {
        // 模拟服务调用抛出异常
        RuntimeException testException = new RuntimeException("测试异常");
        when(chatLimitService.validateChatPermission(TEST_USER_ID)).thenThrow(testException);
        
        // 测试原有逻辑的异常处理
        ReflectionTestUtils.setField(chatService, "requestValidationCache", null);
        String originalResult = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermission", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 测试优化逻辑的异常处理
        RequestValidationCache mockCache = mock(RequestValidationCache.class);
        when(mockCache.getOrCompute(eq("chat_permission:" + TEST_USER_ID), any()))
            .thenAnswer(invocation -> {
                // 执行supplier，应该抛出异常
                throw testException;
            });
        
        ReflectionTestUtils.setField(chatService, "requestValidationCache", mockCache);
        String optimizedResult = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 验证：两个结果应该一致
        assertEquals(originalResult, optimizedResult, "异常处理应该保持一致");
        assertEquals("Request failed. Please try again later.", originalResult, "应该返回通用错误消息");
    }

    @Test
    @DisplayName("测试缓存降级机制")
    void testCacheFallbackMechanism() {
        // 安排：设置缓存为null来模拟缓存不可用的情况
        ReflectionTestUtils.setField(chatService, "requestValidationCache", null);
        
        ChatLimitVO mockLimitVO = new ChatLimitVO();
        mockLimitVO.setCanChat(true);
        when(chatLimitService.validateChatPermission(TEST_USER_ID)).thenReturn(mockLimitVO);
        
        AiModel mockModel = new AiModel();
        mockModel.setId(TEST_MODEL_ID);
        when(aiModelService.getById(TEST_MODEL_ID)).thenReturn(mockModel);
        
        ChatLimitVO mockModelLimitVO = new ChatLimitVO();
        mockModelLimitVO.setCanChat(true);
        when(chatLimitService.validateModelChatPermission(TEST_USER_ID, TEST_MODEL_ID.toString()))
            .thenReturn(mockModelLimitVO);
        
        // 执行：调用优化版本，但缓存不可用
        String result = (String) ReflectionTestUtils.invokeMethod(
            chatService, "validateChatPermissionOptimized", TEST_USER_ID, TEST_SESSION_ID, TEST_MODEL_ID
        );
        
        // 验证：应该成功降级到原有逻辑
        assertNull(result, "缓存不可用时应该成功降级");
        
        // 验证直接调用了服务，没有通过缓存
        verify(chatLimitService, times(1)).validateChatPermission(TEST_USER_ID);
        verify(aiModelService, times(1)).getById(TEST_MODEL_ID);
        verify(chatLimitService, times(1)).validateModelChatPermission(TEST_USER_ID, TEST_MODEL_ID.toString());
    }
}