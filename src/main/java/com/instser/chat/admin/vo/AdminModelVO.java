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
    /** 基础权重（1-100，同 model_code 多行密钥轮询用） */
    private Integer weight;
    /** 有效权重 = max(1, weight − failPenalty × consecutiveFails) */
    private int effectiveWeight;
    private Long successCount;
    private Long failCount;
    private Integer consecutiveFails;
    private String lastError;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updateTime;
}
