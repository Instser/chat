package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 续写操作线程池配置
 * 用于隔离续写操作，避免在Reactor线程中执行阻塞操作
 *
 * @author instser
 * @since 2025-07-03
 */
@Slf4j
@Configuration
public class ContinuationConfig {

    private ThreadPoolTaskExecutor continuationExecutor;

    /**
     * 创建续写操作专用线程池
     */
    @Bean("continuationExecutor")
    public Executor continuationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：始终保持活跃的线程数
        executor.setCorePoolSize(3);
        
        // 最大线程数：高峰期最多创建的线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量：等待执行的任务队列大小
        executor.setQueueCapacity(50);
        
        // 线程名前缀：便于日志追踪和调试
        executor.setThreadNamePrefix("continuation-");
        
        // 线程存活时间：空闲线程的最大存活时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：队列满时由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 优雅关闭：等待任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.initialize();
        
        // 保存引用用于监控和关闭
        this.continuationExecutor = executor;
        
        log.info("续写线程池初始化完成 - 核心线程:{}, 最大线程:{}, 队列容量:{}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 应用关闭时优雅关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        if (continuationExecutor != null) {
            log.info("开始关闭续写线程池");
            
            try {
                // 停止接收新任务
                continuationExecutor.shutdown();
                
                // 等待现有任务完成
                if (!continuationExecutor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("续写线程池未能在30秒内优雅关闭，强制关闭");
                    continuationExecutor.getThreadPoolExecutor().shutdownNow();
                    
                    // 再等待一段时间确保强制关闭完成
                    if (!continuationExecutor.getThreadPoolExecutor().awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("续写线程池强制关闭失败");
                    }
                }
                
                log.info("续写线程池已成功关闭");
                
            } catch (InterruptedException e) {
                log.error("等待续写线程池关闭时被中断", e);
                continuationExecutor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取线程池状态信息（用于监控）
     */
    public String getThreadPoolStatus() {
        if (continuationExecutor != null) {
            ThreadPoolExecutor executor = continuationExecutor.getThreadPoolExecutor();
            return String.format("活跃线程:%d, 队列大小:%d, 完成任务:%d, 总任务:%d",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getCompletedTaskCount(),
                    executor.getTaskCount());
        }
        return "线程池未初始化";
    }
}
