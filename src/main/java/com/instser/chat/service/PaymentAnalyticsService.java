package com.instser.chat.service;

import com.instser.chat.vo.PaymentAnalyticsVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付数据分析服务接口
 *
 * @author instser
 * @since 2025-06-29
 */
public interface PaymentAnalyticsService {

    /**
     * 获取支付概览数据
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 支付概览数据
     */
    PaymentAnalyticsVO getPaymentOverview(LocalDate startDate, LocalDate endDate);

    /**
     * 计算支付成功率
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 支付成功率（百分比）
     */
    BigDecimal calculateSuccessRate(LocalDate startDate, LocalDate endDate);

    /**
     * 获取支付方式统计
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 支付方式统计 Map<支付方式, 订单数量>
     */
    Map<String, Long> getPaymentMethodStats(LocalDate startDate, LocalDate endDate);

    /**
     * 获取每日收入统计
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 每日收入统计 Map<日期, 收入金额>
     */
    Map<LocalDate, BigDecimal> getDailyRevenueStats(LocalDate startDate, LocalDate endDate);

    /**
     * 获取套餐销售统计
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 套餐销售统计 Map<套餐ID, 销售数量>
     */
    Map<Long, Long> getPackageSalesStats(LocalDate startDate, LocalDate endDate);

    /**
     * 计算平均订单金额
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 平均订单金额
     */
    BigDecimal calculateAverageOrderAmount(LocalDate startDate, LocalDate endDate);

    /**
     * 获取用户支付行为分析
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 用户支付行为分析数据
     */
    Map<String, Object> getUserPaymentBehaviorAnalysis(LocalDate startDate, LocalDate endDate);

    /**
     * 获取支付时间分布统计
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 支付时间分布 Map<小时, 支付次数>
     */
    Map<Integer, Long> getPaymentTimeDistribution(LocalDate startDate, LocalDate endDate);

    /**
     * 获取退款统计
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 退款统计数据
     */
    Map<String, Object> getRefundStats(LocalDate startDate, LocalDate endDate);

    /**
     * 生成支付报表
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 支付报表数据
     */
    Map<String, Object> generatePaymentReport(LocalDate startDate, LocalDate endDate);
}

/**
 * 支付数据分析服务实现类
 */
