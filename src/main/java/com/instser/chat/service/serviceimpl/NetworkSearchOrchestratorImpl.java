package com.instser.chat.service.serviceimpl;

import com.instser.chat.dto.NetworkSearchProcessResult;
import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.entity.GeminiSource;
import com.instser.chat.entity.GeminiTextSegment;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.NetworkAiService;
import com.instser.chat.service.NetworkSearchOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkSearchOrchestratorImpl implements NetworkSearchOrchestrator {

    private final NetworkAiService networkAiService;

    @Override
    public NetworkSearchProcessResult processNetworkContent(List<Map<String, String>> messages, String query) {
        if (messages == null || messages.isEmpty()) {
            return new NetworkSearchProcessResult(messages, new ArrayList<>());
        }
        try {
            log.info("【Gemini联网】开始处理联网搜索内容，最新查询：{}，会话消息数量：{}", query, messages.size());
            String fullContext = buildFullContextForNetworkSearch(messages);
            GeminiGroundingResponse geminiResponse = networkAiService.queryWithGeminiGrounding(fullContext);
            if (geminiResponse == null) {
                log.warn("【Gemini联网】Gemini响应为空，上下文长度：{}", fullContext.length());
                return new NetworkSearchProcessResult(messages, new ArrayList<>());
            }
            String formattedNetworkContent = formatCompleteGeminiResponse(geminiResponse);
            List<Map<String, String>> updatedMessages = new ArrayList<>(messages);
            if (!updatedMessages.isEmpty()) {
                Map<String, String> lastMessage = updatedMessages.get(updatedMessages.size() - 1);
                if ("user".equals(lastMessage.get("role"))) {
                    String originalContent = lastMessage.get("content");
                    String updatedContent = originalContent + "\n\nBelow is the networking content you get from your own search:\n\n" + formattedNetworkContent;
                    lastMessage.put("content", updatedContent);
                }
            }
            return new NetworkSearchProcessResult(updatedMessages, convertGeminiSourcesToWebSearchResults(geminiResponse.getSources()));
        } catch (Exception e) {
            log.error("处理联网搜索内容失败，查询：{}，消息数量：{}", query, messages.size(), e);
            return new NetworkSearchProcessResult(messages, new ArrayList<>());
        }
    }

    private String buildFullContextForNetworkSearch(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("以下是用户与AI助手的完整对话历史，请基于这个上下文理解用户的最新需求并进行网络搜索：\n\n");
        for (Map<String, String> message : messages) {
            String role = message.get("role");
            String content = message.get("content");
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            String cleanContent = extractOriginalUserContent(content);
            if ("user".equals(role)) {
                contextBuilder.append("用户: ").append(cleanContent).append("\n\n");
            } else if ("assistant".equals(role)) {
                String shortContent = cleanContent.length() > 200 ? cleanContent.substring(0, 200) + "..." : cleanContent;
                contextBuilder.append("AI助手: ").append(shortContent).append("\n\n");
            }
        }
        contextBuilder.append("请基于以上对话上下文，理解用户的最新需求并进行相关的网络搜索。");
        return contextBuilder.toString();
    }

    private String extractOriginalUserContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        String originalContent = content.trim();
        if (originalContent.contains("。以下是文件内容：[")) {
            originalContent = originalContent.substring(0, originalContent.indexOf("。以下是文件内容：[")).trim();
        }
        if (originalContent.contains("以下是你自己联网搜索得到的结果：[")) {
            originalContent = originalContent.substring(0, originalContent.indexOf("以下是你自己联网搜索得到的结果：[")).trim();
        }
        return originalContent.isEmpty() ? content : originalContent;
    }

    private String formatCompleteGeminiResponse(GeminiGroundingResponse response) {
        StringBuilder formatted = new StringBuilder();
        if (response.getResponseText() != null) {
            formatted.append(response.getResponseText()).append("\n\n");
        }
        if (response.getSearchQueries() != null && !response.getSearchQueries().isEmpty()) {
            formatted.append("Search Queries:\n");
            for (String query : response.getSearchQueries()) {
                formatted.append("- ").append(query).append("\n");
            }
            formatted.append("\n");
        }
        if (response.getSources() != null && !response.getSources().isEmpty()) {
            formatted.append("Sources:\n");
            for (int i = 0; i < response.getSources().size(); i++) {
                GeminiSource source = response.getSources().get(i);
                formatted.append(String.format("[%d] %s - %s\n", i + 1, source.getTitle(), source.getUrl()));
            }
            formatted.append("\n");
        }
        if (response.getTextSegments() != null && !response.getTextSegments().isEmpty()) {
            formatted.append("Citation Details:\n");
            for (GeminiTextSegment segment : response.getTextSegments()) {
                if (segment.getText() != null) {
                    formatted.append("\"").append(segment.getText()).append("\" ");
                    if (segment.getSourceIndices() != null && !segment.getSourceIndices().isEmpty()) {
                        formatted.append("(Sources: ");
                        for (Integer index : segment.getSourceIndices()) {
                            formatted.append("[").append(index + 1).append("] ");
                        }
                        formatted.append(")");
                    }
                    formatted.append("\n");
                }
            }
        }
        return formatted.toString().trim();
    }

    private List<WebSearchResult> convertGeminiSourcesToWebSearchResults(List<GeminiSource> sources) {
        if (sources == null) {
            return new ArrayList<>();
        }
        return sources.stream()
                .map(source -> {
                    WebSearchResult result = new WebSearchResult();
                    result.setUrl(source.getUrl());
                    result.setTitle(source.getTitle());
                    return result;
                })
                .collect(Collectors.toList());
    }
}
