package com.instser.chat.event;

import java.time.LocalDateTime;

/**
 * 运维操作审计事件 — 通过 Spring Event 异步发布，不阻塞主流程
 */
public record OperationAuditEvent(
    String operator,
    Long operatorId,
    String operationType,
    String target,
    String targetDetail,
    String result,
    String clientIp,
    String traceId,
    LocalDateTime createdTime
) {}
