package com.instser.chat.service.ai;

import com.instser.chat.entity.AiModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible + Gemini 特例 的 AI Client 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient implements AiChatClient {

    private final WebClient.Builder webClientBuilder;

    @Override
    public Map<String, Object> buildStreamRequestBody(AiModel model,
                                                      List<Map<String, String>> messages,
                                                      boolean thinkingEnabled) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model.getModelCode());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        // 开启/关闭都显式传，避免依赖上游默认值
        requestBody.put("thinking", thinkingEnabled);

        if ("Gemini".equalsIgnoreCase(model.getProvider())
                || (model.getProvider() != null && model.getProvider().toLowerCase().contains("gemini"))) {
            // Gemini 特例：关闭思考时强制 none；开启时不覆盖原生 thinking 开关
            if (!thinkingEnabled) {
                requestBody.put("reasoning_effort", "none");
                log.debug("检测到Gemini模型，thinking=false，添加 reasoning_effort=none");
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
