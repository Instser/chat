package com.instser.chat.util;

import com.instser.chat.config.JwtConfig;
import com.instser.chat.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 试用Token生成测试
 *
 * @author instser
 * @since 2026-08-22
 */
class JwtUtilTrialTokenTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("test-secret-key-for-jwt-trial-token-test-very-long-and-secure-0123456789abcdef");
        jwtConfig.setExpiration(604800);
        jwtUtil = new JwtUtil(jwtConfig);
    }

    @Test
    void generateTrialToken_shouldExpireInTwoHours() {
        User user = new User();
        user.setId(123L);
        user.setUsername("guest_test");
        user.setEmail("guest_test@guest.local");
        user.setRole("user");

        long sessionSeconds = 2 * 3600;
        String token = jwtUtil.generateTrialToken(user, sessionSeconds);

        assertTrue(jwtUtil.validateToken(token));

        // 过期时间应约为 2 小时（允许少量执行耗时偏差）
        long deltaMs = Math.abs(jwtUtil.getExpirationDateFromToken(token).getTime()
                - System.currentTimeMillis() - sessionSeconds * 1000);
        assertTrue(deltaMs < 5000, "过期时间偏差过大：" + deltaMs + "ms");
    }

    @Test
    void generateTrialToken_shouldContainAuthTypeClaim() {
        User user = new User();
        user.setId(456L);
        user.setUsername("guest_abc");
        user.setEmail("guest_abc@guest.local");
        user.setRole("user");

        String token = jwtUtil.generateTrialToken(user, 7200);

        // 通过 JwtUtil 解析验证 claim
        assertEquals(456L, jwtUtil.getUserIdFromToken(token));
        assertEquals("guest_abc", jwtUtil.getUsernameFromToken(token));

        // 验证 authType claim（直接解析 JWT）
        Claims claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-secret-key-for-jwt-trial-token-test-very-long-and-secure-0123456789abcdef".getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        assertEquals("trial", claims.get("authType"));
    }

    @Test
    void generateToken_defaultTtl_shouldRemainSevenDays() {
        User user = new User();
        user.setId(789L);
        user.setUsername("normal_user");
        user.setEmail("normal@example.com");
        user.setRole("user");

        String token = jwtUtil.generateToken(user);

        long deltaMs = Math.abs(jwtUtil.getExpirationDateFromToken(token).getTime()
                - System.currentTimeMillis() - 604800 * 1000);
        assertTrue(deltaMs < 5000, "默认过期时间偏差过大：" + deltaMs + "ms");
    }
}
