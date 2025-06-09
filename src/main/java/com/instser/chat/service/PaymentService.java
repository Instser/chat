package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;

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
     * @param notifyBody 回调内容
     * @param signature 签名
     * @param requestTime 请求时间
     * @param clientId 客户端ID
     * @return 处理结果
     */
    boolean handlePaymentNotify(String notifyBody, String signature, String requestTime, String clientId);

    /**
     * 取消支付
     *
     * @param orderNo 订单号
     * @return 是否成功
     */
    boolean cancelPayment(String orderNo);

    /**
     * 验证支付回调签名
     *
     * @param requestUri 请求URI
     * @param requestMethod 请求方法
     * @param clientId 客户端ID
     * @param requestTime 请求时间
     * @param signature 签名
     * @param notifyBody 回调内容
     * @return 验证结果
     */
    boolean verifyNotifySignature(String requestUri, String requestMethod, String clientId, 
                                 String requestTime, String signature, String notifyBody);
}
