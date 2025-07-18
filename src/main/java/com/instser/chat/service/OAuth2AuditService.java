package com.instser.chat.service;

import com.instser.chat.entity.OAuth2AuditLog;
import com.instser.chat.dao.OAuth2AuditLogDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2审计服务
 *
 * @author instser
 * @since 2025-07-16
 */
@Service
@Slf4j
public class OAuth2AuditService {

    @Autowired
    private OAuth2AuditLogDao auditLogDao;

    /**
     * 记录OAuth2事件（异步）
     */
    @Async
    public void logOAuth2Event(Long userId, String provider, String action, String status,
                              String clientIp, String userAgent, long executionTime,
                              String errorCode, String errorMessage) {

        try {
            OAuth2AuditLog auditLog = new OAuth2AuditLog();
            auditLog.setUserId(userId);
            auditLog.setProvider(provider);
            auditLog.setAction(action);
            auditLog.setStatus(status);
            auditLog.setClientIp(clientIp);
            auditLog.setUserAgent(truncateUserAgent(userAgent));
            auditLog.setExecutionTime((int) executionTime);
            auditLog.setErrorCode(errorCode);
            auditLog.setErrorMessage(truncateErrorMessage(errorMessage));

            auditLogDao.insert(auditLog);

            // 记录关键事件到应用日志
            if ("FAILED".equals(status) || "ERROR".equals(status)) {
                log.warn("OAuth2事件失败: userId={}, provider={}, action={}, error={}",
                    userId, provider, action, errorCode);
            } else {
                log.info("OAuth2事件成功: userId={}, provider={}, action={}",
                    userId, provider, action);
            }

        } catch (Exception e) {
            log.error("记录OAuth2审计日志失败", e);
        }
    }

    /**
     * 记录OAuth2事件（带请求和响应数据）
     */
    @Async
    public void logOAuth2EventWithData(Long userId, String provider, String action, String status,
                                      String clientIp, String userAgent, long executionTime,
                                      String errorCode, String errorMessage,
                                      String requestData, String responseData) {

        try {
            OAuth2AuditLog auditLog = new OAuth2AuditLog();
            auditLog.setUserId(userId);
            auditLog.setProvider(provider);
            auditLog.setAction(action);
            auditLog.setStatus(status);
            auditLog.setClientIp(clientIp);
            auditLog.setUserAgent(truncateUserAgent(userAgent));
            auditLog.setExecutionTime((int) executionTime);
            auditLog.setErrorCode(errorCode);
            auditLog.setErrorMessage(truncateErrorMessage(errorMessage));
            auditLog.setRequestData(truncateData(requestData));
            auditLog.setResponseData(truncateData(responseData));

            auditLogDao.insert(auditLog);

            // 记录详细日志
            log.debug("OAuth2事件详情: userId={}, provider={}, action={}, status={}, requestData={}, responseData={}",
                userId, provider, action, status, 
                truncateForLog(requestData), truncateForLog(responseData));

        } catch (Exception e) {
            log.error("记录OAuth2审计日志失败", e);
        }
    }

    /**
     * 获取用户OAuth2操作历史
     */
    public List<OAuth2AuditLog> getUserOAuth2History(Long userId, int limit) {
        return auditLogDao.findByUserIdOrderByCreateTimeDesc(userId, limit);
    }

    /**
     * 获取指定时间范围内的登录统计
     */
    public OAuth2Statistics getOAuth2Statistics(LocalDateTime startTime, LocalDateTime endTime) {
        int successfulLogins = auditLogDao.countSuccessfulLogins(startTime, endTime);
        int failedLogins = auditLogDao.countFailedLogins(startTime, endTime);
        
        OAuth2Statistics stats = new OAuth2Statistics();
        stats.setSuccessfulLogins(successfulLogins);
        stats.setFailedLogins(failedLogins);
        stats.setTotalLogins(successfulLogins + failedLogins);
        stats.setSuccessRate(calculateSuccessRate(successfulLogins, failedLogins));
        stats.setStartTime(startTime);
        stats.setEndTime(endTime);
        
        return stats;
    }

    /**
     * 根据IP地址查找最近的审计日志
     */
    public List<OAuth2AuditLog> getRecentLogsByIp(String clientIp, int limit) {
        return auditLogDao.findByClientIp(clientIp, limit);
    }

    /**
     * 截断用户代理字符串
     */
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) return null;
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }

    /**
     * 截断错误信息
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null) return null;
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }

    /**
     * 截断数据字段
     */
    private String truncateData(String data) {
        if (data == null) return null;
        return data.length() > 2000 ? data.substring(0, 2000) : data;
    }

    /**
     * 截断日志输出
     */
    private String truncateForLog(String data) {
        if (data == null) return null;
        return data.length() > 200 ? data.substring(0, 200) + "..." : data;
    }

    /**
     * 计算成功率
     */
    private double calculateSuccessRate(int successful, int failed) {
        int total = successful + failed;
        if (total == 0) return 0.0;
        return (double) successful / total * 100;
    }

    /**
     * OAuth2统计信息内部类
     */
    public static class OAuth2Statistics {
        private int successfulLogins;
        private int failedLogins;
        private int totalLogins;
        private double successRate;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        // Getters and Setters
        public int getSuccessfulLogins() { return successfulLogins; }
        public void setSuccessfulLogins(int successfulLogins) { this.successfulLogins = successfulLogins; }
        
        public int getFailedLogins() { return failedLogins; }
        public void setFailedLogins(int failedLogins) { this.failedLogins = failedLogins; }
        
        public int getTotalLogins() { return totalLogins; }
        public void setTotalLogins(int totalLogins) { this.totalLogins = totalLogins; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
}
