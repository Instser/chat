package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import com.instser.chat.vo.PaymentMethodVO;
import com.instser.chat.vo.PaymentStatusResponseVO;

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
     * 查询支付状态并同步最新状态
     *
     * @param orderNo 订单号
     * @return 支付状态响应
     */
    PaymentStatusResponseVO queryAndSyncPaymentStatus(String orderNo);

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

    /**
     * 处理易支付回调通知
     *
     * @param notifyParams 回调参数
     * @return 处理结果
     */
    boolean handleYiPayNotify(Map<String, String> notifyParams);

    /**
     * 处理微信支付回调通知（通过易支付）
     *
     * @param notifyParams 回调参数
     * @return 处理结果
     */
    boolean handleWechatPayNotify(Map<String, String> notifyParams);

    /**
     * 处理支付宝回调通知（通过易支付）
     *
     * @param notifyParams 回调参数
     * @return 处理结果
     */
    boolean handleAlipayNotify(Map<String, String> notifyParams);

    /**
     * 异步处理支付回调通知
     * 立即返回，避免易支付重试，后台异步处理回调逻辑
     *
     * @param notifyParams 回调参数
     */
    void processPaymentCallbackAsync(Map<String, String> notifyParams);

    /**
     * 获取可用的支付方式列表
     *
     * @return 支付方式列表
     */
    List<PaymentMethodVO> getAvailablePaymentMethods();

    /**
     * 获取指定的支付策略
     *
     * @param paymentMethod 支付方式
     * @return 支付策略
     */
    PaymentStrategy getPaymentStrategy(String paymentMethod);
}
