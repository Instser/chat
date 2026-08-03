package com.instser.chat.service.stream;

import com.instser.chat.entity.WebSearchResult;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 业务层内部流事件（阶段五协议边界）。
 *
 * <p>业务只产生事件；SSE 文本协议仅由 ChatStreamEventWriter 知道。</p>
 */
@Getter
public final class ChatStreamEvent {

    public enum Type {
        CONTENT_DELTA,
        REASONING_DELTA,
        NETWORK_EVIDENCE,
        COMPLETED,
        FAILED,
        INTERRUPTED
    }

    private final Type type;
    private final String text;
    private final List<WebSearchResult> networkResults;
    private final boolean willGenerateTitle;
    private final boolean realAiResponse;
    private final String generatedTitle;
    private final String errorMessage;
    private final Integer errorType;

    private ChatStreamEvent(Type type,
                            String text,
                            List<WebSearchResult> networkResults,
                            boolean willGenerateTitle,
                            boolean realAiResponse,
                            String generatedTitle,
                            String errorMessage,
                            Integer errorType) {
        this.type = type;
        this.text = text;
        this.networkResults = networkResults;
        this.willGenerateTitle = willGenerateTitle;
        this.realAiResponse = realAiResponse;
        this.generatedTitle = generatedTitle;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    public static ChatStreamEvent content(String text) {
        return new ChatStreamEvent(Type.CONTENT_DELTA, text, null, false, false, null, null, null);
    }

    public static ChatStreamEvent reasoning(String text) {
        return new ChatStreamEvent(Type.REASONING_DELTA, text, null, false, false, null, null, null);
    }

    public static ChatStreamEvent network(List<WebSearchResult> results) {
        return new ChatStreamEvent(Type.NETWORK_EVIDENCE, null,
                results == null ? Collections.emptyList() : results,
                false, false, null, null, null);
    }

    public static ChatStreamEvent completed(boolean willGenerateTitle, boolean realAiResponse, String generatedTitle) {
        return new ChatStreamEvent(Type.COMPLETED, null, null, willGenerateTitle, realAiResponse, generatedTitle, null, null);
    }

    public static ChatStreamEvent failed(String errorMessage) {
        return new ChatStreamEvent(Type.FAILED, null, null, false, false, null, errorMessage, 0);
    }

    public static ChatStreamEvent failed(String errorMessage, int errorType) {
        return new ChatStreamEvent(Type.FAILED, null, null, false, false, null, errorMessage, errorType);
    }

    public static ChatStreamEvent interrupted() {
        return new ChatStreamEvent(Type.INTERRUPTED, null, null, false, false, null, null, null);
    }
}
