package com.instser.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminPackageUpsertRequest {

    @NotBlank
    @Size(max = 100)
    private String packageName;

    @Size(max = 500)
    private String description;

    @NotBlank
    private String packageType;

    @NotNull
    private BigDecimal price;

    private BigDecimal discountPrice;

    @NotNull
    private Integer validityDays;

    private String limitType;
    private String modelPermissions;
    private String features;
    private Integer isEnabled;
    private Integer sortOrder;

    @Size(max = 500)
    private String reason;
}
