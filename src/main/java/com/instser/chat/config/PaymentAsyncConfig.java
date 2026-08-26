package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 支付异步处理配置
 * 
 * @author instser
 * @since 2025-07-29
 */
@Slf4j
@Configuration
@EnableAsync
public class PaymentAsyncConfig {

    /**
     * 支付回调异步处理线程池
     */
    @Bean("paymentCallbackExecutor")
    public ThreadPoolTaskExecutor paymentCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(5);
        
        // 最大线程数
        executor.setMaxPoolSize(20);
        
        // 队列容量
        executor.setQueueCapacity(100);
        
        // 线程名前缀
        executor.setThreadNamePrefix("payment-callback-");
        
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 传递 MDC（traceId 等）到异步线程
        executor.setTaskDecorator(new MdcTaskDecorator());

        // 等待任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待时间
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("支付回调异步处理线程池初始化完成");
        return executor;
    }

    /**
     * Stripe Webhook处理专用线程池
     * 用于异步处理Stripe Webhook事件
     */
    @Bean("stripeWebhookExecutor")
    public ThreadPoolTaskExecutor stripeWebhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(3);
        
        // 最大线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量
        executor.setQueueCapacity(50);
        
        // 线程名前缀
        executor.setThreadNamePrefix("stripe-webhook-");
        
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 传递 MDC（traceId 等）到异步线程
        executor.setTaskDecorator(new MdcTaskDecorator());

        // 等待任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待时间
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Stripe Webhook异步处理线程池初始化完成");
        return executor;
    }
}
