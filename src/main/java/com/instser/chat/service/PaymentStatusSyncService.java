package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;

/**
 * 支付状态同步服务接口
 * 
 * @author instser
 * @since 2025-07-29
 */
public interface PaymentStatusSyncService {

    /**
     * 同步单个订单的支付状态
     * 
     * @param orderNo 订单号
     * @return 是否同步成功
     */
    boolean syncOrderStatus(String orderNo);

    /**
     * 定时同步待支付订单状态
     * 检查5-30分钟前创建的待支付订单
     */
    void syncPendingOrdersStatus();

    /**
     * 查询易支付订单状态
     * 
     * @param orderNo 订单号
     * @return 订单状态信息
     */
    PaymentQueryResult queryYiPayOrderStatus(String orderNo);

    /**
     * 支付查询结果
     */
    class PaymentQueryResult {
        private boolean success;
        private String status;
        private String tradeNo;
        private String message;

        public PaymentQueryResult(boolean success, String status, String tradeNo, String message) {
            this.success = success;
            this.status = status;
            this.tradeNo = tradeNo;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStatus() {
            return status;
        }

        public String getTradeNo() {
            return tradeNo;
        }

        public String getMessage() {
            return message;
        }

        public boolean isPaid() {
            return success && "TRADE_SUCCESS".equals(status);
        }
    }
}
