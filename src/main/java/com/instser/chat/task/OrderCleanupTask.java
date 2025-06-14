package com.instser.chat.task;

import com.instser.chat.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单清理定时任务
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupTask {

    private final OrderService orderService;

    /**
     * 清理过期订单
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000) // 10分钟 = 600000毫秒
    public void cleanupExpiredOrders() {
        try {
            log.info("开始执行过期订单清理任务");
            int cleanedCount = orderService.cleanupExpiredOrders();
            if (cleanedCount > 0) {
                log.info("过期订单清理完成，共处理 {} 个订单", cleanedCount);
            }
        } catch (Exception e) {
            log.error("过期订单清理任务执行失败", e);
        }
    }

    /**
     * 订单状态同步检查
     * 每30分钟执行一次
     */
    @Scheduled(fixedRate = 1800000) // 30分钟 = 1800000毫秒
    public void syncOrderStatus() {
        try {
            log.info("开始执行订单状态同步检查");
            int syncedCount = orderService.syncPendingOrderStatus();
            if (syncedCount > 0) {
                log.info("订单状态同步完成，共同步 {} 个订单", syncedCount);
            }
        } catch (Exception e) {
            log.error("订单状态同步任务执行失败", e);
        }
    }
}
