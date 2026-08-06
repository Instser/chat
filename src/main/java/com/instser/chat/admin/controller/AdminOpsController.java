package com.instser.chat.admin.controller;

import com.instser.chat.admin.dto.AdminReasonRequest;
import com.instser.chat.admin.service.AdminOpsService;
import com.instser.chat.common.result.Result;
import com.instser.chat.dto.ErrorAnalysisRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/ops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-运维中心", description = "健康、缓存、日志与 AI 分析")
public class AdminOpsController {

    private final AdminOpsService adminOpsService;

    @GetMapping("/version")
    @Operation(summary = "版本与运行信息")
    public Result<Map<String, Object>> version() {
        return Result.success(adminOpsService.version());
    }

    @GetMapping("/health")
    @Operation(summary = "真实健康检查")
    public Result<Map<String, Object>> health() {
        return Result.success(adminOpsService.health());
    }

    @GetMapping("/metrics/summary")
    @Operation(summary = "白名单指标摘要")
    public Result<Map<String, Object>> metrics() {
        return Result.success(adminOpsService.metricsSummary());
    }

    @GetMapping("/monitoring")
    @Operation(summary = "运维监控汇总（AI/HTTP/系统/支付/模型连通）")
    public Result<Map<String, Object>> monitoring(
            @RequestParam(value = "range", required = false, defaultValue = "7d") String range) {
        return Result.success(adminOpsService.monitoring(range));
    }

    @GetMapping("/cache/summary")
    @Operation(summary = "缓存管理摘要")
    public Result<Map<String, Object>> cacheSummary() {
        return Result.success(adminOpsService.cacheSummary());
    }

    @PostMapping("/cache/user-package/evict-all")
    @Operation(summary = "清理用户套餐相关缓存")
    public Result<Map<String, Object>> evictCache(@Valid @RequestBody AdminReasonRequest request) {
        return adminOpsService.evictUserPackageCache(request);
    }

    @GetMapping("/loggers")
    @Operation(summary = "Logger 级别列表")
    public Result<Map<String, String>> loggers() {
        return Result.success(adminOpsService.listLoggers());
    }

    @PutMapping("/loggers/{loggerName}")
    @Operation(summary = "修改 Logger 级别（白名单）")
    public Result<String> updateLogger(@PathVariable String loggerName,
                                       @RequestParam String level,
                                       @RequestParam(required = false, defaultValue = "manual") String reason) {
        return adminOpsService.updateLogger(loggerName, level, reason);
    }

    @DeleteMapping("/loggers/{loggerName}")
    @Operation(summary = "重置 Logger 级别（白名单）")
    public Result<String> resetLogger(@PathVariable String loggerName,
                                      @RequestParam(required = false, defaultValue = "manual") String reason) {
        return adminOpsService.resetLogger(loggerName, reason);
    }

    @PutMapping("/logging/preset")
    @Operation(summary = "应用日志预设")
    public Result<Map<String, String>> preset(@RequestParam String preset,
                                              @RequestParam(required = false, defaultValue = "manual") String reason) {
        return adminOpsService.applyPreset(preset, reason);
    }

    @PostMapping("/ai/analyze-error")
    @Operation(summary = "脱敏 AI 错误分析")
    public Result<Map<String, Object>> analyzeError(@Valid @RequestBody ErrorAnalysisRequest request) {
        return adminOpsService.analyzeError(request);
    }
}
