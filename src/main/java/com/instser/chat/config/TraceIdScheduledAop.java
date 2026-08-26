package com.instser.chat.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 为 {@code @Scheduled} 定时任务注入独立 traceId。
 * <p>定时任务没有父 HTTP 请求，不经过 {@code TraceIdFilter}，MDC 为空。
 * 本切面在每次定时任务执行前生成独立 traceId 放入 MDC，执行完毕清理，
 * 使定时任务的日志也能按 traceId 聚合（每次执行一个独立 traceId）。
 *
 * @author instser
 * @since 2026-08-26
 */
@Aspect
@Component
public class TraceIdScheduledAop {

    /**
     * 拦截所有带 {@code @Scheduled} 注解的方法。
     * <p>注：Spring 的 {@code @Scheduled} 注解位于 {@code org.springframework.scheduling.annotation} 包。
     */
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduled(ProceedingJoinPoint pjp) throws Throwable {
        String traceId = MDC.get("traceId");
        boolean createdHere = false;
        // 若当前线程已有 traceId（如被 @Async 线程池调度），复用而非覆盖
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put("traceId", traceId);
            createdHere = true;
        }
        try {
            return pjp.proceed();
        } finally {
            if (createdHere) {
                MDC.remove("traceId");
            }
        }
    }
}
