package com.instser.chat.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网络搜索内容修改性能指标
 *
 * @author instser
 * @since 2025-07-20
 */
@Slf4j
@Component
public class WebSearchMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer requestTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Counter rateLimitCounter;
    private final Counter concurrentConflictCounter;
    private final Counter redisFailoverCounter;
    private final Gauge activeRequestsGauge;
    
    // 活跃请求数计数器
    private final AtomicInteger activeRequestCount = new AtomicInteger(0);

    public WebSearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 响应时间指标
        this.requestTimer = Timer.builder("web_search_update_duration_seconds")
                .description("网络搜索内容更新请求处理时间")
                .tag("operation", "update")
                .register(meterRegistry);

        // 成功请求计数
        this.successCounter = Counter.builder("web_search_update_requests_total")
                .description("网络搜索内容更新请求总数")
                .tag("status", "success")
                .register(meterRegistry);

        // 错误请求计数 - 移除固定tag，支持动态错误码
        this.errorCounter = Counter.builder("web_search_update_errors_total")
                .description("网络搜索内容更新错误总数")
                .register(meterRegistry);

        // 频率限制计数
        this.rateLimitCounter = Counter.builder("web_search_update_rate_limited_total")
                .description("被频率限制的请求数")
                .register(meterRegistry);

        // 并发冲突计数
        this.concurrentConflictCounter = Counter.builder("web_search_update_concurrent_conflicts_total")
                .description("并发修改冲突次数")
                .register(meterRegistry);

        // Redis降级计数
        this.redisFailoverCounter = Counter.builder("web_search_update_redis_fallback_total")
                .description("Redis降级次数")
                .register(meterRegistry);

        // 活跃请求数
        this.activeRequestsGauge = Gauge.builder("web_search_update_active_requests", activeRequestCount, AtomicInteger::get)
                .description("当前活跃的更新请求数")
                .register(meterRegistry);
    }

    /**
     * 开始计时
     */
    public Timer.Sample startTimer() {
        activeRequestCount.incrementAndGet();
        return Timer.start(meterRegistry);
    }

    /**
     * 记录成功请求
     */
    public void recordSuccess(Timer.Sample sample) {
        sample.stop(requestTimer);
        successCounter.increment();
        activeRequestCount.decrementAndGet();
    }

    /**
     * 记录错误请求
     */
    public void recordError(Timer.Sample sample, String errorCode) {
        sample.stop(requestTimer);

        // 使用动态标签记录不同类型的错误
        Counter.builder("web_search_update_errors_total")
                .description("网络搜索内容更新错误总数")
                .tag("error_code", errorCode)
                .register(meterRegistry)
                .increment();

        // 同时增加总错误计数
        errorCounter.increment();
        activeRequestCount.decrementAndGet();
    }

    /**
     * 记录频率限制
     */
    public void recordRateLimit() {
        rateLimitCounter.increment();
    }

    /**
     * 记录并发冲突
     */
    public void recordConcurrentConflict() {
        concurrentConflictCounter.increment();
    }

    /**
     * 记录Redis降级
     */
    public void recordRedisFailover() {
        redisFailoverCounter.increment();
    }



    /**
     * 获取指标统计信息
     */
    public String getMetricsStats() {
        double totalRequests = successCounter.count() + errorCounter.count();
        double successRate = totalRequests > 0 ? (successCounter.count() / totalRequests) * 100 : 0;

        return String.format("WebSearch指标 - 总请求: %.0f, 成功: %.0f, 错误: %.0f, 成功率: %.2f%%, " +
                "频率限制: %.0f, 并发冲突: %.0f, Redis降级: %.0f, 活跃请求: %d",
                totalRequests,
                successCounter.count(),
                errorCounter.count(),
                successRate,
                rateLimitCounter.count(),
                concurrentConflictCounter.count(),
                redisFailoverCounter.count(),
                activeRequestCount.get());
    }

    /**
     * 获取详细的错误统计信息
     */
    public String getErrorBreakdown() {
        try {
            StringBuilder breakdown = new StringBuilder("错误分类统计: ");

            // 这里可以通过MeterRegistry获取按错误码分类的统计
            meterRegistry.getMeters().stream()
                    .filter(meter -> meter.getId().getName().equals("web_search_update_errors_total"))
                    .filter(meter -> meter.getId().getTag("error_code") != null)
                    .forEach(meter -> {
                        String errorCode = meter.getId().getTag("error_code");
                        double count = ((Counter) meter).count();
                        if (count > 0) {
                            breakdown.append(String.format("%s: %.0f, ", errorCode, count));
                        }
                    });

            String result = breakdown.toString();
            return result.endsWith(", ") ? result.substring(0, result.length() - 2) : result;
        } catch (Exception e) {
            log.warn("获取错误分类统计失败", e);
            return "错误分类统计获取失败";
        }
    }
}
