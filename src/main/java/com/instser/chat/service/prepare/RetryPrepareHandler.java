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
 * <p>不负责限流判定与 SSE 写出；调用方必须在 delete 之后再 Guard。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryPrepareHandler {

    private final ChatMessageService chatMessageService;
    private final FileUploadService fileUploadService;
    private final ChatContextBuilder chatContextBuilder;

    public PreparedTurn prepareAfterDelete(String sessionId,
                                           Long userId,
                                           Long modelId,
                                           Boolean network,
                                           ChatSession chatSession) {
        List<ChatMessage> historyMessages = chatMessageService.getSessionMessagesForAi(sessionId);
        if (historyMessages == null || historyMessages.isEmpty()) {
            return PreparedTurn.failure("会话中没有可用的消息");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage message : historyMessages) {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", message.getRole());
            msg.put("content", message.getContent());
            messages.add(msg);
        }

        // 恢复最后一条用户消息的附件，供上下文构建
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
                .filter(m -> "assistant".equals(m.get("role")))
                .findAny()
                .isEmpty();

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
