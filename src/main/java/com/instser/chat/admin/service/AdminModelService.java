package com.instser.chat.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.instser.chat.admin.audit.AdminAuditPublisher;
import com.instser.chat.admin.common.SensitiveMasker;
import com.instser.chat.admin.dto.AdminModelCredentialRequest;
import com.instser.chat.admin.dto.AdminModelUpsertRequest;
import com.instser.chat.admin.dto.AdminStatusRequest;
import com.instser.chat.admin.vo.AdminModelVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelRotationService;
import com.instser.chat.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminModelService {

    /** 密钥轮询基础权重默认值 */
    private static final int DEFAULT_WEIGHT = 100;

    private final AiModelService aiModelService;
    private final AdminAuditPublisher adminAuditPublisher;
    private final RestTemplate restTemplate;
    private final AiModelRotationService aiModelRotationService;

    public List<AdminModelVO> listAll() {
        return aiModelService.list(new LambdaQueryWrapper<AiModel>()
                        .orderByDesc(AiModel::getCreatedTime))
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    public AdminModelVO getById(Long modelId) {
        return toVo(requireModel(modelId));
    }

    @Transactional
    public Result<AdminModelVO> create(AdminModelUpsertRequest request) {
        // 允许模型代码重复（同一代码可对应不同凭据/端点配置），不再做唯一性拦截。
        AiModel model = new AiModel();
        apply(model, request);
        model.setCreatedTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        aiModelService.save(model);
        // 新增行可能是同 model_code 密钥池的新成员：立即失效该代码的本地缓存
        aiModelRotationService.evictCache(model.getModelCode());
        String createReason = StringUtils.hasText(request.getReason()) ? request.getReason() : "N/A";
        adminAuditPublisher.success("MODEL_CREATE", String.valueOf(model.getId()),
                "code=" + model.getModelCode() + ",reason=" + createReason);
        return Result.success(toVo(model), "模型已创建");
    }

    @Transactional
    public Result<AdminModelVO> update(Long modelId, AdminModelUpsertRequest request) {
        AiModel model = requireModel(modelId);
        // 允许模型代码重复，更新时不再校验代码冲突。
        String oldModelCode = model.getModelCode();
        apply(model, request);
        model.setUpdateTime(LocalDateTime.now());
        aiModelService.updateById(model);
        // 权重/代码可能变更：新旧 model_code 的密钥池缓存都失效
        aiModelRotationService.evictCache(oldModelCode);
        aiModelRotationService.evictCache(model.getModelCode());
        String updateReason = StringUtils.hasText(request.getReason()) ? request.getReason() : "N/A";
        adminAuditPublisher.success("MODEL_UPDATE", String.valueOf(modelId),
                "code=" + model.getModelCode() + ",reason=" + updateReason);
        return Result.success(toVo(model), "模型已更新");
    }

    @Transactional
    public Result<AdminModelVO> updateCredential(Long modelId, AdminModelCredentialRequest request) {
        AiModel model = requireModel(modelId);
        if (StringUtils.hasText(request.getBaseUrl())) {
            model.setBaseUrl(request.getBaseUrl().trim());
        }
        model.setApiKey(request.getApiKey().trim());
        model.setUpdateTime(LocalDateTime.now());
        aiModelService.updateById(model);
        // 凭据变更：该 model_code 的密钥池缓存立即失效
        aiModelRotationService.evictCache(model.getModelCode());
        // reason 现为可选字段，留空时记录占位，便于审计链路保持统一格式。
        String reason = StringUtils.hasText(request.getReason()) ? request.getReason() : "N/A";
        adminAuditPublisher.success("MODEL_CREDENTIAL_UPDATE", String.valueOf(modelId),
                "apiKeyUpdated=true,reason=" + reason);
        return Result.success(toVo(model), "模型凭据已更新");
    }

    @Transactional
    public Result<AdminModelVO> updateAvailability(Long modelId, AdminStatusRequest request) {
        AiModel model = requireModel(modelId);
        Integer old = model.getIsAvailable();
        model.setIsAvailable(request.getStatus());
        model.setUpdateTime(LocalDateTime.now());
        aiModelService.updateById(model);
        // 可用性决定该行是否参与轮询：缓存立即失效
        aiModelRotationService.evictCache(model.getModelCode());
        adminAuditPublisher.success("MODEL_AVAILABILITY", String.valueOf(modelId),
                "old=" + old + ",new=" + request.getStatus() + ",reason=" + request.getReason());
        return Result.success(toVo(model), "模型可用状态已更新");
    }

    @Transactional
    public Result<AdminModelVO> updateVisibility(Long modelId, AdminStatusRequest request) {
        AiModel model = requireModel(modelId);
        Integer old = model.getIsVisible();
        model.setIsVisible(request.getStatus());
        model.setUpdateTime(LocalDateTime.now());
        aiModelService.updateById(model);
        // 可见性不影响轮询候选，但为保证管理端展示与缓存一致，一并失效
        aiModelRotationService.evictCache(model.getModelCode());
        adminAuditPublisher.success("MODEL_VISIBILITY", String.valueOf(modelId),
                "old=" + old + ",new=" + request.getStatus() + ",reason=" + request.getReason());
        return Result.success(toVo(model), "模型可见状态已更新");
    }

    /**
     * 清零某模型行的轮询统计（累计成功/失败、连续失败、最近错误），使有效权重回到基础权重。
     * 仅影响该行自身的统计列，不改动凭据与可用性。
     */
    @Transactional
    public Result<AdminModelVO> resetStats(Long modelId) {
        AiModel model = requireModel(modelId);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AiModel> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiModel::getId, modelId)
                .set(AiModel::getSuccessCount, 0L)
                .set(AiModel::getFailCount, 0L)
                .set(AiModel::getConsecutiveFails, 0)
                // 显式置 NULL，避免 null 值绑定歧义
                .setSql("last_error = NULL")
                .set(AiModel::getUpdateTime, now);
        aiModelService.update(wrapper);
        // 本地缓存中的连续失败数立即归零（权重恢复）
        aiModelRotationService.evictCache(model.getModelCode());
        AiModel refreshed = aiModelService.getById(modelId);
        AiModel result = refreshed != null ? refreshed : model;
        // 审计只记录脱敏 key 与变更摘要，绝不落完整密钥
        adminAuditPublisher.success("MODEL_RESET_STATS", String.valueOf(modelId),
                "code=" + result.getModelCode()
                        + ",apiKey=" + SensitiveMasker.maskSecret(result.getApiKey())
                        + ",reset=success_count,fail_count,consecutive_fails,last_error");
        return Result.success(toVo(result), "轮询统计已清零");
    }

    /**
     * 手动连通性测试：走与聊天一致的最小 completion 探测。
     * 注意：isAvailable 是配置开关，不代表实时连通；本接口才是实时结果。
     * 使用独立短/中超时 RestTemplate，避免默认客户端过长或过短。
     */
    public Result<Map<String, Object>> testConnectivity(Long modelId) {
        AiModel model = requireModel(modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", String.valueOf(modelId));
        result.put("modelCode", model.getModelCode());
        result.put("note", "此结果为实时探测；列表「配置启用」来自数据库开关，二者含义不同");
        if (!StringUtils.hasText(model.getBaseUrl()) || !StringUtils.hasText(model.getApiKey())) {
            result.put("status", "FAILED");
            result.put("message", "缺少 baseUrl 或 apiKey");
            adminAuditPublisher.failed("MODEL_TEST", String.valueOf(modelId), "missing credential");
            return Result.success(result);
        }

        long start = System.currentTimeMillis();
        // 手动测试允许比仪表盘更宽松，贴近真实聊天冷启动
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        RestTemplate probeClient = new RestTemplate(factory);

        try {
            ModelChatProbeSupport.BuiltRequest probe = buildProbeRequest(model);
            result.put("probeMode", probe.mode());
            result.put("requestUrl", probe.url());
            result.put("probePrompt", ModelChatProbeSupport.PROBE_USER_PROMPT);
            ResponseEntity<String> response = probeClient.exchange(
                    probe.url(),
                    probe.method(),
                    probe.entity(),
                    String.class
            );
            long latency = System.currentTimeMillis() - start;
            boolean httpOk = response.getStatusCode().is2xxSuccessful();
            ModelChatProbeSupport.ContentCheck check = ModelChatProbeSupport.evaluateResponse(
                    httpOk,
                    response.getStatusCode().value(),
                    response.getBody(),
                    probe.mode()
            );
            boolean ok = check.httpOk() && check.contentOk();
            result.put("status", ok ? "UP" : "DOWN");
            result.put("httpStatus", response.getStatusCode().value());
            result.put("latencyMs", latency);
            result.put("contentOk", check.contentOk());
            result.put("replyPreview", check.extractedText());
            result.put("message", check.message());
            if (ok) {
                adminAuditPublisher.success("MODEL_TEST", String.valueOf(modelId),
                        "httpStatus=" + response.getStatusCode().value()
                                + ",contentOk=true,latencyMs=" + latency);
            } else {
                adminAuditPublisher.failed("MODEL_TEST", String.valueOf(modelId),
                        sanitizeError(check.message()));
            }
            return Result.success(result);
        } catch (RestClientException ex) {
            long latency = System.currentTimeMillis() - start;
            result.put("status", "DOWN");
            result.put("latencyMs", latency);
            result.put("timedOut", isTimeout(ex));
            result.put("contentOk", false);
            result.put("message", (isTimeout(ex) ? "探测超时: " : "连通失败: ") + sanitizeError(ex.getMessage()));
            adminAuditPublisher.failed("MODEL_TEST", String.valueOf(modelId), sanitizeError(ex.getMessage()));
            return Result.success(result);
        } catch (Exception ex) {
            result.put("status", "DOWN");
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("contentOk", false);
            result.put("message", "连通失败: " + sanitizeError(ex.getMessage()));
            adminAuditPublisher.failed("MODEL_TEST", String.valueOf(modelId), sanitizeError(ex.getMessage()));
            return Result.success(result);
        }
    }

    private ModelChatProbeSupport.BuiltRequest buildProbeRequest(AiModel model) {
        String baseUrl = model.getBaseUrl().replaceAll("/+$", "");
        if (ModelChatProbeSupport.isGeminiNativeEndpoint(baseUrl, model.getProvider())) {
            return ModelChatProbeSupport.buildGeminiNativeRequest(
                    baseUrl, model.getModelCode(), model.getApiKey());
        }
        return ModelChatProbeSupport.buildOpenAiChatRequest(
                baseUrl, model.getModelCode(), model.getApiKey());
    }

    private boolean isTimeout(Throwable ex) {
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

    private void apply(AiModel model, AdminModelUpsertRequest request) {
        model.setModelCode(request.getModelCode());
        model.setModelName(request.getModelName());
        model.setDescription(request.getDescription());
        model.setProvider(request.getProvider());
        model.setVersion(request.getVersion());
        model.setTokenLimit(request.getTokenLimit());
        model.setPricePer1kTokens(request.getPricePer1kTokens());
        if (request.getIsAvailable() != null) {
            model.setIsAvailable(request.getIsAvailable());
        } else if (model.getIsAvailable() == null) {
            model.setIsAvailable(1);
        }
        if (request.getIsVisible() != null) {
            model.setIsVisible(request.getIsVisible());
        } else if (model.getIsVisible() == null) {
            model.setIsVisible(1);
        }
        if (StringUtils.hasText(request.getBaseUrl())) {
            model.setBaseUrl(request.getBaseUrl().trim());
        }
        // 密钥轮询基础权重：留空默认 100，范围由 DTO 校验约束
        if (request.getWeight() != null) {
            model.setWeight(request.getWeight());
        } else if (model.getWeight() == null) {
            model.setWeight(DEFAULT_WEIGHT);
        }
    }

    private AiModel requireModel(Long modelId) {
        AiModel model = aiModelService.getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }
        return model;
    }

    private AdminModelVO toVo(AiModel model) {
        return AdminModelVO.builder()
                .id(model.getId())
                .modelCode(model.getModelCode())
                .modelName(model.getModelName())
                .description(model.getDescription())
                .provider(model.getProvider())
                .version(model.getVersion())
                .tokenLimit(model.getTokenLimit())
                .pricePer1kTokens(model.getPricePer1kTokens())
                .isAvailable(model.getIsAvailable())
                .isVisible(model.getIsVisible())
                .baseUrl(model.getBaseUrl())
                .apiKeyConfigured(SensitiveMasker.hasSecret(model.getApiKey()))
                .apiKeyMasked(SensitiveMasker.maskSecret(model.getApiKey()))
                .weight(model.getWeight())
                // 有效权重按当前连续失败数实时计算，供前端展示降权效果
                .effectiveWeight(aiModelRotationService.effectiveWeight(model))
                .successCount(model.getSuccessCount())
                .failCount(model.getFailCount())
                .consecutiveFails(model.getConsecutiveFails())
                .lastError(model.getLastError())
                .lastUsedTime(model.getLastUsedTime())
                .createdTime(model.getCreatedTime())
                .updateTime(model.getUpdateTime())
                .build();
    }

    private String sanitizeError(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.replaceAll("(?i)(Bearer\\s+)\\S+", "$1***")
                .replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)\\S+", "$1***");
    }
}
