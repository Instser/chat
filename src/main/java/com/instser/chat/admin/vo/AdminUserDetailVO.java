package com.instser.chat.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;
    private String email;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createdTime;
    private LocalDateTime updateTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;

    private Integer chatCountRemaining;
    private Integer totalChatCount;
    private LocalDateTime packageExpireTime;
    private Long tokenBalance;
    private Long tokenUsed;
    private String membershipLevel;
    private LocalDateTime membershipExpireTime;
    private BigDecimal totalAmountSpent;
    private Long packageRemainingDays;
}
