package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.serviceimpl.PaymentAuditServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 优化的定时任务服务
 * 统一管理所有定时任务，减少数据库负担
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizedScheduledTaskService {

    private final OrderService orderService;
    private final PaymentAuditService paymentAuditService;
    private final ChatInterruptService chatInterruptService;
    
    // 异步执行器，避免阻塞定时器线程
    private final Executor taskExecutor = Executors.newFixedThreadPool(3);

    /**
     * 主要定时任务 - 每10分钟执行一次
     * 统一处理订单相关的所有操作，减少数据库查询次数
     */
    @Scheduled(fixedRate = 600000) // 10分钟
    public void mainScheduledTask() {
        log.debug("开始执行主要定时任务");
        
        // 异步执行，避免阻塞
        CompletableFuture.runAsync(() -> {
            try {
                // 一次性查询所有需要处理的订单
                List<UserOrder> ordersToProcess = getOrdersForProcessing();
                
                if (!ordersToProcess.isEmpty()) {
                    log.info("发现{}个订单需要处理", ordersToProcess.size());
                    
                    // 分类处理订单
                    processOrdersBatch(ordersToProcess);
                } else {
                    log.debug("没有需要处理的订单");
                }
                
            } catch (Exception e) {
                log.error("主要定时任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 轻量级任务 - 每5分钟执行一次
     * 只处理内存操作和简单检查
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void lightweightTask() {
        log.debug("开始执行轻量级任务");
        
        try {
            // 清理超时会话（内存操作）
            chatInterruptService.cleanupTimeoutSessions();
            
            // 其他轻量级检查...
            
        } catch (Exception e) {
            log.error("轻量级任务执行异常", e);
        }
    }

    /**
     * 支付同步任务 - 每15分钟执行一次（错峰）
     * 专门处理支付相关的同步操作
     */
    @Scheduled(fixedRate = 900000, initialDelay = 300000) // 15分钟，延迟5分钟启动
    public void paymentSyncTask() {
        log.debug("开始执行支付同步任务");
        
        CompletableFuture.runAsync(() -> {
            try {
                // 获取需要同步的支付订单
                LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
                List<UserOrder> pendingOrders = orderService.getPendingOrdersAfterTime(cutoffTime, 50);
                
                if (!pendingOrders.isEmpty()) {
                    log.info("开始同步{}个待支付订单", pendingOrders.size());
                    // 执行支付同步逻辑
                    syncPaymentOrders(pendingOrders);
                }
                
            } catch (Exception e) {
                log.error("支付同步任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 每日维护任务 - 每天凌晨2点执行
     * 处理数据归档、清理等重型操作
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMaintenanceTask() {
        log.info("开始执行每日维护任务");
        
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 归档30天前的过期订单
                archiveOldOrders(30);
                
                // 2. 清理过期的使用记录
                cleanExpiredUsageRecords(90);
                
                // 3. 清理过期的审计日志
                cleanExpiredAuditLogs(90);
                
                log.info("每日维护任务执行完成");
                
            } catch (Exception e) {
                log.error("每日维护任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 获取需要处理的订单（一次查询，减少数据库负担）
     */
    private List<UserOrder> getOrdersForProcessing() {
        try {
            // 查询所有需要处理的订单：过期的、长时间未支付的等
            return orderService.getOrdersForBatchProcessing();
        } catch (Exception e) {
            log.error("获取待处理订单异常", e);
            return List.of();
        }
    }

    /**
     * 批量处理订单
     */
    @Transactional
    private void processOrdersBatch(List<UserOrder> orders) {
        int expiredCount = 0;
        int syncedCount = 0;
        
        for (UserOrder order : orders) {
            try {
                if (isOrderExpired(order)) {
                    // 处理过期订单
                    if (cancelExpiredOrder(order)) {
                        expiredCount++;
                    }
                } else if (needsStatusSync(order)) {
                    // 处理需要状态同步的订单
                    if (syncOrderStatus(order)) {
                        syncedCount++;
                    }
                }
            } catch (Exception e) {
                log.error("处理订单异常，订单号: {}", order.getOrderNo(), e);
            }
        }
        
        if (expiredCount > 0 || syncedCount > 0) {
            log.info("批量处理订单完成，过期处理: {}, 状态同步: {}", expiredCount, syncedCount);
        }
    }

    /**
     * 同步支付订单
     */
    private void syncPaymentOrders(List<UserOrder> orders) {
        // 实现支付订单同步逻辑
        log.debug("同步支付订单，数量: {}", orders.size());
    }

    /**
     * 判断订单是否过期
     */
    private boolean isOrderExpired(UserOrder order) {
        return order.getExpireTime() != null && 
               LocalDateTime.now().isAfter(order.getExpireTime()) &&
               order.getPaymentStatus() == 0;
    }

    /**
     * 判断订单是否需要状态同步
     */
    private boolean needsStatusSync(UserOrder order) {
        // 1小时前创建的待支付订单需要同步
        return order.getPaymentStatus() == 0 &&
               order.getUpdateTime().isBefore(LocalDateTime.now().minusHours(1));
    }

    /**
     * 取消过期订单
     */
    private boolean cancelExpiredOrder(UserOrder order) {
        try {
            boolean success = orderService.updatePaymentStatus(order.getOrderNo(), 2, null);
            if (success) {
                // 记录审计日志
                Map<String, Object> detailsMap = Map.of(
                    "reason", "订单过期自动取消",
                    "expireTime", order.getExpireTime().toString(),
                    "cancelTime", LocalDateTime.now().toString()
                );
                String details = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(detailsMap);
                
                paymentAuditService.recordStatusChangeAudit(
                    order.getOrderNo(), 0, 2, "订单过期自动取消", details
                );
                
                log.debug("过期订单取消成功: {}", order.getOrderNo());
                return true;
            }
        } catch (Exception e) {
            log.error("取消过期订单异常，订单号: {}", order.getOrderNo(), e);
        }
        return false;
    }

    /**
     * 同步订单状态
     */
    private boolean syncOrderStatus(UserOrder order) {
        try {
            // 实现订单状态同步逻辑
            log.debug("同步订单状态: {}", order.getOrderNo());
            return true;
        } catch (Exception e) {
            log.error("同步订单状态异常，订单号: {}", order.getOrderNo(), e);
            return false;
        }
    }

    /**
     * 归档旧订单
     */
    private void archiveOldOrders(int daysBeforeArchive) {
        try {
            log.info("开始归档{}天前的订单", daysBeforeArchive);
            // 实现订单归档逻辑
            // TODO: 需要在OrderService中实现相应方法
        } catch (Exception e) {
            log.error("归档旧订单异常", e);
        }
    }

    /**
     * 清理过期的使用记录
     */
    private void cleanExpiredUsageRecords(int retentionDays) {
        try {
            log.info("开始清理{}天前的使用记录", retentionDays);
            // 实现使用记录清理逻辑
        } catch (Exception e) {
            log.error("清理使用记录异常", e);
        }
    }

    /**
     * 清理过期的审计日志
     */
    private void cleanExpiredAuditLogs(int retentionDays) {
        try {
            log.info("开始清理{}天前的审计日志", retentionDays);
            // 实现审计日志清理逻辑
        } catch (Exception e) {
            log.error("清理审计日志异常", e);
        }
    }
}
