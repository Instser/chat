package com.instser.chat.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增模型密钥请求。
 *
 * @author instser
 * @since 2026-09-16
 */
@Data
public class AdminModelKeyAddRequest {

    /** API密钥（必填，入库前去除首尾空白） */
    @NotBlank
    @Size(max = 512)
    private String apiKey;

    /** 基础权重（1-100，留空默认 100） */
    @Min(value = 1, message = "权重范围为1-100")
    @Max(value = 100, message = "权重范围为1-100")
    private Integer weight;

    /** 备注（便于区分渠道/供应商） */
    @Size(max = 128)
    private String remark;

    /** 操作原因（可选） */
    @Size(max = 500)
    private String reason;
}
