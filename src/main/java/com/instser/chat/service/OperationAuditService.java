package com.instser.chat.service;

import com.instser.chat.dao.OperationAuditDao;
import com.instser.chat.entity.OperationAudit;
import com.instser.chat.event.OperationAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 运维操作审计服务 — 异步消费 OperationAuditEvent 写入数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationAuditService {

    private final OperationAuditDao operationAuditDao;

    @Async
    @EventListener
    public void handleOperationAudit(OperationAuditEvent event) {
        try {
            OperationAudit audit = new OperationAudit();
            audit.setOperator(event.operator());
            audit.setOperatorId(event.operatorId());
            audit.setOperationType(event.operationType());
            audit.setTarget(event.target());
            audit.setTargetDetail(event.targetDetail());
            audit.setResult(event.result());
            audit.setClientIp(event.clientIp());
            audit.setTraceId(event.traceId());
            audit.setCreatedTime(event.createdTime());

            operationAuditDao.insert(audit);
            log.debug("运维审计记录已保存: type={} target={} result={}",
                    event.operationType(), event.target(), event.result());
        } catch (Exception e) {
            log.error("运维审计记录保存失败: type={} target={}", event.operationType(), event.target(), e);
        }
    }
}
