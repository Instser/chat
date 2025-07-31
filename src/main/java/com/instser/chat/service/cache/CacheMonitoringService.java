package com.instser.chat.service.cache;

import com.instser.chat.service.cache.RedisCircuitBreakerEvent;
import com.instser.chat.service.cache.RedisRecoveryEvent;
import com.instser.chat.service.cache.RedisCircuitBreakerResetEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局缓存监控服务
 * 实现全系统缓存状态监控、健康检查和告警机制
 *
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Service
public class CacheMonitoringService {

    @Autowired
    private HybridCacheManager hybridCacheManager;

    @Autowired
    private RedisCircuitBreaker circuitBreaker;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GlobalCacheManager globalCacheManager;

    // 全局监控统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong redisFailures = new AtomicLong(0);
    private final AtomicLong fallbackUsages = new AtomicLong(0);

    // 按模块统计
    private final Map<String, ModuleStats> moduleStats = new ConcurrentHashMap<>();

    // 按缓存类型统计
    private final Map<GlobalCacheManager.CacheType, TypeStats> typeStats = new ConcurrentHashMap<>();

    // 告警状态
    private volatile boolean redisAlertSent = false;
    private volatile LocalDateTime lastRedisFailureTime;
    private volatile LocalDateTime lastRedisRecoveryTime;

    @PostConstruct
    public void init() {
        log.info("缓存监控服务初始化完成");
    }

    /**
     * 监听Redis熔断器事件
     */
    @EventListener
    public void onRedisCircuitBreakerEvent(RedisCircuitBreakerEvent event) {
        if (event.isFailedOver()) {
            lastRedisFailureTime = event.getEventTime();
            redisFailures.incrementAndGet();
            
            log.error("【缓存告警】Redis熔断器开启 - 时间: {}, 失败次数: {}, 状态: {}", 
                    event.getEventTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    event.getFailureCount(),
                    event.getState());
            
            // 发送告警
            sendRedisFailureAlert(event);
            redisAlertSent = true;
        }
    }

    /**
     * 监听Redis恢复事件
     */
    @EventListener
    public void onRedisRecoveryEvent(RedisRecoveryEvent event) {
        lastRedisRecoveryTime = event.getEventTime();
        
        log.info("【缓存恢复】Redis熔断器恢复正常 - 时间: {}, 成功次数: {}", 
                event.getEventTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                event.getSuccessCount());
        
        // 发送恢复通知
        sendRedisRecoveryAlert(event);
        redisAlertSent = false;
    }

