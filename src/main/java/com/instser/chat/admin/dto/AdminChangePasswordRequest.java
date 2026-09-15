package com.instser.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员修改密码请求DTO
 *
 * @author instser
 * @since 2026-09-15
 */
@Data
public class AdminChangePasswordRequest {

    /**
     * 原密码
     */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度不能少于6位")
    private String newPassword;
}
