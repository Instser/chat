package com.instser.chat.admin.controller;

import com.instser.chat.admin.service.AdminOpsService;
import com.instser.chat.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-仪表盘", description = "系统概览与健康")
public class AdminDashboardController {

    private final AdminOpsService adminOpsService;

    @GetMapping("/overview")
    @Operation(summary = "系统概览")
    public Result<Map<String, Object>> overview() {
        return Result.success(adminOpsService.overview());
    }

    @GetMapping("/health")
    @Operation(summary = "脱敏健康状态")
    public Result<Map<String, Object>> health() {
        return Result.success(adminOpsService.health());
    }

    @GetMapping("/alerts")
    @Operation(summary = "告警摘要（当前返回健康降级组件）")
    public Result<Map<String, Object>> alerts() {
        Map<String, Object> health = adminOpsService.health();
        return Result.success(Map.of(
                "status", health.get("status"),
                "components", health.get("components"),
                "checkedAt", health.get("checkedAt")
        ));
    }
}
