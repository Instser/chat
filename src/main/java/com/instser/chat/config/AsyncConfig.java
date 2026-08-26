package com.instser.chat.config;

import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 异步执行器全局配置。
 * <p>实现 {@link AsyncConfigurer}，为未显式指定执行器的 {@code @Async} 方法
 * 提供带 {@link MdcTaskDecorator} 的默认线程池，确保 traceId 跨线程传递。
 * 同时处理 {@code @EventListener + @Async} 等场景（默认会 fallback 到
 * {@code SimpleAsyncTaskExecutor}，不受 TaskDecorator 管控）。
 *
 * @author instser
 * @since 2026-08-26
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private final ThreadPoolTaskExecutor baseAsyncExecutor;

    public AsyncConfig(ThreadPoolTaskExecutor baseAsyncExecutor) {
        this.baseAsyncExecutor = baseAsyncExecutor;
    }

    /**
     * 全局默认 @Async 执行器：复用已配好 TaskDecorator 的 baseAsyncExecutor。
     * 这样所有 @Async（含 @EventListener+@Async 未指定执行器的）都走带 MDC 传递的线程池。
     */
    @Override
    public Executor getAsyncExecutor() {
        return baseAsyncExecutor;
    }

    /**
     * 异步任务未捕获异常处理器。
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
