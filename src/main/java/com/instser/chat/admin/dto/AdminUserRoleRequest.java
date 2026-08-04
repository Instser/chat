package com.instser.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserRoleRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "user|admin", message = "角色只能为 user 或 admin")
    private String role;

    @NotBlank(message = "操作原因不能为空")
    @Size(max = 500, message = "操作原因不能超过500字符")
    private String reason;
}
