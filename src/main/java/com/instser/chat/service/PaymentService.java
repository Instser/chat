package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;

import java.util.List;
import java.util.Map;

/**
 * 支付服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface PaymentService {

    /**
     * 创建支付订单
     *
     * @param paymentRequest 支付请求
     * @return 支付响应
     */
    PaymentResponseVO createPayment(PaymentRequestVO paymentRequest);

    /**
     * 查询支付状态
     *
     * @param orderNo 订单号
     * @return 订单信息
     */
    UserOrder queryPaymentStatus(String orderNo);

    /**
     * 处理支付回调
     *
     * @param paymentMethod 支付方式
     * @param notifyParams 回调参数
     * @return 处理结果
     */
    boolean handlePaymentNotify(String paymentMethod, Map<String, String> notifyParams);

    /**
     * 取消支付
     *
     * @param orderNo 订单号
     * @return 是否成功
     */
    boolean cancelPayment(String orderNo);

    /**
     * 获取支持的支付方式
     *
     * @return 支付方式列表
     */
    List<String> getSupportedPaymentMethods();

    /**
     * 检查支付方式是否可用
     *
     * @param paymentMethod 支付方式
     * @return 是否可用
     */
    boolean isPaymentMethodAvailable(String paymentMethod);
}
