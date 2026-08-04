package com.instser.chat.admin.controller;

import com.instser.chat.admin.common.PageResult;
import com.instser.chat.admin.query.AdminAuditQuery;
import com.instser.chat.admin.service.AdminAuditService;
import com.instser.chat.common.result.Result;
import com.instser.chat.entity.OAuth2AuditLog;
import com.instser.chat.entity.OperationAudit;
import com.instser.chat.entity.PaymentAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/audits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-审计", description = "操作/支付/OAuth 审计查询")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @GetMapping("/operations")
    @Operation(summary = "运维操作审计列表")
    public Result<PageResult<OperationAudit>> operations(AdminAuditQuery query) {
        return Result.success(adminAuditService.listOperations(query));
    }

    @GetMapping("/operations/{id}")
    @Operation(summary = "运维操作审计详情")
    public Result<OperationAudit> operationDetail(@PathVariable Long id) {
        return Result.success(adminAuditService.getOperation(id));
    }

    @GetMapping("/payments")
    @Operation(summary = "支付审计列表")
    public Result<PageResult<PaymentAudit>> payments(AdminAuditQuery query) {
        return Result.success(adminAuditService.listPayments(query));
    }

    @GetMapping("/oauth")
    @Operation(summary = "OAuth 审计列表")
    public Result<PageResult<OAuth2AuditLog>> oauth(AdminAuditQuery query) {
        return Result.success(adminAuditService.listOauth(query));
    }

    @GetMapping("/by-trace/{traceId}")
    @Operation(summary = "按 traceId 聚合操作审计")
    public Result<Map<String, Object>> byTrace(@PathVariable String traceId) {
        return Result.success(adminAuditService.byTrace(traceId));
    }
}
