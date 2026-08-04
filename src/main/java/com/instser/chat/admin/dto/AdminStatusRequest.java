package com.instser.chat.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminStatusRequest {

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
