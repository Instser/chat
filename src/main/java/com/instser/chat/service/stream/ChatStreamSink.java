package com.instser.chat.service.stream;

import com.instser.chat.entity.WebSearchResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 业务层流写出端口。业务代码依赖此接口，不直接依赖 HttpServletResponse / Emitter。
 *
 * <p>异步实现使用 {@link ResponseBodyEmitter} 而非 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}，
 * 因为现网协议是 Vercel AI data stream 原始行（{@code 0:}/{@code g:}/{@code d:}/{@code u:}），
 * SseEmitter 会再包一层 {@code data:} 帧，破坏前端解析。</p>
 */
public interface ChatStreamSink {

    void setHeaders();

    void writeError(String errorMessage) throws IOException;

    void writeCustomError(String errorMessage, int errorType) throws IOException;

    void writeContent(String content);

    void writeReasoning(String reasoning);

    void writeNetworkResults(List<WebSearchResult> results);

    void writeCompletion(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) throws IOException;

    void writeInterruptCompletion() throws IOException;

    /**
     * 正常结束通道（异步 emitter 必调；同步 Servlet 实现可为空操作）。
     */
    default void complete() {
        // no-op for servlet adapter
    }

    /**
     * 异常结束通道。
     */
    default void completeWithError(Throwable error) {
        // no-op for servlet adapter
    }

    /**
     * 是否为异步 emitter 写出（Controller 快速返回场景）。
     */
    default boolean isAsync() {
        return false;
    }

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
            public void writeReasoning(String reasoning) {
                writer.writeReasoning(reasoning, response);
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
        };
    }

    /**
     * 异步通道适配：原始协议字符串经 ResponseBodyEmitter 写出，不经 SseEmitter 二次包装。
     *
     * @param emitter 超时由 Controller 创建时指定；0L 表示不超时（仍受 MVC/网关约束）
     */
    static ChatStreamSink of(ResponseBodyEmitter emitter, ChatStreamEventWriter writer) {
        final Object writeLock = new Object();
        return new ChatStreamSink() {
            private volatile boolean finished;

            @Override
            public void setHeaders() {
                // ResponseBodyEmitter 场景下 Content-Type 由 Controller produces / 返回值处理；
                // 额外业务头（如 x-vercel-ai-data-stream）在 Controller 创建 emitter 前写入 HttpServletResponse。
            }

            @Override
            public void writeError(String errorMessage) throws IOException {
                sendRaw(writer.formatError(errorMessage));
            }

            @Override
            public void writeCustomError(String errorMessage, int errorType) throws IOException {
                sendRaw(writer.formatCustomError(errorMessage, errorType));
            }

            @Override
            public void writeContent(String content) {
                if (content == null || content.isEmpty()) {
                    return;
                }
                try {
                    // 异步路径不做 Thread.sleep 智能分块，避免阻塞 reactor/业务线程
                    sendRaw(writer.formatContentBlock(content));
                } catch (IOException e) {
                    throw new IllegalStateException("异步写出 content 失败", e);
                }
            }

            @Override
            public void writeReasoning(String reasoning) {
                if (reasoning == null || reasoning.isEmpty()) {
                    return;
                }
                try {
                    sendRaw(writer.formatReasoningBlock(reasoning));
                } catch (IOException e) {
                    throw new IllegalStateException("异步写出 reasoning 失败", e);
                }
            }

            @Override
            public void writeNetworkResults(List<WebSearchResult> results) {
                if (results == null || results.isEmpty()) {
                    return;
                }
                try {
                    for (String line : writer.formatNetworkResults(results)) {
                        sendRaw(line);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("异步写出 network 失败", e);
                }
            }

            @Override
            public void writeCompletion(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) throws IOException {
                sendRaw(writer.buildCompletionData(willGenerateTitle, isRealAiResponse, generatedTitle));
            }

            @Override
            public void writeInterruptCompletion() throws IOException {
                sendRaw(writer.buildInterruptCompletionData());
            }

            @Override
            public void complete() {
                synchronized (writeLock) {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // already completed
                    }
                }
            }

            @Override
            public void completeWithError(Throwable error) {
                synchronized (writeLock) {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    try {
                        emitter.completeWithError(error != null ? error : new IllegalStateException("stream error"));
                    } catch (Exception ignored) {
                        // already completed
                    }
                }
            }

            @Override
            public boolean isAsync() {
                return true;
            }

            private void sendRaw(String payload) throws IOException {
                if (payload == null || payload.isEmpty()) {
                    return;
                }
                synchronized (writeLock) {
                    if (finished) {
                        throw new IOException("emitter already completed");
                    }
                    emitter.send(payload, MediaType.TEXT_PLAIN);
                }
            }
        };
    }
}
