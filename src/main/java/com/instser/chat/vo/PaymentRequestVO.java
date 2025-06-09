package com.instser.chat.vo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

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
     * 支付方式（alipay）
     */
    @NotBlank(message = "支付方式不能为空")
    private String paymentMethod;

    /**
     * 支付类型（alipay-支付宝SDK，yipay-易支付）
     */
    @NotBlank(message = "支付类型不能为空")
    private String type;

    /**
     * 支付成功后跳转URL
     */
    private String returnUrl;

    /**
     * 用户ID（从token中获取，前端不需要传递）
     */
    private Long userId;
}
