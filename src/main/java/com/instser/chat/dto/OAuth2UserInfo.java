package com.instser.chat.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2用户信息DTO
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
public class OAuth2UserInfo {

    /**
     * 第三方用户ID
     */
    private String id;

    /**
     * OAuth提供商
     */
    private String provider;

    /**
     * 用户名
     */
    private String name;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 原始数据（JSON格式）
     */
    private String rawData;
}
