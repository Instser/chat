package com.instser.chat.service.prepare;

import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.context.PreparedChatContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Edit-resend 用例准备。
 *
 * <p>时序拆成两段：
 * 1) {@link #replaceUserMessage}：删除旧消息、保存新用户消息、迁移附件
 * 2) {@link #buildContext}：在限流通过后构建 AI 上下文
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EditResendPrepareHandler {

    private final ChatMessageService chatMessageService;
    private final FileUploadService fileUploadService;
    private final ChatContextBuilder chatContextBuilder;

    /**
     * 替换用户消息（限流前必须完成）。
     */
    public PreparedTurn replaceUserMessage(String sessionId,
                                           String messageId,
                                           String newContent,
                                           Long userId,
                                           ChatSession chatSession) {
        String originalFilesId = fileUploadService.getFileIdsByMessageId(messageId);
        boolean hasAttachments = originalFilesId != null && !originalFilesId.trim().isEmpty();

        if (hasAttachments) {
            List<String> preserveFileIds = Arrays.asList(originalFilesId.split(","));
            int deletedCount = chatMessageService.deleteMessagesFromPointWithFilePreservation(
                    messageId, sessionId, preserveFileIds);
            if (deletedCount == 0) {
                return PreparedTurn.failure("删除消息失败");
            }
            log.info("edit-resend 成功删除{}条消息（保留文件）", deletedCount);
        } else {
            int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
            if (deletedCount == 0) {
                return PreparedTurn.failure("删除消息失败");
            }
            log.info("edit-resend 成功删除{}条消息", deletedCount);
        }

        ChatMessage newUserMessage = new ChatMessage();
        newUserMessage.setMessageId(UUID.randomUUID().toString());
        newUserMessage.setSessionId(sessionId);
        newUserMessage.setRole("user");
        newUserMessage.setContent(newContent);
        newUserMessage.setMessageType("text");
        newUserMessage.setHasAttachments(hasAttachments ? 1 : 0);
        newUserMessage.setHasWebSearch(0);
        newUserMessage.setUserId(userId);
        newUserMessage.setModelId(chatSession.getModelId());
        newUserMessage.setTokenCount(Math.max(1, newContent == null ? 0 : newContent.length() / 4));
        newUserMessage.setStatus(1);
        newUserMessage.setCreatedTime(LocalDateTime.now());
        newUserMessage.setUpdateTime(LocalDateTime.now());

        boolean saved = chatMessageService.save(newUserMessage);
        if (!saved) {
            return PreparedTurn.failure("保存用户消息失败");
        }

        if (hasAttachments) {
            for (String raw : originalFilesId.split(",")) {
                String fileId = raw.trim();
                if (fileId.isEmpty()) {
                    continue;
                }
                try {
                    fileUploadService.updateFileAssociation(fileId, newUserMessage.getMessageId(), sessionId, userId);
                } catch (Exception e) {
                    log.error("edit-resend 转移文件关联失败 fileId={}", fileId, e);
                }
            }
        }

        return PreparedTurn.builder()
                .success(true)
                .userId(userId)
                .sessionId(sessionId)
                .modelId(chatSession.getModelId())
                .userMessageId(newUserMessage.getMessageId())
                .build();
    }

    /**
     * 限流通过后构建 AI 上下文。
     */
    public PreparedTurn buildContext(String sessionId,
                                     Long userId,
                                     ChatSession chatSession,
                                     Boolean network,
                                     String userMessageId) {
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

        String filesId = null;
        if (userMessageId != null) {
            filesId = fileUploadService.getFileIdsByMessageId(userMessageId);
        }

        PreparedChatContext prepared = chatContextBuilder.buildFromMessages(
                messages, userId, sessionId, filesId, network != null && network);
        if (prepared == null || prepared.getAiMessages().isEmpty()) {
            return PreparedTurn.failure("会话中没有可用的消息");
        }

        boolean willGenerateTitle = prepared.getAiMessages().stream()
                .noneMatch(m -> "assistant".equals(m.get("role")));

        return PreparedTurn.builder()
                .success(true)
                .userId(userId)
                .sessionId(sessionId)
                .modelId(chatSession.getModelId())
                .userMessageId(userMessageId)
                .messages(prepared.getAiMessages())
                .originalMessages(prepared.getOriginalMessages())
                .networkSearchResults(prepared.getNetworkSearchResults())
                .willGenerateTitle(willGenerateTitle)
                .build();
    }

    /**
     * 兼容旧调用：一次完成替换 + 上下文（仅非限流路径可直接使用）。
     */
    public PreparedTurn prepare(String sessionId,
                                String messageId,
                                String newContent,
                                Long userId,
                                ChatSession chatSession,
                                Boolean network) {
        PreparedTurn replaced = replaceUserMessage(sessionId, messageId, newContent, userId, chatSession);
        if (!replaced.isSuccess()) {
            return replaced;
        }
        return buildContext(sessionId, userId, chatSession, network, replaced.getUserMessageId());
    }
}
