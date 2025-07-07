package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 性能优化配置类
 * 针对8核8G服务器进行优化配置
 *
 * @author instser
 * @since 2025-07-07
 */
@Slf4j
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "async")
public class PerformanceOptimizationConfig {

    private int corePoolSize = 8;
    private int maxPoolSize = 16;
    private int queueCapacity = 200;
    private String threadNamePrefix = "ChatAsync-";
    private int keepAliveSeconds = 60;

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("性能优化配置初始化 (8核8G服务器)");
        log.info("========================================");
        log.info("CPU核心数: {}", Runtime.getRuntime().availableProcessors());
        log.info("最大内存: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        log.info("可用内存: {} MB", Runtime.getRuntime().freeMemory() / 1024 / 1024);
        log.info("总内存: {} MB", Runtime.getRuntime().totalMemory() / 1024 / 1024);
        log.info("线程池配置: 核心={}, 最大={}, 队列={}", corePoolSize, maxPoolSize, queueCapacity);
        log.info("========================================");
    }

    /**
     * 优化的异步任务执行器
     * 基于8核8G服务器配置
     */
    @Bean("optimizedAsyncExecutor")
    public Executor optimizedAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 基础配置
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        
        // 性能优化配置
        executor.setAllowCoreThreadTimeOut(true);  // 允许核心线程超时
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 优雅关闭
        executor.setAwaitTerminationSeconds(30);  // 等待终止时间
        
        // 拒绝策略: 调用者运行策略 (避免任务丢失)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        log.info("优化异步执行器初始化完成: 核心线程={}, 最大线程={}, 队列容量={}", 
                corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }

    /**
     * 高并发场景专用执行器
     * 用于处理高并发请求
     */
    @Bean("highConcurrencyExecutor")
    public Executor highConcurrencyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 高并发配置 (更大的线程池)
        executor.setCorePoolSize(16);  // 核心线程数 = CPU核数 * 2
        executor.setMaxPoolSize(32);   // 最大线程数 = CPU核数 * 4
        executor.setQueueCapacity(500); // 更大的队列容量
        executor.setThreadNamePrefix("HighConcurrency-");
        executor.setKeepAliveSeconds(30); // 更短的存活时间
        
        // 性能优化
        executor.setAllowCoreThreadTimeOut(false); // 核心线程不超时
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 拒绝策略: 丢弃最老的任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        
        executor.initialize();
        
        log.info("高并发执行器初始化完成: 核心线程=16, 最大线程=32, 队列容量=500");
        
        return executor;
    }

    /**
     * IO密集型任务执行器
     * 用于文件处理、网络请求等IO密集型操作
     */
    @Bean("ioIntensiveExecutor")
    public Executor ioIntensiveExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // IO密集型配置 (更多线程)
        executor.setCorePoolSize(20);  // IO密集型可以有更多线程
        executor.setMaxPoolSize(50);   // 最大线程数
        executor.setQueueCapacity(100); // 适中的队列容量
        executor.setThreadNamePrefix("IOIntensive-");
        executor.setKeepAliveSeconds(120); // 更长的存活时间
        
        // 性能优化
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        
        // 拒绝策略: 调用者运行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        log.info("IO密集型执行器初始化完成: 核心线程=20, 最大线程=50, 队列容量=100");
        
        return executor;
    }

    // Getter和Setter方法
    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }
}
