package com.instser.chat.service.context;

import com.instser.chat.entity.WebSearchResult;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 调用前冻结的上下文产物。
 *
 * <p>originalMessages 仅含历史原始内容，供标题生成；
 * aiMessages 可含文件/联网拼接，仅供上游模型。</p>
 */
@Getter
@Builder
public class PreparedChatContext {

    private final List<Map<String, String>> aiMessages;
    private final List<Map<String, String>> originalMessages;
    private final List<WebSearchResult> networkSearchResults;

    public List<Map<String, String>> getAiMessages() {
        return aiMessages == null ? Collections.emptyList() : aiMessages;
    }

    public List<Map<String, String>> getOriginalMessages() {
        return originalMessages == null ? Collections.emptyList() : originalMessages;
    }

    public List<WebSearchResult> getNetworkSearchResults() {
        return networkSearchResults == null ? Collections.emptyList() : networkSearchResults;
    }

    public boolean hasNetworkSearchResults() {
        return networkSearchResults != null && !networkSearchResults.isEmpty();
    }
}
