package com.instser.chat.service.completion;

import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.MessageSaveResult;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.SearchResponse;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatTitleService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.service.stream.AssistantOutput;
import com.instser.chat.service.stream.ChatStreamSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 响应完成副作用编排。
 *
 * <p>固定顺序：
 * 1) 持久化 assistant / partial（content + reasoning）
 * 2) 绑定联网结果
 * 3) 成功才记 usage
 * 4) 需要时生成 title（只用 content）
 * 5) 写出 completion 帧
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResponseCompletionHandler {

    private final ChatMessageService chatMessageService;
    private final ChatLimitService chatLimitService;
    private final ChatTitleService chatTitleService;
    private final WebSearchContentService webSearchContentService;

    public String persistAssistantContent(String sessionId, Long userId, Long modelId, String content) {
        return persistAssistantContent(sessionId, userId, modelId, content, null);
    }

    public String persistAssistantContent(String sessionId, Long userId, Long modelId,
                                          String content, String reasoning) {
        if (sessionId == null || userId == null || modelId == null) {
            return null;
        }
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
        if (!hasContent && !hasReasoning) {
            return null;
        }
        String contentToSave = hasContent ? content : "";
        MessageSaveResult result = chatMessageService.sendMessageWithTokenInfo(
                sessionId, "assistant", contentToSave, reasoning, userId, modelId);
        if (!result.isSuccess()) {
            return null;
        }
        if (result.getTokenCount() != null && result.getTokenCount() > 0) {
            chatLimitService.recordTokenUsage(userId, result.getTokenCount());
        }
        return result.getMessageUuid() != null ? result.getMessageUuid() : String.valueOf(result.getMessageId());
    }

    public void bindNetworkResults(ChatRequestDTO request, String aiResponseMessageId) {
        if (request == null || aiResponseMessageId == null) {
            return;
        }
        if (request.getNetworkSearchResults() == null || request.getNetworkSearchResults().isEmpty()) {
            return;
        }
        try {
            boolean networkEnabled = request.getNetwork() != null && request.getNetwork();
            if (!networkEnabled) {
                return;
            }
            SearchResponse searchResponse = new SearchResponse();
            searchResponse.setResults(request.getNetworkSearchResults());
            boolean saved = webSearchContentService.saveWebSearchContent(
                    aiResponseMessageId,
                    searchResponse,
                    request.getUserId(),
                    request.getSessionId()
            );
            if (saved) {
                chatMessageService.updateWebSearchFlag(aiResponseMessageId, true);
                log.info("联网结果已绑定到 AI 消息，messageId={}，results={}",
                        aiResponseMessageId, request.getNetworkSearchResults().size());
            }
        } catch (Exception e) {
            log.error("绑定联网结果失败 messageId={}", aiResponseMessageId, e);
        }
    }

    public String generateTitleIfNeeded(ChatRequestDTO request,
                                        boolean willGenerateTitle,
                                        boolean isRealAiResponse,
                                        String aiContent) {
        List<Map<String, String>> original = request == null ? null : request.getOriginalMessages();
        String sessionId = request == null ? null : request.getSessionId();
        return generateTitleIfNeeded(sessionId, original, willGenerateTitle, isRealAiResponse, aiContent);
    }

    public String generateTitleIfNeeded(String sessionId,
                                        List<Map<String, String>> originalMessages,
                                        boolean willGenerateTitle,
                                        boolean isRealAiResponse,
                                        String aiContent) {
        if (!willGenerateTitle || !isRealAiResponse || sessionId == null) {
            return null;
        }
        long titleStartTime = System.currentTimeMillis();
        try {
            CompletableFuture<String> titleFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    List<Map<String, String>> messagesForTitle = chatTitleService.buildTitleMessages(originalMessages, aiContent);
                    return chatTitleService.generateSessionTitle(sessionId, messagesForTitle);
                } catch (Exception e) {
                    LogUtils.logUserError(log, null, "标题生成", "后台标题生成异常，会话ID: {}，异常类型: {}，异常消息: {}", e,
                            sessionId, e.getClass().getSimpleName(), e.getMessage());
                    return null;
                }
            });
            String generatedTitle = titleFuture.get(5, TimeUnit.SECONDS);
            long titleDuration = System.currentTimeMillis() - titleStartTime;
            if (generatedTitle != null && !generatedTitle.equals("New Chat")) {
                log.info("标题生成成功: [{}], 耗时: {}ms", generatedTitle, titleDuration);
                return generatedTitle;
            }
            LogUtils.logUserWarning(log, null, "标题生成", "标题生成失败或返回默认标题，会话ID: {}，返回标题: [{}]，耗时: {}ms",
                    sessionId, generatedTitle, titleDuration);
            return null;
        } catch (TimeoutException e) {
            long titleDuration = System.currentTimeMillis() - titleStartTime;
            LogUtils.logUserWarning(log, null, "标题生成", "标题生成超时，会话ID: {}，超时时长: {}ms",
                    sessionId, titleDuration);
            return null;
        } catch (Exception e) {
            long titleDuration = System.currentTimeMillis() - titleStartTime;
            LogUtils.logUserError(log, null, "标题生成", "标题生成异常，会话ID: {}，耗时: {}ms，异常类型: {}，异常消息: {}", e,
                    sessionId, titleDuration, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    public void writeCompletion(ChatStreamSink sink,
                                boolean willGenerateTitle,
                                boolean isRealAiResponse,
                                String generatedTitle) {
        try {
            sink.writeCompletion(willGenerateTitle, isRealAiResponse, generatedTitle);
        } catch (Exception e) {
            log.error("发送完成标记失败", e);
        }
    }

    public String completeSideEffects(ChatStreamSink sink,
                                      ChatRequestDTO request,
                                      AiModel model,
                                      boolean willGenerateTitle,
                                      boolean isRealAiResponse,
                                      String contentToSave,
                                      boolean interrupted) {
        return completeSideEffects(sink, request, model, willGenerateTitle, isRealAiResponse,
                AssistantOutput.of(contentToSave, null), interrupted, true);
    }

    public String completeSideEffects(ChatStreamSink sink,
                                      ChatRequestDTO request,
                                      AiModel model,
                                      boolean willGenerateTitle,
                                      boolean isRealAiResponse,
                                      String contentToSave,
                                      boolean interrupted,
                                      boolean recordModelUsage) {
        return completeSideEffects(sink, request, model, willGenerateTitle, isRealAiResponse,
                AssistantOutput.of(contentToSave, null), interrupted, recordModelUsage);
    }

    /**
     * 完成副作用总编排（不含续写，续写由上游先完成并写入 state）。
     */
    public String completeSideEffects(ChatStreamSink sink,
                                      ChatRequestDTO request,
                                      AiModel model,
                                      boolean willGenerateTitle,
                                      boolean isRealAiResponse,
                                      AssistantOutput output,
                                      boolean interrupted,
                                      boolean recordModelUsage) {
        String assistantMessageId = null;
        AssistantOutput safeOutput = output == null ? AssistantOutput.empty() : output;
        String contentToSave = safeOutput.content();
        String reasoningToSave = safeOutput.reasoning();

        if (safeOutput.hasPersistableOutput() && request != null && model != null) {
            if (contentToSave != null && contentToSave.contains("Request failed")) {
                chatMessageService.sendErrorResponseMessage(
                        request.getSessionId(), request.getUserId(), model.getId(), contentToSave);
                if (!interrupted) {
                    writeCompletion(sink, false, false, null);
                }
                return null;
            }

            // 中断标记只加在 content；仅思考无正文时 content 允许为空串
            String finalContent;
            if (safeOutput.hasContent()) {
                finalContent = interrupted && !contentToSave.endsWith("[已中断]")
                        ? contentToSave + " [已中断]" : contentToSave;
            } else {
                finalContent = "";
            }

            assistantMessageId = persistAssistantContent(
                    request.getSessionId(), request.getUserId(), model.getId(),
                    finalContent, reasoningToSave);
            if (assistantMessageId != null && isRealAiResponse && !interrupted) {
                bindNetworkResults(request, assistantMessageId);
            }
            if (recordModelUsage && isRealAiResponse) {
                String modelIdStr = model.getId().toString();
                boolean modelRecordSuccess = chatLimitService.recordModelChatUsage(request.getUserId(), modelIdStr);
                if (modelRecordSuccess) {
                    log.info("用户{}模型ID{}使用记录成功", request.getUserId(), modelIdStr);
                } else {
                    log.warn("用户{}模型ID{}使用记录失败", request.getUserId(), modelIdStr);
                }
            }
        }

        if (interrupted) {
            log.debug("中断完成副作用结束，跳过普通完成帧，sessionId={}",
                    request != null ? request.getSessionId() : null);
            return assistantMessageId;
        }

        // 标题只用 content
        String generatedTitle = generateTitleIfNeeded(request, willGenerateTitle, isRealAiResponse, contentToSave);
        writeCompletion(sink, willGenerateTitle, isRealAiResponse, generatedTitle);
        return assistantMessageId;
    }
}
