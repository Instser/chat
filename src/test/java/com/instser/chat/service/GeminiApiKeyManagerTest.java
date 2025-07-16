package com.instser.chat.service;

import com.instser.chat.config.GeminiApiKeyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Gemini API密钥管理器测试类
 *
 * @author instser
 * @since 2025-07-16
 */
@ExtendWith(MockitoExtension.class)
class GeminiApiKeyManagerTest {

    @Mock
    private GeminiApiKeyConfig config;

    private GeminiApiKeyManager keyManager;

    @BeforeEach
    void setUp() {
        // 设置模拟配置
        List<String> testKeys = Arrays.asList(
            "your-google-api-key",
            "your-google-api-key2",
            "your-google-api-key3"
        );
        
        when(config.getKeys()).thenReturn(testKeys);
        when(config.getStrategy()).thenReturn("ROUND_ROBIN");
        when(config.getHealthCheckInterval()).thenReturn(300);
        when(config.getMaxRetries()).thenReturn(3);
        when(config.getDisableTimeout()).thenReturn(600);

        keyManager = new GeminiApiKeyManager(config);
    }

    @Test
    void testGetNextApiKey() {
        // 测试轮询策略
        String firstKey = keyManager.getNextApiKey();
        assertNotNull(firstKey);
        assertTrue(firstKey.startsWith("AIzaSyB8sq"));

        String secondKey = keyManager.getNextApiKey();
        assertNotNull(secondKey);
        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void testReportSuccess() {
        String apiKey = keyManager.getNextApiKey();
        
        // 报告成功不应该抛出异常
        assertDoesNotThrow(() -> keyManager.reportSuccess(apiKey));
    }

    @Test
    void testReportFailure() {
        String apiKey = keyManager.getNextApiKey();
        Exception testException = new RuntimeException("Test failure");
        
        // 报告失败不应该抛出异常
        assertDoesNotThrow(() -> keyManager.reportFailure(apiKey, testException));
    }

    @Test
    void testGetKeyStatistics() {
        Map<String, Object> stats = keyManager.getKeyStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("keys"));
        assertTrue(stats.containsKey("totalKeys"));
        assertTrue(stats.containsKey("availableKeys"));
        assertTrue(stats.containsKey("strategy"));
        
        assertEquals(3, stats.get("totalKeys"));
        assertEquals("ROUND_ROBIN", stats.get("strategy"));
    }

    @Test
    void testUpdateApiKeys() {
        List<String> newKeys = Arrays.asList(
            "your-google-api-key",
            "your-google-api-key4"
        );
        
        assertDoesNotThrow(() -> keyManager.updateApiKeys(newKeys));
        
        Map<String, Object> stats = keyManager.getKeyStatistics();
        assertEquals(2, stats.get("totalKeys"));
    }
}
