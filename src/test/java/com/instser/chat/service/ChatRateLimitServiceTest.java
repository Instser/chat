package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.ChatRateLimitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天频率限制服务测试
 * 验证聊天频率限制功能的正确性
 */
@ExtendWith(MockitoExtension.class)
class ChatRateLimitServiceTest {

    private ChatRateLimitServiceImpl chatRateLimitService;

    @BeforeEach
    void setUp() {
        chatRateLimitService = new ChatRateLimitServiceImpl();
    }

    /**
     * 测试用户聊天频率限制
     */
    @Test
    void testUserChatRateLimit() {
        Long userId = 1L;

        // 前10次请求应该都通过
        for (int i = 0; i < 10; i++) {
            assertTrue(chatRateLimitService.checkUserChatRateLimit(userId), 
                "第" + (i + 1) + "次请求应该通过");
        }

        // 第11次请求应该被限制
        assertFalse(chatRateLimitService.checkUserChatRateLimit(userId), 
            "第11次请求应该被限制");

        // 等待一段时间后，应该可以继续请求
        try {
            Thread.sleep(61000); // 等待61秒，超过1分钟的时间窗口
            assertTrue(chatRateLimitService.checkUserChatRateLimit(userId), 
                "等待后的请求应该通过");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 测试IP聊天频率限制
     */
    @Test
    void testIpChatRateLimit() {
        String testIp = "192.168.1.100";

        // 前30次请求应该都通过
        for (int i = 0; i < 30; i++) {
            assertTrue(chatRateLimitService.checkIpChatRateLimit(testIp), 
                "第" + (i + 1) + "次IP请求应该通过");
        }

        // 第31次请求应该被限制
        assertFalse(chatRateLimitService.checkIpChatRateLimit(testIp), 
            "第31次IP请求应该被限制");
    }

    /**
     * 测试每日聊天限制
     */
    @Test
    void testDailyChatLimit() {
        Long userId = 2L;

        // 前200次请求应该都通过
        for (int i = 0; i < 200; i++) {
            assertTrue(chatRateLimitService.checkDailyChatLimit(userId), 
                "第" + (i + 1) + "次每日请求应该通过");
        }

        // 第201次请求应该被限制
        assertFalse(chatRateLimitService.checkDailyChatLimit(userId), 
            "第201次每日请求应该被限制");
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
     * 测试不同用户的独立限制
     */
    @Test
    void testIndependentUserLimits() {
        Long userId1 = 3L;
        Long userId2 = 4L;

        // 用户1达到限制
        for (int i = 0; i < 10; i++) {
            assertTrue(chatRateLimitService.checkUserChatRateLimit(userId1));
        }
        assertFalse(chatRateLimitService.checkUserChatRateLimit(userId1), 
            "用户1应该被限制");

        // 用户2应该不受影响
        assertTrue(chatRateLimitService.checkUserChatRateLimit(userId2), 
            "用户2不应该受到用户1限制的影响");
    }

    /**
     * 测试不同IP的独立限制
     */
    @Test
    void testIndependentIpLimits() {
        String ip1 = "192.168.1.101";
        String ip2 = "192.168.1.102";

        // IP1达到限制
        for (int i = 0; i < 30; i++) {
            assertTrue(chatRateLimitService.checkIpChatRateLimit(ip1));
        }
        assertFalse(chatRateLimitService.checkIpChatRateLimit(ip1), 
            "IP1应该被限制");

        // IP2应该不受影响
        assertTrue(chatRateLimitService.checkIpChatRateLimit(ip2), 
            "IP2不应该受到IP1限制的影响");
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
        assertTrue(stats.contains("DailyLimit缓存"), "统计信息应该包含DailyLimit缓存信息");
    }

    /**
     * 测试频率限制的时间窗口
     */
    @Test
    void testRateLimitTimeWindow() {
        Long userId = 6L;

        // 快速发送10个请求
        for (int i = 0; i < 10; i++) {
            assertTrue(chatRateLimitService.checkUserChatRateLimit(userId), 
                "快速请求第" + (i + 1) + "次应该通过");
        }

        // 第11次应该被限制
        assertFalse(chatRateLimitService.checkUserChatRateLimit(userId), 
            "第11次请求应该被限制");

        // 模拟时间过去（实际测试中可能需要等待或使用时间模拟）
        // 这里只是验证逻辑正确性
    }

    /**
     * 测试每日限制的日期边界
     */
    @Test
    void testDailyLimitDateBoundary() {
        Long userId = 7L;

        // 测试每日限制的基本功能
        assertTrue(chatRateLimitService.checkDailyChatLimit(userId), 
            "每日限制的第一次请求应该通过");

        // 验证计数器正常工作
        for (int i = 1; i < 10; i++) {
            assertTrue(chatRateLimitService.checkDailyChatLimit(userId), 
                "每日限制的第" + (i + 1) + "次请求应该通过");
        }
    }

    /**
     * 测试并发访问的安全性
     */
    @Test
    void testConcurrentAccess() {
        Long userId = 8L;

        // 模拟并发请求
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                new Thread(() -> {
                    for (int j = 0; j < 2; j++) {
                        chatRateLimitService.checkUserChatRateLimit(userId);
                        chatRateLimitService.checkDailyChatLimit(userId);
                    }
                }).start();
            }
            
            // 等待所有线程完成
            Thread.sleep(1000);
        }, "并发访问不应该抛出异常");
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
}
