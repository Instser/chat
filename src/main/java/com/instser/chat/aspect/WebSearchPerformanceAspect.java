package com.instser.chat.aspect;

import com.instser.chat.annotation.WebSearchMonitoring;
import com.instser.chat.common.exception.WebSearchException;
import com.instser.chat.metrics.WebSearchMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 网络搜索性能监控切面
 *
 * @author instser
 * @since 2025-07-20
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WebSearchPerformanceAspect {

    private final WebSearchMetrics webSearchMetrics;

    /**
     * 监控网络搜索相关方法的性能
     */
    @Around("@annotation(webSearchMonitoring)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint, WebSearchMonitoring webSearchMonitoring) throws Throwable {
        Timer.Sample sample = webSearchMetrics.startTimer();
        String methodName = joinPoint.getSignature().getName();
        String operationName = webSearchMonitoring.value().isEmpty() ? methodName : webSearchMonitoring.value();

        log.debug("开始监控网络搜索操作: {}", operationName);

        try {
            Object result = joinPoint.proceed();
            webSearchMetrics.recordSuccess(sample);
            log.debug("网络搜索操作成功完成: {}", operationName);
            return result;
        } catch (WebSearchException e) {
            String errorCode = e.getErrorCode().name();
            webSearchMetrics.recordError(sample, errorCode);
            log.warn("网络搜索操作业务异常: {}, 错误码: {}, 错误信息: {}", operationName, errorCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            webSearchMetrics.recordError(sample, "UNKNOWN_ERROR");
            log.error("网络搜索操作系统异常: {}, 错误信息: {}", operationName, e.getMessage(), e);
            throw e;
        }
    }
}
