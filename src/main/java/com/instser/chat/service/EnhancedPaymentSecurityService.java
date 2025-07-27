package com.instser.chat.service;

import com.instser.chat.dao.UserOrderDao;
import com.instser.chat.service.cache.HybridCacheManager;
import com.instser.chat.service.cache.RedisCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

/**
 * 增强版支付安全服务
 * 集成混合缓存和熔断器机制的支付安全功能
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Service
public class EnhancedPaymentSecurityService {

    @Autowired
    private HybridCacheManager hybridCacheManager;

    @Autowired
    private RedisCircuitBreaker circuitBreaker;

    @Autowired
    private UserOrderDao userOrderDao;

    // 支付安全配置
    @Value("${payment.security.frequency.max-count:5}")
    private int maxFrequencyCount;

    @Value("${payment.security.frequency.time-window:10}")
    private int frequencyTimeWindow;

    @Value("${payment.security.callback.expire-time:60}")
    private int callbackExpireTime;

    @Value("${payment.security.suspicious.threshold:3}")
    private int suspiciousThreshold;

    @Value("${payment.security.suspicious.expire-days:7}")
    private int suspiciousExpireDays;

    // 内存降级缓存
    private final ConcurrentHashMap<String, AtomicInteger> frequencyFallbackCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> callbackFallbackCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionFallbackCache = new ConcurrentHashMap<>();

    // 线程池用于异步清理任务
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(2);

    /**
     * 检查支付频率限制（混合缓存策略）
     * 
     * @param userId 用户ID
     * @return 是否通过频率检查
     */
    public boolean checkPaymentFrequency(Long userId) {
        if (userId == null) {
            return false;
        }

        String key = "payment:freq:" + userId;
        String timeWindow = String.valueOf(System.currentTimeMillis() / (frequencyTimeWindow * 60 * 1000));
        String fullKey = key + ":" + timeWindow;

        try {
            // 使用混合缓存获取当前计数
            Integer currentCount = hybridCacheManager.get(fullKey, () -> {
                // L3回退：查询数据库中的支付记录
                return getDatabasePaymentCount(userId, frequencyTimeWindow);
            });

            if (currentCount == null) {
                currentCount = 0;
            }

            // 增加计数
            currentCount++;
            
            // 更新缓存
            hybridCacheManager.put(fullKey, currentCount);

            boolean allowed = currentCount <= maxFrequencyCount;
            
            if (!allowed) {
                log.warn("用户{}支付频率过高，当前次数: {}, 最大允许: {}", userId, currentCount, maxFrequencyCount);
            } else {
                log.debug("用户{}支付频率检查通过，当前次数: {}/{}", userId, currentCount, maxFrequencyCount);
            }

            return allowed;

        } catch (Exception e) {
            log.error("检查支付频率异常，用户ID: {}, 使用内存降级策略", userId, e);
            return checkPaymentFrequencyFallback(fullKey);
        }
    }

    /**
     * 支付频率检查降级策略
     */
    private boolean checkPaymentFrequencyFallback(String key) {
        try {
            AtomicInteger count = frequencyFallbackCache.computeIfAbsent(key, k -> new AtomicInteger(0));
            int currentCount = count.incrementAndGet();
            
            // 设置过期清理（简单实现）
            scheduleCleanup(key, frequencyTimeWindow * 60 * 1000);
            
            boolean allowed = currentCount <= maxFrequencyCount;
            log.debug("内存降级频率检查，key: {}, 当前次数: {}, 允许: {}", key, currentCount, allowed);
            
            return allowed;
        } catch (Exception e) {
            log.error("内存降级频率检查异常，key: {}", key, e);
            // 最终降级：允许通过，避免影响业务
            return true;
        }
    }

    /**
     * 防重放攻击检查（混合缓存策略）
     * 
     * @param callbackId 回调ID
     * @return true表示已处理过（重放攻击），false表示首次处理
     */
    public boolean isCallbackProcessed(String callbackId) {
        if (callbackId == null || callbackId.trim().isEmpty()) {
            log.warn("回调ID为空");
            return true;
        }

        String key = "payment:callback:" + callbackId;

        try {
            // 使用混合缓存检查是否存在
            Boolean exists = hybridCacheManager.exists(key);
            
            if (exists) {
                log.warn("检测到重放攻击，回调ID: {}", callbackId);
                return true;
            }

            // 标记为已处理
            hybridCacheManager.put(key, System.currentTimeMillis());
            log.debug("回调ID: {} 标记为已处理", callbackId);
            return false;

        } catch (Exception e) {
            log.error("防重放攻击检查异常，回调ID: {}, 使用内存降级策略", callbackId, e);
            return isCallbackProcessedFallback(key);
        }
    }

    /**
     * 防重放攻击检查降级策略
     */
    private boolean isCallbackProcessedFallback(String key) {
        try {
            Long processTime = callbackFallbackCache.get(key);
            
            if (processTime != null) {
                // 检查是否过期
                if (System.currentTimeMillis() - processTime < callbackExpireTime * 60 * 1000) {
                    log.warn("内存降级检测到重放攻击，key: {}", key);
                    return true;
                }
            }

            // 标记为已处理
            callbackFallbackCache.put(key, System.currentTimeMillis());
            scheduleCleanup(key, callbackExpireTime * 60 * 1000);
            
            log.debug("内存降级回调处理，key: {}", key);
            return false;
            
        } catch (Exception e) {
            log.error("内存降级防重放检查异常，key: {}", key, e);
            // 最终降级：允许处理，避免订单丢失
            return false;
        }
    }

    /**
     * 获取用户支付会话（混合缓存策略）
     * 
     * @param userId 用户ID
     * @return 是否成功获取会话锁
     */
    public boolean acquirePaymentSession(Long userId) {
        if (userId == null) {
            return false;
        }

        String key = "payment:session:" + userId;

        try {
            // 检查是否已存在会话
            Boolean exists = hybridCacheManager.exists(key);
            
            if (exists) {
                log.warn("用户{}支付会话锁获取失败，可能存在并发支付", userId);
                return false;
            }

            // 获取会话锁
            hybridCacheManager.put(key, "locked");
            log.debug("用户{}成功获取支付会话锁", userId);
            return true;

        } catch (Exception e) {
            log.error("获取支付会话锁异常，用户ID: {}, 使用内存降级策略", userId, e);
            return acquirePaymentSessionFallback(key);
        }
    }

    /**
     * 支付会话获取降级策略
     */
    private boolean acquirePaymentSessionFallback(String key) {
        try {
            Long sessionTime = sessionFallbackCache.get(key);
            
            if (sessionTime != null) {
                // 检查是否过期（5分钟）
                if (System.currentTimeMillis() - sessionTime < 5 * 60 * 1000) {
                    log.warn("内存降级会话锁获取失败，key: {}", key);
                    return false;
                }
            }

            // 获取会话锁
            sessionFallbackCache.put(key, System.currentTimeMillis());
            scheduleCleanup(key, 5 * 60 * 1000);
            
            log.debug("内存降级会话锁获取成功，key: {}", key);
            return true;
            
        } catch (Exception e) {
            log.error("内存降级会话锁获取异常，key: {}", key, e);
            // 最终降级：拒绝获取，保证安全
            return false;
        }
    }

    /**
     * 释放用户支付会话
     * 
     * @param userId 用户ID
     */
    public void releasePaymentSession(Long userId) {
        if (userId == null) {
            return;
        }

        String key = "payment:session:" + userId;

        try {
            hybridCacheManager.evict(key);
            log.debug("用户{}支付会话锁已释放", userId);
        } catch (Exception e) {
            log.error("释放支付会话锁异常，用户ID: {}", userId, e);
            // 降级处理
            sessionFallbackCache.remove(key);
        }
    }

    /**
     * 检查用户是否有活跃的支付会话
     * 
     * @param userId 用户ID
     * @return 是否有活跃会话
     */
    public boolean hasActivePaymentSession(Long userId) {
        if (userId == null) {
            return false;
        }

        String key = "payment:session:" + userId;

        try {
            return hybridCacheManager.exists(key);
        } catch (Exception e) {
            log.error("检查活跃支付会话异常，用户ID: {}", userId, e);
            // 降级处理
            Long sessionTime = sessionFallbackCache.get(key);
            return sessionTime != null && (System.currentTimeMillis() - sessionTime < 5 * 60 * 1000);
        }
    }

    /**
     * 记录可疑支付行为（混合缓存策略）
     * 
     * @param userId 用户ID
     * @param reason 可疑原因
     */
    public void recordSuspiciousBehavior(Long userId, String reason) {
        if (userId == null) {
            return;
        }

        String key = "payment:suspicious:" + userId;
        String value = reason + ":" + System.currentTimeMillis();

        try {
            // 使用混合缓存记录可疑行为
            hybridCacheManager.put(key + ":" + System.currentTimeMillis(), value);
            log.warn("记录用户{}可疑行为: {}", userId, reason);
        } catch (Exception e) {
            log.error("记录可疑行为异常，用户ID: {}", userId, e);
        }
    }

    /**
     * 检查可疑行为
     * 
     * @param userId 用户ID
     * @return 是否通过检查
     */
    public boolean checkSuspiciousBehavior(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
            // 这里可以实现更复杂的可疑行为检查逻辑
            // 暂时简化处理，总是返回true
            return true;
        } catch (Exception e) {
            log.error("检查可疑行为异常，用户ID: {}", userId, e);
            return true;
        }
    }

    /**
     * 从数据库获取支付次数（L3回退策略）
     */
    private Integer getDatabasePaymentCount(Long userId, int timeWindowMinutes) {
        try {
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(timeWindowMinutes);
            String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // 查询数据库中指定时间窗口内的支付次数
            Integer count = userOrderDao.countRecentPayments(userId, startTimeStr);
            log.debug("数据库查询用户{}最近{}分钟支付次数: {}", userId, timeWindowMinutes, count);
            
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("数据库查询支付次数异常，用户ID: {}", userId, e);
            return 0;
        }
    }

    /**
     * 调度清理任务（使用线程池）
     */
    private void scheduleCleanup(String key, long delayMs) {
        cleanupExecutor.schedule(() -> {
            frequencyFallbackCache.remove(key);
            callbackFallbackCache.remove(key);
            sessionFallbackCache.remove(key);
            log.debug("清理过期缓存项: {}", key);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 销毁资源
     */
    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("增强版支付安全服务资源已释放");
    }

    /**
     * 获取缓存统计信息
     */
    public PaymentSecurityStats getStats() {
        return PaymentSecurityStats.builder()
                .hybridCacheStats(hybridCacheManager.getStats())
                .circuitBreakerStats(circuitBreaker.getStats())
                .fallbackCacheSize(frequencyFallbackCache.size() + callbackFallbackCache.size() + sessionFallbackCache.size())
                .build();
    }

    /**
     * 支付安全统计信息
     */
    public static class PaymentSecurityStats {
        private final HybridCacheManager.CacheStats hybridCacheStats;
        private final RedisCircuitBreaker.CircuitBreakerStats circuitBreakerStats;
        private final int fallbackCacheSize;

        private PaymentSecurityStats(Builder builder) {
            this.hybridCacheStats = builder.hybridCacheStats;
            this.circuitBreakerStats = builder.circuitBreakerStats;
            this.fallbackCacheSize = builder.fallbackCacheSize;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public HybridCacheManager.CacheStats getHybridCacheStats() { return hybridCacheStats; }
        public RedisCircuitBreaker.CircuitBreakerStats getCircuitBreakerStats() { return circuitBreakerStats; }
        public int getFallbackCacheSize() { return fallbackCacheSize; }

        public static class Builder {
            private HybridCacheManager.CacheStats hybridCacheStats;
            private RedisCircuitBreaker.CircuitBreakerStats circuitBreakerStats;
            private int fallbackCacheSize;

            public Builder hybridCacheStats(HybridCacheManager.CacheStats hybridCacheStats) { 
                this.hybridCacheStats = hybridCacheStats; return this; 
            }
            public Builder circuitBreakerStats(RedisCircuitBreaker.CircuitBreakerStats circuitBreakerStats) { 
                this.circuitBreakerStats = circuitBreakerStats; return this; 
            }
            public Builder fallbackCacheSize(int fallbackCacheSize) { 
                this.fallbackCacheSize = fallbackCacheSize; return this; 
            }

            public PaymentSecurityStats build() { return new PaymentSecurityStats(this); }
        }
    }
}
