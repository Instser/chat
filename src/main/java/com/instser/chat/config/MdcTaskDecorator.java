package com.instser.chat.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;
import java.util.UUID;

/**
 * MDC 上下文传递装饰器。
 * <p>用于 {@code @Async} 线程池：在提交任务时捕获提交线程的 MDC 快照，
 * 在执行线程上恢复，执行完毕后清理，避免 traceId 等诊断上下文在线程切换后丢失。
 *
 * <p>原理：Spring 的 {@link TaskDecorator} 会包装提交到线程池的每个 {@link Runnable}。
 * 在 {@code decorate} 时（仍在提交线程）捕获 MDC，在 {@code run} 时（已在执行线程）恢复。
 *
 * <p>若提交线程 MDC 为空（如 {@code @EventListener(ApplicationReadyEvent)} 启动场景
 * 或非 HTTP 线程提交），为执行线程生成一个独立 traceId，保证异步任务日志始终有 traceId。
 *
 * @author instser
 * @since 2026-08-26
 */
public class MdcTaskDecorator implements TaskDecorator {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在提交线程捕获 MDC 快照（ThreadLocal 不可跨线程，需拷贝 Map）
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        boolean parentHasTraceId = contextMap != null && contextMap.containsKey(TRACE_ID_KEY)
                && !contextMap.get(TRACE_ID_KEY).isBlank();
        return () -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }
            // 提交线程无 traceId 时，为异步任务生成独立 traceId（启动预热等场景）
            if (!parentHasTraceId) {
                MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString().replace("-", ""));
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
