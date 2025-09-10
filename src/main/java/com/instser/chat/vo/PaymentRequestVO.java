package com.instser.chat.vo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 支付请求VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PaymentRequestVO {

    /**
     * 套餐ID
     */
    @NotNull(message = "套餐ID不能为空")
    private Long packageId;

    /**
     * 货币类型（USD/CNY）
     */
    @NotBlank(message = "货币类型不能为空")
    private String currency;

    /**
     * 支付方式（alipay-支付宝SDK，yipay-易支付，stripe-Stripe支付，paypal-PayPal支付）
     */
    @NotBlank(message = "支付方式不能为空")
    private String paymentMethod;



    /**
     * 用户ID（从token中获取，前端不需要传递）
     */
    private Long userId;

    /**
     * 支付请求唯一标识（用于防重放攻击）
     * 前端可选传递，后端会自动生成
     */
    private String requestId;
}
