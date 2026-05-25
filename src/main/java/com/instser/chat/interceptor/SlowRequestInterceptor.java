package com.instser.chat.interceptor;

import com.instser.chat.metrics.ApiMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class SlowRequestInterceptor implements HandlerInterceptor {

    private static final String START_TIME_KEY = "req.startTime";
    private static final long WARN_THRESHOLD_MS = 1000;
    private static final long ERROR_THRESHOLD_MS = 3000;

    private final MeterRegistry registry;
    private final ApiMetrics apiMetrics;
    private final ConcurrentMap<String, Counter> slowRequestCounters = new ConcurrentHashMap<>();

    public SlowRequestInterceptor(MeterRegistry registry, ApiMetrics apiMetrics) {
        this.registry = registry;
        this.apiMetrics = apiMetrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_TIME_KEY, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_KEY);
        if (startTime == null) return;

        long costMs = System.currentTimeMillis() - startTime;
        String method = request.getMethod();
        String path = getPatternPath(request);
        String traceId = MDC.get("traceId");
        int status = response.getStatus();

        if (costMs > ERROR_THRESHOLD_MS) {
            getOrCreateSlowCounter("error").increment();
            log.error("SLOW_REQUEST_ERROR method={} path={} costMs={} status={} traceId={}",
                    method, path, costMs, status, traceId);
        } else if (costMs > WARN_THRESHOLD_MS) {
            getOrCreateSlowCounter("warn").increment();
            log.warn("SLOW_REQUEST_WARN method={} path={} costMs={} status={} traceId={}",
                    method, path, costMs, status, traceId);
        }

        apiMetrics.recordRequest(method, path, status);
        if (ex != null) {
            apiMetrics.recordException(method, path, ex.getClass().getSimpleName());
        }
    }

    private Counter getOrCreateSlowCounter(String threshold) {
        return slowRequestCounters.computeIfAbsent(threshold, k ->
                Counter.builder("slow_requests_total")
                        .description("慢请求计数")
                        .tag("threshold", k)
                        .register(registry)
        );
    }

    private String getPatternPath(HttpServletRequest request) {
        Object pattern = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }
}
