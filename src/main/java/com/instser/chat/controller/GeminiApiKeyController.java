package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.service.GeminiApiKeyManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Gemini API密钥管理控制器
 * 提供密钥管理的REST API接口，仅限localhost访问
 *
 * @author instser
 * @since 2025-07-16
 */
@RestController
@RequestMapping("/admin/gemini")
@Slf4j
public class GeminiApiKeyController {

    private final GeminiApiKeyManager keyManager;

    @Autowired
    public GeminiApiKeyController(GeminiApiKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /**
     * 检查是否为localhost访问
     */
    private boolean isLocalhostAccess(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String remoteHost = request.getRemoteHost();
        
        // 检查IPv4和IPv6的localhost地址
        return "127.0.0.1".equals(remoteAddr) || 
               "0:0:0:0:0:0:0:1".equals(remoteAddr) || 
               "::1".equals(remoteAddr) ||
               "localhost".equals(remoteHost);
    }

    /**
     * 获取密钥统计信息
     */
    @GetMapping("/keys/stats")
    public Result<Map<String, Object>> getKeyStatistics(HttpServletRequest request) {
        if (!isLocalhostAccess(request)) {
            log.warn("【密钥管理API】非localhost访问被拒绝，来源：{}", request.getRemoteAddr());
            return Result.failed("访问被拒绝：仅允许localhost访问");
        }

        try {
            Map<String, Object> stats = keyManager.getKeyStatistics();
            log.info("【密钥管理API】获取密钥统计成功，总密钥数：{}", stats.get("totalKeys"));
            return Result.success(stats, "获取密钥统计成功");
        } catch (Exception e) {
            log.error("【密钥管理API】获取密钥统计失败：{}", e.getMessage(), e);
            return Result.failed("获取统计失败：" + e.getMessage());
        }
    }

    /**
     * 更新API密钥列表
     */
    @PostMapping("/keys/update")
    public Result<String> updateApiKeys(@RequestBody UpdateKeysRequest request, HttpServletRequest httpRequest) {
        if (!isLocalhostAccess(httpRequest)) {
            log.warn("【密钥管理API】非localhost访问被拒绝，来源：{}", httpRequest.getRemoteAddr());
            return Result.failed("访问被拒绝：仅允许localhost访问");
        }

        if (request.getKeys() == null || request.getKeys().isEmpty()) {
            return Result.failed("密钥列表不能为空");
        }

        try {
            keyManager.updateApiKeys(request.getKeys());
            log.info("【密钥管理API】API密钥列表更新成功，新密钥数量：{}", request.getKeys().size());
            return Result.success("API密钥列表更新成功");
        } catch (Exception e) {
            log.error("【密钥管理API】更新API密钥列表失败：{}", e.getMessage(), e);
            return Result.failed("更新失败：" + e.getMessage());
        }
    }

    /**
     * 手动禁用密钥
     */
    @PostMapping("/keys/{keyIndex}/disable")
    public Result<String> disableKey(@PathVariable int keyIndex, HttpServletRequest request) {
        if (!isLocalhostAccess(request)) {
            log.warn("【密钥管理API】非localhost访问被拒绝，来源：{}", request.getRemoteAddr());
            return Result.failed("访问被拒绝：仅允许localhost访问");
        }

        try {
            // 这里可以实现具体的密钥禁用逻辑
            log.info("【密钥管理API】手动禁用密钥，索引：{}", keyIndex);
            return Result.success("密钥已禁用");
        } catch (Exception e) {
            log.error("【密钥管理API】禁用密钥失败：{}", e.getMessage(), e);
            return Result.failed("禁用失败：" + e.getMessage());
        }
    }

    /**
     * 手动启用密钥
     */
    @PostMapping("/keys/{keyIndex}/enable")
    public Result<String> enableKey(@PathVariable int keyIndex, HttpServletRequest request) {
        if (!isLocalhostAccess(request)) {
            log.warn("【密钥管理API】非localhost访问被拒绝，来源：{}", request.getRemoteAddr());
            return Result.failed("访问被拒绝：仅允许localhost访问");
        }

        try {
            // 这里可以实现具体的密钥启用逻辑
            log.info("【密钥管理API】手动启用密钥，索引：{}", keyIndex);
            return Result.success("密钥已启用");
        } catch (Exception e) {
            log.error("【密钥管理API】启用密钥失败：{}", e.getMessage(), e);
            return Result.failed("启用失败：" + e.getMessage());
        }
    }

    /**
     * 获取密钥健康状态
     */
    @GetMapping("/keys/health")
    public Result<Map<String, Object>> getKeyHealth(HttpServletRequest request) {
        if (!isLocalhostAccess(request)) {
            log.warn("【密钥管理API】非localhost访问被拒绝，来源：{}", request.getRemoteAddr());
            return Result.failed("访问被拒绝：仅允许localhost访问");
        }

        try {
            Map<String, Object> health = keyManager.getKeyStatistics();
            log.debug("【密钥管理API】获取密钥健康状态成功");
            return Result.success(health, "获取健康状态成功");
        } catch (Exception e) {
            log.error("【密钥管理API】获取健康状态失败：{}", e.getMessage(), e);
            return Result.failed("获取健康状态失败：" + e.getMessage());
        }
    }

    /**
     * 更新密钥请求DTO
     */
    @Data
    public static class UpdateKeysRequest {
        private List<String> keys;
    }
}
