package com.instser.chat.admin.controller;

import com.instser.chat.admin.service.AdminLogService;
import com.instser.chat.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员日志查看接口。
 * <p>读取后端日志文件尾部，支持按 traceId / 关键词 / 级别筛选。
 * 日志文件由 logback 输出到 {@code logs/} 目录。
 * <p>路径使用 {@code /admin/log-viewer} 而非 {@code /admin/logs}，
 * 避免与旧的 API Key 鉴权的 {@code LogManagementController}（{@code /admin/logs/**}，已 permitAll）冲突，
 * 本接口强制 JWT + ROLE_ADMIN 鉴权（由 {@code /admin/**} 规则保障）。
 *
 * @author instser
 * @since 2026-08-26
 */
@RestController
@RequestMapping("/admin/log-viewer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-日志查看", description = "应用日志与报错日志查看")
public class AdminLogController {

    private final AdminLogService adminLogService;

    @GetMapping("/files")
    @Operation(summary = "列出可用日志文件")
    public Result<List<Map<String, Object>>> files() {
        return Result.success(adminLogService.listFiles());
    }

    @GetMapping("/tail")
    @Operation(summary = "读取日志尾部（支持 traceId/关键词/级别筛选）")
    public Result<Map<String, Object>> tail(
            @Parameter(description = "日志文件名") @RequestParam(defaultValue = "jinyu-chat-all.log") String file,
            @Parameter(description = "读取行数（默认500，上限5000）") @RequestParam(required = false) Integer lines,
            @Parameter(description = "按 traceId 筛选") @RequestParam(required = false) String traceId,
            @Parameter(description = "关键词搜索（忽略大小写）") @RequestParam(required = false) String keyword,
            @Parameter(description = "日志级别（ERROR/WARN/INFO/DEBUG）") @RequestParam(required = false) String level
    ) {
        return Result.success(adminLogService.tail(file, lines, traceId, keyword, level));
    }
}
