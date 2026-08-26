package com.instser.chat.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.MDC;

/**
 * Reactor / CompletableFuture 异步链路 MDC 恢复工具。
 * <p>异步任务切换到 ForkJoinPool / Reactor 调度线程后，
 * ThreadLocal 的 MDC 不会跟随传递，导致切换后线程的日志丢失 traceId。
 * 本工具在调用方捕获 traceId，在异步回调执行前恢复 MDC、执行后清理。
 *
 * @author instser
 * @since 2026-08-26
 */
public final class ReactorMdcUtils {

    public static final String TRACE_ID_KEY = "traceId";

    private ReactorMdcUtils() {
    }

    /**
     * 捕获当前线程的 traceId，返回可跨线程传递的快照字符串。
     */
    public static String captureTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 在指定 traceId 上下文中执行 Runnable，执行前恢复 MDC，执行后清理。
     * 用于 Reactor 链回调（doOnNext/doOnError/doOnComplete/doOnCancel 等）。
     */
    public static void runWithTraceId(String traceId, Runnable action) {
        boolean restored = false;
        if (traceId != null && (MDC.get(TRACE_ID_KEY) == null)) {
            MDC.put(TRACE_ID_KEY, traceId);
            restored = true;
        }
        try {
            action.run();
        } finally {
            if (restored) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }

    /**
     * 在指定 traceId 上下文中执行有返回值的 Supplier，执行前恢复 MDC，执行后清理。
     * 用于 {@link java.util.concurrent.CompletableFuture#supplyAsync} 等需要返回值的异步任务。
     */
    public static <T> T callWithTraceId(String traceId, Supplier<T> action) {
        boolean restored = false;
        if (traceId != null && (MDC.get(TRACE_ID_KEY) == null)) {
            MDC.put(TRACE_ID_KEY, traceId);
            restored = true;
        }
        try {
            return action.get();
        } finally {
            if (restored) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }

    /**
     * 包裹 Consumer，使其在指定 traceId 上下文中执行。
     */
    public static <T> Consumer<T> withTraceId(String traceId, Consumer<T> delegate) {
        return t -> runWithTraceId(traceId, () -> delegate.accept(t));
    }
}
