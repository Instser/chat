package com.instser.chat.interceptor;

import com.instser.chat.metrics.ApiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SlowRequestInterceptorTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ApiMetrics apiMetrics = new ApiMetrics(registry);
    private final SlowRequestInterceptor interceptor = new SlowRequestInterceptor(registry, apiMetrics);

    @Test
    void shouldNotCreateSlowRequestCounterWhenUnderThreshold() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 500);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(registry.find("slow_requests_total").counter());
    }

    @Test
    void shouldRecordSlowRequestWarnWhenOverOneSecond() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/api/slow-endpoint");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 1500);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNotNull(registry.find("slow_requests_total").tag("threshold", "warn").counter());
        assertEquals(1.0, registry.find("slow_requests_total").tag("threshold", "warn").counter().count());
    }

    @Test
    void shouldRecordSlowRequestErrorWhenOverThreeSeconds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRequestURI("/api/very-slow");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 3500);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNotNull(registry.find("slow_requests_total").tag("threshold", "error").counter());
        assertEquals(1.0, registry.find("slow_requests_total").tag("threshold", "error").counter().count());
    }

    @Test
    void shouldCallApiMetricsOnCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/test");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 100);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNotNull(registry.find("api_requests_total")
                .tag("method", "GET").tag("path", "/api/test").tag("status", "200").counter());
    }

    @Test
    void shouldCallApiMetricsExceptionOnError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRequestURI("/api/error");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/error");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 200);

        interceptor.afterCompletion(request, response, new Object(),
                new RuntimeException("test"));

        assertNotNull(registry.find("api_exceptions_total")
                .tag("method", "POST").tag("path", "/api/error")
                .tag("exception_type", "RuntimeException").counter());
    }
}
