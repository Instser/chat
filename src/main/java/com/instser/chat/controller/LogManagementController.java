package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.util.LogLevelManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志管理控制器
 * 提供日志级别管理的Web接口（仅限localhost访问）
 *
 * @author instser
 * @since 2025-07-16
 */
@Slf4j
@RestController
@RequestMapping("/management/logs")
@RequiredArgsConstructor
@Tag(name = "日志管理", description = "日志级别管理接口（仅限localhost访问）")
public class LogManagementController {

    private final LogLevelManager logLevelManager;

    @GetMapping("/levels")
    @Operation(summary = "获取所有主要Logger的当前级别")
    public Result<Map<String, String>> getAllLoggerLevels() {
        log.info("【日志管理】获取所有Logger级别");
        Map<String, String> levels = logLevelManager.getAllMainLoggerLevels();
        return Result.success(levels);
    }

    @GetMapping("/levels/{loggerName}")
    @Operation(summary = "获取指定Logger的当前级别")
    public Result<String> getLoggerLevel(
            @Parameter(description = "Logger名称") @PathVariable String loggerName) {
        log.info("【日志管理】获取Logger级别，Logger：{}", loggerName);
        String level = logLevelManager.getCurrentLevel(loggerName);
        return Result.success(level);
    }

    @PostMapping("/levels/{loggerName}")
    @Operation(summary = "设置指定Logger的日志级别")
    public Result<String> setLoggerLevel(
            @Parameter(description = "Logger名称") @PathVariable String loggerName,
            @Parameter(description = "日志级别") @RequestParam String level) {
        log.info("【日志管理】设置Logger级别，Logger：{}，级别：{}", loggerName, level);
        boolean success = logLevelManager.setLevel(loggerName, level);
        if (success) {
            return Result.success("日志级别设置成功");
        } else {
            return Result.failed("日志级别设置失败");
        }
    }

    @DeleteMapping("/levels/{loggerName}")
    @Operation(summary = "重置指定Logger的日志级别")
    public Result<String> resetLoggerLevel(
            @Parameter(description = "Logger名称") @PathVariable String loggerName) {
        log.info("【日志管理】重置Logger级别，Logger：{}", loggerName);
        boolean success = logLevelManager.resetLevel(loggerName);
        if (success) {
            return Result.success("日志级别重置成功");
        } else {
            return Result.failed("日志级别重置失败");
        }
    }

    @PostMapping("/presets/development")
    @Operation(summary = "设置开发环境日志级别")
    public Result<String> setDevelopmentLevels() {
        log.info("【日志管理】设置开发环境日志级别");
        logLevelManager.setDevelopmentLevels();
        return Result.success("开发环境日志级别设置完成");
    }

    @PostMapping("/presets/production")
    @Operation(summary = "设置生产环境日志级别")
    public Result<String> setProductionLevels() {
        log.info("【日志管理】设置生产环境日志级别");
        logLevelManager.setProductionLevels();
        return Result.success("生产环境日志级别设置完成");
    }

    @PostMapping("/presets/debugging")
    @Operation(summary = "设置问题排查日志级别")
    public Result<String> setDebuggingLevels() {
        log.info("【日志管理】设置问题排查日志级别");
        logLevelManager.setDebuggingLevels();
        return Result.success("问题排查日志级别设置完成");
    }

    @PostMapping("/sql/enable")
    @Operation(summary = "开启SQL日志")
    public Result<String> enableSqlLogging() {
        log.info("【日志管理】开启SQL日志");
        logLevelManager.enableSqlLogging();
        return Result.success("SQL日志已开启");
    }

    @PostMapping("/sql/disable")
    @Operation(summary = "关闭SQL日志")
    public Result<String> disableSqlLogging() {
        log.info("【日志管理】关闭SQL日志");
        logLevelManager.disableSqlLogging();
        return Result.success("SQL日志已关闭");
    }
}
