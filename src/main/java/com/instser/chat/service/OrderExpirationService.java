package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;

import java.util.List;

/**
 * 订单过期处理服务接口
 *
 * @author instser
 * @since 2025-06-29
 */
public interface OrderExpirationService {

    /**
     * 处理过期订单（定时任务调用）
     */
    void handleExpiredOrders();

    /**
     * 取消过期订单
     *
     * @param orderNo 订单号
     * @return 是否取消成功
     */
    boolean cancelExpiredOrder(String orderNo);

    /**
     * 批量取消过期订单
     *
     * @param orderNos 订单号列表
     * @return 成功取消的订单数量
     */
    int batchCancelExpiredOrders(List<String> orderNos);

    /**
     * 获取过期订单列表
     *
     * @return 过期订单列表
     */
    List<UserOrder> getExpiredOrders();

    /**
     * 检查订单是否过期
     *
     * @param orderNo 订单号
     * @return 是否过期
     */
    boolean isOrderExpired(String orderNo);

    /**
     * 发送订单过期通知
     *
     * @param order 订单信息
     */
    void sendExpirationNotification(UserOrder order);

    /**
     * 清理过期订单数据（归档）
     *
     * @param daysBeforeArchive 多少天前的过期订单进行归档
     * @return 归档的订单数量
     */
    int archiveExpiredOrders(int daysBeforeArchive);
}
