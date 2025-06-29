package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.ChatRateLimitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天频率限制服务简单测试
 * 验证基本功能是否正常工作
 */
@ExtendWith(MockitoExtension.class)
class ChatRateLimitServiceSimpleTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private PackageService packageService;

    @Mock
    private UserDailyUsageService userDailyUsageService;

    private ChatRateLimitServiceImpl chatRateLimitService;

    @BeforeEach
    void setUp() {
        chatRateLimitService = new ChatRateLimitServiceImpl(userAccountService, packageService, userDailyUsageService);
    }

    /**
     * 测试基本功能
     */
    @Test
    void testBasicFunctionality() {
        Long userId = 1L;
        String testIp = "192.168.1.100";

        // 测试第一次请求应该通过
        assertTrue(chatRateLimitService.checkUserChatRateLimit(userId), 
            "第一次用户请求应该通过");
        
        assertTrue(chatRateLimitService.checkIpChatRateLimit(testIp), 
            "第一次IP请求应该通过");
        
        assertFalse(chatRateLimitService.checkDailyChatLimit(userId),
            "没有套餐的用户每日请求应该被拒绝");
    }

    /**
     * 测试null参数处理
     */
    @Test
    void testNullParameterHandling() {
        // 测试null用户ID
        assertFalse(chatRateLimitService.checkUserChatRateLimit(null), 
            "null用户ID应该被拒绝");

        assertFalse(chatRateLimitService.checkDailyChatLimit(null), 
            "null用户ID的每日限制应该被拒绝");

        // 测试null IP（应该允许）
        assertTrue(chatRateLimitService.checkIpChatRateLimit(null), 
            "null IP应该被允许");

        // 测试空字符串IP（应该允许）
        assertTrue(chatRateLimitService.checkIpChatRateLimit(""), 
            "空字符串IP应该被允许");
    }

    /**
     * 测试缓存清理功能
     */
    @Test
    void testCacheCleanup() {
        Long userId = 5L;
        String testIp = "192.168.1.103";

        // 生成一些请求
        for (int i = 0; i < 5; i++) {
            chatRateLimitService.checkUserChatRateLimit(userId);
            chatRateLimitService.checkIpChatRateLimit(testIp);
            chatRateLimitService.checkDailyChatLimit(userId);
        }

        // 执行缓存清理
        assertDoesNotThrow(() -> chatRateLimitService.cleanExpiredCache(), 
            "缓存清理不应该抛出异常");

        // 获取缓存统计
        String stats = chatRateLimitService.getCacheStats();
        assertNotNull(stats, "缓存统计不应该为null");
        assertTrue(stats.contains("RateLimit缓存"), "统计信息应该包含RateLimit缓存信息");
        assertTrue(stats.contains("RateLimit缓存"), "统计信息应该包含RateLimit缓存信息");
    }

    /**
     * 测试边界条件
     */
    @Test
    void testBoundaryConditions() {
        // 测试极大的用户ID
        Long largeUserId = Long.MAX_VALUE;
        assertTrue(chatRateLimitService.checkUserChatRateLimit(largeUserId), 
            "极大用户ID应该被正确处理");

        // 测试特殊IP地址
        assertTrue(chatRateLimitService.checkIpChatRateLimit("127.0.0.1"), 
            "本地IP应该被正确处理");
        assertTrue(chatRateLimitService.checkIpChatRateLimit("::1"), 
            "IPv6本地地址应该被正确处理");
    }

    /**
     * 测试多次请求的基本行为
     */
    @Test
    void testMultipleRequests() {
        Long userId = 10L;
        String testIp = "192.168.1.200";

        // 测试多次用户请求
        for (int i = 0; i < 5; i++) {
            boolean result = chatRateLimitService.checkUserChatRateLimit(userId);
            assertTrue(result, "前5次用户请求应该都通过");
        }

        // 测试多次IP请求
        for (int i = 0; i < 5; i++) {
            boolean result = chatRateLimitService.checkIpChatRateLimit(testIp);
            assertTrue(result, "前5次IP请求应该都通过");
        }

        // 测试每日请求（没有套餐时应该被拒绝）
        boolean result = chatRateLimitService.checkDailyChatLimit(userId);
        assertFalse(result, "没有套餐的用户每日请求应该被拒绝");
    }

    /**
     * 测试不同用户的独立性
     */
    @Test
    void testUserIndependence() {
        Long userId1 = 100L;
        Long userId2 = 200L;

        // 用户1的请求
        assertTrue(chatRateLimitService.checkUserChatRateLimit(userId1), 
            "用户1的请求应该通过");
        assertFalse(chatRateLimitService.checkDailyChatLimit(userId1),
            "没有套餐的用户1每日请求应该被拒绝");

        // 用户2的请求不应该受到用户1的影响
        assertTrue(chatRateLimitService.checkUserChatRateLimit(userId2),
            "用户2的请求不应该受到用户1的影响");
        assertFalse(chatRateLimitService.checkDailyChatLimit(userId2),
            "没有套餐的用户2每日请求应该被拒绝");
    }

    /**
     * 测试不同IP的独立性
     */
    @Test
    void testIpIndependence() {
        String ip1 = "192.168.1.101";
        String ip2 = "192.168.1.102";

        // IP1的请求
        assertTrue(chatRateLimitService.checkIpChatRateLimit(ip1), 
            "IP1的请求应该通过");

        // IP2的请求不应该受到IP1的影响
        assertTrue(chatRateLimitService.checkIpChatRateLimit(ip2), 
            "IP2的请求不应该受到IP1的影响");
    }

    /**
     * 测试getClientIpAddress方法
     */
    @Test
    void testGetClientIpAddress() {
        // 不抛出异常就算成功
        assertDoesNotThrow(() -> chatRateLimitService.getClientIpAddress(),
            "获取客户端IP不应该抛出异常");
    }
}
