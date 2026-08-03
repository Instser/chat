package com.instser.chat.service.ai;

import com.instser.chat.entity.AiModel;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 调用端口：屏蔽 Provider 差异与 HTTP 细节。
 */
public interface AiChatClient {

    /**
     * 构建 OpenAI-compatible 流式请求体（含 Gemini 特例参数）。
     *
     * @param thinkingEnabled 是否开启原生思考；true/false 均会显式写入
     *                         thinking: {type: enabled|disabled}
     */
    Map<String, Object> buildStreamRequestBody(AiModel model,
                                              List<Map<String, String>> messages,
                                              boolean thinkingEnabled);

    /**
     * 创建已配置鉴权头的 WebClient。
     */
    WebClient createWebClient(AiModel model);

    /**
     * 发起流式请求，返回原始 DataBuffer 流。
     */
    Flux<DataBuffer> stream(AiModel model, Map<String, Object> requestBody, int timeoutSeconds);
}
