package com.instser.chat.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminPackageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String packageName;
    private String description;
    private String packageType;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Integer validityDays;
    private String limitType;
    private String modelPermissions;
    private String features;
    private Integer isEnabled;
    private Integer sortOrder;
    private Integer chatCount;
    private Integer dailyChatLimit;
    private LocalDateTime createdTime;
    private LocalDateTime updateTime;
}
