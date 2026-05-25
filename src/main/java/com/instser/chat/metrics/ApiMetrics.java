package com.instser.chat.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ApiMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> exceptionCounters = new ConcurrentHashMap<>();

    public ApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String method, String path, int statusCode) {
        String key = method + ":" + path + ":" + statusCode;
        requestCounters.computeIfAbsent(key, k ->
                Counter.builder("api_requests_total")
                        .description("API 请求总数")
                        .tag("method", method)
                        .tag("path", path)
                        .tag("status", String.valueOf(statusCode))
                        .register(registry)
        ).increment();
    }

    public void recordException(String method, String path, String exceptionType) {
        String key = method + ":" + path + ":" + exceptionType;
        exceptionCounters.computeIfAbsent(key, k ->
                Counter.builder("api_exceptions_total")
                        .description("API 异常总数")
                        .tag("method", method)
                        .tag("path", path)
                        .tag("exception_type", exceptionType)
                        .register(registry)
        ).increment();
    }
}
