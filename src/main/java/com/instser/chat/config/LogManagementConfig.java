package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 日志管理配置
 * 负责日志文件的监控、清理和空间管理
 *
 * @author instser
 * @since 2025-07-16
 */
@Slf4j
@Configuration
@EnableScheduling
public class LogManagementConfig {

    @Value("${logging.file.path:logs}")
    private String logPath;

    @Value("${logging.management.max-total-size:2048}")
    private long maxTotalSizeMB;

    @Value("${logging.management.max-days:15}")
    private int maxDays;

    @Value("${logging.management.disk-warning-threshold:80}")
    private int diskWarningThreshold;

    @Value("${logging.management.disk-critical-threshold:90}")
    private int diskCriticalThreshold;

    /**
     * 初始化日志管理
     */
    @PostConstruct
    public void initLogManagement() {
        log.info("【日志管理】初始化日志管理配置");
        log.info("【日志管理】日志路径：{}，最大总大小：{}MB，保留天数：{}天", 
            logPath, maxTotalSizeMB, maxDays);
        
        // 确保日志目录存在
        createLogDirectoryIfNotExists();
        
        // 启动时执行一次清理
        cleanupOldLogFiles();
        
        log.info("【日志管理】日志管理配置初始化完成");
    }

    /**
     * 每小时检查日志文件状态
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void monitorLogFiles() {
        try {
            checkDiskSpace();
            checkLogFileSize();
            reportLogStatistics();
        } catch (Exception e) {
            log.error("【日志管理】监控日志文件时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 每天凌晨2点清理过期日志文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldLogFiles() {
        try {
            log.info("【日志清理】开始清理过期日志文件");
            
            File logDir = new File(logPath);
            if (!logDir.exists()) {
                log.warn("【日志清理】日志目录不存在：{}", logPath);
                return;
            }

            File[] logFiles = logDir.listFiles((dir, name) -> 
                name.endsWith(".log") && !name.equals("jinyu-chat.log") && !name.equals("jinyu-chat-error.log"));

            if (logFiles == null || logFiles.length == 0) {
                log.info("【日志清理】没有找到需要清理的日志文件");
                return;
            }

            // 按修改时间排序，删除最旧的文件
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

            long totalSize = calculateTotalLogSize();
            long maxTotalSizeBytes = maxTotalSizeMB * 1024 * 1024;
            int deletedCount = 0;
            long deletedSize = 0;

            for (File file : logFiles) {
                // 如果总大小超过限制，删除最旧的文件
                if (totalSize > maxTotalSizeBytes) {
                    long fileSize = file.length();
                    if (file.delete()) {
                        totalSize -= fileSize;
                        deletedSize += fileSize;
                        deletedCount++;
                        log.info("【日志清理】删除过期日志文件：{}，大小：{}MB", 
                            file.getName(), fileSize / 1024 / 1024);
                    }
                }
            }

            if (deletedCount > 0) {
                log.info("【日志清理】清理完成，删除文件数：{}，释放空间：{}MB", 
                    deletedCount, deletedSize / 1024 / 1024);
            } else {
                log.info("【日志清理】无需清理，当前日志总大小：{}MB", totalSize / 1024 / 1024);
            }

        } catch (Exception e) {
            log.error("【日志清理】清理过期日志文件时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 检查磁盘空间
     */
    private void checkDiskSpace() {
        try {
            File logDir = new File(logPath);
            long totalSpace = logDir.getTotalSpace();
            long freeSpace = logDir.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            double usagePercent = (double) usedSpace / totalSpace * 100;

            if (usagePercent > diskCriticalThreshold) {
                log.error("【磁盘告警】磁盘使用率严重告警：{}%，可用空间：{}GB", 
                    String.format("%.1f", usagePercent), freeSpace / 1024 / 1024 / 1024);
                // 触发紧急清理
                emergencyCleanup();
            } else if (usagePercent > diskWarningThreshold) {
                log.warn("【磁盘告警】磁盘使用率告警：{}%，可用空间：{}GB", 
                    String.format("%.1f", usagePercent), freeSpace / 1024 / 1024 / 1024);
            }

        } catch (Exception e) {
            log.error("【磁盘监控】检查磁盘空间时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 检查日志文件大小
     */
    private void checkLogFileSize() {
        try {
            long totalLogSize = calculateTotalLogSize();
            long maxTotalSizeBytes = maxTotalSizeMB * 1024 * 1024;
            
            if (totalLogSize > maxTotalSizeBytes) {
                log.warn("【日志告警】日志总大小超过限制：{}MB > {}MB", 
                    totalLogSize / 1024 / 1024, maxTotalSizeMB);
                // 触发清理
                cleanupOldLogFiles();
            }

        } catch (Exception e) {
            log.error("【日志监控】检查日志文件大小时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 报告日志统计信息
     */
    private void reportLogStatistics() {
        try {
            File logDir = new File(logPath);
            if (!logDir.exists()) {
                return;
            }

            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null) {
                return;
            }

            long totalSize = 0;
            int fileCount = 0;
            
            for (File file : logFiles) {
                totalSize += file.length();
                fileCount++;
            }

            log.info("【日志统计】文件数量：{}，总大小：{}MB，平均大小：{}MB", 
                fileCount, totalSize / 1024 / 1024, 
                fileCount > 0 ? (totalSize / fileCount) / 1024 / 1024 : 0);

        } catch (Exception e) {
            log.error("【日志统计】生成日志统计信息时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 紧急清理 - 磁盘空间严重不足时
     */
    private void emergencyCleanup() {
        try {
            log.warn("【紧急清理】开始紧急清理日志文件");
            
            File logDir = new File(logPath);
            File[] logFiles = logDir.listFiles((dir, name) -> 
                name.endsWith(".log") && !name.equals("jinyu-chat.log") && !name.equals("jinyu-chat-error.log"));

            if (logFiles == null || logFiles.length == 0) {
                return;
            }

            // 按修改时间排序，删除最旧的50%文件
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));
            int deleteCount = Math.max(1, logFiles.length / 2);
            
            long deletedSize = 0;
            for (int i = 0; i < deleteCount && i < logFiles.length; i++) {
                File file = logFiles[i];
                long fileSize = file.length();
                if (file.delete()) {
                    deletedSize += fileSize;
                    log.warn("【紧急清理】删除日志文件：{}，大小：{}MB", 
                        file.getName(), fileSize / 1024 / 1024);
                }
            }

            log.warn("【紧急清理】紧急清理完成，删除文件数：{}，释放空间：{}MB", 
                deleteCount, deletedSize / 1024 / 1024);

        } catch (Exception e) {
            log.error("【紧急清理】紧急清理时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 计算日志文件总大小
     */
    private long calculateTotalLogSize() {
        try {
            File logDir = new File(logPath);
            if (!logDir.exists()) {
                return 0;
            }

            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null) {
                return 0;
            }

            return Arrays.stream(logFiles)
                    .mapToLong(File::length)
                    .sum();

        } catch (Exception e) {
            log.error("【日志统计】计算日志文件总大小时发生异常：{}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 创建日志目录
     */
    private void createLogDirectoryIfNotExists() {
        try {
            Path logDirPath = Paths.get(logPath);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
                log.info("【日志管理】创建日志目录：{}", logPath);
            }
        } catch (IOException e) {
            log.error("【日志管理】创建日志目录失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 获取日志管理状态
     */
    public String getLogManagementStatus() {
        try {
            long totalSize = calculateTotalLogSize();
            File logDir = new File(logPath);
            long freeSpace = logDir.getFreeSpace();
            long totalSpace = logDir.getTotalSpace();
            double diskUsage = (double) (totalSpace - freeSpace) / totalSpace * 100;

            return String.format("日志总大小: %dMB, 磁盘使用率: %.1f%%, 可用空间: %dGB", 
                totalSize / 1024 / 1024, diskUsage, freeSpace / 1024 / 1024 / 1024);

        } catch (Exception e) {
            return "获取日志管理状态失败: " + e.getMessage();
        }
    }
}
