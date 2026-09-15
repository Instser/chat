package com.instser.chat.util;

import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.AdminJwtConfig;
import com.instser.chat.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 管理员 JWT 工具类
 *
 * 用于后台会话隔离：管理员账号仍是 t_user 中 role='admin' 的用户，
 * 但后台 Token 使用独立的 jwt.admin.secret 签名并写入独立的 admin-token Cookie，
 * 与前台 auth-token 物理隔离——两个 Cookie 可同时存在、互不顶号，
 * 且两者的 Token 互换 Cookie 也验证不通过。
 *
 * @author instser
 * @since 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminJwtUtil {

    /**
     * 管理员 Token 类型标记
     */
    private static final String AUTH_TYPE_ADMIN = "admin";

    private final AdminJwtConfig adminJwtConfig;

    /**
     * 获取管理员签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(adminJwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成管理员 JWT Token（默认有效期）
     */
    public String generateToken(User user) {
        return generateToken(user, adminJwtConfig.getExpiration());
    }

    /**
     * 生成管理员 JWT Token（自定义有效期，单位秒）
     */
    public String generateToken(User user, long expirationSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());
        claims.put("authType", AUTH_TYPE_ADMIN);

        return createToken(claims, user.getUsername(), expirationSeconds);
    }

    /**
     * 创建 Token
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * 从 Token 中获取用户角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return (String) claims.get("role");
    }

    /**
     * 从 Token 中获取过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 从 Token 中获取指定声明
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 从 Token 中获取所有声明
     */
    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            LogUtils.logUserWarning(log, null, "管理员JWT验证", "管理员 JWT Token已过期: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            LogUtils.logUserError(log, null, "管理员JWT验证", "不支持的管理员 JWT Token", e);
            throw e;
        } catch (MalformedJwtException e) {
            LogUtils.logUserError(log, null, "管理员JWT验证", "管理员 JWT Token格式错误", e);
            throw e;
        } catch (SecurityException e) {
            LogUtils.logUserError(log, null, "管理员JWT验证", "管理员 JWT Token签名验证失败", e);
            throw e;
        } catch (IllegalArgumentException e) {
            LogUtils.logUserError(log, null, "管理员JWT验证", "管理员 JWT Token参数错误", e);
            throw e;
        }
    }

    /**
     * 检查 Token 是否过期
     */
    public Boolean isTokenExpired(String token) {
        try {
            final Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 验证管理员 Token（校验签名、有效期与 authType 标记）
     */
    public Boolean validateToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            if (isTokenExpired(token)) {
                return false;
            }
            // 仅接受带 authType=admin 标记的后台 Token，前台 Token 一律不认
            return AUTH_TYPE_ADMIN.equals(claims.get("authType", String.class));
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "管理员JWT验证", "管理员 Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证管理员 Token（同时校验用户名）
     */
    public Boolean validateToken(String token, String username) {
        try {
            final String tokenUsername = getUsernameFromToken(token);
            return (username.equals(tokenUsername) && validateToken(token));
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "管理员JWT验证", "管理员 Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建管理员认证 Cookie（默认有效期，名称为 admin-token）
     */
    public ResponseCookie createAuthCookie(String token) {
        return createAuthCookie(token, adminJwtConfig.getExpiration());
    }

    /**
     * 创建管理员认证 Cookie（自定义有效期，单位秒）
     */
    public ResponseCookie createAuthCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(adminJwtConfig.getCookieName(), token)
                .httpOnly(adminJwtConfig.isHttpOnly())
                .secure(adminJwtConfig.isSecure())
                .sameSite(adminJwtConfig.getSameSite())
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .path(adminJwtConfig.getPath())
                .build();
    }

    /**
     * 清除管理员认证 Cookie（仅清 admin-token，不影响前台 auth-token）
     */
    public ResponseCookie clearAuthCookie() {
        return ResponseCookie.from(adminJwtConfig.getCookieName(), "")
                .httpOnly(adminJwtConfig.isHttpOnly())
                .maxAge(0)
                .path(adminJwtConfig.getPath())
                .build();
    }

    /**
     * 从请求头中提取 Token
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(adminJwtConfig.getTokenPrefix())) {
            return authHeader.substring(adminJwtConfig.getTokenPrefix().length());
        }
        return null;
    }
}
