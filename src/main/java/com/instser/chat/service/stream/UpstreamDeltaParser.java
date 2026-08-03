package com.instser.chat.service.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.service.content.ContentPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 上游 OpenAI-compatible SSE JSON 的统一解析入口。
 * 主链路与续写共用，避免双份逻辑漂移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpstreamDeltaParser {

    private final ContentPolicy contentPolicy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析并消费一个完整 JSON 帧。
     *
     * @param thinkingEnabled deepThinking=true 时才写出/累计 reasoning
     * @return 本帧是否产生了任何可观察输出（content 或 reasoning）
     */
    public boolean consume(String jsonData,
                           ChatStreamSink sink,
                           StreamResponseState state,
                           boolean thinkingEnabled) {
        if (jsonData == null || jsonData.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return false;
            }
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null || delta.isNull()) {
                return false;
            }

            boolean produced = false;

            if (thinkingEnabled) {
                String reasoning = textualOrNull(delta.get("reasoning_content"));
                if (reasoning != null && !reasoning.isEmpty()) {
                    sink.writeReasoning(reasoning);
                    state.appendReasoning(reasoning);
                    produced = true;
                }
            }

            String content = textualOrNull(delta.get("content"));
            if (content != null && !content.isEmpty()) {
                ContentPolicy.ContentDetectionResult detection = contentPolicy.detectContentAndGetResult(content);
                if (detection.needsRetry()) {
                    throw new ContentPolicy.ChatRetryException("检测到需要重试的错误消息");
                }
                if (!detection.wasFiltered()) {
                    String clean = detection.getCleanedContent();
                    if (clean != null && !clean.isEmpty()) {
                        sink.writeContent(clean);
                        state.appendContent(clean);
                        produced = true;
                    }
                }
            }

            return produced;
        } catch (ContentPolicy.ChatRetryException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析上游 delta 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 续写路径：仅解析 content 文本（不做 contentPolicy 检测），供缓存/对齐逻辑使用。
     * reasoning 在 thinkingEnabled 时直接写出并累计到 state。
     */
    public String extractRawContentAndEmitReasoning(String jsonData,
                                                    ChatStreamSink sink,
                                                    StreamResponseState state,
                                                    boolean thinkingEnabled) {
        if (jsonData == null || jsonData.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null || delta.isNull()) {
                return null;
            }

            if (thinkingEnabled) {
                String reasoning = textualOrNull(delta.get("reasoning_content"));
                if (reasoning != null && !reasoning.isEmpty()) {
                    sink.writeReasoning(reasoning);
                    state.appendReasoning(reasoning);
                }
            }

            return textualOrNull(delta.get("content"));
        } catch (Exception e) {
            log.error("续写解析上游 delta 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String textualOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }
}
