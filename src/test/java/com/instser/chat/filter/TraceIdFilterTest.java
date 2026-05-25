package com.instser.chat.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderNotPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            // 断言：在 filter chain 执行期间，MDC 中有 traceId
            String mdcTraceId = MDC.get("traceId");
            assertNotNull(mdcTraceId, "MDC 中应有 traceId");
            assertFalse(mdcTraceId.isBlank(), "traceId 不应为空");
        };

        filter.doFilterInternal(request, response, chain);

        // 断言：响应头包含 X-Trace-Id
        String headerTraceId = response.getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头 X-Trace-Id 不应为空");
        assertFalse(headerTraceId.isBlank(), "响应头 X-Trace-Id 不应为空");
    }

    @Test
    void shouldUseExistingTraceIdWhenHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "incoming-trace-id-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            String mdcTraceId = MDC.get("traceId");
            assertEquals("incoming-trace-id-12345", mdcTraceId,
                    "MDC 中应为请求头传入的 traceId");
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals("incoming-trace-id-12345", response.getHeader("X-Trace-Id"),
                "响应头应返回与请求相同的 traceId");
    }

    @Test
    void shouldCleanupMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
        };

        filter.doFilterInternal(request, response, chain);

        // 断言：filter 执行完毕后 MDC 中的 traceId 已被清除
        assertNull(MDC.get("traceId"), "请求结束后 MDC 中的 traceId 应被清除");
    }
}
