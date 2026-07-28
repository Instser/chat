package com.instser.chat.service.stream;

import com.instser.chat.entity.WebSearchResult;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 业务层流写出端口。业务代码依赖此接口，不直接依赖 HttpServletResponse。
 */
public interface ChatStreamSink {

    void setHeaders();

    void writeError(String errorMessage) throws IOException;

    void writeCustomError(String errorMessage, int errorType) throws IOException;

    void writeContent(String content);

    void writeNetworkResults(List<WebSearchResult> results);

    void writeCompletion(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) throws IOException;

    void writeInterruptCompletion() throws IOException;

    /**
     * 过渡期：少数底层组件仍需要原生 response。
     * 终局可删除此方法。
     */
    HttpServletResponse rawResponse();

    static ChatStreamSink of(HttpServletResponse response, ChatStreamEventWriter writer) {
        return new ChatStreamSink() {
            @Override
            public void setHeaders() {
                writer.setStreamResponseHeaders(response);
            }

            @Override
            public void writeError(String errorMessage) throws IOException {
                writer.writeErrorResponse(response, errorMessage);
            }

            @Override
            public void writeCustomError(String errorMessage, int errorType) throws IOException {
                writer.writeCustomErrorResponse(response, errorMessage, errorType);
            }

            @Override
            public void writeContent(String content) {
                writer.writeContent(content, response);
            }

            @Override
            public void writeNetworkResults(List<WebSearchResult> results) {
                writer.sendNetworkSearchResults(response, results);
            }

            @Override
            public void writeCompletion(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) throws IOException {
                writer.writeCompletion(response, willGenerateTitle, isRealAiResponse, generatedTitle);
            }

            @Override
            public void writeInterruptCompletion() throws IOException {
                writer.writeInterruptCompletion(response);
            }

            @Override
            public HttpServletResponse rawResponse() {
                return response;
            }
        };
    }
}
