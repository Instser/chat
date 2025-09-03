package com.instser.chat.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 混合缓存管理器
 * 实现L1(内存) + L2(Redis) + L3(数据库)的多层缓存策略
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Component
public class HybridCacheManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisCircuitBreaker circuitBreaker;

    // L1缓存：本地内存缓存（Caffeine）
    private final Cache<String, Object> l1Cache;

    // 缓存命中日志限制
    private static final Map<String, Long> lastLogTime = new ConcurrentHashMap<>();
    private static final long LOG_INTERVAL_MS = 30000; // 30秒内相同key只记录一次日志

    // 缓存配置
    private static final int L1_MAX_SIZE = 1000;
    private static final int L1_EXPIRE_MINUTES = 5;
    private static final int L2_EXPIRE_MINUTES = 30;

    /**
     * 判断是否应该记录缓存命中日志，避免日志刷屏
     */
    private boolean shouldLogCacheHit(String key) {
        // 对频繁命中的缓存key进行日志限制
        long now = System.currentTimeMillis();
        Long lastLog = lastLogTime.get(key);
        
        if (lastLog == null || now - lastLog > LOG_INTERVAL_MS) {
            lastLogTime.put(key, now);
            return true;
        }
        
        return false;
    }

    public HybridCacheManager() {
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfterWrite(L1_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 获取缓存值，使用多层缓存策略
     * 
     * @param key 缓存键
     * @param fallbackSupplier 数据库回退供应商
     * @param <T> 值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> fallbackSupplier) {
        return get(key, fallbackSupplier, Duration.ofMinutes(L2_EXPIRE_MINUTES));
    }

    /**
     * 获取缓存值，使用多层缓存策略，支持自定义TTL
     * 
     * @param key 缓存键
     * @param fallbackSupplier 数据库回退供应商
     * @param ttl 缓存过期时间
     * @param <T> 值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> fallbackSupplier, Duration ttl) {
        try {
            // L1缓存：内存缓存
            Object l1Value = l1Cache.getIfPresent(key);
            if (l1Value != null) {
                // 只在特定条件下记录调试日志，避免日志刷屏
                if (log.isDebugEnabled() && shouldLogCacheHit(key)) {
                    log.debug("L1缓存命中，key: {}", key);
                }
                return (T) l1Value;
            }

            // L2缓存：Redis缓存
            if (circuitBreaker.isAvailable()) {
                try {
                    Object l2Value = redisTemplate.opsForValue().get(key);
                    if (l2Value != null) {
                        if (log.isDebugEnabled() && shouldLogCacheHit(key)) {
                            log.debug("L2缓存命中，key: {}", key);
                        }
                        // 回填L1缓存
                        l1Cache.put(key, l2Value);
                        return (T) l2Value;
                    }
                } catch (Exception e) {
                    log.warn("L2缓存访问失败，key: {}, 错误: {}", key, e.getMessage());
                    circuitBreaker.recordFailure();
                }
            } else {
                log.debug("Redis熔断器开启，跳过L2缓存，key: {}", key);
            }

            // L3缓存：数据库回退
            log.debug("缓存未命中，执行数据库查询，key: {}", key);
            T dbValue = fallbackSupplier.get();
            
            if (dbValue != null) {
                // 异步回填缓存，使用指定的TTL
                asyncFillCache(key, dbValue, ttl);
            }
            
            return dbValue;

        } catch (Exception e) {
            log.error("混合缓存获取异常，key: {}", key, e);
            // 最终回退到数据库
            return fallbackSupplier.get();
        }
    }

    /**
     * 设置缓存值到所有层级
     * 
     * @param key 缓存键
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        put(key, value, Duration.ofMinutes(L2_EXPIRE_MINUTES));
    }

    /**
     * 设置缓存值到所有层级，支持自定义TTL
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 缓存过期时间
     */
    public void put(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }

        try {
            // 设置L1缓存
            l1Cache.put(key, value);
            log.debug("L1缓存设置成功，key: {}", key);

            // 设置L2缓存
            if (circuitBreaker.isAvailable()) {
                try {
                    redisTemplate.opsForValue().set(key, value, ttl);
                    log.debug("L2缓存设置成功，key: {}", key);
                    circuitBreaker.recordSuccess();
                } catch (Exception e) {
                    log.warn("L2缓存设置失败，key: {}, 错误: {}", key, e.getMessage());
                    circuitBreaker.recordFailure();
                }
            }

        } catch (Exception e) {
            log.error("混合缓存设置异常，key: {}", key, e);
        }
    }

    /**
     * 删除缓存
     * 
     * @param key 缓存键
     */
    public void evict(String key) {
        try {
            // 删除L1缓存
            l1Cache.invalidate(key);
            log.debug("L1缓存删除成功，key: {}", key);

            // 删除L2缓存
            if (circuitBreaker.isAvailable()) {
                try {
                    redisTemplate.delete(key);
                    log.debug("L2缓存删除成功，key: {}", key);
                    circuitBreaker.recordSuccess();
                } catch (Exception e) {
                    log.warn("L2缓存删除失败，key: {}, 错误: {}", key, e.getMessage());
                    circuitBreaker.recordFailure();
                }
            }

        } catch (Exception e) {
            log.error("混合缓存删除异常，key: {}", key, e);
        }
    }

    /**
     * 检查缓存是否存在
     * 
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            // 检查L1缓存
            if (l1Cache.getIfPresent(key) != null) {
                return true;
            }

            // 检查L2缓存
            if (circuitBreaker.isAvailable()) {
                try {
                    Boolean exists = redisTemplate.hasKey(key);
                    circuitBreaker.recordSuccess();
                    return exists != null && exists;
                } catch (Exception e) {
                    log.warn("L2缓存存在性检查失败，key: {}, 错误: {}", key, e.getMessage());
                    circuitBreaker.recordFailure();
                }
            }

            return false;

        } catch (Exception e) {
            log.error("混合缓存存在性检查异常，key: {}", key, e);
            return false;
        }
    }

    /**
     * 异步回填缓存
     * 
     * @param key 缓存键
     * @param value 缓存值
     */
    private void asyncFillCache(String key, Object value) {
        asyncFillCache(key, value, Duration.ofMinutes(L2_EXPIRE_MINUTES));
    }

    /**
     * 异步回填缓存，支持自定义TTL
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 缓存过期时间
     */
    private void asyncFillCache(String key, Object value, Duration ttl) {
        // 使用异步方式回填缓存，避免阻塞主线程
        new Thread(() -> {
            try {
                put(key, value, ttl);
                log.debug("异步回填缓存完成，key: {}", key);
            } catch (Exception e) {
                log.warn("异步回填缓存失败，key: {}, 错误: {}", key, e.getMessage());
            }
        }).start();
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 统计信息
     */
    public CacheStats getStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats l1Stats = l1Cache.stats();
        
        return CacheStats.builder()
                .l1HitCount(l1Stats.hitCount())
                .l1MissCount(l1Stats.missCount())
                .l1HitRate(l1Stats.hitRate())
                .l1EvictionCount(l1Stats.evictionCount())
                .l1Size(l1Cache.estimatedSize())
                .redisAvailable(circuitBreaker.isAvailable())
                .circuitBreakerState(circuitBreaker.getState().name())
                .build();
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        try {
            // 清空L1缓存
            l1Cache.invalidateAll();
            log.info("L1缓存已清空");

            // 清空L2缓存（谨慎操作）
            if (circuitBreaker.isAvailable()) {
                try {
                    // 清空用户账户相关缓存，修复序列化问题
                    Set<String> keys = redisTemplate.keys("user:account:*");
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        log.info("用户账户缓存已清空，共清除{}个key", keys.size());
                    }
                    // 清空支付相关缓存
                    Set<String> paymentKeys = redisTemplate.keys("payment:*");
                    if (paymentKeys != null && !paymentKeys.isEmpty()) {
                        redisTemplate.delete(paymentKeys);
                        log.info("支付缓存已清空，共清除{}个key", paymentKeys.size());
                    }
                    circuitBreaker.recordSuccess();
                } catch (Exception e) {
                    log.warn("L2缓存清空失败，错误: {}", e.getMessage());
                    circuitBreaker.recordFailure();
                }
            }

        } catch (Exception e) {
            log.error("混合缓存清空异常", e);
        }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long l1HitCount;
        private final long l1MissCount;
        private final double l1HitRate;
        private final long l1EvictionCount;
        private final long l1Size;
        private final boolean redisAvailable;
        private final String circuitBreakerState;

        private CacheStats(Builder builder) {
            this.l1HitCount = builder.l1HitCount;
            this.l1MissCount = builder.l1MissCount;
            this.l1HitRate = builder.l1HitRate;
            this.l1EvictionCount = builder.l1EvictionCount;
            this.l1Size = builder.l1Size;
            this.redisAvailable = builder.redisAvailable;
            this.circuitBreakerState = builder.circuitBreakerState;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public long getL1HitCount() { return l1HitCount; }
        public long getL1MissCount() { return l1MissCount; }
        public double getL1HitRate() { return l1HitRate; }
        public long getL1EvictionCount() { return l1EvictionCount; }
        public long getL1Size() { return l1Size; }
        public boolean isRedisAvailable() { return redisAvailable; }
        public String getCircuitBreakerState() { return circuitBreakerState; }

        public static class Builder {
            private long l1HitCount;
            private long l1MissCount;
            private double l1HitRate;
            private long l1EvictionCount;
            private long l1Size;
            private boolean redisAvailable;
            private String circuitBreakerState;

            public Builder l1HitCount(long l1HitCount) { this.l1HitCount = l1HitCount; return this; }
            public Builder l1MissCount(long l1MissCount) { this.l1MissCount = l1MissCount; return this; }
            public Builder l1HitRate(double l1HitRate) { this.l1HitRate = l1HitRate; return this; }
            public Builder l1EvictionCount(long l1EvictionCount) { this.l1EvictionCount = l1EvictionCount; return this; }
            public Builder l1Size(long l1Size) { this.l1Size = l1Size; return this; }
            public Builder redisAvailable(boolean redisAvailable) { this.redisAvailable = redisAvailable; return this; }
            public Builder circuitBreakerState(String circuitBreakerState) { this.circuitBreakerState = circuitBreakerState; return this; }

            public CacheStats build() { return new CacheStats(this); }
        }
    }
}
