package com.instser.chat.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserSummaryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createdTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;

    private Integer chatCountRemaining;
    private LocalDateTime packageExpireTime;
}
