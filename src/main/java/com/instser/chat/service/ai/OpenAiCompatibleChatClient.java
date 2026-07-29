package com.instser.chat.service.ai;

import com.instser.chat.entity.AiModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible + Gemini 特例 的 AI Client 实现。
 *
 * <p>压测时可开启 {@code chat.loadtest.mock-upstream-enabled=true}，
 * 将上游 URL 强制指到本地 Mock（零 AI token），不改数据库模型配置。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient implements AiChatClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * 仅压测：开启后 stream() 忽略模型表 baseUrl，改打 Mock。
     * 默认 false，生产勿开。
     */
    @Value("${chat.loadtest.mock-upstream-enabled:false}")
    private boolean mockUpstreamEnabled;

    /**
     * Mock 完整 completions URL，例如 http://127.0.0.1:18080/v1/chat/completions
     */
    @Value("${chat.loadtest.mock-upstream-url:http://127.0.0.1:18080/v1/chat/completions}")
    private String mockUpstreamUrl;

    @Override
    public Map<String, Object> buildStreamRequestBody(AiModel model, List<Map<String, String>> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model.getModelCode());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        if ("Gemini".equalsIgnoreCase(model.getProvider())
                || (model.getProvider() != null && model.getProvider().toLowerCase().contains("gemini"))) {
            requestBody.put("reasoning_effort", "none");
            log.debug("检测到Gemini模型，添加reasoning_effort=none参数");
        }
        return requestBody;
    }

    @Override
    public WebClient createWebClient(AiModel model) {
        String apiKey = model.getApiKey();
        if (mockUpstreamEnabled) {
            // Mock 不校验 Key；避免误打真网时仍用库内密钥的错觉
            apiKey = StringUtils.hasText(apiKey) ? apiKey : "mock-key";
        }
        return webClientBuilder
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                .defaultHeader("Accept-Charset", "UTF-8")
                .defaultHeader("User-Agent", "OpenAI/PowerShell")
                .build();
    }

    @Override
    public Flux<DataBuffer> stream(AiModel model, Map<String, Object> requestBody, int timeoutSeconds) {
        WebClient webClient = createWebClient(model);
        String targetUrl = resolveUpstreamUrl(model);
        if (mockUpstreamEnabled) {
            log.warn("LOADTEST mock-upstream 已启用，AI 流改打: {} (原模型 baseUrl={})",
                    targetUrl, model.getBaseUrl());
        }
        return webClient.post()
                .uri(targetUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    private String resolveUpstreamUrl(AiModel model) {
        if (mockUpstreamEnabled && StringUtils.hasText(mockUpstreamUrl)) {
            return mockUpstreamUrl.trim();
        }
        return model.getBaseUrl();
    }
}
