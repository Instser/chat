package com.instser.chat.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * OAuth2登录请求DTO
 *
 * @author instser
 * @since 2025-07-17
 */
@Data
public class OAuth2LoginRequest {

    /**
     * OAuth2授权码
     */
    @NotBlank(message = "授权码不能为空")
    private String code;
}
