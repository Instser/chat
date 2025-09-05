package com.instser.chat.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 请求级验证缓存组件
 * 使用Spring @RequestScope管理生命周期，避免ThreadLocal内存泄漏风险
 * 
 * @author instser
 * @since 2025-09-05
 */
@Slf4j
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
@ConditionalOnProperty(
    name = "chat.optimization.request-cache.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class RequestValidationCache {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    
    /**
     * 获取或计算缓存值
     * 
     * @param key 缓存键
     * @param supplier 值计算函数
     * @return 缓存值或计算值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        T cached = (T) cache.get(key);
        if (cached != null) {
            log.debug("请求级缓存命中: {}", key);
            return cached;
        }
        
        log.debug("请求级缓存未命中，计算值: {}", key);
        T computed = supplier.get();
        if (computed != null) {
            cache.put(key, computed);
            log.debug("请求级缓存已存储: {}", key);
        }
        return computed;
    }
    
    /**
     * 直接获取缓存值，不进行计算
     * 
     * @param key 缓存键
     * @return 缓存值或null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) cache.get(key);
    }
    
    /**
     * 存储缓存值
     * 
     * @param key 缓存键
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        if (value != null) {
            cache.put(key, value);
            log.debug("请求级缓存已存储: {}", key);
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(cache.size());
    }
    
    /**
     * 请求结束时自动清理缓存
     * Spring会自动调用此方法
     */
    @PreDestroy
    public void cleanup() {
        int size = cache.size();
        cache.clear();
        log.debug("请求级缓存已清理，清理了{}个条目", size);
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int size;
        
        public CacheStats(int size) {
            this.size = size;
        }
        
        public int getSize() {
            return size;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{size=%d}", size);
        }
    }
}