    /**
     * 监听Redis重置事件
     */
    @EventListener
    public void onRedisCircuitBreakerResetEvent(RedisCircuitBreakerResetEvent event) {
        log.info("【缓存重置】Redis熔断器手动重置 - 时间: {}", 
                event.getEventTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        redisAlertSent = false;
    }

    /**
     * 缓存健康检查
     * 由 OptimizedScheduledTaskService 统一调用
     */
    public void performHealthCheck() {
        try {
            log.debug("开始执行缓存健康检查");
            
            // 检查Redis连接状态
            boolean redisHealthy = checkRedisHealth();
            
            // 检查缓存性能指标
            CacheHealthMetrics metrics = collectHealthMetrics();
            
            // 记录健康状态
            logHealthStatus(redisHealthy, metrics);
            
            // 检查是否需要告警
            checkAndSendAlerts(redisHealthy, metrics);
            
        } catch (Exception e) {
            log.error("缓存健康检查异常", e);
        }
    }

    /**
     * 检查Redis健康状态
     */
    private boolean checkRedisHealth() {
        try {
            String testKey = "health:check:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(testKey, "ping", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            
            boolean healthy = "ping".equals(result);
            if (healthy) {
                log.debug("Redis健康检查通过");
            } else {
                log.warn("Redis健康检查失败：响应不匹配");
            }
            
            return healthy;
            
        } catch (Exception e) {
            log.warn("Redis健康检查异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 收集健康指标
     */
    private CacheHealthMetrics collectHealthMetrics() {
        HybridCacheManager.CacheStats cacheStats = hybridCacheManager.getStats();
        RedisCircuitBreaker.CircuitBreakerStats circuitStats = circuitBreaker.getStats();
        
        return CacheHealthMetrics.builder()
                .l1HitRate(cacheStats.getL1HitRate())
                .l1Size(cacheStats.getL1Size())
                .redisAvailable(cacheStats.isRedisAvailable())
                .circuitBreakerState(circuitStats.getState())
                .failureCount(circuitStats.getFailureCount())
                .successCount(circuitStats.getSuccessCount())
                .totalRequests(totalRequests.get())
                .cacheHits(cacheHits.get())
                .cacheMisses(cacheMisses.get())
                .redisFailures(redisFailures.get())
                .fallbackUsages(fallbackUsages.get())
                .build();
    }

    /**
     * 记录健康状态日志
     */
    private void logHealthStatus(boolean redisHealthy, CacheHealthMetrics metrics) {
        if (log.isInfoEnabled()) {
            log.info("【缓存健康状态】Redis: {}, 熔断器: {}, L1命中率: {:.2f}%, L1大小: {}, 总请求: {}, 缓存命中: {}, Redis失败: {}",
                    redisHealthy ? "健康" : "异常",
                    metrics.getCircuitBreakerState(),
                    metrics.getL1HitRate() * 100,
                    metrics.getL1Size(),
                    metrics.getTotalRequests(),
                    metrics.getCacheHits(),
                    metrics.getRedisFailures());
        }
    }

    /**
     * 检查并发送告警
     */
    private void checkAndSendAlerts(boolean redisHealthy, CacheHealthMetrics metrics) {
        // 检查L1缓存命中率告警
        if (metrics.getL1HitRate() < 0.5 && metrics.getTotalRequests() > 100) {
            log.warn("【缓存告警】L1缓存命中率过低: {:.2f}%, 总请求数: {}", 
                    metrics.getL1HitRate() * 100, metrics.getTotalRequests());
        }
        
        // 检查Redis失败率告警
        if (metrics.getTotalRequests() > 0) {
            double redisFailureRate = (double) metrics.getRedisFailures() / metrics.getTotalRequests();
            if (redisFailureRate > 0.1) { // 失败率超过10%
                log.warn("【缓存告警】Redis失败率过高: {:.2f}%, 失败次数: {}, 总请求: {}", 
                        redisFailureRate * 100, metrics.getRedisFailures(), metrics.getTotalRequests());
            }
        }
        
        // 检查降级使用率告警
        if (metrics.getTotalRequests() > 0) {
            double fallbackRate = (double) metrics.getFallbackUsages() / metrics.getTotalRequests();
            if (fallbackRate > 0.2) { // 降级使用率超过20%
                log.warn("【缓存告警】降级策略使用率过高: {:.2f}%, 降级次数: {}, 总请求: {}", 
                        fallbackRate * 100, metrics.getFallbackUsages(), metrics.getTotalRequests());
            }
        }
    }

    /**
     * 发送Redis故障告警
     */
    private void sendRedisFailureAlert(RedisCircuitBreakerEvent event) {
        // 这里可以集成实际的告警系统，如邮件、短信、钉钉等
        String alertMessage = String.format(
                "【支付系统告警】Redis缓存故障\n" +
                "时间: %s\n" +
                "状态: %s\n" +
                "失败次数: %d\n" +
                "影响: 支付模块已切换到降级模式\n" +
                "建议: 请立即检查Redis服务状态",
                event.getEventTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                event.getState(),
                event.getFailureCount()
        );
        
        log.error("【告警通知】{}", alertMessage);
        
        // TODO: 集成实际告警渠道
        // emailService.sendAlert(alertMessage);
        // smsService.sendAlert(alertMessage);
        // dingTalkService.sendAlert(alertMessage);
    }

    /**
     * 发送Redis恢复通知
     */
    private void sendRedisRecoveryAlert(RedisRecoveryEvent event) {
        String recoveryMessage = String.format(
                "【支付系统恢复】Redis缓存恢复正常\n" +
                "时间: %s\n" +
                "成功次数: %d\n" +
                "状态: 支付模块已恢复正常模式",
                event.getEventTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                event.getSuccessCount()
        );
        
        log.info("【恢复通知】{}", recoveryMessage);
        
        // TODO: 集成实际通知渠道
        // emailService.sendNotification(recoveryMessage);
        // dingTalkService.sendNotification(recoveryMessage);
    }

    /**
     * 记录缓存操作统计
     */
    public void recordCacheOperation(boolean hit, boolean redisFailed, boolean fallbackUsed) {
        totalRequests.incrementAndGet();
        
        if (hit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        
        if (redisFailed) {
            redisFailures.incrementAndGet();
        }
        
        if (fallbackUsed) {
            fallbackUsages.incrementAndGet();
        }
    }

    /**
     * 获取监控统计信息
     */
    public Map<String, Object> getMonitoringStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 基础统计
        stats.put("totalRequests", totalRequests.get());
        stats.put("cacheHits", cacheHits.get());
        stats.put("cacheMisses", cacheMisses.get());
        stats.put("redisFailures", redisFailures.get());
        stats.put("fallbackUsages", fallbackUsages.get());
        
        // 计算比率
        long total = totalRequests.get();
        if (total > 0) {
            stats.put("hitRate", (double) cacheHits.get() / total);
            stats.put("redisFailureRate", (double) redisFailures.get() / total);
            stats.put("fallbackRate", (double) fallbackUsages.get() / total);
        } else {
            stats.put("hitRate", 0.0);
            stats.put("redisFailureRate", 0.0);
            stats.put("fallbackRate", 0.0);
        }
        
        // 状态信息
        stats.put("redisAvailable", circuitBreaker.isAvailable());
        stats.put("circuitBreakerState", circuitBreaker.getState().name());
        stats.put("lastRedisFailureTime", lastRedisFailureTime);
        stats.put("lastRedisRecoveryTime", lastRedisRecoveryTime);
        stats.put("redisAlertSent", redisAlertSent);
        
        // 缓存统计
        HybridCacheManager.CacheStats cacheStats = hybridCacheManager.getStats();
        stats.put("l1HitCount", cacheStats.getL1HitCount());
        stats.put("l1MissCount", cacheStats.getL1MissCount());
        stats.put("l1HitRate", cacheStats.getL1HitRate());
        stats.put("l1Size", cacheStats.getL1Size());
        
        return stats;
    }

    /**
     * 重置监控统计
     */
    public void resetStats() {
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        redisFailures.set(0);
        fallbackUsages.set(0);
        
        log.info("缓存监控统计已重置");
    }

    /**
     * 记录模块缓存操作统计
     */
    public void recordModuleOperation(String module, boolean hit, boolean redisFailed, boolean fallbackUsed) {
        ModuleStats stats = moduleStats.computeIfAbsent(module, k -> new ModuleStats());
        stats.recordOperation(hit, redisFailed, fallbackUsed);

        // 同时记录全局统计
        recordCacheOperation(hit, redisFailed, fallbackUsed);
    }

    /**
     * 记录缓存类型操作统计
     */
    public void recordTypeOperation(GlobalCacheManager.CacheType type, boolean hit, boolean redisFailed, boolean fallbackUsed) {
        TypeStats stats = typeStats.computeIfAbsent(type, k -> new TypeStats());
        stats.recordOperation(hit, redisFailed, fallbackUsed);
    }

    /**
     * 获取模块统计信息
     */
    public Map<String, ModuleStats> getModuleStats() {
        return new HashMap<>(moduleStats);
    }

    /**
     * 获取类型统计信息
     */
    public Map<GlobalCacheManager.CacheType, TypeStats> getTypeStats() {
        return new HashMap<>(typeStats);
    }

    /**
     * 获取全局缓存监控统计
     */
    public GlobalCacheMonitoringStats getGlobalMonitoringStats() {
        GlobalCacheManager.GlobalCacheStats globalStats = globalCacheManager.getGlobalStats();

        return GlobalCacheMonitoringStats.builder()
                .globalStats(globalStats)
                .moduleStats(getModuleStats())
                .typeStats(getTypeStats())
                .totalRequests(totalRequests.get())
                .cacheHits(cacheHits.get())
                .cacheMisses(cacheMisses.get())
                .redisFailures(redisFailures.get())
                .fallbackUsages(fallbackUsages.get())
                .redisAvailable(circuitBreaker.isAvailable())
                .circuitBreakerState(circuitBreaker.getState().name())
                .lastRedisFailureTime(lastRedisFailureTime)
                .lastRedisRecoveryTime(lastRedisRecoveryTime)
                .redisAlertSent(redisAlertSent)
                .build();
    }

    /**
     * 模块统计信息
     */
    public static class ModuleStats {
        private final AtomicLong requests = new AtomicLong(0);
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong redisFailures = new AtomicLong(0);
        private final AtomicLong fallbackUsages = new AtomicLong(0);

        public void recordOperation(boolean hit, boolean redisFailed, boolean fallbackUsed) {
            requests.incrementAndGet();
            if (hit) {
                hits.incrementAndGet();
            } else {
                misses.incrementAndGet();
            }
            if (redisFailed) {
                redisFailures.incrementAndGet();
            }
            if (fallbackUsed) {
                fallbackUsages.incrementAndGet();
            }
        }

        // Getters
        public long getRequests() { return requests.get(); }
        public long getHits() { return hits.get(); }
        public long getMisses() { return misses.get(); }
        public long getRedisFailures() { return redisFailures.get(); }
        public long getFallbackUsages() { return fallbackUsages.get(); }
        public double getHitRate() {
            long total = requests.get();
            return total > 0 ? (double) hits.get() / total : 0.0;
        }
    }

    /**
     * 缓存类型统计信息
     */
    public static class TypeStats {
        private final AtomicLong requests = new AtomicLong(0);
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong redisFailures = new AtomicLong(0);
        private final AtomicLong fallbackUsages = new AtomicLong(0);

        public void recordOperation(boolean hit, boolean redisFailed, boolean fallbackUsed) {
            requests.incrementAndGet();
            if (hit) {
                hits.incrementAndGet();
            } else {
                misses.incrementAndGet();
            }
            if (redisFailed) {
                redisFailures.incrementAndGet();
            }
            if (fallbackUsed) {
                fallbackUsages.incrementAndGet();
            }
        }

        // Getters
        public long getRequests() { return requests.get(); }
        public long getHits() { return hits.get(); }
        public long getMisses() { return misses.get(); }
        public long getRedisFailures() { return redisFailures.get(); }
        public long getFallbackUsages() { return fallbackUsages.get(); }
        public double getHitRate() {
            long total = requests.get();
            return total > 0 ? (double) hits.get() / total : 0.0;
        }
    }

    /**
     * 全局缓存监控统计信息
     */
    public static class GlobalCacheMonitoringStats {
        private final GlobalCacheManager.GlobalCacheStats globalStats;
        private final Map<String, ModuleStats> moduleStats;
        private final Map<GlobalCacheManager.CacheType, TypeStats> typeStats;
        private final long totalRequests;
        private final long cacheHits;
        private final long cacheMisses;
        private final long redisFailures;
        private final long fallbackUsages;
        private final boolean redisAvailable;
        private final String circuitBreakerState;
        private final LocalDateTime lastRedisFailureTime;
        private final LocalDateTime lastRedisRecoveryTime;
        private final boolean redisAlertSent;

        private GlobalCacheMonitoringStats(Builder builder) {
            this.globalStats = builder.globalStats;
            this.moduleStats = builder.moduleStats;
            this.typeStats = builder.typeStats;
            this.totalRequests = builder.totalRequests;
            this.cacheHits = builder.cacheHits;
            this.cacheMisses = builder.cacheMisses;
            this.redisFailures = builder.redisFailures;
            this.fallbackUsages = builder.fallbackUsages;
            this.redisAvailable = builder.redisAvailable;
            this.circuitBreakerState = builder.circuitBreakerState;
            this.lastRedisFailureTime = builder.lastRedisFailureTime;
            this.lastRedisRecoveryTime = builder.lastRedisRecoveryTime;
            this.redisAlertSent = builder.redisAlertSent;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public GlobalCacheManager.GlobalCacheStats getGlobalStats() { return globalStats; }
        public Map<String, ModuleStats> getModuleStats() { return moduleStats; }
        public Map<GlobalCacheManager.CacheType, TypeStats> getTypeStats() { return typeStats; }
        public long getTotalRequests() { return totalRequests; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getRedisFailures() { return redisFailures; }
        public long getFallbackUsages() { return fallbackUsages; }
        public boolean isRedisAvailable() { return redisAvailable; }
        public String getCircuitBreakerState() { return circuitBreakerState; }
        public LocalDateTime getLastRedisFailureTime() { return lastRedisFailureTime; }
        public LocalDateTime getLastRedisRecoveryTime() { return lastRedisRecoveryTime; }
        public boolean isRedisAlertSent() { return redisAlertSent; }

        public static class Builder {
            private GlobalCacheManager.GlobalCacheStats globalStats;
            private Map<String, ModuleStats> moduleStats;
            private Map<GlobalCacheManager.CacheType, TypeStats> typeStats;
            private long totalRequests;
            private long cacheHits;
            private long cacheMisses;
            private long redisFailures;
            private long fallbackUsages;
            private boolean redisAvailable;
            private String circuitBreakerState;
            private LocalDateTime lastRedisFailureTime;
            private LocalDateTime lastRedisRecoveryTime;
            private boolean redisAlertSent;

            public Builder globalStats(GlobalCacheManager.GlobalCacheStats globalStats) { this.globalStats = globalStats; return this; }
            public Builder moduleStats(Map<String, ModuleStats> moduleStats) { this.moduleStats = moduleStats; return this; }
            public Builder typeStats(Map<GlobalCacheManager.CacheType, TypeStats> typeStats) { this.typeStats = typeStats; return this; }
            public Builder totalRequests(long totalRequests) { this.totalRequests = totalRequests; return this; }
            public Builder cacheHits(long cacheHits) { this.cacheHits = cacheHits; return this; }
            public Builder cacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; return this; }
            public Builder redisFailures(long redisFailures) { this.redisFailures = redisFailures; return this; }
            public Builder fallbackUsages(long fallbackUsages) { this.fallbackUsages = fallbackUsages; return this; }
            public Builder redisAvailable(boolean redisAvailable) { this.redisAvailable = redisAvailable; return this; }
            public Builder circuitBreakerState(String circuitBreakerState) { this.circuitBreakerState = circuitBreakerState; return this; }
            public Builder lastRedisFailureTime(LocalDateTime lastRedisFailureTime) { this.lastRedisFailureTime = lastRedisFailureTime; return this; }
            public Builder lastRedisRecoveryTime(LocalDateTime lastRedisRecoveryTime) { this.lastRedisRecoveryTime = lastRedisRecoveryTime; return this; }
            public Builder redisAlertSent(boolean redisAlertSent) { this.redisAlertSent = redisAlertSent; return this; }

            public GlobalCacheMonitoringStats build() { return new GlobalCacheMonitoringStats(this); }
        }
    }

    /**
     * 缓存健康指标
     */
    public static class CacheHealthMetrics {
        private final double l1HitRate;
        private final long l1Size;
        private final boolean redisAvailable;
        private final String circuitBreakerState;
        private final int failureCount;
        private final int successCount;
        private final long totalRequests;
        private final long cacheHits;
        private final long cacheMisses;
        private final long redisFailures;
        private final long fallbackUsages;

        private CacheHealthMetrics(Builder builder) {
            this.l1HitRate = builder.l1HitRate;
            this.l1Size = builder.l1Size;
            this.redisAvailable = builder.redisAvailable;
            this.circuitBreakerState = builder.circuitBreakerState;
            this.failureCount = builder.failureCount;
            this.successCount = builder.successCount;
            this.totalRequests = builder.totalRequests;
            this.cacheHits = builder.cacheHits;
            this.cacheMisses = builder.cacheMisses;
            this.redisFailures = builder.redisFailures;
            this.fallbackUsages = builder.fallbackUsages;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public double getL1HitRate() { return l1HitRate; }
        public long getL1Size() { return l1Size; }
        public boolean isRedisAvailable() { return redisAvailable; }
        public String getCircuitBreakerState() { return circuitBreakerState; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public long getTotalRequests() { return totalRequests; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getRedisFailures() { return redisFailures; }
        public long getFallbackUsages() { return fallbackUsages; }

        public static class Builder {
            private double l1HitRate;
            private long l1Size;
            private boolean redisAvailable;
            private String circuitBreakerState;
            private int failureCount;
            private int successCount;
            private long totalRequests;
            private long cacheHits;
            private long cacheMisses;
            private long redisFailures;
            private long fallbackUsages;

            public Builder l1HitRate(double l1HitRate) { this.l1HitRate = l1HitRate; return this; }
            public Builder l1Size(long l1Size) { this.l1Size = l1Size; return this; }
            public Builder redisAvailable(boolean redisAvailable) { this.redisAvailable = redisAvailable; return this; }
            public Builder circuitBreakerState(String circuitBreakerState) { this.circuitBreakerState = circuitBreakerState; return this; }
            public Builder failureCount(int failureCount) { this.failureCount = failureCount; return this; }
            public Builder successCount(int successCount) { this.successCount = successCount; return this; }
            public Builder totalRequests(long totalRequests) { this.totalRequests = totalRequests; return this; }
            public Builder cacheHits(long cacheHits) { this.cacheHits = cacheHits; return this; }
            public Builder cacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; return this; }
            public Builder redisFailures(long redisFailures) { this.redisFailures = redisFailures; return this; }
            public Builder fallbackUsages(long fallbackUsages) { this.fallbackUsages = fallbackUsages; return this; }

            public CacheHealthMetrics build() { return new CacheHealthMetrics(this); }
        }
    }
}
