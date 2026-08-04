package com.instser.chat.admin.dto;

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

    @Size(max = 500)
    private String reason;
}
