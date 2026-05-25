package com.instser.chat.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ApiMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ApiMetrics apiMetrics = new ApiMetrics(registry);

    @Test
    void shouldCreateAndIncrementRequestCounter() {
        apiMetrics.recordRequest("GET", "/api/chat/sessions", 200);
        apiMetrics.recordRequest("GET", "/api/chat/sessions", 200);

        Counter counter = registry.find("api_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/chat/sessions")
                .tag("status", "200")
                .counter();
        assertNotNull(counter, "Counter 应被注册");
        assertEquals(2.0, counter.count(), "两次调用后 count 应为 2");
    }

    @Test
    void shouldCreateAndIncrementExceptionCounter() {
        apiMetrics.recordException("POST", "/api/chat", "RuntimeException");
        apiMetrics.recordException("POST", "/api/chat", "RuntimeException");

        Counter counter = registry.find("api_exceptions_total")
                .tag("method", "POST")
                .tag("path", "/api/chat")
                .tag("exception_type", "RuntimeException")
                .counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    void shouldReuseCounterForSameLabels() {
        // shouldCreateAndIncrementRequestCounter 已跑过两次 recordRequest
        // 同一标签再次调用，count 继续递增（说明复用而非新建）
        apiMetrics.recordRequest("GET", "/api/chat/sessions", 200);
        Counter counter = registry.find("api_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/chat/sessions")
                .tag("status", "200")
                .counter();
        assertEquals(3.0, counter.count(), "同标签应复用已有 Counter");
    }
}
