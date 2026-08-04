package com.instser.chat.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 模型“真实可聊”探测：要求模型只返回 1，并解析响应体校验内容。
 * HTTP 通但无实际 completion 内容时视为失败。
 */
public final class ModelChatProbeSupport {

    public static final String PROBE_USER_PROMPT =
            "Reply with only the single digit 1. No other text.";

    /** 足够输出一个 "1"；过小可能导致部分网关空 content */
    public static final int PROBE_MAX_TOKENS = 16;

    private static final Pattern HAS_DIGIT_ONE = Pattern.compile("(?:^|\\D)1(?:\\D|$)");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModelChatProbeSupport() {
    }

    public record BuiltRequest(String mode, String url, HttpMethod method, HttpEntity<?> entity) {
    }

    public record ContentCheck(
            boolean httpOk,
            boolean contentOk,
            boolean hasErrorField,
            String extractedText,
            String message
    ) {
    }

    public static BuiltRequest buildOpenAiChatRequest(String baseUrl, String modelCode, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey == null ? "" : apiKey.trim());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelCode);
        body.put("stream", false);
        body.put("max_tokens", PROBE_MAX_TOKENS);
        // 关闭思考，避免探测被 reasoning 占满 token
        body.put("thinking", Map.of("type", "disabled"));
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", PROBE_USER_PROMPT
        )));
        String url = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return new BuiltRequest("openai-chat-completions", url, HttpMethod.POST, new HttpEntity<>(body, headers));
    }

    public static BuiltRequest buildGeminiNativeRequest(String baseUrl, String modelCode, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey == null ? "" : apiKey.trim());
        String root = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String url = root + "/models/" + modelCode + ":generateContent";
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", PROBE_MAX_TOKENS);
        generationConfig.put("temperature", 0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of(
                "parts", List.of(Map.of("text", PROBE_USER_PROMPT))
        )));
        body.put("generationConfig", generationConfig);
        return new BuiltRequest("gemini-generateContent", url, HttpMethod.POST, new HttpEntity<>(body, headers));
    }

    public static boolean isGeminiNativeEndpoint(String baseUrl, String provider) {
        String url = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        String p = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        if (url.contains("chat/completions")) {
            return false;
        }
        return url.contains("/v1beta") || url.contains("generatecontent") || p.contains("gemini");
    }

    public static ContentCheck evaluateResponse(boolean http2xx, Integer httpStatus, String body, String mode) {
        if (!http2xx) {
            return new ContentCheck(false, false, false, null,
                    "HTTP " + (httpStatus == null ? "?" : httpStatus));
        }
        if (!StringUtils.hasText(body)) {
            return new ContentCheck(true, false, false, null,
                    "HTTP 成功但响应体为空，无法确认可聊天");
        }

        try {
            JsonNode root = MAPPER.readTree(body);

            // 常见错误字段（部分网关 HTTP 200 仍返回 error）
            if (root.has("error") && !root.get("error").isNull()) {
                String err = summarizeError(root.get("error"));
                return new ContentCheck(true, false, true, null,
                        "响应含 error 字段: " + err);
            }

            String text = extractAssistantText(root, mode);
            if (!StringUtils.hasText(text)) {
                // 有 choices 但 content 空，或 finish_reason 异常
                String finish = firstText(root, "choices", 0, "finish_reason");
                String reason = StringUtils.hasText(finish)
                        ? ("finish_reason=" + finish)
                        : "未解析到 assistant 文本";
                return new ContentCheck(true, false, false, null,
                        "HTTP 成功但无聊天内容（" + reason + "）");
            }

            String trimmed = text.trim();
            boolean ok = isAcceptableProbeReply(trimmed);
            if (ok) {
                return new ContentCheck(true, true, false, preview(trimmed),
                        "真实可聊探测成功（模型返回有效内容）");
            }
            return new ContentCheck(true, false, false, preview(trimmed),
                    "HTTP 成功且有回复，但未按要求返回 1（实际: " + preview(trimmed) + "）");
        } catch (Exception ex) {
            // 非 JSON：若 body 很短且含 1，宽松通过；否则失败
            String raw = body.trim();
            if (raw.length() <= 8 && isAcceptableProbeReply(raw)) {
                return new ContentCheck(true, true, false, preview(raw),
                        "真实可聊探测成功（非 JSON 短回复）");
            }
            return new ContentCheck(true, false, false, null,
                    "响应无法解析为聊天结果: " + sanitize(ex.getMessage()));
        }
    }

    /**
     * 接受：纯 "1"、"1."、"1\n"、带少量空白/标点，或明确包含独立数字 1。
     * 拒绝：空串、纯 reasoning 占位、明显错误话术且无 1。
     */
    static boolean isAcceptableProbeReply(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String t = text.trim();
        if ("1".equals(t) || "\"1\"".equals(t) || "'1'".equals(t)) {
            return true;
        }
        // 去掉常见包裹后仍是 1
        String compact = t.replaceAll("[\\s\"'`*_。．.！!？?：:；;，,\\[\\]()（）]", "");
        if ("1".equals(compact)) {
            return true;
        }
        // 极短回复且含独立 1（允许 "答案是1"）
        if (t.length() <= 16 && HAS_DIGIT_ONE.matcher(t).find()) {
            return true;
        }
        return false;
    }

    private static String extractAssistantText(JsonNode root, String mode) {
        if (root == null) {
            return null;
        }
        // OpenAI chat.completion
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            JsonNode message = first.get("message");
            if (message != null) {
                String content = textOrNull(message.get("content"));
                if (StringUtils.hasText(content)) {
                    return content;
                }
                // 部分兼容网关把文本放在 reasoning/text
                String reasoning = textOrNull(message.get("reasoning_content"));
                if (StringUtils.hasText(reasoning) && !StringUtils.hasText(content)) {
                    // 仅有思考无最终答案：不算可聊成功
                    return null;
                }
            }
            String text = textOrNull(first.get("text"));
            if (StringUtils.hasText(text)) {
                return text;
            }
            JsonNode delta = first.get("delta");
            if (delta != null) {
                String c = textOrNull(delta.get("content"));
                if (StringUtils.hasText(c)) {
                    return c;
                }
            }
        }

        // Gemini generateContent
        JsonNode candidates = root.get("candidates");
        if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
            JsonNode content = candidates.get(0).get("content");
            if (content != null) {
                JsonNode parts = content.get("parts");
                if (parts != null && parts.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        String t = textOrNull(part.get("text"));
                        if (StringUtils.hasText(t)) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(t);
                        }
                    }
                    if (sb.length() > 0) {
                        return sb.toString();
                    }
                }
            }
        }

        // 少数网关直接 data.output / output_text
        String output = textOrNull(root.get("output_text"));
        if (StringUtils.hasText(output)) {
            return output;
        }
        return null;
    }

    private static String firstText(JsonNode root, String arr, int idx, String field) {
        try {
            JsonNode n = root.path(arr).path(idx).path(field);
            return n.isMissingNode() || n.isNull() ? null : n.asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        // content 有时是数组 parts
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    sb.append(item.asText());
                } else if (item.has("text")) {
                    sb.append(item.get("text").asText(""));
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        return null;
    }

    private static String summarizeError(JsonNode errorNode) {
        if (errorNode == null || errorNode.isNull()) {
            return "unknown";
        }
        if (errorNode.isTextual()) {
            return sanitize(errorNode.asText());
        }
        String msg = textOrNull(errorNode.get("message"));
        if (StringUtils.hasText(msg)) {
            return sanitize(msg);
        }
        String code = textOrNull(errorNode.get("code"));
        if (StringUtils.hasText(code)) {
            return sanitize(code);
        }
        return sanitize(errorNode.toString());
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= 40) {
            return t;
        }
        return t.substring(0, 40) + "...";
    }

    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String m = message;
        if (m.length() > 180) {
            m = m.substring(0, 180) + "...";
        }
        return m.replaceAll("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
    }
}
