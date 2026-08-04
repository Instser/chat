package com.instser.chat.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminOrderSyncRequest {

    @Min(1)
    @Max(200)
    private Integer maxCount = 50;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
