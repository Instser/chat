package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.UserOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
     * 根据用户ID查询待支付订单列表
     *
     * @param userId 用户ID
     * @return 待支付订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE user_id = #{userId} AND payment_status = 0 AND expire_time > NOW() AND deleted = 0 ORDER BY create_time DESC")
    List<UserOrder> selectPendingOrdersByUserId(Long userId);

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

    /**
     * 查询指定时间之后的待支付订单
     *
     * @param afterTime 时间阈值
     * @param maxCount 最大数量
     * @return 订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time > NOW() " +
            "AND create_time > #{afterTime} AND deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{maxCount}")
    List<UserOrder> selectPendingOrdersAfterTime(@Param("afterTime") LocalDateTime afterTime, @Param("maxCount") int maxCount);

    /**
     * 查询待支付订单
     *
     * @param maxCount 最大数量
     * @return 订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time > NOW() AND deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{maxCount}")
    List<UserOrder> selectPendingOrders(@Param("maxCount") int maxCount);

    /**
     * 查询需要批量处理的订单（包括过期订单和需要状态同步的订单）
     * 限制数量以避免一次处理过多订单
     *
     * @return 订单列表
     */
    @Select("(" +
            "SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time < NOW() AND deleted = 0 " +
            "ORDER BY expire_time ASC LIMIT 50" +
            ") UNION ALL (" +
            "SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time > NOW() " +
            "AND update_time < DATE_SUB(NOW(), INTERVAL 1 HOUR) AND deleted = 0 " +
            "ORDER BY update_time ASC LIMIT 30" +
            ") ORDER BY expire_time ASC, update_time ASC")
    List<UserOrder> selectOrdersForBatchProcessing();

    /**
     * 查询需要状态同步的订单（长时间未更新的待支付订单）
     *
     * @param maxCount 最大数量
     * @return 订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time > NOW() " +
            "AND update_time < DATE_SUB(NOW(), INTERVAL 1 HOUR) AND deleted = 0 " +
            "ORDER BY update_time ASC LIMIT #{maxCount}")
    List<UserOrder> selectOrdersNeedingStatusSync(@Param("maxCount") int maxCount);

    /**
     * 取消用户的所有待支付订单
     *
     * @param userId 用户ID
     * @return 取消的订单数量
     */
    @Update("UPDATE t_user_order SET payment_status = 2, update_time = NOW() " +
            "WHERE user_id = #{userId} AND payment_status = 0 AND expire_time > NOW() AND deleted = 0")
    int cancelUserPendingOrders(Long userId);

    /**
     * 统计用户已支付订单数量
     * 用于判定用户是否从未充值
     *
     * @param userId 用户ID
     * @return 已支付订单数量
     */
    @Select("SELECT COUNT(*) FROM t_user_order WHERE user_id = #{userId} AND payment_status = 1 AND deleted = 0")
    Integer countPaidOrdersByUserId(Long userId);

    /**
     * 统计用户已支付的非免费套餐订单数量
     * 用于判定用户是否只使用免费套餐
     *
     * @param userId 用户ID
     * @return 已支付的非免费套餐订单数量
     */
    @Select("SELECT COUNT(*) FROM t_user_order WHERE user_id = #{userId} AND payment_status = 1 AND package_id != 1 AND deleted = 0")
    Integer countPaidNonFreeOrdersByUserId(Long userId);

    /**
     * 获取用户首次支付时间
     *
     * @param userId 用户ID
     * @return 首次支付时间
     */
    @Select("SELECT MIN(payment_time) FROM t_user_order WHERE user_id = #{userId} AND payment_status = 1 AND deleted = 0")
    LocalDateTime getFirstPaymentTimeByUserId(Long userId);

    /**
     * 获取用户总支付金额
     *
     * @param userId 用户ID
     * @return 总支付金额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_user_order WHERE user_id = #{userId} AND payment_status = 1 AND deleted = 0")
    BigDecimal getTotalPaidAmountByUserId(Long userId);
}
