package com.instser.chat.service.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置开关功能测试
 * 验证RequestValidationCache的条件化加载
 * 
 * @author instser
 * @since 2025-09-05
 */
public class ConfigurationToggleTest {

    /**
     * 测试缓存启用配置
     */
    @SpringBootTest(
        properties = {"chat.optimization.request-cache.enabled=true"},
        classes = {RequestValidationCache.class, ConfigurationToggleTest.TestConfig.class}
    )
    @DisplayName("测试缓存启用时的Bean注册")
    static class CacheEnabledTest {
        
        @Autowired
        private ApplicationContext applicationContext;
        
        @Test
        void testCacheIsRegistered() {
            // 验证缓存Bean已注册
            assertTrue(applicationContext.containsBean("requestValidationCache"));
            
            // 验证可以获取缓存实例
            RequestValidationCache cache = applicationContext.getBean(RequestValidationCache.class);
            assertNotNull(cache);
            
            // 验证缓存功能正常
            String result = cache.getOrCompute("test_key", () -> "test_value");
            assertEquals("test_value", result);
        }
    }

    /**
     * 测试缓存禁用配置
     */
    @SpringBootTest(
        properties = {"chat.optimization.request-cache.enabled=false"},
        classes = {RequestValidationCache.class, ConfigurationToggleTest.TestConfig.class}
    )
    @DisplayName("测试缓存禁用时的Bean不注册")  
    static class CacheDisabledTest {
        
        @Autowired
        private ApplicationContext applicationContext;
        
        @Test
        void testCacheIsNotRegistered() {
            // 验证缓存Bean未注册
            assertFalse(applicationContext.containsBean("requestValidationCache"));
            
            // 验证无法获取缓存Bean
            assertThrows(Exception.class, () -> {
                applicationContext.getBean(RequestValidationCache.class);
            });
        }
    }
    
    /**
     * 测试默认配置（未设置时应该禁用）
     */
    @SpringBootTest(
        classes = {RequestValidationCache.class, ConfigurationToggleTest.TestConfig.class}
    )
    @DisplayName("测试默认配置（应该禁用缓存）")
    static class DefaultConfigTest {
        
        @Autowired
        private ApplicationContext applicationContext;
        
        @Test
        void testDefaultConfigDisablesCache() {
            // 验证默认情况下缓存Bean未注册
            assertFalse(applicationContext.containsBean("requestValidationCache"));
        }
    }

    @TestConfiguration
    static class TestConfig {
        // 提供必要的测试配置
    }
}