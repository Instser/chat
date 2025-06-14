package com.instser.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class LoginRequest {
    
    /**
     * 用户名或邮箱
     */
    @NotBlank(message = "用户名或邮箱不能为空")
    private String usernameOrEmail;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
