package com.instser.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求DTO
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class ChangePasswordRequest {

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度不能少于6位")
    private String newPassword;
}
