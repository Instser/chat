package com.instser.chat.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiCallMetricsTest {

    private MeterRegistry registry;
    private AiCallMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiCallMetrics(registry, new AiCallWindowStore());
    }

    @Test
    void shouldIncrementTotalCallsOnStartCall() {
        Timer.Sample sample = metrics.startCall();

        assertNotNull(sample, "startCall 应返回非空 Timer.Sample");
        Counter successCounter = registry.find("ai_calls_total")
                .tag("status", "success")
                .counter();
        assertNotNull(successCounter, "success counter 在构造时已注册");
        assertEquals(0.0, successCounter.count(), "尚未记录成功，count 应为 0");
    }

    @Test
    void shouldRecordSuccessAndUpdateCounters() {
        Timer.Sample sample = metrics.startCall();
        metrics.recordSuccess(sample, "gpt-4", 500, 3000, 1000, 500);

        Counter successCounter = registry.find("ai_calls_total")
                .tag("status", "success")
                .counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count());

        Counter promptTokens = registry.find("ai_token_consumption_total")
                .tag("type", "prompt")
                .counter();
        assertNotNull(promptTokens);
        assertEquals(1000.0, promptTokens.count());

        Counter completionTokens = registry.find("ai_token_consumption_total")
                .tag("type", "completion")
                .counter();
        assertNotNull(completionTokens);
        assertEquals(500.0, completionTokens.count());

        Timer duration = registry.find("ai_call_duration_seconds")
                .tag("model", "gpt-4")
                .tag("status", "success")
                .timer();
        assertNotNull(duration);
        assertTrue(duration.count() > 0);

        Timer ttft = registry.find("ai_ttft_seconds")
                .tag("model", "gpt-4")
                .timer();
        assertNotNull(ttft);
        assertTrue(ttft.count() > 0);
    }

    @Test
    void shouldRecordFailureAndUpdateFailureCounters() {
        Timer.Sample sample = metrics.startCall();
        metrics.recordFailure(sample, "gemini-pro", "timeout", "Connection timed out");

        Counter failedCounter = registry.find("ai_calls_total")
                .tag("status", "failed")
                .counter();
        assertNotNull(failedCounter);
        assertEquals(1.0, failedCounter.count());

        Counter failureDetail = registry.find("ai_calls_failed_total")
                .tag("model", "gemini-pro")
                .tag("error_type", "timeout")
                .counter();
        assertNotNull(failureDetail);
        assertEquals(1.0, failureDetail.count());
    }

    @Test
    void shouldTrackActiveCallsGauge() {
        Gauge gauge = registry.find("ai_calls_active").gauge();
        assertNotNull(gauge);

        metrics.startCall();
        metrics.startCall();

        assertEquals(2.0, gauge.value(), "启动两次后 active 应为 2");

        Timer.Sample sample = metrics.startCall();
        metrics.recordSuccess(sample, "gpt-4", 100, 1000, 100, 50);

        assertEquals(2.0, gauge.value(), "完成一次后 active 应为 2");
    }

    @Test
    void shouldHandleMultipleModels() {
        Timer.Sample s1 = metrics.startCall();
        metrics.recordSuccess(s1, "gpt-4", 300, 2000, 500, 200);

        Timer.Sample s2 = metrics.startCall();
        metrics.recordFailure(s2, "claude-3", "api_error", "Rate limited");

        Timer gpt4Timer = registry.find("ai_call_duration_seconds")
                .tag("model", "gpt-4")
                .timer();
        assertNotNull(gpt4Timer);

        Timer claudeTimer = registry.find("ai_call_duration_seconds")
                .tag("model", "claude-3")
                .timer();
        assertNotNull(claudeTimer);
    }
}
