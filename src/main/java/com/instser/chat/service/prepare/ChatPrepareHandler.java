package com.instser.chat.service.prepare;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.context.PreparedChatContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 普通聊天用例准备：先落用户消息，再构建 AI 上下文。
 *
 * <p>限流/权限 Guard 不在此处执行，由调用方在 prepare 之后统一处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPrepareHandler {

    private final ChatMessageService chatMessageService;
    private final FileUploadService fileUploadService;
    private final ChatContextBuilder chatContextBuilder;

    /**
     * 保存用户原始消息（不含文件拼接正文）。
     *
     * @return messageId，失败返回 null
     */
    public String saveUserMessage(ChatRequestDTO request, AiModel model) {
        try {
            String userContent = extractLatestUserMessage(request.getMessages());
            if (userContent == null || userContent.trim().isEmpty()) {
                log.error("无法提取用户消息内容");
                return null;
            }

            boolean hasAttachments = request.getFilesId() != null && !request.getFilesId().trim().isEmpty();
            Long messageDbId = chatMessageService.sendMessage(
                    request.getSessionId(),
                    "user",
                    userContent,
                    request.getUserId(),
                    model.getId(),
                    hasAttachments ? 1 : 0
            );
            if (messageDbId == null) {
                log.error("保存用户消息失败");
                return null;
            }

            ChatMessage saved = chatMessageService.getById(messageDbId);
            if (saved == null || saved.getMessageId() == null) {
                log.error("保存用户消息后无法读取 messageId");
                return null;
            }

            if (request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                updateFilesAssociation(request.getFilesId(), saved.getMessageId(), request.getSessionId(), request.getUserId());
            }

            log.info("用户消息已提前保存，messageId={}，sessionId={}", saved.getMessageId(), request.getSessionId());
            return saved.getMessageId();
        } catch (Exception e) {
            log.error("提前保存用户消息异常", e);
            return null;
        }
    }

    /**
     * 用户消息已保存后的上下文准备。
     */
    public PreparedTurn prepareContext(ChatRequestDTO request, AiModel model, String userMessageId) {
        PreparedChatContext prepared = chatContextBuilder.buildFromSessionHistory(request);
        if (prepared == null || prepared.getAiMessages().isEmpty()) {
            return PreparedTurn.failure("会话中没有可用的消息");
        }

        return PreparedTurn.builder()
                .success(true)
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .modelId(model.getId())
                .userMessageId(userMessageId)
                .messages(prepared.getAiMessages())
                .originalMessages(prepared.getOriginalMessages())
                .networkSearchResults(prepared.getNetworkSearchResults())
                .willGenerateTitle(true)
                .build();
    }

    private String extractLatestUserMessage(java.util.List<java.util.Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            java.util.Map<String, String> message = messages.get(i);
            if ("user".equals(message.get("role"))) {
                return message.get("content");
            }
        }
        return null;
    }

    private void updateFilesAssociation(String filesId, String messageId, String sessionId, Long userId) {
        if (filesId == null || filesId.trim().isEmpty() || messageId == null || sessionId == null || userId == null) {
            return;
        }
        for (String raw : filesId.split(",")) {
            String fileId = raw.trim();
            if (fileId.isEmpty()) {
                continue;
            }
            try {
                fileUploadService.updateFileAssociation(fileId, messageId, sessionId, userId);
            } catch (Exception e) {
                log.warn("关联文件失败 fileId={} messageId={}", fileId, messageId, e);
            }
        }
    }
}
