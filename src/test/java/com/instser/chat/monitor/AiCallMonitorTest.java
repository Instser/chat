package com.instser.chat.monitor;

import com.instser.chat.metrics.AiCallMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiCallMonitorTest {

    private MeterRegistry registry;
    private AiCallMetrics metrics;
    private AiCallMonitor monitor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiCallMetrics(registry);
        monitor = new AiCallMonitor(metrics);
    }

    @Test
    void shouldReturnNonNullCallContextOnBeginCall() {
        AiCallMonitor.CallContext ctx = monitor.beginCall("gpt-4");

        assertNotNull(ctx, "beginCall 应返回非空 CallContext");
    }

    @Test
    void shouldRecordSuccessCallViaMonitor() {
        AiCallMonitor.CallContext ctx = monitor.beginCall("gpt-4");
        monitor.markFirstToken(ctx);
        monitor.recordSuccess(ctx);

        Counter successCounter = registry.find("ai_calls_total")
                .tag("status", "success")
                .counter();
        assertNotNull(successCounter, "成功调用后应有 success counter");
        assertEquals(1.0, successCounter.count());
    }

    @Test
    void shouldRecordFailedCallViaMonitor() {
        AiCallMonitor.CallContext ctx = monitor.beginCall("claude-3");
        monitor.recordFailure(ctx, "timeout", "Read timed out");

        Counter failedCounter = registry.find("ai_calls_total")
                .tag("status", "failed")
                .counter();
        assertNotNull(failedCounter);
        assertEquals(1.0, failedCounter.count());

        Counter detailCounter = registry.find("ai_calls_failed_total")
                .tag("model", "claude-3")
                .tag("error_type", "timeout")
                .counter();
        assertNotNull(detailCounter);
        assertEquals(1.0, detailCounter.count());
    }

    @Test
    void shouldMarkFirstTokenAndRecordSuccessWithoutError() {
        AiCallMonitor.CallContext ctx = monitor.beginCall("gemini-pro");
        monitor.markFirstToken(ctx);
        monitor.recordSuccess(ctx);

        assertNotNull(ctx, "CallContext 应非空");
    }

    @Test
    void shouldRecordFailureWithoutFirstToken() {
        AiCallMonitor.CallContext ctx = monitor.beginCall("gpt-4");
        monitor.recordFailure(ctx, "connection_error", "Connection refused");

        Counter failedCounter = registry.find("ai_calls_total")
                .tag("status", "failed")
                .counter();
        assertNotNull(failedCounter);
        assertEquals(1.0, failedCounter.count());
    }

    @Test
    void shouldTrackCallContextTokens() {
        AiCallMonitor.CallContext ctx = monitor.beginCall("gpt-4");
        ctx.addTokens(200, 100);
        ctx.addTokens(50, 30);
        monitor.recordSuccess(ctx);

        Counter promptTokens = registry.find("ai_token_consumption_total")
                .tag("type", "prompt")
                .counter();
        assertNotNull(promptTokens);
        assertEquals(250.0, promptTokens.count());

        Counter completionTokens = registry.find("ai_token_consumption_total")
                .tag("type", "completion")
                .counter();
        assertNotNull(completionTokens);
        assertEquals(130.0, completionTokens.count());
    }
}
