package com.instser.chat.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminModelVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String modelCode;
    private String modelName;
    private String description;
    private String provider;
    private String version;
    private Integer tokenLimit;
    private BigDecimal pricePer1kTokens;
    private Integer isAvailable;
    private Integer isVisible;
    private String baseUrl;
    private boolean apiKeyConfigured;
    private String apiKeyMasked;
    /**
     * 基础权重（1-100）。
     * 说明：轮询维度已改为「单个模型行内的多个密钥」，行级连续失败不再参与选 key；
     * 该列保留为兼容字段（未配置密钥池时的行级统计仍在使用）。
     */
    private Integer weight;
    /** 有效权重 = max(1, weight − failPenalty × consecutiveFails)（行级兼容路径下的展示值） */
    private int effectiveWeight;
    private Long successCount;
    private Long failCount;
    private Integer consecutiveFails;
    private String lastError;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updateTime;
}
