package com.instser.chat.util;

import com.instser.chat.config.JwtConfig;
import com.instser.chat.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import com.instser.chat.common.util.LogUtils;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT工具类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtConfig jwtConfig;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
    }

    /**
     * 生成JWT Token（默认有效期）
     */
    public String generateToken(User user) {
        return generateToken(user, jwtConfig.getExpiration());
    }

    /**
     * 生成JWT Token（自定义有效期，单位秒）
     */
    public String generateToken(User user, long expirationSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole());

        return createToken(claims, user.getUsername(), expirationSeconds);
    }

    /**
     * 生成一键试用JWT Token（短时效，仅过期时间与普通Token不同）
     */
    public String generateTrialToken(User user, long expirationSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole());
        // 试用会话标记（不可见，仅用于后续统计/限流扩展）
        claims.put("authType", "trial");

        return createToken(claims, user.getUsername(), expirationSeconds);
    }

    /**
     * 创建Token
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
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从Token中获取用户ID
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
     * 从Token中获取用户角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return (String) claims.get("role");
    }

    /**
     * 从Token中获取过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 从Token中获取指定声明
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 从Token中获取所有声明
     */
    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            LogUtils.logUserWarning(log, null, "JWT验证", "JWT Token已过期: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            LogUtils.logUserError(log, null, "JWT验证", "不支持的JWT Token: {}", e, e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            LogUtils.logUserError(log, null, "JWT验证", "JWT Token格式错误: {}", e, e.getMessage());
            throw e;
        } catch (SecurityException e) {
            LogUtils.logUserError(log, null, "JWT验证", "JWT Token签名验证失败: {}", e, e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            LogUtils.logUserError(log, null, "JWT验证", "JWT Token参数错误: {}", e, e.getMessage());
            throw e;
        }
    }

    /**
     * 检查Token是否过期
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
     * 验证Token
     */
    public Boolean validateToken(String token, String username) {
        try {
            final String tokenUsername = getUsernameFromToken(token);
            return (username.equals(tokenUsername) && !isTokenExpired(token));
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "JWT验证", "Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证Token（不检查用户名）
     */
    public Boolean validateToken(String token) {
        try {
            getAllClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "JWT验证", "Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建认证Cookie（默认有效期）
     */
    public ResponseCookie createAuthCookie(String token) {
        return createAuthCookie(token, jwtConfig.getExpiration());
    }

    /**
     * 创建认证Cookie（自定义有效期，单位秒）
     */
    public ResponseCookie createAuthCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(jwtConfig.getCookieName(), token)
                .httpOnly(jwtConfig.isHttpOnly())
                .secure(jwtConfig.isSecure())
                .sameSite(jwtConfig.getSameSite())
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .path(jwtConfig.getPath())
                .build();
    }

    /**
     * 清除认证Cookie
     */
    public ResponseCookie clearAuthCookie() {
        return ResponseCookie.from(jwtConfig.getCookieName(), "")
                .httpOnly(jwtConfig.isHttpOnly())
                .maxAge(0)
                .path(jwtConfig.getPath())
                .build();
    }

    /**
     * 从请求头中提取Token
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(jwtConfig.getTokenPrefix())) {
            return authHeader.substring(jwtConfig.getTokenPrefix().length());
        }
        return null;
    }
}
