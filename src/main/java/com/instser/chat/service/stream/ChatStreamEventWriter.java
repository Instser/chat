package com.instser.chat.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.entity.WebSearchResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 SSE 协议写出器（阶段 1.5 / 阶段五过渡实现）。
 *
 * <p>当前仍接收 HttpServletResponse，集中所有前端协议字符串拼装：
 * content(0:)、reasoning(g:)、network(u:)、completion(d:)、error。</p>
 */
@Slf4j
@Component
public class ChatStreamEventWriter {

    private static final boolean ENABLE_SMART_CHUNKING = true;
    /** 非阻塞改造：分块 sleep 会占线程；保持 0。需要“打字机”观感应由前端控制。 */
    private static final long CHUNK_DELAY_MS = 0;
    private static final int MIN_CHUNK_SIZE = 5;
    private static final int MAX_CHUNK_SIZE = 25;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setStreamResponseHeaders(HttpServletResponse response) {
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-vercel-ai-data-stream", "v1");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
    }

    public void writeErrorResponse(HttpServletResponse response, String errorMessage) throws IOException {
        response.getWriter().write(formatError(errorMessage));
        response.getWriter().flush();
    }

    public void writeCustomErrorResponse(HttpServletResponse response, String errorMessage, int errorType) throws IOException {
        response.getWriter().write(formatCustomError(errorMessage, errorType));
        response.getWriter().flush();
    }

    /** 协议编码：错误帧（默认 0: 前缀）。 */
    public String formatError(String errorMessage) {
        return "0:\"" + (errorMessage == null ? "" : errorMessage) + "\"\n";
    }

    /** 协议编码：带类型错误帧。 */
    public String formatCustomError(String errorMessage, int errorType) {
        return errorType + ":\"" + (errorMessage == null ? "" : errorMessage) + "\"\n";
    }

    /** 协议编码：整块 content（无 sleep 分块，供异步路径使用）。 */
    public String formatContentBlock(String content) {
        try {
            return String.format("0:%s\n", objectMapper.writeValueAsString(content == null ? "" : content));
        } catch (Exception e) {
            log.error("编码 content 失败: {}", e.getMessage());
            return "0:\"\"\n";
        }
    }

    /** 协议编码：整块 reasoning（AI SDK v1 g:）。 */
    public String formatReasoningBlock(String reasoning) {
        try {
            return String.format("g:%s\n", objectMapper.writeValueAsString(reasoning == null ? "" : reasoning));
        } catch (Exception e) {
            log.error("编码 reasoning 失败: {}", e.getMessage());
            return "g:\"\"\n";
        }
    }

