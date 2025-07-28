package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

/**
 * 管理接口服务器配置
 * 为缓存管理和日志管理接口配置8081端口
 *
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Configuration
public class CacheManagementServerConfig implements WebMvcConfigurer {

    @Value("${cache.management.port:8081}")
    private int managementPort;

    @Value("${cache.management.enabled:true}")
    private boolean managementEnabled;

    @PostConstruct
    public void init() {
        if (managementEnabled) {
            log.info("=== 管理接口配置 ===");
            log.info("状态: 已启用");
            log.info("集成方式: 使用现有management端口8081");
            log.info("绑定地址: 127.0.0.1");
            log.info("访问限制: API密钥验证（支持远程访问）");
            log.info("缓存管理接口: http://127.0.0.1:8081/admin/cache/*");
            log.info("日志管理接口: http://127.0.0.1:8081/admin/logs/*");
            log.info("监控端点: http://127.0.0.1:8081/actuator/*");
            log.info("访问方式: 需要在请求中包含有效的API密钥");
            log.info("===================");
        } else {
            log.info("管理接口配置已禁用");
        }
    }

    /**
     * 管理接口配置说明
     *
     * 缓存管理和日志管理接口将集成到现有的Spring Boot Management端口8081中，
     * 与Actuator端点共享同一个端口，避免端口冲突。
     *
     * 访问方式：
     * - 缓存管理接口: http://127.0.0.1:8081/admin/cache/*
     * - 日志管理接口: http://127.0.0.1:8081/admin/logs/*
     * - Actuator端点: http://127.0.0.1:8081/actuator/*
     */
}
