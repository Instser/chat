package com.instser.chat.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * PayPal支付配置类
 * 
 * @author instser
 * @since 2024-09-10
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "payment.paypal")
public class PayPalConfig {

    /**
     * PayPal客户端ID
     */
    private String clientId;

    /**
     * PayPal客户端密钥
     */
    private String clientSecret;

    /**
     * PayPal环境模式（sandbox/live）
     */
    private String mode = "sandbox";

    /**
     * 默认货币
     */
    private String currency = "USD";

    /**
     * 支持的货币列表
     */
    private List<String> supportedCurrencies = Arrays.asList(
        "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "SEK", "NOK", "DKK", "CNY"
    );

    /**
     * PayPal API基础URL
     */
    private String baseUrl;

    /**
     * 连接超时时间（秒）
     */
    private int connectTimeout = 30;

    /**
     * 读取超时时间（秒）
     */
    private int readTimeout = 80;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * Webhook ID (用于验证webhook签名)
     */
    private String webhookId;

    /**
     * 支付成功后跳转URL（可选）
     */
    private String returnUrl;

    /**
     * 支付取消后跳转URL（可选）
     */
    private String cancelUrl;

    /**
     * 是否启用PayPal支付
     */
    private boolean enabled = true;

    /**
     * 配置完整性检查
     */
    public boolean isConfigComplete() {
        boolean complete = enabled && 
                          StringUtils.hasText(clientId) && 
                          StringUtils.hasText(clientSecret) && 
                          StringUtils.hasText(mode);
        
        if (!complete) {
            log.warn("PayPal配置不完整：enabled={}, clientId={}, clientSecret={}, mode={}", 
                enabled ? "已启用" : "已禁用",
                StringUtils.hasText(clientId) ? "已配置" : "未配置",
                StringUtils.hasText(clientSecret) ? "已配置" : "未配置", 
                StringUtils.hasText(mode) ? mode : "未配置");
        }
        
        return complete;
    }

    /**
     * 检查货币是否支持
     */
    public boolean isCurrencySupported(String currency) {
        if (currency == null) {
            return false;
        }
        return supportedCurrencies.contains(currency.toUpperCase());
    }

    /**
     * 获取环境类型描述
     */
    public String getEnvironment() {
        return "sandbox".equalsIgnoreCase(mode) ? "沙箱环境" : "生产环境";
    }

    /**
     * 是否为沙箱环境
     */
    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(mode);
    }

    /**
     * 获取PayPal API基础URL
     */
    public String getApiBaseUrl() {
        if (StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        return isSandbox() ? "https://api-m.sandbox.paypal.com" : "https://api-m.paypal.com";
    }

    /**
     * 初始化PayPal配置
     */
    @PostConstruct
    public void initPayPal() {
        if (isConfigComplete()) {
            log.info("PayPal配置初始化成功");
            log.info("- 环境: {}", getEnvironment());
            log.info("- API URL: {}", getApiBaseUrl());
            log.info("- 默认货币: {}", currency.toUpperCase());
            log.info("- 支持货币: {}", supportedCurrencies);
            log.info("- 连接超时: {}秒", connectTimeout);
            log.info("- 读取超时: {}秒", readTimeout);
            log.info("- 最大重试: {}次", maxRetries);
            
        } else {
            log.warn("PayPal配置不完整，PayPal支付功能将不可用");
            if (!enabled) {
                log.info("PayPal支付已被禁用");
            } else {
                log.warn("请检查以下配置项：");
                log.warn("- payment.paypal.enabled: 是否启用PayPal支付");
                log.warn("- payment.paypal.client-id: PayPal客户端ID");
                log.warn("- payment.paypal.client-secret: PayPal客户端密钥");  
                log.warn("- payment.paypal.mode: PayPal环境模式 (sandbox/live)");
            }
        }
    }
}