    /** 协议编码：联网结果多行。 */
    public List<String> formatNetworkResults(List<WebSearchResult> searchResults) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (searchResults == null || searchResults.isEmpty()) {
            return lines;
        }
        for (WebSearchResult result : searchResults) {
            try {
                Map<String, String> resultData = new HashMap<>();
                resultData.put("url", result.getUrl() != null ? result.getUrl() : "");
                resultData.put("title", result.getTitle() != null ? result.getTitle() : "");
                lines.add(String.format("u:%s\n", objectMapper.writeValueAsString(resultData)));
            } catch (Exception e) {
                log.error("编码联网结果失败", e);
            }
        }
        return lines;
    }

    /**
     * 写出内容增量；根据配置选择智能分块或整块发送。
     */
    public void writeContent(String content, HttpServletResponse response) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (ENABLE_SMART_CHUNKING) {
            sendContentBySmartChunking(content, response);
        } else {
            sendContentAsBlock(content, response);
        }
    }

    /**
     * 写出 reasoning 增量（g:）。同步路径整块写出，不做 sleep 分块。
     */
    public void writeReasoning(String reasoning, HttpServletResponse response) {
        if (reasoning == null || reasoning.isEmpty()) {
            return;
        }
        try {
            response.getWriter().write(formatReasoningBlock(reasoning));
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("写出 reasoning 失败: {}", e.getMessage());
        }
    }

    public void sendContentBySmartChunking(String content, HttpServletResponse response) {
        try {
            int optimalChunkSize = getOptimalChunkSize(content);
            int[] codePoints = content.codePoints().toArray();
            StringBuilder chunk = new StringBuilder();
            int sentChars = 0;

            for (int i = 0; i < codePoints.length; i++) {
                int codePoint = codePoints[i];
                String character = new String(Character.toChars(codePoint));
                chunk.append(character);

                if (chunk.length() >= optimalChunkSize || i == codePoints.length - 1) {
                    try {
                        String chunkContent = chunk.toString();
                        String vercelFormat = String.format("0:%s\n", objectMapper.writeValueAsString(chunkContent));
                        response.getWriter().write(vercelFormat);
                        response.getWriter().flush();

                        sentChars += chunkContent.length();
                        chunk.setLength(0);

                        if (CHUNK_DELAY_MS > 0) {
                            Thread.sleep(CHUNK_DELAY_MS);
                        }
                    } catch (InterruptedException e) {
                        log.warn("续写分块发送被中断: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        log.info("尝试继续发送剩余内容...");
                    } catch (IOException e) {
                        log.error("续写发送分块内容失败，尝试重试: {}", e.getMessage());
                        try {
                            String chunkContent = chunk.toString();
                            String vercelFormat = String.format("0:%s\n", objectMapper.writeValueAsString(chunkContent));
                            response.getWriter().write(vercelFormat);
                            response.getWriter().flush();
                            sentChars += chunkContent.length();
                            log.info("续写分块重试发送成功");
                        } catch (Exception retryException) {
                            log.error("续写分块重试也失败，跳过此块: {}", retryException.getMessage());
                        }
                        chunk.setLength(0);
                    }
                }
            }

            log.debug("完成智能分块流式发送，总字符数: {}, 实际发送: {}, 分块大小: {}",
                    codePoints.length, sentChars, optimalChunkSize);
        } catch (Exception e) {
            log.error("智能分块发送内容失败: {}", e.getMessage());
            try {
                String vercelFormat = String.format("0:%s\n", objectMapper.writeValueAsString(content));
                response.getWriter().write(vercelFormat);
                response.getWriter().flush();
                log.info("回退到块发送模式: [{}]", content);
            } catch (Exception fallbackException) {
                log.error("回退发送也失败: {}", fallbackException.getMessage());
            }
        }
    }

    public void sendContentAsBlock(String content, HttpServletResponse response) {
        try {
            if (content != null && !content.isEmpty()) {
                response.getWriter().write(formatContentBlock(content));
                response.getWriter().flush();
                log.info("块发送模式数据发送成功: [{}]", content);
            } else {
                log.debug("块发送模式：内容为空，跳过发送");
            }
        } catch (Exception e) {
            log.error("块发送模式失败: {}", e.getMessage());
        }
    }

    public int getOptimalChunkSize(String content) {
        int length = content == null ? 0 : content.length();

        if (length <= 20) {
            return length;
        } else if (length <= 100) {
            return Math.max(MIN_CHUNK_SIZE, Math.min(length / 4, 10));
        } else if (length <= 300) {
            return Math.max(10, Math.min(length / 8, 15));
        } else {
            return Math.max(15, Math.min(MAX_CHUNK_SIZE, length / 12));
        }
    }

    public String buildInterruptCompletionData() {
        try {
            Map<String, Object> interruptMap = new HashMap<>();
            interruptMap.put("finishReason", "interrupted");
            interruptMap.put("usage", Map.of(
                    "promptTokens", 0,
                    "completionTokens", 0
            ));
            interruptMap.put("title", false);
            interruptMap.put("interrupted", true);

            return String.format("d:%s\n", objectMapper.writeValueAsString(interruptMap));
        } catch (Exception e) {
            log.error("构建中断完成标记数据失败", e);
            return "d:{\"finishReason\":\"interrupted\",\"usage\":{\"promptTokens\":0,\"completionTokens\":0},\"title\":false,\"interrupted\":true}\n";
        }
    }

    public void writeInterruptCompletion(HttpServletResponse response) throws IOException {
        response.getWriter().write(buildInterruptCompletionData());
        response.getWriter().flush();
    }

    public String buildCompletionData(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) {
        try {
            log.info("=== 构建完成标记数据 ===");
            log.info("willGenerateTitle: {}, isRealAiResponse: {}, generatedTitle: [{}]",
                    willGenerateTitle, isRealAiResponse, generatedTitle);

            Map<String, Object> completionMap = new HashMap<>();
            completionMap.put("finishReason", "stop");
            completionMap.put("usage", Map.of(
                    "promptTokens", 10,
                    "completionTokens", 20
            ));

            if (isRealAiResponse && willGenerateTitle) {
                if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                    completionMap.put("title", generatedTitle.trim());
                    log.info("标题将返回给前端: [{}]", generatedTitle.trim());
                } else {
                    completionMap.put("title", false);
                    LogUtils.logUserWarning(log, null, "标题生成", "标题生成失败或为空，返回false给前端，原因: 生成的标题为null或空字符串");
                }
            } else {
                completionMap.put("title", false);
                log.info("不需要生成标题，返回false给前端 - willGenerateTitle: {}, isRealAiResponse: {}",
                        willGenerateTitle, isRealAiResponse);
            }

            String result = String.format("d:%s\n", objectMapper.writeValueAsString(completionMap));
            log.info("完成标记数据构建完成: {}", result.trim());
            log.info("=== 构建完成标记数据结束 ===");
            return result;
        } catch (Exception e) {
            log.error("构建完成标记数据失败", e);
            return "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":10,\"completionTokens\":20},\"title\":false}\n";
        }
    }

    public void writeCompletion(HttpServletResponse response, boolean willGenerateTitle,
                                boolean isRealAiResponse, String generatedTitle) throws IOException {
        response.getWriter().write(buildCompletionData(willGenerateTitle, isRealAiResponse, generatedTitle));
        response.getWriter().flush();
    }

    public void sendNetworkSearchResults(HttpServletResponse response, List<WebSearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return;
        }

        try {
            log.info("开始发送{}个联网搜索结果给前端", searchResults.size());

            for (String line : formatNetworkResults(searchResults)) {
                response.getWriter().write(line);
                response.getWriter().flush();
            }

            log.info("成功发送{}个联网搜索结果给前端", searchResults.size());
        } catch (Exception e) {
            log.error("发送联网搜索结果失败", e);
        }
    }

    /**
     * 阶段五：业务内部事件 -> 前端 SSE 协议唯一出口。
     * 当前仍持有 HttpServletResponse，作为去 Servlet 化前的过渡适配层。
     */
    public void writeEvent(HttpServletResponse response, ChatStreamEvent event) throws IOException {
        if (event == null || event.getType() == null) {
            return;
        }
        switch (event.getType()) {
            case CONTENT_DELTA -> writeContent(event.getText(), response);
            case REASONING_DELTA -> writeReasoning(event.getText(), response);
            case NETWORK_EVIDENCE -> sendNetworkSearchResults(response, event.getNetworkResults());
            case COMPLETED -> writeCompletion(response, event.isWillGenerateTitle(), event.isRealAiResponse(), event.getGeneratedTitle());
            case FAILED -> {
                if (event.getErrorType() != null && event.getErrorType() != 0) {
                    writeCustomErrorResponse(response, event.getErrorMessage(), event.getErrorType());
                } else {
                    writeErrorResponse(response, event.getErrorMessage());
                }
            }
            case INTERRUPTED -> writeInterruptCompletion(response);
            default -> log.warn("未知 ChatStreamEvent 类型: {}", event.getType());
        }
    }
}
