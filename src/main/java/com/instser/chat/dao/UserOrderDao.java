package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.UserOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户订单数据访问层
 *
 * @author instser
 * @since 2024-01-01
 */
@Mapper
public interface UserOrderDao extends BaseMapper<UserOrder> {

    /**
     * 根据订单号查询订单
     *
     * @param orderNo 订单号
     * @return 订单信息
     */
    @Select("SELECT * FROM t_user_order WHERE order_no = #{orderNo} AND deleted = 0 LIMIT 1")
    UserOrder selectByOrderNo(String orderNo);

    /**
     * 根据用户ID查询订单列表
     *
     * @param userId 用户ID
     * @return 订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<UserOrder> selectByUserId(Long userId);

    /**
     * 根据支付请求ID查询订单
     *
     * @param paymentRequestId 支付请求ID
     * @return 订单信息
     */
    @Select("SELECT * FROM t_user_order WHERE payment_request_id = #{paymentRequestId} AND deleted = 0 LIMIT 1")
    UserOrder selectByPaymentRequestId(String paymentRequestId);

    /**
     * 更新订单支付状态
     *
     * @param orderNo 订单号
     * @param paymentStatus 支付状态
     * @param transactionId 交易流水号
     * @return 更新行数
     */
    @Update("UPDATE t_user_order SET payment_status = #{paymentStatus}, transaction_id = #{transactionId}, " +
            "payment_time = NOW(), update_time = NOW() WHERE order_no = #{orderNo} AND deleted = 0")
    int updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId);

    /**
     * 查询用户的有效订单数量
     *
     * @param userId 用户ID
     * @return 有效订单数量
     */
    @Select("SELECT COUNT(*) FROM t_user_order WHERE user_id = #{userId} AND payment_status = 1 AND deleted = 0")
    int countValidOrdersByUserId(Long userId);

    /**
     * 清理过期订单（将过期的未支付订单标记为已取消）
     *
     * @return 清理的订单数量
     */
    @Update("UPDATE t_user_order SET payment_status = 2, update_time = NOW() " +
            "WHERE payment_status = 0 AND expire_time < NOW() AND deleted = 0")
    int cleanupExpiredOrders();

    /**
     * 同步待支付订单状态（查询超过1小时未更新的待支付订单）
     *
     * @return 需要同步的订单数量
     */
    @Select("SELECT COUNT(*) FROM t_user_order " +
            "WHERE payment_status = 0 AND expire_time > NOW() " +
            "AND update_time < DATE_SUB(NOW(), INTERVAL 1 HOUR) AND deleted = 0")
    int syncPendingOrderStatus();

    /**
     * 查询过期的未支付订单
     *
     * @return 过期订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time < NOW() AND deleted = 0")
    List<UserOrder> selectExpiredOrders();
}
