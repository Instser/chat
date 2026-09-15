package com.instser.chat.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminModelUpsertRequest {

    @NotBlank
    @Size(max = 100)
    private String modelCode;

    @NotBlank
    @Size(max = 100)
    private String modelName;

    @Size(max = 500)
    private String description;

    private String provider;
    private String version;
    private Integer tokenLimit;
    private BigDecimal pricePer1kTokens;
    private Integer isAvailable;
    private Integer isVisible;
    private String baseUrl;

    /** 密钥轮询基础权重（1-100，留空默认 100） */
    @Min(value = 1, message = "权重范围为1-100")
    @Max(value = 100, message = "权重范围为1-100")
    private Integer weight;

    @Size(max = 500)
    private String reason;
}
