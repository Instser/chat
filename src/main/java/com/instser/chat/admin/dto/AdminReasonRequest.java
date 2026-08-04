package com.instser.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 高风险操作统一要求 reason。
 */
@Data
public class AdminReasonRequest {

    @NotBlank(message = "操作原因不能为空")
    @Size(max = 500, message = "操作原因不能超过500字符")
    private String reason;
}
