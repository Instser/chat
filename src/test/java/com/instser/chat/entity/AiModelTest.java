package com.instser.chat.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModelTest {

    @Test
    void getters_shouldTrimWhitespaceForRequestCriticalFields() {
        AiModel model = new AiModel();
        model.setModelCode("\r\nQwen/Qwen2.5-7B-Instruct  ");
        model.setBaseUrl("  https://api.siliconflow.cn/v1/chat/completions\r\n");
        model.setApiKey("  test-key  ");
        model.setProvider("  Gemini\t");
        model.setModelName("  PERFORMANCE  ");

        assertEquals("Qwen/Qwen2.5-7B-Instruct", model.getModelCode());
        assertEquals("https://api.siliconflow.cn/v1/chat/completions", model.getBaseUrl());
        assertEquals("test-key", model.getApiKey());
        assertEquals("Gemini", model.getProvider());
        assertEquals("PERFORMANCE", model.getModelName());
    }
}
