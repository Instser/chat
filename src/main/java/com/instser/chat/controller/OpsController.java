package com.instser.chat.controller;

import com.alibaba.druid.pool.DruidDataSource;
import com.instser.chat.common.result.Result;
import com.instser.chat.dto.ErrorAnalysisRequest;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.GeminiNetworkService;
import com.instser.chat.service.ManagementApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
@Tag(name = "运维管理", description = "运维排障与状态查询接口")
public class OpsController {

    private static final int MAX_ERROR_LOG_LENGTH = 8000;
    private static final int MAX_CONTEXT_INFO_LENGTH = 2000;

    /** 脱敏：常见密钥/Token/密码模式 */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|bearer|password|secret|passwd|private[_-]?key)" +
                    "\\s*([:=]|is)\\s*['\"]?([^'\\\"\\s,;]+)['\"]?"
    );
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*"
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    );

    private final ManagementApiKeyService managementApiKeyService;
    private final BuildProperties buildProperties;
    private final ApplicationContext ctx;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final GeminiNetworkService geminiNetworkService;
    private final AiModelService aiModelService;
    @Qualifier("aiRestTemplate")
    private final RestTemplate restTemplate;

    // ==================== /ops/version ====================

    @GetMapping("/version")
    @Operation(summary = "获取应用版本与构建信息")
    public ResponseEntity<Result<Map<String, Object>>> version(HttpServletRequest request) {
        if (!managementApiKeyService.validateRequest(request)) {
            return forbidden();
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "jinyu-chat");
        info.put("version", buildProperties.getVersion());
        info.put("groupId", buildProperties.getGroup());
        info.put("artifactId", buildProperties.getArtifact());
        info.put("buildTime", buildProperties.getTime().toString());

        try {
            Properties gitProps = new Properties();
            gitProps.load(getClass().getClassLoader().getResourceAsStream("git.properties"));
            info.put("gitCommit", gitProps.getProperty("git.commit.id.abbrev", "unknown"));
            info.put("gitBranch", gitProps.getProperty("git.branch", "unknown"));
            info.put("gitCommitTime", gitProps.getProperty("git.commit.time", "unknown"));
        } catch (Exception e) {
            log.warn("无法读取 git.properties: {}", e.getMessage());
            info.put("gitCommit", "unavailable");
        }

        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));

        if (ctx instanceof WebApplicationContext) {
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            info.put("uptimeSeconds", uptime / 1000);
            info.put("startupTime", new Date(System.currentTimeMillis() - uptime).toString());
        }

        info.put("springBootVersion", SpringBootVersion.getVersion());
        info.put("activeProfiles", String.join(",", ctx.getEnvironment().getActiveProfiles()));

        return ResponseEntity.ok(Result.success(info));
    }

    // ==================== /ops/health/full ====================

    @GetMapping("/health/full")
    @Operation(summary = "全组件健康检查")
    public ResponseEntity<Result<Map<String, Object>>> fullHealth(HttpServletRequest request) {
        if (!managementApiKeyService.validateRequest(request)) {
            return forbidden();
        }

        Map<String, Object> health = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();

        components.put("application", Map.of("status", "UP", "uptime",
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000 + "s"));
        components.put("database", checkDatabase());
        components.put("redis", checkRedis());
        components.put("aiApi", checkAiApi());

        boolean allUp = components.values().stream()
                .allMatch(c -> "UP".equals(((Map<String, Object>) c).get("status")));
        health.put("overall", allUp ? "UP" : "DEGRADED");
        health.put("components", components);
        health.put("checkedAt", Instant.now().toString());

        return ResponseEntity.ok(Result.success(health));
    }

    private Map<String, Object> checkDatabase() {
        try {
            if (dataSource instanceof DruidDataSource ds) {
                return Map.of(
                        "status", "UP",
                        "activeConnections", ds.getActiveCount(),
                        "maxConnections", ds.getMaxActive(),
                        "url", ds.getUrl().replaceAll("//.*@", "//***@")
                );
            }
            return Map.of("status", "UP", "note", "非Druid数据源");
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private Map<String, Object> checkRedis() {
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            return Map.of("status", "UP", "ping", pong);
        } catch (Exception e) {
            log.error("Redis 健康检查失败", e);
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private Map<String, Object> checkAiApi() {
        try {
            boolean available = geminiNetworkService.isServiceAvailable();
            return Map.of("status", available ? "UP" : "DOWN");
        } catch (Exception e) {
            log.error("AI API 健康检查失败", e);
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    // ==================== /ops/ai/analyze-error ====================

    @PostMapping("/ai/analyze-error")
    @Operation(summary = "AI 错误日志分析")
    public ResponseEntity<Result<Map<String, Object>>> analyzeError(
            @RequestBody @Valid ErrorAnalysisRequest request,
            HttpServletRequest httpServletRequest) {
        if (!managementApiKeyService.validateRequest(httpServletRequest)) {
            return forbidden();
        }

        String rawErrorLog = request.getErrorLog();
        String rawContext = request.getContextInfo();

        if (rawErrorLog != null && rawErrorLog.length() > MAX_ERROR_LOG_LENGTH) {
            Result<Map<String, Object>> result = new Result<>(400, "异常日志长度不能超过" + MAX_ERROR_LOG_LENGTH + "字符");
            return ResponseEntity.badRequest().body(result);
        }
        if (rawContext != null && rawContext.length() > MAX_CONTEXT_INFO_LENGTH) {
            Result<Map<String, Object>> result = new Result<>(400, "上下文信息长度不能超过" + MAX_CONTEXT_INFO_LENGTH + "字符");
            return ResponseEntity.badRequest().body(result);
        }

        String sanitizedErrorLog = sanitizeForAi(rawErrorLog);
        String sanitizedContext = sanitizeForAi(rawContext);

        String traceId = MDC.get("traceId");
        log.info("AI 错误分析请求 traceId={} errorLogLen={} sanitizedLen={}",
                traceId,
                rawErrorLog != null ? rawErrorLog.length() : 0,
                sanitizedErrorLog != null ? sanitizedErrorLog.length() : 0);

        List<AiModel> availableModels = aiModelService.getAvailableModels();
        if (availableModels.isEmpty()) {
            return ResponseEntity.ok(Result.failed("无可用的 AI 模型"));
        }
        AiModel model = availableModels.get(0);

        String prompt = buildErrorAnalysisPrompt(sanitizedErrorLog, sanitizedContext);

        try {
            Map<String, Object> analysis = callAiForAnalysis(prompt, model);
            analysis.put("sanitized", true);
            analysis.put("inputLength", rawErrorLog != null ? rawErrorLog.length() : 0);
            return ResponseEntity.ok(Result.success(analysis));
        } catch (Exception e) {
            log.error("AI 错误分析调用失败", e);
            return ResponseEntity.ok(Result.failed("AI 分析调用失败: " + e.getMessage()));
        }
    }

    /**
     * 脱敏后送入 AI，避免密钥、Token、邮箱等敏感信息外泄到模型提供商。
     */
    public static String sanitizeForAi(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String sanitized = SENSITIVE_PATTERN.matcher(input).replaceAll("$1$2***REDACTED***");
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1***REDACTED***");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("***JWT_REDACTED***");
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("***EMAIL_REDACTED***");
        return sanitized;
    }

    private String buildErrorAnalysisPrompt(String errorLog, String contextInfo) {
        return String.format("""
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
                """, contextInfo != null ? contextInfo : "无", errorLog);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiForAnalysis(String prompt, AiModel model) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model.getModelCode());

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);

        requestBody.put("messages", messages);
        requestBody.put("stream", false);
        requestBody.put("max_tokens", 1024);
        requestBody.put("temperature", 0.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(model.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                model.getBaseUrl(), entity, String.class);

        String responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("AI 返回空响应");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            var json = mapper.readTree(responseBody);
            String content = json.get("choices").get(0).get("message").get("content").asText();
            // 尝试解析 AI 返回的 JSON
            return mapper.readValue(content, Map.class);
        } catch (Exception e) {
            log.warn("AI 分析结果 JSON 解析失败，返回原始内容", e);
            return Map.of("raw", responseBody, "error", "AI 返回格式解析失败");
        }
    }

    // ==================== 辅助方法 ====================

    private ResponseEntity<Result<Map<String, Object>>> forbidden() {
        Result<Map<String, Object>> result = new Result<>(403, "访问被拒绝：需要有效的API密钥");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }
}
