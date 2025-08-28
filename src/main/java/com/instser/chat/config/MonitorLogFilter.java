package com.instser.chat.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 监控日志过滤器
 * 用于过滤监控相关的日志信息，确保只有监控指标相关的日志才会输出到监控日志文件
 *
 * @author instser
 * @since 2025-07-31
 */
public class MonitorLogFilter extends Filter<ILoggingEvent> {

    /**
     * 监控相关的日志关键字
     */
    private static final String[] MONITOR_KEYWORDS = {
        "【性能报告】",
        "【监控详情】", 
        "【线程池】",
        "【资源预警】",
        "【任务执行】",
        "【缓存健康状态】",
        "【缓存统计】",
        "【缓存监控】",
        "【系统监控】",
        "【性能监控】",
        "【内存监控】",
        "【CPU监控】",
        "【数据库监控】",
        "【连接池监控】",
        "【定时任务】"
    };

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null || event.getMessage() == null) {
            return FilterReply.DENY;
        }

        String message = event.getFormattedMessage();
        
        // 检查是否包含监控关键字
        for (String keyword : MONITOR_KEYWORDS) {
            if (message.contains(keyword)) {
                return FilterReply.ACCEPT;
            }
        }
        
        // 检查logger名称是否为监控相关
        String loggerName = event.getLoggerName();
        if (loggerName != null) {
            if (loggerName.contains("monitor") || 
                loggerName.contains("Monitor") ||
                loggerName.contains("CacheMonitoringService")) {
                return FilterReply.ACCEPT;
            }
        }
        
        return FilterReply.DENY;
    }
}
