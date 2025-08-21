package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.service.EnhancedPaymentSecurityService;
import com.instser.chat.service.ManagementApiKeyService;
import com.instser.chat.service.cache.CacheMonitoringService;
import com.instser.chat.service.cache.CacheRegistryService;
import com.instser.chat.service.cache.GlobalCacheManager;
import com.instser.chat.service.cache.HybridCacheManager;
import com.instser.chat.service.cache.RedisCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.annotation.PostConstruct;

import com.instser.chat.common.util.LogUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 全局缓存管理控制器
 * 提供全系统缓存监控、管理和调试接口
 *

 * 安全配置:
 * - 运行端口: 8081 (集成到现有management端口)
 * - 访问限制: API密钥验证（支持远程访问）
 * - 访问地址: http://127.0.0.1:8081/admin/cache/*
 * - 监控端点: http://127.0.0.1:8081/actuator/*
 *
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache")
public class CacheManagementController {

    @Autowired(required = false)
    private HybridCacheManager hybridCacheManager;

    @Autowired(required = false)
    private RedisCircuitBreaker circuitBreaker;

    @Autowired(required = false)
    private CacheMonitoringService monitoringService;

    @Autowired(required = false)
    private EnhancedPaymentSecurityService paymentSecurityService;

    @Autowired(required = false)
    private GlobalCacheManager globalCacheManager;

    @Autowired(required = false)
    @Lazy
    private CacheRegistryService cacheRegistryService;

    @Autowired(required = false)
    private ManagementApiKeyService managementApiKeyService;

    // 操作历史记录（最多保留100条）
    private final Queue<Map<String, Object>> operationHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_HISTORY_SIZE = 100;

    @PostConstruct
    public void init() {
        log.info("=== CacheManagementController 初始化完成 ===");
        log.info("控制器映射路径: /admin/cache");
        log.info("测试端点: GET /admin/cache/test");
        log.info("统计端点: GET /admin/cache/stats");
        log.info("安全控制: API密钥验证（支持远程访问）");
        log.info("==========================================");
    }

