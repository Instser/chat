package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.config.LogManagementConfig;
import com.instser.chat.event.OperationAuditEvent;
import com.instser.chat.service.ManagementApiKeyService;
import com.instser.chat.util.LogLevelManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import com.instser.chat.common.util.LogUtils;

/**
 * 日志管理控制器
 * 提供日志级别管理的Web接口（仅限localhost访问）

 *
 * 访问配置:
 * - 运行端口: 8081 (集成到现有management端口)
 * - 访问限制: API密钥验证（支持远程访问）
 * - 访问地址: http://127.0.0.1:8081/admin/logs/*
 * - 监控端点: http://127.0.0.1:8081/actuator/*
 *
 * @author instser
 * @since 2025-07-16
 */
@Slf4j
@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@Tag(name = "日志管理", description = "日志级别管理接口（API密钥验证）")
public class LogManagementController {

    private final LogLevelManager logLevelManager;
    private final LogManagementConfig logManagementConfig;
    private final ManagementApiKeyService managementApiKeyService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private void publishAudit(String operationType, String target, String targetDetail,
                               String result, HttpServletRequest request) {
        applicationEventPublisher.publishEvent(new OperationAuditEvent(
                "system", null, operationType, target, targetDetail,
                result, request.getRemoteAddr(), MDC.get("traceId"), LocalDateTime.now()
        ));
    }

    /**
     * 验证API密钥
     */
    private boolean isAuthorizedAccess(HttpServletRequest request) {
        return managementApiKeyService.validateRequest(request);
    }

    @GetMapping("/levels")
    @Operation(summary = "获取所有主要Logger的当前级别")
    public ResponseEntity<Result<Map<String, String>>> getAllLoggerLevels(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<Map<String, String>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "获取所有Logger级别");
        Map<String, String> levels = logLevelManager.getAllMainLoggerLevels();
        return ResponseEntity.ok(Result.success(levels));
    }

    @GetMapping("/levels/{loggerName}")
    @Operation(summary = "获取指定Logger的当前级别")
    public ResponseEntity<Result<String>> getLoggerLevel(
            @Parameter(description = "Logger名称") @PathVariable String loggerName,
            HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "获取Logger级别，Logger：{}", loggerName);
        String level = logLevelManager.getCurrentLevel(loggerName);
        return ResponseEntity.ok(Result.success(level));
    }

    @PostMapping("/levels/{loggerName}")
    @Operation(summary = "设置指定Logger的日志级别")
    public ResponseEntity<Result<String>> setLoggerLevel(
            @Parameter(description = "Logger名称") @PathVariable String loggerName,
            @Parameter(description = "日志级别") @RequestParam String level,
            HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "设置Logger级别，Logger：{}，级别：{}", loggerName, level);
        boolean success = logLevelManager.setLevel(loggerName, level);
        if (success) {
            publishAudit("LOG_LEVEL_CHANGE", loggerName, "level=" + level,
                    "SUCCESS", request);
            return ResponseEntity.ok(Result.success("日志级别设置成功"));
        } else {
            return ResponseEntity.ok(Result.failed("日志级别设置失败"));
        }
    }

    @DeleteMapping("/levels/{loggerName}")
    @Operation(summary = "重置指定Logger的日志级别")
    public ResponseEntity<Result<String>> resetLoggerLevel(
            @Parameter(description = "Logger名称") @PathVariable String loggerName,
            HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "重置Logger级别，Logger：{}", loggerName);
        boolean success = logLevelManager.resetLevel(loggerName);
        if (success) {
            publishAudit("LOG_LEVEL_CHANGE", loggerName, "action=reset",
                    "SUCCESS", request);
            return ResponseEntity.ok(Result.success("日志级别重置成功"));
        } else {
            return ResponseEntity.ok(Result.failed("日志级别重置失败"));
        }
    }

    @PostMapping("/presets/development")
    @Operation(summary = "设置开发环境日志级别")
    public ResponseEntity<Result<String>> setDevelopmentLevels(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "设置开发环境日志级别");
        logLevelManager.setDevelopmentLevels();
        publishAudit("LOG_LEVEL_CHANGE", "preset", "preset=development",
                "SUCCESS", request);
        return ResponseEntity.ok(Result.success("开发环境日志级别设置完成"));
    }

    @PostMapping("/presets/production")
    @Operation(summary = "设置生产环境日志级别")
    public ResponseEntity<Result<String>> setProductionLevels(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "设置生产环境日志级别");
        logLevelManager.setProductionLevels();
        publishAudit("LOG_LEVEL_CHANGE", "preset", "preset=production",
                "SUCCESS", request);
        return ResponseEntity.ok(Result.success("生产环境日志级别设置完成"));
    }

    @PostMapping("/presets/debugging")
    @Operation(summary = "设置问题排查日志级别")
    public ResponseEntity<Result<String>> setDebuggingLevels(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "设置问题排查日志级别");
        logLevelManager.setDebuggingLevels();
        publishAudit("LOG_LEVEL_CHANGE", "preset", "preset=debugging",
                "SUCCESS", request);
        return ResponseEntity.ok(Result.success("问题排查日志级别设置完成"));
    }

    @PostMapping("/sql/enable")
    @Operation(summary = "开启SQL日志")
    public ResponseEntity<Result<String>> enableSqlLogging(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "开启SQL日志");
        logLevelManager.enableSqlLogging();
        publishAudit("SQL_LOG_TOGGLE", "sql-logging", "action=enable",
                "SUCCESS", request);
        return ResponseEntity.ok(Result.success("SQL日志已开启"));
    }

    @PostMapping("/sql/disable")
    @Operation(summary = "关闭SQL日志")
    public ResponseEntity<Result<String>> disableSqlLogging(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "关闭SQL日志");
        logLevelManager.disableSqlLogging();
        publishAudit("SQL_LOG_TOGGLE", "sql-logging", "action=disable",
                "SUCCESS", request);
        return ResponseEntity.ok(Result.success("SQL日志已关闭"));
    }

    @GetMapping("/files/status")
    @Operation(summary = "获取日志文件状态")
    public ResponseEntity<Result<String>> getLogFileStatus(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("日志管理API非法访问尝试 - IP: {}", request.getRemoteAddr());
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        LogUtils.logAdminOperation(log, "日志管理", "获取日志文件状态");
        String status = logManagementConfig.getLogManagementStatus();
        return ResponseEntity.ok(Result.success(status));
    }


}
