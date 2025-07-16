package com.instser.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API密钥状态实体类
 * 用于跟踪每个API密钥的使用状态和统计信息
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyStatus {
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * 是否可用
     */
    private boolean available = true;
    
    /**
     * 失败次数
     */
    private int failureCount = 0;
    
    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsed;
    
    /**
     * 最后失败时间
     */
    private LocalDateTime lastFailure;
    
    /**
     * 禁用到期时间
     */
    private LocalDateTime disabledUntil;
    
    /**
     * 总请求次数
     */
    private long totalRequests = 0;
    
    /**
     * 成功请求次数
     */
    private long successRequests = 0;
    
    /**
     * 权重（用于加权轮询）
     */
    private int weight = 1;
}
