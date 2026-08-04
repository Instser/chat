package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.instser.chat.dao.UserOrderDao;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.PaymentAnalyticsService;
import com.instser.chat.vo.PaymentAnalyticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 支付数据分析服务实现：全部基于 t_user_order 真实查询，禁止硬编码假数据。
 *
 * <p>支付状态：0 未支付 / 1 已支付 / 2 已取消 / 3 已退款</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAnalyticsServiceImpl implements PaymentAnalyticsService {

    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELED = 2;
    private static final int STATUS_REFUNDED = 3;

    private final UserOrderDao userOrderDao;

    @Override
    public PaymentAnalyticsVO getPaymentOverview(LocalDate startDate, LocalDate endDate) {
        LocalDate start = Objects.requireNonNullElse(startDate, LocalDate.now().minusDays(7));
        LocalDate end = Objects.requireNonNullElse(endDate, LocalDate.now());
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        try {
            PaymentAnalyticsVO overview = new PaymentAnalyticsVO();
            overview.setStartDate(start);
            overview.setEndDate(end);

            overview.setTotalOrders(getTotalOrderCount(start, end));
            overview.setSuccessfulOrders(getSuccessfulOrderCount(start, end));
            overview.setCanceledOrders(getCanceledOrderCount(start, end));
            overview.setRefundedOrders(getRefundedOrderCount(start, end));
            overview.setSuccessRate(calculateSuccessRate(start, end));
            overview.setTotalRevenue(getTotalRevenue(start, end));
            overview.setAverageOrderAmount(calculateAverageOrderAmount(start, end));
            overview.setPaymentMethodStats(getPaymentMethodStats(start, end));
            overview.setDailyRevenueStats(getDailyRevenueStats(start, end));
            overview.setPackageSalesStats(getPackageSalesStats(start, end));
            overview.setPaymentTimeDistribution(getPaymentTimeDistribution(start, end));
            overview.setUserBehaviorAnalysis(getUserPaymentBehaviorAnalysis(start, end));
            overview.setRefundStats(getRefundStats(start, end));
            return overview;
        } catch (Exception e) {
            log.error("获取支付概览数据异常: {} ~ {}", start, end, e);
            PaymentAnalyticsVO empty = new PaymentAnalyticsVO();
            empty.setStartDate(start);
            empty.setEndDate(end);
            empty.setTotalOrders(0L);
            empty.setSuccessfulOrders(0L);
            empty.setCanceledOrders(0L);
            empty.setRefundedOrders(0L);
            empty.setSuccessRate(BigDecimal.ZERO);
            empty.setTotalRevenue(BigDecimal.ZERO);
            empty.setAverageOrderAmount(BigDecimal.ZERO);
            empty.setPaymentMethodStats(Map.of());
            empty.setDailyRevenueStats(Map.of());
            empty.setPackageSalesStats(Map.of());
            empty.setPaymentTimeDistribution(Map.of());
            empty.setUserBehaviorAnalysis(Map.of());
            empty.setRefundStats(Map.of());
            return empty;
        }
    }

    @Override
    public BigDecimal calculateSuccessRate(LocalDate startDate, LocalDate endDate) {
        Long totalOrders = getTotalOrderCount(startDate, endDate);
        if (totalOrders == null || totalOrders == 0) {
            return BigDecimal.ZERO;
        }
        Long successfulOrders = getSuccessfulOrderCount(startDate, endDate);
        return BigDecimal.valueOf(successfulOrders == null ? 0 : successfulOrders)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Long> getPaymentMethodStats(LocalDate startDate, LocalDate endDate) {
        List<UserOrder> paid = listPaidOrders(startDate, endDate);
        Map<String, Long> stats = new LinkedHashMap<>();
        for (UserOrder order : paid) {
            String method = order.getPaymentMethod();
            if (method == null || method.isBlank()) {
                method = "unknown";
            }
            stats.merge(method, 1L, Long::sum);
        }
        return stats;
    }

    @Override
    public Map<LocalDate, BigDecimal> getDailyRevenueStats(LocalDate startDate, LocalDate endDate) {
        LocalDate start = Objects.requireNonNullElse(startDate, LocalDate.now().minusDays(7));
        LocalDate end = Objects.requireNonNullElse(endDate, LocalDate.now());
        Map<LocalDate, BigDecimal> stats = new LinkedHashMap<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            stats.put(cursor, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            cursor = cursor.plusDays(1);
        }

        for (UserOrder order : listPaidOrders(start, end)) {
            LocalDate day = resolvePaidDay(order);
            if (day == null || day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
            stats.merge(day, amount, BigDecimal::add);
        }
        // 统一 scale
        stats.replaceAll((k, v) -> v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP));
        return stats;
    }

    @Override
    public Map<Long, Long> getPackageSalesStats(LocalDate startDate, LocalDate endDate) {
        Map<Long, Long> stats = new LinkedHashMap<>();
        for (UserOrder order : listPaidOrders(startDate, endDate)) {
            Long packageId = order.getPackageId();
            if (packageId == null) {
                continue;
            }
            stats.merge(packageId, 1L, Long::sum);
        }
        return stats;
    }

    @Override
    public BigDecimal calculateAverageOrderAmount(LocalDate startDate, LocalDate endDate) {
        BigDecimal totalRevenue = getTotalRevenue(startDate, endDate);
        Long successfulOrders = getSuccessfulOrderCount(startDate, endDate);
        if (successfulOrders == null || successfulOrders == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalRevenue.divide(BigDecimal.valueOf(successfulOrders), 2, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> getUserPaymentBehaviorAnalysis(LocalDate startDate, LocalDate endDate) {
        List<UserOrder> paid = listPaidOrders(startDate, endDate);
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("paidOrders", (long) paid.size());
        analysis.put("uniquePayingUsers", paid.stream()
                .map(UserOrder::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count());

        long withCreateAndPay = 0;
        double totalMinutes = 0;
        for (UserOrder order : paid) {
            if (order.getCreateTime() != null && order.getPaymentTime() != null
                    && !order.getPaymentTime().isBefore(order.getCreateTime())) {
                long seconds = java.time.Duration.between(order.getCreateTime(), order.getPaymentTime()).getSeconds();
                totalMinutes += seconds / 60.0;
                withCreateAndPay++;
            }
        }
        analysis.put("averagePaymentTimeMinutes",
                withCreateAndPay == 0 ? 0.0 : BigDecimal.valueOf(totalMinutes / withCreateAndPay)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue());

        Long createdInRange = getTotalOrderCount(startDate, endDate);
        long paidCount = paid.size();
        analysis.put("conversionRate",
                createdInRange == null || createdInRange == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(paidCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(createdInRange), 2, RoundingMode.HALF_UP));
        return analysis;
    }

    @Override
    public Map<Integer, Long> getPaymentTimeDistribution(LocalDate startDate, LocalDate endDate) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            distribution.put(hour, 0L);
        }
        for (UserOrder order : listPaidOrders(startDate, endDate)) {
            LocalDateTime payAt = order.getPaymentTime() != null ? order.getPaymentTime() : order.getCreateTime();
            if (payAt == null) {
                continue;
            }
            distribution.merge(payAt.getHour(), 1L, Long::sum);
        }
        return distribution;
    }

    @Override
    public Map<String, Object> getRefundStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new LinkedHashMap<>();
        Long refundedOrders = getRefundedOrderCount(startDate, endDate);
        BigDecimal refundAmount = getRefundAmount(startDate, endDate);
        stats.put("refundedOrders", refundedOrders);
        stats.put("refundAmount", refundAmount);
        stats.put("refundRate", calculateRefundRate(startDate, endDate));
        return stats;
    }

    @Override
    public Map<String, Object> generatePaymentReport(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = new LinkedHashMap<>();
        PaymentAnalyticsVO overview = getPaymentOverview(startDate, endDate);
        report.put("overview", overview);
        report.put("trends", analyzeTrends(overview));
        report.put("anomalies", detectAnomalies(overview));
        report.put("recommendations", generateRecommendations(overview));
        return report;
    }

    private Long getTotalOrderCount(LocalDate startDate, LocalDate endDate) {
        // 区间内创建的订单（含各状态）
        return userOrderDao.selectCount(baseCreateTimeWrapper(startDate, endDate));
    }

    private Long getSuccessfulOrderCount(LocalDate startDate, LocalDate endDate) {
        // 近 N 日支付成功：以支付时间优先，无支付时间则回退创建时间
        return (long) listPaidOrders(startDate, endDate).size();
    }

    private Long getCanceledOrderCount(LocalDate startDate, LocalDate endDate) {
        return userOrderDao.selectCount(baseCreateTimeWrapper(startDate, endDate)
                .eq(UserOrder::getPaymentStatus, STATUS_CANCELED));
    }

    private Long getRefundedOrderCount(LocalDate startDate, LocalDate endDate) {
        return userOrderDao.selectCount(baseCreateTimeWrapper(startDate, endDate)
                .eq(UserOrder::getPaymentStatus, STATUS_REFUNDED));
    }

    private BigDecimal getTotalRevenue(LocalDate startDate, LocalDate endDate) {
        return listPaidOrders(startDate, endDate).stream()
                .map(UserOrder::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getRefundAmount(LocalDate startDate, LocalDate endDate) {
        List<UserOrder> refunded = userOrderDao.selectList(baseCreateTimeWrapper(startDate, endDate)
                .eq(UserOrder::getPaymentStatus, STATUS_REFUNDED));
        return refunded.stream()
                .map(UserOrder::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRefundRate(LocalDate startDate, LocalDate endDate) {
        Long totalOrders = getTotalOrderCount(startDate, endDate);
        if (totalOrders == null || totalOrders == 0) {
            return BigDecimal.ZERO;
        }
        Long refundedOrders = getRefundedOrderCount(startDate, endDate);
        return BigDecimal.valueOf(refundedOrders == null ? 0 : refundedOrders)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> analyzeTrends(PaymentAnalyticsVO overview) {
        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("totalOrders", overview.getTotalOrders());
        trends.put("successfulOrders", overview.getSuccessfulOrders());
        trends.put("totalRevenue", overview.getTotalRevenue());
        trends.put("successRate", overview.getSuccessRate());
        return trends;
    }

    private Map<String, Object> detectAnomalies(PaymentAnalyticsVO overview) {
        Map<String, Object> anomalies = new LinkedHashMap<>();
        BigDecimal successRate = overview.getSuccessRate() == null ? BigDecimal.ZERO : overview.getSuccessRate();
        BigDecimal refundRate = BigDecimal.ZERO;
        if (overview.getRefundStats() != null && overview.getRefundStats().get("refundRate") instanceof BigDecimal br) {
            refundRate = br;
        }
        anomalies.put("lowSuccessRate", successRate.compareTo(BigDecimal.valueOf(50)) < 0);
        anomalies.put("highRefundRate", refundRate.compareTo(BigDecimal.valueOf(20)) > 0);
        return anomalies;
    }

    private Map<String, Object> generateRecommendations(PaymentAnalyticsVO overview) {
        Map<String, Object> recommendations = new LinkedHashMap<>();
        if (overview.getSuccessfulOrders() == null || overview.getSuccessfulOrders() == 0) {
            recommendations.put("hint", "近区间无成功支付，请核对支付通道与订单状态同步");
        } else {
            recommendations.put("hint", "数据来自 t_user_order 实表统计");
        }
        return recommendations;
    }

    /**
     * 支付成功订单：payment_status=1，且 COALESCE(payment_time, create_time) 落在 [start, end] 日闭区间。
     * 先按时间窗口取候选（支付时间或创建时间命中），再内存精确过滤。
     */
    private List<UserOrder> listPaidOrders(LocalDate startDate, LocalDate endDate) {
        LocalDate start = Objects.requireNonNullElse(startDate, LocalDate.now().minusDays(7));
        LocalDate end = Objects.requireNonNullElse(endDate, LocalDate.now());
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEndExclusive = end.plusDays(1).atStartOfDay();

        List<UserOrder> candidates = userOrderDao.selectList(new LambdaQueryWrapper<UserOrder>()
                .eq(UserOrder::getPaymentStatus, STATUS_PAID)
                .and(w -> w
                        .nested(p -> p.isNotNull(UserOrder::getPaymentTime)
                                .ge(UserOrder::getPaymentTime, rangeStart)
                                .lt(UserOrder::getPaymentTime, rangeEndExclusive))
                        .or(c -> c.ge(UserOrder::getCreateTime, rangeStart)
                                .lt(UserOrder::getCreateTime, rangeEndExclusive))
                ));

        return candidates.stream()
                .filter(o -> {
                    LocalDate day = resolvePaidDay(o);
                    return day != null && !day.isBefore(start) && !day.isAfter(end);
                })
                .collect(Collectors.toList());
    }

    private LocalDate resolvePaidDay(UserOrder order) {
        if (order.getPaymentTime() != null) {
            return order.getPaymentTime().toLocalDate();
        }
        if (order.getCreateTime() != null) {
            return order.getCreateTime().toLocalDate();
        }
        return null;
    }

    private LambdaQueryWrapper<UserOrder> baseCreateTimeWrapper(LocalDate startDate, LocalDate endDate) {
        LocalDate start = Objects.requireNonNullElse(startDate, LocalDate.now().minusDays(7));
        LocalDate end = Objects.requireNonNullElse(endDate, LocalDate.now());
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEndExclusive = end.plusDays(1).atStartOfDay();
        return new LambdaQueryWrapper<UserOrder>()
                .ge(UserOrder::getCreateTime, rangeStart)
                .lt(UserOrder::getCreateTime, rangeEndExclusive);
    }
}
