package com.instser.chat.config;

import com.stripe.Stripe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Stripe支付配置类
 * 
 * @author instser
 * @since 2025-08-29
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "payment.stripe")
public class StripeConfig {

    /**
     * Stripe公钥 (pk_test_xxx 或 pk_live_xxx)
     */
    private String publicKey;

    /**
     * Stripe私钥 (sk_test_xxx 或 sk_live_xxx)
     */
    private String secretKey;

    /**
     * Webhook签名密钥 (whsec_xxx)
     */
    private String webhookSecret;

    /**
     * 默认货币
     */
    private String currency = "usd";

    /**
     * 是否沙箱模式
     */
    private boolean sandbox = true;

    /**
     * 支持的货币列表
     */
    private List<String> supportedCurrencies = Arrays.asList(
        "usd", "eur", "gbp", "jpy", "cad", "aud", "chf", "sek", "nok", "dkk"
    );

    /**
     * Stripe API版本
     */
    private String apiVersion = "2024-06-20";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 30000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 80000;

    /**
     * 最大网络重试次数
     */
    private int maxNetworkRetries = 2;

    /**
     * 配置完整性检查
     */
    public boolean isConfigComplete() {
        boolean complete = StringUtils.hasText(publicKey) && 
                          StringUtils.hasText(secretKey) && 
                          StringUtils.hasText(webhookSecret);
        
        if (!complete) {
            log.warn("Stripe配置不完整：publicKey={}, secretKey={}, webhookSecret={}", 
                StringUtils.hasText(publicKey) ? "已配置" : "未配置",
                StringUtils.hasText(secretKey) ? "已配置" : "未配置", 
                StringUtils.hasText(webhookSecret) ? "已配置" : "未配置");
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
        return supportedCurrencies.contains(currency.toLowerCase());
    }

    /**
     * 获取环境类型描述
     */
    public String getEnvironment() {
        return sandbox ? "测试环境" : "生产环境";
    }

    /**
     * 初始化Stripe配置
     */
    @PostConstruct
    public void initStripe() {
        if (isConfigComplete()) {
            // 设置API密钥
            Stripe.apiKey = secretKey;
            
            // 注意：Stripe Java SDK 24.16.0 不支持动态设置API版本
            // API版本通过HTTP头设置，这里仅作记录
            
            // 设置超时配置
            Stripe.setConnectTimeout(connectTimeout);
            Stripe.setReadTimeout(readTimeout);
            Stripe.setMaxNetworkRetries(maxNetworkRetries);
            
            log.info("Stripe配置初始化成功");
            log.info("- 环境: {}", getEnvironment());
            log.info("- API版本: {}", apiVersion);
            log.info("- 默认货币: {}", currency.toUpperCase());
            log.info("- 支持货币: {}", supportedCurrencies);
            log.info("- 连接超时: {}ms", connectTimeout);
            log.info("- 读取超时: {}ms", readTimeout);
            
        } else {
            log.warn("Stripe配置不完整，跳过初始化");
            log.warn("请检查以下配置项：");
            log.warn("- payment.stripe.public-key: Stripe公钥");
            log.warn("- payment.stripe.secret-key: Stripe私钥");  
            log.warn("- payment.stripe.webhook-secret: Webhook签名密钥");
        }
    }
}