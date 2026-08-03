package com.instser.chat.service.prepare;

import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.context.PreparedChatContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retry 用例准备：删除目标 assistant 及之后消息，重建 turn 消息与上下文。
 *
 * <p>调用方时序：validate → prepare(delete+context) → rateLimit → model/package guard → stream</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryPrepareHandler {

    private final ChatMessageService chatMessageService;
    private final FileUploadService fileUploadService;
    private final ChatContextBuilder chatContextBuilder;

    /**
     * 删除目标消息及后续，并重建 AI 上下文。
     */
    public PreparedTurn prepare(String sessionId,
                                String messageId,
                                Long userId,
                                Long modelId,
                                Boolean network,
                                ChatSession chatSession) {
        int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
        if (deletedCount == 0) {
            return PreparedTurn.failure("删除消息失败");
        }
        log.info("retry 成功删除{}条消息，sessionId={} messageId={}", deletedCount, sessionId, messageId);

        return prepareAfterDelete(sessionId, userId, modelId, network, chatSession);
    }

    /**
     * 删除已完成后的上下文重建（供限流失败后仅需会话状态时也可复用）。
     */
    public PreparedTurn prepareAfterDelete(String sessionId,
                                           Long userId,
                                           Long modelId,
                                           Boolean network,
                                           ChatSession chatSession) {
        List<ChatMessage> historyMessages = chatMessageService.getSessionMessagesForAi(sessionId);
        if (historyMessages == null || historyMessages.isEmpty()) {
            return PreparedTurn.failure("会话中没有可用的消息");
        }

        boolean hasUser = historyMessages.stream().anyMatch(m -> "user".equals(m.getRole()));
        if (!hasUser) {
            return PreparedTurn.failure("未找到用户消息");
        }

        // 模型上下文只含 role+content，永不回喂 reasoning
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage message : historyMessages) {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", message.getRole());
            msg.put("content", message.getContent() != null ? message.getContent() : "");
            messages.add(msg);
        }

        String filesId = null;
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = historyMessages.get(i);
            if ("user".equals(msg.getRole())) {
                filesId = fileUploadService.getFileIdsByMessageId(msg.getMessageId());
                break;
            }
        }

        PreparedChatContext prepared = chatContextBuilder.buildFromMessages(
                messages, userId, sessionId, filesId, network != null && network);

        if (prepared == null || prepared.getAiMessages().isEmpty()) {
            return PreparedTurn.failure("会话中没有可用的消息");
        }

        boolean willGenerateTitle = prepared.getAiMessages().stream()
                .noneMatch(m -> "assistant".equals(m.get("role")));

        List<WebSearchResult> networkResults = prepared.getNetworkSearchResults();
        return PreparedTurn.builder()
                .success(true)
                .userId(userId)
                .sessionId(sessionId)
                .modelId(modelId != null ? modelId : chatSession.getModelId())
                .messages(prepared.getAiMessages())
                .originalMessages(prepared.getOriginalMessages())
                .networkSearchResults(networkResults)
                .willGenerateTitle(willGenerateTitle)
                .build();
    }
}
