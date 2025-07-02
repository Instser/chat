package com.instser.chat.config;

import com.alipay.global.api.AlipayClient;
import com.alipay.global.api.DefaultAlipayClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 支付宝配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    /**
     * 应用ID（客户端ID）
     */
    private String clientId;

    /**
     * 商户私钥
     */
    private String privateKey;

    /**
     * 支付宝公钥
     */
    private String publicKey;

    /**
     * 是否沙箱环境
     */
    private Boolean sandbox = true;



    /**
     * 支付回调通知URL
     */
    private String notifyUrl;

    /**
     * 网关地址
     */
    private String gatewayUrl;

    /**
     * 创建支付宝客户端
     */
    @Bean
    public AlipayClient alipayClient() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(privateKey) || !StringUtils.hasText(publicKey)) {
            log.warn("支付宝配置信息不完整，将使用默认配置（仅用于开发测试）");
            // 返回一个默认的客户端，避免启动失败
            return new DefaultAlipayClient(
                    "https://open-sg-global.alipay.com", // 使用新加坡端点作为默认
                    "default_private_key",
                    "default_public_key"
            );
        }

        // 根据是否沙箱环境选择端点
        String endpoint = sandbox ? "https://open-sg-global.alipay.com" : "https://open-sg-global.alipay.com"; // 生产环境也使用SG端点

        log.info("初始化支付宝客户端，沙箱模式: {}, 端点: {}", sandbox, endpoint);

        return new DefaultAlipayClient(endpoint, privateKey, publicKey);
    }

    /**
     * 获取网关地址
     */
    public String getGatewayUrl() {
        if (StringUtils.hasText(gatewayUrl)) {
            return gatewayUrl;
        }
        // 默认使用新加坡网关
        return sandbox ? "https://open-sg-global.alipay.com" : "https://open-sg-global.alipay.com";
    }

    /**
     * 检查配置是否完整
     */
    public boolean isConfigComplete() {
        return StringUtils.hasText(clientId) && 
               StringUtils.hasText(privateKey) && 
               StringUtils.hasText(publicKey);
    }
}