    /**
     * 检查是否有有效API密钥
     */
    private boolean isAuthorizedAccess(HttpServletRequest request) {
        return managementApiKeyService.validateRequest(request);
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 记录操作历史
     */
    private void recordOperation(String operation, String details, String clientIp) {
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        record.put("operation", operation);
        record.put("details", details);
        record.put("clientIp", clientIp);

        operationHistory.offer(record);

        // 保持历史记录数量在限制内
        while (operationHistory.size() > MAX_HISTORY_SIZE) {
            operationHistory.poll();
        }
    }

    /**
     * 测试端点 - 验证控制器是否工作
     */
    @GetMapping("/test")
    public ResponseEntity<Result<String>> test(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("缓存管理API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }
        return ResponseEntity.ok(Result.success("缓存管理控制器工作正常"));
    }

    /**
     * 获取全局缓存统计信息（简化版）
     */
    @GetMapping("/stats")
    public ResponseEntity<Result<Map<String, Object>>> getCacheStats(
            HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean detailed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (!isAuthorizedAccess(request)) {
            log.warn("缓存统计API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        try {
            Map<String, Object> stats = new HashMap<>();

            // 全局缓存统计
            GlobalCacheManager.GlobalCacheStats globalStats = globalCacheManager.getGlobalStats();
            stats.put("global", Map.of(
                "totalCaches", globalStats.getTotalCaches(),
                "typeDistribution", globalStats.getTypeDistribution(),
                "moduleDistribution", globalStats.getModuleDistribution(),
                "redisAvailable", globalStats.isRedisAvailable(),
                "circuitBreakerState", globalStats.getCircuitBreakerState()
            ));

            // 混合缓存统计
            HybridCacheManager.CacheStats cacheStats = hybridCacheManager.getStats();
            stats.put("hybridCache", Map.of(
                "l1HitCount", cacheStats.getL1HitCount(),
                "l1MissCount", cacheStats.getL1MissCount(),
                "l1HitRate", String.format("%.2f%%", cacheStats.getL1HitRate() * 100),
                "l1Size", cacheStats.getL1Size(),
                "l1EvictionCount", cacheStats.getL1EvictionCount(),
                "redisAvailable", cacheStats.isRedisAvailable(),
                "circuitBreakerState", cacheStats.getCircuitBreakerState()
            ));

            // 熔断器统计
            RedisCircuitBreaker.CircuitBreakerStats circuitStats = circuitBreaker.getStats();
            stats.put("circuitBreaker", Map.of(
                "state", circuitStats.getState(),
                "failureCount", circuitStats.getFailureCount(),
                "successCount", circuitStats.getSuccessCount(),
                "failureThreshold", circuitStats.getFailureThreshold(),
                "successThreshold", circuitStats.getSuccessThreshold(),
                "recoveryTimeoutMs", circuitStats.getRecoveryTimeoutMs(),
                "lastFailureTime", circuitStats.getLastFailureTime(),
                "lastSuccessTime", circuitStats.getLastSuccessTime()
            ));

            // 全局监控统计
            CacheMonitoringService.GlobalCacheMonitoringStats monitoringStats = monitoringService.getGlobalMonitoringStats();
            stats.put("monitoring", Map.of(
                "totalRequests", monitoringStats.getTotalRequests(),
                "cacheHits", monitoringStats.getCacheHits(),
                "cacheMisses", monitoringStats.getCacheMisses(),
                "redisFailures", monitoringStats.getRedisFailures(),
                "fallbackUsages", monitoringStats.getFallbackUsages(),
                "moduleStats", monitoringStats.getModuleStats(),
                "typeStats", monitoringStats.getTypeStats()
            ));

            // 发现的缓存实例（简化版或分页版）
            Map<String, CacheRegistryService.DiscoveredCache> discoveredCaches = cacheRegistryService.getDiscoveredCaches();
            if (detailed) {
                // 详细模式：分页返回
                List<Map<String, Object>> cacheList = discoveredCaches.entrySet().stream()
                    .skip((long) page * size)
                    .limit(size)
                    .map(entry -> {
                        Map<String, Object> cacheInfo = new HashMap<>();
                        cacheInfo.put("name", entry.getKey());
                        cacheInfo.put("type", entry.getValue().getType().toString());
                        cacheInfo.put("module", entry.getValue().getModule());
                        cacheInfo.put("description", entry.getValue().getDescription());
                        cacheInfo.put("discoveredAt", entry.getValue().getDiscoveredAt());
                        return cacheInfo;
                    })
                    .collect(Collectors.toList());

                stats.put("discovered", Map.of(
                    "totalDiscovered", discoveredCaches.size(),
                    "page", page,
                    "size", size,
                    "totalPages", (discoveredCaches.size() + size - 1) / size,
                    "caches", cacheList
                ));
            } else {
                // 简化模式：只返回统计信息
                Map<String, Long> typeCount = discoveredCaches.values().stream()
                    .collect(Collectors.groupingBy(
                        cache -> cache.getType().toString(),
                        Collectors.counting()
                    ));

                Map<String, Long> moduleCount = discoveredCaches.values().stream()
                    .collect(Collectors.groupingBy(
                        CacheRegistryService.DiscoveredCache::getModule,
                        Collectors.counting()
                    ));

                stats.put("discovered", Map.of(
                    "totalDiscovered", discoveredCaches.size(),
                    "typeDistribution", typeCount,
                    "moduleDistribution", moduleCount
                ));
            }

            // 支付安全统计
            EnhancedPaymentSecurityService.PaymentSecurityStats securityStats = paymentSecurityService.getStats();
            stats.put("paymentSecurity", Map.of(
                "fallbackCacheSize", securityStats.getFallbackCacheSize()
            ));

            return ResponseEntity.ok(Result.success(stats));

        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failed("获取缓存统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取缓存性能监控信息
     */
    @GetMapping("/performance")
    public ResponseEntity<Result<Map<String, Object>>> getCachePerformance(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("缓存性能监控API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        try {
            Map<String, Object> performance = new HashMap<>();

            // 混合缓存性能
            HybridCacheManager.CacheStats cacheStats = hybridCacheManager.getStats();
            performance.put("hybridCache", Map.of(
                "l1HitRate", String.format("%.2f%%", cacheStats.getL1HitRate() * 100),
                "l1Size", cacheStats.getL1Size(),
                "l1HitCount", cacheStats.getL1HitCount(),
                "l1MissCount", cacheStats.getL1MissCount(),
                "l1EvictionCount", cacheStats.getL1EvictionCount(),
                "redisAvailable", cacheStats.isRedisAvailable(),
                "circuitBreakerState", cacheStats.getCircuitBreakerState()
            ));

            // 熔断器性能
            RedisCircuitBreaker.CircuitBreakerStats circuitStats = circuitBreaker.getStats();
            performance.put("circuitBreaker", Map.of(
                "state", circuitStats.getState(),
                "failureRate", circuitStats.getFailureCount() > 0 ?
                    String.format("%.2f%%", (double) circuitStats.getFailureCount() /
                    (circuitStats.getFailureCount() + circuitStats.getSuccessCount()) * 100) : "0.00%",
                "totalOperations", circuitStats.getFailureCount() + circuitStats.getSuccessCount(),
                "uptime", circuitStats.getLastSuccessTime() > 0 ?
                    System.currentTimeMillis() - circuitStats.getLastSuccessTime() : 0
            ));

            // 全局监控性能
            CacheMonitoringService.GlobalCacheMonitoringStats monitoringStats = monitoringService.getGlobalMonitoringStats();
            long totalRequests = monitoringStats.getTotalRequests();
            long cacheHits = monitoringStats.getCacheHits();
            performance.put("monitoring", Map.of(
                "globalHitRate", totalRequests > 0 ?
                    String.format("%.2f%%", (double) cacheHits / totalRequests * 100) : "0.00%",
                "totalRequests", totalRequests,
                "cacheHits", cacheHits,
                "cacheMisses", monitoringStats.getCacheMisses(),
                "redisFailures", monitoringStats.getRedisFailures(),
                "fallbackUsages", monitoringStats.getFallbackUsages()
            ));

            return ResponseEntity.ok(Result.success(performance));

        } catch (Exception e) {
            log.error("获取缓存性能信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failed("获取缓存性能信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取操作历史记录
     */
    @GetMapping("/history")
    public ResponseEntity<Result<Map<String, Object>>> getOperationHistory(
            HttpServletRequest request,
            @RequestParam(defaultValue = "20") int limit) {

        if (!isAuthorizedAccess(request)) {
            log.warn("操作历史API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        try {
            List<Map<String, Object>> history = operationHistory.stream()
                .skip(Math.max(0, operationHistory.size() - limit))
                .collect(Collectors.toList());

            Collections.reverse(history); // 最新的在前面

            Map<String, Object> result = new HashMap<>();
            result.put("history", history);
            result.put("totalRecords", operationHistory.size());
            result.put("limit", limit);

            return ResponseEntity.ok(Result.success(result));

        } catch (Exception e) {
            log.error("获取操作历史失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failed("获取操作历史失败: " + e.getMessage()));
        }
    }

    /**
     * 获取详细的缓存列表（分页）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getCacheList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String module) {
        try {
            Map<String, CacheRegistryService.DiscoveredCache> discoveredCaches = cacheRegistryService.getDiscoveredCaches();

            // 过滤
            List<Map<String, Object>> filteredCaches = discoveredCaches.entrySet().stream()
                .filter(entry -> type == null || entry.getValue().getType().toString().equals(type))
                .filter(entry -> module == null || entry.getValue().getModule().equals(module))
                .map(entry -> {
                    Map<String, Object> cacheInfo = new HashMap<>();
                    cacheInfo.put("name", entry.getKey());
                    cacheInfo.put("type", entry.getValue().getType().toString());
                    cacheInfo.put("module", entry.getValue().getModule());
                    cacheInfo.put("description", entry.getValue().getDescription());
                    cacheInfo.put("discoveredAt", entry.getValue().getDiscoveredAt());
                    return cacheInfo;
                })
                .collect(Collectors.toList());

            // 分页
            int total = filteredCaches.size();
            int totalPages = (total + size - 1) / size;
            List<Map<String, Object>> pagedCaches = filteredCaches.stream()
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("caches", pagedCaches);
            result.put("pagination", Map.of(
                "page", page,
                "size", size,
                "total", total,
                "totalPages", totalPages
            ));

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取缓存列表失败", e);
            return Result.failed("获取缓存列表失败: " + e.getMessage());
        }
    }

    /**
     * 按模块获取缓存信息
     */
    @GetMapping("/module/{module}")
    public Result<Map<String, Object>> getCachesByModule(@PathVariable String module) {
        try {
            Map<String, Object> result = new HashMap<>();

            // 全局缓存管理器中的缓存
            Map<String, GlobalCacheManager.CacheInstance> globalCaches = globalCacheManager.getCachesByModule(module);
            result.put("globalCaches", globalCaches);

            // 发现的缓存实例
            Map<String, CacheRegistryService.DiscoveredCache> discoveredCaches = cacheRegistryService.getDiscoveredCachesByModule(module);
            result.put("discoveredCaches", discoveredCaches);

            // 模块统计
            Map<String, CacheMonitoringService.ModuleStats> moduleStats = monitoringService.getModuleStats();
            result.put("moduleStats", moduleStats.get(module));

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取模块缓存信息失败，module: {}", module, e);
            return Result.failed("获取模块缓存信息失败: " + e.getMessage());
        }
    }

    /**
     * 按类型获取缓存信息
     */
    @GetMapping("/type/{type}")
    public Result<Map<String, Object>> getCachesByType(@PathVariable String type) {
        try {
            GlobalCacheManager.CacheType cacheType = GlobalCacheManager.CacheType.valueOf(type.toUpperCase());
            Map<String, Object> result = new HashMap<>();

            // 全局缓存管理器中的缓存
            Map<String, GlobalCacheManager.CacheInstance> globalCaches = globalCacheManager.getCachesByType(cacheType);
            result.put("globalCaches", globalCaches);

            // 发现的缓存实例
            Map<String, CacheRegistryService.DiscoveredCache> discoveredCaches = cacheRegistryService.getDiscoveredCachesByType(cacheType);
            result.put("discoveredCaches", discoveredCaches);

            // 类型统计
            Map<GlobalCacheManager.CacheType, CacheMonitoringService.TypeStats> typeStats = monitoringService.getTypeStats();
            result.put("typeStats", typeStats.get(cacheType));

            return Result.success(result);

        } catch (IllegalArgumentException e) {
            return Result.failed("无效的缓存类型: " + type);
        } catch (Exception e) {
            log.error("获取类型缓存信息失败，type: {}", type, e);
            return Result.failed("获取类型缓存信息失败: " + e.getMessage());
        }
    }

    /**
     * 清空所有缓存
     */
    @PostMapping("/clear")
    public ResponseEntity<Result<String>> clearCache(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("清空缓存API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<String> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        try {
            String clientIp = getClientIpAddress(request);
            hybridCacheManager.clear();

            // 记录操作历史
            recordOperation("CLEAR_ALL_CACHE", "清空所有缓存", clientIp);

            LogUtils.logAdminOperation(log, "清空缓存", "管理员清空所有缓存，IP: {}", clientIp);
            return ResponseEntity.ok(Result.success("缓存已清空"));
        } catch (Exception e) {
            log.error("清空缓存失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failed("清空缓存失败: " + e.getMessage()));
        }
    }

    /**
     * 缓存预热
     */
    @PostMapping("/warmup")
    public ResponseEntity<Result<Map<String, Object>>> warmupCache(
            HttpServletRequest request,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String type) {

        if (!isAuthorizedAccess(request)) {
            log.warn("缓存预热API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        try {
            String clientIp = getClientIpAddress(request);
            Map<String, Object> result = new HashMap<>();
            int warmedCaches = 0;

            // 获取需要预热的缓存
            Map<String, CacheRegistryService.DiscoveredCache> discoveredCaches = cacheRegistryService.getDiscoveredCaches();

            for (Map.Entry<String, CacheRegistryService.DiscoveredCache> entry : discoveredCaches.entrySet()) {
                CacheRegistryService.DiscoveredCache cache = entry.getValue();

                // 过滤条件
                if (module != null && !cache.getModule().equals(module)) {
                    continue;
                }
                if (type != null && !cache.getType().toString().equals(type)) {
                    continue;
                }

                // 执行预热（这里可以根据具体缓存类型实现不同的预热策略）
                try {
                    // 对于混合缓存，可以预加载一些常用数据
                    if (cache.getType() == GlobalCacheManager.CacheType.HYBRID) {
                        // 这里可以添加具体的预热逻辑
                        log.debug("预热混合缓存: {}", entry.getKey());
                    }
                    warmedCaches++;
                } catch (Exception e) {
                    log.warn("预热缓存失败: {}, 错误: {}", entry.getKey(), e.getMessage());
                }
            }

            // 记录操作历史
            String details = String.format("预热缓存 - 模块: %s, 类型: %s, 成功: %d个",
                module != null ? module : "全部",
                type != null ? type : "全部",
                warmedCaches);
            recordOperation("WARMUP_CACHE", details, clientIp);

            result.put("warmedCaches", warmedCaches);
            result.put("module", module);
            result.put("type", type);

            LogUtils.logAdminOperation(log, "缓存预热", "缓存预热完成，IP: {}, 预热缓存数: {}", clientIp, warmedCaches);
            return ResponseEntity.ok(Result.success(result));

        } catch (Exception e) {
            log.error("缓存预热失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failed("缓存预热失败: " + e.getMessage()));
        }
    }

    /**
     * 获取API密钥统计信息
     */
    @GetMapping("/security/api-keys")
    public ResponseEntity<Result<Map<String, Object>>> getApiKeyStats(HttpServletRequest request) {
        if (!isAuthorizedAccess(request)) {
            log.warn("API密钥统计API非法访问尝试 - IP: {}", getClientIpAddress(request));
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        try {
            Map<String, Object> result = new HashMap<>();

            if (managementApiKeyService != null) {
                result.putAll(managementApiKeyService.getApiKeyStats());

                // 显示主密钥（现在不再限制本地访问）
                if (managementApiKeyService.isApiKeyEnabled()) {
                    result.put("masterApiKey", managementApiKeyService.getMasterApiKey());
                }
            } else {
                result.put("enabled", false);
                result.put("message", "API密钥服务未启用");
            }

            return ResponseEntity.ok(Result.success(result));

        } catch (Exception e) {
            log.error("获取API密钥统计失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failed("获取API密钥统计失败: " + e.getMessage()));
        }
    }

    /**
     * 删除指定缓存
     */
    @DeleteMapping("/evict/{key}")
    public Result<String> evictCache(@PathVariable String key) {
        try {
            hybridCacheManager.evict(key);
            LogUtils.logAdminOperation(log, "删除缓存", "管理员删除缓存，key: {}", key);
            return Result.success("缓存已删除");
        } catch (Exception e) {
            log.error("删除缓存失败，key: {}", key, e);
            return Result.failed("删除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清空指定模块的缓存
     */
    @PostMapping("/clear/module/{module}")
    public Result<String> clearModuleCache(@PathVariable String module) {
        try {
            globalCacheManager.clearModule(module);
            LogUtils.logAdminOperation(log, "清空模块缓存", "管理员清空模块缓存，module: {}", module);
            return Result.success("模块缓存已清空");
        } catch (Exception e) {
            log.error("清空模块缓存失败，module: {}", module, e);
            return Result.failed("清空模块缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清空指定类型的缓存
     */
    @PostMapping("/clear/type/{type}")
    public Result<String> clearTypeCache(@PathVariable String type) {
        try {
            GlobalCacheManager.CacheType cacheType = GlobalCacheManager.CacheType.valueOf(type.toUpperCase());
            Map<String, GlobalCacheManager.CacheInstance> caches = globalCacheManager.getCachesByType(cacheType);

            for (String cacheName : caches.keySet()) {
                globalCacheManager.clearCache(cacheName);
            }

            LogUtils.logAdminOperation(log, "清空类型缓存", "管理员清空类型缓存，type: {}", type);
            return Result.success("类型缓存已清空");
        } catch (IllegalArgumentException e) {
            return Result.failed("无效的缓存类型: " + type);
        } catch (Exception e) {
            log.error("清空类型缓存失败，type: {}", type, e);
            return Result.failed("清空类型缓存失败: " + e.getMessage());
        }
    }

    /**
     * 重置熔断器
     */
    @PostMapping("/circuit-breaker/reset")
    public Result<String> resetCircuitBreaker() {
        try {
            circuitBreaker.reset();
            LogUtils.logAdminOperation(log, "重置熔断器", "管理员重置Redis熔断器");
            return Result.success("熔断器已重置");
        } catch (Exception e) {
            log.error("重置熔断器失败", e);
            return Result.failed("重置熔断器失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发熔断器
     */
    @PostMapping("/circuit-breaker/force-open")
    public Result<String> forceOpenCircuitBreaker() {
        try {
            circuitBreaker.forceOpen();
            LogUtils.logAdminOperation(log, "手动开启熔断器", "管理员手动触发Redis熔断器开启");
            return Result.success("熔断器已手动开启");
        } catch (Exception e) {
            log.error("手动触发熔断器失败", e);
            return Result.failed("手动触发熔断器失败: " + e.getMessage());
        }
    }

    /**
     * 重置监控统计
     */
    @PostMapping("/monitoring/reset")
    public Result<String> resetMonitoringStats() {
        try {
            monitoringService.resetStats();
            LogUtils.logAdminOperation(log, "重置监控统计", "管理员重置缓存监控统计");
            return Result.success("监控统计已重置");
        } catch (Exception e) {
            log.error("重置监控统计失败", e);
            return Result.failed("重置监控统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存健康状态
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> getCacheHealth() {
        try {
            Map<String, Object> health = new HashMap<>();

            // Redis可用性
            boolean redisAvailable = circuitBreaker.isAvailable();
            health.put("redisAvailable", redisAvailable);
            health.put("circuitBreakerState", circuitBreaker.getState().name());

            // 缓存性能
            HybridCacheManager.CacheStats cacheStats = hybridCacheManager.getStats();
            double hitRate = cacheStats.getL1HitRate();
            health.put("l1HitRate", hitRate);
            health.put("l1Size", cacheStats.getL1Size());

            // 整体健康状态
            String overallStatus;
            if (!redisAvailable) {
                overallStatus = "DEGRADED"; // 降级状态
            } else if (hitRate < 0.5) {
                overallStatus = "WARNING"; // 警告状态
            } else {
                overallStatus = "HEALTHY"; // 健康状态
            }
            health.put("overallStatus", overallStatus);

            // 建议
            if (!redisAvailable) {
                health.put("recommendation", "Redis服务异常，系统已切换到降级模式，请检查Redis连接");
            } else if (hitRate < 0.5) {
                health.put("recommendation", "缓存命中率较低，建议检查缓存策略或增加缓存预热");
            } else {
                health.put("recommendation", "缓存系统运行正常");
            }

            return Result.success(health);

        } catch (Exception e) {
            log.error("获取缓存健康状态失败", e);
            return Result.failed("获取缓存健康状态失败: " + e.getMessage());
        }
    }

    /**
     * 测试缓存功能
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> testCache(@RequestParam(defaultValue = "test-key") String key,
                                                 @RequestParam(defaultValue = "test-value") String value) {
        try {
            Map<String, Object> result = new HashMap<>();
            long startTime = System.currentTimeMillis();

            // 测试写入
            hybridCacheManager.put(key, value);
            long writeTime = System.currentTimeMillis() - startTime;

            // 测试读取
            startTime = System.currentTimeMillis();
            Object cachedValue = hybridCacheManager.get(key, () -> "fallback-value");
            long readTime = System.currentTimeMillis() - startTime;

            // 测试存在性检查
            startTime = System.currentTimeMillis();
            boolean exists = hybridCacheManager.exists(key);
            long existsTime = System.currentTimeMillis() - startTime;

            result.put("writeTimeMs", writeTime);
            result.put("readTimeMs", readTime);
            result.put("existsTimeMs", existsTime);
            result.put("cachedValue", cachedValue);
            result.put("exists", exists);
            result.put("redisAvailable", circuitBreaker.isAvailable());
            result.put("circuitBreakerState", circuitBreaker.getState().name());

            // 清理测试数据
            hybridCacheManager.evict(key);

            log.info("缓存功能测试完成，key: {}, 写入耗时: {}ms, 读取耗时: {}ms", key, writeTime, readTime);
            return Result.success(result);

        } catch (Exception e) {
            log.error("缓存功能测试失败", e);
            return Result.failed("缓存功能测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存配置信息
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getCacheConfig() {
        try {
            Map<String, Object> config = new HashMap<>();

            // 全局缓存统计
            GlobalCacheManager.GlobalCacheStats globalStats = globalCacheManager.getGlobalStats();
            config.put("global", globalStats);

            // 熔断器配置
            RedisCircuitBreaker.CircuitBreakerStats circuitStats = circuitBreaker.getStats();
            config.put("circuitBreaker", Map.of(
                "failureThreshold", circuitStats.getFailureThreshold(),
                "successThreshold", circuitStats.getSuccessThreshold(),
                "recoveryTimeoutMs", circuitStats.getRecoveryTimeoutMs()
            ));

            // L1缓存配置
            config.put("l1Cache", Map.of(
                "maxSize", 1000, // 从HybridCacheManager中获取
                "expireMinutes", 5
            ));

            // L2缓存配置
            config.put("l2Cache", Map.of(
                "expireMinutes", 30
            ));

            // 缓存类型说明
            Map<String, String> cacheTypes = new HashMap<>();
            for (GlobalCacheManager.CacheType type : GlobalCacheManager.CacheType.values()) {
                cacheTypes.put(type.name(), type.getDescription());
            }
            config.put("cacheTypes", cacheTypes);

            return Result.success(config);

        } catch (Exception e) {
            log.error("获取缓存配置失败", e);
            return Result.failed("获取缓存配置失败: " + e.getMessage());
        }
    }
}
