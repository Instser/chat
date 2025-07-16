package com.instser.chat.monitor;

import com.alibaba.druid.pool.DruidDataSource;
import com.instser.chat.common.util.LogUtils;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/**
 * 性能监控组件
 * 定期监控系统性能指标
 *
 * @author instser
 * @since 2025-07-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceMonitor {

    private final DataSource dataSource;

    /**
     * 每5分钟监控一次系统性能
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void monitorSystemPerformance() {
        try {
            logSystemInfo();
            logMemoryInfo();
            logThreadInfo();
            logDatabaseConnectionPool();
        } catch (Exception e) {
            log.error("【系统异常】组件：性能监控，错误：{}，影响：监控数据缺失", e.getMessage(), e);
        }
    }

    /**
     * 每小时输出详细性能报告
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void generatePerformanceReport() {
        try {
            log.info("【性能报告】========================================");
            log.info("【性能报告】📊 系统性能报告 (8核8G服务器)");
            log.info("【性能报告】========================================");

            logDetailedSystemInfo();
            logDetailedMemoryInfo();
            logDetailedThreadInfo();
            logDetailedDatabaseInfo();

            log.info("【性能报告】========================================");
            log.info("【性能报告】📊 性能报告结束");
            log.info("【性能报告】========================================");
        } catch (Exception e) {
            log.error("【系统异常】组件：性能报告生成，错误：{}，影响：性能报告缺失", e.getMessage(), e);
        }
    }

    /**
     * 记录系统基本信息
     */
    private void logSystemInfo() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        // 尝试获取CPU使用率 (仅在支持的JVM上可用)
        double cpuUsage = -1;
        double systemCpuUsage = -1;

        if (osBean instanceof OperatingSystemMXBean) {
            OperatingSystemMXBean sunOsBean = (OperatingSystemMXBean) osBean;
            cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
            systemCpuUsage = sunOsBean.getCpuLoad() * 100; // 使用getCpuLoad()替代已废弃的getSystemCpuLoad()
        }

        if (cpuUsage >= 0) {
            log.info("【性能报告】CPU：{}%，内存：计算中，线程：计算中，连接池：计算中",
                    String.format("%.1f", cpuUsage));

            // CPU使用率告警
            if (cpuUsage > 80) {
                log.warn("【资源预警】类型：CPU，使用率：{}%，阈值：80%", String.format("%.1f", cpuUsage));
            }
        } else {
            log.info("【性能报告】CPU：N/A，内存：计算中，线程：计算中，连接池：计算中");
        }
    }

    /**
     * 记录内存信息
     */
    private void logMemoryInfo() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024;
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024;
        
        double heapUsagePercent = (double) heapUsed / heapMax * 100;
        
        log.debug("【监控详情】堆内存：{}/{} MB，非堆：{} MB，GC次数：计算中",
                heapUsed, heapMax, nonHeapUsed);

        // 内存使用率告警
        if (heapUsagePercent > 85) {
            log.warn("【资源预警】类型：堆内存，使用率：{}%，阈值：85%", String.format("%.1f", heapUsagePercent));
        }
    }

    /**
     * 记录线程信息
     */
    private void logThreadInfo() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        int daemonThreadCount = threadBean.getDaemonThreadCount();
        
        log.info("【线程池】活跃：{}/{}，队列：{}，完成率：计算中%",
                threadCount, peakThreadCount, daemonThreadCount);

        // 线程数告警
        if (threadCount > 500) {
            log.warn("【资源预警】类型：线程数，使用率：{}个，阈值：500个", threadCount);
        }
    }

    /**
     * 记录数据库连接池信息
     */
    private void logDatabaseConnectionPool() {
        if (dataSource instanceof DruidDataSource) {
            DruidDataSource druidDataSource = (DruidDataSource) dataSource;
            
            int activeCount = druidDataSource.getActiveCount();
            int maxActive = druidDataSource.getMaxActive();
            int poolingCount = druidDataSource.getPoolingCount();
            
            double activePercent = (double) activeCount / maxActive * 100;
            
            log.debug("【监控详情】数据库连接池：活跃={}/{} ({}%), 空闲={}",
                    activeCount, maxActive, String.format("%.1f", activePercent), poolingCount);

            // 连接池使用率告警
            if (activePercent > 90) {
                log.warn("【资源预警】类型：数据库连接池，使用率：{}%，阈值：90%", String.format("%.1f", activePercent));
            }
        }
    }

    /**
     * 记录详细系统信息
     */
    private void logDetailedSystemInfo() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        log.info("🖥️ 系统信息:");
        log.info("  操作系统: {} {}", osBean.getName(), osBean.getVersion());
        log.info("  系统架构: {}", osBean.getArch());
        log.info("  可用处理器: {}", osBean.getAvailableProcessors());
        log.info("  系统负载: {}", osBean.getSystemLoadAverage());
    }

    /**
     * 记录详细内存信息
     */
    private void logDetailedMemoryInfo() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        
        log.info("💾 内存详情:");
        log.info("  堆内存: 已用={}MB, 最大={}MB, 已提交={}MB", 
                memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024,
                memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024,
                memoryBean.getHeapMemoryUsage().getCommitted() / 1024 / 1024);
        log.info("  非堆内存: 已用={}MB, 最大={}MB, 已提交={}MB", 
                memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024,
                memoryBean.getNonHeapMemoryUsage().getMax() / 1024 / 1024,
                memoryBean.getNonHeapMemoryUsage().getCommitted() / 1024 / 1024);
        log.info("  JVM内存: 总计={}MB, 最大={}MB, 空闲={}MB", 
                runtime.totalMemory() / 1024 / 1024,
                runtime.maxMemory() / 1024 / 1024,
                runtime.freeMemory() / 1024 / 1024);
    }

    /**
     * 记录详细线程信息
     */
    private void logDetailedThreadInfo() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        log.info("🧵 线程详情:");
        log.info("  当前线程数: {}", threadBean.getThreadCount());
        log.info("  峰值线程数: {}", threadBean.getPeakThreadCount());
        log.info("  守护线程数: {}", threadBean.getDaemonThreadCount());
        log.info("  总启动线程数: {}", threadBean.getTotalStartedThreadCount());
    }

    /**
     * 记录详细数据库信息
     */
    private void logDetailedDatabaseInfo() {
        if (dataSource instanceof DruidDataSource) {
            DruidDataSource druidDataSource = (DruidDataSource) dataSource;
            
            log.info("🗄️ 数据库连接池详情:");
            log.info("  初始连接数: {}", druidDataSource.getInitialSize());
            log.info("  最小空闲连接: {}", druidDataSource.getMinIdle());
            log.info("  最大活跃连接: {}", druidDataSource.getMaxActive());
            log.info("  当前活跃连接: {}", druidDataSource.getActiveCount());
            log.info("  当前空闲连接: {}", druidDataSource.getPoolingCount());
            log.info("  连接获取次数: {}", druidDataSource.getConnectCount());
            log.info("  连接关闭次数: {}", druidDataSource.getCloseCount());
            log.info("  连接泄漏次数: {}", druidDataSource.getRemoveAbandonedCount());
            log.info("  等待连接次数: {}", druidDataSource.getNotEmptyWaitCount());
            log.info("  平均等待时间: {}ms", druidDataSource.getNotEmptyWaitMillis());
        }
    }

    /**
     * 获取系统性能摘要
     */
    public String getPerformanceSummary() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

            // 尝试获取CPU使用率
            double cpuUsage = -1;
            if (osBean instanceof OperatingSystemMXBean) {
                OperatingSystemMXBean sunOsBean = (OperatingSystemMXBean) osBean;
                cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
            }

            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
            long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024;
            int threadCount = threadBean.getThreadCount();

            int activeConnections = 0;
            int maxConnections = 0;
            if (dataSource instanceof DruidDataSource) {
                DruidDataSource druidDataSource = (DruidDataSource) dataSource;
                activeConnections = druidDataSource.getActiveCount();
                maxConnections = druidDataSource.getMaxActive();
            }

            if (cpuUsage >= 0) {
                return String.format("CPU: %.1f%%, 内存: %d/%dMB, 线程: %d, 连接: %d/%d",
                        cpuUsage, heapUsed, heapMax, threadCount, activeConnections, maxConnections);
            } else {
                return String.format("CPU: N/A, 内存: %d/%dMB, 线程: %d, 连接: %d/%d",
                        heapUsed, heapMax, threadCount, activeConnections, maxConnections);
            }
        } catch (Exception e) {
            return "性能信息获取失败: " + e.getMessage();
        }
    }
}
