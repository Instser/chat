package com.instser.chat.admin.service;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.instser.chat.admin.audit.AdminAuditPublisher;
import com.instser.chat.admin.common.AdminErrorCodes;
import com.instser.chat.admin.dto.AdminReasonRequest;
import com.instser.chat.common.result.Result;
import com.instser.chat.component.ChatLimitCacheManager;
import com.instser.chat.controller.OpsController;
import com.instser.chat.dto.ErrorAnalysisRequest;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.metrics.AiCallWindowStore;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PaymentAnalyticsService;
import com.instser.chat.service.UserService;
import com.instser.chat.util.LogLevelManager;
import com.instser.chat.vo.PaymentAnalyticsVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOpsService {

    /**
     * 仪表盘 AI 探测预算：
     * - 单模型短超时
     * - 全部模型并行探测
     * - 总预算到点立即返回，失败模型只记结果，不拖垮 overview/后台
     */
    /** 仪表盘总预算：到点返回，失败模型只记结果。手动测试用更宽松超时见 AdminModelService。 */
    private static final long AI_PROBE_TOTAL_BUDGET_MS = 8000L;
    private static final int AI_PROBE_CONNECT_TIMEOUT_MS = 3000;
    private static final int AI_PROBE_READ_TIMEOUT_MS = 6000;
    private static final int AI_PROBE_MAX_MODELS = 20;

    private static final Set<String> LOGGER_WHITELIST = Set.of(
            "root",
            "com.instser.chat",
            "com.instser.chat.service.serviceimpl.ChatServiceImpl",
            "com.instser.chat.service.serviceimpl.PaymentServiceImpl",
            "com.instser.chat.service.serviceimpl.UserServiceImpl",
            "com.instser.chat.service.serviceimpl.FileUploadServiceImpl",
            "com.instser.chat.service.serviceimpl.GeminiNetworkServiceImpl",
            "com.instser.chat.dao",
            "org.springframework",
            "com.alibaba.druid",
            "org.apache.ibatis.logging.jdbc",
            "java.sql.Connection",
            "java.sql.Statement",
            "java.sql.PreparedStatement",
            "java.sql.ResultSet"
    );

    private final BuildProperties buildProperties;
    private final ApplicationContext applicationContext;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final AiModelService aiModelService;
    private final UserService userService;
    private final OrderService orderService;
    private final PaymentAnalyticsService paymentAnalyticsService;
    private final ChatLimitCacheManager chatLimitCacheManager;
    private final LogLevelManager logLevelManager;
    private final RestTemplate restTemplate;
    private final AdminAuditPublisher adminAuditPublisher;
    private final MeterRegistry meterRegistry;
    private final AiCallWindowStore aiCallWindowStore;

    public Map<String, Object> version() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "jinyu-chat");
        info.put("version", buildProperties.getVersion());
        info.put("groupId", buildProperties.getGroup());
        info.put("artifactId", buildProperties.getArtifact());
        info.put("buildTime", buildProperties.getTime() != null ? buildProperties.getTime().toString() : null);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("springBootVersion", SpringBootVersion.getVersion());
        info.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        info.put("activeProfiles", Arrays.asList(applicationContext.getEnvironment().getActiveProfiles()));
        return info;
    }

    public Map<String, Object> health() {
        // 仪表盘/健康接口可探测 AI；运维监控页默认不自动探测，见 healthLite()
        return buildHealth(true);
    }

    /** 不含外网 AI 探测的轻量健康，避免监控自动刷新打爆上游。 */
    public Map<String, Object> healthLite() {
        return buildHealth(false);
    }

    private Map<String, Object> buildHealth(boolean probeAi) {
        Map<String, Object> health = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("application", component("UP", ManagementFactory.getRuntimeMXBean().getUptime(), null));
        components.put("database", checkDatabase());
        components.put("redis", checkRedis());
        if (probeAi) {
            components.put("aiApi", checkAiApi());
        } else {
            components.put("aiApi", Map.of(
                    "status", "SKIPPED",
                    "message", "监控页默认跳过 AI 探测，请手动点击「探测模型」",
                    "probe", "manual-only"
            ));
        }

        // SKIPPED 不视为故障；DOWN/DEGRADED 才降级
        boolean degraded = components.values().stream().anyMatch(c -> {
            Object st = ((Map<?, ?>) c).get("status");
            return "DOWN".equals(st) || "DEGRADED".equals(st);
        });
        boolean allUp = components.values().stream().allMatch(c -> {
            Object st = ((Map<?, ?>) c).get("status");
            return "UP".equals(st) || "SKIPPED".equals(st);
        });
        health.put("status", degraded ? "DEGRADED" : (allUp ? "UP" : "DEGRADED"));
        health.put("components", components);
        health.put("checkedAt", Instant.now().toString());
        return health;
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        long userTotal = userService.count();
        long adminTotal = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, "admin"));
        long enabledUsers = userService.count(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        long orderTotal = orderService.count();
        long paidOrders = orderService.count(new LambdaQueryWrapper<UserOrder>().eq(UserOrder::getPaymentStatus, 1));
        long pendingOrders = orderService.count(new LambdaQueryWrapper<UserOrder>().eq(UserOrder::getPaymentStatus, 0));

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(7);
        PaymentAnalyticsVO analytics = paymentAnalyticsService.getPaymentOverview(start, end);

        data.put("users", Map.of(
                "total", userTotal,
                "enabled", enabledUsers,
                "admins", adminTotal
        ));
        data.put("orders", Map.of(
                "total", orderTotal,
                "paid", paidOrders,
                "pending", pendingOrders
        ));
        data.put("paymentLast7Days", analytics);
        data.put("health", health());
        data.put("generatedAt", Instant.now().toString());
        return data;
    }

    public Map<String, Object> metricsSummary() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("jvmUptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        metrics.put("heapUsedMb", (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()) / (1024 * 1024));
        metrics.put("heapMaxMb", (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax()) / (1024 * 1024));
        metrics.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        if (dataSource instanceof DruidDataSource ds) {
            metrics.put("dbActive", ds.getActiveCount());
            metrics.put("dbMaxActive", ds.getMaxActive());
            metrics.put("dbPoolingCount", ds.getPoolingCount());
        }
        metrics.put("note", "仅返回白名单运行指标，不含用户/订单标签");
        return metrics;
    }

    /**
     * 管理后台 Grafana 风格监控汇总（快照，非长期时序库）。
     * 覆盖：AI 调用 / HTTP / 系统资源 / 支付订单 / 模型连通。
     * 模型探测失败只影响 models 块，不拖垮整接口其它块。
     *
     * @param range AI 时间窗：1d / 7d / 30d / all（进程累计 Micrometer）
     */
    public Map<String, Object> monitoring(String range) {
        return monitoring(range, false);
    }

    /**
     * @param probeModels true 时才对外网模型做真实可聊探测；默认 false，由前端手动触发。
     */
    public Map<String, Object> monitoring(String range, boolean probeModels) {
        String normalized = normalizeRange(range);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put("range", normalized);
        data.put("supportedRanges", List.of("1d", "7d", "30d", "all"));
        data.put("probeModels", probeModels);
        data.put("note", "AI 指标为进程内窗口；模型连通默认手动探测，避免自动刷新打上游。");

        // 1) 系统资源
        data.put("system", buildSystemMetrics());

        // 2) AI 调用（窗口 or 进程累计）
        data.put("ai", buildAiMetrics(normalized));

        // 3) HTTP/API（已排除 monitoring 自监控路径）
        data.put("http", buildHttpMetrics());

        // 4) 支付/订单（窗口与 AI 对齐：1d/7d/30d；all 仍用 7d 业务统计）
        data.put("payment", buildPaymentMetrics(paymentDaysForRange(normalized)));

        // 5) 模型连通：默认不探测；手动时才跑 checkAiApi
        if (probeModels) {
            data.put("models", probeModelsNow());
        } else {
            data.put("models", idleModelsProbePlaceholder());
        }

        // 健康总览：监控页用轻量版，避免自动刷新二次探测 AI
        try {
            data.put("health", healthLite());
        } catch (Exception ex) {
            data.put("health", Map.of("status", "DEGRADED", "message", sanitize(ex.getMessage())));
        }
        return data;
    }

    /** 仅模型探测，供运维页手动按钮调用。 */
    public Map<String, Object> probeModelsNow() {
        try {
            return checkAiApi();
        } catch (Exception ex) {
            log.warn("manual models probe failed: {}", sanitize(ex.getMessage()));
            return Map.of(
                    "status", "DOWN",
                    "message", "模型探测异常: " + sanitize(ex.getMessage()),
                    "models", List.of(),
                    "probe", "manual"
            );
        }
    }

    private Map<String, Object> idleModelsProbePlaceholder() {
        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("status", "IDLE");
        idle.put("message", "尚未探测。点击「探测模型」发起真实可聊检测（不会随自动刷新执行）");
        idle.put("models", List.of());
        idle.put("probe", "manual-only");
        idle.put("probedModels", 0);
        return idle;
    }

    private Map<String, Object> buildSystemMetrics() {
        Map<String, Object> system = new LinkedHashMap<>();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memory.getNonHeapMemoryUsage().getUsed();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        jvm.put("heapUsedMb", heapUsed / (1024 * 1024));
        jvm.put("heapMaxMb", heapMax > 0 ? heapMax / (1024 * 1024) : -1);
        jvm.put("heapUsageRatio", heapMax > 0 ? round4((double) heapUsed / heapMax) : null);
        jvm.put("nonHeapUsedMb", nonHeapUsed / (1024 * 1024));
        jvm.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("peakThreadCount", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        jvm.put("daemonThreadCount", ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        system.put("jvm", jvm);

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("availableProcessors", os.getAvailableProcessors());
        cpu.put("systemLoadAverage", os.getSystemLoadAverage());
        // 尝试读取 process.cpu.usage / system.cpu.usage（若 Micrometer 已注册）
        cpu.put("processCpuUsage", gaugeValue("process.cpu.usage"));
        cpu.put("systemCpuUsage", gaugeValue("system.cpu.usage"));
        system.put("cpu", cpu);

        Map<String, Object> memoryGauge = new LinkedHashMap<>();
        memoryGauge.put("jvmMemoryUsedBytes", sumGaugeByName("jvm.memory.used"));
        memoryGauge.put("jvmMemoryMaxBytes", sumGaugeByName("jvm.memory.max"));
        system.put("memoryMeters", memoryGauge);

        if (dataSource instanceof DruidDataSource ds) {
            Map<String, Object> db = new LinkedHashMap<>();
            db.put("active", ds.getActiveCount());
            db.put("maxActive", ds.getMaxActive());
            db.put("pooling", ds.getPoolingCount());
            db.put("waitThreadCount", ds.getWaitThreadCount());
            system.put("dbPool", db);
        } else {
            system.put("dbPool", Map.of("note", "非 Druid 数据源，无池化细节"));
        }

        // 图表友好：资源柱状序列
        List<Map<String, Object>> resourceBars = new ArrayList<>();
        resourceBars.add(metricBar("heapUsedMb", jvm.get("heapUsedMb")));
        resourceBars.add(metricBar("heapMaxMb", jvm.get("heapMaxMb")));
        resourceBars.add(metricBar("threads", jvm.get("threadCount")));
        resourceBars.add(metricBar("cpuLoad*100", os.getSystemLoadAverage() >= 0
                ? round4(os.getSystemLoadAverage() * 100) : 0));
        if (dataSource instanceof DruidDataSource ds2) {
            resourceBars.add(metricBar("dbActive", ds2.getActiveCount()));
            resourceBars.add(metricBar("dbPooling", ds2.getPoolingCount()));
        }
        system.put("resourceBars", resourceBars);
        return system;
    }

    private Map<String, Object> buildAiMetrics(String range) {
        if (!"all".equals(range)) {
            return buildAiWindowMetrics(range);
        }
        return buildAiProcessLifetimeMetrics();
    }

    /** 1d/7d/30d：进程内小时桶窗口。 */
    private Map<String, Object> buildAiWindowMetrics(String range) {
        int hours = hoursForRange(range);
        AiCallWindowStore.Snapshot snap = aiCallWindowStore.snapshot(hours);

        Map<String, Object> ai = new LinkedHashMap<>();
        Map<String, Object> totals = new LinkedHashMap<>();
        long success = ((Number) snap.totals.getOrDefault("successCount", 0L)).longValue();
        long failed = ((Number) snap.totals.getOrDefault("failedCount", 0L)).longValue();
        totals.put("callsTotal", success + failed);
        totals.put("callsSuccess", success);
        totals.put("callsFailed", failed);
        totals.put("active", gaugeValue("ai_calls_active"));
        // token 仍只有进程累计（窗口未单独记 token）
        totals.put("promptTokens", round0(counterTotal("ai_token_consumption_total", Map.of("type", "prompt"))));
        totals.put("completionTokens", round0(counterTotal("ai_token_consumption_total", Map.of("type", "completion"))));
        double denom = success + failed;
        totals.put("successRate", denom > 0 ? round4(success / denom) : null);
        totals.put("ttftMeanMs", snap.totals.get("ttftMeanMs"));
        totals.put("durationMeanMs", snap.totals.get("durationMeanMs"));
        ai.put("totals", totals);
        ai.put("byModel", snap.byModel);
        ai.put("timeline", snap.timeline);
        ai.put("window", snap.meta);
        ai.put("source", "process-window");
        ai.put("range", range);

        List<Map<String, Object>> statusPie = new ArrayList<>();
        statusPie.add(Map.of("name", "success", "value", success));
        statusPie.add(Map.of("name", "failed", "value", failed));
        ai.put("statusPie", statusPie);

        List<Map<String, Object>> modelBars = snap.byModel.stream()
                .filter(m -> {
                    Object model = m.get("model");
                    return model != null && !"unknown".equals(String.valueOf(model)) && !String.valueOf(model).isBlank();
                })
                .map(m -> {
                    Map<String, Object> bar = new LinkedHashMap<>();
                    bar.put("name", String.valueOf(m.get("model")));
                    bar.put("calls", ((Number) m.getOrDefault("successCount", 0L)).longValue()
                            + ((Number) m.getOrDefault("failedCount", 0L)).longValue());
                    bar.put("ttftMeanMs", m.get("ttftMeanMs"));
                    bar.put("durationMeanMs", m.get("durationMeanMs"));
                    return bar;
                }).collect(Collectors.toList());
        ai.put("modelBars", modelBars);
        // 窗口模式失败明细仍回落 Micrometer（无按窗失败分类存储）
        ai.put("failures", buildMicrometerFailures());
        return ai;
    }

    /** all：Micrometer 进程累计（重启清零）。 */
    private Map<String, Object> buildAiProcessLifetimeMetrics() {
        Map<String, Object> ai = new LinkedHashMap<>();
        double total = counterTotal("ai_calls_total", null);
        double success = counterTotal("ai_calls_total", Map.of("status", "success"));
        double failed = counterTotal("ai_calls_total", Map.of("status", "failed"));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("callsTotal", round0(Math.max(total, success + failed)));
        totals.put("callsSuccess", round0(success));
        totals.put("callsFailed", round0(failed));
        totals.put("active", gaugeValue("ai_calls_active"));
        totals.put("promptTokens", round0(counterTotal("ai_token_consumption_total", Map.of("type", "prompt"))));
        totals.put("completionTokens", round0(counterTotal("ai_token_consumption_total", Map.of("type", "completion"))));
        double denom = success + failed;
        totals.put("successRate", denom > 0 ? round4(success / denom) : null);
        ai.put("totals", totals);
        ai.put("source", "process-lifetime");
        ai.put("range", "all");
        ai.put("window", Map.of(
                "bucketUnit", "process",
                "note", "Micrometer 进程累计，无时间窗；重启清空"
        ));
        ai.put("timeline", List.of());

        Map<String, Map<String, Object>> byModel = new LinkedHashMap<>();
        for (Meter meter : meterRegistry.find("ai_call_duration_seconds").meters()) {
            if (!(meter instanceof Timer timer)) {
                continue;
            }
            String model = tagValue(meter, "model");
            String status = tagValue(meter, "status");
            Map<String, Object> row = byModel.computeIfAbsent(model, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("model", k);
                m.put("durationCount", 0L);
                m.put("durationMeanMs", 0d);
                m.put("successCount", 0L);
                m.put("failedCount", 0L);
                m.put("ttftMeanMs", null);
                m.put("ttftCount", 0L);
                return m;
            });
            long count = timer.count();
            row.put("durationCount", ((Number) row.get("durationCount")).longValue() + count);
            if ("success".equals(status)) {
                row.put("successCount", ((Number) row.get("successCount")).longValue() + count);
            } else if ("failed".equals(status)) {
                row.put("failedCount", ((Number) row.get("failedCount")).longValue() + count);
            }
            if (count > 0) {
                double prevMean = ((Number) row.get("durationMeanMs")).doubleValue();
                long prevCount = ((Number) row.get("durationCount")).longValue() - count;
                double meanMs = timer.mean(TimeUnit.MILLISECONDS);
                double merged = prevCount <= 0 ? meanMs : ((prevMean * prevCount) + meanMs * count) / (prevCount + count);
                row.put("durationMeanMs", round2(merged));
            }
        }
        for (Meter meter : meterRegistry.find("ai_ttft_seconds").meters()) {
            if (!(meter instanceof Timer timer)) {
                continue;
            }
            String model = tagValue(meter, "model");
            Map<String, Object> row = byModel.computeIfAbsent(model, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("model", k);
                m.put("durationCount", 0L);
                m.put("durationMeanMs", 0d);
                m.put("successCount", 0L);
                m.put("failedCount", 0L);
                m.put("ttftMeanMs", null);
                m.put("ttftCount", 0L);
                return m;
            });
            long count = timer.count();
            if (count > 0) {
                row.put("ttftCount", count);
                row.put("ttftMeanMs", round2(timer.mean(TimeUnit.MILLISECONDS)));
            }
        }
        List<Map<String, Object>> modelRows = byModel.values().stream()
                .sorted((a, b) -> Long.compare(
                        ((Number) b.getOrDefault("durationCount", 0L)).longValue(),
                        ((Number) a.getOrDefault("durationCount", 0L)).longValue()))
                .collect(Collectors.toList());
        ai.put("byModel", modelRows);
        ai.put("failures", buildMicrometerFailures());

        List<Map<String, Object>> statusPie = new ArrayList<>();
        statusPie.add(Map.of("name", "success", "value", totals.get("callsSuccess")));
        statusPie.add(Map.of("name", "failed", "value", totals.get("callsFailed")));
        ai.put("statusPie", statusPie);

        List<Map<String, Object>> modelBars = modelRows.stream()
                .filter(m -> {
                    Object model = m.get("model");
                    return model != null && !"unknown".equals(String.valueOf(model)) && !String.valueOf(model).isBlank();
                })
                .map(m -> {
                    Map<String, Object> bar = new LinkedHashMap<>();
                    bar.put("name", String.valueOf(m.get("model")));
                    bar.put("calls", ((Number) m.getOrDefault("successCount", 0L)).longValue()
                            + ((Number) m.getOrDefault("failedCount", 0L)).longValue());
                    bar.put("ttftMeanMs", m.get("ttftMeanMs"));
                    bar.put("durationMeanMs", m.get("durationMeanMs"));
                    return bar;
                }).collect(Collectors.toList());
        ai.put("modelBars", modelBars);
        return ai;
    }

    private List<Map<String, Object>> buildMicrometerFailures() {
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Meter meter : meterRegistry.find("ai_calls_failed_total").meters()) {
            if (!(meter instanceof Counter counter)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("model", tagValue(meter, "model"));
            row.put("errorType", tagValue(meter, "error_type"));
            row.put("count", round0(counter.count()));
            failures.add(row);
        }
        failures.sort((a, b) -> Double.compare(
                ((Number) b.get("count")).doubleValue(),
                ((Number) a.get("count")).doubleValue()));
        return failures;
    }

    private static String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "7d";
        }
        String r = range.trim().toLowerCase(Locale.ROOT);
        return switch (r) {
            case "1d", "24h", "day", "today" -> "1d";
            case "7d", "week" -> "7d";
            case "30d", "month" -> "30d";
            case "all", "lifetime", "process", "long" -> "all";
            default -> "7d";
        };
    }

    private static int hoursForRange(String range) {
        return switch (range) {
            case "1d" -> 24;
            case "30d" -> 24 * 30;
            case "all" -> AiCallWindowStore.MAX_HOURS;
            default -> 24 * 7; // 7d
        };
    }

    private static int paymentDaysForRange(String range) {
        return switch (range) {
            case "1d" -> 1;
            case "30d" -> 30;
            case "all" -> 7; // 支付无进程窗，all 仍展示近 7 日业务统计
            default -> 7;
        };
    }

    /** 监控接口自身路径，不进入 HTTP Top 统计。 */
    static boolean isMonitoringSelfPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("/admin/ops/monitoring")
                || p.endsWith("/ops/monitoring")
                || p.contains("/admin/ops/monitoring/models-probe");
    }

    private Map<String, Object> buildHttpMetrics() {
        Map<String, Object> http = new LinkedHashMap<>();
        List<Map<String, Object>> requests = new ArrayList<>();
        long totalReq = 0;
        long errorReq = 0;
        Map<String, Long> pathCounts = new LinkedHashMap<>();
        Map<String, Long> statusCounts = new LinkedHashMap<>();

        for (Meter meter : meterRegistry.find("api_requests_total").meters()) {
            if (!(meter instanceof Counter counter)) {
                continue;
            }
            String path = tagValue(meter, "path");
            // 不把监控页自身请求算进 Top 路径，避免自激刷榜
            if (isMonitoringSelfPath(path)) {
                continue;
            }
            long c = Math.round(counter.count());
            String method = tagValue(meter, "method");
            String status = tagValue(meter, "status");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", method);
            row.put("path", path);
            row.put("status", status);
            row.put("count", c);
            requests.add(row);
            totalReq += c;
            if (status != null && status.startsWith("5")) {
                errorReq += c;
            }
            String pathKey = method + " " + path;
            pathCounts.merge(pathKey, c, Long::sum);
            statusCounts.merge(status == null ? "unknown" : status, c, Long::sum);
        }
        requests.sort((a, b) -> Long.compare(
                ((Number) b.get("count")).longValue(),
                ((Number) a.get("count")).longValue()));
        http.put("requests", requests.stream().limit(50).collect(Collectors.toList()));
        http.put("totalRequests", totalReq);
        http.put("error5xx", errorReq);
        http.put("errorRate", totalReq > 0 ? round4((double) errorReq / totalReq) : null);

        List<Map<String, Object>> topPaths = pathCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(15)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
        http.put("topPaths", topPaths);

        List<Map<String, Object>> statusBars = statusCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
        http.put("statusBars", statusBars);

        List<Map<String, Object>> exceptions = new ArrayList<>();
        for (Meter meter : meterRegistry.find("api_exceptions_total").meters()) {
            if (!(meter instanceof Counter counter)) {
                continue;
            }
            String path = tagValue(meter, "path");
            if (isMonitoringSelfPath(path)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", tagValue(meter, "method"));
            row.put("path", path);
            row.put("exceptionType", tagValue(meter, "exception_type"));
            row.put("count", round0(counter.count()));
            exceptions.add(row);
        }
        exceptions.sort((a, b) -> Double.compare(
                ((Number) b.get("count")).doubleValue(),
                ((Number) a.get("count")).doubleValue()));
        http.put("exceptions", exceptions.stream().limit(30).collect(Collectors.toList()));

        // Spring MVC http.server.requests 延迟（若启用）
        List<Map<String, Object>> serverLatency = new ArrayList<>();
        for (Meter meter : meterRegistry.find("http.server.requests").meters()) {
            if (!(meter instanceof Timer timer) || timer.count() == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uri", tagValue(meter, "uri"));
            row.put("method", tagValue(meter, "method"));
            row.put("status", tagValue(meter, "status"));
            row.put("outcome", tagValue(meter, "outcome"));
            row.put("count", timer.count());
            row.put("meanMs", round2(timer.mean(TimeUnit.MILLISECONDS)));
            row.put("maxMs", round2(timer.max(TimeUnit.MILLISECONDS)));
            serverLatency.add(row);
        }
        serverLatency.sort((a, b) -> Long.compare(
                ((Number) b.get("count")).longValue(),
                ((Number) a.get("count")).longValue()));
        http.put("serverLatency", serverLatency.stream().limit(20).collect(Collectors.toList()));
        return http;
    }

    private Map<String, Object> buildPaymentMetrics() {
        return buildPaymentMetrics(7);
    }

    private Map<String, Object> buildPaymentMetrics(int days) {
        int windowDays = Math.max(1, Math.min(days, 90));
        Map<String, Object> payment = new LinkedHashMap<>();
        long orderTotal = orderService.count();
        long paidOrders = orderService.count(new LambdaQueryWrapper<UserOrder>().eq(UserOrder::getPaymentStatus, 1));
        long pendingOrders = orderService.count(new LambdaQueryWrapper<UserOrder>().eq(UserOrder::getPaymentStatus, 0));
        payment.put("orders", Map.of(
                "total", orderTotal,
                "paid", paidOrders,
                "pending", pendingOrders
        ));

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(windowDays);
        payment.put("windowDays", windowDays);
        PaymentAnalyticsVO analytics;
        try {
            analytics = paymentAnalyticsService.getPaymentOverview(start, end);
        } catch (Exception ex) {
            log.warn("payment analytics for monitoring failed: {}", sanitize(ex.getMessage()));
            payment.put("last7Days", Map.of("error", sanitize(ex.getMessage())));
            payment.put("dailyRevenue", List.of());
            payment.put("paymentMethods", List.of());
            payment.put("orderStatusPie", List.of(
                    Map.of("name", "paid", "value", paidOrders),
                    Map.of("name", "pending", "value", pendingOrders)
            ));
            return payment;
        }

        Map<String, Object> lastWindow = new LinkedHashMap<>();
        lastWindow.put("startDate", String.valueOf(analytics.getStartDate()));
        lastWindow.put("endDate", String.valueOf(analytics.getEndDate()));
        lastWindow.put("days", windowDays);
        lastWindow.put("totalOrders", analytics.getTotalOrders());
        lastWindow.put("successfulOrders", analytics.getSuccessfulOrders());
        lastWindow.put("canceledOrders", analytics.getCanceledOrders());
        lastWindow.put("refundedOrders", analytics.getRefundedOrders());
        lastWindow.put("successRate", analytics.getSuccessRate());
        lastWindow.put("totalRevenue", analytics.getTotalRevenue());
        lastWindow.put("averageOrderAmount", analytics.getAverageOrderAmount());
        payment.put("last7Days", lastWindow); // 兼容旧前端字段名
        payment.put("period", lastWindow);

        List<Map<String, Object>> daily = new ArrayList<>();
        if (analytics.getDailyRevenueStats() != null) {
            analytics.getDailyRevenueStats().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("date", e.getKey() == null ? null : e.getKey().toString());
                        row.put("revenue", e.getValue() == null ? BigDecimal.ZERO : e.getValue());
                        daily.add(row);
                    });
        }
        payment.put("dailyRevenue", daily);

        List<Map<String, Object>> methods = new ArrayList<>();
        if (analytics.getPaymentMethodStats() != null) {
            analytics.getPaymentMethodStats().forEach((k, v) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", k);
                row.put("value", v == null ? 0L : v);
                methods.add(row);
            });
        }
        payment.put("paymentMethods", methods);

        List<Map<String, Object>> statusPie = new ArrayList<>();
        statusPie.add(Map.of("name", "paid", "value", paidOrders));
        statusPie.add(Map.of("name", "pending", "value", pendingOrders));
        if (analytics.getCanceledOrders() != null) {
            statusPie.add(Map.of("name", "canceled7d", "value", analytics.getCanceledOrders()));
        }
        payment.put("orderStatusPie", statusPie);
        return payment;
    }

    private double counterTotal(String name, Map<String, String> tags) {
        double sum = 0;
        for (Meter meter : meterRegistry.find(name).meters()) {
            if (!(meter instanceof Counter counter)) {
                continue;
            }
            if (tags != null && !tags.isEmpty()) {
                boolean match = true;
                for (Map.Entry<String, String> e : tags.entrySet()) {
                    if (!e.getValue().equals(tagValue(meter, e.getKey()))) {
                        match = false;
                        break;
                    }
                }
                if (!match) {
                    continue;
                }
            } else if (tags == null) {
                // 仅无 tag 的 counter（避免 success/failed 重复计入）
                if (!meter.getId().getTags().isEmpty()) {
                    continue;
                }
            }
            sum += counter.count();
        }
        return sum;
    }

    private Double gaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        if (gauge == null) {
            return null;
        }
        return round4(gauge.value());
    }

    private Double sumGaugeByName(String name) {
        double sum = 0;
        boolean any = false;
        for (Meter meter : meterRegistry.find(name).meters()) {
            if (meter instanceof io.micrometer.core.instrument.Gauge g) {
                sum += g.value();
                any = true;
            }
        }
        return any ? round2(sum) : null;
    }

    private String tagValue(Meter meter, String key) {
        if (meter == null || key == null) {
            return "unknown";
        }
        String v = meter.getId().getTag(key);
        return v == null || v.isBlank() ? "unknown" : v;
    }

    private Map<String, Object> metricBar(String name, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private double round0(double v) {
        return Math.rint(v);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    public Map<String, Object> cacheSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("managedScopes", List.of(
                "user:account:*",
                "package:config:*",
                "model:permission:*",
                "user-model-usage"
        ));
        summary.put("note", "缓存清理仅允许限定前缀，禁止 FLUSHDB/FLUSHALL");
        return summary;
    }

    public Result<Map<String, Object>> evictUserPackageCache(AdminReasonRequest request) {
        chatLimitCacheManager.clearAllUserPackageCaches();
        adminAuditPublisher.success("CACHE_EVICT_ALL", "user-package-cache",
                "scope=all,reason=" + request.getReason());
        return Result.success(Map.of(
                "scope", "all-users-package-and-chat-cache",
                "reason", request.getReason()
        ), "缓存清理成功");
    }

    public Map<String, String> listLoggers() {
        return logLevelManager.getAllMainLoggerLevels();
    }

    public Result<String> updateLogger(String loggerName, String level, String reason) {
        if (!isLoggerAllowed(loggerName)) {
            adminAuditPublisher.failed("LOGGER_UPDATE", loggerName, "not in whitelist");
            return new Result<>(AdminErrorCodes.OPS_REJECTED, "Logger 不在白名单");
        }
        boolean ok = logLevelManager.setLevel(loggerName, level);
        if (!ok) {
            adminAuditPublisher.failed("LOGGER_UPDATE", loggerName, "level=" + level);
            return Result.failed("设置日志级别失败");
        }
        adminAuditPublisher.success("LOGGER_UPDATE", loggerName, "level=" + level + ",reason=" + reason);
        return Result.success(logLevelManager.getCurrentLevel(loggerName), "日志级别已更新");
    }

    public Result<String> resetLogger(String loggerName, String reason) {
        if (!isLoggerAllowed(loggerName)) {
            return new Result<>(AdminErrorCodes.OPS_REJECTED, "Logger 不在白名单");
        }
        boolean ok = logLevelManager.resetLevel(loggerName);
        if (!ok) {
            return Result.failed("重置日志级别失败");
        }
        adminAuditPublisher.success("LOGGER_RESET", loggerName, "reason=" + reason);
        return Result.success(logLevelManager.getCurrentLevel(loggerName), "日志级别已重置");
    }

    public Result<Map<String, String>> applyPreset(String preset, String reason) {
        switch (preset == null ? "" : preset.toLowerCase(Locale.ROOT)) {
            case "development" -> logLevelManager.setDevelopmentLevels();
            case "production" -> logLevelManager.setProductionLevels();
            case "debugging" -> logLevelManager.setDebuggingLevels();
            default -> {
                return new Result<>(AdminErrorCodes.OPS_REJECTED, "不支持的预设: " + preset);
            }
        }
        adminAuditPublisher.success("LOGGER_PRESET", preset, "reason=" + reason);
        return Result.success(logLevelManager.getAllMainLoggerLevels(), "日志预设已应用");
    }

    public Result<Map<String, Object>> analyzeError(ErrorAnalysisRequest request) {
        String rawErrorLog = request.getErrorLog();
        String rawContext = request.getContextInfo();
        String sanitizedErrorLog = OpsController.sanitizeForAi(rawErrorLog);
        String sanitizedContext = OpsController.sanitizeForAi(rawContext);

        List<AiModel> availableModels = aiModelService.getAvailableModels();
        if (availableModels.isEmpty()) {
            return Result.failed("无可用的 AI 模型");
        }
        AiModel model = availableModels.get(0);
        try {
            Map<String, Object> analysis = callAiForAnalysis(sanitizedErrorLog, sanitizedContext, model);
            analysis.put("sanitized", true);
            analysis.put("inputLength", rawErrorLog != null ? rawErrorLog.length() : 0);
            adminAuditPublisher.success("AI_ANALYZE_ERROR", "ops",
                    "len=" + (rawErrorLog != null ? rawErrorLog.length() : 0));
            return Result.success(analysis);
        } catch (Exception ex) {
            log.error("管理员 AI 错误分析失败", ex);
            adminAuditPublisher.failed("AI_ANALYZE_ERROR", "ops", ex.getMessage());
            return Result.failed("AI 分析调用失败");
        }
    }

    private Map<String, Object> checkDatabase() {
        long start = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            Map<String, Object> result = component("UP", System.currentTimeMillis() - start, null);
            if (dataSource instanceof DruidDataSource ds) {
                result.put("activeConnections", ds.getActiveCount());
                result.put("maxConnections", ds.getMaxActive());
            }
            return result;
        } catch (Exception ex) {
            log.error("管理员数据库健康检查失败", ex);
            return component("DOWN", System.currentTimeMillis() - start, sanitize(ex.getMessage()));
        }
    }

    private Map<String, Object> checkRedis() {
        long start = System.currentTimeMillis();
        RedisConnection connection = null;
        try {
            connection = redisConnectionFactory.getConnection();
            String pong = connection.ping();
            Map<String, Object> result = component("UP", System.currentTimeMillis() - start, null);
            result.put("ping", pong);
            return result;
        } catch (Exception ex) {
            log.error("管理员 Redis 健康检查失败", ex);
            return component("DOWN", System.currentTimeMillis() - start, sanitize(ex.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * 对全部可用模型做短超时并行探测。
     * 任一模型超时/失败只记录在 models[] 中；总预算到期立即汇总返回，
     * 不拖垮 overview 与其它管理接口。
     */
    private Map<String, Object> checkAiApi() {
        long start = System.currentTimeMillis();
        try {
            List<AiModel> models = aiModelService.getAvailableModels();
            if (models == null || models.isEmpty()) {
                Map<String, Object> down = component("DOWN", elapsed(start), "无可用 AI 模型配置");
                down.put("availableModels", 0);
                down.put("upCount", 0);
                down.put("downCount", 0);
                down.put("timeoutCount", 0);
                down.put("models", List.of());
                down.put("probe", "all-models-live");
                return down;
            }

            List<AiModel> probeTargets = models.stream()
                    .filter(Objects::nonNull)
                    .limit(AI_PROBE_MAX_MODELS)
                    .toList();

            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>(probeTargets.size());
            for (AiModel model : probeTargets) {
                futures.add(CompletableFuture.supplyAsync(() -> probeSingleModel(model)));
            }

            List<Map<String, Object>> modelResults = new ArrayList<>(probeTargets.size());
            int upCount = 0;
            int downCount = 0;
            int timeoutCount = 0;

            try {
                CompletableFuture
                        .allOf(futures.toArray(CompletableFuture[]::new))
                        .get(AI_PROBE_TOTAL_BUDGET_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                // 总预算到点：未完成的记为超时，已完成的照常汇总
                log.warn("AI 全量探测达到总预算 {}ms，未完成模型记为超时", AI_PROBE_TOTAL_BUDGET_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException execution) {
                log.warn("AI 全量探测汇总异常: {}", execution.getMessage());
            }

            for (int i = 0; i < futures.size(); i++) {
                CompletableFuture<Map<String, Object>> future = futures.get(i);
                AiModel model = probeTargets.get(i);
                Map<String, Object> one;
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    try {
                        one = future.getNow(null);
                    } catch (Exception ex) {
                        one = null;
                    }
                } else if (future.isDone() && future.isCompletedExceptionally()) {
                    one = modelProbeResult(model, "DOWN", false, false, null,
                            sanitize(futureExceptionMessage(future)));
                    future.cancel(true);
                } else {
                    future.cancel(true);
                    one = modelProbeResult(model, "DOWN", false, true, null,
                            "探测超时(>" + AI_PROBE_TOTAL_BUDGET_MS + "ms 总预算)");
                }
                if (one == null) {
                    one = modelProbeResult(model, "DOWN", false, true, null, "探测无结果");
                }
                modelResults.add(one);
                if ("UP".equals(one.get("status"))) {
                    upCount++;
                } else {
                    downCount++;
                    if (Boolean.TRUE.equals(one.get("timedOut"))) {
                        timeoutCount++;
                    }
                }
            }

            String status;
            String message = null;
            if (upCount == modelResults.size()) {
                status = "UP";
            } else if (upCount == 0) {
                status = "DOWN";
                message = "全部模型探测失败/超时";
            } else {
                status = "DEGRADED";
                message = "部分模型不可用: up=" + upCount + ", down=" + downCount
                        + ", timeout=" + timeoutCount;
            }

            Map<String, Object> result = component(status, elapsed(start), message);
            result.put("availableModels", models.size());
            result.put("probedModels", modelResults.size());
            result.put("upCount", upCount);
            result.put("downCount", downCount);
            result.put("timeoutCount", timeoutCount);
            result.put("models", modelResults);
            result.put("probe", "all-models-live");
            result.put("budgetMs", AI_PROBE_TOTAL_BUDGET_MS);
            return result;
        } catch (Exception ex) {
            return component("DOWN", elapsed(start), sanitize(ex.getMessage()));
        }
    }

    /**
     * 单模型短超时“真实可聊”探测：要求模型只返回 1，并校验响应体内容。
     * HTTP 通但无 completion 文本只记该模型 DOWN，不抛到调用方。
     */
    private Map<String, Object> probeSingleModel(AiModel model) {
        long start = System.currentTimeMillis();
        if (model == null) {
            return modelProbeResult(null, "DOWN", false, false, null, "模型为空");
        }
        if (!StringUtils.hasText(model.getBaseUrl())
                || !StringUtils.hasText(model.getModelCode())
                || !StringUtils.hasText(model.getApiKey())) {
            Map<String, Object> r = modelProbeResult(model, "DOWN", false, false, null, "模型配置不完整");
            r.put("latencyMs", elapsed(start));
            return r;
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(AI_PROBE_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(AI_PROBE_READ_TIMEOUT_MS);
        RestTemplate probeClient = new RestTemplate(factory);

        String baseUrl = model.getBaseUrl().replaceAll("/+$", "");
        boolean geminiNative = ModelChatProbeSupport.isGeminiNativeEndpoint(baseUrl, model.getProvider());
        ModelChatProbeSupport.BuiltRequest probe = geminiNative
                ? ModelChatProbeSupport.buildGeminiNativeRequest(baseUrl, model.getModelCode(), model.getApiKey())
                : ModelChatProbeSupport.buildOpenAiChatRequest(baseUrl, model.getModelCode(), model.getApiKey());

        try {
            ResponseEntity<String> response = probeClient.exchange(
                    probe.url(),
                    probe.method(),
                    probe.entity(),
                    String.class
            );
            boolean httpOk = response.getStatusCode().is2xxSuccessful();
            ModelChatProbeSupport.ContentCheck check = ModelChatProbeSupport.evaluateResponse(
                    httpOk,
                    response.getStatusCode().value(),
                    response.getBody(),
                    probe.mode()
            );
            boolean ok = check.httpOk() && check.contentOk();
            Map<String, Object> r = modelProbeResult(
                    model,
                    ok ? "UP" : "DOWN",
                    httpOk,
                    false,
                    response.getStatusCode().value(),
                    check.message()
            );
            r.put("latencyMs", elapsed(start));
            r.put("probeMode", probe.mode());
            r.put("contentOk", check.contentOk());
            r.put("replyPreview", check.extractedText());
            return r;
        } catch (RestClientException ex) {
            boolean timedOut = isTimeoutException(ex);
            Map<String, Object> r = modelProbeResult(
                    model,
                    "DOWN",
                    false,
                    timedOut,
                    null,
                    (timedOut ? "探测超时: " : "探测失败: ") + sanitize(ex.getMessage())
            );
            r.put("latencyMs", elapsed(start));
            r.put("probeMode", probe.mode());
            r.put("contentOk", false);
            return r;
        } catch (Exception ex) {
            Map<String, Object> r = modelProbeResult(model, "DOWN", false, false, null,
                    "探测异常: " + sanitize(ex.getMessage()));
            r.put("latencyMs", elapsed(start));
            r.put("contentOk", false);
            return r;
        }
    }

    private Map<String, Object> modelProbeResult(AiModel model,
                                                 String status,
                                                 boolean reachable,
                                                 boolean timedOut,
                                                 Integer httpStatus,
                                                 String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (model != null) {
            map.put("id", model.getId() != null ? String.valueOf(model.getId()) : null);
            map.put("modelCode", model.getModelCode());
            map.put("modelName", model.getModelName());
            map.put("provider", model.getProvider());
        }
        map.put("status", status);
        map.put("reachable", reachable);
        map.put("timedOut", timedOut);
        if (httpStatus != null) {
            map.put("httpStatus", httpStatus);
        }
        if (StringUtils.hasText(message)) {
            map.put("message", message);
        }
        map.put("checkedAt", Instant.now().toString());
        return map;
    }

    private long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String name = cur.getClass().getName();
            String msg = cur.getMessage() == null ? "" : cur.getMessage().toLowerCase(Locale.ROOT);
            if (name.contains("Timeout") || msg.contains("timed out") || msg.contains("timeout")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private String futureExceptionMessage(CompletableFuture<?> future) {
        try {
            future.getNow(null);
            return "unknown";
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        }
    }

    private Map<String, Object> component(String status, long latencyMs, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("latencyMs", latencyMs);
        map.put("checkedAt", Instant.now().toString());
        if (StringUtils.hasText(message)) {
            map.put("message", message);
        }
        return map;
    }

    private boolean isLoggerAllowed(String loggerName) {
        return StringUtils.hasText(loggerName) && LOGGER_WHITELIST.contains(loggerName.trim());
    }

    private String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("//.*@", "//***@");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiForAnalysis(String errorLog, String contextInfo, AiModel model) throws Exception {
        String prompt = """
                你是一个 Java 应用运维专家。请分析以下异常日志并给出排查建议。
                注意：输入已做脱敏，***REDACTED*** 代表被遮蔽的敏感值。
                应用上下文：%s
                异常日志：%s
                请按以下 JSON 格式返回（只返回 JSON，不要其他内容）：
                {
                  "summary": "一句话描述异常原因",
                  "causes": ["可能原因1", "可能原因2"],
                  "steps": ["排查步骤1", "排查步骤2"],
                  "logQuery": "建议的 Loki/Grep 查询语句",
                  "confidence": 0.85
                }
                """.formatted(contextInfo != null ? contextInfo : "无", errorLog);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model.getModelCode());
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        requestBody.put("stream", false);
        requestBody.put("max_tokens", 1024);
        requestBody.put("temperature", 0.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(model.getApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity(model.getBaseUrl(),
                new HttpEntity<>(requestBody, headers), String.class);
        String responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("AI 返回空响应");
        }
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree(responseBody);
        String content = json.get("choices").get(0).get("message").get("content").asText();
        try {
            return mapper.readValue(content, Map.class);
        } catch (Exception ex) {
            return Map.of("raw", content, "error", "AI 返回格式解析失败");
        }
    }
}
