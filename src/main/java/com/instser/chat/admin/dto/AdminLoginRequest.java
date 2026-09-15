package com.instser.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员登录请求DTO
 *
 * username 兼容用户名或邮箱（与前台登录一致，由 UserDetailsServiceImpl 判定）
 *
 * @author instser
 * @since 2026-09-15
 */
@Data
public class AdminLoginRequest {

    /**
     * 管理员账号（用户名或邮箱）
     */
    @NotBlank(message = "账号不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
