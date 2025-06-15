package com.instser.chat.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付响应VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PaymentResponseVO {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付请求ID
     */
    private String paymentRequestId;

    /**
     * 支付跳转URL
     */
    private String paymentUrl;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 货币类型
     */
    private String currency;

    /**
     * 支付状态
     */
    private Integer paymentStatus;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 响应消息
     */
    private String message;
}
