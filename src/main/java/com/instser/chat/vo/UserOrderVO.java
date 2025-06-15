package com.instser.chat.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户订单VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class UserOrderVO {

    /**
     * 主键ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private String id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private String userId;

    /**
     * 套餐ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private String packageId;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 货币类型
     */
    private String currency;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 支付状态（0-未支付，1-已支付，2-已取消，3-已退款）
     */
    private Integer paymentStatus;

    /**
     * 支付状态描述
     */
    private String paymentStatusDesc;

    /**
     * 支付时间
     */
    private LocalDateTime paymentTime;

    /**
     * 交易流水号
     */
    private String transactionId;

    /**
     * 支付跳转URL
     */
    private String paymentUrl;

    /**
     * 订单过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
