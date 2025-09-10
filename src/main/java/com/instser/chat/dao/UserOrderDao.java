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
     * 根据交易流水号查询订单
     *
     * @param transactionId 交易流水号
     * @return 订单信息
     */
    @Select("SELECT * FROM t_user_order WHERE transaction_id = #{transactionId} AND deleted = 0 LIMIT 1")
    UserOrder selectByTransactionId(String transactionId);

    /**
     * 更新订单支付状态（总是设置支付时间）
     *
     * @param orderNo 订单号
     * @param paymentStatus 支付状态
     * @param transactionId 交易流水号
     * @param paymentTime 支付时间
     * @param updateTime 更新时间
     * @return 更新行数
     */
    @Update("UPDATE t_user_order SET payment_status = #{paymentStatus}, transaction_id = #{transactionId}, " +
            "payment_time = #{paymentTime}, update_time = #{updateTime} WHERE order_no = #{orderNo} AND deleted = 0")
    int updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId,
                           LocalDateTime paymentTime, LocalDateTime updateTime);

    /**
     * 更新订单支付状态（不设置支付时间）
     *
     * @param orderNo 订单号
     * @param paymentStatus 支付状态
     * @param transactionId 交易流水号
     * @param updateTime 更新时间
     * @return 更新行数
     */
    @Update("UPDATE t_user_order SET payment_status = #{paymentStatus}, transaction_id = #{transactionId}, " +
            "update_time = #{updateTime} WHERE order_no = #{orderNo} AND deleted = 0")
    int updatePaymentStatusWithoutTime(String orderNo, Integer paymentStatus, String transactionId,
                                      LocalDateTime updateTime);

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
     * 优化：添加expire_time非空检查，限制查询数量，按过期时间排序
     * 修复：使用参数化查询解决时区问题
     *
     * @param currentTime 当前时间
     * @return 过期订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time IS NOT NULL AND expire_time < #{currentTime} AND deleted = 0 " +
            "ORDER BY expire_time ASC LIMIT 200")
    List<UserOrder> selectExpiredOrders(@Param("currentTime") LocalDateTime currentTime);



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
     * 优化：确保正确识别过期订单，添加更详细的条件
     * 修复：使用参数化查询解决时区问题
     *
     * @param currentTime 当前时间
     * @param syncThresholdTime 同步阈值时间
     * @return 订单列表
     */
    @Select("(" +
            "SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time IS NOT NULL AND expire_time < #{currentTime} AND deleted = 0 " +
            "ORDER BY expire_time ASC LIMIT 100" +
            ") UNION ALL (" +
            "SELECT * FROM t_user_order WHERE payment_status = 0 AND expire_time IS NOT NULL AND expire_time > #{currentTime} " +
            "AND update_time < #{syncThresholdTime} AND deleted = 0 " +
            "ORDER BY update_time ASC LIMIT 50" +
            ") ORDER BY expire_time ASC, update_time ASC")
    List<UserOrder> selectOrdersForBatchProcessing(@Param("currentTime") LocalDateTime currentTime,
                                                   @Param("syncThresholdTime") LocalDateTime syncThresholdTime);

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
     * 查询用户在指定时间窗口内的支付次数
     * 用于支付频率限制的数据库回退策略
     *
     * @param userId 用户ID
     * @param startTime 开始时间
     * @return 支付次数
     */
    @Select("SELECT COUNT(*) FROM t_user_order WHERE user_id = #{userId} AND create_time >= #{startTime} AND deleted = 0")
    Integer countRecentPayments(@Param("userId") Long userId, @Param("startTime") String startTime);

    /**
     * 获取用户总支付金额
     *
     * @param userId 用户ID
     * @return 总支付金额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_user_order WHERE user_id = #{userId} AND payment_status = 1 AND deleted = 0")
    BigDecimal getTotalPaidAmountByUserId(Long userId);

    /**
     * 获取用户在指定时间范围内的待支付订单
     *
     * @param userId 用户ID
     * @param packageId 套餐ID
     * @param timeThreshold 时间阈值
     * @return 待支付订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE user_id = #{userId} AND package_id = #{packageId} " +
            "AND payment_status = 0 AND create_time >= #{timeThreshold} AND deleted = 0 " +
            "ORDER BY create_time DESC")
    List<UserOrder> selectRecentPendingOrders(@Param("userId") Long userId,
                                              @Param("packageId") Long packageId,
                                              @Param("timeThreshold") LocalDateTime timeThreshold);

    /**
     * 获取指定时间范围内的待支付订单（用于状态同步）
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 待支付订单列表
     */
    @Select("SELECT * FROM t_user_order WHERE payment_status = 0 " +
            "AND create_time BETWEEN #{endTime} AND #{startTime} " +
            "AND deleted = 0 ORDER BY create_time ASC LIMIT 100")
    List<UserOrder> selectPendingOrdersInTimeRange(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);
}
