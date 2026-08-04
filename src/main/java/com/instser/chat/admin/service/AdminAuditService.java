package com.instser.chat.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.instser.chat.admin.common.PageResult;
import com.instser.chat.admin.query.AdminAuditQuery;
import com.instser.chat.dao.OAuth2AuditLogDao;
import com.instser.chat.dao.OperationAuditDao;
import com.instser.chat.dao.PaymentAuditDao;
import com.instser.chat.entity.OAuth2AuditLog;
import com.instser.chat.entity.OperationAudit;
import com.instser.chat.entity.PaymentAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final OperationAuditDao operationAuditDao;
    private final PaymentAuditDao paymentAuditDao;
    private final OAuth2AuditLogDao oAuth2AuditLogDao;

    public PageResult<OperationAudit> listOperations(AdminAuditQuery query) {
        Page<OperationAudit> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<OperationAudit> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOperationType())) {
            wrapper.eq(OperationAudit::getOperationType, query.getOperationType().trim());
        }
        if (query.getOperatorId() != null) {
            wrapper.eq(OperationAudit::getOperatorId, query.getOperatorId());
        }
        if (StringUtils.hasText(query.getResult())) {
            wrapper.eq(OperationAudit::getResult, query.getResult().trim());
        }
        if (StringUtils.hasText(query.getTarget())) {
            wrapper.like(OperationAudit::getTarget, query.getTarget().trim());
        }
        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.eq(OperationAudit::getTraceId, query.getTraceId().trim());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(OperationAudit::getCreatedTime, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(OperationAudit::getCreatedTime, query.getCreatedTo());
        }
        wrapper.orderByDesc(OperationAudit::getCreatedTime);
        return PageResult.of(operationAuditDao.selectPage(page, wrapper));
    }

    public OperationAudit getOperation(Long id) {
        OperationAudit audit = operationAuditDao.selectById(id);
        if (audit == null) {
            throw new IllegalArgumentException("审计记录不存在: " + id);
        }
        return audit;
    }

    public PageResult<PaymentAudit> listPayments(AdminAuditQuery query) {
        Page<PaymentAudit> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<PaymentAudit> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.eq(PaymentAudit::getOrderNo, query.getOrderNo().trim());
        }
        if (StringUtils.hasText(query.getOperationType())) {
            wrapper.eq(PaymentAudit::getOperationType, query.getOperationType().trim());
        }
        if (query.getOperatorId() != null) {
            wrapper.eq(PaymentAudit::getOperatorId, query.getOperatorId());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(PaymentAudit::getCreateTime, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(PaymentAudit::getCreateTime, query.getCreatedTo());
        }
        wrapper.orderByDesc(PaymentAudit::getCreateTime);
        return PageResult.of(paymentAuditDao.selectPage(page, wrapper));
    }

    public PageResult<OAuth2AuditLog> listOauth(AdminAuditQuery query) {
        Page<OAuth2AuditLog> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<OAuth2AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getProvider())) {
            wrapper.eq(OAuth2AuditLog::getProvider, query.getProvider().trim());
        }
        if (StringUtils.hasText(query.getAction())) {
            wrapper.eq(OAuth2AuditLog::getAction, query.getAction().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(OAuth2AuditLog::getStatus, query.getStatus().trim());
        }
        if (query.getOperatorId() != null) {
            wrapper.eq(OAuth2AuditLog::getUserId, query.getOperatorId());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(OAuth2AuditLog::getCreateTime, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(OAuth2AuditLog::getCreateTime, query.getCreatedTo());
        }
        wrapper.orderByDesc(OAuth2AuditLog::getCreateTime);
        return PageResult.of(oAuth2AuditLogDao.selectPage(page, wrapper));
    }

    public Map<String, Object> byTrace(String traceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("traceId", traceId);
        data.put("operations", operationAuditDao.selectList(new LambdaQueryWrapper<OperationAudit>()
                .eq(OperationAudit::getTraceId, traceId)
                .orderByDesc(OperationAudit::getCreatedTime)
                .last("LIMIT 100")));
        data.put("note", "支付/OAuth 审计表当前无 trace_id 字段，仅返回操作审计");
        return data;
    }
}
