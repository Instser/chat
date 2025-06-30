package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.UserOrder;

import java.time.LocalDateTime;
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
     * 根据用户ID获取待支付订单列表
     *
     * @param userId 用户ID
     * @return 待支付订单列表
     */
    List<UserOrder> getPendingOrdersByUserId(Long userId);

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

    /**
     * 清理过期订单
     *
     * @return 清理的订单数量
     */
    int cleanupExpiredOrders();

    /**
     * 同步待支付订单状态
     *
     * @return 同步的订单数量
     */
    int syncPendingOrderStatus();

    /**
     * 取消用户的所有待支付订单
     *
     * @param userId 用户ID
     * @return 取消的订单数量
     */
    int cancelUserPendingOrders(Long userId);

    /**
     * 获取指定时间之后的待支付订单
     *
     * @param afterTime 时间阈值
     * @param maxCount 最大数量
     * @return 订单列表
     */
    List<UserOrder> getPendingOrdersAfterTime(LocalDateTime afterTime, int maxCount);

    /**
     * 获取所有待支付订单
     *
     * @param maxCount 最大数量
     * @return 订单列表
     */
    List<UserOrder> getPendingOrders(int maxCount);

    /**
     * 获取需要批量处理的订单（包括过期订单和需要状态同步的订单）
     *
     * @return 订单列表
     */
    List<UserOrder> getOrdersForBatchProcessing();

    /**
     * 获取过期的未支付订单
     *
     * @return 过期订单列表
     */
    List<UserOrder> getExpiredOrders();

    /**
     * 获取需要状态同步的订单（长时间未更新的待支付订单）
     *
     * @param maxCount 最大数量
     * @return 订单列表
     */
    List<UserOrder> getOrdersNeedingStatusSync(int maxCount);
}
