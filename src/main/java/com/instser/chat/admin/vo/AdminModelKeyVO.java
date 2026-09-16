package com.instser.chat.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型API密钥池视图对象：对外一律只暴露脱敏密钥，绝不回显明文。
 *
 * @author instser
 * @since 2026-09-16
 */
@Data
@Builder
public class AdminModelKeyVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属模型行ID（t_ai_model.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 脱敏后的密钥（前4***后4） */
    private String apiKeyMasked;

    private String remark;

    /** 基础权重（1-100） */
    private Integer weight;

    /** 有效权重 = max(1, weight − failPenalty × consecutiveFails) */
    private int effectiveWeight;

    /** 状态（0-禁用，1-启用） */
    private Integer status;

    private Long successCount;
    private Long failCount;
    private Integer consecutiveFails;
    private String lastError;
    private LocalDateTime lastUsedTime;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime lastFailTime;
    private LocalDateTime createTime;
}
