package com.instser.chat.service.stream;

import com.instser.chat.entity.WebSearchResult;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 业务层流写出端口。业务代码依赖此接口，不直接依赖 HttpServletResponse。
 *
 * <p>异步实现使用 Servlet {@link AsyncContext} 直接写 {@link ServletOutputStream}，
 * 保证 chunked 响应写出终止块（{@code 0\r\n\r\n}）。
 * 曾用 ResponseBodyEmitter 跨线程 complete 会出现「内容完整但缺终止块 → 浏览器 Network error」。</p>
 *
 * <p>协议为 Vercel AI data stream 原始行（{@code 0:}/{@code g:}/{@code d:}/{@code u:}），
 * 禁止用 SseEmitter（会再包一层 {@code data:} 帧）。</p>
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

    /** 正常结束通道（异步必调；同步 Servlet 实现可为空操作）。 */
    default void complete() {
        // no-op for servlet adapter
    }

    /** 异常结束通道。 */
    default void completeWithError(Throwable error) {
        // no-op for servlet adapter
    }

    /** 是否为异步写出（Controller 快速返回场景）。 */
    default boolean isAsync() {
        return false;
    }

    /** 同步路径：直接写 HttpServletResponse（单测 / 同步调用）。 */
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
     * 异步通道：AsyncContext + OutputStream，保证 chunked 终止块被写出。
     *
     * <p>headers 须在 startAsync 前由 Controller 写入；本方法只负责 body 与 complete。</p>
     */
    static ChatStreamSink ofAsync(AsyncContext asyncContext, ChatStreamEventWriter writer) {
        final Object writeLock = new Object();
        final HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
        return new ChatStreamSink() {
            private volatile boolean finished;

            @Override
            public void setHeaders() {
                // headers 已在 startAsync 前设置
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
                        response.getOutputStream().flush();
                    } catch (Exception ignored) {
                        // flush best-effort
                    }
                    try {
                        asyncContext.complete();
                    } catch (Exception ignored) {
                        // already completed
                    }
                }
            }

            @Override
            public void completeWithError(Throwable error) {
                // 流协议侧尽量已写 error/d 帧；此处只保证通道干净关闭
                complete();
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
                        throw new IOException("async stream already completed");
                    }
                    ServletOutputStream out = response.getOutputStream();
                    out.write(payload.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        };
    }
}
