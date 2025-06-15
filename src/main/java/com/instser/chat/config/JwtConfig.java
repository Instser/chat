package com.instser.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {
    
    /**
     * JWT密钥
     */
    private String secret = "change-me-jwt-secret-key";
    
    /**
     * Token过期时间（秒）- 7天
     */
    private long expiration = 604800;
    
    /**
     * Cookie名称
     */
    private String cookieName = "auth-token";
    
    /**
     * Cookie是否HttpOnly
     */
    private boolean httpOnly = false;
    
    /**
     * Cookie是否Secure（HTTPS）
     */
    private boolean secure = false;
    
    /**
     * Cookie SameSite策略
     */
    private String sameSite = "Lax";

    /**
     * Cookie路径
     */
    private String path = "/api";

    /**
     * Token前缀
     */
    private String tokenPrefix = "Bearer ";

    /**
     * 请求头名称
     */
    private String headerName = "Authorization";
}
