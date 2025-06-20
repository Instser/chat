package com.instser.chat.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付状态响应VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PaymentStatusResponseVO {

    /**
     * 支付状态 (保持原始状态码: 0=待支付, 1=已支付, 2=已取消, 3=已退款)
     */
    private Integer status;

    /**
     * 状态描述信息
     */
    private String message;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付请求ID
     */
    private String paymentRequestId;

    /**
     * 支付状态字符串（用于API同步）
     */
    private String paymentStatus;

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
     * 订单创建时间
     */
    private LocalDateTime createdTime;

    public PaymentStatusResponseVO() {}

    public PaymentStatusResponseVO(Integer status, String message, String orderNo) {
        this.status = status;
        this.message = message;
        this.orderNo = orderNo;
    }
}
