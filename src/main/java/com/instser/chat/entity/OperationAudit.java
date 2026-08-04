package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_operation_audit")
public class OperationAudit {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String operator;
    private Long operatorId;
    /** 操作时角色快照 */
    private String operatorRole;
    private String operationType;
    private String target;
    private String targetDetail;
    private String result;
    private String clientIp;
    private String traceId;
    private String requestMethod;
    private String requestPath;
    private String userAgent;
    private String requestSummary;
    private String oldValue;
    private String newValue;
    private String failureReason;
    private Long durationMs;
    /** LOW/MEDIUM/HIGH/CRITICAL */
    private String riskLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
