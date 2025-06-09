package com.instser.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Configuration
public class WebClientConfig {

    /**
     * 创建WebClient Bean
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
