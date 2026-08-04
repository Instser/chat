package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

import com.instser.chat.common.util.LogUtils;
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
    "https://chat.instser.cn", "http://chat.instser.cn",
    "http://203.0.113.16:3000"
}, allowedHeaders = "*", allowCredentials = "true")
public class PaymentAuditController {

    private final PaymentAuditService paymentAuditService;

    /**
     * 根据订单号查询审计日志（仅管理员）
     */
    @GetMapping("/order/{orderNo}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<PaymentAudit>> getAuditByOrderNo(@PathVariable @NotBlank String orderNo, Authentication authentication) {
        try {
            List<PaymentAudit> auditList = paymentAuditService.getAuditByOrderNo(orderNo);
            return Result.success(auditList, "查询审计日志成功");
        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "查询审计日志", "查询订单审计日志失败，订单号: {}", e, orderNo);
            return Result.failed("查询审计日志失败");
        }
    }

    /**
     * 根据操作类型查询审计日志（仅管理员）
     */
    @GetMapping("/operation/{operationType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<PaymentAudit>> getAuditByOperationType(Authentication authentication,
            @PathVariable @NotBlank String operationType,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<PaymentAudit> auditList = paymentAuditService.getAuditByOperationType(operationType, startTime, endTime);
            return Result.success(auditList, "查询审计日志成功");
        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "查询审计日志", "查询操作类型审计日志失败，操作类型: {}", e, operationType);
            return Result.failed("查询审计日志失败");
        }
    }

    /**
     * 根据操作人查询审计日志（仅管理员）
     */
    @GetMapping("/operator/{operatorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<PaymentAudit>> getAuditByOperator(Authentication authentication,
            @PathVariable @NotNull Long operatorId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<PaymentAudit> auditList = paymentAuditService.getAuditByOperator(operatorId, startTime, endTime);
            return Result.success(auditList, "查询审计日志成功");
        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "查询审计日志", "查询操作人审计日志失败，操作人ID: {}", e, operatorId);
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
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) principal;
                return userDetails.getUserId();
            }
            return null;
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "认证", "获取用户ID失败: {}", e.getMessage());
            return null;
        }
    }
}
