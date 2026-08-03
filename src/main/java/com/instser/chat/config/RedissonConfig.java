package com.instser.chat.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Redisson配置类
 * 配置Redisson客户端连接参数
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    // 本地 Redis 仅 requirepass 时可不配置 username；有 ACL 再传用户名
    @Value("${spring.redis.username:}")
    private String redisUsername;

    @Value("${spring.redis.password:your-redis-password}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.timeout:3000ms}")
    private String redisTimeoutStr;

    /**
     * 解析timeout字符串为毫秒数
     * 支持格式：3000ms, 3000, 3s等
     */
    private int parseTimeout(String timeoutStr) {
        if (timeoutStr == null || timeoutStr.trim().isEmpty()) {
            return 3000; // 默认3秒
        }

        String timeout = timeoutStr.trim().toLowerCase();

        if (timeout.endsWith("ms")) {
            return Integer.parseInt(timeout.substring(0, timeout.length() - 2));
        } else if (timeout.endsWith("s")) {
            return Integer.parseInt(timeout.substring(0, timeout.length() - 1)) * 1000;
        } else {
            // 假设是毫秒数
            return Integer.parseInt(timeout);
        }
    }

    /**
     * 配置Redisson客户端
     * 使用单节点模式连接Redis
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 解析timeout值
        int timeoutMs = parseTimeout(redisTimeoutStr);

        // 构建Redis连接地址
        String redisAddress = String.format("redis://%s:%d", redisHost, redisPort);
        log.info("配置Redisson连接地址: {}, 超时时间: {}ms", redisAddress, timeoutMs);

        // 配置单节点模式
        var singleServerConfig = config.useSingleServer()
                .setAddress(redisAddress)
                .setDatabase(redisDatabase)
                .setConnectTimeout(timeoutMs)
                .setTimeout(timeoutMs)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20)
                .setIdleConnectionTimeout(10000)
                .setPingConnectionInterval(30000)
                .setKeepAlive(true)
                // 设置DNS监控间隔
                .setDnsMonitoringInterval(5000);

        // 本地 Redis 常见仅 requirepass（无 ACL 用户名）；云 Redis 可能同时有 username+password
        boolean hasPassword = redisPassword != null && !redisPassword.trim().isEmpty();
        boolean hasUsername = redisUsername != null && !redisUsername.trim().isEmpty();
        if (hasPassword) {
            singleServerConfig.setPassword(redisPassword);
            if (hasUsername) {
                log.info("配置Redis认证信息，用户名: {}", redisUsername);
                singleServerConfig.setUsername(redisUsername);
            } else {
                log.info("配置Redis密码认证（无用户名/ACL）");
            }
        } else {
            log.info("未配置Redis认证信息，使用无密码连接");
        }

        // 设置编解码器
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());

        try {
            RedissonClient redissonClient = Redisson.create(config);
            log.info("Redisson客户端创建成功，连接到: {}:{}", redisHost, redisPort);
            return redissonClient;
        } catch (Exception e) {
            log.error("创建Redisson客户端失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法连接到Redis服务器: " + redisHost + ":" + redisPort, e);
        }
    }
}
