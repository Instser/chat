package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户订单实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_user_order")
public class UserOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 订单编号
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 用户ID
     */
    @TableField("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 套餐ID
     */
    @TableField("package_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;

    /**
     * 订单金额
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 货币类型（USD/CNY）
     */
    @TableField("currency")
    private String currency;

    /**
     * 支付方式（alipay/wechat/paypal等）
     */
    @TableField("payment_method")
    private String paymentMethod;

    /**
     * 支付状态（0-未支付，1-已支付，2-已取消，3-已退款）
     */
    @TableField("payment_status")
    private Integer paymentStatus;

    /**
     * 支付时间
     */
    @TableField("payment_time")
    private LocalDateTime paymentTime;

    /**
     * 交易流水号（支付宝返回的交易号）
     */
    @TableField("transaction_id")
    private String transactionId;

    /**
     * 支付请求ID（发送给支付宝的请求ID）
     */
    @TableField("payment_request_id")
    private String paymentRequestId;

    /**
     * 支付跳转URL
     */
    @TableField("payment_url")
    private String paymentUrl;

    /**
     * 订单过期时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 备注信息
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
