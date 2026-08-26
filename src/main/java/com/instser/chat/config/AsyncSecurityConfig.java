package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.security.web.context.request.async.SecurityContextCallableProcessingInterceptor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

/**
 * 异步安全配置
 * 解决SSE流式响应中的Spring Security异步认证问题
 */
@Slf4j
@Configuration
public class AsyncSecurityConfig implements WebMvcConfigurer {

    /**
     * 配置异步支持，解决SSE流式响应的安全上下文传递问题
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 设置异步请求超时时间（30分钟）
        configurer.setDefaultTimeout(30 * 60 * 1000);

        // 添加安全上下文处理拦截器
        configurer.registerCallableInterceptors(securityContextCallableProcessingInterceptor());

        // 设置异步执行器
        configurer.setTaskExecutor(securityContextAwareAsyncExecutor());
    }

    /**
     * 安全上下文处理拦截器
     * 确保异步请求能够正确传递安全上下文
     */
    @Bean
    public SecurityContextCallableProcessingInterceptor securityContextCallableProcessingInterceptor() {
        return new SecurityContextCallableProcessingInterceptor();
    }

    /**
     * 基础异步执行器
     */
    @Bean
    public ThreadPoolTaskExecutor baseAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ChatAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 安全上下文感知的异步执行器
     * 使用安全上下文包装的执行器，确保异步任务中能够访问安全上下文
     */
    @Bean
    public AsyncTaskExecutor securityContextAwareAsyncExecutor() {
        return new DelegatingSecurityContextAsyncTaskExecutor(baseAsyncExecutor());
    }

    /**
     * 兼容性异步执行器（保持原有Bean名称）
     */
    @Bean
    public AsyncTaskExecutor asyncExecutor() {
        return securityContextAwareAsyncExecutor();
    }

    /**
     * SSE专用执行器 - 绕过Spring Security异步检查
     * 专门用于SSE流式响应，避免Spring Security在异步任务完成后进行权限检查
     */
    @Bean("sseBypassExecutor")
    public Executor sseBypassExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SSE-Bypass-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();

        return new SecurityBypassExecutor(executor);
    }

    /**
     * 执行器包装器 - 绕过Spring Security的异步检查
     * 通过清除安全上下文来避免Spring Security在异步任务完成后进行权限验证
     */
    public static class SecurityBypassExecutor implements Executor {
        private final Executor delegate;

        public SecurityBypassExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            // 保存原始安全上下文
            SecurityContext originalContext = SecurityContextHolder.getContext();
            try {
                // 清除安全上下文，避免异步权限检查
                SecurityContextHolder.clearContext();
                delegate.execute(() -> {
                    try {
                        command.run();
                    } catch (Exception e) {
                        log.error("SSE异步任务执行异常", e);
                    }
                });
            } finally {
                // 恢复原始安全上下文
                SecurityContextHolder.setContext(originalContext);
            }
        }
    }
}
