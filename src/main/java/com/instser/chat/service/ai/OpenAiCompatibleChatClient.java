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
