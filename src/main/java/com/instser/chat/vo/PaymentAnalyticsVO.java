package com.instser.chat.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 支付数据分析VO
 *
 * @author instser
 * @since 2025-06-29
 */
@Data
public class PaymentAnalyticsVO {

    /**
     * 统计开始日期
     */
    private LocalDate startDate;

    /**
     * 统计结束日期
     */
    private LocalDate endDate;

    /**
     * 总订单数
     */
    private Long totalOrders;

    /**
     * 成功支付订单数
     */
    private Long successfulOrders;

    /**
     * 取消订单数
     */
    private Long canceledOrders;

    /**
     * 退款订单数
     */
    private Long refundedOrders;

    /**
     * 支付成功率（百分比）
     */
    private BigDecimal successRate;

    /**
     * 总收入
     */
    private BigDecimal totalRevenue;

    /**
     * 平均订单金额
     */
    private BigDecimal averageOrderAmount;

    /**
     * 支付方式统计
     * Map<支付方式, 订单数量>
     */
    private Map<String, Long> paymentMethodStats;

    /**
     * 每日收入统计
     * Map<日期, 收入金额>
     */
    private Map<LocalDate, BigDecimal> dailyRevenueStats;

    /**
     * 套餐销售统计
     * Map<套餐ID, 销售数量>
     */
    private Map<Long, Long> packageSalesStats;

    /**
     * 支付时间分布
     * Map<小时, 支付次数>
     */
    private Map<Integer, Long> paymentTimeDistribution;

    /**
     * 用户支付行为分析
     */
    private Map<String, Object> userBehaviorAnalysis;

    /**
     * 退款统计
     */
    private Map<String, Object> refundStats;
}
