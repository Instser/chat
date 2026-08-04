package com.instser.chat.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminOrderVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String orderNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;

    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private Integer paymentStatus;
    private LocalDateTime paymentTime;
    private String transactionId;
    private LocalDateTime expireTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
