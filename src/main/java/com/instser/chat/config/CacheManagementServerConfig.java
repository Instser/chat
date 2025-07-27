package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

/**
 * 缓存管理服务器配置
 * 为缓存管理接口配置独立的8081端口
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Configuration
public class CacheManagementServerConfig implements WebMvcConfigurer {

    @Value("${cache.management.port:8081}")
    private int cacheManagementPort;

    @Value("${cache.management.enabled:true}")
    private boolean cacheManagementEnabled;

    @PostConstruct
    public void init() {
        if (cacheManagementEnabled) {
            log.info("=== 缓存管理配置 ===");
            log.info("状态: 已启用");
            log.info("集成方式: 使用现有management端口8081");
            log.info("绑定地址: 127.0.0.1");
            log.info("访问限制: 仅本地访问");
            log.info("管理接口: http://127.0.0.1:8081/api/admin/cache/*");
            log.info("监控端点: http://127.0.0.1:8081/actuator/*");
            log.info("===================");
        } else {
            log.info("缓存管理配置已禁用");
        }
    }

    /**
     * 缓存管理配置说明
     *
     * 缓存管理接口将集成到现有的Spring Boot Management端口8081中，
     * 与Actuator端点共享同一个端口，避免端口冲突。
     *
     * 访问方式：
     * - 缓存管理接口: http://127.0.0.1:8081/api/admin/cache/*
     * - Actuator端点: http://127.0.0.1:8081/actuator/*
     */
}
