package com.instser.chat.common.result;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class ResultTraceIdTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldCaptureTraceIdFromMdcInConstructor() {
        MDC.put("traceId", "test-trace-id-abc");

        Result<String> result = new Result<>();

        assertEquals("test-trace-id-abc", result.getTraceId(),
                "Result 应从 MDC 中自动获取 traceId");
    }

    @Test
    void shouldHaveNullTraceIdWhenMdcEmpty() {
        Result<String> result = new Result<>();

        assertNull(result.getTraceId(),
                "MDC 无 traceId 时 Result.traceId 应为 null");
    }

    @Test
    void shouldIncludeTraceIdInSuccessResponse() {
        MDC.put("traceId", "success-trace-123");

        Result<String> result = Result.success("data");

        assertEquals("success-trace-123", result.getTraceId(),
                "Result.success() 构造的响应应包含 traceId");
    }

    @Test
    void shouldIncludeTraceIdInFailedResponse() {
        MDC.put("traceId", "failed-trace-456");

        Result<String> result = Result.failed("error");

        assertEquals("failed-trace-456", result.getTraceId(),
                "Result.failed() 构造的响应应包含 traceId");
    }
}
