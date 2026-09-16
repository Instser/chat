package com.instser.chat.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新模型密钥请求：权重/状态/备注均可选，留空表示不变更。
 *
 * @author instser
 * @since 2026-09-16
 */
@Data
public class AdminModelKeyUpdateRequest {

    /** 基础权重（1-100） */
    @Min(value = 1, message = "权重范围为1-100")
    @Max(value = 100, message = "权重范围为1-100")
    private Integer weight;

    /** 状态（0-禁用，1-启用） */
    @Min(value = 0, message = "状态仅支持0或1")
    @Max(value = 1, message = "状态仅支持0或1")
    private Integer status;

    /** 备注（便于区分渠道/供应商） */
    @Size(max = 128)
    private String remark;

    /** 操作原因（可选） */
    @Size(max = 500)
    private String reason;
}
