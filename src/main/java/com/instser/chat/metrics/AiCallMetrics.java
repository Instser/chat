package com.instser.chat.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AiCallMetrics {

    private final MeterRegistry registry;

    private final Counter totalCalls;
    private final Counter successCalls;
    private final Counter failedCalls;
    private final Counter promptTokens;
    private final Counter completionTokens;
    private final AtomicLong activeCalls = new AtomicLong(0);

    public AiCallMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.totalCalls = Counter.builder("ai_calls_total")
                .description("AI 调用总数").register(registry);
        this.successCalls = Counter.builder("ai_calls_total")
                .description("AI 调用总数").tag("status", "success").register(registry);
        this.failedCalls = Counter.builder("ai_calls_total")
                .description("AI 调用总数").tag("status", "failed").register(registry);
        this.promptTokens = Counter.builder("ai_token_consumption_total")
                .description("Token 消耗量").tag("type", "prompt").register(registry);
        this.completionTokens = Counter.builder("ai_token_consumption_total")
                .description("Token 消耗量").tag("type", "completion").register(registry);

        Gauge.builder("ai_calls_active", activeCalls, AtomicLong::get)
                .description("当前活跃 AI 调用数").register(registry);
    }

    public Timer.Sample startCall() {
        activeCalls.incrementAndGet();
        totalCalls.increment();
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String model,
                               long ttftMs, long totalMs, long promptTok, long completionTok) {
        sample.stop(Timer.builder("ai_call_duration_seconds")
                .tag("model", model).tag("status", "success")
                .register(registry));
        Timer.builder("ai_ttft_seconds")
                .tag("model", model)
                .register(registry)
                .record(ttftMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        successCalls.increment();
        promptTokens.increment(promptTok);
        completionTokens.increment(completionTok);
        activeCalls.decrementAndGet();
    }

    public void recordFailure(Timer.Sample sample, String model,
                               String errorType, String errorMessage) {
        sample.stop(Timer.builder("ai_call_duration_seconds")
                .tag("model", model).tag("status", "failed")
                .register(registry));
        Counter.builder("ai_calls_failed_total")
                .tag("model", model).tag("error_type", errorType)
                .register(registry).increment();
        failedCalls.increment();
        activeCalls.decrementAndGet();
    }
}
