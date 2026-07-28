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
import com.instser.chat.service.stream.ChatStreamEventWriter;
import jakarta.servlet.http.HttpServletResponse;
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
 * 1) 持久化 assistant / partial
 * 2) 绑定联网结果
 * 3) 成功才记 usage（由 message 服务/limit 服务内部语义保证）
 * 4) 需要时生成 title
 * 5) 写出 completion 帧
 * </p>
 *
 * <p>阶段4 先收口“落库 + 联网绑定 + 标题 + completion 写出”，
 * 续写仍由 ChatServiceImpl 在调用本 Handler 前完成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResponseCompletionHandler {

    private final ChatMessageService chatMessageService;
    private final ChatLimitService chatLimitService;
    private final ChatTitleService chatTitleService;
    private final WebSearchContentService webSearchContentService;
    private final ChatStreamEventWriter chatStreamEventWriter;

    public String persistAssistantContent(String sessionId, Long userId, Long modelId, String content) {
        if (sessionId == null || userId == null || modelId == null || content == null || content.trim().isEmpty()) {
            return null;
        }
        MessageSaveResult result = chatMessageService.sendMessageWithTokenInfo(
                sessionId, "assistant", content, userId, modelId);
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

    public void writeCompletion(HttpServletResponse response,
                                boolean willGenerateTitle,
                                boolean isRealAiResponse,
                                String generatedTitle) {
        try {
            chatStreamEventWriter.writeCompletion(response, willGenerateTitle, isRealAiResponse, generatedTitle);
        } catch (Exception e) {
            log.error("发送完成标记失败", e);
        }
    }

    /**
     * 完成副作用总编排（不含续写，续写由上游先完成并写入 content）。
     *
     * @return assistant message id（可能为 null）
     */
    public String completeSideEffects(HttpServletResponse response,
                                      ChatRequestDTO request,
                                      AiModel model,
                                      boolean willGenerateTitle,
                                      boolean isRealAiResponse,
                                      String contentToSave,
                                      boolean interrupted) {
        return completeSideEffects(response, request, model, willGenerateTitle, isRealAiResponse,
                contentToSave, interrupted, true);
    }

    /**
     * @param recordModelUsage 是否记录模型使用次数（错误响应必须 false）
     */
    public String completeSideEffects(HttpServletResponse response,
                                      ChatRequestDTO request,
                                      AiModel model,
                                      boolean willGenerateTitle,
                                      boolean isRealAiResponse,
                                      String contentToSave,
                                      boolean interrupted,
                                      boolean recordModelUsage) {
        String assistantMessageId = null;
        if (contentToSave != null && !contentToSave.trim().isEmpty() && request != null && model != null) {
            if (contentToSave.contains("Request failed")) {
                // 错误响应：只落错误消息，不扣次、不绑联网、不生成标题
                chatMessageService.sendErrorResponseMessage(
                        request.getSessionId(), request.getUserId(), model.getId(), contentToSave);
                writeCompletion(response, false, false, null);
                return null;
            }

            String finalContent = interrupted && !contentToSave.endsWith("[已中断]")
                    ? contentToSave + " [已中断]" : contentToSave;
            assistantMessageId = persistAssistantContent(
                    request.getSessionId(), request.getUserId(), model.getId(), finalContent);
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

        String generatedTitle = generateTitleIfNeeded(request, willGenerateTitle, isRealAiResponse,
                interrupted ? contentToSave : contentToSave);
        writeCompletion(response, willGenerateTitle, isRealAiResponse, generatedTitle);
        return assistantMessageId;
    }
}
