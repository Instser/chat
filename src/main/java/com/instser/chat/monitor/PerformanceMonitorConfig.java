package com.instser.chat.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 性能监控配置类
 * 用于配置监控间隔和告警阈值
 *
 * @author instser
 * @since 2025-07-20
 */
@Data
@Component
@ConfigurationProperties(prefix = "performance.monitor")
public class PerformanceMonitorConfig {

    /**
     * 监控间隔配置
     */
    private Interval interval = new Interval();

    /**
     * 告警阈值配置
     */
    private Threshold threshold = new Threshold();

    /**
     * 服务器信息配置
     */
    private Server server = new Server();

    @Data
    public static class Interval {
        /**
         * 基础监控间隔（毫秒），默认5分钟
         */
        private long basic = 300000L;

        /**
         * 详细报告间隔（毫秒），默认1小时
         */
        private long detailed = 3600000L;
    }

    @Data
    public static class Threshold {
        /**
         * CPU使用率告警阈值（百分比），默认80%
         */
        private double cpuUsage = 80.0;

        /**
         * 堆内存使用率告警阈值（百分比），默认85%
         */
        private double heapMemoryUsage = 85.0;

        /**
         * 线程数告警阈值，默认500个
         */
        private int threadCount = 500;

        /**
         * 数据库连接池使用率告警阈值（百分比），默认90%
         */
        private double connectionPoolUsage = 90.0;
    }

    @Data
    public static class Server {
        /**
         * 服务器描述信息，默认为空（自动检测）
         */
        private String description = "";

        /**
         * 是否启用自动检测服务器信息
         */
        private boolean autoDetect = true;
    }
}
