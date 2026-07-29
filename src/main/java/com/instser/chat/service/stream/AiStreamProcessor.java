package com.instser.chat.service.stream;

import com.instser.chat.constants.PromptConstants;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.monitor.AiCallMonitor;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.ai.AiChatClient;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.util.ChatSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * AI 主聊天流处理器：负责 prompt 组装、上游流消费、内容写出与状态维护。
 * 完成副作用（落库/标题/usage）由外部 completionCallback 处理。
 *
 * <p>异步路径（{@link ChatStreamSink#isAsync()} = true）使用 subscribe + registry，不 blockLast。
 * 同步路径保留 blockLast 兼容旧 Controller/测试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStreamProcessor {

    private final AiChatClient aiChatClient;
    private final ContentPolicy contentPolicy;
    private final ChatInterruptService chatInterruptService;
    private final AiCallMonitor aiCallMonitor;
    private final StreamSubscriptionRegistry streamSubscriptionRegistry;

    @FunctionalInterface
    public interface CompletionCallback {
        /**
         * @return 是否最终中断
         */
        boolean onComplete(ChatStreamSink sink,
                           boolean willGenerateTitle,
                           boolean isRealAiResponse,
                           ChatRequestDTO request,
                           StringBuilder aiResponseContent,
                           AiModel model,
                           String interfaceType,
                           boolean wasManuallyInterrupted);
    }

    @FunctionalInterface
    public interface ErrorPersister {
        void saveError(String sessionId, Long userId, Long modelId, String errorContent);
    }

    public ChatResponseDTO stream(ChatRequestDTO request,
                                  AiModel model,
                                  ChatStreamSink sink,
                                  boolean willGenerateTitle,
                                  String interfaceType,
                                  CompletionCallback completionCallback,
                                  ErrorPersister errorPersister,
                                  BiFunction<Map<String, Object>, AiModel, Void> requestLogger) {
        if (sink != null && sink.isAsync()) {
            return streamAsync(request, model, sink, willGenerateTitle, interfaceType,
                    completionCallback, errorPersister, requestLogger);
        }
        return streamBlocking(request, model, sink, willGenerateTitle, interfaceType,
                completionCallback, errorPersister, requestLogger);
    }

    /**
     * 同步阻塞路径（兼容旧 Controller/单测）。
     */
    private ChatResponseDTO streamBlocking(ChatRequestDTO request,
                                           AiModel model,
                                           ChatStreamSink sink,
                                           boolean willGenerateTitle,
                                           String interfaceType,
                                           CompletionCallback completionCallback,
                                           ErrorPersister errorPersister,
                                           BiFunction<Map<String, Object>, AiModel, Void> requestLogger) {
        try (ChatSessionManager sessionManager = new ChatSessionManager(
                chatInterruptService, request.getSessionId(), request.getUserId())) {
            sessionManager.register();

            final StreamResponseState streamState = new StreamResponseState();
            final StringBuilder aiResponseContent = new StringBuilder();
            final AiCallMonitor.CallContext[] aiCtxHolder = {null};

            try {
                Flux<String> lineFlux = buildLineFlux(request, model, sink, willGenerateTitle, interfaceType,
                        completionCallback, errorPersister, requestLogger, streamState, aiResponseContent, aiCtxHolder);

                lineFlux
                        .onErrorReturn("")
                        .blockLast();

                if (streamState.isInterrupted()) {
                    aiCallMonitor.recordSuccess(aiCtxHolder[0]);
                    return ChatResponseDTO.interrupted(streamState.hasContentSent(), aiResponseContent.toString());
                }
                aiCallMonitor.recordSuccess(aiCtxHolder[0]);
                return ChatResponseDTO.success(streamState.hasContentSent(), streamState.isRealAiResponse(), aiResponseContent.toString());
            } catch (ContentPolicy.ChatRetryException e) {
                throw e;
            } catch (Exception e) {
                log.error("流式聊天异常", e);
                aiCallMonitor.recordFailure(aiCtxHolder[0], e.getClass().getSimpleName(), e.getMessage());
                try {
                    String errorContent = "Request failed. Please try again later.";
                    sink.writeContent(errorContent);
                    sink.writeCompletion(false, false, null);
                    if (request.getSessionId() != null && request.getUserId() != null) {
                        errorPersister.saveError(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                    }
                    return ChatResponseDTO.errorWithContent(errorContent);
                } catch (Exception ioException) {
                    log.error("发送异常消息失败", ioException);
                    return ChatResponseDTO.failure("System error");
                }
            }
        }
    }

    /**
     * 异步订阅路径：请求线程立即返回；完成/错误在回调中处理并 complete emitter。
     */
    private ChatResponseDTO streamAsync(ChatRequestDTO request,
                                        AiModel model,
                                        ChatStreamSink sink,
                                        boolean willGenerateTitle,
                                        String interfaceType,
                                        CompletionCallback completionCallback,
                                        ErrorPersister errorPersister,
                                        BiFunction<Map<String, Object>, AiModel, Void> requestLogger) {
        ChatSessionManager sessionManager = new ChatSessionManager(
                chatInterruptService, request.getSessionId(), request.getUserId());
        sessionManager.register();

        final StreamResponseState streamState = new StreamResponseState();
        final StringBuilder aiResponseContent = new StringBuilder();
        final AiCallMonitor.CallContext[] aiCtxHolder = {null};
        final AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        try {
            Flux<String> lineFlux = buildLineFlux(request, model, sink, willGenerateTitle, interfaceType,
                    completionCallback, errorPersister, requestLogger, streamState, aiResponseContent, aiCtxHolder);

            Disposable disposable = lineFlux
                    .doOnCancel(() -> log.info("上游流被取消 sessionId={}", request.getSessionId()))
                    .subscribe(
                            line -> {
                                // 行处理已在 doOnNext（publishOn 后 content 写在 boundedElastic）
                            },
                            error -> {
                                log.error("异步流错误 sessionId={}", request.getSessionId(), error);
                                try {
                                    // doOnError 可能已写过错误帧；此处只记监控与收敛
                                    aiCallMonitor.recordFailure(aiCtxHolder[0],
                                            error.getClass().getSimpleName(), error.getMessage());
                                    log.info("STREAM_TERMINAL sessionId={} reason=failure type={}",
                                            request.getSessionId(), error.getClass().getSimpleName());
                                } catch (Exception e) {
                                    log.error("异步错误收尾失败", e);
                                } finally {
                                    cleanupAsync(request.getSessionId(), sessionManager, disposableRef);
                                    sink.complete();
                                }
                            },
                            () -> {
                                try {
                                    aiCallMonitor.recordSuccess(aiCtxHolder[0]);
                                    String reason = streamState.isInterrupted() ? "interrupted_or_partial" : "success";
                                    log.info("STREAM_TERMINAL sessionId={} reason={} interrupted={} contentLen={}",
                                            request.getSessionId(), reason, streamState.isInterrupted(),
                                            aiResponseContent.length());
                                } finally {
                                    cleanupAsync(request.getSessionId(), sessionManager, disposableRef);
                                    sink.complete();
                                }
                            }
                    );

            disposableRef.set(disposable);
            if (request.getSessionId() != null) {
                streamSubscriptionRegistry.register(request.getSessionId(), disposable);
            }
            // 异步：立即返回 accepted，不表示 AI 已完成
            return ChatResponseDTO.success(false, false, null);
        } catch (ContentPolicy.ChatRetryException e) {
            cleanupAsync(request.getSessionId(), sessionManager, disposableRef);
            throw e;
        } catch (Exception e) {
            log.error("异步流启动失败", e);
            aiCallMonitor.recordFailure(aiCtxHolder[0], e.getClass().getSimpleName(), e.getMessage());
            try {
                String errorContent = "Request failed. Please try again later.";
                sink.writeContent(errorContent);
                sink.writeCompletion(false, false, null);
                if (request.getSessionId() != null && request.getUserId() != null) {
                    errorPersister.saveError(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                }
            } catch (Exception ioException) {
                log.error("异步启动失败写出错误", ioException);
            } finally {
                cleanupAsync(request.getSessionId(), sessionManager, disposableRef);
                sink.completeWithError(e);
            }
            return ChatResponseDTO.errorWithContent("Request failed. Please try again later.");
        }
    }

    private void cleanupAsync(String sessionId, ChatSessionManager sessionManager,
                              AtomicReference<Disposable> disposableRef) {
        try {
            if (sessionId != null) {
                streamSubscriptionRegistry.remove(sessionId);
            }
        } catch (Exception e) {
            log.warn("清理订阅注册失败 sessionId={}", sessionId, e);
        }
        try {
            sessionManager.close();
        } catch (Exception e) {
            log.warn("清理会话失败 sessionId={}", sessionId, e);
        }
        disposableRef.getAndSet(null);
    }

    private Flux<String> buildLineFlux(ChatRequestDTO request,
                                       AiModel model,
                                       ChatStreamSink sink,
                                       boolean willGenerateTitle,
                                       String interfaceType,
                                       CompletionCallback completionCallback,
                                       ErrorPersister errorPersister,
                                       BiFunction<Map<String, Object>, AiModel, Void> requestLogger,
                                       StreamResponseState streamState,
                                       StringBuilder aiResponseContent,
                                       AiCallMonitor.CallContext[] aiCtxHolder) {
        List<Map<String, String>> messagesWithPrompt = buildMessagesWithPrompt(request);
        log.info("使用{}提示词（伪造对话形式），消息总数: {}",
                Boolean.TRUE.equals(request.getDeepThinking()) ? "深度思考" : "默认",
                messagesWithPrompt.size());

        StringBuilder jsonBuffer = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();

        Map<String, Object> requestBody = aiChatClient.buildStreamRequestBody(model, messagesWithPrompt);
        if (requestLogger != null) {
            requestLogger.apply(requestBody, model);
        }
        aiCtxHolder[0] = aiCallMonitor.beginCall(model.getModelCode());
        log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());

        return aiChatClient.stream(model, requestBody, 30)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String chunk = new String(bytes, StandardCharsets.UTF_8);
                    return chunk.replaceAll("[\uFFFD\u0000]", "");
                })
                .flatMap(chunk -> {
                    lineBuffer.append(chunk);
                    String bufferedContent = lineBuffer.toString();
                    String[] lines = bufferedContent.split("\n", -1);
                    List<String> validLines = new ArrayList<>();
                    for (int i = 0; i < lines.length - 1; i++) {
                        String line = lines[i].trim();
                        if (!line.isEmpty()) {
                            validLines.add(line);
                        }
                    }
                    String lastLine = lines[lines.length - 1];
                    lineBuffer.setLength(0);
                    if (!lastLine.isEmpty()) {
                        lineBuffer.append(lastLine);
                    }
                    return Flux.fromIterable(validLines);
                })
                .takeWhile(line -> {
                    if (request.getSessionId() != null && chatInterruptService.isInterrupted(request.getSessionId())) {
                        log.info("检测到聊天中断信号，停止流处理，会话ID：{}", request.getSessionId());
                        streamState.markInterrupted();
                        if (!streamState.isInterruptSaved()) {
                            try {
                                sink.writeInterruptCompletion();
                                log.info("已发送中断完成标记");
                            } catch (Exception e) {
                                log.error("发送中断完成标记失败", e);
                            } finally {
                                streamState.markInterruptSaved();
                            }
                        }
                        return false;
                    }
                    return true;
                })
                // 收尾/续写/落库切到弹性线程，避免在 reactor-http 线程上 join/DB 造成假非阻塞
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(line -> {
                    try {
                        if (line.startsWith("data: ")) {
                            if (line.equals("data: [DONE]")) {
                                // 完成统一在 doOnComplete + tryHandleCompletion，避免与收尾双发
                                log.debug("收到 data: [DONE]，等待 doOnComplete 收尾 sessionId={}", request.getSessionId());
                                return;
                            }

                            String jsonData = line.substring(6).trim();
                            jsonBuffer.append(jsonData);
                            String bufferedJson = jsonBuffer.toString();
                            if (isValidJson(bufferedJson)) {
                                try {
                                    String contentSent = processCompleteJson(bufferedJson, sink);
                                    if (contentSent != null) {
                                        maybeSendNetwork(request, sink, streamState);
                                        streamState.markRealAiResponse();
                                        aiResponseContent.append(contentSent);
                                        if (!streamState.isFirstTokenMarked()) {
                                            streamState.markFirstToken();
                                            aiCallMonitor.markFirstToken(aiCtxHolder[0]);
                                        }
                                    }
                                    jsonBuffer.setLength(0);
                                } catch (ContentPolicy.ChatRetryException e) {
                                    throw e;
                                }
                            }
                        } else if (!line.trim().isEmpty() && jsonBuffer.length() > 0) {
                            if (jsonBuffer.length() > 10000) {
                                jsonBuffer.setLength(0);
                                return;
                            }
                            jsonBuffer.append(line.trim());
                            String bufferedJson = jsonBuffer.toString();
                            if (isValidJson(bufferedJson)) {
                                try {
                                    String contentSent = processCompleteJson(bufferedJson, sink);
                                    if (contentSent != null) {
                                        maybeSendNetwork(request, sink, streamState);
                                        streamState.markRealAiResponse();
                                        aiResponseContent.append(contentSent);
                                        if (!streamState.isFirstTokenMarked()) {
                                            streamState.markFirstToken();
                                            aiCallMonitor.markFirstToken(aiCtxHolder[0]);
                                        }
                                    }
                                    jsonBuffer.setLength(0);
                                } catch (ContentPolicy.ChatRetryException e) {
                                    throw e;
                                }
                            }
                        }
                    } catch (ContentPolicy.ChatRetryException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("处理流式数据失败: {}", e.getMessage());
                    }
                })
                .doOnError(error -> {
                    log.error("AI API流式响应处理失败", error);
                    try {
                        String errorContent = "Request failed. Please try again later.";
                        sink.writeContent(errorContent);
                        sink.writeCompletion(false, false, null);
                        streamState.markContentSent();
                        if (request.getSessionId() != null && request.getUserId() != null) {
                            errorPersister.saveError(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                        }
                    } catch (Exception e) {
                        log.error("发送错误消息失败", e);
                    }
                })
                .doOnComplete(() -> {
                    boolean wasManuallyInterrupted = request.getSessionId() != null
                            && chatInterruptService.isInterrupted(request.getSessionId());
                    if (lineBuffer.length() > 0) {
                        String remainingLine = lineBuffer.toString().trim();
                        if (!remainingLine.isEmpty() && remainingLine.startsWith("data: ")
                                && !remainingLine.equals("data: [DONE]")) {
                            String jsonData = remainingLine.substring(6).trim();
                            jsonBuffer.append(jsonData);
                            String bufferedJson = jsonBuffer.toString();
                            if (isValidJson(bufferedJson)) {
                                try {
                                    String contentSent = processCompleteJson(bufferedJson, sink);
                                    if (contentSent != null) {
                                        maybeSendNetwork(request, sink, streamState);
                                        streamState.markRealAiResponse();
                                        aiResponseContent.append(contentSent);
                                        if (!streamState.isFirstTokenMarked()) {
                                            streamState.markFirstToken();
                                            aiCallMonitor.markFirstToken(aiCtxHolder[0]);
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        lineBuffer.setLength(0);
                    }
                    if (jsonBuffer.length() > 0) {
                        jsonBuffer.setLength(0);
                    }
                    if (streamState.tryHandleCompletion()) {
                        boolean completionInterrupted = completionCallback.onComplete(sink, willGenerateTitle, streamState.isRealAiResponse(),
                                request, aiResponseContent, model, interfaceType, wasManuallyInterrupted);
                        if (completionInterrupted) {
                            streamState.markInterrupted();
                        }
                    }
                });
    }

    private List<Map<String, String>> buildMessagesWithPrompt(ChatRequestDTO request) {
        List<Map<String, String>> messagesWithPrompt = new ArrayList<>();
        boolean isDeepThink = request.getDeepThinking() != null && request.getDeepThinking();
        String promptContent = isDeepThink ? PromptConstants.DEEP_THINKING_PROMPT : PromptConstants.DEFAULT_SYSTEM_PROMPT;
        Map<String, String> fakeUserMessage = new HashMap<>();
        fakeUserMessage.put("role", "user");
        fakeUserMessage.put("content", "who are you？");
        messagesWithPrompt.add(fakeUserMessage);
        Map<String, String> fakeAssistantMessage = new HashMap<>();
        fakeAssistantMessage.put("role", "assistant");
        fakeAssistantMessage.put("content", promptContent);
        messagesWithPrompt.add(fakeAssistantMessage);
        if (request.getMessages() != null) {
            messagesWithPrompt.addAll(request.getMessages());
        }
        return messagesWithPrompt;
    }

    private void maybeSendNetwork(ChatRequestDTO request, ChatStreamSink sink, StreamResponseState streamState) {
        if (!streamState.isNetworkResultsSent()
                && request.getNetworkSearchResults() != null
                && !request.getNetworkSearchResults().isEmpty()) {
            sink.writeNetworkResults(request.getNetworkSearchResults());
            streamState.markNetworkResultsSent();
        }
    }

    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String processCompleteJson(String jsonData, ChatStreamSink sink) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(jsonData);
            com.fasterxml.jackson.databind.JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                com.fasterxml.jackson.databind.JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    com.fasterxml.jackson.databind.JsonNode contentNode = delta.get("content");
                    if (contentNode != null && contentNode.isTextual()) {
                        String content = contentNode.asText();
                        if (content != null && !content.isEmpty()) {
                            ContentPolicy.ContentDetectionResult detectionResult = contentPolicy.detectContentAndGetResult(content);
                            if (detectionResult.needsRetry()) {
                                throw new ContentPolicy.ChatRetryException("检测到需要重试的错误消息");
                            }
                            if (detectionResult.wasFiltered()) {
                                return null;
                            }
                            String cleanContent = detectionResult.getCleanedContent();
                            if (cleanContent != null && !cleanContent.isEmpty()) {
                                sink.writeContent(cleanContent);
                                return cleanContent;
                            }
                        }
                    }
                }
            }
            return null;
        } catch (ContentPolicy.ChatRetryException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理完整JSON数据失败: {}", e.getMessage());
            return null;
        }
    }
}
