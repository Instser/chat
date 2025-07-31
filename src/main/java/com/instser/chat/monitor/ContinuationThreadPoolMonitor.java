package com.instser.chat.monitor;

import com.instser.chat.common.util.LogUtils;
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
     * 监控线程池状态
     * 由 OptimizedScheduledTaskService 统一调用
     */
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
            log.debug("【任务执行】任务类型：续写线程池，执行时间：监控中，结果：活跃线程{}/{}，队列{}",
                    activeCount, maximumPoolSize, queueSize);

            // 检查告警条件
            checkAndAlert(activeCount, queueSize, corePoolSize, maximumPoolSize);

        } catch (Exception e) {
            log.error("【系统异常】组件：续写线程池监控，错误：{}，影响：监控数据缺失", e.getMessage(), e);
        }
    }

    /**
     * 检查告警条件并发出告警
     */
    private void checkAndAlert(int activeCount, int queueSize, int corePoolSize, int maximumPoolSize) {
        // 队列积压告警
        if (queueSize > QUEUE_SIZE_WARNING_THRESHOLD) {
            log.warn("【资源预警】类型：续写线程池队列，使用率：{}个任务，阈值：{}个", queueSize, QUEUE_SIZE_WARNING_THRESHOLD);
        }

        // 活跃线程数告警
        if (activeCount > ACTIVE_THREAD_WARNING_THRESHOLD) {
            log.warn("【资源预警】类型：续写线程池活跃线程，使用率：{}/{}，阈值：{}", activeCount, maximumPoolSize, ACTIVE_THREAD_WARNING_THRESHOLD);
        }

        // 队列使用率告警
        int queueCapacity = 50; // 从配置中获取
        double queueUsageRatio = (double) queueSize / queueCapacity;
        if (queueUsageRatio > QUEUE_USAGE_WARNING_RATIO) {
            log.warn("【资源预警】类型：续写线程池队列使用率，使用率：{}%，阈值：{}%",
                    String.format("%.1f", queueUsageRatio * 100), String.format("%.1f", QUEUE_USAGE_WARNING_RATIO * 100));
        }

        // 线程池饱和告警
        if (activeCount >= maximumPoolSize && queueSize > 0) {
            log.warn("【性能下降】指标：续写线程池饱和度，当前值：活跃线程{}，队列{}，基准值：正常运行",
                    activeCount, queueSize);
        }
    }

    /**
     * 输出详细的性能统计
     * 由 OptimizedScheduledTaskService 统一调用
     */
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
            
            log.info("【线程池】活跃：{}/{}，队列：{}，完成率：{}%",
                activeCount, maximumPoolSize, queueSize, String.format("%.2f", completionRate));
            log.debug("【监控详情】线程配置：核心线程={}, 最大线程={}, 当前线程={}", corePoolSize, maximumPoolSize, poolSize);
            log.debug("【监控详情】任务统计：总任务={}, 已完成={}, 待处理={}", totalTaskCount, completedTaskCount, pendingTasks);
            log.debug("【监控详情】健康状态：{}", getHealthStatus(activeCount, queueSize, maximumPoolSize));

        } catch (Exception e) {
            log.error("【系统异常】组件：续写线程池性能报告，错误：{}，影响：性能报告缺失", e.getMessage(), e);
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
