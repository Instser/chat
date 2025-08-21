package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.UserPaymentStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import com.instser.chat.common.util.LogUtils;

/**
 * 用户支付状态控制器

 * 提供用户充值状态查询API
 *
 * @author instser
 * @since 2025-07-14
 */
@Slf4j
@RestController
@RequestMapping("/user/status")
@RequiredArgsConstructor
@Tag(name = "用户支付状态", description = "用户充值状态查询相关接口")
public class UserPaymentStatusController {

    private final UserPaymentStatusService userPaymentStatusService;

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            // 检查是否是CustomUserDetails类型
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetailsServiceImpl.CustomUserDetails) {
                UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (UserDetailsServiceImpl.CustomUserDetails) principal;
                return userDetails.getUserId();
            }
            // 如果不是CustomUserDetails，返回null表示认证失败
            LogUtils.logUserWarning(log, null, "认证", "认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "认证", "获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 获取当前用户的支付状态概览
     */
    @GetMapping("/overview")
    @Operation(summary = "获取用户支付状态概览", description = "获取当前用户的充值状态、总金额等信息")
    public ResponseEntity<Result<Map<String, Object>>> getPaymentStatusOverview(Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            Map<String, Object> overview = new HashMap<>();

            // 基本状态判定
            boolean neverPaid = userPaymentStatusService.hasNeverPaid(userId);
            boolean onlyFree = userPaymentStatusService.isOnlyFreeUser(userId);
            boolean activePaid = userPaymentStatusService.isActivePaidUser(userId);

            // 支付信息
            BigDecimal totalAmount = userPaymentStatusService.getTotalPaidAmount(userId);
            LocalDateTime firstPaymentTime = userPaymentStatusService.getFirstPaymentTime(userId);

            // 状态分类
            String statusCategory;
            if (neverPaid) {
                statusCategory = "NEVER_PAID";
            } else if (onlyFree) {
                statusCategory = "FREE_ONLY";
            } else if (activePaid) {
                statusCategory = "ACTIVE_PAID";
            } else {
                statusCategory = "INACTIVE_PAID";
            }

            overview.put("userId", userId);
            overview.put("neverPaid", neverPaid);
            overview.put("onlyFreeUser", onlyFree);
            overview.put("activePaidUser", activePaid);
            overview.put("statusCategory", statusCategory);
            overview.put("totalPaidAmount", totalAmount);
            overview.put("firstPaymentTime", firstPaymentTime);

            LogUtils.logUserOperation(log, userId, "查询支付状态", "用户支付状态查询成功: {}", statusCategory);
            return ResponseEntity.ok(Result.success(overview));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "查询支付状态", "获取用户支付状态概览异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }

    /**
     * 检查用户是否从未充值
     */
    @GetMapping("/new")
    @Operation(summary = "检查用户是否从未充值", description = "判定用户是否从未进行过任何充值")
    public ResponseEntity<Result<Boolean>> hasNeverPaid(Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            boolean neverPaid = userPaymentStatusService.hasNeverPaid(userId);
            LogUtils.logUserDebug(log, userId, "充值状态检查", "从未充值={}", neverPaid);

            return ResponseEntity.ok(Result.success(neverPaid));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "充值状态检查", "检查用户充值状态异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }

    /**
     * 检查用户是否只使用免费套餐
     */
    @GetMapping("/free-only")
    @Operation(summary = "检查用户是否只使用免费套餐", description = "判定用户是否只绑定过免费套餐")
    public ResponseEntity<Result<Boolean>> isOnlyFreeUser(Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            boolean onlyFree = userPaymentStatusService.isOnlyFreeUser(userId);
            LogUtils.logUserDebug(log, userId, "套餐使用状态检查", "仅免费套餐={}", onlyFree);

            return ResponseEntity.ok(Result.success(onlyFree));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "套餐使用状态检查", "检查用户套餐使用状态异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }

    /**
     * 检查用户是否为活跃付费用户
     */
    @GetMapping("/active-paid")
    @Operation(summary = "检查用户是否为活跃付费用户", description = "判定用户是否有有效的付费套餐")
    public ResponseEntity<Result<Boolean>> isActivePaidUser(Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            boolean activePaid = userPaymentStatusService.isActivePaidUser(userId);
            LogUtils.logUserDebug(log, userId, "活跃付费状态检查", "活跃付费用户={}", activePaid);

            return ResponseEntity.ok(Result.success(activePaid));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "活跃付费状态检查", "检查用户活跃付费状态异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }

    /**
     * 获取用户总充值金额
     */
    @GetMapping("/total-amount")
    @Operation(summary = "获取用户总充值金额", description = "获取用户历史总充值金额")
    public ResponseEntity<Result<BigDecimal>> getTotalPaidAmount(Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            BigDecimal totalAmount = userPaymentStatusService.getTotalPaidAmount(userId);
            LogUtils.logUserDebug(log, userId, "总充值金额查询", "总充值金额: {}", totalAmount);

            return ResponseEntity.ok(Result.success(totalAmount));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "总充值金额查询", "获取用户总充值金额异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }

    /**
     * 获取用户首次充值时间
     */
    @GetMapping("/first-payment-time")
    @Operation(summary = "获取用户首次充值时间", description = "获取用户首次成功支付的时间")
    public ResponseEntity<Result<LocalDateTime>> getFirstPaymentTime(Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            LocalDateTime firstPaymentTime = userPaymentStatusService.getFirstPaymentTime(userId);
            LogUtils.logUserDebug(log, userId, "首次充值时间查询", "首次充值时间: {}", firstPaymentTime);

            return ResponseEntity.ok(Result.success(firstPaymentTime));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "首次充值时间查询", "获取用户首次充值时间异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }

    /**
     * 管理员接口：查询指定用户的支付状态
     */
    @GetMapping("/admin/{userId}")
    @Operation(summary = "管理员查询用户支付状态", description = "管理员查询指定用户的充值状态信息")
    public ResponseEntity<Result<Map<String, Object>>> getPaymentStatusByAdmin(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            Authentication authentication) {
        try {
            Long currentUserId = getCurrentUserId(authentication);
            if (currentUserId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未登录"));
            }

            // 检查管理员权限（这里简化处理，实际应该检查用户角色）
            // TODO: 添加管理员权限检查

            Map<String, Object> status = new HashMap<>();
            status.put("userId", userId);
            status.put("neverPaid", userPaymentStatusService.hasNeverPaid(userId));
            status.put("onlyFreeUser", userPaymentStatusService.isOnlyFreeUser(userId));
            status.put("activePaidUser", userPaymentStatusService.isActivePaidUser(userId));
            status.put("totalPaidAmount", userPaymentStatusService.getTotalPaidAmount(userId));
            status.put("firstPaymentTime", userPaymentStatusService.getFirstPaymentTime(userId));

            LogUtils.logAdminOperation(log, "查询用户支付状态", "管理员{}查询用户{}支付状态成功", currentUserId, userId);
            return ResponseEntity.ok(Result.success(status));

        } catch (Exception e) {
            LogUtils.logAdminOperation(log, "查询用户支付状态", "管理员查询用户支付状态异常", e);
            return ResponseEntity.ok(Result.failed("查询失败"));
        }
    }
}
