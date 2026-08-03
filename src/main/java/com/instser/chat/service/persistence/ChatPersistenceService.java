package com.instser.chat.service.persistence;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.dto.MessageSaveResult;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.completion.AiResponseCompletionHandler;
import com.instser.chat.service.stream.ChatStreamSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 聊天持久化收口：用户/AI/错误/部分消息保存与文件关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPersistenceService {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final FileUploadService fileUploadService;
    private final ChatLimitService chatLimitService;
    private final AiResponseCompletionHandler aiResponseCompletionHandler;

    public String extractLatestUserMessage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> message = messages.get(i);
            if ("user".equals(message.get("role"))) {
                return message.get("content");
            }
        }
        return null;
    }

    public Long getModelIdFromSession(String sessionId) {
        try {
            if (sessionId != null) {
                ChatSession session = chatSessionService.getSessionInfo(sessionId);
                if (session != null && session.getModelId() != null) {
                    return session.getModelId();
                }
            }
        } catch (Exception e) {
            log.warn("从会话{}获取模型ID失败", sessionId, e);
        }
        return null;
    }

    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId) {
        Long modelId = getModelIdFromSession(sessionId);
        return saveUserMessage(messages, sessionId, userId, modelId);
    }

    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId, Long modelId) {
        log.debug("【消息保存】开始保存用户消息，用户ID：{}，会话ID：{}，模型ID：{}", userId, sessionId, modelId);
        try {
            if (messages == null || messages.isEmpty() || sessionId == null || userId == null) {
                log.warn("【消息保存】参数验证失败，用户ID：{}，会话ID：{}，消息数：{}",
                        userId, sessionId, messages != null ? messages.size() : 0);
                return null;
            }
            String lastUserMessage = extractLatestUserMessage(messages);
            if (lastUserMessage != null && !lastUserMessage.trim().isEmpty()) {
                Long messageDbId = chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId, modelId);
                if (messageDbId != null) {
                    ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                    if (savedMessage != null) {
                        log.info("【消息保存】用户消息保存成功，用户ID：{}，会话ID：{}，消息ID：{}，内容长度：{}",
                                userId, sessionId, savedMessage.getMessageId(), lastUserMessage.length());
                        return savedMessage.getMessageId();
                    }
                }
            } else {
                log.warn("【消息保存】未找到有效的用户消息，用户ID：{}，会话ID：{}", userId, sessionId);
            }
        } catch (Exception e) {
            log.error("【消息保存】保存用户消息失败，用户ID：{}，会话ID：{}", userId, sessionId, e);
        }
        return null;
    }

    /**
     * chat 入口：权限前先落用户消息，保证限制场景也有完整记录。
     */
    public String saveUserMessageEarly(ChatRequestDTO request, AiModel model, ChatStreamSink sink) {
        try {
            if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
                String newSessionId = chatSessionService.createSession(request.getUserId(), model.getId());
                request.setSessionId(newSessionId);
                log.info("为用户{}创建新会话，会话ID：{}", request.getUserId(), newSessionId);
            }

            String newUserMessageContent = extractLatestUserMessage(request.getMessages());
            if (newUserMessageContent == null || newUserMessageContent.trim().isEmpty()) {
                String errorContent = "用户消息内容不能为空";
                try {
                    sink.writeError(errorContent);
                } catch (IOException e) {
                    log.error("发送错误响应失败", e);
                }
                return null;
            }

            boolean hasAttachments = request.getFilesId() != null && !request.getFilesId().trim().isEmpty();
            Long messageDbId = chatMessageService.sendMessage(
                    request.getSessionId(),
                    "user",
                    newUserMessageContent,
                    request.getUserId(),
                    model.getId(),
                    hasAttachments ? 1 : 0
            );

            String userMessageId = null;
            if (messageDbId != null) {
                ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                if (savedMessage != null) {
                    userMessageId = savedMessage.getMessageId();
                    log.debug("用户消息已早期保存，会话ID：{}，用户ID：{}，消息ID：{}，模型ID：{}，附件：{}，内容长度：{}",
                            request.getSessionId(), request.getUserId(), userMessageId, model.getId(),
                            hasAttachments ? "有" : "无", newUserMessageContent.length());
                }
            }

            if (userMessageId != null && request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                updateFilesAssociationIfNeeded(request.getFilesId(), userMessageId, request.getSessionId(), request.getUserId());
                log.info("用户{}文件关联已更新，消息ID：{}", request.getUserId(), userMessageId);
            }
            return userMessageId;
        } catch (Exception e) {
            log.error("早期保存用户消息失败，用户ID：{}，会话ID：{}", request.getUserId(), request.getSessionId(), e);
            try {
                sink.writeError("保存用户消息失败");
            } catch (IOException ioException) {
                log.error("发送错误响应失败", ioException);
            }
            return null;
        }
    }

    public void saveAiResponse(String sessionId, Long userId, Long modelId, String content) {
        saveAiResponseAndGetMessageId(sessionId, userId, modelId, content);
    }

    public String saveAiResponseAndGetMessageId(String sessionId, Long userId, Long modelId, String content) {
        return aiResponseCompletionHandler.persistAssistantContent(sessionId, userId, modelId, content);
    }

    public void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent) {
        try {
            if (sessionId != null && userId != null && modelId != null) {
                chatMessageService.sendErrorResponseMessage(sessionId, userId, modelId, errorContent);
                log.info("错误响应已保存到数据库，会话ID：{}，用户ID：{}，模型ID：{}，状态：3，内容：{}",
                        sessionId, userId, modelId, errorContent);
            } else {
                log.warn("无法保存错误响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}",
                        sessionId, userId, modelId);
            }
        } catch (Exception e) {
            log.error("保存错误响应到数据库失败", e);
        }
    }

    public void savePartialAiResponse(String sessionId, Long userId, Long modelId, String partialContent, boolean isInterrupted) {
        savePartialAiResponse(sessionId, userId, modelId, partialContent, null, isInterrupted);
    }

    public void savePartialAiResponse(String sessionId, Long userId, Long modelId,
                                      String partialContent, String reasoning, boolean isInterrupted) {
        try {
            boolean hasContent = partialContent != null && !partialContent.trim().isEmpty();
            boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
            if (sessionId != null && userId != null && modelId != null && (hasContent || hasReasoning)) {
                String contentToSave;
                if (hasContent) {
                    contentToSave = isInterrupted ? partialContent + " [已中断]" : partialContent;
                } else {
                    contentToSave = "";
                }
                MessageSaveResult result = chatMessageService.sendMessageWithTokenInfo(
                        sessionId, "assistant", contentToSave, reasoning, userId, modelId);
                if (result.isSuccess() && result.getTokenCount() > 0) {
                    boolean tokenRecorded = chatLimitService.recordTokenUsage(userId, result.getTokenCount());
                    if (tokenRecorded) {
                        log.debug("用户{}部分AI响应Token使用记录成功，使用了{}个Token", userId, result.getTokenCount());
                    } else {
                        log.warn("用户{}部分AI响应Token使用记录失败", userId);
                    }
                }
                String logMessage = isInterrupted ? "部分AI响应（中断）已保存到数据库" : "部分AI响应已保存到数据库";
                log.info("{}，会话ID：{}，用户ID：{}，模型ID：{}，内容长度：{}，reasoning长度：{}，Token数：{}",
                        logMessage, sessionId, userId, modelId,
                        partialContent != null ? partialContent.length() : 0,
                        reasoning != null ? reasoning.length() : 0,
                        result.isSuccess() ? result.getTokenCount() : 0);
            } else {
                log.warn("无法保存部分AI响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}, contentLength={}, reasoningLength={}",
                        sessionId, userId, modelId,
                        partialContent != null ? partialContent.length() : 0,
                        reasoning != null ? reasoning.length() : 0);
            }
        } catch (Exception e) {
            log.error("保存部分AI响应到数据库失败", e);
        }
    }

    public ChatResponseDTO saveErrorResponseAndReturn(ChatStreamSink sink, String errorMessage,
                                                      ChatRequestDTO request, AiModel model) {
        try {
            sink.writeError(errorMessage);
            saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorMessage);
            return ChatResponseDTO.failure(errorMessage);
        } catch (IOException e) {
            log.error("发送错误响应失败，用户ID：{}，会话ID：{}", request.getUserId(), request.getSessionId(), e);
            saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorMessage);
            return ChatResponseDTO.failure("System error");
        }
    }

    public ChatResponseDTO handleErrorResponse(ChatStreamSink sink, String errorMessage,
                                               String sessionId, Long userId, Long modelId) {
        try {
            sink.writeError(errorMessage);
            if (sessionId != null && userId != null && modelId != null) {
                saveErrorResponse(sessionId, userId, modelId, errorMessage);
                log.info("错误响应已保存到数据库，会话ID：{}，用户ID：{}，错误：{}", sessionId, userId, errorMessage);
            }
            return ChatResponseDTO.errorWithContent(errorMessage);
        } catch (IOException e) {
            log.error("发送错误响应失败", e);
            return ChatResponseDTO.failure("System error");
        }
    }

    public ChatResponseDTO handleStreamChatException(Exception e, ChatRequestDTO request, ChatStreamSink sink,
                                                     AiModelServiceBridge modelLookup) {
        log.error("处理流式聊天请求异常", e);
        try {
            String errorContent = "Request failed. Please try again later.";
            sink.writeError(errorContent);
            if (request.getSessionId() != null && request.getUserId() != null && request.getModelId() != null) {
                AiModel model = modelLookup.getById(request.getModelId());
                if (model != null) {
                    saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                }
            }
            return ChatResponseDTO.errorWithContent(errorContent);
        } catch (IOException ioException) {
            log.error("发送系统错误消息失败", ioException);
            return ChatResponseDTO.failure("System error");
        }
    }

    public void updateFilesAssociation(String filesId, String messageId, String sessionId, Long userId) {
        try {
            if (filesId == null || filesId.trim().isEmpty() || messageId == null || sessionId == null || userId == null) {
                log.warn("无法更新文件关联信息，缺少必要参数");
                return;
            }
            String[] fileIds = filesId.split(",");
            int successCount = 0;
            int totalCount = fileIds.length;
            for (String fileId : fileIds) {
                fileId = fileId.trim();
                if (fileId.isEmpty()) {
                    continue;
                }
                try {
                    boolean updated = fileUploadService.updateFileAssociation(fileId, messageId, sessionId, userId);
                    if (updated) {
                        successCount++;
                        log.info("文件{}关联信息已更新，消息ID：{}，会话ID：{}，用户ID：{}", fileId, messageId, sessionId, userId);
                    } else {
                        log.warn("文件{}关联信息更新失败，用户ID：{}", fileId, userId);
                    }
                } catch (Exception e) {
                    log.error("文件{}关联信息更新时发生异常，用户ID：{}", fileId, userId, e);
                }
            }
            log.info("文件关联信息更新完成，成功：{}/{}，消息ID：{}，会话ID：{}，用户ID：{}",
                    successCount, totalCount, messageId, sessionId, userId);
        } catch (Exception e) {
            log.error("更新文件关联信息失败", e);
        }
    }

    public void updateFilesAssociationIfNeeded(String filesId, String messageId, String sessionId, Long userId) {
        try {
            if (filesId == null || filesId.trim().isEmpty() || messageId == null || sessionId == null || userId == null) {
                log.warn("无法更新文件关联信息，缺少必要参数");
                return;
            }
            String[] fileIds = filesId.split(",");
            int successCount = 0;
            int totalCount = fileIds.length;
            for (String fileId : fileIds) {
                fileId = fileId.trim();
                if (fileId.isEmpty()) {
                    continue;
                }
                try {
                    FileUpload existingFile = fileUploadService.getByFileId(fileId);
                    if (existingFile != null && existingFile.getMessageId() != null
                            && !existingFile.getMessageId().trim().isEmpty()) {
                        log.info("文件{}已关联到消息{}，跳过更新", fileId, existingFile.getMessageId());
                        continue;
                    }
                    boolean updated = fileUploadService.updateFileAssociation(fileId, messageId, sessionId, userId);
                    if (updated) {
                        successCount++;
                        log.info("文件{}关联信息已更新，消息ID：{}，会话ID：{}，用户ID：{}", fileId, messageId, sessionId, userId);
                    } else {
                        log.warn("文件{}关联信息更新失败，用户ID：{}", fileId, userId);
                    }
                } catch (Exception e) {
                    log.error("文件{}关联信息更新时发生异常，用户ID：{}", fileId, userId, e);
                }
            }
            log.info("文件关联信息条件更新完成，成功：{}/{}，消息ID：{}，会话ID：{}，用户ID：{}",
                    successCount, totalCount, messageId, sessionId, userId);
        } catch (Exception e) {
            log.error("条件更新文件关联信息失败", e);
        }
    }

    public void updateSessionModelIfNeeded(String sessionId, Long newModelId, Long currentModelId) {
        if (newModelId != null && !newModelId.equals(currentModelId)) {
            chatSessionService.updateSessionModel(sessionId, newModelId);
            log.info("会话{}的模型已更新为：{}", sessionId, newModelId);
        }
    }

    /**
     * 避免 ChatPersistenceService 直接依赖 AiModelService 造成循环时的轻桥。
     */
    @FunctionalInterface
    public interface AiModelServiceBridge {
        AiModel getById(Long id);
    }
}
