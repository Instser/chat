package com.instser.chat.strategy;

import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;

/**
 * 支付策略接口
 * 定义不同支付方式的统一接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface PaymentStrategy {

    /**
     * 获取支付方式名称
     *
     * @return 支付方式名称
     */
    String getPaymentMethod();

    /**
     * 创建支付订单
     *
     * @param paymentRequest 支付请求
     * @return 支付响应
     */
    PaymentResponseVO createPayment(PaymentRequestVO paymentRequest);

    /**
     * 处理支付回调
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    boolean handleCallback(String callbackData);

    /**
     * 验证支付配置是否完整
     *
     * @return 配置是否完整
     */
    boolean isConfigComplete();

    /**
     * 查询支付状态
     *
     * @param orderNo 订单号
     * @return 支付状态
     */
    String queryPaymentStatus(String orderNo);
}
