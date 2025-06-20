package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;

import java.util.List;

/**
 * 支付状态同步服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface PaymentSyncService {

    /**
     * 同步单个订单的支付状态
     *
     * @param orderNo 订单号
     * @return 是否同步成功
     */
    boolean syncOrderStatus(String orderNo);

    /**
     * 批量同步待支付订单状态
     *
     * @param maxCount 最大同步数量
     * @return 同步成功的订单数量
     */
    int syncPendingOrders(int maxCount);

    /**
     * 获取需要同步的待支付订单列表
     *
     * @param maxCount 最大数量
     * @return 订单列表
     */
    List<UserOrder> getPendingOrdersForSync(int maxCount);

    /**
     * 定时同步任务
     */
    void scheduledSync();

    /**
     * 手动触发全量同步
     *
     * @return 同步结果统计
     */
    SyncResult manualSyncAll();

    /**
     * 同步结果统计类
     */
    class SyncResult {
        private int totalCount;
        private int successCount;
        private int failedCount;
        private int skippedCount;

        public SyncResult() {}

        public SyncResult(int totalCount, int successCount, int failedCount, int skippedCount) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
        }

        // Getters and setters
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }

        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

        public int getSkippedCount() { return skippedCount; }
        public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

        @Override
        public String toString() {
            return String.format("SyncResult{total=%d, success=%d, failed=%d, skipped=%d}",
                    totalCount, successCount, failedCount, skippedCount);
        }
    }
}
