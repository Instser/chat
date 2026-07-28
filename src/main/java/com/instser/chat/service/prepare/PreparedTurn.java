package com.instser.chat.service.prepare;

import com.instser.chat.entity.WebSearchResult;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用例准备阶段产物：会话 turn 已就绪，可供 Guard / Context / Stream 使用。
 */
@Getter
@Builder
public class PreparedTurn {

    private final boolean success;
    private final String errorMessage;
    private final Long userId;
    private final String sessionId;
    private final Long modelId;
    private final String userMessageId;
    private final List<Map<String, String>> messages;
    private final List<Map<String, String>> originalMessages;
    private final List<WebSearchResult> networkSearchResults;
    private final boolean willGenerateTitle;

    public static PreparedTurn failure(String errorMessage) {
        return PreparedTurn.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public List<Map<String, String>> getMessages() {
        return messages == null ? Collections.emptyList() : messages;
    }

    public List<Map<String, String>> getOriginalMessages() {
        return originalMessages == null ? Collections.emptyList() : originalMessages;
    }

    public List<WebSearchResult> getNetworkSearchResults() {
        return networkSearchResults == null ? Collections.emptyList() : networkSearchResults;
    }
}
