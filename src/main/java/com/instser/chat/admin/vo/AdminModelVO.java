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
    private LocalDateTime createdTime;
    private LocalDateTime updateTime;
}
