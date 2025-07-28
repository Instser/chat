package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 管理接口配置
 * 8081端口的管理接口现在只使用API密钥验证，不再需要IP过滤器
 *
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Configuration
public class CacheManagementConfig {

    @PostConstruct
    public void init() {
        log.info("=== 管理接口配置 ===");
        log.info("认证方式: API密钥验证");
        log.info("IP限制: 已移除，支持远程访问");
        log.info("缓存管理接口: http://127.0.0.1:8081/admin/cache/*");
        log.info("日志管理接口: http://127.0.0.1:8081/admin/logs/*");
        log.info("访问方式: 需要在请求中包含有效的API密钥");
        log.info("  - Header方式: X-API-Key: <your-api-key>");
        log.info("  - 参数方式: ?apiKey=<your-api-key>");
        log.info("===================");
    }
}
