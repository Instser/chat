package com.instser.chat.service;

import com.instser.chat.entity.AiModel;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI服务测试类
 *
 * @author instser
 * @since 2024-01-01
 */
public class OpenAiServiceTest {

    @Test
    public void testDirectAiCall() {
        try {
            // 创建测试模型
            AiModel model = new AiModel();
            model.setId(1L);
            model.setModelCode("qwen3-235b-a22b");
            model.setModelName("通义千问");
            model.setBaseUrl("http://203.0.113.15:3000/v1");
            model.setApiKey("a2366021253");

            // 创建测试消息
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", "你好");
            messages.add(message);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 50);
            requestBody.put("temperature", 0.7);

            // 创建WebClient
            WebClient webClient = WebClient.builder()
                    .baseUrl(model.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            System.out.println("开始调用AI API...");

            // 发送请求
            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("AI API响应: " + response);

            // 验证响应不为空
            assert response != null && !response.isEmpty();

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
