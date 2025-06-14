package com.instser.chat.common.exception;

/**
 * 支付相关异常
 *
 * @author instser
 * @since 2024-01-01
 */
public class PaymentException extends BusinessException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentException(String message, String detail) {
        super(500, message, detail);
    }

    /**
     * 支付金额异常
     */
    public static PaymentException invalidAmount(String amount) {
        return new PaymentException("支付金额无效", "金额: " + amount);
    }

    /**
     * 支付方式不支持异常
     */
    public static PaymentException unsupportedPaymentMethod(String method) {
        return new PaymentException("不支持的支付方式", "支付方式: " + method);
    }

    /**
     * 订单状态异常
     */
    public static PaymentException invalidOrderStatus(String orderId, String status) {
        return new PaymentException("订单状态异常", "订单ID: " + orderId + ", 状态: " + status);
    }

    /**
     * 支付回调验证失败
     */
    public static PaymentException callbackVerificationFailed(String reason) {
        return new PaymentException("支付回调验证失败", reason);
    }

    /**
     * 重复支付异常
     */
    public static PaymentException duplicatePayment(String orderId) {
        return new PaymentException("重复支付", "订单ID: " + orderId);
    }
}
