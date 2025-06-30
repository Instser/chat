package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付审计日志控制器
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@RestController
@RequestMapping("/payment/audit")
@RequiredArgsConstructor
@Validated
@CrossOrigin(originPatterns = {
    "http://localhost:*", "https://localhost:*",
    "http://127.0.0.1:*", "https://127.0.0.1:*",
    "http://192.168.1.100:*", "https://192.168.1.100:*",
    "http://192.168.1.101:*", "https://192.168.1.101:*",
    "https://chat.example.com", "http://chat.example.com"
}, allowedHeaders = "*", allowCredentials = "true")
public class PaymentAuditController {

    private final PaymentAuditService paymentAuditService;

    /**
     * 根据订单号查询审计日志
     */
    @GetMapping("/order/{orderNo}")
    public Result<List<PaymentAudit>> getAuditByOrderNo(@PathVariable @NotBlank String orderNo) {
        try {
            List<PaymentAudit> auditList = paymentAuditService.getAuditByOrderNo(orderNo);
            return Result.success(auditList, "查询审计日志成功");
        } catch (Exception e) {
            log.error("查询订单审计日志失败，订单号: {}", orderNo, e);
            return Result.failed("查询审计日志失败");
        }
    }

    /**
     * 根据操作类型查询审计日志
     */
    @GetMapping("/operation/{operationType}")
    public Result<List<PaymentAudit>> getAuditByOperationType(
            @PathVariable @NotBlank String operationType,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<PaymentAudit> auditList = paymentAuditService.getAuditByOperationType(operationType, startTime, endTime);
            return Result.success(auditList, "查询审计日志成功");
        } catch (Exception e) {
            log.error("查询操作类型审计日志失败，操作类型: {}", operationType, e);
            return Result.failed("查询审计日志失败");
        }
    }

    /**
     * 根据操作人查询审计日志
     */
    @GetMapping("/operator/{operatorId}")
    public Result<List<PaymentAudit>> getAuditByOperator(
            @PathVariable @NotNull Long operatorId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<PaymentAudit> auditList = paymentAuditService.getAuditByOperator(operatorId, startTime, endTime);
            return Result.success(auditList, "查询审计日志成功");
        } catch (Exception e) {
            log.error("查询操作人审计日志失败，操作人ID: {}", operatorId, e);
            return Result.failed("查询审计日志失败");
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            log.warn("无法解析用户ID: {}", authentication.getName());
            return null;
        }
    }
}
