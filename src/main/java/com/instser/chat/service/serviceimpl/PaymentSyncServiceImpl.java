package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PaymentSyncService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.strategy.PaymentStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 支付状态同步服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSyncServiceImpl implements PaymentSyncService {

    private final OrderService orderService;
    private final PaymentStrategyFactory paymentStrategyFactory;
    
    // 用于异步执行同步任务的线程池
    private final ExecutorService syncExecutor = Executors.newFixedThreadPool(5);

    @Override
    public boolean syncOrderStatus(String orderNo) {
        int maxRetries = 2;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("开始同步订单状态，订单号: {}, 尝试 {}/{}", orderNo, attempt, maxRetries);

                // 查找订单
                UserOrder order = orderService.getOrderByOrderNo(orderNo);
                if (order == null) {
                    log.warn("订单不存在，跳过同步，订单号: {}", orderNo);
                    return false;
                }

                // 检查订单是否需要同步
                if (!needsSync(order)) {
                    log.debug("订单无需同步，订单号: {}, 状态: {}", orderNo, order.getPaymentStatus());
                    return true; // 返回true表示不需要同步，但不是错误
                }

                // 获取支付策略
                PaymentStrategy strategy = paymentStrategyFactory.getStrategy(order.getPaymentMethod());
                if (strategy == null) {
                    log.warn("未找到支付策略，跳过同步，订单号: {}, 支付方式: {}", orderNo, order.getPaymentMethod());
                    return false;
                }

                // 查询最新状态
                String latestStatus = strategy.queryPaymentStatus(orderNo);

                if (latestStatus != null && !latestStatus.equals("ERROR")) {
                    log.debug("订单状态同步完成，订单号: {}, 最新状态: {}", orderNo, latestStatus);
                    return true;
                } else {
                    log.warn("订单状态查询失败，订单号: {}, 状态: {}, 尝试 {}/{}",
                            orderNo, latestStatus, attempt, maxRetries);

                    if (attempt < maxRetries) {
                        // 等待后重试
                        try {
                            Thread.sleep(2000); // 2秒
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("订单状态同步重试被中断，订单号: {}", orderNo);
                            return false;
                        }
                    }
                }

            } catch (Exception e) {
                log.error("同步订单状态异常，订单号: {}, 尝试 {}/{}", orderNo, attempt, maxRetries, e);

                if (attempt < maxRetries) {
                    // 等待后重试
                    try {
                        Thread.sleep(2000); // 2秒
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("订单状态同步重试被中断，订单号: {}", orderNo);
                        return false;
                    }
                }
            }
        }

        log.error("订单状态同步失败，已达到最大重试次数，订单号: {}", orderNo);
        return false;
    }

    @Override
    public int syncPendingOrders(int maxCount) {
        try {
            log.info("开始批量同步待支付订单，最大数量: {}", maxCount);

            // 获取需要同步的订单
            List<UserOrder> pendingOrders = getPendingOrdersForSync(maxCount);
            if (pendingOrders.isEmpty()) {
                log.debug("没有需要同步的订单");
                return 0;
            }

            log.info("找到 {} 个需要同步的订单", pendingOrders.size());

            int successCount = 0;
            for (UserOrder order : pendingOrders) {
                try {
                    if (syncOrderStatus(order.getOrderNo())) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("同步订单失败，订单号: {}", order.getOrderNo(), e);
                }
            }

            log.info("批量同步完成，总数: {}, 成功: {}", pendingOrders.size(), successCount);
            return successCount;

        } catch (Exception e) {
            log.error("批量同步订单异常", e);
            return 0;
        }
    }

    @Override
    public List<UserOrder> getPendingOrdersForSync(int maxCount) {
        try {
            // 获取创建时间在30分钟内的待支付订单
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
            return orderService.getPendingOrdersAfterTime(cutoffTime, maxCount);
        } catch (Exception e) {
            log.error("获取待同步订单列表异常", e);
            return List.of();
        }
    }

    @Override
    public void scheduledSync() {
        try {
            log.debug("开始定时同步任务");
            
            // 异步执行同步任务，避免阻塞定时器
            CompletableFuture.runAsync(() -> {
                try {
                    int syncCount = syncPendingOrders(50); // 每次最多同步50个订单
                    if (syncCount > 0) {
                        log.info("定时同步完成，同步订单数: {}", syncCount);
                    }
                } catch (Exception e) {
                    log.error("定时同步任务异常", e);
                }
            }, syncExecutor);

        } catch (Exception e) {
            log.error("启动定时同步任务异常", e);
        }
    }

    @Override
    public SyncResult manualSyncAll() {
        try {
            log.info("开始手动全量同步");

            SyncResult result = new SyncResult();
            int batchSize = 100;

            while (true) {
                List<UserOrder> orders = getPendingOrdersForSync(batchSize);
                if (orders.isEmpty()) {
                    break;
                }

                for (UserOrder order : orders) {
                    result.setTotalCount(result.getTotalCount() + 1);

                    try {
                        if (!needsSync(order)) {
                            result.setSkippedCount(result.getSkippedCount() + 1);
                            continue;
                        }

                        if (syncOrderStatus(order.getOrderNo())) {
                            result.setSuccessCount(result.getSuccessCount() + 1);
                        } else {
                            result.setFailedCount(result.getFailedCount() + 1);
                        }
                    } catch (Exception e) {
                        log.error("同步订单失败，订单号: {}", order.getOrderNo(), e);
                        result.setFailedCount(result.getFailedCount() + 1);
                    }
                }

                if (orders.size() < batchSize) {
                    break; // 已经是最后一批
                }
            }

            log.info("手动全量同步完成: {}", result);
            return result;

        } catch (Exception e) {
            log.error("手动全量同步异常", e);
            return new SyncResult(0, 0, 1, 0);
        }
    }

    /**
     * 判断订单是否需要同步
     */
    private boolean needsSync(UserOrder order) {
        if (order == null) {
            return false;
        }

        // 只同步待支付状态的订单
        if (order.getPaymentStatus() != 0) {
            return false;
        }

        // 检查订单是否过期
        if (orderService.isOrderExpired(order.getOrderNo())) {
            return false;
        }

        // 检查订单创建时间，只同步30分钟内的订单
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        return order.getCreateTime().isAfter(cutoffTime);
    }
}
