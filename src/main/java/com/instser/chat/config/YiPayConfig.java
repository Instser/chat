package com.instser.chat.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 易支付配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "yipay")
public class YiPayConfig {

    /**
     * 商户ID
     */
    private String pid;

    /**
     * 商户密钥
     */
    private String key;

    /**
     * 易支付网关地址
     */
    private String url;

    /**
     * 支付成功后跳转URL
     */
    private String returnUrl;

    /**
     * 支付回调通知URL
     */
    private String notifyUrl;

    /**
     * 默认支付方式（可选：alipay、wxpay、qqpay等）
     */
    private String defaultType;

    /**
     * 检查配置是否完整
     */
    public boolean isConfigComplete() {
        boolean complete = StringUtils.hasText(pid) && 
                          StringUtils.hasText(key) && 
                          StringUtils.hasText(url);
        
        if (!complete) {
            log.warn("易支付配置不完整 - PID: {}, Key: {}, URL: {}", 
                    StringUtils.hasText(pid) ? "已配置" : "未配置",
                    StringUtils.hasText(key) ? "已配置" : "未配置", 
                    StringUtils.hasText(url) ? "已配置" : "未配置");
        }
        
        return complete;
    }

    /**
     * 获取提交URL
     */
    public String getSubmitUrl() {
        if (StringUtils.hasText(url)) {
            return url.endsWith("/") ? url + "submit.php" : url + "/submit.php";
        }
        return null;
    }

    /**
     * 获取查询URL
     */
    public String getQueryUrl() {
        if (StringUtils.hasText(url)) {
            return url.endsWith("/") ? url + "api.php" : url + "/api.php";
        }
        return null;
    }
}
