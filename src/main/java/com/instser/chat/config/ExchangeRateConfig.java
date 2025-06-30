package com.instser.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 汇率服务配置类
 *
 * @author instser
 * @since 2025-06-30
 */
@Configuration
@ConfigurationProperties(prefix = "exchange")
@Data
public class ExchangeRateConfig {
    
    /**
     * API配置
     */
    private Api api = new Api();
    
    /**
     * 缓存配置
     */
    private Cache cache = new Cache();
    
    /**
     * 默认汇率配置
     */
    private Map<String, BigDecimal> defaultRates;
    
    @Data
    public static class Api {
        /**
         * 汇率API地址
         */
        private String url = "https://api.exchangerate-api.com/v4/latest/";
        
        /**
         * 是否启用汇率API
         */
        private boolean enabled = true;
        
        /**
         * API密钥（如需要）
         */
        private String key;
        
        /**
         * 请求超时时间（毫秒）
         */
        private int timeout = 5000;
        
        /**
         * 重试次数
         */
        private int retryCount = 3;
    }
    
    @Data
    public static class Cache {
        /**
         * 缓存过期时间（分钟）
         */
        private int expireMinutes = 30;
        
        /**
         * 缓存前缀
         */
        private String keyPrefix = "exchange_rate";
    }
}
