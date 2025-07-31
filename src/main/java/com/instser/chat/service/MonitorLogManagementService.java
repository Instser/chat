package com.instser.chat.service;

import com.instser.chat.config.MonitorLogConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 监控日志管理服务
 * 负责监控日志的清理、状态监控和管理
 *
 * @author instser
 * @since 2025-07-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorLogManagementService {

    private final MonitorLogConfig monitorLogConfig;

    /**
     * 定期清理过期的监控日志文件
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredMonitorLogs() {
        if (!monitorLogConfig.isEnabled()) {
            return;
        }

        try {
            log.info("【监控日志清理】开始清理过期监控日志文件");
            
            Path logDir = Paths.get(monitorLogConfig.getPath());
            if (!Files.exists(logDir)) {
                log.debug("【监控日志清理】监控日志目录不存在：{}", logDir);
                return;
            }

            // 获取所有监控日志文件
            List<File> monitorLogFiles = getMonitorLogFiles(logDir);
            
            if (monitorLogFiles.isEmpty()) {
                log.debug("【监控日志清理】未找到监控日志文件");
                return;
            }

            // 清理过期文件
            int deletedCount = cleanupExpiredFiles(monitorLogFiles);
            
            // 检查总大小限制
            int sizeLimitDeletedCount = cleanupBySizeLimit(monitorLogFiles);
            
            int totalDeleted = deletedCount + sizeLimitDeletedCount;
            if (totalDeleted > 0) {
                log.info("【监控日志清理】清理完成，删除文件数：{}个", totalDeleted);
            } else {
                log.debug("【监控日志清理】无需清理，所有文件都在保留期内");
            }
            
        } catch (Exception e) {
            log.error("【监控日志清理】清理监控日志文件时发生异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 获取所有监控日志文件
     */
    private List<File> getMonitorLogFiles(Path logDir) throws IOException {
        return Files.list(logDir)
                .map(Path::toFile)
                .filter(file -> file.getName().startsWith(monitorLogConfig.getFilePrefix()))
                .filter(File::isFile)
                .collect(Collectors.toList());
    }

    /**
     * 清理过期文件
     */
    private int cleanupExpiredFiles(List<File> files) {
        long cutoffTime = System.currentTimeMillis() - (monitorLogConfig.getMaxDays() * 24L * 60 * 60 * 1000);
        int deletedCount = 0;

        for (File file : files) {
            if (file.lastModified() < cutoffTime) {
                try {
                    if (file.delete()) {
                        log.debug("【监控日志清理】删除过期文件：{}", file.getName());
                        deletedCount++;
                    } else {
                        log.warn("【监控日志清理】删除过期文件失败：{}", file.getName());
                    }
                } catch (Exception e) {
                    log.error("【监控日志清理】删除文件异常：{}，错误：{}", file.getName(), e.getMessage());
                }
            }
        }

        return deletedCount;
    }

    /**
     * 按大小限制清理文件
     */
    private int cleanupBySizeLimit(List<File> files) {
        // 重新获取文件列表（排除已删除的文件）
        files = files.stream()
                .filter(File::exists)
                .collect(Collectors.toList());

        long totalSize = files.stream().mapToLong(File::length).sum();
        long maxTotalSizeBytes = monitorLogConfig.getMaxTotalSize() * 1024L * 1024L;

        if (totalSize <= maxTotalSizeBytes) {
            return 0;
        }

        // 按修改时间排序，删除最旧的文件
        files.sort(Comparator.comparingLong(File::lastModified));
        
        int deletedCount = 0;
        for (File file : files) {
            if (totalSize <= maxTotalSizeBytes) {
                break;
            }
            
            try {
                long fileSize = file.length();
                if (file.delete()) {
                    log.debug("【监控日志清理】删除超限文件：{}，大小：{}MB", 
                            file.getName(), fileSize / 1024 / 1024);
                    totalSize -= fileSize;
                    deletedCount++;
                } else {
                    log.warn("【监控日志清理】删除超限文件失败：{}", file.getName());
                }
            } catch (Exception e) {
                log.error("【监控日志清理】删除文件异常：{}，错误：{}", file.getName(), e.getMessage());
            }
        }

        return deletedCount;
    }

    /**
     * 获取监控日志统计信息
     */
    public MonitorLogStats getMonitorLogStats() {
        if (!monitorLogConfig.isEnabled()) {
            return MonitorLogStats.disabled();
        }

        try {
            Path logDir = Paths.get(monitorLogConfig.getPath());
            if (!Files.exists(logDir)) {
                return MonitorLogStats.empty();
            }

            List<File> monitorLogFiles = getMonitorLogFiles(logDir);
            
            long totalSize = monitorLogFiles.stream().mapToLong(File::length).sum();
            int fileCount = monitorLogFiles.size();
            
            return MonitorLogStats.builder()
                    .enabled(true)
                    .fileCount(fileCount)
                    .totalSizeMB(totalSize / 1024 / 1024)
                    .maxTotalSizeMB(monitorLogConfig.getMaxTotalSize())
                    .maxDays(monitorLogConfig.getMaxDays())
                    .currentLogFilePath(monitorLogConfig.getMonitorLogFilePath())
                    .build();
                    
        } catch (Exception e) {
            log.error("【监控日志管理】获取监控日志统计信息失败：{}", e.getMessage(), e);
            return MonitorLogStats.error(e.getMessage());
        }
    }

    /**
     * 监控日志统计信息
     */
    public static class MonitorLogStats {
        private boolean enabled;
        private int fileCount;
        private long totalSizeMB;
        private long maxTotalSizeMB;
        private int maxDays;
        private String currentLogFilePath;
        private String error;

        public static MonitorLogStats disabled() {
            MonitorLogStats stats = new MonitorLogStats();
            stats.enabled = false;
            return stats;
        }

        public static MonitorLogStats empty() {
            MonitorLogStats stats = new MonitorLogStats();
            stats.enabled = true;
            stats.fileCount = 0;
            stats.totalSizeMB = 0;
            return stats;
        }

        public static MonitorLogStats error(String error) {
            MonitorLogStats stats = new MonitorLogStats();
            stats.enabled = true;
            stats.error = error;
            return stats;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private MonitorLogStats stats = new MonitorLogStats();

            public Builder enabled(boolean enabled) {
                stats.enabled = enabled;
                return this;
            }

            public Builder fileCount(int fileCount) {
                stats.fileCount = fileCount;
                return this;
            }

            public Builder totalSizeMB(long totalSizeMB) {
                stats.totalSizeMB = totalSizeMB;
                return this;
            }

            public Builder maxTotalSizeMB(long maxTotalSizeMB) {
                stats.maxTotalSizeMB = maxTotalSizeMB;
                return this;
            }

            public Builder maxDays(int maxDays) {
                stats.maxDays = maxDays;
                return this;
            }

            public Builder currentLogFilePath(String currentLogFilePath) {
                stats.currentLogFilePath = currentLogFilePath;
                return this;
            }

            public MonitorLogStats build() {
                return stats;
            }
        }

        // Getters
        public boolean isEnabled() { return enabled; }
        public int getFileCount() { return fileCount; }
        public long getTotalSizeMB() { return totalSizeMB; }
        public long getMaxTotalSizeMB() { return maxTotalSizeMB; }
        public int getMaxDays() { return maxDays; }
        public String getCurrentLogFilePath() { return currentLogFilePath; }
        public String getError() { return error; }
    }
}
