package com.instser.chat.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 监控日志配置类
 * 用于管理监控日志的配置和状态
 *
 * @author instser
 * @since 2025-07-31
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "logging.monitor")
public class MonitorLogConfig {

    /**
     * 是否启用监控日志分离
     */
    private boolean enabled = true;

    /**
     * 监控日志文件路径
     */
    private String path = "logs";

    /**
     * 监控日志文件名前缀
     */
    private String filePrefix = "jinyu-chat-monitor";

    /**
     * 监控日志保留天数
     */
    private int maxDays = 7;

    /**
     * 监控日志文件最大大小（MB）
     */
    private int maxFileSize = 100;

    /**
     * 监控日志总大小限制（MB）
     */
    private int maxTotalSize = 1024;

    /**
     * 是否在控制台显示监控日志
     */
    private boolean showInConsole = false;

    /**
     * 是否在主日志文件中显示监控日志
     */
    private boolean showInMainLog = false;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("【监控日志配置】监控日志分离功能已启用");
            log.info("【监控日志配置】监控日志路径：{}", path);
            log.info("【监控日志配置】监控日志文件前缀：{}", filePrefix);
            log.info("【监控日志配置】保留天数：{}天", maxDays);
            log.info("【监控日志配置】单文件最大大小：{}MB", maxFileSize);
            log.info("【监控日志配置】总大小限制：{}MB", maxTotalSize);
            log.info("【监控日志配置】控制台显示：{}", showInConsole ? "是" : "否");
            log.info("【监控日志配置】主日志显示：{}", showInMainLog ? "是" : "否");
            
            // 确保监控日志目录存在
            ensureLogDirectoryExists();
            
            log.info("【监控日志配置】监控日志配置初始化完成");
        } else {
            log.info("【监控日志配置】监控日志分离功能已禁用，监控信息将输出到主日志文件");
        }
    }

    /**
     * 确保日志目录存在
     */
    private void ensureLogDirectoryExists() {
        try {
            Path logPath = Paths.get(path);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
                log.info("【监控日志配置】创建监控日志目录：{}", logPath.toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("【监控日志配置】创建监控日志目录失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 获取监控日志文件完整路径
     */
    public String getMonitorLogFilePath() {
        return path + File.separator + filePrefix + ".log";
    }

    /**
     * 获取监控日志文件大小（MB）
     */
    public long getMonitorLogFileSize() {
        try {
            Path logFile = Paths.get(getMonitorLogFilePath());
            if (Files.exists(logFile)) {
                return Files.size(logFile) / 1024 / 1024;
            }
        } catch (Exception e) {
            log.debug("【监控日志配置】获取监控日志文件大小失败：{}", e.getMessage());
        }
        return 0;
    }

    /**
     * 检查监控日志配置状态
     */
    public void checkStatus() {
        if (enabled) {
            long fileSize = getMonitorLogFileSize();
            log.debug("【监控日志配置】监控日志文件大小：{}MB", fileSize);
            
            if (fileSize > maxFileSize * 0.8) {
                log.warn("【监控日志配置】监控日志文件大小接近限制：{}MB/{}MB", fileSize, maxFileSize);
            }
        }
    }
}
