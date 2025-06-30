package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.dao.PaymentAuditDao;
import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付审计服务实现类
 *
 * @author instser
 * @since 2025-06-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAuditServiceImpl implements PaymentAuditService {

    private final PaymentAuditDao paymentAuditDao;
    private final ObjectMapper objectMapper;

    @Override
    public void recordAudit(String orderNo, String operationType, Integer oldStatus, Integer newStatus,
                           Long operatorId, String operatorType, String reason, String details,
                           String clientIp, String userAgent) {
        try {
            PaymentAudit audit = new PaymentAudit();
            audit.setOrderNo(orderNo);
            audit.setOperationType(operationType);
            audit.setOldStatus(oldStatus);
            audit.setNewStatus(newStatus);
            audit.setOperatorId(operatorId);
            audit.setOperatorType(operatorType);
            audit.setOperationReason(reason);
            audit.setOperationDetails(details);
            audit.setClientIp(clientIp);
            audit.setUserAgent(userAgent);

            paymentAuditDao.insert(audit);
            
            log.debug("支付审计日志记录成功，订单号: {}, 操作类型: {}, 操作人: {}", 
                    orderNo, operationType, operatorId);
                    
        } catch (Exception e) {
            log.error("记录支付审计日志失败，订单号: {}, 操作类型: {}", orderNo, operationType, e);
        }
    }

    @Override
    public void recordCreateAudit(String orderNo, Long operatorId, String details, String clientIp, String userAgent) {
        recordAudit(orderNo, OperationType.CREATE, null, 0, 
                   operatorId, OperatorType.USER, "创建支付订单", details, clientIp, userAgent);
    }

    @Override
    public void recordStatusChangeAudit(String orderNo, Integer oldStatus, Integer newStatus, String reason, String details) {
        String operationType;
        String operatorType = OperatorType.SYSTEM;
        
        // 根据状态变更确定操作类型
        if (newStatus == 1) {
            operationType = OperationType.PAY;
            operatorType = OperatorType.CALLBACK;
        } else if (newStatus == 2) {
            operationType = OperationType.CANCEL;
        } else if (newStatus == 3) {
            operationType = OperationType.REFUND;
        } else {
            operationType = OperationType.SYNC;
        }
        
        recordAudit(orderNo, operationType, oldStatus, newStatus, 
                   null, operatorType, reason, details, null, null);
    }

    @Override
    public List<PaymentAudit> getAuditByOrderNo(String orderNo) {
        try {
            return paymentAuditDao.selectByOrderNo(orderNo);
        } catch (Exception e) {
            log.error("查询订单审计日志失败，订单号: {}", orderNo, e);
            return List.of();
        }
    }

    @Override
    public List<PaymentAudit> getAuditByOperationType(String operationType, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            return paymentAuditDao.selectByOperationType(operationType, startTime, endTime);
        } catch (Exception e) {
            log.error("查询操作类型审计日志失败，操作类型: {}", operationType, e);
            return List.of();
        }
    }

    @Override
    public List<PaymentAudit> getAuditByOperator(Long operatorId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            return paymentAuditDao.selectByOperator(operatorId, startTime, endTime);
        } catch (Exception e) {
            log.error("查询操作人审计日志失败，操作人ID: {}", operatorId, e);
            return List.of();
        }
    }

    /**
     * 将对象转换为JSON字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("对象转JSON失败: {}", obj, e);
            return obj.toString();
        }
    }

    /**
     * 创建操作详情Map
     */
    public String createOperationDetails(String key, Object value) {
        Map<String, Object> details = new HashMap<>();
        details.put(key, value);
        details.put("timestamp", LocalDateTime.now());
        return toJsonString(details);
    }

    /**
     * 创建操作详情Map（多个键值对）
     */
    public String createOperationDetails(Map<String, Object> data) {
        Map<String, Object> details = new HashMap<>(data);
        details.put("timestamp", LocalDateTime.now());
        return toJsonString(details);
    }
}
