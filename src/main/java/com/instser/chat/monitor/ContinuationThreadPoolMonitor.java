package com.instser.chat.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 续写线程池监控组件
 * 定期监控线程池状态，提供告警和性能指标
 *
 * @author instser
 * @since 2025-07-03
 */
@Slf4j
@Component
public class ContinuationThreadPoolMonitor {

    @Autowired
    @Qualifier("continuationExecutor")
    private Executor continuationExecutor;

    // 告警阈值
    private static final int QUEUE_SIZE_WARNING_THRESHOLD = 40;
    private static final int ACTIVE_THREAD_WARNING_THRESHOLD = 8;
    private static final double QUEUE_USAGE_WARNING_RATIO = 0.8; // 队列使用率80%告警

    /**
     * 每30秒监控一次线程池状态
     */
    @Scheduled(fixedRate = 30000)
    public void monitorThreadPool() {
        try {
            if (!(continuationExecutor instanceof ThreadPoolTaskExecutor)) {
                log.warn("续写线程池不是ThreadPoolTaskExecutor类型，无法监控");
                return;
            }

            ThreadPoolExecutor executor = ((ThreadPoolTaskExecutor) continuationExecutor).getThreadPoolExecutor();
            
            int activeCount = executor.getActiveCount();
            int queueSize = executor.getQueue().size();
            long completedTaskCount = executor.getCompletedTaskCount();
            long totalTaskCount = executor.getTaskCount();
            int corePoolSize = executor.getCorePoolSize();
            int maximumPoolSize = executor.getMaximumPoolSize();
            
            // 记录基本状态
            log.debug("续写线程池状态 - 活跃线程:{}/{}, 队列大小:{}, 完成任务:{}, 总任务:{}",
                    activeCount, maximumPoolSize, queueSize, completedTaskCount, totalTaskCount);
            
            // 检查告警条件
            checkAndAlert(activeCount, queueSize, corePoolSize, maximumPoolSize);
            
        } catch (Exception e) {
            log.error("监控续写线程池状态时发生异常", e);
        }
    }

    /**
     * 检查告警条件并发出告警
     */
    private void checkAndAlert(int activeCount, int queueSize, int corePoolSize, int maximumPoolSize) {
        // 队列积压告警
        if (queueSize > QUEUE_SIZE_WARNING_THRESHOLD) {
            log.warn("🚨 续写线程池队列积压严重！当前队列大小：{}，建议检查续写请求处理速度", queueSize);
        }
        
        // 活跃线程数告警
        if (activeCount > ACTIVE_THREAD_WARNING_THRESHOLD) {
            log.warn("🚨 续写线程池活跃线程数较高！当前活跃线程：{}，最大线程：{}", activeCount, maximumPoolSize);
        }
        
        // 队列使用率告警
        int queueCapacity = 50; // 从配置中获取
        double queueUsageRatio = (double) queueSize / queueCapacity;
        if (queueUsageRatio > QUEUE_USAGE_WARNING_RATIO) {
            log.warn("🚨 续写线程池队列使用率过高！当前使用率：{}%，队列大小：{}/{}",
                    String.format("%.1f", queueUsageRatio * 100), queueSize, queueCapacity);
        }
        
        // 线程池饱和告警
        if (activeCount >= maximumPoolSize && queueSize > 0) {
            log.warn("🚨 续写线程池已饱和！所有线程都在工作且有任务排队，活跃线程：{}，队列大小：{}",
                    activeCount, queueSize);
        }
    }

    /**
     * 每5分钟输出详细的性能统计
     */
    @Scheduled(fixedRate = 300000)
    public void reportDetailedStats() {
        try {
            if (!(continuationExecutor instanceof ThreadPoolTaskExecutor)) {
                log.warn("续写线程池不是ThreadPoolTaskExecutor类型，无法生成详细报告");
                return;
            }

            ThreadPoolExecutor executor = ((ThreadPoolTaskExecutor) continuationExecutor).getThreadPoolExecutor();
            
            int activeCount = executor.getActiveCount();
            int queueSize = executor.getQueue().size();
            long completedTaskCount = executor.getCompletedTaskCount();
            long totalTaskCount = executor.getTaskCount();
            int corePoolSize = executor.getCorePoolSize();
            int maximumPoolSize = executor.getMaximumPoolSize();
            int poolSize = executor.getPoolSize();
            
            // 计算性能指标
            double completionRate = totalTaskCount > 0 ? (double) completedTaskCount / totalTaskCount * 100 : 0;
            long pendingTasks = totalTaskCount - completedTaskCount;
            
            log.info("📊 续写线程池性能报告：");
            log.info("   ├─ 线程配置：核心线程={}, 最大线程={}, 当前线程={}", corePoolSize, maximumPoolSize, poolSize);
            log.info("   ├─ 任务统计：总任务={}, 已完成={}, 待处理={}", totalTaskCount, completedTaskCount, pendingTasks);
            log.info("   ├─ 完成率：{}%", String.format("%.2f", completionRate));
            log.info("   ├─ 当前状态：活跃线程={}, 队列大小={}", activeCount, queueSize);
            log.info("   └─ 健康状态：{}", getHealthStatus(activeCount, queueSize, maximumPoolSize));
            
        } catch (Exception e) {
            log.error("生成续写线程池性能报告时发生异常", e);
        }
    }

    /**
     * 获取线程池健康状态
     */
    private String getHealthStatus(int activeCount, int queueSize, int maximumPoolSize) {
        if (queueSize > QUEUE_SIZE_WARNING_THRESHOLD || activeCount >= maximumPoolSize) {
            return "⚠️ 需要关注";
        } else if (queueSize > 20 || activeCount > 5) {
            return "⚡ 繁忙";
        } else if (activeCount > 0) {
            return "✅ 正常工作";
        } else {
            return "😴 空闲";
        }
    }

    /**
     * 手动获取当前线程池状态（用于接口查询）
     */
    public String getCurrentStatus() {
        try {
            if (!(continuationExecutor instanceof ThreadPoolTaskExecutor)) {
                return "续写线程池类型不支持监控";
            }

            ThreadPoolExecutor executor = ((ThreadPoolTaskExecutor) continuationExecutor).getThreadPoolExecutor();
            
            return String.format("续写线程池状态 - 活跃线程:%d/%d, 队列大小:%d, 完成任务:%d, 总任务:%d, 健康状态:%s",
                    executor.getActiveCount(),
                    executor.getMaximumPoolSize(),
                    executor.getQueue().size(),
                    executor.getCompletedTaskCount(),
                    executor.getTaskCount(),
                    getHealthStatus(executor.getActiveCount(), executor.getQueue().size(), executor.getMaximumPoolSize()));
                    
        } catch (Exception e) {
            log.error("获取续写线程池状态时发生异常", e);
            return "获取状态失败：" + e.getMessage();
        }
    }
}
