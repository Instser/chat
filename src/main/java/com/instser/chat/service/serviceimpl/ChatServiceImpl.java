package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.constants.PromptConstants;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.dto.MessageSaveResult;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.service.*;
import com.instser.chat.vo.ChatLimitVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * 聊天业务服务实现类
 *
 * @author instser
 * @since 2025-06-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final AiModelService aiModelService;
    private final ChatLimitService chatLimitService;
    private final ChatRateLimitService chatRateLimitService;
    private final FileUploadService fileUploadService;
    private final ChatInterruptService chatInterruptService;
    private final WebClient.Builder webClientBuilder;
    private final RestTemplate restTemplate;
    private final TruncationDetectionService truncationDetectionService;

    @Qualifier("continuationExecutor")
    private final Executor continuationExecutor;

    // 智能分块响应配置
    private static final boolean ENABLE_SMART_CHUNKING = true;
    private static final long CHUNK_DELAY_MS = 30;
    private static final int MIN_CHUNK_SIZE = 5;
    private static final int MAX_CHUNK_SIZE = 25;

    @Override
    public ChatResponseDTO processStreamChat(ChatRequestDTO request, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理流式聊天请求，用户ID：{}，模型ID：{}", request.getUserId(), request.getModelId());

        try {
            // 设置响应头
            setStreamResponseHeaders(response);

            // 验证请求参数
            String validationError = validateChatRequest(request);
            if (validationError != null) {
                writeErrorResponse(response, validationError);
                return ChatResponseDTO.failure(validationError);
            }

            // 验证用户权限
            String permissionError = validateUserPermission(authentication, request.getUserId());
            if (permissionError != null) {
                writeErrorResponse(response, permissionError);
                return ChatResponseDTO.failure(permissionError);
            }

            // 检查聊天频率限制
            String rateLimitError = validateChatRateLimit(request.getUserId());
            if (rateLimitError != null) {
                writeErrorResponse(response, rateLimitError);
                saveErrorResponse(request.getSessionId(), request.getUserId(), request.getModelId(), rateLimitError);
                return ChatResponseDTO.errorWithContent(rateLimitError);
            }

            // 检查用户是否已有活跃会话（每个用户只能有一个活跃对话）
            if (chatInterruptService.hasActiveSession(request.getUserId())) {
                String errorMessage = "Currently chatting, please end the chat and try again";
                writeErrorResponse(response, errorMessage);
                return ChatResponseDTO.failure(errorMessage);
            }

            // 获取模型配置
            AiModel model = aiModelService.getById(request.getModelId());
            if (model == null || model.getIsAvailable() != 1) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                if (request.getSessionId() != null && request.getUserId() != null) {
                    // 尝试获取模型信息，如果模型不存在则使用默认模型ID 1
                    Long modelIdForSave = (model != null) ? model.getId() : 1L;
                    saveErrorResponse(request.getSessionId(), request.getUserId(), modelIdForSave, errorContent);
                    log.info("AI模型不可用错误响应已保存到数据库，会话ID：{}，用户ID：{}", request.getSessionId(), request.getUserId());
                }

                return ChatResponseDTO.errorWithContent(errorContent);
            }

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(request.getUserId());
            if (!limitCheck.getCanChat()) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                if (request.getSessionId() != null && request.getUserId() != null) {
                    saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                    log.info("聊天权限验证失败错误响应已保存到数据库，会话ID：{}，用户ID：{}，原因：{}",
                            request.getSessionId(), request.getUserId(),
                            limitCheck.getLimitReason() != null ? limitCheck.getLimitReason() : "Chat permission verification failed");
                }

                return ChatResponseDTO.errorWithContent(errorContent);
            }

            log.info("用户{}聊天权限验证通过，套餐：{}，限制类型：{}",
                    request.getUserId(), limitCheck.getPackageName(), limitCheck.getLimitType());

            // 如果没有sessionId，创建新会话
            if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
                String newSessionId = chatSessionService.createSession(request.getUserId(), model.getId());
                request.setSessionId(newSessionId);
                log.info("为用户{}创建新会话，会话ID：{}", request.getUserId(), newSessionId);
            }

            // 检查是否有文件附件
            boolean hasAttachments = request.getFilesId() != null && !request.getFilesId().trim().isEmpty();

            // 保存用户原始消息到数据库（不包含文件内容）
            String userMessageId = saveUserOriginalMessage(request.getMessages(), request.getSessionId(), request.getUserId(), model.getId(), hasAttachments);

            // 立即更新文件关联信息（避免AI错误时文件关联丢失）
            if (userMessageId != null && request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                updateFilesAssociationIfNeeded(request.getFilesId(), userMessageId, request.getSessionId(), request.getUserId());
                log.info("用户{}文件关联已更新，消息ID：{}", request.getUserId(), userMessageId);
            }

            // 处理文件内容并附加到用户消息中（仅用于AI处理）
            List<Map<String, String>> processedMessages = processFilesContent(request.getMessages(), request.getFilesId(), request.getUserId());
            request.setMessages(processedMessages);

            // 判断是否会生成标题
            boolean willGenerateTitle = request.getSessionId() != null && isFirstAiResponse(request.getMessages());

            // 调用AI服务进行流式响应
            ChatResponseDTO result = streamChatWithAI(request, model, response, willGenerateTitle, "chat");

            // 处理响应结果
            if (result.isRealAiResponse()) {
                // 记录使用次数（续写不计入聊天次数）
                if (!result.isSavedByContinuation()) {
                    boolean recordSuccess = chatLimitService.recordChatUsage(request.getUserId());
                    if (recordSuccess) {
                        log.info("用户{}聊天使用次数记录成功", request.getUserId());
                    } else {
                        log.warn("用户{}聊天使用次数记录失败", request.getUserId());
                    }
                } else {
                    log.info("用户{}续写完成，不计入聊天次数", request.getUserId());
                }

                // 保存AI响应到数据库（如果未通过续写保存）
                if (!result.isSavedByContinuation() && request.getSessionId() != null &&
                    result.getAiResponseContent() != null && !result.getAiResponseContent().trim().isEmpty()) {
                    saveAiResponse(request.getSessionId(), request.getUserId(), model.getId(), result.getAiResponseContent());
                }

                log.info("用户{}AI响应成功", request.getUserId());
            } else if (result.isInterrupted() && result.isHasContentSent()) {
                // 中断响应且有内容发送，记录使用次数但不重复保存（已在中断时保存）
                boolean recordSuccess = chatLimitService.recordChatUsage(request.getUserId());
                if (recordSuccess) {
                    log.info("用户{}中断聊天使用次数记录成功", request.getUserId());
                } else {
                    log.warn("用户{}中断聊天使用次数记录失败", request.getUserId());
                }
                log.info("用户{}聊天被中断，已保存部分内容", request.getUserId());
            } else if (result.isHasContentSent()) {
                // 有内容发送（错误响应），但不记录使用次数
                log.info("用户{}收到错误响应，已保存聊天记录但不记录使用次数", request.getUserId());
            }

            return result;

        } catch (Exception e) {
            log.error("处理流式聊天请求异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                if (request.getSessionId() != null && request.getUserId() != null) {
                    AiModel model = aiModelService.getById(request.getModelId());
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
    }

    @Override
    public String validateChatRequest(ChatRequestDTO request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "消息列表不能为空";
        }

        if (request.getUserId() == null) {
            return "用户ID不能为空";
        }

        if (request.getModelId() == null) {
            return "AI模型ID不能为空";
        }

        // 验证最后一条消息必须是用户消息
        Map<String, String> lastMessage = request.getMessages().get(request.getMessages().size() - 1);
        String role = lastMessage.get("role");
        if (!"user".equals(role)) {
            return "最后一条消息必须是用户消息";
        }

        return null; // 验证通过
    }

    @Override
    public String validateUserPermission(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "用户未认证";
        }
        return null; // 验证通过
    }

    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId) {
        // 从会话中获取modelId，如果获取失败则使用null（会在sendMessage中处理）
        Long modelId = getModelIdFromSession(sessionId);
        return saveUserMessage(messages, sessionId, userId, modelId);
    }

    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId, Long modelId) {
        try {
            if (messages == null || messages.isEmpty() || sessionId == null || userId == null) {
                log.warn("无法保存用户消息到数据库，缺少必要参数");
                return null;
            }

            // 从消息列表中找到最后一条用户消息
            String lastUserMessage = null;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> message = messages.get(i);
                if ("user".equals(message.get("role"))) {
                    lastUserMessage = message.get("content");
                    break;
                }
            }

            if (lastUserMessage != null && !lastUserMessage.trim().isEmpty()) {
                Long messageDbId = chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId, modelId);
                if (messageDbId != null) {
                    // 获取刚创建的消息以获取messageId
                    ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                    if (savedMessage != null) {
                        log.info("用户消息已保存到数据库，会话ID：{}，用户ID：{}，消息ID：{}，模型ID：{}，内容长度：{}",
                                sessionId, userId, savedMessage.getMessageId(), modelId, lastUserMessage.length());
                        return savedMessage.getMessageId();
                    }
                }
            } else {
                log.warn("未找到有效的用户消息，跳过保存");
            }
        } catch (Exception e) {
            log.error("保存用户消息到数据库失败", e);
        }
        return null;
    }

    @Override
    public String saveUserOriginalMessage(List<Map<String, String>> originalMessages, String sessionId, Long userId, Long modelId, boolean hasAttachments) {
        try {
            if (originalMessages == null || originalMessages.isEmpty() || sessionId == null || userId == null) {
                log.warn("无法保存用户原始消息到数据库，缺少必要参数");
                return null;
            }

            // 从消息列表中找到最后一条用户消息
            String lastUserMessage = null;
            for (int i = originalMessages.size() - 1; i >= 0; i--) {
                Map<String, String> message = originalMessages.get(i);
                if ("user".equals(message.get("role"))) {
                    lastUserMessage = message.get("content");
                    break;
                }
            }

            if (lastUserMessage != null && !lastUserMessage.trim().isEmpty()) {
                Long messageDbId = chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId, modelId, hasAttachments ? 1 : 0);
                if (messageDbId != null) {
                    // 获取刚创建的消息以获取messageId
                    ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                    if (savedMessage != null) {
                        log.info("用户原始消息已保存到数据库，会话ID：{}，用户ID：{}，消息ID：{}，模型ID：{}，附件：{}，内容长度：{}",
                                sessionId, userId, savedMessage.getMessageId(), modelId, hasAttachments ? "有" : "无", lastUserMessage.length());
                        return savedMessage.getMessageId();
                    }
                }
            } else {
                log.warn("未找到有效的用户消息，跳过保存");
            }
        } catch (Exception e) {
            log.error("保存用户原始消息到数据库失败", e);
        }
        return null;
    }

    /**
     * 更新文件的关联信息（在AI响应成功后调用）
     *
     * @param filesId 文件ID列表（逗号分隔）
     * @param messageId 消息ID
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    private void updateFilesAssociation(String filesId, String messageId, String sessionId, Long userId) {
        try {
            if (filesId == null || filesId.trim().isEmpty() || messageId == null || sessionId == null || userId == null) {
                log.warn("无法更新文件关联信息，缺少必要参数");
                return;
            }

            // 解析文件ID列表
            String[] fileIds = filesId.split(",");
            int successCount = 0;
            int totalCount = fileIds.length;

            for (String fileId : fileIds) {
                fileId = fileId.trim();
                if (fileId.isEmpty()) {
                    continue;
                }

                try {
                    // 更新文件的messageId和sessionId
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

    /**
     * 仅在文件尚未关联到任何消息时更新文件关联信息
     * @param filesId 文件ID列表（逗号分隔）
     * @param messageId 消息ID
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    private void updateFilesAssociationIfNeeded(String filesId, String messageId, String sessionId, Long userId) {
        try {
            if (filesId == null || filesId.trim().isEmpty() || messageId == null || sessionId == null || userId == null) {
                log.warn("无法更新文件关联信息，缺少必要参数");
                return;
            }

            // 解析文件ID列表
            String[] fileIds = filesId.split(",");
            int successCount = 0;
            int totalCount = fileIds.length;

            for (String fileId : fileIds) {
                fileId = fileId.trim();
                if (fileId.isEmpty()) {
                    continue;
                }

                try {
                    // 检查文件是否已经关联到其他消息
                    FileUpload existingFile = fileUploadService.getByFileId(fileId);
                    if (existingFile != null && existingFile.getMessageId() != null && !existingFile.getMessageId().trim().isEmpty()) {
                        log.info("文件{}已关联到消息{}，跳过更新", fileId, existingFile.getMessageId());
                        continue;
                    }

                    // 更新文件的messageId和sessionId
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

    @Override
    public void saveAiResponse(String sessionId, Long userId, Long modelId, String content) {
        try {
            if (sessionId != null && userId != null && modelId != null && content != null && !content.trim().isEmpty()) {
                // 保存AI响应消息到数据库并获取Token信息（避免额外数据库查询）
                MessageSaveResult result = chatMessageService.sendMessageWithTokenInfo(sessionId, "assistant", content, userId, modelId);

                // 检查保存结果并记录Token使用量
                if (result.isSuccess() && result.getTokenCount() > 0) {
                    boolean tokenRecorded = chatLimitService.recordTokenUsage(userId, result.getTokenCount());
                    if (tokenRecorded) {
                        log.debug("用户{}AI响应Token使用记录成功，使用了{}个Token", userId, result.getTokenCount());
                    } else {
                        log.warn("用户{}AI响应Token使用记录失败，可能余额不足", userId);
                    }

                    log.info("AI响应已保存到数据库，会话ID：{}，用户ID：{}，模型ID：{}，内容长度：{}，Token数：{}",
                            sessionId, userId, modelId, content.length(), result.getTokenCount());
                } else if (result.isSuccess()) {
                    log.info("AI响应已保存到数据库，会话ID：{}，用户ID：{}，模型ID：{}，内容长度：{}，Token数：0",
                            sessionId, userId, modelId, content.length());
                } else {
                    log.warn("AI响应消息保存失败，无法记录Token使用量");
                }
            } else {
                log.warn("无法保存AI响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}, contentLength={}",
                        sessionId, userId, modelId, content != null ? content.length() : 0);
            }
        } catch (Exception e) {
            log.error("保存AI响应到数据库失败", e);
        }
    }

    @Override
    public void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent) {
        try {
            if (sessionId != null && userId != null && modelId != null) {
                chatMessageService.sendMessage(sessionId, "assistant", errorContent, userId, modelId);
                log.info("错误响应已保存到数据库，会话ID：{}，用户ID：{}，模型ID：{}，内容：{}", sessionId, userId, modelId, errorContent);
            } else {
                log.warn("无法保存错误响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}",
                        sessionId, userId, modelId);
            }
        } catch (Exception e) {
            log.error("保存错误响应到数据库失败", e);
        }
    }

    @Override
    public boolean isFirstAiResponse(List<Map<String, String>> messages) {
        // 统计用户消息数量
        long userMessageCount = messages.stream()
            .filter(msg -> "user".equals(msg.get("role")))
            .count();

        // 如果只有1条用户消息，说明这是首次AI响应
        return userMessageCount == 1;
    }

    /**
     * 通用的聊天权限和会话验证
     */
    private ChatValidationResult validateChatPermissions(String sessionId, String messageId, String expectedRole,
                                                        Authentication authentication) {
        // 验证用户认证
        if (authentication == null || !authentication.isAuthenticated()) {
            return ChatValidationResult.error("用户未认证");
        }

        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ChatValidationResult.error("无法获取用户信息");
        }

        // 验证会话是否存在
        ChatSession chatSession = chatSessionService.getSessionInfo(sessionId);
        if (chatSession == null) {
            return ChatValidationResult.error("会话不存在");
        }

        // 验证会话是否属于当前用户
        if (!chatSession.getUserId().equals(userId)) {
            return ChatValidationResult.error("无权访问此会话");
        }

        // 验证消息是否存在
        ChatMessage targetMessage = chatMessageService.getByMessageId(messageId);
        if (targetMessage == null) {
            return ChatValidationResult.error("消息不存在");
        }

        // 验证消息是否属于此会话
        if (!targetMessage.getSessionId().equals(sessionId)) {
            return ChatValidationResult.error("消息不属于此会话");
        }

        // 验证消息角色
        if (expectedRole != null && !expectedRole.equals(targetMessage.getRole())) {
            String errorMsg = "assistant".equals(expectedRole) ? "只能重新生成AI消息" : "只能编辑用户消息";
            return ChatValidationResult.error(errorMsg);
        }

        return ChatValidationResult.success(userId, chatSession, targetMessage);
    }

    /**
     * 获取并验证AI模型
     */
    private AiModel getAndValidateModel(Long modelId, ChatSession chatSession) {
        Long targetModelId = modelId != null ? modelId : chatSession.getModelId();
        AiModel model = aiModelService.getById(targetModelId);

        if (model == null || model.getIsAvailable() != 1) {
            return null;
        }

        return model;
    }

    /**
     * 更新会话模型（如果需要）
     */
    private void updateSessionModelIfNeeded(String sessionId, Long newModelId, Long currentModelId) {
        if (newModelId != null && !newModelId.equals(currentModelId)) {
            chatSessionService.updateSessionModel(sessionId, newModelId);
            log.info("会话{}的模型已更新为：{}", sessionId, newModelId);
        }
    }

    /**
     * 处理AI响应结果的通用逻辑
     */
    private void handleAiResponseResult(ChatResponseDTO result, Long userId, String sessionId,
                                      AiModel model, String operationType) {
        if (result.isRealAiResponse()) {
            // 记录使用次数（续写不计入聊天次数）
            if (!result.isSavedByContinuation()) {
                boolean recordSuccess = chatLimitService.recordChatUsage(userId);
                if (recordSuccess) {
                    log.info("用户{}{}聊天使用次数记录成功", userId, operationType);
                } else {
                    log.warn("用户{}{}聊天使用次数记录失败", userId, operationType);
                }
            } else {
                log.info("用户{}{}续写完成，不计入聊天次数", userId, operationType);
            }

            // 保存AI响应到数据库（如果未通过续写保存）
            if (!result.isSavedByContinuation() && result.getAiResponseContent() != null &&
                !result.getAiResponseContent().trim().isEmpty()) {
                saveAiResponse(sessionId, userId, model.getId(), result.getAiResponseContent());
            }

            log.info("用户{}{}AI响应成功", userId, operationType);
        } else if (result.isHasContentSent()) {
            // 有内容发送（错误响应），但不记录使用次数
            log.info("用户{}{}收到错误响应，已保存聊天记录但不记录使用次数", userId, operationType);
        }
    }

    /**
     * 聊天验证结果内部类
     */
    private static class ChatValidationResult {
        private final boolean success;
        private final String errorMessage;
        private final Long userId;
        private final ChatSession chatSession;
        private final ChatMessage targetMessage;

        private ChatValidationResult(boolean success, String errorMessage, Long userId,
                                   ChatSession chatSession, ChatMessage targetMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.userId = userId;
            this.chatSession = chatSession;
            this.targetMessage = targetMessage;
        }

        public static ChatValidationResult success(Long userId, ChatSession chatSession, ChatMessage targetMessage) {
            return new ChatValidationResult(true, null, userId, chatSession, targetMessage);
        }

        public static ChatValidationResult error(String errorMessage) {
            return new ChatValidationResult(false, errorMessage, null, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Long getUserId() { return userId; }
        public ChatSession getChatSession() { return chatSession; }
        public ChatMessage getTargetMessage() { return targetMessage; }
    }

    /**
     * 设置流式响应头
     */
    private void setStreamResponseHeaders(HttpServletResponse response) {
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-vercel-ai-data-stream", "v1");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, String errorMessage) throws IOException {
        response.getWriter().write("3:\"" + errorMessage + "\"\n");
        response.getWriter().flush();
    }

    /**
     * 调用AI服务进行流式响应
     */
    private ChatResponseDTO streamChatWithAI(ChatRequestDTO request, AiModel model, HttpServletResponse response, boolean willGenerateTitle, String interfaceType) {
        // 注册活跃会话以支持中断功能
        if (request.getSessionId() != null && request.getUserId() != null) {
            chatInterruptService.registerActiveSession(request.getSessionId(), request.getUserId());
        }

        // 用于跟踪是否有AI内容被成功发送和是否为真实AI响应
        final boolean[] hasContentSent = {false};
        final boolean[] isRealAiResponse = {false};
        final boolean[] isInterrupted = {false};
        final boolean[] interruptSaved = {false}; // 防止多次保存中断内容
        final boolean[] savedByContinuation = {false}; // 标识是否已通过续写完成保存
        // 用于收集完整的AI响应内容
        final StringBuilder aiResponseContent = new StringBuilder();

        try {
            // 添加系统提示词到消息列表开头
            List<Map<String, String>> messagesWithPrompt = new ArrayList<>();

            // 根据deepthink参数选择提示词
            boolean isDeepThink = request.getDeepThinking() != null && request.getDeepThinking();
            String systemPrompt = isDeepThink ? PromptConstants.DEEP_THINKING_PROMPT : PromptConstants.DEFAULT_SYSTEM_PROMPT;

            // 添加系统提示词
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesWithPrompt.add(systemMessage);

            // 添加用户的消息
            messagesWithPrompt.addAll(request.getMessages());

            log.info("使用{}提示词，消息总数: {}", isDeepThink ? "深度思考" : "默认", messagesWithPrompt.size());

            // 添加JSON数据缓冲区，用于处理分片数据
            StringBuilder jsonBuffer = new StringBuilder();
            // 添加行缓冲区，用于处理被分割的行
            StringBuilder lineBuffer = new StringBuilder();

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messagesWithPrompt);
            requestBody.put("stream", true);

            // 只有当provider是Gemini时才添加reasoning_effort参数
            if ("Gemini".equalsIgnoreCase(model.getProvider())) {
                requestBody.put("reasoning_effort", "none");
                log.debug("检测到Gemini模型，添加reasoning_effort=none参数");
            }

            // 记录详细的AI请求信息
            logAiRequestDetails(requestBody, model, request.getUserId());

            // 创建WebClient（使用配置的Builder，已禁用SSL验证）
            WebClient webClient = webClientBuilder
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .defaultHeader("Accept-Charset", "UTF-8")
                    .defaultHeader("User-Agent", "OpenAI/PowerShell")
                    .build();

            log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());

            // 发送流式请求并处理响应
            webClient.post()
                    .uri(model.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofSeconds(30))
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String chunk = new String(bytes, StandardCharsets.UTF_8);
                        return chunk.replaceAll("[\uFFFD\u0000]", "");
                    })
                    .flatMap(chunk -> {
                        // 将chunk添加到行缓冲区
                        lineBuffer.append(chunk);

                        // 按换行符分割，但保留最后一个不完整的行
                        String bufferedContent = lineBuffer.toString();
                        String[] lines = bufferedContent.split("\n", -1);
                        List<String> validLines = new ArrayList<>();

                        // 处理除最后一行外的所有行（这些都是完整的行）
                        for (int i = 0; i < lines.length - 1; i++) {
                            String line = lines[i].trim();
                            if (!line.isEmpty()) {
                                validLines.add(line);
                            }
                        }

                        // 最后一行可能不完整，保留在缓冲区中
                        String lastLine = lines[lines.length - 1];
                        lineBuffer.setLength(0);
                        if (!lastLine.isEmpty()) {
                            lineBuffer.append(lastLine);
                        }

                        return Flux.fromIterable(validLines);
                    })
                    .doOnNext(line -> {
                        try {
                            // 检查是否被中断
                            if (request.getSessionId() != null && chatInterruptService.isInterrupted(request.getSessionId())) {
                                log.info("检测到聊天中断信号，会话ID：{}", request.getSessionId());
                                isInterrupted[0] = true;

                                // 只保存一次中断内容，防止重复保存
                                if (!interruptSaved[0] && aiResponseContent.length() > 0) {
                                    savePartialAiResponse(request.getSessionId(), request.getUserId(), model.getId(),
                                                        aiResponseContent.toString(), true);
                                    interruptSaved[0] = true; // 标记已保存
                                    log.info("已保存中断前的部分AI响应，内容长度：{}", aiResponseContent.length());
                                }

                                // 发送中断完成标记（只发送一次）
                                if (!interruptSaved[0] || aiResponseContent.length() == 0) {
                                    try {
                                        String interruptData = buildInterruptCompletionData();
                                        response.getWriter().write(interruptData);
                                        response.getWriter().flush();
                                        log.info("已发送中断完成标记");
                                        interruptSaved[0] = true; // 标记已处理
                                    } catch (Exception e) {
                                        log.error("发送中断完成标记失败", e);
                                    }
                                }

                                return; // 停止处理后续数据
                            }

                            log.info("收到AI响应行: [{}]", line);

                            if (line.startsWith("data: ")) {
                                if (line.equals("data: [DONE]")) {
                                    log.info("收到AI响应完成标记，开始处理完成逻辑");
                                    boolean continuationSaved = handleStreamCompletion(response, willGenerateTitle, isRealAiResponse[0], request, aiResponseContent.toString(), model, interfaceType);
                                    if (continuationSaved) {
                                        // 续写已完成保存，设置标识避免重复保存
                                        savedByContinuation[0] = true;
                                    }
                                    return;
                                }

                                // 提取JSON数据部分
                                String jsonData = line.substring(6).trim();
                                jsonBuffer.append(jsonData);

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    String contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent != null) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true;
                                        aiResponseContent.append(contentSent);
                                    }
                                    jsonBuffer.setLength(0);
                                }
                            } else if (!line.trim().isEmpty() && jsonBuffer.length() > 0) {
                                // 处理跨行的JSON片段（不以data:开头但缓冲区有数据）
                                log.debug("处理跨行JSON片段: [{}]", line);

                                // 防止缓冲区无限增长
                                if (jsonBuffer.length() > 10000) {
                                    log.warn("JSON缓冲区过大，清空重新开始: {}", jsonBuffer.length());
                                    jsonBuffer.setLength(0);
                                    return;
                                }

                                jsonBuffer.append(line.trim());

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    log.debug("跨行JSON重组成功，长度: {}", bufferedJson.length());
                                    String contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent != null) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true;
                                        aiResponseContent.append(contentSent);
                                    }
                                    jsonBuffer.setLength(0);
                                } else {
                                    log.debug("跨行JSON仍不完整，继续等待: 当前长度 {}", bufferedJson.length());
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理流式数据失败: {}, 缓冲区内容: [{}]", e.getMessage(), jsonBuffer.toString());
                            // 不要立即清空缓冲区，给跨行JSON一个机会
                            // jsonBuffer.setLength(0);
                        }
                    })
                    .doOnError(error -> {
                        log.error("AI API流式响应处理失败", error);
                        try {
                            String errorContent = "Request failed. Please try again later.";
                            String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                            response.getWriter().write(vercelFormat);

                            String completionData = buildCompletionData(false, false, null);
                            response.getWriter().write(completionData);
                            response.getWriter().flush();

                            hasContentSent[0] = true;

                            // 保存错误响应到数据库
                            if (request.getSessionId() != null && request.getUserId() != null) {
                                saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                                log.info("AI错误响应已保存到数据库，会话ID：{}，用户ID：{}", request.getSessionId(), request.getUserId());
                            }
                        } catch (Exception e) {
                            log.error("发送错误消息失败", e);
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("AI API流式响应处理完成");

                        // 清理会话状态
                        if (request.getSessionId() != null) {
                            chatInterruptService.markSessionCompleted(request.getSessionId());
                        }

                        // 处理剩余的行缓冲区数据
                        if (lineBuffer.length() > 0) {
                            String remainingLine = lineBuffer.toString().trim();
                            if (!remainingLine.isEmpty()) {
                                log.info("处理流完成时剩余的行数据: [{}]", remainingLine);
                                try {
                                    // 处理剩余的行数据
                                    if (remainingLine.startsWith("data: ")) {
                                        if (remainingLine.equals("data: [DONE]")) {
                                            log.info("收到AI响应完成标记（来自剩余数据）");
                                            boolean continuationSaved = handleStreamCompletion(response, willGenerateTitle, isRealAiResponse[0], request, aiResponseContent.toString(), model, interfaceType);
                                            if (continuationSaved) {
                                                // 续写已完成保存，设置标识避免重复保存
                                                savedByContinuation[0] = true;
                                            }
                                        } else {
                                            String jsonData = remainingLine.substring(6).trim();
                                            jsonBuffer.append(jsonData);

                                            String bufferedJson = jsonBuffer.toString();
                                            if (isValidJson(bufferedJson)) {
                                                String contentSent = processCompleteJson(bufferedJson, response);
                                                if (contentSent != null) {
                                                    hasContentSent[0] = true;
                                                    isRealAiResponse[0] = true;
                                                    aiResponseContent.append(contentSent);
                                                }
                                            }
                                        }
                                    } else if (jsonBuffer.length() > 0) {
                                        // 处理跨行的JSON片段
                                        jsonBuffer.append(remainingLine);
                                        String bufferedJson = jsonBuffer.toString();
                                        if (isValidJson(bufferedJson)) {
                                            String contentSent = processCompleteJson(bufferedJson, response);
                                            if (contentSent != null) {
                                                hasContentSent[0] = true;
                                                isRealAiResponse[0] = true;
                                                aiResponseContent.append(contentSent);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("处理剩余行数据失败: {}", e.getMessage());
                                }
                            }
                            lineBuffer.setLength(0);
                        }

                        // 清理JSON缓冲区
                        if (jsonBuffer.length() > 0) {
                            log.warn("流完成时JSON缓冲区仍有数据，长度: {}, 内容: [{}]", jsonBuffer.length(), jsonBuffer.toString());
                            jsonBuffer.setLength(0);
                        }
                    })
                    .onErrorReturn("")
                    .blockLast();

            // 如果被中断，返回特殊的响应状态
            if (isInterrupted[0]) {
                return ChatResponseDTO.interrupted(hasContentSent[0], aiResponseContent.toString());
            }

            ChatResponseDTO result = ChatResponseDTO.success(hasContentSent[0], isRealAiResponse[0], aiResponseContent.toString());
            result.setSavedByContinuation(savedByContinuation[0]);
            return result;

        } catch (Exception e) {
            log.error("流式聊天异常", e);

            // 清理会话状态
            if (request.getSessionId() != null) {
                chatInterruptService.markSessionCompleted(request.getSessionId());
            }

            try {
                String errorContent = "Request failed. Please try again later.";
                String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                response.getWriter().write(vercelFormat);

                String completionData = buildCompletionData(false, false, null);
                response.getWriter().write(completionData);
                response.getWriter().flush();

                // 保存错误响应到数据库
                if (request.getSessionId() != null && request.getUserId() != null) {
                    saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                    log.info("流式聊天异常错误响应已保存到数据库，会话ID：{}，用户ID：{}", request.getSessionId(), request.getUserId());
                }

                return ChatResponseDTO.errorWithContent(errorContent);
            } catch (IOException ioException) {
                log.error("发送异常消息失败", ioException);
                return ChatResponseDTO.failure("System error");
            }
        } finally {
            // 确保会话状态被清理
            if (request.getSessionId() != null) {
                chatInterruptService.markSessionCompleted(request.getSessionId());
            }
        }
    }

    /**
     * 处理流式响应完成
     * @return true表示已通过续写完成保存，false表示需要正常保存
     */
    private boolean handleStreamCompletion(HttpServletResponse response, boolean willGenerateTitle, boolean isRealAiResponse,
                                      ChatRequestDTO request, String aiResponseContent, AiModel model, String interfaceType) {
        try {
            // 检查是否应用截断检测
            boolean shouldApply = truncationDetectionService.shouldApplyTruncationDetection(interfaceType);

            if (shouldApply && isRealAiResponse && aiResponseContent != null && !aiResponseContent.trim().isEmpty()) {
                // 计算Token数量
                int tokenCount = calculateTokenCount(aiResponseContent);

                // 进行截断检测（带Token限制）
                log.info("开始截断检测，会话ID：{}，内容长度：{}，Token数量：{}",
                        request.getSessionId(), aiResponseContent.length(), tokenCount);

                boolean isComplete = truncationDetectionService.isContentComplete(aiResponseContent, tokenCount);

                if (!isComplete) {
                    log.info("检测到内容被截断，开始续写流程，会话ID：{}", request.getSessionId());

                    // 使用线程池执行续写操作，避免在reactor线程中阻塞
                    CompletableFuture<Boolean> continuationFuture = CompletableFuture
                        .supplyAsync(() -> {
                            log.info("续写任务开始执行，线程：{}, 会话ID：{}",
                                    Thread.currentThread().getName(), request.getSessionId());
                            return continueAiResponse(response, request, model, aiResponseContent);
                        }, continuationExecutor)
                        .exceptionally(throwable -> {
                            log.error("续写操作异常，线程：{}, 会话ID：{}",
                                    Thread.currentThread().getName(), request.getSessionId(), throwable);
                            return false;
                        });

                    try {
                        // 等待续写完成（在专用线程中执行，不阻塞reactor线程）
                        boolean continuationSuccess = continuationFuture.join();

                        if (continuationSuccess) {
                            log.info("续写操作成功完成，会话ID：{}", request.getSessionId());

                            // 续写完成后也需要检查是否需要生成标题
                            if (willGenerateTitle && isRealAiResponse) {
                                log.info("续写完成后开始生成标题，会话ID: {}", request.getSessionId());
                                try {
                                    CompletableFuture<String> titleFuture = CompletableFuture.supplyAsync(() -> {
                                        try {
                                            return generateSessionTitle(request.getSessionId(), request.getMessages());
                                        } catch (Exception e) {
                                            log.error("续写完成后标题生成失败", e);
                                            return null;
                                        }
                                    });
                                    String generatedTitle = titleFuture.get(5, TimeUnit.SECONDS);

                                    if (generatedTitle != null && !generatedTitle.equals("New Chat")) {
                                        log.info("续写完成后标题生成成功: {}", generatedTitle);
                                        // 发送包含标题的完成标记
                                        String completionData = buildCompletionData(true, true, generatedTitle);
                                        response.getWriter().write(completionData);
                                        response.getWriter().flush();
                                    } else {
                                        log.info("续写完成后标题生成失败或返回默认标题: {}", generatedTitle);
                                        // 发送不包含标题的完成标记
                                        String completionData = buildCompletionData(true, true, null);
                                        response.getWriter().write(completionData);
                                        response.getWriter().flush();
                                    }
                                } catch (TimeoutException e) {
                                    log.warn("续写完成后标题生成超时，发送完成标记");
                                    String completionData = buildCompletionData(true, true, null);
                                    response.getWriter().write(completionData);
                                    response.getWriter().flush();
                                } catch (Exception e) {
                                    log.error("续写完成后标题生成异常", e);
                                    String completionData = buildCompletionData(true, true, null);
                                    response.getWriter().write(completionData);
                                    response.getWriter().flush();
                                }
                            } else {
                                // 不需要生成标题，直接发送完成标记
                                String completionData = buildCompletionData(willGenerateTitle, isRealAiResponse, null);
                                response.getWriter().write(completionData);
                                response.getWriter().flush();
                            }

                            return true; // 续写成功，已完成保存，不需要继续执行后续逻辑
                        } else {
                            log.warn("续写操作失败，继续正常流程，会话ID：{}", request.getSessionId());
                        }
                    } catch (Exception e) {
                        log.error("等待续写操作完成时发生异常，会话ID：{}", request.getSessionId(), e);
                    }
                }
            }

            String generatedTitle = null;

            // 如果需要生成标题且是真实AI响应，等待标题生成完成
            if (willGenerateTitle && isRealAiResponse) {
                log.info("开始生成标题，会话ID: {}", request.getSessionId());
                try {
                    CompletableFuture<String> titleFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return generateSessionTitle(request.getSessionId(), request.getMessages());
                        } catch (Exception e) {
                            log.error("后台标题生成失败", e);
                            return null;
                        }
                    });
                    generatedTitle = titleFuture.get(5, TimeUnit.SECONDS);

                    if (generatedTitle != null && !generatedTitle.equals("New Chat")) {
                        log.info("标题生成成功: {}", generatedTitle);
                    } else {
                        log.info("标题生成失败或返回默认标题: {}", generatedTitle);
                        generatedTitle = null;
                    }
                } catch (TimeoutException e) {
                    log.warn("标题生成超时，继续发送响应");
                    generatedTitle = null;
                } catch (Exception e) {
                    log.error("标题生成异常", e);
                    generatedTitle = null;
                }
            }

            // 发送完成标记
            String completionData = buildCompletionData(willGenerateTitle, isRealAiResponse, generatedTitle);
            response.getWriter().write(completionData);
            response.getWriter().flush();

            return false; // 正常流程，需要调用者进行保存

        } catch (Exception e) {
            log.error("处理完成标记时发生异常", e);
            try {
                String completionData = buildCompletionData(false, false, null);
                response.getWriter().write(completionData);
                response.getWriter().flush();
            } catch (Exception ex) {
                log.error("发送异常完成标记失败", ex);
            }
            return false; // 异常情况，需要调用者进行保存
        }
    }

    /**
     * 验证JSON字符串是否完整有效
     */
    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 续写缓存配置常量（简化策略：缓存300字符一次性删除三种标签）
    private static final int MIN_CACHE_TIME_MS = 500;    // 最小缓存时间
    private static final int MAX_CACHE_TIME_MS = 2000;   // 最大缓存时间
    private static final int MIN_CACHE_BLOCKS = 3;       // 最小缓存块数
    private static final int MAX_CACHE_BLOCKS = 8;       // 最大缓存块数
    private static final int MIN_CACHE_LENGTH = 300;     // 缓存300字符确保捕获所有AI标记

    /**
     * 处理续写的JSON数据（使用自适应缓存策略）
     */
    private String processContinuationJson(String jsonData, HttpServletResponse response, String originalContent,
                                         StringBuilder continuationContent, StringBuilder alignedContentForSaving,
                                         boolean[] alignmentDone, StringBuilder cacheBuffer, long[] cacheStartTime,
                                         int[] cacheBlockCount, boolean[] cleaningDone) {
        log.debug("=== 开始处理续写JSON数据（自适应缓存策略）===");
        log.debug("续写JSON长度: {}, 对齐状态: {}, 清理状态: {}", jsonData.length(), alignmentDone[0], cleaningDone[0]);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonData);

            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");

                if (delta != null && delta.has("content")) {
                    String content = delta.get("content").asText();
                    if (content != null && !content.isEmpty()) {
                        log.debug("续写JSON解析 - 原始content: [{}]", content);

                        // 如果还没有开始缓存，初始化缓存
                        if (cacheStartTime[0] == 0) {
                            cacheStartTime[0] = System.currentTimeMillis();
                            log.info("续写缓存开始，时间: {}", cacheStartTime[0]);
                        }

                        // 如果清理还没完成，继续缓存
                        if (!cleaningDone[0]) {
                            cacheBuffer.append(content);
                            cacheBlockCount[0]++;
                            log.info("续写缓存中，块数: {}, 缓存长度: {}, 内容预览: [{}]",
                                    cacheBlockCount[0], cacheBuffer.length(),
                                    cacheBuffer.length() > 50 ? cacheBuffer.substring(0, 50) + "..." : cacheBuffer.toString());

                            // 检查是否应该停止缓存并进行清理
                            if (shouldStopCaching(cacheBuffer, cacheStartTime[0], cacheBlockCount[0])) {
                                // 一次性清理所有标签
                                String cachedContent = cacheBuffer.toString();
                                String cleanedCache = cleanAllTagsOnce(cachedContent);
                                log.info("续写缓存清理完成，原长度: {}, 清理后长度: {}, 缓存时间: {}ms, 缓存块数: {}",
                                        cachedContent.length(), cleanedCache.length(),
                                        System.currentTimeMillis() - cacheStartTime[0], cacheBlockCount[0]);

                                cleaningDone[0] = true;
                                cacheBuffer.setLength(0); // 清空缓存

                                // 处理清理后的内容
                                if (!cleanedCache.isEmpty()) {
                                    return processCachedContent(cleanedCache, originalContent, continuationContent,
                                                              alignedContentForSaving, alignmentDone, response, mapper);
                                }
                            }
                            return null; // 继续缓存
                        } else {
                            // 清理已完成，后续内容直接使用，不再检测删除
                            log.debug("续写后续处理 - 直接使用content: [{}]", content.length() > 100 ? content.substring(0, 100) + "..." : content);

                            if (!content.isEmpty()) {
                                // 对齐完成后，直接添加内容（不再清理）
                                alignedContentForSaving.append(content);
                                continuationContent.append(content);

                                // 发送后续内容给前端
                                log.debug("续写发送后续内容给前端，使用智能分块: {}", ENABLE_SMART_CHUNKING);
                                if (ENABLE_SMART_CHUNKING) {
                                    sendContentBySmartChunking(content, response, mapper);
                                } else {
                                    sendContentAsBlock(content, response, mapper);
                                }
                                return content;
                            }
                        }
                    } else {
                        log.debug("续写原始content为空或null");
                    }
                } else {
                    log.debug("续写delta节点不存在content字段");
                }
            } else {
                log.debug("续写choices节点为空或不是数组");
            }

            log.debug("续写JSON处理完成，返回null");
            return null;
        } catch (Exception e) {
            log.error("处理续写JSON数据失败: {}, JSON内容: {}", e.getMessage(), jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData, e);
            return null;
        }
    }

    /**
     * 判断是否应该停止缓存并进行清理（更保守的策略）
     */
    private boolean shouldStopCaching(StringBuilder cache, long startTime, int blockCount) {
        long elapsed = System.currentTimeMillis() - startTime;
        String cacheContent = cache.toString();

        // 安全边界：超过最大限制必须停止
        if (elapsed > MAX_CACHE_TIME_MS || blockCount > MAX_CACHE_BLOCKS) {
            log.info("达到缓存上限，强制停止缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return true;
        }

        // 最小要求：未达到最小要求继续缓存
        if (elapsed < MIN_CACHE_TIME_MS ||
            blockCount < MIN_CACHE_BLOCKS ||
            cache.length() < MIN_CACHE_LENGTH) {
            log.debug("未达到最小缓存要求，继续缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return false;
        }

        // 智能检测：如果有未闭合标签，必须继续缓存
        boolean hasUnclosed = hasUnclosedTags(cacheContent);
        if (hasUnclosed) {
            log.debug("检测到未闭合标签，继续缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return false;
        }

        // 保守策略：即使没有未闭合标签，也要确保缓存足够的内容
        // 如果内容看起来像是AI回复的开头（包含特定模式），继续缓存
        if (looksLikeAiResponseStart(cacheContent) && elapsed < MAX_CACHE_TIME_MS * 0.7) {
            log.debug("内容疑似AI回复开头，继续缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return false;
        }

        log.info("缓存条件满足，停止缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
        return true;
    }

    /**
     * 检查内容是否看起来像AI回复的开头
     */
    private boolean looksLikeAiResponseStart(String content) {
        // 检查是否包含常见的AI回复开头模式
        return content.contains("```") ||
               content.contains("<") ||
               content.contains("•") ||
               content.contains("User") ||
               content.contains("function") ||
               content.contains("class") ||
               content.length() < 500; // 内容较短时继续缓存
    }

    /**
     * 检查是否有未闭合的标签
     */
    private boolean hasUnclosedTags(String content) {
        // 检查thinking标签
        if (content.contains("<thinking>") && !content.contains("</thinking>")) {
            return true;
        }

        // 检查response标签
        if (content.contains("<response>") && !content.contains("</response>")) {
            return true;
        }

        // 检查代码块标记（```）
        int codeBlockCount = 0;
        int index = 0;
        while ((index = content.indexOf("```", index)) != -1) {
            codeBlockCount++;
            index += 3;
        }
        if (codeBlockCount % 2 != 0) {
            return true;
        }

        return false;
    }

    /**
     * 一次性清理所有AI标记
     */
    private String cleanAllTagsOnce(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        String cleaned = content;
        log.debug("开始一次性清理所有标签，原始长度: {}", content.length());

        // 1. 清理thinking标签块
        cleaned = removeThinkingBlocks(cleaned);

        // 2. 清理response标签
        cleaned = removeResponseTags(cleaned);

        // 3. 清理代码块开头标记
        cleaned = removeCodeBlockMarkers(cleaned);

        // 4. 清理多余空白
        cleaned = cleanExtraWhitespace(cleaned);

        log.debug("一次性清理完成，原长度: {}, 清理后长度: {}, 移除字符数: {}",
                content.length(), cleaned.length(), content.length() - cleaned.length());

        return cleaned;
    }

    /**
     * 处理缓存清理后的内容（修复保存逻辑）
     */
    private String processCachedContent(String cleanedCache, String originalContent, StringBuilder continuationContent,
                                      StringBuilder alignedContentForSaving, boolean[] alignmentDone,
                                      HttpServletResponse response, ObjectMapper mapper) {
        if (!alignmentDone[0]) {
            // 第一次处理，需要对齐
            log.info("续写首次内容处理，开始执行对齐算法");
            log.debug("续写对齐参数 - 原内容长度: {}, 续写内容长度: {}", originalContent.length(), cleanedCache.length());

            // 执行对齐算法
            String alignedContent = truncationDetectionService.alignContent(originalContent, cleanedCache);
            log.debug("续写对齐结果 - 对齐后内容长度: {}", alignedContent.length());

            // 计算需要发送给前端的内容（去除重叠部分）
            String contentToSend;
            if (alignedContent.length() > originalContent.length()) {
                contentToSend = alignedContent.substring(originalContent.length());
                log.debug("续写对齐成功，提取新增内容长度: {}", contentToSend.length());
            } else {
                contentToSend = cleanedCache; // 如果对齐失败，发送原始内容
                log.warn("续写对齐失败，使用原始内容，长度: {}", cleanedCache.length());
            }

            // 重要修复：保存完整的对齐内容（包含原内容+续写内容）
            alignedContentForSaving.append(alignedContent);
            continuationContent.append(contentToSend);
            alignmentDone[0] = true;

            // 调试日志：检查发送给前端的清理后内容
            String sendPreview = contentToSend.length() > 200 ?
                "开头: [" + contentToSend.substring(0, 100) + "...] 结尾: [..." + contentToSend.substring(contentToSend.length() - 100) + "]" :
                "完整内容: [" + contentToSend + "]";
            log.info("续写对齐详情 - 原内容长度: {}, 对齐后长度: {}, 发送长度: {}, 保存长度: {}",
                    originalContent.length(), alignedContent.length(), contentToSend.length(), alignedContentForSaving.length());
            log.info("续写发送给前端的清理后内容预览: {}", sendPreview);

            // 额外日志：显示保存内容的预览（用于调试保存逻辑）
            String savePreview = alignedContentForSaving.length() > 200 ?
                "开头: [" + alignedContentForSaving.substring(0, 100) + "...] 结尾: [..." + alignedContentForSaving.substring(alignedContentForSaving.length() - 100) + "]" :
                "完整内容: [" + alignedContentForSaving + "]";
            log.info("续写保存内容预览: {}", savePreview);

            // 发送对齐后的内容给前端
            if (!contentToSend.isEmpty()) {
                log.debug("续写发送对齐后内容给前端，使用智能分块: {}", ENABLE_SMART_CHUNKING);
                if (ENABLE_SMART_CHUNKING) {
                    sendContentBySmartChunking(contentToSend, response, mapper);
                } else {
                    sendContentAsBlock(contentToSend, response, mapper);
                }
                log.debug("续写对齐后内容发送完成");
            } else {
                log.warn("续写对齐后内容为空，跳过发送");
            }

            return contentToSend;
        } else {
            // 已经对齐过，直接发送新内容
            log.debug("续写后续内容处理，直接发送新内容长度: {}", cleanedCache.length());
            alignedContentForSaving.append(cleanedCache);
            continuationContent.append(cleanedCache);

            log.debug("续写发送后续内容给前端，使用智能分块: {}", ENABLE_SMART_CHUNKING);
            if (ENABLE_SMART_CHUNKING) {
                sendContentBySmartChunking(cleanedCache, response, mapper);
            } else {
                sendContentAsBlock(cleanedCache, response, mapper);
            }
            log.debug("续写后续内容发送完成");
            return cleanedCache;
        }
    }

    /**
     * 处理完整的JSON数据
     */
    private String processCompleteJson(String jsonData, HttpServletResponse response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonData);

            log.debug("处理AI API响应: {}", jsonData.length() > 200 ? jsonData.substring(0, 200) + "..." : jsonData);

            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    String content = delta.get("content").asText();
                    if (content != null && !content.isEmpty()) {
                        String cleanContent = cleanContent(content);

                        if (!cleanContent.isEmpty()) {
                            log.debug("AI响应内容: [{}]", cleanContent.length() > 50 ? cleanContent.substring(0, 50) + "..." : cleanContent);

                            if (ENABLE_SMART_CHUNKING) {
                                sendContentBySmartChunking(cleanContent, response, mapper);
                            } else {
                                sendContentAsBlock(cleanContent, response, mapper);
                            }
                            return cleanContent;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("处理完整JSON数据失败: {}, JSON内容: {}", e.getMessage(), jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
            return null;
        }
    }

    /**
     * 清理内容，移除无效字符并过滤特定内容
     */
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }

        // 检查是否包含需要过滤的内容
        if (shouldFilterContent(content)) {
            log.info("检测到需要过滤的AI响应内容，已阻止向前端发送");
            return ""; // 返回空字符串，不向前端发送
        }

        // 基础清理：移除无效字符
        String cleaned = content.replaceAll("[\uFFFD\u0000]", "");
        cleaned = cleaned.replaceAll(" {2,}", " ");

        return cleaned;
    }

    /**
     * 清理续写内容中的AI标记（在流式响应开始时执行）
     */
    private String cleanContinuationContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        String cleaned = content;
        log.debug("开始清理续写内容，原始长度: {}", content.length());

        // 1. 移除完整的 <thinking>...</thinking> 块（最重要的清理）
        cleaned = removeThinkingBlocks(cleaned);

        // 2. 移除开头的 <response> 标签
        cleaned = removeResponseTags(cleaned);

        // 3. 移除开头的代码块标记（```javascript, ```html 等）
        cleaned = removeCodeBlockMarkers(cleaned);

        // 4. 清理多余的空白行
        cleaned = cleanExtraWhitespace(cleaned);

        log.debug("续写内容清理完成，清理后长度: {}, 移除字符数: {}",
                cleaned.length(), content.length() - cleaned.length());

        return cleaned;
    }



    /**
     * 清理thinking标记和内容（续写保存时使用）
     */
    private String cleanThinkingContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        String result = content;
        log.debug("开始清理thinking内容，原始长度: {}", content.length());

        // 检测并删除完整的thinking块
        while (true) {
            int thinkingStart = result.indexOf("<thinking>");
            int thinkingEnd = result.indexOf("</thinking>");

            if (thinkingStart != -1 && thinkingEnd != -1 && thinkingEnd > thinkingStart) {
                // 找到完整的thinking块，删除从<thinking>到</thinking>的所有内容
                String beforeThinking = result.substring(0, thinkingStart);
                String afterThinking = result.substring(thinkingEnd + "</thinking>".length());
                result = beforeThinking + afterThinking;

                log.info("删除了一个完整thinking块，位置: {} - {}", thinkingStart, thinkingEnd + "</thinking>".length());
            } else {
                break; // 没有更多完整的thinking块
            }
        }

        // 删除单独的thinking标记
        result = result.replaceAll("(?m)^\\s*<thinking>\\s*$", "");
        result = result.replaceAll("(?m)^\\s*</thinking>\\s*$", "");

        // 清理多余的空白行
        result = result.replaceAll("\n{3,}", "\n\n");
        result = result.trim();

        log.debug("thinking内容清理完成，清理后长度: {}, 移除字符数: {}",
                result.length(), content.length() - result.length());

        return result;
    }

    /**
     * 移除 <thinking>...</thinking> 块和单独的thinking标记，以及无标签的thinking内容
     */
    private String removeThinkingBlocks(String content) {
        if (content == null) return content;

        String result = content;
        log.debug("removeThinkingBlocks - 输入内容: [{}], 长度: {}", content, content.length());

        // 1. 移除完整的 thinking 块，包括标签和内部所有内容
        // 使用循环确保删除所有thinking块
        while (result.contains("<thinking>") && result.contains("</thinking>")) {
            int startIndex = result.indexOf("<thinking>");
            int endIndex = result.indexOf("</thinking>");

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                String beforeThinking = result.substring(0, startIndex);
                String afterThinking = result.substring(endIndex + "</thinking>".length());
                String removedContent = result.substring(startIndex, endIndex + "</thinking>".length());

                result = beforeThinking + afterThinking;
                log.info("移除了thinking块，完整移除内容: [{}]", removedContent);
            } else {
                break; // 防止无限循环
            }
        }

        // 2. 移除任何位置的单独 <thinking> 标记
        String afterStartTag = result.replaceAll("<thinking>\\s*", "");
        if (!afterStartTag.equals(result)) {
            log.debug("移除了thinking开始标记，原长度: {}, 新长度: {}", result.length(), afterStartTag.length());
        }
        result = afterStartTag;

        // 3. 移除任何位置的单独 </thinking> 标记
        String afterEndTag = result.replaceAll("\\s*</thinking>", "");
        if (!afterEndTag.equals(result)) {
            log.debug("移除了thinking结束标记，原长度: {}, 新长度: {}", result.length(), afterEndTag.length());
        }
        result = afterEndTag;

        // 4. 移除无标签的thinking内容模式（以 • 开头的AI分析内容）
        String thinkingPattern = "(?s)•\\s*[^\\n]*(?:\\n•\\s*[^\\n]*)*";
        String afterThinkingContent = result.replaceAll(thinkingPattern, "");
        if (!afterThinkingContent.equals(result)) {
            log.debug("移除了无标签thinking内容，原长度: {}, 新长度: {}", result.length(), afterThinkingContent.length());
        }
        result = afterThinkingContent;



        if (!result.equals(content)) {
            log.debug("thinking块清理完成，原长度: {}, 新长度: {}, 移除字符数: {}",
                    content.length(), result.length(), content.length() - result.length());
        } else {
            log.debug("thinking块清理无变化，内容: [{}]", content);
        }

        return result;
    }

    /**
     * 移除 <response> 和 </response> 标签（任何位置）
     */
    private String removeResponseTags(String content) {
        if (content == null) return content;

        String result = content;

        // 1. 移除所有 <response> 标签
        result = result.replaceAll("<response>", "");

        // 2. 移除所有 </response> 标签
        result = result.replaceAll("</response>", "");

        if (!result.equals(content)) {
            log.info("移除了 response 标签，原内容: [{}], 清理后: [{}]", content, result);
        }

        return result;
    }

    /**
     * 移除代码块标记（任何位置）
     */
    private String removeCodeBlockMarkers(String content) {
        if (content == null) return content;

        String result = content;

        // 移除任何位置的代码块开始标记，如 ```javascript, ```html, ```css, ```json 等
        result = result.replaceAll("```\\w*", "");

        // 移除单独的 ``` 标记
        result = result.replaceAll("```", "");

        if (!result.equals(content)) {
            log.info("移除了代码块标记，原内容: [{}], 清理后: [{}]", content, result);
        }

        return result;
    }

    /**
     * 清理多余的空白
     */
    private String cleanExtraWhitespace(String content) {
        if (content == null) return content;

        String result = content;

        // 移除开头的空白行
        result = result.replaceAll("^\\s*\\n+", "");

        // 移除结尾的空白行
        result = result.replaceAll("\\n+\\s*$", "");

        return result;
    }

    /**
     * 检查内容是否需要过滤
     * 过滤包含特定广告或推广内容的AI响应
     */
    private boolean shouldFilterContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        // 定义需要过滤的关键词和模式
        String[] filterPatterns = {
            "欢迎使用 Augment Proxy",
            "Augment Proxy 官网",
            "该服务完全免费",
            "如果你是付费获取的，请前往购买平台举报",
            "augment.caicode.org"
        };

        String lowerContent = content.toLowerCase();

        // 检查是否包含任何过滤模式
        for (String pattern : filterPatterns) {
            if (content.contains(pattern) || lowerContent.contains(pattern.toLowerCase())) {
                log.debug("检测到过滤模式: [{}] 在内容中: [{}]", pattern,
                    content.length() > 100 ? content.substring(0, 100) + "..." : content);
                return true;
            }
        }

        // 检查是否包含markdown链接格式的推广内容
        if (content.matches(".*\\[.*Augment.*Proxy.*\\]\\(.*\\).*")) {
            log.debug("检测到Augment Proxy推广链接，内容: [{}]",
                content.length() > 100 ? content.substring(0, 100) + "..." : content);
            return true;
        }

        return false;
    }

    /**
     * 智能分块发送内容
     */
    private void sendContentBySmartChunking(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            int optimalChunkSize = getOptimalChunkSize(content);
            int[] codePoints = content.codePoints().toArray();
            StringBuilder chunk = new StringBuilder();
            int sentChars = 0;

            for (int i = 0; i < codePoints.length; i++) {
                int codePoint = codePoints[i];
                String character = new String(Character.toChars(codePoint));

                if (codePoint != 0xFFFD && codePoint != 0x0000) {
                    chunk.append(character);
                }

                if (chunk.length() >= optimalChunkSize || i == codePoints.length - 1) {
                    try {
                        String chunkContent = chunk.toString();
                        String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(chunkContent));
                        response.getWriter().write(vercelFormat);
                        response.getWriter().flush();

                        sentChars += chunkContent.length();
                        chunk.setLength(0);

                        if (CHUNK_DELAY_MS > 0) {
                            Thread.sleep(CHUNK_DELAY_MS);
                        }
                    } catch (InterruptedException e) {
                        log.warn("续写分块发送被中断: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        // 不要break，尝试发送剩余内容
                        log.info("尝试继续发送剩余内容...");
                    } catch (IOException e) {
                        log.error("续写发送分块内容失败，尝试重试: {}", e.getMessage());
                        // 重试一次
                        try {
                            String chunkContent = chunk.toString();
                            String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(chunkContent));
                            response.getWriter().write(vercelFormat);
                            response.getWriter().flush();
                            sentChars += chunkContent.length();
                            log.info("续写分块重试发送成功");
                        } catch (Exception retryException) {
                            log.error("续写分块重试也失败，跳过此块: {}", retryException.getMessage());
                        }
                        chunk.setLength(0);
                    }
                }
            }

            log.info("完成智能分块流式发送，总字符数: {}, 实际发送: {}, 分块大小: {}", codePoints.length, sentChars, optimalChunkSize);

        } catch (Exception e) {
            log.error("智能分块发送内容失败: {}", e.getMessage());
            try {
                String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(content));
                response.getWriter().write(vercelFormat);
                response.getWriter().flush();
                log.info("回退到块发送模式: [{}]", content);
            } catch (Exception fallbackException) {
                log.error("回退发送也失败: {}", fallbackException.getMessage());
            }
        }
    }

    /**
     * 传统块发送模式
     */
    private void sendContentAsBlock(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            String cleanContent = cleanContent(content);
            if (!cleanContent.isEmpty()) {
                String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(cleanContent));
                response.getWriter().write(vercelFormat);
                response.getWriter().flush();
                log.info("块发送模式数据发送成功: [{}]", cleanContent);
            } else {
                log.debug("块发送模式：内容为空，跳过发送");
            }
        } catch (Exception e) {
            log.error("块发送模式失败: {}", e.getMessage());
        }
    }

    /**
     * 根据内容长度计算最优分块大小
     */
    private int getOptimalChunkSize(String content) {
        int length = content.length();

        if (length <= 20) {
            return length;
        } else if (length <= 100) {
            return Math.max(MIN_CHUNK_SIZE, Math.min(length / 4, 10));
        } else if (length <= 300) {
            return Math.max(10, Math.min(length / 8, 15));
        } else {
            return Math.max(15, Math.min(MAX_CHUNK_SIZE, length / 12));
        }
    }

    /**
     * 构建中断完成标记数据
     */
    private String buildInterruptCompletionData() {
        try {
            Map<String, Object> interruptMap = new HashMap<>();
            interruptMap.put("finishReason", "interrupted");
            interruptMap.put("usage", Map.of(
                "promptTokens", 0,
                "completionTokens", 0
            ));
            interruptMap.put("title", false);
            interruptMap.put("interrupted", true);

            return String.format("d:%s\n", new ObjectMapper().writeValueAsString(interruptMap));
        } catch (Exception e) {
            log.error("构建中断完成标记数据失败", e);
            return "d:{\"finishReason\":\"interrupted\",\"usage\":{\"promptTokens\":0,\"completionTokens\":0},\"title\":false,\"interrupted\":true}\n";
        }
    }

    /**
     * 执行AI响应续写
     */
    private boolean continueAiResponse(HttpServletResponse response, ChatRequestDTO request, AiModel model, String originalContent) {
        try {
            log.info("开始执行续写操作，会话ID：{}，原内容长度：{}", request.getSessionId(), originalContent.length());

            // 构建续写请求的消息列表
            List<Map<String, String>> continuationMessages = buildContinuationMessages(request.getMessages(), originalContent);

            // 创建续写请求
            ChatRequestDTO continuationRequest = new ChatRequestDTO();
            continuationRequest.setMessages(continuationMessages);
            continuationRequest.setUserId(request.getUserId());
            continuationRequest.setModelId(model.getId());
            continuationRequest.setSessionId(request.getSessionId());
            continuationRequest.setDeepThinking(request.getDeepThinking());

            // 用于收集续写内容和对齐状态
            final StringBuilder continuationContent = new StringBuilder();
            final StringBuilder alignedContentForSaving = new StringBuilder();
            final boolean[] hasContentSent = {false};
            final boolean[] isRealAiResponse = {false};
            final boolean[] alignmentDone = {false};

            // 执行续写请求（带对齐的流式处理）
            boolean success = executeContinuationRequestWithAlignment(continuationRequest, model, response,
                    originalContent, continuationContent, alignedContentForSaving, hasContentSent, isRealAiResponse, alignmentDone);

            if (success && hasContentSent[0]) {
                // 保存完整内容到数据库（续写不计入聊天次数，所以直接保存）
                if (request.getSessionId() != null && request.getUserId() != null) {
                    // 简化逻辑：直接拼接原内容和续写内容（续写内容已在缓存阶段清理过）
                    String continuationContentStr = continuationContent.toString();

                    // 拼接完整内容：原内容 + 续写内容（不再清理，因为已在缓存阶段清理过）
                    String finalContent = originalContent + continuationContentStr;

                    // 调试日志：检查保存内容的开头和结尾
                    log.info("续写保存详情 - 原内容长度: {}, 续写长度: {}, 最终长度: {}",
                            originalContent.length(), continuationContentStr.length(), finalContent.length());
                    log.info("续写保存 - 原内容: [{}]", originalContent.length() > 500 ? originalContent.substring(0, 500) + "..." : originalContent);
                    log.info("续写保存 - 续写内容: [{}]", continuationContentStr);
                    log.info("续写保存 - 最终完整内容: [{}]", finalContent.length() > 1000 ? finalContent.substring(0, 1000) + "..." : finalContent);

                    saveAiResponse(request.getSessionId(), request.getUserId(), model.getId(), finalContent);
                    log.info("续写内容已保存到数据库，会话ID：{}，用户ID：{}，最终长度：{}，不计入聊天次数",
                            request.getSessionId(), request.getUserId(), finalContent.length());
                }

                // 发送完成标记
                String completionData = buildCompletionData(false, true, null);
                response.getWriter().write(completionData);
                response.getWriter().flush();

                log.info("续写操作成功，未计入聊天次数");
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("续写操作失败", e);
            return false;
        }
    }

    /**
     * 构建续写消息列表
     */
    private List<Map<String, String>> buildContinuationMessages(List<Map<String, String>> originalMessages, String originalContent) {
        List<Map<String, String>> continuationMessages = new ArrayList<>(originalMessages);

        // 添加被截断的assistant回复内容
        Map<String, String> truncatedAssistantMessage = new HashMap<>();
        truncatedAssistantMessage.put("role", "assistant");
        truncatedAssistantMessage.put("content", originalContent);
        continuationMessages.add(truncatedAssistantMessage);

        // 添加续写提示词作为新的用户消息（但不保存到数据库）
        Map<String, String> continuationPrompt = new HashMap<>();
        continuationPrompt.put("role", "user");
        continuationPrompt.put("content",
            "请直接从上一个回复的截断点继续输出，不要重复已有内容，不要添加解释或分析，保持原有的语言风格和格式。直接继续：");

        continuationMessages.add(continuationPrompt);

        log.info("构建续写消息列表完成，原始消息数：{}，添加截断内容长度：{}，最终消息数：{}",
                originalMessages.size(), originalContent.length(), continuationMessages.size());

        return continuationMessages;
    }

    /**
     * 执行续写请求（带对齐的流式处理）
     */
    private boolean executeContinuationRequestWithAlignment(ChatRequestDTO request, AiModel model, HttpServletResponse response,
                                                          String originalContent, StringBuilder continuationContent,
                                                          StringBuilder alignedContentForSaving, boolean[] hasContentSent,
                                                          boolean[] isRealAiResponse, boolean[] alignmentDone) {
        log.info("=== 开始执行续写请求（自适应缓存策略）===");
        log.info("续写参数 - 用户ID: {}, 模型: {}, 原内容长度: {}", request.getUserId(), model.getModelCode(), originalContent.length());

        // 初始化缓存相关变量
        StringBuilder cacheBuffer = new StringBuilder();
        long[] cacheStartTime = {0};
        int[] cacheBlockCount = {0};
        boolean[] cleaningDone = {false};

        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("stream", true);
            requestBody.put("messages", request.getMessages());
            requestBody.put("model", model.getModelCode());

            // 添加reasoning_effort参数（仅对Gemini模型）
            if (model.getProvider() != null && model.getProvider().toLowerCase().contains("gemini")) {
                requestBody.put("reasoning_effort", "none");
                log.debug("续写请求添加reasoning_effort参数");
            }

            log.info("构建续写消息列表完成，原始消息数：{}，添加截断内容长度：{}，最终消息数：{}",
                    request.getMessages().size() - 2, originalContent.length(), request.getMessages().size());

            // 记录详细的AI请求信息
            logAiRequestDetails(requestBody, model, request.getUserId());

            log.info("发送续写请求到AI API: {}", model.getBaseUrl());

            // 创建WebClient（使用配置的Builder，已禁用SSL验证）
            WebClient webClient = webClientBuilder
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .defaultHeader("Accept-Charset", "UTF-8")
                    .defaultHeader("User-Agent", "OpenAI/PowerShell")
                    .build();

            // 发送请求并处理流式响应
            final int[] chunkCount = {0};
            final int[] totalBytes = {0};

            // 添加缓冲区机制（参考主聊天，防止数据丢失）
            StringBuilder jsonBuffer = new StringBuilder();
            StringBuilder lineBuffer = new StringBuilder();

            log.info("=== 开始发送续写WebClient请求 ===");

            webClient.post()
                    .uri(model.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofSeconds(60))  // 续写设置更长的超时时间
                    .doOnSubscribe(subscription -> log.info("续写WebClient订阅成功，开始接收数据流"))
                    .doOnRequest(n -> log.debug("续写WebClient请求 {} 个数据块", n))
                    .map(dataBuffer -> {
                        int bufferSize = dataBuffer.readableByteCount();
                        byte[] bytes = new byte[bufferSize];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String chunk = new String(bytes, StandardCharsets.UTF_8);

                        chunkCount[0]++;
                        totalBytes[0] += bufferSize;
                        log.debug("续写接收数据块 #{}, 大小: {} bytes, 累计: {} bytes", chunkCount[0], bufferSize, totalBytes[0]);

                        return chunk;
                    })
                    .flatMap(chunk -> {
                        // 将chunk添加到行缓冲区（参考主聊天的处理方式）
                        lineBuffer.append(chunk);

                        // 按换行符分割，但保留最后一个不完整的行
                        String bufferedContent = lineBuffer.toString();
                        String[] lines = bufferedContent.split("\n", -1);
                        List<String> validLines = new ArrayList<>();

                        // 处理除最后一行外的所有行（这些都是完整的行）
                        for (int i = 0; i < lines.length - 1; i++) {
                            String line = lines[i].trim();
                            if (!line.isEmpty()) {
                                validLines.add(line);
                            }
                        }

                        // 最后一行可能不完整，保留在缓冲区中
                        String lastLine = lines[lines.length - 1];
                        lineBuffer.setLength(0);
                        if (!lastLine.isEmpty()) {
                            lineBuffer.append(lastLine);
                        }

                        return Flux.fromIterable(validLines);
                    })
                    .doOnNext(line -> {
                        try {
                            log.debug("续写处理完整行，内容长度: {}, 完整内容: [{}]",
                                    line.length(), line.length() > 100 ? line.substring(0, 100) + "..." : line);

                            if (line.startsWith("data: ")) {
                                if (line.equals("data: [DONE]")) {
                                    log.info("续写接收到完成标记 [DONE]，流式响应结束");
                                    return; // 续写完成
                                }

                                String jsonData = line.substring(6).trim();
                                log.debug("续写提取JSON数据: [{}]", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);

                                // 使用JSON缓冲区处理（参考主聊天）
                                jsonBuffer.append(jsonData);

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    log.debug("续写JSON验证通过，开始处理");
                                    try {
                                        String contentSent = processContinuationJson(bufferedJson, response,
                                                originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone);
                                        if (contentSent != null) {
                                            hasContentSent[0] = true;
                                            isRealAiResponse[0] = true;
                                            // 注意：contentSent已经在processContinuationJson中添加到continuationContent了

                                            log.debug("续写成功处理内容，发送长度: {}, 累计续写长度: {}",
                                                    contentSent.length(), continuationContent.length());
                                        } else {
                                            log.debug("续写处理返回null，可能是空内容或缓存中");
                                        }
                                    } catch (Exception jsonProcessException) {
                                        log.error("处理续写JSON失败，跳过此块: {}, JSON内容: [{}]",
                                                jsonProcessException.getMessage(),
                                                bufferedJson.length() > 200 ? bufferedJson.substring(0, 200) + "..." : bufferedJson);
                                    }
                                    jsonBuffer.setLength(0); // 清空JSON缓冲区
                                } else {
                                    log.debug("续写JSON仍不完整，继续等待: 当前长度 {}", bufferedJson.length());
                                }
                            } else if (!line.trim().isEmpty() && jsonBuffer.length() > 0) {
                                // 处理跨行的JSON片段（参考主聊天）
                                log.debug("续写处理跨行JSON片段: [{}]", line);

                                // 防止缓冲区无限增长
                                if (jsonBuffer.length() > 10000) {
                                    log.warn("续写JSON缓冲区过大，清空重新开始: {}", jsonBuffer.length());
                                    jsonBuffer.setLength(0);
                                    return;
                                }

                                jsonBuffer.append(line.trim());

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    log.debug("续写跨行JSON重组成功，长度: {}", bufferedJson.length());
                                    try {
                                        String contentSent = processContinuationJson(bufferedJson, response,
                                                originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone);
                                        if (contentSent != null) {
                                            hasContentSent[0] = true;
                                            isRealAiResponse[0] = true;
                                        }
                                    } catch (Exception jsonProcessException) {
                                        log.error("处理续写跨行JSON失败: {}", jsonProcessException.getMessage());
                                    }
                                    jsonBuffer.setLength(0);
                                } else {
                                    log.debug("续写跨行JSON仍不完整，继续等待: 当前长度 {}", bufferedJson.length());
                                }
                            } else if (!line.isEmpty()) {
                                log.debug("续写跳过非data行: [{}]", line);
                            }
                        } catch (Exception e) {
                            log.error("处理续写响应块失败，但继续处理: {}, 异常详情: ", e.getMessage(), e);
                            // 不要抛出异常，继续处理下一个chunk
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("续写WebClient流式响应完成，总接收数据块: {}, 总字节数: {}", chunkCount[0], totalBytes[0]);
                        log.info("续写完成状态 - hasContentSent: {}, isRealAiResponse: {}, alignmentDone: {}",
                                hasContentSent[0], isRealAiResponse[0], alignmentDone[0]);
                        log.info("续写内容统计 - 续写内容长度: {}, 对齐后内容长度: {}",
                                continuationContent.length(), alignedContentForSaving.length());

                        // 处理剩余的行缓冲区数据（参考主聊天）
                        if (lineBuffer.length() > 0) {
                            String remainingLine = lineBuffer.toString().trim();
                            if (!remainingLine.isEmpty()) {
                                log.info("续写处理流完成时剩余的行数据: [{}]", remainingLine);
                                try {
                                    if (remainingLine.startsWith("data: ")) {
                                        if (remainingLine.equals("data: [DONE]")) {
                                            log.info("续写收到完成标记（来自剩余数据）");
                                        } else {
                                            String jsonData = remainingLine.substring(6).trim();
                                            jsonBuffer.append(jsonData);
                                            String bufferedJson = jsonBuffer.toString();
                                            if (isValidJson(bufferedJson)) {
                                                log.debug("续写处理剩余JSON成功");
                                                try {
                                                    String contentSent = processContinuationJson(bufferedJson, response,
                                                            originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                            cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone);
                                                    if (contentSent != null) {
                                                        hasContentSent[0] = true;
                                                        isRealAiResponse[0] = true;
                                                    }
                                                } catch (Exception e) {
                                                    log.error("处理续写剩余JSON失败: {}", e.getMessage());
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("续写处理剩余行数据失败: {}", e.getMessage());
                                }
                            }
                            lineBuffer.setLength(0);
                        }

                        // 清理JSON缓冲区
                        if (jsonBuffer.length() > 0) {
                            log.warn("续写流完成时JSON缓冲区仍有数据，长度: {}, 内容: [{}]", jsonBuffer.length(), jsonBuffer.toString());
                            jsonBuffer.setLength(0);
                        }
                    })
                    .doOnCancel(() -> {
                        log.warn("续写WebClient流式响应被取消");
                    })
                    .doOnError(error -> {
                        log.error("续写请求失败，错误类型: {}, 错误消息: {}", error.getClass().getSimpleName(), error.getMessage(), error);
                        log.error("续写失败时状态 - 已接收数据块: {}, 总字节数: {}, hasContentSent: {}",
                                chunkCount[0], totalBytes[0], hasContentSent[0]);

                        // 发送错误信息给前端
                        try {
                            String errorContent = "续写请求失败，请重试";
                            String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                            response.getWriter().write(vercelFormat);
                            response.getWriter().flush();
                            hasContentSent[0] = true;
                            log.info("续写错误消息已发送给前端");
                        } catch (Exception e) {
                            log.error("发送续写错误消息失败", e);
                        }
                    })
                    .onErrorReturn("")  // 错误时返回空字符串，避免异常传播
                    .blockLast();

            log.info("续写WebClient.blockLast()执行完成，最终状态 - hasContentSent: {}", hasContentSent[0]);

            return hasContentSent[0];

        } catch (Exception e) {
            log.error("执行续写请求失败", e);
            return false;
        }
    }

    /**
     * 执行续写请求（原方法保留用于兼容）
     */
    private boolean executeContinuationRequest(ChatRequestDTO request, AiModel model, HttpServletResponse response,
                                             StringBuilder continuationContent, boolean[] hasContentSent, boolean[] isRealAiResponse) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", request.getMessages());
            requestBody.put("stream", true);

            // 只有当provider是Gemini时才添加reasoning_effort参数
            if ("Gemini".equalsIgnoreCase(model.getProvider())) {
                requestBody.put("reasoning_effort", "none");
            }

            // 记录请求信息
            logAiRequestDetails(requestBody, model, request.getUserId());

            // 创建WebClient
            WebClient webClient = webClientBuilder
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .defaultHeader("Accept-Charset", "UTF-8")
                    .defaultHeader("User-Agent", "OpenAI/PowerShell")
                    .build();

            log.info("发送续写请求到AI API: {}", model.getBaseUrl());

            // 处理流式响应
            StringBuilder jsonBuffer = new StringBuilder();

            webClient.post()
                    .uri(model.getBaseUrl())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return new String(bytes, StandardCharsets.UTF_8);
                    })
                    .doOnNext(chunk -> {
                        try {
                            String[] lines = chunk.split("\n");
                            for (String line : lines) {
                                line = line.trim();
                                if (line.startsWith("data: ")) {
                                    if (line.equals("data: [DONE]")) {
                                        return; // 续写完成
                                    }

                                    String jsonData = line.substring(6).trim();
                                    jsonBuffer.append(jsonData);

                                    if (isValidJson(jsonBuffer.toString())) {
                                        String contentSent = processCompleteJson(jsonBuffer.toString(), response);
                                        if (contentSent != null) {
                                            hasContentSent[0] = true;
                                            isRealAiResponse[0] = true;
                                            continuationContent.append(contentSent);
                                        }
                                        jsonBuffer.setLength(0);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理续写响应块失败", e);
                        }
                    })
                    .doOnError(error -> log.error("续写请求失败", error))
                    .blockLast();

            return hasContentSent[0];

        } catch (Exception e) {
            log.error("执行续写请求失败", e);
            return false;
        }
    }

    /**
     * 构建完成标记数据
     */
    private String buildCompletionData(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) {
        try {
            Map<String, Object> completionMap = new HashMap<>();
            completionMap.put("finishReason", "stop");
            completionMap.put("usage", Map.of(
                "promptTokens", 10,
                "completionTokens", 20
            ));

            if (isRealAiResponse && willGenerateTitle) {
                if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                    completionMap.put("title", generatedTitle.trim());
                } else {
                    completionMap.put("title", false);
                }
            } else {
                completionMap.put("title", false);
            }

            return String.format("d:%s\n", new ObjectMapper().writeValueAsString(completionMap));
        } catch (Exception e) {
            log.error("构建完成标记数据失败", e);
            return "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":10,\"completionTokens\":20},\"title\":false}\n";
        }
    }

    @Override
    public String generateSessionTitle(String sessionId, List<Map<String, String>> messages) {
        log.info("=== 开始后台同步标题生成流程 ===");
        log.info("会话ID: {}, 消息数量: {}", sessionId, messages.size());

        try {
            // 获取固定的标题生成模型（ID为1L）
            AiModel titleModel = aiModelService.getById(4L);
            if (titleModel == null || titleModel.getIsAvailable() != 1) {
                log.error("标题生成模型（ID=1L）不可用，跳过标题生成");
                return null;
            }
            log.info("标题生成模型: {}", titleModel.getModelName());

            // 构建对话内容
            String conversationContent = buildConversationContent(messages);
            if (conversationContent == null || conversationContent.trim().isEmpty()) {
                log.warn("无法构建对话内容，跳过标题生成");
                return null;
            }
            log.info("对话内容构建成功，长度: {}字符", conversationContent.length());

            // 构建标题生成提示词
            String titlePrompt = buildTitlePrompt(conversationContent);
            log.info("标题提示词构建成功，长度: {}字符", titlePrompt.length());

            // 调用AI服务生成标题
            String rawTitle = callAiForTitleWithHttpClient(titlePrompt, titleModel);
            if (rawTitle == null || rawTitle.trim().isEmpty()) {
                log.warn("AI服务返回空标题");
                return null;
            }
            log.info("AI服务返回原始标题: [{}]", rawTitle);

            // 清理标题
            String cleanedTitle = cleanTitle(rawTitle);
            log.info("标题清理完成: [{}] -> [{}]", rawTitle, cleanedTitle);

            // 保存标题到数据库
            if (cleanedTitle != null && !cleanedTitle.equals("New Chat")) {
                boolean saved = chatSessionService.updateSessionTitle(sessionId, cleanedTitle);
                if (saved) {
                    log.info("标题保存到数据库成功: {}", cleanedTitle);
                } else {
                    log.error("标题保存到数据库失败: {}", cleanedTitle);
                }
            } else {
                log.warn("标题为空或为默认标题，不保存到数据库: [{}]", cleanedTitle);
            }

            log.info("=== 后台同步标题生成流程完成，最终标题: [{}] ===", cleanedTitle);
            return cleanedTitle;

        } catch (Exception e) {
            log.error("后台同步标题生成过程中发生异常", e);
            return null;
        }
    }

    /**
     * 构建对话内容
     */
    private String buildConversationContent(List<Map<String, String>> messages) {
        log.info("开始构建对话内容，消息数量: {}", messages != null ? messages.size() : 0);

        if (messages == null || messages.isEmpty()) {
            log.warn("消息列表为空，无法构建对话内容");
            return null;
        }

        StringBuilder conversationBuilder = new StringBuilder();
        int userMessageCount = 0;
        int assistantMessageCount = 0;

        for (Map<String, String> message : messages) {
            String role = message.get("role");
            String content = message.get("content");

            if ("user".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("User: ").append(content.trim()).append("\n");
                userMessageCount++;
            } else if ("assistant".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("AI: ").append(content.trim()).append("\n");
                assistantMessageCount++;
            }
        }

        String result = conversationBuilder.toString().trim();
        log.info("对话内容构建完成，用户消息: {}条，AI消息: {}条，总长度: {}字符",
                userMessageCount, assistantMessageCount, result.length());

        if (result.isEmpty()) {
            log.warn("构建的对话内容为空");
            return null;
        }

        return result;
    }

    /**
     * 构建标题生成提示词
     */
    private String buildTitlePrompt(String conversationContent) {
        log.info("构建标题生成提示词，对话内容长度: {}字符", conversationContent != null ? conversationContent.length() : 0);

        String prompt = PromptConstants.TITLE_GENERATION_PROMPT
            .replace("{conversationContent}", conversationContent);

        log.debug("标题生成提示词构建完成，总长度: {}字符", prompt.length());
        return prompt;
    }

    /**
     * 清理标题内容
     */
    private String cleanTitle(String title) {
        log.info("开始清理标题，原始标题: [{}]", title);

        if (title == null || title.trim().isEmpty()) {
            log.warn("标题为null或空，返回默认标题");
            return "New Chat";
        }

        // 基础清理：移除引号和多余空格
        String cleaned = title.trim()
            .replaceAll("^[\"']+|[\"']+$", "") // 移除开头和结尾的引号
            .replaceAll("\\s+", " ") // 合并多个空格
            .trim();

        log.info("标题清理完成: [{}] -> [{}]", title, cleaned);

        // 如果清理后为空，返回默认标题
        if (cleaned.isEmpty()) {
            log.warn("清理后标题为空，返回默认标题");
            return "New Chat";
        }

        return cleaned;
    }

    /**
     * 使用HTTP客户端调用AI服务生成标题
     */
    private String callAiForTitleWithHttpClient(String titlePrompt, AiModel model) {
        try {
            log.info("开始使用HTTP客户端调用AI服务生成标题");

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());

            List<Map<String, String>> titleMessages = new ArrayList<>();
            Map<String, String> titleMessage = new HashMap<>();
            titleMessage.put("role", "user");
            titleMessage.put("content", titlePrompt);
            titleMessages.add(titleMessage);

            requestBody.put("messages", titleMessages);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 50);
            requestBody.put("temperature", 0.7);

            // 只有当provider是Gemini时才添加reasoning_effort参数
            if ("Gemini".equalsIgnoreCase(model.getProvider())) {
                requestBody.put("reasoning_effort", "none");
                log.debug("检测到Gemini模型，添加reasoning_effort=none参数");
            }

            // 记录详细的AI标题生成请求信息
            logAiTitleRequestDetails(requestBody, model);

            log.info("AI标题生成请求体构建完成");

            // 使用注入的RestTemplate进行HTTP调用（已禁用SSL验证）

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(model.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("开始发送HTTP请求到: {}", model.getBaseUrl());

            // 发送请求并获取响应
            ResponseEntity<String> response = restTemplate.postForEntity(
                model.getBaseUrl(),
                entity,
                String.class
            );

            String responseBody = response.getBody();
            log.info("收到AI服务响应，响应长度: {}字符", responseBody != null ? responseBody.length() : 0);

            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.warn("AI服务返回空响应");
                return null;
            }

            // 解析响应
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(responseBody);
            log.info("AI服务响应JSON解析成功");

            JsonNode choices = responseJson.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String title = message.get("content").asText();
                    log.info("AI服务返回标题: [{}]", title);
                    return title;
                } else {
                    log.warn("message节点不存在或没有content字段");
                }
            } else {
                log.warn("choices节点为空或不是数组");
            }

            log.warn("AI服务响应格式异常，完整响应: {}", responseBody);
            return null;

        } catch (Exception e) {
            log.error("使用HTTP客户端调用AI服务生成标题失败", e);
            return null;
        }
    }

    @Override
    public List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId) {
        // 如果没有文件ID或消息列表为空，直接返回原消息列表
        if (filesId == null || filesId.trim().isEmpty() || messages == null || messages.isEmpty()) {
            return messages;
        }

        try {
            // 解析文件ID列表
            String[] fileIds = filesId.split(",");
            if (fileIds.length == 0) {
                return messages;
            }

            // 获取文件内容
            StringBuilder fileContentBuilder = new StringBuilder();
            fileContentBuilder.append("以下是文件内容：[");

            boolean hasValidFiles = false;
            for (int i = 0; i < fileIds.length; i++) {
                String fileId = fileIds[i].trim();
                if (fileId.isEmpty()) {
                    continue;
                }

                try {
                    // 获取文件信息和内容
                    String fileContent = fileUploadService.getFileContent(fileId, userId);
                    if (fileContent != null && !fileContent.trim().isEmpty()) {
                        // 获取文件名
                        com.instser.chat.entity.FileUpload fileUpload = fileUploadService.getByFileId(fileId);
                        String fileName = fileUpload != null ? fileUpload.getFileName() : "unknown";

                        if (hasValidFiles) {
                            fileContentBuilder.append(",");
                        }

                        // 构建文件内容格式：{"fileName":"fileContent"}
                        fileContentBuilder.append("{\"").append(fileName)
                                         .append("\":\"").append(escapeJsonString(fileContent)).append("\"}");

                        hasValidFiles = true;
                        log.info("成功获取文件内容，文件ID：{}，文件名：{}，内容长度：{}", fileId, fileName, fileContent.length());
                    } else {
                        log.warn("文件内容为空或无法获取，文件ID：{}", fileId);
                    }
                } catch (Exception e) {
                    log.error("获取文件内容失败，文件ID：{}", fileId, e);
                }
            }

            fileContentBuilder.append("]");

            // 如果有有效的文件内容，则附加到最后一条用户消息中
            if (hasValidFiles) {
                // 找到最后一条用户消息
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, String> message = messages.get(i);
                    if ("user".equals(message.get("role"))) {
                        String originalContent = message.get("content");
                        String newContent = originalContent + "。" + fileContentBuilder.toString();
                        message.put("content", newContent);

                        log.info("已将文件内容附加到用户消息中，原始内容长度：{}，附加后长度：{}",
                                originalContent.length(), newContent.length());
                        break;
                    }
                }
            }

            return messages;

        } catch (Exception e) {
            log.error("处理文件内容失败", e);
            return messages; // 出错时返回原消息列表
        }
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 记录详细的AI请求信息
     */
    private void logAiRequestDetails(Map<String, Object> requestBody, AiModel model, Long userId) {
        try {
            log.info("=== AI请求详细信息 ===");
            log.info("用户ID: {}", userId);
            log.info("模型: {} ({})", model.getModelCode(), model.getBaseUrl());

            // 处理请求体，截取content字段
            Map<String, Object> processedRequestBody = truncateRequestBodyContent(requestBody);

            // 输出处理后的原始请求体
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBodyJson = objectMapper.writeValueAsString(processedRequestBody);
            log.info("原始请求体: {}", requestBodyJson);

            log.info("=== AI请求信息结束 ===");
        } catch (Exception e) {
            log.warn("记录AI请求详细信息失败: {}", e.getMessage());
        }
    }

    /**
     * 记录详细的AI标题生成请求信息
     */
    private void logAiTitleRequestDetails(Map<String, Object> requestBody, AiModel model) {
        try {
            log.info("=== AI标题生成请求详细信息 ===");
            log.info("模型: {} ({})", model.getModelCode(), model.getBaseUrl());

            // 处理请求体，截取content字段
            Map<String, Object> processedRequestBody = truncateRequestBodyContent(requestBody);

            // 输出处理后的原始请求体
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBodyJson = objectMapper.writeValueAsString(processedRequestBody);
            log.info("原始请求体: {}", requestBodyJson);

            log.info("=== AI标题生成请求信息结束 ===");
        } catch (Exception e) {
            log.warn("记录AI标题生成请求详细信息失败: {}", e.getMessage());
        }
    }

    /**
     * 截取请求体中messages里的content字段，只保留前30和后30个字符
     */
    private Map<String, Object> truncateRequestBodyContent(Map<String, Object> requestBody) {
        try {
            // 深拷贝请求体，避免修改原始数据
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonString = objectMapper.writeValueAsString(requestBody);
            Map<String, Object> processedBody = objectMapper.readValue(jsonString, Map.class);

            // 处理messages数组
            Object messagesObj = processedBody.get("messages");
            if (messagesObj instanceof List) {
                List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesObj;
                for (Map<String, Object> message : messages) {
                    Object contentObj = message.get("content");
                    if (contentObj instanceof String) {
                        String content = (String) contentObj;
                        String truncatedContent = truncateContent(content, 30, 30);
                        message.put("content", truncatedContent);
                    }
                }
            }

            return processedBody;
        } catch (Exception e) {
            log.warn("处理请求体content字段失败: {}", e.getMessage());
            return requestBody; // 失败时返回原始请求体
        }
    }

    /**
     * 截取字符串，保留前n和后n个字符
     */
    private String truncateContent(String content, int prefixLength, int suffixLength) {
        if (content == null) {
            return null;
        }

        if (content.length() <= prefixLength + suffixLength) {
            return content; // 内容较短，直接返回
        }

        String prefix = content.substring(0, prefixLength);
        String suffix = content.substring(content.length() - suffixLength);
        return prefix + "..." + suffix;
    }





    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("认证信息为空或未认证");
            return null;
        }
        try {
            // 检查是否是CustomUserDetails类型
            Object principal = authentication.getPrincipal();
            log.debug("认证主体类型：{}", principal != null ? principal.getClass().getName() : "null");
            if (principal instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) principal;
                Long userId = userDetails.getUserId();
                log.debug("从认证信息中获取到用户ID：{}", userId);
                return userId;
            }
            // 如果不是CustomUserDetails，返回null表示认证失败
            log.warn("认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return null;
        }
    }

    @Override
    public void processRetryChat(String sessionId, String messageId, Long modelId, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理重新生成请求，sessionId：{}，messageId：{}，modelId：{}", sessionId, messageId, modelId);

        try {
            // 设置响应头
            setStreamResponseHeaders(response);

            // 验证聊天权限和会话
            ChatValidationResult validation = validateChatPermissions(sessionId, messageId, "assistant", authentication);
            if (!validation.isSuccess()) {
                writeErrorResponse(response, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            log.info("重新生成请求 - 验证通过，用户ID：{}，会话ID：{}", userId, sessionId);

            // 检查聊天频率限制
            String rateLimitError = validateChatRateLimit(userId);
            if (rateLimitError != null) {
                writeErrorResponse(response, rateLimitError);
                saveErrorResponse(sessionId, userId, modelId, rateLimitError);
                return;
            }

            // 删除指定AI消息及之后的所有消息
            int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
            if (deletedCount == 0) {
                writeErrorResponse(response, "删除消息失败");
                return;
            }

            log.info("成功删除{}条消息，准备重新生成AI回复", deletedCount);

            // 获取删除后的会话消息，构建新的聊天请求
            List<ChatMessage> remainingMessages = chatMessageService.getSessionMessages(sessionId);
            if (remainingMessages.isEmpty()) {
                writeErrorResponse(response, "会话中没有可用的消息");
                return;
            }

            // 找到最后一条用户消息
            ChatMessage lastUserMessage = null;
            for (int i = remainingMessages.size() - 1; i >= 0; i--) {
                ChatMessage message = remainingMessages.get(i);
                if ("user".equals(message.getRole())) {
                    lastUserMessage = message;
                    break;
                }
            }

            if (lastUserMessage == null) {
                writeErrorResponse(response, "未找到用户消息");
                return;
            }

            // 构建消息列表用于AI请求
            List<Map<String, String>> messages = new ArrayList<>();
            ChatMessage lastUserMessageEntity = null;
            for (ChatMessage message : remainingMessages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", message.getRole());
                msgMap.put("content", message.getContent());
                messages.add(msgMap);

                // 记录最后一条用户消息实体，用于检查附件
                if ("user".equals(message.getRole())) {
                    lastUserMessageEntity = message;
                }
            }

            // 检查最后一条用户消息是否有附件，如果有则附加文件内容供AI处理
            log.debug("检查重试消息附件，lastUserMessageEntity: {}, hasAttachments: {}",
                    lastUserMessageEntity != null ? lastUserMessageEntity.getMessageId() : "null",
                    lastUserMessageEntity != null ? lastUserMessageEntity.getHasAttachments() : "null");

            if (lastUserMessageEntity != null && lastUserMessageEntity.getHasAttachments() != null && lastUserMessageEntity.getHasAttachments() > 0) {
                // 获取该消息关联的文件ID列表
                String filesId = fileUploadService.getFileIdsByMessageId(lastUserMessageEntity.getMessageId());
                log.debug("重试消息关联的文件ID列表：{}", filesId);

                if (filesId != null && !filesId.trim().isEmpty()) {
                    // 找到最后一条用户消息并附加文件内容
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Map<String, String> msg = messages.get(i);
                        if ("user".equals(msg.get("role"))) {
                            log.info("开始为重试请求的用户消息附加文件内容，原始内容：{}，文件ID：{}",
                                    msg.get("content"), filesId);

                            // 处理文件内容并附加到这条消息
                            List<Map<String, String>> tempMessages = new ArrayList<>();
                            tempMessages.add(msg);
                            List<Map<String, String>> processedMessages = processFilesContent(tempMessages, filesId, userId);
                            if (!processedMessages.isEmpty()) {
                                // 更新消息内容（仅用于AI处理）
                                String originalContent = msg.get("content");
                                String newContent = processedMessages.get(0).get("content");
                                msg.put("content", newContent);
                                log.info("已为重试请求的用户消息附加文件内容，原始长度：{}，附加后长度：{}，文件ID：{}",
                                        originalContent.length(), newContent.length(), filesId);
                            } else {
                                log.warn("processFilesContent返回空结果，文件ID：{}", filesId);
                            }
                            break;
                        }
                    }
                } else {
                    log.info("重试消息没有关联的文件ID，messageId：{}", lastUserMessageEntity.getMessageId());
                }
            } else {
                log.info("重试消息没有附件标识，messageId：{}",
                        lastUserMessageEntity != null ? lastUserMessageEntity.getMessageId() : "null");
            }

            // 获取并验证AI模型
            AiModel model = getAndValidateModel(modelId, chatSession);
            if (model == null) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                saveErrorResponse(sessionId, chatSession.getUserId(), 1L, errorContent);
                log.info("重试请求AI模型不可用错误响应已保存到数据库，会话ID：{}，用户ID：{}", sessionId, chatSession.getUserId());
                return;
            }

            // 更新会话模型（如果需要）
            updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.getCanChat()) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                saveErrorResponse(sessionId, userId, model.getId(), errorContent);
                log.info("重试请求聊天权限验证失败错误响应已保存到数据库，会话ID：{}，用户ID：{}，原因：{}",
                        sessionId, userId,
                        limitCheck.getLimitReason() != null ? limitCheck.getLimitReason() : "Chat permission verification failed");
                return;
            }

            // 构建ChatRequestDTO
            ChatRequestDTO retryRequest = new ChatRequestDTO();
            retryRequest.setMessages(messages);
            retryRequest.setUserId(userId);
            retryRequest.setModelId(model.getId());
            retryRequest.setSessionId(sessionId);
            retryRequest.setDeepThinking(false); // 重新生成时使用默认模式

            // 判断是否需要生成标题（如果删除后只剩一条用户消息，则需要生成标题）
            boolean willGenerateTitle = isFirstAiResponse(messages);
            log.info("重新生成请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                messages.stream().filter(msg -> "user".equals(msg.get("role"))).count());

            // 调用AI服务进行流式响应
            ChatResponseDTO result = streamChatWithAI(retryRequest, model, response, willGenerateTitle, "retry");

            // 处理响应结果
            handleAiResponseResult(result, userId, sessionId, model, "重新生成");

        } catch (Exception e) {
            log.error("处理重新生成请求异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                var chatSession = chatSessionService.getSessionInfo(sessionId);
                if (chatSession != null) {
                    AiModel model = aiModelService.getById(chatSession.getModelId());
                    if (model != null) {
                        saveErrorResponse(sessionId, chatSession.getUserId(), model.getId(), errorContent);
                    }
                }
            } catch (Exception ex) {
                log.error("发送重新生成错误消息失败", ex);
            }
        }
    }

    @Override
    public void processEditResendChat(String sessionId, String messageId, String newContent, String filesId, Long modelId, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理编辑重发请求，sessionId：{}，messageId：{}，newContent长度：{}，filesId：{}，modelId：{}", sessionId, messageId, newContent.length(), filesId, modelId);

        try {
            // 设置响应头
            setStreamResponseHeaders(response);

            // 验证聊天权限和会话
            ChatValidationResult validation = validateChatPermissions(sessionId, messageId, "user", authentication);
            if (!validation.isSuccess()) {
                writeErrorResponse(response, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            log.info("编辑重发请求 - 验证通过，用户ID：{}，会话ID：{}", userId, sessionId);

            // 检查聊天频率限制
            String rateLimitError = validateChatRateLimit(userId);
            if (rateLimitError != null) {
                writeErrorResponse(response, rateLimitError);
                saveErrorResponse(sessionId, userId, modelId, rateLimitError);
                return;
            }

            // 在删除消息之前，先获取被编辑消息的文件关联信息
            String originalFilesId = fileUploadService.getFileIdsByMessageId(messageId);
            log.info("被编辑消息的原始文件关联：{}", originalFilesId);

            // edit-resend场景不会传入filesId，只需要处理原有的文件关联
            boolean hasAttachments = originalFilesId != null && !originalFilesId.trim().isEmpty();

            // 先删除指定用户消息及之后的所有消息，但保留文件关联（如果有的话）
            if (hasAttachments) {
                // 如果有文件关联，使用特殊的删除方法保留文件
                List<String> preserveFileIds = Arrays.asList(originalFilesId.split(","));
                int deletedCount = chatMessageService.deleteMessagesFromPointWithFilePreservation(messageId, sessionId, preserveFileIds);
                if (deletedCount == 0) {
                    writeErrorResponse(response, "删除消息失败");
                    return;
                }
                log.info("成功删除{}条消息（保留文件）", deletedCount);
            } else {
                // 没有文件关联，正常删除
                int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
                if (deletedCount == 0) {
                    writeErrorResponse(response, "删除消息失败");
                    return;
                }
                log.info("成功删除{}条消息", deletedCount);
            }

            // 保存新的用户原始消息（不包含文件内容）
            ChatMessage newUserMessage = new ChatMessage();
            newUserMessage.setMessageId(java.util.UUID.randomUUID().toString());
            newUserMessage.setSessionId(sessionId);
            newUserMessage.setRole("user");
            newUserMessage.setContent(newContent); // 保存原始内容
            newUserMessage.setMessageType("text");
            newUserMessage.setHasAttachments(hasAttachments ? 1 : 0); // 设置附件标识
            newUserMessage.setUserId(userId);
            newUserMessage.setModelId(chatSession.getModelId());
            newUserMessage.setStatus(1); // 成功状态
            newUserMessage.setCreatedTime(LocalDateTime.now());
            newUserMessage.setUpdateTime(LocalDateTime.now());

            boolean saved = chatMessageService.save(newUserMessage);
            if (!saved) {
                writeErrorResponse(response, "保存用户消息失败");
                return;
            }

            log.info("成功保存新的用户消息，messageId：{}，附件：{}", newUserMessage.getMessageId(), hasAttachments ? "有" : "无");

            // 如果有附件，将保留的文件关联转移到新消息
            if (hasAttachments) {
                log.info("edit-resend场景：开始转移文件关联，原始文件ID：{}，新消息ID：{}",
                        originalFilesId, newUserMessage.getMessageId());

                updateFilesAssociation(originalFilesId, newUserMessage.getMessageId(), sessionId, userId);

                // 验证文件关联是否成功转移
                String updatedFilesId = fileUploadService.getFileIdsByMessageId(newUserMessage.getMessageId());
                log.info("edit-resend场景：文件关联转移完成，新消息关联的文件ID：{}，预期文件ID：{}",
                        updatedFilesId, originalFilesId);

                if (updatedFilesId == null || !updatedFilesId.equals(originalFilesId)) {
                    log.error("edit-resend场景：文件关联转移失败！预期：{}，实际：{}", originalFilesId, updatedFilesId);
                } else {
                    log.info("edit-resend场景：文件关联转移成功验证通过");
                }
            }

            // 获取更新后的会话消息，构建新的聊天请求
            List<ChatMessage> remainingMessages = chatMessageService.getSessionMessages(sessionId);
            if (remainingMessages.isEmpty()) {
                writeErrorResponse(response, "会话中没有可用的消息");
                return;
            }

            // 构建消息列表用于AI请求
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage message : remainingMessages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", message.getRole());
                msgMap.put("content", message.getContent());
                messages.add(msgMap);
            }

            // 如果最后一条用户消息有附件，需要附加文件内容供AI处理
            log.debug("检查编辑重发消息附件，hasAttachments: {}, originalFilesId: {}", hasAttachments, originalFilesId);

            if (hasAttachments && !messages.isEmpty()) {
                // 找到最后一条用户消息
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, String> msg = messages.get(i);
                    if ("user".equals(msg.get("role"))) {
                        log.info("开始为编辑重发的用户消息附加文件内容，原始内容：{}，文件ID：{}",
                                msg.get("content"), originalFilesId);

                        // 处理文件内容并附加到这条消息
                        List<Map<String, String>> tempMessages = new ArrayList<>();
                        tempMessages.add(msg);
                        List<Map<String, String>> processedMessages = processFilesContent(tempMessages, originalFilesId, userId);
                        if (!processedMessages.isEmpty()) {
                            // 更新消息内容（仅用于AI处理）
                            String originalContent = msg.get("content");
                            String processedContent = processedMessages.get(0).get("content");
                            msg.put("content", processedContent);
                            log.info("已为编辑重发的用户消息附加文件内容，原始长度：{}，附加后长度：{}，文件数量：{}",
                                    originalContent.length(), processedContent.length(), originalFilesId.split(",").length);
                        } else {
                            log.warn("processFilesContent返回空结果，文件ID：{}", originalFilesId);
                        }
                        break;
                    }
                }
            } else {
                log.info("编辑重发消息没有附件或消息列表为空，hasAttachments: {}, messagesSize: {}",
                        hasAttachments, messages.size());
            }

            // 获取并验证AI模型
            AiModel model = getAndValidateModel(modelId, chatSession);
            if (model == null) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                saveErrorResponse(sessionId, chatSession.getUserId(), 1L, errorContent);
                log.info("编辑重发请求AI模型不可用错误响应已保存到数据库，会话ID：{}，用户ID：{}", sessionId, chatSession.getUserId());
                return;
            }

            // 更新会话模型（如果需要）
            updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.getCanChat()) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                saveErrorResponse(sessionId, userId, model.getId(), errorContent);
                log.info("编辑重发请求聊天权限验证失败错误响应已保存到数据库，会话ID：{}，用户ID：{}，原因：{}",
                        sessionId, userId,
                        limitCheck.getLimitReason() != null ? limitCheck.getLimitReason() : "Chat permission verification failed");
                return;
            }

            // 构建ChatRequestDTO
            ChatRequestDTO editResendRequest = new ChatRequestDTO();
            editResendRequest.setMessages(messages);
            editResendRequest.setUserId(userId);
            editResendRequest.setModelId(model.getId());
            editResendRequest.setSessionId(sessionId);
            editResendRequest.setDeepThinking(false); // 编辑重发时使用默认模式

            // 判断是否需要生成标题（如果编辑的是第一条用户消息，则需要生成标题）
            boolean willGenerateTitle = isFirstAiResponse(messages);
            log.info("编辑重发请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                messages.stream().filter(msg -> "user".equals(msg.get("role"))).count());

            // 调用AI服务进行流式响应
            ChatResponseDTO result = streamChatWithAI(editResendRequest, model, response, willGenerateTitle, "edit-resend");

            // 处理响应结果
            handleAiResponseResult(result, userId, sessionId, model, "编辑重发");

        } catch (Exception e) {
            log.error("处理编辑重发请求异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                var chatSession = chatSessionService.getSessionInfo(sessionId);
                if (chatSession != null) {
                    AiModel model = aiModelService.getById(chatSession.getModelId());
                    if (model != null) {
                        saveErrorResponse(sessionId, chatSession.getUserId(), model.getId(), errorContent);
                    }
                }
            } catch (Exception ex) {
                log.error("发送编辑重发错误消息失败", ex);
            }
        }
    }

    /**
     * 从会话中获取模型ID
     *
     * @param sessionId 会话ID
     * @return 模型ID，如果获取失败则返回null
     */
    private Long getModelIdFromSession(String sessionId) {
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
        return null; // 返回null，让sendMessage方法使用默认值
    }

    @Override
    public void savePartialAiResponse(String sessionId, Long userId, Long modelId, String partialContent, boolean isInterrupted) {
        try {
            if (sessionId != null && userId != null && modelId != null && partialContent != null && !partialContent.trim().isEmpty()) {
                // 为中断的响应添加标识
                String contentToSave = isInterrupted ? partialContent + " [已中断]" : partialContent;

                // 保存部分AI响应消息到数据库并获取Token信息
                MessageSaveResult result = chatMessageService.sendMessageWithTokenInfo(sessionId, "assistant", contentToSave, userId, modelId);

                // 记录Token使用量（中断响应也应该计入Token）
                if (result.isSuccess() && result.getTokenCount() > 0) {
                    boolean tokenRecorded = chatLimitService.recordTokenUsage(userId, result.getTokenCount());
                    if (tokenRecorded) {
                        log.debug("用户{}部分AI响应Token使用记录成功，使用了{}个Token", userId, result.getTokenCount());
                    } else {
                        log.warn("用户{}部分AI响应Token使用记录失败", userId);
                    }
                }

                String logMessage = isInterrupted ? "部分AI响应（中断）已保存到数据库" : "部分AI响应已保存到数据库";
                log.info("{}，会话ID：{}，用户ID：{}，模型ID：{}，内容长度：{}，Token数：{}",
                        logMessage, sessionId, userId, modelId, partialContent.length(),
                        result.isSuccess() ? result.getTokenCount() : 0);
            } else {
                log.warn("无法保存部分AI响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}, contentLength={}",
                        sessionId, userId, modelId, partialContent != null ? partialContent.length() : 0);
            }
        } catch (Exception e) {
            log.error("保存部分AI响应到数据库失败", e);
        }
    }

    /**
     * 处理文件内容用于日志记录，截取每个文件的content部分
     */
    private String processFileContentForLogging(String fileContentPart) {
        if (fileContentPart == null || fileContentPart.trim().isEmpty()) {
            return "";
        }

        try {
            StringBuilder result = new StringBuilder();

            // 使用正则表达式匹配每个文件的JSON格式：{"fileName":"content"}
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\"([^\"]+)\":\"([^\"]*(?:\\\\.[^\"]*)*)\"}");
            java.util.regex.Matcher matcher = pattern.matcher(fileContentPart);

            boolean first = true;
            while (matcher.find()) {
                if (!first) {
                    result.append(",");
                }
                first = false;

                String fileName = matcher.group(1);
                String content = matcher.group(2);

                // 截取content，保留前后各15字符
                String truncatedContent = truncateFileContent(content);

                result.append("{\"").append(fileName).append("\":\"").append(truncatedContent).append("\"}");
            }

            return result.toString();

        } catch (Exception e) {
            log.debug("处理文件内容用于日志记录失败", e);
            return fileContentPart; // 出错时返回原内容
        }
    }

    /**
     * 截取单个文件的内容，保留前后各15字符
     */
    private String truncateFileContent(String content) {
        if (content == null) {
            return "";
        }

        // 处理转义字符
        String unescaped = content.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");

        if (unescaped.length() <= 30) {
            return content; // 返回原始转义格式
        }

        // 截取前15和后15字符
        String prefix = unescaped.substring(0, 15);
        String suffix = unescaped.substring(unescaped.length() - 15);

        // 重新转义用于JSON格式
        String truncated = prefix + "..." + suffix;
        return truncated.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * 统计文件内容中的文件数量
     */
    private int countFilesInContent(String fileContentPart) {
        if (fileContentPart == null || fileContentPart.trim().isEmpty()) {
            return 0;
        }

        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\"[^\"]+\":\"[^\"]*(?:\\\\.[^\"]*)*\"}");
            java.util.regex.Matcher matcher = pattern.matcher(fileContentPart);

            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;

        } catch (Exception e) {
            log.debug("统计文件数量失败", e);
            return 0;
        }
    }

    /**
     * 计算文本的token数量
     * 使用更准确的算法，区分中英文字符
     *
     * @param content 文本内容
     * @return token数量
     */
    private int calculateTokenCount(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int englishCount = 0;
        int digitCount = 0;
        int punctuationCount = 0;
        int whitespaceCount = 0;

        for (char c : content.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) {
                // 中文字符（包括中文标点）
                chineseCount++;
            } else if (Character.isLetter(c)) {
                // 英文字母
                englishCount++;
            } else if (Character.isDigit(c)) {
                // 数字
                digitCount++;
            } else if (Character.isWhitespace(c)) {
                // 空白字符
                whitespaceCount++;
            } else {
                // 标点符号和其他字符
                punctuationCount++;
            }
        }

        // Token计算规则：
        // - 中文字符：每个字符约1.2个token
        // - 英文字母：每4个字符约1个token
        // - 数字：每4个字符约1个token
        // - 标点符号：每3个字符约1个token
        // - 空白字符：每8个字符约1个token
        int totalTokens = (int) Math.ceil(
            chineseCount * 1.2 +
            englishCount / 4.0 +
            digitCount / 4.0 +
            punctuationCount / 3.0 +
            whitespaceCount / 8.0
        );

        // 最小值为1，避免返回0
        return Math.max(1, totalTokens);
    }

    /**
     * 提取文件内容中的文件名列表
     */
    private List<String> extractFileNames(String fileContentPart) {
        List<String> fileNames = new ArrayList<>();

        if (fileContentPart == null || fileContentPart.trim().isEmpty()) {
            return fileNames;
        }

        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\":");
            java.util.regex.Matcher matcher = pattern.matcher(fileContentPart);

            while (matcher.find()) {
                String fileName = matcher.group(1);
                if (fileName != null && !fileName.trim().isEmpty()) {
                    fileNames.add(fileName);
                }
            }

        } catch (Exception e) {
            log.debug("提取文件名失败", e);
        }

        return fileNames;
    }

    /**
     * 验证聊天频率限制
     *
     * @param userId 用户ID
     * @return 错误消息，如果验证通过则返回null
     */
    private String validateChatRateLimit(Long userId) {
        try {
            // 检查用户聊天频率限制（每分钟限制）
            if (!chatRateLimitService.checkUserChatRateLimit(userId)) {
                log.warn("用户聊天频率超限，用户ID: {}", userId);
                return "Request failed. Please try again later.";
            }

            // 检查IP聊天频率限制（每分钟限制）
            String clientIp = chatRateLimitService.getClientIpAddress();
            if (!chatRateLimitService.checkIpChatRateLimit(clientIp)) {
                log.warn("IP聊天频率超限，IP: {}, 用户ID: {}", clientIp, userId);
                return "Request failed. Please try again later.";
            }

            // 检查用户每日聊天限制
            if (!chatRateLimitService.checkDailyChatLimit(userId)) {
                log.warn("用户每日聊天次数超限，用户ID: {}", userId);
                return "Request failed. Please try again later.";
            }

            return null; // 验证通过
        } catch (Exception e) {
            log.error("验证聊天频率限制时发生异常，用户ID: {}", userId, e);
            return "Request failed. Please try again later.";
        }
    }

}
