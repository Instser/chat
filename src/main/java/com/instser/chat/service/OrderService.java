package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.UserOrder;

import java.util.List;

/**
 * 订单服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface OrderService extends IService<UserOrder> {

    /**
     * 创建订单
     *
     * @param userId 用户ID
     * @param packageId 套餐ID
     * @param currency 货币类型
     * @param paymentMethod 支付方式
     * @return 订单信息
     */
    UserOrder createOrder(Long userId, Long packageId, String currency, String paymentMethod);

    /**
     * 根据订单号获取订单
     *
     * @param orderNo 订单号
     * @return 订单信息
     */
    UserOrder getOrderByOrderNo(String orderNo);

    /**
     * 根据支付请求ID获取订单
     *
     * @param paymentRequestId 支付请求ID
     * @return 订单信息
     */
    UserOrder getOrderByPaymentRequestId(String paymentRequestId);

    /**
     * 根据用户ID获取订单列表
     *
     * @param userId 用户ID
     * @return 订单列表
     */
    List<UserOrder> getOrdersByUserId(Long userId);

    /**
     * 更新订单支付状态
     *
     * @param orderNo 订单号
     * @param paymentStatus 支付状态
     * @param transactionId 交易流水号
     * @return 是否更新成功
     */
    boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId);

    /**
     * 取消订单
     *
     * @param orderNo 订单号
     * @return 是否取消成功
     */
    boolean cancelOrder(String orderNo);

    /**
     * 检查订单是否过期
     *
     * @param orderNo 订单号
     * @return 是否过期
     */
    boolean isOrderExpired(String orderNo);

    /**
     * 生成订单号
     *
     * @return 订单号
     */
    String generateOrderNo();
}
