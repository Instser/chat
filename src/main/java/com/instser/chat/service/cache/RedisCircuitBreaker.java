package com.instser.chat.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis熔断器
 * 实现Redis连接状态监控、自动熔断和恢复机制
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Component
public class RedisCircuitBreaker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // 熔断器状态
    public enum State {
        CLOSED,     // 关闭状态（正常）
        OPEN,       // 开启状态（熔断）
        HALF_OPEN   // 半开状态（尝试恢复）
    }

    private volatile State state = State.CLOSED;
    
    // 配置参数
    @Value("${payment.cache.circuit-breaker.failure-threshold:3}")
    private int failureThreshold;
    
    @Value("${payment.cache.circuit-breaker.recovery-timeout:30000}")
    private long recoveryTimeoutMs;
    
    @Value("${payment.cache.circuit-breaker.success-threshold:2}")
    private int successThreshold;
    
    @Value("${payment.cache.circuit-breaker.health-check-interval:10000}")
    private long healthCheckIntervalMs;

    // 统计计数器
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);

    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public RedisCircuitBreaker(RedisTemplate<String, Object> redisTemplate, 
                              ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        // 启动健康检查定时任务
        scheduler.scheduleWithFixedDelay(
            this::performHealthCheck,
            healthCheckIntervalMs,
            healthCheckIntervalMs,
            TimeUnit.MILLISECONDS
        );

        // 启动状态监控定时任务
        scheduler.scheduleWithFixedDelay(
            this::monitorState,
            30000, // 30秒后开始
            60000, // 每分钟执行一次
            TimeUnit.MILLISECONDS
        );

        log.info("Redis熔断器初始化完成，失败阈值: {}, 恢复超时: {}ms, 成功阈值: {}", 
                failureThreshold, recoveryTimeoutMs, successThreshold);
    }

    /**
     * 检查Redis是否可用
     * 
     * @return 是否可用
     */
    public boolean isAvailable() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否到了尝试恢复的时间
                if (System.currentTimeMillis() - lastFailureTime.get() >= recoveryTimeoutMs) {
                    log.info("Redis熔断器进入半开状态，尝试恢复连接");
                    state = State.HALF_OPEN;
                    successCount.set(0);
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return false;
        }
    }

    /**
     * 记录成功操作
     */
    public void recordSuccess() {
        lastSuccessTime.set(System.currentTimeMillis());
        
        switch (state) {
            case CLOSED:
                // 正常状态，重置失败计数
                failureCount.set(0);
                break;
            case HALF_OPEN:
                // 半开状态，累计成功次数
                int currentSuccessCount = successCount.incrementAndGet();
                if (currentSuccessCount >= successThreshold) {
                    log.info("Redis熔断器恢复正常，连续成功次数: {}", currentSuccessCount);
                    state = State.CLOSED;
                    failureCount.set(0);
                    successCount.set(0);
                    
                    // 发布恢复事件
                    eventPublisher.publishEvent(new RedisRecoveryEvent(this));
                }
                break;
            case OPEN:
                // 开启状态不处理成功记录
                break;
        }
    }

    /**
     * 记录失败操作
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        
        switch (state) {
            case CLOSED:
                // 正常状态，累计失败次数
                int currentFailureCount = failureCount.incrementAndGet();
                if (currentFailureCount >= failureThreshold) {
                    log.warn("Redis熔断器开启，连续失败次数: {}", currentFailureCount);
                    state = State.OPEN;
                    
                    // 发布熔断事件
                    eventPublisher.publishEvent(new RedisCircuitBreakerEvent(this, true));
                }
                break;
            case HALF_OPEN:
                // 半开状态遇到失败，立即回到开启状态
                log.warn("Redis熔断器半开状态失败，回到开启状态");
                state = State.OPEN;
                successCount.set(0);
                
                // 发布熔断事件
                eventPublisher.publishEvent(new RedisCircuitBreakerEvent(this, true));
                break;
            case OPEN:
                // 开启状态不处理失败记录
                break;
        }
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        try {
            // 只在熔断状态下执行健康检查
            if (state != State.OPEN) {
                return;
            }

            // 执行简单的Redis ping操作
            String testKey = "circuit-breaker:health-check:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(testKey, "ping", Duration.ofSeconds(5));
            String result = (String) redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);

            if ("ping".equals(result)) {
                log.debug("Redis健康检查成功");
                // 健康检查成功，但不直接恢复，等待正常业务操作触发恢复
            }

        } catch (Exception e) {
            log.debug("Redis健康检查失败: {}", e.getMessage());
        }
    }

    /**
     * 监控状态变化
     */
    private void monitorState() {
        try {
            log.debug("Redis熔断器状态监控 - 状态: {}, 失败次数: {}, 成功次数: {}, 最后失败时间: {}, 最后成功时间: {}",
                    state,
                    failureCount.get(),
                    successCount.get(),
                    lastFailureTime.get() > 0 ? LocalDateTime.now().minusNanos((System.currentTimeMillis() - lastFailureTime.get()) * 1_000_000) : "无",
                    lastSuccessTime.get() > 0 ? LocalDateTime.now().minusNanos((System.currentTimeMillis() - lastSuccessTime.get()) * 1_000_000) : "无"
            );

        } catch (Exception e) {
            log.warn("Redis熔断器状态监控异常", e);
        }
    }

    /**
     * 获取当前状态
     * 
     * @return 熔断器状态
     */
    public State getState() {
        return state;
    }

    /**
     * 获取失败次数
     * 
     * @return 失败次数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取成功次数
     * 
     * @return 成功次数
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取最后失败时间
     * 
     * @return 最后失败时间戳
     */
    public long getLastFailureTime() {
        return lastFailureTime.get();
    }

    /**
     * 获取最后成功时间
     * 
     * @return 最后成功时间戳
     */
    public long getLastSuccessTime() {
        return lastSuccessTime.get();
    }

    /**
     * 手动重置熔断器（用于测试或紧急恢复）
     */
    public void reset() {
        log.info("手动重置Redis熔断器");
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        lastSuccessTime.set(System.currentTimeMillis());
        
        // 发布重置事件
        eventPublisher.publishEvent(new RedisCircuitBreakerResetEvent(this));
    }

    /**
     * 手动触发熔断（用于测试或紧急情况）
     */
    public void forceOpen() {
        log.warn("手动触发Redis熔断器开启");
        state = State.OPEN;
        lastFailureTime.set(System.currentTimeMillis());
        
        // 发布熔断事件
        eventPublisher.publishEvent(new RedisCircuitBreakerEvent(this, true));
    }

    /**
     * 获取熔断器统计信息
     * 
     * @return 统计信息
     */
    public CircuitBreakerStats getStats() {
        return CircuitBreakerStats.builder()
                .state(state.name())
                .failureCount(failureCount.get())
                .successCount(successCount.get())
                .lastFailureTime(lastFailureTime.get())
                .lastSuccessTime(lastSuccessTime.get())
                .failureThreshold(failureThreshold)
                .successThreshold(successThreshold)
                .recoveryTimeoutMs(recoveryTimeoutMs)
                .build();
    }

    /**
     * 销毁资源
     */
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Redis熔断器资源已释放");
    }

    /**
     * 熔断器统计信息
     */
    public static class CircuitBreakerStats {
        private final String state;
        private final int failureCount;
        private final int successCount;
        private final long lastFailureTime;
        private final long lastSuccessTime;
        private final int failureThreshold;
        private final int successThreshold;
        private final long recoveryTimeoutMs;

        private CircuitBreakerStats(Builder builder) {
            this.state = builder.state;
            this.failureCount = builder.failureCount;
            this.successCount = builder.successCount;
            this.lastFailureTime = builder.lastFailureTime;
            this.lastSuccessTime = builder.lastSuccessTime;
            this.failureThreshold = builder.failureThreshold;
            this.successThreshold = builder.successThreshold;
            this.recoveryTimeoutMs = builder.recoveryTimeoutMs;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getState() { return state; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public long getLastFailureTime() { return lastFailureTime; }
        public long getLastSuccessTime() { return lastSuccessTime; }
        public int getFailureThreshold() { return failureThreshold; }
        public int getSuccessThreshold() { return successThreshold; }
        public long getRecoveryTimeoutMs() { return recoveryTimeoutMs; }

        public static class Builder {
            private String state;
            private int failureCount;
            private int successCount;
            private long lastFailureTime;
            private long lastSuccessTime;
            private int failureThreshold;
            private int successThreshold;
            private long recoveryTimeoutMs;

            public Builder state(String state) { this.state = state; return this; }
            public Builder failureCount(int failureCount) { this.failureCount = failureCount; return this; }
            public Builder successCount(int successCount) { this.successCount = successCount; return this; }
            public Builder lastFailureTime(long lastFailureTime) { this.lastFailureTime = lastFailureTime; return this; }
            public Builder lastSuccessTime(long lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; return this; }
            public Builder failureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; return this; }
            public Builder successThreshold(int successThreshold) { this.successThreshold = successThreshold; return this; }
            public Builder recoveryTimeoutMs(long recoveryTimeoutMs) { this.recoveryTimeoutMs = recoveryTimeoutMs; return this; }

            public CircuitBreakerStats build() { return new CircuitBreakerStats(this); }
        }
    }
}
