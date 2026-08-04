package com.instser.chat.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminPackageActionRequest {

    @NotNull(message = "套餐ID不能为空")
    private Long packageId;

    @Min(value = 1, message = "有效天数至少为1")
    private Integer validityDays;

    @Min(value = 0, message = "聊天次数不能为负")
    private Integer chatCount;

    @NotBlank(message = "操作原因不能为空")
    @Size(max = 500, message = "操作原因不能超过500字符")
    private String reason;
}
