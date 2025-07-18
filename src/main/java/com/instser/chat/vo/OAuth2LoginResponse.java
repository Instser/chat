package com.instser.chat.vo;

import lombok.Builder;
import lombok.Data;

/**
 * OAuth2授权响应VO（用于获取授权URL）
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
@Builder
public class OAuth2LoginResponse {

    /**
     * 授权URL
     */
    private String authUrl;

    /**
     * OAuth提供商
     */
    private String provider;

    /**
     * 状态令牌
     */
    private String state;

    /**
     * 过期时间（秒）
     */
    private Integer expiresIn;
}
