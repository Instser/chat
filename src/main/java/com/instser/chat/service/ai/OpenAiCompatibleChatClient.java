package com.instser.chat.service.ai;

import com.instser.chat.config.AiUpstreamRetryProperties;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelRotationService;
import com.instser.chat.service.dto.SelectedCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenAI-compatible + Gemini 特例 的 AI Client 实现。
 *
 * <p><b>上游重试</b>：{@link #stream} 对任何上游错误（HTTP 4xx/5xx、超时、连接失败）
 * 按 {@link AiUpstreamRetryProperties} 配置固定间隔重试，默认 20 次 / 1s，
 * 统一作用于所有走流式对话链路的业务入口（chat / retry / edit-resend）。</p>
 *
 * <p><b>密钥轮询协同</b>：每次订阅（首次 + 每次重试）都在 {@code defer} 内重新执行
 * {@link AiModelRotationService#selectForChat(Long)}——轮询发生在单个模型行（model_id）内部的
 * 密钥池（{@code t_ai_model_key}），按有效权重加权随机选取一个 key；同 model_code 的多行
 * 属不同模型级别（BEST/PERFORMANCE/STANDARD），不跨行轮换，故端点恒定取自入参 {@code model}
 * 的 baseUrl，只有 apiKey 随选中结果变化。首个 DataBuffer 到达时 {@code reportSuccess}
 * （连续失败清零、权重恢复）；在「尚未输出任何内容」（emitted==false）时失败，则
 * {@code reportFailure}（连续失败+1 → 有效权重下降）。因此下一次重试重选时，失败的 key
 * 大概率让位给其它 key，形成"重试 + 换密钥"的自然协同；一旦已开始输出（emitted==true），
 * 中途断流既不重试也不上报失败（该 key 已成功服务过，属连接问题）。</p>
 *
 * <p>密钥池为空/未配置/查询异常时 {@code selectForChat} 返回 null，回退入参 {@code model}
 * 自带的 {@code apiKey}（原单密钥逻辑），此时上报走行级兼容路径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient implements AiChatClient {

    /** 上报给轮询服务的错误摘要长度上限（入库前还会再截断 255） */
    private static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    private final WebClient.Builder webClientBuilder;
    private final AiUpstreamRetryProperties retryProperties;
    private final AiModelRotationService aiModelRotationService;

    @Override
    public Map<String, Object> buildStreamRequestBody(AiModel model,
                                                      List<Map<String, String>> messages,
                                                      boolean thinkingEnabled) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model.getModelCode());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        // DeepSeek / OpenAI 兼容网关：thinking 需 ThinkingOptions 结构，不能传 boolean
        // 开/关都显式传 type，避免依赖上游默认值
        Map<String, Object> thinkingOptions = new HashMap<>();
        thinkingOptions.put("type", thinkingEnabled ? "enabled" : "disabled");
        requestBody.put("thinking", thinkingOptions);

        if ("Gemini".equalsIgnoreCase(model.getProvider())
                || (model.getProvider() != null && model.getProvider().toLowerCase().contains("gemini"))) {
            // Gemini 特例：关闭思考时强制 none；开启时不覆盖原生 thinking 开关
            if (!thinkingEnabled) {
                requestBody.put("reasoning_effort", "none");
                log.debug("检测到Gemini模型，thinking=disabled，添加 reasoning_effort=none");
            }
        }
        return requestBody;
    }

    @Override
    public WebClient createWebClient(AiModel model) {
        return webClientBuilder
                .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                .defaultHeader("Accept-Charset", "UTF-8")
                .defaultHeader("User-Agent", "OpenAI/PowerShell")
                .build();
    }

    @Override
    public Flux<DataBuffer> stream(AiModel model, Map<String, Object> requestBody, int timeoutSeconds) {
        if (!retryProperties.isStreamEnabled()) {
            // 未开启重试：单次订阅内选一次密钥行（重新订阅会重新选）
            return Flux.defer(() -> streamOnceWithRotation(model, requestBody, timeoutSeconds,
                    new AtomicBoolean(false)));
        }
        // 用 defer 包裹：每次重试重新订阅时重新发起请求（重新建连），避免复用已失败的连接
        long maxAttempts = retryProperties.getMaxAttempts();
        long delayMs = retryProperties.getFixedDelayMs();
        AtomicLong attempt = new AtomicLong(0);
        // 是否已有数据流出：一旦上游开始输出 token，中途失败不能再重试，
        // 否则会在已输出内容后重新生成整段回复造成内容重复。
        // 429/503 等错误发生在响应状态阶段（尚未输出任何内容），不受此限制，可安全重试。
        AtomicBoolean emitted = new AtomicBoolean(false);
        return Flux.defer(() -> streamOnceWithRotation(model, requestBody, timeoutSeconds, emitted))
                .retryWhen(Retry
                        .from(companion -> companion
                                .flatMap(rs -> {
                                    long n = attempt.incrementAndGet();
                                    if (emitted.get()) {
                                        // 流式已开始输出首个 token 后中断：透传原始错误，不重试
                                        log.warn("AI上游流式调用中途失败（已输出部分内容），不再重试，错误：{}",
                                                rs.failure() != null ? rs.failure().getMessage() : "unknown");
                                        return Mono.error(rs.failure() != null ? rs.failure()
                                                : new RuntimeException("AI上游流式调用中途失败"));
                                    }
                                    log.warn("AI上游流式调用失败，第{}次重试（最大{}次），错误：{}",
                                            n, maxAttempts, rs.failure() != null ? rs.failure().getMessage() : "unknown");
                                    if (n > maxAttempts) {
                                        return Mono.error(new RuntimeException(
                                                "AI上游流式调用重试耗尽，attempts=" + maxAttempts, rs.failure()));
                                    }
                                    return Mono.delay(Duration.ofMillis(delayMs));
                                })));
    }

    /**
     * 单次订阅：选密钥（模型行内密钥池）→ 发起请求 → 按 {@code emitted} 门控上报成功/失败。
     *
     * <p>选中时用「模型行 baseUrl + 选中 key」发请求；未选中（池为空/查询异常）时
     * 回退入参 {@code model} 的 apiKey，保持原单密钥逻辑。</p>
     *
     * @param emitted 是否已输出首个 token 的门控标志（重试路径跨订阅共享）
     */
    private Flux<DataBuffer> streamOnceWithRotation(AiModel model, Map<String, Object> requestBody,
                                                   int timeoutSeconds, AtomicBoolean emitted) {
        SelectedCredential selected = selectRotationCredential(model);
        Flux<DataBuffer> upstream = selected != null
                ? doStreamOnce(model, selected, requestBody, timeoutSeconds)
                : doStreamOnce(model, requestBody, timeoutSeconds);
        return upstream
                .doOnNext(buffer -> {
                    // 仅首个 buffer 上报一次成功
                    if (emitted.compareAndSet(false, true)) {
                        reportSuccessQuietly(model.getId(), selected);
                    }
                })
                .doOnError(ex -> {
                    // 尚未输出任何内容即失败：对选中 key 记一次失败（降权），已输出后断流不上报
                    if (!emitted.get()) {
                        reportFailureQuietly(model.getId(), selected, ex);
                    }
                });
    }

    /**
     * 选取本次使用的密钥；任何异常都只记日志并回退（返回 null）。
     */
    private SelectedCredential selectRotationCredential(AiModel model) {
        try {
            return aiModelRotationService.selectForChat(model.getId());
        } catch (Exception ex) {
            log.warn("密钥轮询选取失败，回退模型自身凭据，modelId={}，错误：{}",
                    model.getId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 上报成功：选中 key 走池路径（keyId 非空），回退场景走行级兼容路径。
     */
    private void reportSuccessQuietly(Long modelRowId, SelectedCredential selected) {
        try {
            aiModelRotationService.reportSuccess(modelRowId, selected != null ? selected.keyId() : null);
        } catch (Exception ex) {
            log.warn("密钥轮询上报成功异常（忽略），modelRowId={}，keyId={}，错误：{}",
                    modelRowId, selected != null ? selected.keyId() : null, ex.getMessage());
        }
    }

    /**
     * 上报失败：选中 key 走池路径（keyId 非空），回退场景走行级兼容路径；错误摘要含 HTTP 状态并截断。
     */
    private void reportFailureQuietly(Long modelRowId, SelectedCredential selected, Throwable ex) {
        try {
            aiModelRotationService.reportFailure(modelRowId, selected != null ? selected.keyId() : null,
                    summarizeError(ex));
        } catch (Exception e) {
            log.warn("密钥轮询上报失败异常（忽略），modelRowId={}，keyId={}，错误：{}",
                    modelRowId, selected != null ? selected.keyId() : null, e.getMessage());
        }
    }

    /**
     * 提取上游错误摘要：优先包含 HTTP 状态码与状态文本，其次异常类型/消息，截断后返回。
     */
    private String summarizeError(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        WebClientResponseException responseException = findResponseException(ex);
        String summary;
        if (responseException != null) {
            StringBuilder builder = new StringBuilder()
                    .append("HTTP ").append(responseException.getStatusCode().value());
            if (StringUtils.hasText(responseException.getStatusText())) {
                builder.append(' ').append(responseException.getStatusText());
            }
            String body = responseException.getResponseBodyAsString();
            if (StringUtils.hasText(body)) {
                builder.append(" - ").append(body.trim());
            }
            summary = builder.toString();
        } else {
            summary = ex.getClass().getSimpleName()
                    + (StringUtils.hasText(ex.getMessage()) ? ": " + ex.getMessage() : "");
        }
        summary = summary.replaceAll("\\s+", " ").trim();
        return summary.length() > MAX_ERROR_SUMMARY_LENGTH
                ? summary.substring(0, MAX_ERROR_SUMMARY_LENGTH)
                : summary;
    }

    /**
     * 沿异常链查找 WebClientResponseException（HTTP 4xx/5xx）。
     */
    private WebClientResponseException findResponseException(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < 4) {
            if (current instanceof WebClientResponseException responseException) {
                return responseException;
            }
            current = current.getCause();
        }
        return null;
    }

    private Flux<DataBuffer> doStreamOnce(AiModel model, Map<String, Object> requestBody, int timeoutSeconds) {
        return doStream(createWebClient(model), model.getBaseUrl(), requestBody, timeoutSeconds);
    }

    /**
     * 按轮询选中的凭据发请求：端点取模型行 baseUrl，鉴权头取选中密钥的明文。
     *
     * <p>端点恒定来自模型行（不跨行轮换），极端情况下模型行未配置 baseUrl 时
     * 回退入参 {@code model} 的端点，避免请求 URL 为空。</p>
     */
    private Flux<DataBuffer> doStreamOnce(AiModel model, SelectedCredential selected,
                                          Map<String, Object> requestBody, int timeoutSeconds) {
        String url = StringUtils.hasText(selected.baseUrl()) ? selected.baseUrl() : model.getBaseUrl();
        return doStream(createWebClient(selected.apiKey()), url, requestBody, timeoutSeconds);
    }

    private Flux<DataBuffer> doStream(WebClient webClient, String url, Map<String, Object> requestBody,
                                      int timeoutSeconds) {
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * 按明文密钥构建 WebClient（与 {@link #createWebClient(AiModel)} 保持一致的头信息）。
     */
    private WebClient createWebClient(String apiKey) {
        return webClientBuilder
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                .defaultHeader("Accept-Charset", "UTF-8")
                .defaultHeader("User-Agent", "OpenAI/PowerShell")
                .build();
    }
}
