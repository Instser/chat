package com.instser.chat.service.ai;

import com.instser.chat.entity.AiModel;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleChatClientThinkingTest {

    private final OpenAiCompatibleChatClient client =
            new OpenAiCompatibleChatClient(WebClient.builder());

    @Test
    void thinkingTrueSetsExplicitEnabledAndNoGeminiNone() {
        AiModel model = model("openai", "gpt-x");
        Map<String, Object> body = client.buildStreamRequestBody(model, List.of(Map.of("role", "user", "content", "hi")), true);

        assertEquals(Map.of("type", "enabled"), body.get("thinking"));
        assertFalse(body.containsKey("reasoning_effort"));
        assertEquals(Boolean.TRUE, body.get("stream"));
    }

    @Test
    void thinkingFalseSetsExplicitDisabled() {
        AiModel model = model("openai", "gpt-x");
        Map<String, Object> body = client.buildStreamRequestBody(model, List.of(Map.of("role", "user", "content", "hi")), false);

        assertEquals(Map.of("type", "disabled"), body.get("thinking"));
        assertFalse(body.containsKey("reasoning_effort"));
    }

    @Test
    void geminiThinkingFalseAddsReasoningEffortNone() {
        AiModel model = model("Gemini", "gemini-2.5-flash");
        Map<String, Object> body = client.buildStreamRequestBody(model, List.of(Map.of("role", "user", "content", "hi")), false);

        assertEquals(Map.of("type", "disabled"), body.get("thinking"));
        assertEquals("none", body.get("reasoning_effort"));
    }

    @Test
    void geminiThinkingTrueDoesNotForceReasoningEffortNone() {
        AiModel model = model("Gemini", "gemini-2.5-flash");
        Map<String, Object> body = client.buildStreamRequestBody(model, List.of(Map.of("role", "user", "content", "hi")), true);

        assertEquals(Map.of("type", "enabled"), body.get("thinking"));
        assertFalse(body.containsKey("reasoning_effort"));
    }

    private static AiModel model(String provider, String code) {
        AiModel model = new AiModel();
        model.setProvider(provider);
        model.setModelCode(code);
        model.setApiKey("k");
        model.setBaseUrl("http://example.test/v1/chat/completions");
        return model;
    }
}
