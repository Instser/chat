package com.instser.chat.common.exception;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTraceIdTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldIncludeTraceIdInExceptionResponse() {
        MDC.put("traceId", "exception-trace-789");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");

        Result<String> result = handler.handleException(
                new RuntimeException("test error"), request);

        assertNotNull(result.getTraceId(),
                "异常响应应包含 traceId");
        assertTrue(result.getMessage().contains("exception-trace-789"),
                "异常响应消息应包含 traceId 值: " + result.getMessage());
        assertEquals(ResultCode.FAILED.getCode(), result.getCode(),
                "异常响应状态码应为 FAILED");
    }

    @Test
    void shouldNotBreakWhenMdcHasNoTraceId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");

        // MDC 中没有 traceId 时不应抛异常
        Result<String> result = handler.handleException(
                new RuntimeException("test error"), request);

        assertNotNull(result, "即使 MDC 无 traceId 也应正常返回 Result");
        assertEquals(ResultCode.FAILED.getCode(), result.getCode());
        // traceId 可能为 null 或 "null"，取决于实现，这里只验证不抛异常
    }
}
