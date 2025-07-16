package com.instser.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini API密钥轮询配置类
 * 
 * @author instser
 * @since 2025-07-16
 */
@Data
@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiApiKeyConfig {
    
    /**
     * API密钥列表
     */
    private List<String> keys = new ArrayList<>();
    
    /**
     * 密钥轮询策略：ROUND_ROBIN(轮询), RANDOM(随机), WEIGHTED(加权)
     */
    private String strategy = "ROUND_ROBIN";
    
    /**
     * 基础URL
     */
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    
    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = 10000;
    
    /**
     * 密钥健康检查间隔（秒）
     */
    private int healthCheckInterval = 300;
    
    /**
     * 密钥失败重试次数
     */
    private int maxRetries = 3;
    
    /**
     * 密钥禁用时间（秒）
     */
    private int disableTimeout = 600;
}
