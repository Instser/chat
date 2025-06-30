package com.instser.chat.service;

import com.instser.chat.entity.PaymentAudit;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付审计服务接口
 *
 * @author instser
 * @since 2025-06-29
 */
public interface PaymentAuditService {

    /**
     * 记录支付操作审计日志
     *
     * @param orderNo 订单号
     * @param operationType 操作类型
     * @param oldStatus 原状态
     * @param newStatus 新状态
     * @param operatorId 操作人ID
     * @param operatorType 操作人类型
     * @param reason 操作原因
     * @param details 操作详情
     * @param clientIp 客户端IP
     * @param userAgent 用户代理
     */
    void recordAudit(String orderNo, String operationType, Integer oldStatus, Integer newStatus,
                    Long operatorId, String operatorType, String reason, String details,
                    String clientIp, String userAgent);

    /**
     * 记录支付创建审计日志
     *
     * @param orderNo 订单号
     * @param operatorId 操作人ID
     * @param details 操作详情
     * @param clientIp 客户端IP
     * @param userAgent 用户代理
     */
    void recordCreateAudit(String orderNo, Long operatorId, String details, String clientIp, String userAgent);

    /**
     * 记录支付状态变更审计日志
     *
     * @param orderNo 订单号
     * @param oldStatus 原状态
     * @param newStatus 新状态
     * @param reason 变更原因
     * @param details 操作详情
     */
    void recordStatusChangeAudit(String orderNo, Integer oldStatus, Integer newStatus, String reason, String details);

    /**
     * 根据订单号查询审计日志
     *
     * @param orderNo 订单号
     * @return 审计日志列表
     */
    List<PaymentAudit> getAuditByOrderNo(String orderNo);

    /**
     * 根据操作类型查询审计日志
     *
     * @param operationType 操作类型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 审计日志列表
     */
    List<PaymentAudit> getAuditByOperationType(String operationType, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据操作人查询审计日志
     *
     * @param operatorId 操作人ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 审计日志列表
     */
    List<PaymentAudit> getAuditByOperator(Long operatorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 操作类型常量
     */
    interface OperationType {
        String CREATE = "CREATE";           // 创建订单
        String PAY = "PAY";                // 支付
        String CANCEL = "CANCEL";          // 取消
        String REFUND = "REFUND";          // 退款
        String QUERY = "QUERY";            // 查询
        String SYNC = "SYNC";              // 同步状态
        String CALLBACK = "CALLBACK";      // 回调处理
    }

    /**
     * 操作人类型常量
     */
    interface OperatorType {
        String USER = "USER";              // 用户操作
        String SYSTEM = "SYSTEM";          // 系统操作
        String ADMIN = "ADMIN";            // 管理员操作
        String CALLBACK = "CALLBACK";      // 回调操作
    }
}
