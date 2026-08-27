package com.instser.chat.service.ai;

import com.instser.chat.config.AiUpstreamRetryProperties;
import com.instser.chat.entity.AiModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenAI-compatible + Gemini 特例 的 AI Client 实现。
 *
 * <p><b>上游重试</b>：{@link #stream} 对任何上游错误（HTTP 4xx/5xx、超时、连接失败）
 * 按 {@link AiUpstreamRetryProperties} 配置固定间隔重试，默认 20 次 / 500ms，
 * 统一作用于所有走流式对话链路的业务入口（chat / retry / edit-resend）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient implements AiChatClient {

    private final WebClient.Builder webClientBuilder;
    private final AiUpstreamRetryProperties retryProperties;

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
            return doStreamOnce(model, requestBody, timeoutSeconds);
        }
        // 用 defer 包裹：每次重试重新订阅时重新发起请求（重新建连），避免复用已失败的连接
        long maxAttempts = retryProperties.getMaxAttempts();
        long delayMs = retryProperties.getFixedDelayMs();
        AtomicLong attempt = new AtomicLong(0);
        return Flux.defer(() -> doStreamOnce(model, requestBody, timeoutSeconds))
                .retryWhen(Retry
                        .from(companion -> companion
                                .map(rs -> {
                                    long n = attempt.incrementAndGet();
                                    log.warn("AI上游流式调用失败，第{}次重试（最大{}次），错误：{}",
                                            n, maxAttempts, rs.failure() != null ? rs.failure().getMessage() : "unknown");
                                    return n;
                                })
                                .flatMap(n -> n > maxAttempts
                                        ? Mono.error(new RuntimeException(
                                                "AI上游流式调用重试耗尽，attempts=" + maxAttempts))
                                        : Mono.delay(Duration.ofMillis(delayMs)))));
    }

    private Flux<DataBuffer> doStreamOnce(AiModel model, Map<String, Object> requestBody, int timeoutSeconds) {
        WebClient webClient = createWebClient(model);
        return webClient.post()
                .uri(model.getBaseUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }
}
