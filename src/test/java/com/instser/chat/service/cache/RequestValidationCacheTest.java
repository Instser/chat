package com.instser.chat.service.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RequestValidationCache 测试类
 * 
 * @author instser
 * @since 2025-09-05
 */
public class RequestValidationCacheTest {
    
    private RequestValidationCache cache;
    
    @BeforeEach
    public void setUp() {
        cache = new RequestValidationCache();
    }
    
    @Test
    public void testGetOrCompute_FirstCall_ShouldComputeValue() {
        // 安排
        String key = "test_key";
        String expectedValue = "computed_value";
        
        // 执行
        String result = cache.getOrCompute(key, () -> expectedValue);
        
        // 验证
        assertEquals(expectedValue, result);
        assertEquals(1, cache.getStats().getSize());
    }
    
    @Test
    public void testGetOrCompute_SecondCall_ShouldReturnCachedValue() {
        // 安排
        String key = "test_key";
        String computedValue = "computed_value";
        boolean[] supplierCalled = {false};
        
        // 第一次调用，计算值
        cache.getOrCompute(key, () -> {
            supplierCalled[0] = true;
            return computedValue;
        });
        
        // 重置标记
        supplierCalled[0] = false;
        
        // 执行第二次调用
        String result = cache.getOrCompute(key, () -> {
            supplierCalled[0] = true;
            return "should_not_be_called";
        });
        
        // 验证
        assertEquals(computedValue, result);
        assertFalse(supplierCalled[0], "Supplier should not be called on cache hit");
        assertEquals(1, cache.getStats().getSize());
    }
    
    @Test
    public void testCleanup() {
        // 安排
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        assertEquals(2, cache.getStats().getSize());
        
        // 执行
        cache.cleanup();
        
        // 验证
        assertEquals(0, cache.getStats().getSize());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }
    
    @Test
    public void testPutAndGet() {
        // 安排
        String key = "test_key";
        String value = "test_value";
        
        // 执行
        cache.put(key, value);
        String result = cache.get(key);
        
        // 验证
        assertEquals(value, result);
    }
    
    @Test
    public void testPutNull_ShouldNotStore() {
        // 安排
        String key = "test_key";
        
        // 执行
        cache.put(key, null);
        
        // 验证
        assertEquals(0, cache.getStats().getSize());
        assertNull(cache.get(key));
    }
}