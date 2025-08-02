package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.dao.UserOrderDao;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.ExchangeRateService;
import com.instser.chat.service.OrderService;

import com.instser.chat.service.PackageService;
import com.instser.chat.service.PaymentStatusSyncService;
import com.instser.chat.service.RedisDistributedLockService;
import com.instser.chat.service.UserPaymentStatusService;
import com.instser.chat.service.cache.GlobalCacheManager;
import com.instser.chat.util.MoneyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<UserOrderDao, UserOrder> implements OrderService {

    private final PackageService packageService;
    private final RedisDistributedLockService distributedLockService;
    private final UserPaymentStatusService userPaymentStatusService;
    private final ExchangeRateService exchangeRateService;
    private final PaymentStatusSyncService paymentStatusSyncService;
    private final GlobalCacheManager globalCacheManager;

    public OrderServiceImpl(PackageService packageService,
                           RedisDistributedLockService distributedLockService,
                           UserPaymentStatusService userPaymentStatusService,
                           ExchangeRateService exchangeRateService,
                           @Lazy PaymentStatusSyncService paymentStatusSyncService,
                           GlobalCacheManager globalCacheManager) {
        this.packageService = packageService;
        this.distributedLockService = distributedLockService;
        this.userPaymentStatusService = userPaymentStatusService;
        this.exchangeRateService = exchangeRateService;
        this.paymentStatusSyncService = paymentStatusSyncService;
        this.globalCacheManager = globalCacheManager;
    }

    @Override
    public UserOrder createOrder(Long userId, Long packageId, String currency, String paymentMethod) {
        // 获取套餐信息
        Package packageInfo = packageService.getPackageById(packageId);
        if (packageInfo == null) {
            throw new BusinessException("套餐不存在");
        }

        if (packageInfo.getIsEnabled() != 1) {
            throw new BusinessException("套餐已下架");
        }

        // 【新增】检查是否有相同套餐的近期待支付订单
        List<UserOrder> recentOrders = getRecentPendingOrders(userId, packageId, 30);
        if (!recentOrders.isEmpty()) {
            log.info("发现用户{}有{}个相同套餐的近期待支付订单，返回最新的待支付订单", userId, recentOrders.size());

            // 获取最新的待支付订单
            UserOrder latestOrder = recentOrders.get(0); // 查询结果已按创建时间倒序排列

            // 主动同步支付状态，确保返回的订单状态是最新的
            try {
                log.debug("同步订单{}的支付状态", latestOrder.getOrderNo());
                paymentStatusSyncService.syncOrderStatus(latestOrder.getOrderNo());

                // 重新获取订单信息，确保状态是最新的
                UserOrder updatedOrder = getOrderByOrderNo(latestOrder.getOrderNo());
                if (updatedOrder != null) {
                    latestOrder = updatedOrder;
                }
            } catch (Exception e) {
                log.warn("同步订单{}状态异常，使用当前状态: {}", latestOrder.getOrderNo(), e.getMessage());
            }

            log.info("返回用户{}的现有订单: {}，状态: {}", userId, latestOrder.getOrderNo(), latestOrder.getPaymentStatus());
            return latestOrder;
        }

        // 选择价格：检查用户是否为新用户且套餐有优惠价格
        BigDecimal amount = selectPrice(userId, packageInfo);

        if (!MoneyUtil.isZeroOrPositive(amount)) {
            throw new BusinessException("套餐价格配置错误");
        }

        // 汇率转换：如果是易支付方式或前端请求CNY货币，则转换为人民币
        if (MoneyUtil.needsCurrencyConversion(paymentMethod, currency)) {
            amount = convertPriceWithExchangeRate(amount, "USD", "CNY", paymentMethod);
        }

        // 格式化金额精度
        amount = MoneyUtil.formatAmount(amount, currency);

        // 【保留原有逻辑】检查并取消用户的进行中订单
        int cancelledCount = cancelUserPendingOrders(userId);
        if (cancelledCount > 0) {
            log.info("用户{}发起新订单，已取消{}个进行中的订单", userId, cancelledCount);
        }

        // 创建订单
        UserOrder order = new UserOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setPackageId(packageId);
        order.setAmount(amount);
        order.setCurrency(currency.toUpperCase());
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(0); // 未支付
        order.setPaymentRequestId(generatePaymentRequestId());
        order.setExpireTime(LocalDateTime.now().plusMinutes(30)); // 30分钟后过期
        order.setRemark("套餐购买: " + packageInfo.getPackageName());

        // 保存订单
        if (!this.save(order)) {
            throw new BusinessException("订单创建失败");
        }

        // 清除近期未支付订单缓存
        clearRecentPendingOrdersCache(userId, packageId);

        log.info("创建订单成功，订单号: {}, 用户ID: {}, 套餐ID: {}, 金额: {} {}",
                order.getOrderNo(), userId, packageId, amount, currency);

        return order;
    }

    /**
     * 选择订单价格
     * 如果用户从未付费且套餐有优惠价格，则使用优惠价格；否则使用原价
     *
     * @param userId 用户ID
     * @param packageInfo 套餐信息
     * @return 选择的价格
     */
    private BigDecimal selectPrice(Long userId, Package packageInfo) {
        try {
            // 检查用户是否从未付费
            boolean isNewUser = userPaymentStatusService.hasNeverPaid(userId);

            // 检查套餐是否有优惠价格
            BigDecimal discountPrice = packageInfo.getDiscountPrice();
            boolean hasDiscountPrice = discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0;

            if (isNewUser && hasDiscountPrice) {
                log.info("用户{}为新用户且套餐{}有优惠价格，使用优惠价格: {} USD",
                        userId, packageInfo.getId(), discountPrice);
                return discountPrice;
            } else {
                log.debug("用户{}使用套餐{}原价: {} USD (新用户: {}, 有优惠价格: {})",
                        userId, packageInfo.getId(), packageInfo.getPrice(), isNewUser, hasDiscountPrice);
                return packageInfo.getPrice();
            }
        } catch (Exception e) {
            log.error("选择价格时发生异常，用户ID: {}, 套餐ID: {}, 使用原价",
                    userId, packageInfo.getId(), e);
            return packageInfo.getPrice();
        }
    }

    /**
     * 汇率转换价格
     *
     * @param amount 原始金额
     * @param fromCurrency 源货币
     * @param toCurrency 目标货币
     * @param paymentMethod 支付方式（用于日志）
     * @return 转换后的金额
     */
    private BigDecimal convertPriceWithExchangeRate(BigDecimal amount, String fromCurrency, String toCurrency, String paymentMethod) {
        try {
            BigDecimal convertedAmount = exchangeRateService.convertPrice(amount, fromCurrency, toCurrency);
            log.info("支付方式{}汇率转换: {} {} -> {} {} (汇率: {})",
                    paymentMethod, amount, fromCurrency, convertedAmount, toCurrency,
                    exchangeRateService.getExchangeRate(fromCurrency, toCurrency));
            return convertedAmount;
        } catch (Exception e) {
            log.error("汇率转换失败，支付方式: {}, 金额: {} {}, 使用原始金额",
                    paymentMethod, amount, fromCurrency, e);
            return amount;
        }
    }

    @Override
    public UserOrder getOrderByOrderNo(String orderNo) {
        return baseMapper.selectByOrderNo(orderNo);
    }

    @Override
    public UserOrder getOrderByPaymentRequestId(String paymentRequestId) {
        return baseMapper.selectByPaymentRequestId(paymentRequestId);
    }

    @Override
    public List<UserOrder> getOrdersByUserId(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public List<UserOrder> getPendingOrdersByUserId(Long userId) {
        return baseMapper.selectPendingOrdersByUserId(userId);
    }

    @Override
    public boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId) {
        // 默认行为：只有支付成功时才设置支付时间
        return updatePaymentStatus(orderNo, paymentStatus, transactionId, paymentStatus == 1);
    }

    @Override
    public boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId, boolean setPaymentTime) {
        // 使用分布式锁保护订单状态更新
        return distributedLockService.executeOrderLock(orderNo, () -> {
            try {
                UserOrder order = getOrderByOrderNo(orderNo);
                if (order == null) {
                    log.warn("更新订单状态失败，未找到订单: {}", orderNo);
                    return false;
                }

                LocalDateTime now = LocalDateTime.now();
                int result;

                if (setPaymentTime && paymentStatus == 1) {
                    // 支付成功时设置支付时间
                    result = baseMapper.updatePaymentStatus(orderNo, paymentStatus, transactionId, now, now);
                    log.info("更新订单支付状态成功（含支付时间），订单号: {}, 状态: {}, 交易号: {}", orderNo, paymentStatus, transactionId);
                } else {
                    // 其他状态变更不设置支付时间
                    result = baseMapper.updatePaymentStatusWithoutTime(orderNo, paymentStatus, transactionId, now);
                    log.info("更新订单支付状态成功（不含支付时间），订单号: {}, 状态: {}, 交易号: {}", orderNo, paymentStatus, transactionId);
                }

                if (result > 0) {
                    // 清除缓存
                    clearRecentPendingOrdersCache(order.getUserId(), order.getPackageId());
                    return true;
                } else {
                    log.warn("更新订单支付状态失败，订单号: {}, 状态: {}, 交易号: {}", orderNo, paymentStatus, transactionId);
                    return false;
                }
            } catch (Exception e) {
                log.error("更新订单支付状态异常，订单号: {}, 状态: {}, 交易号: {}", orderNo, paymentStatus, transactionId, e);
                return false;
            }
        });
    }

    @Override
    public boolean cancelOrder(String orderNo) {
        UserOrder order = getOrderByOrderNo(orderNo);
        if (order == null) {
            return false;
        }

        if (order.getPaymentStatus() == 1) {
            throw new BusinessException("已支付的订单不能取消");
        }

        order.setPaymentStatus(2); // 已取消
        boolean success = this.updateById(order);
        if (success) {
            clearRecentPendingOrdersCache(order.getUserId(), order.getPackageId());
        }
        return success;
    }

    @Override
    public boolean isOrderExpired(String orderNo) {
        UserOrder order = getOrderByOrderNo(orderNo);
        if (order == null) {
            return true;
        }

        return order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime());
    }

    @Override
    public String generateOrderNo() {
        // 格式: JY + 年月日 + 时分秒 + 4位随机数
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "JY" + timestamp + random;
    }

    @Override
    public int cleanupExpiredOrders() {
        try {
            // 1. 获取所有过期的订单
            List<UserOrder> expiredOrders = getExpiredOrders();
            if (expiredOrders.isEmpty()) {
                log.info("没有需要清理的过期订单");
                return 0;
            }

            log.info("发现 {} 个需要清理的过期订单", expiredOrders.size());

            // 2. 清除相关缓存
            for (UserOrder order : expiredOrders) {
                clearRecentPendingOrdersCache(order.getUserId(), order.getPackageId());
            }
            log.info("已清除 {} 个过期订单的相关缓存", expiredOrders.size());

            // 3. 批量更新数据库
            int cleanedCount = baseMapper.cleanupExpiredOrders();
            log.info("成功清理了 {} 个过期订单", cleanedCount);

            return cleanedCount;
        } catch (Exception e) {
            log.error("清理过期订单时发生异常", e);
            return 0;
        }
    }

    @Override
    public int syncPendingOrderStatus() {
        return baseMapper.syncPendingOrderStatus();
    }

    @Override
    public List<UserOrder> getPendingOrdersAfterTime(LocalDateTime afterTime, int maxCount) {
        try {
            log.debug("获取指定时间之后的待支付订单，时间: {}, 最大数量: {}", afterTime, maxCount);
            return baseMapper.selectPendingOrdersAfterTime(afterTime, maxCount);
        } catch (Exception e) {
            log.error("获取待支付订单异常", e);
            return List.of();
        }
    }

    @Override
    public List<UserOrder> getPendingOrders(int maxCount) {
        try {
            log.debug("获取待支付订单，最大数量: {}", maxCount);
            return baseMapper.selectPendingOrders(maxCount);
        } catch (Exception e) {
            log.error("获取待支付订单异常", e);
            return List.of();
        }
    }

    @Override
    public List<UserOrder> getOrdersForBatchProcessing() {
        try {
            log.debug("获取需要批量处理的订单");
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime syncThresholdTime = currentTime.minusMinutes(30); // 30分钟前
            return baseMapper.selectOrdersForBatchProcessing(currentTime, syncThresholdTime);
        } catch (Exception e) {
            log.error("获取批量处理订单异常", e);
            return List.of();
        }
    }

    @Override
    public List<UserOrder> getExpiredOrders() {
        try {
            log.debug("获取过期订单");

            // 执行过期订单查询（使用当前时间解决时区问题）
            LocalDateTime queryTime = LocalDateTime.now();
            List<UserOrder> expiredOrders = baseMapper.selectExpiredOrders(queryTime);
            log.debug("查询到过期订单数量: {}", expiredOrders.size());

            return expiredOrders;
        } catch (Exception e) {
            log.error("获取过期订单异常", e);
            return List.of();
        }
    }

    @Override
    public List<UserOrder> getOrdersNeedingStatusSync(int maxCount) {
        try {
            log.debug("获取需要状态同步的订单，最大数量: {}", maxCount);
            return baseMapper.selectOrdersNeedingStatusSync(maxCount);
        } catch (Exception e) {
            log.error("获取需要状态同步的订单异常", e);
            return List.of();
        }
    }

    @Override
    public int cancelUserPendingOrders(Long userId) {
        try {
            int cancelledCount = baseMapper.cancelUserPendingOrders(userId);
            if (cancelledCount > 0) {
                log.info("取消用户{}的{}个待支付订单", userId, cancelledCount);
            }
            return cancelledCount;
        } catch (Exception e) {
            log.error("取消用户{}的待支付订单异常", userId, e);
            return 0;
        }
    }

    @Override
    public List<UserOrder> getRecentPendingOrders(Long userId, Long packageId, int minutesAgo) {
        String cacheKey = "order:recent_pending:" + userId + ":" + packageId;
        return globalCacheManager.get("order:recent_pending", cacheKey, () -> {
            try {
                LocalDateTime timeThreshold = LocalDateTime.now().minusMinutes(minutesAgo);
                log.debug("数据库查询：获取用户{}套餐{}在{}分钟内的待支付订单", userId, packageId, minutesAgo);
                return baseMapper.selectRecentPendingOrders(userId, packageId, timeThreshold);
            } catch (Exception e) {
                log.error("数据库查询：获取用户{}套餐{}的近期待支付订单异常", userId, packageId, e);
                return List.of();
            }
        });
    }

    @Override
    public List<UserOrder> getPendingOrdersInTimeRange(int startMinutesAgo, int endMinutesAgo) {
        try {
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(startMinutesAgo);
            LocalDateTime endTime = LocalDateTime.now().minusMinutes(endMinutesAgo);
            log.debug("获取{}到{}分钟前的待支付订单", startMinutesAgo, endMinutesAgo);
            return baseMapper.selectPendingOrdersInTimeRange(startTime, endTime);
        } catch (Exception e) {
            log.error("获取时间范围内的待支付订单异常", e);
            return List.of();
        }
    }

    /**
     * 生成支付请求ID
     */
    private String generatePaymentRequestId() {
        // 格式: PAY + 年月日时分秒毫秒 + 3位随机数
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int random = ThreadLocalRandom.current().nextInt(100, 999);
        return "PAY" + timestamp + random;
    }
    private void clearRecentPendingOrdersCache(Long userId, Long packageId) {
        String cacheKey = "order:recent_pending:" + userId + ":" + packageId;
        globalCacheManager.evict("order:recent_pending", cacheKey);
        log.debug("已清除近期未支付订单缓存，key: {}", cacheKey);
    }
}
