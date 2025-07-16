package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Gemini API配置类
 * 配置Gemini客户端和相关服务
 *
 * 注意：由于Java版本的Google AI SDK可能存在兼容性问题，
 * 这里先使用RestTemplate进行HTTP调用，后续可以考虑升级到官方SDK
 *
 * @author instser
 * @since 2025-07-14
 */
@Slf4j
@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${gemini.api.timeout:10000}")
    private int timeout;

    /**
     * 创建用于Gemini API调用的RestTemplate
     */
    @Bean("geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        log.info("【Gemini配置】初始化Gemini RestTemplate，API Key: {}...",
                apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "未配置");

        RestTemplate restTemplate = new RestTemplate();

        log.info("【Gemini配置】Gemini RestTemplate初始化成功");
        return restTemplate;
    }

    /**
     * 获取Gemini API密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 获取Gemini API基础URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 获取请求超时时间
     */
    public int getTimeout() {
        return timeout;
    }
}
