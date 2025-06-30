package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付审计日志实体类
 *
 * @author instser
 * @since 2025-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_payment_audit")
public class PaymentAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 订单号
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 操作类型（CREATE/PAY/CANCEL/REFUND/QUERY等）
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 原状态
     */
    @TableField("old_status")
    private Integer oldStatus;

    /**
     * 新状态
     */
    @TableField("new_status")
    private Integer newStatus;

    /**
     * 操作人ID（系统操作时为null）
     */
    @TableField("operator_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /**
     * 操作人类型（USER/SYSTEM/ADMIN）
     */
    @TableField("operator_type")
    private String operatorType;

    /**
     * 操作原因/描述
     */
    @TableField("operation_reason")
    private String operationReason;

    /**
     * 操作详情（JSON格式）
     */
    @TableField("operation_details")
    private String operationDetails;

    /**
     * 客户端IP
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * 用户代理
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（0-未删除，1-已删除）
     */
    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
