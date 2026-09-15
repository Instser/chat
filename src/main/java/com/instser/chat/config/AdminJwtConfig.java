package com.instser.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 管理员 JWT 配置类（后台会话隔离）
 *
 * 管理员账号仍是 t_user 中 role='admin' 的用户，但后台登录使用独立密钥签发 Token，
 * 并写入独立 Cookie（默认 admin-token），与前台 jwt.* 的 auth-token 并存互不顶号。
 *
 * @author instser
 * @since 2026-09-15
 */
@Configuration
@ConfigurationProperties(prefix = "jwt.admin")
@Data
public class AdminJwtConfig {

    /**
     * 管理员 JWT 密钥（必须与前台 jwt.secret 不同）
     */
    private String secret = "change-me-admin-jwt-secret-key";

    /**
     * 管理员 Token 过期时间（秒）- 12小时
     */
    private long expiration = 43200;

    /**
     * 管理员 Cookie 名称
     */
    private String cookieName = "admin-token";

    /**
     * 管理员 Cookie 是否 HttpOnly
     */
    private boolean httpOnly = true;

    /**
     * 管理员 Cookie 是否 Secure（HTTPS）
     */
    private boolean secure = false;

    /**
     * 管理员 Cookie SameSite 策略
     */
    private String sameSite = "Lax";

    /**
     * 管理员 Cookie 路径
     */
    private String path = "/api";

    /**
     * 请求头名称
     */
    private String headerName = "Authorization";

    /**
     * Token 前缀
     */
    private String tokenPrefix = "Bearer ";
}
