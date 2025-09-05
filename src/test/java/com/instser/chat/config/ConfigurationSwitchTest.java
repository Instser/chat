package com.instser.chat.config;

import com.instser.chat.service.cache.RequestValidationCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置开关功能验证测试
 * 验证RequestValidationCache的条件化注册机制
 * 
 * @author instser
 * @since 2025-09-05
 */
@SpringBootTest(classes = RequestValidationCache.class)
public class ConfigurationSwitchTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("测试默认配置：缓存应该被禁用")
    void testDefaultConfiguration() {
        // 验证默认情况下缓存Bean未注册
        boolean hasCache = applicationContext.containsBean("requestValidationCache");
        
        // 由于默认配置enabled=false，缓存应该不被注册
        assertFalse(hasCache, "默认配置下缓存应该被禁用");
        
        // 尝试获取Bean应该失败
        try {
            RequestValidationCache cache = applicationContext.getBean(RequestValidationCache.class);
            fail("应该无法获取未注册的缓存Bean");
        } catch (Exception e) {
            // 预期的异常，表示Bean未注册
            assertTrue(e.getMessage().contains("No qualifying bean") || 
                      e.getMessage().contains("No bean") || 
                      e.getMessage().contains("not found"));
        }
    }
}