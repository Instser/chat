package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.constants.PromptConstants;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.dto.MessageSaveResult;
import com.instser.chat.dto.NetworkSearchProcessResult;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.entity.WebSearchResult;

import com.instser.chat.monitor.AiCallMonitor;
import com.instser.chat.service.*;
import com.instser.chat.service.cache.RequestValidationCache;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.context.PreparedChatContext;
import com.instser.chat.service.ai.AiChatClient;
import com.instser.chat.service.completion.AiResponseCompletionHandler;
import com.instser.chat.service.prepare.ChatPrepareHandler;
import com.instser.chat.service.prepare.EditResendPrepareHandler;
import com.instser.chat.service.prepare.PreparedTurn;
import com.instser.chat.service.prepare.RetryPrepareHandler;
import com.instser.chat.service.stream.AiStreamProcessor;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import com.instser.chat.service.stream.StreamResponseState;
import com.instser.chat.util.ChatSessionManager;
import com.instser.chat.vo.ChatLimitVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
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
    private final WebSearchContentService webSearchContentService;
    private final AiCallMonitor aiCallMonitor;
    private final ChatTitleService chatTitleService;
    private final NetworkSearchOrchestrator networkSearchOrchestrator;
    private final ContentPolicy contentPolicy;
    private final ChatStreamEventWriter chatStreamEventWriter;
    private final AiStreamProcessor aiStreamProcessor;
    private final ChatContextBuilder chatContextBuilder;
    private final ChatPrepareHandler chatPrepareHandler;
    private final RetryPrepareHandler retryPrepareHandler;
    private final EditResendPrepareHandler editResendPrepareHandler;
    private final AiResponseCompletionHandler aiResponseCompletionHandler;
    private final AiChatClient aiChatClient;

    @Qualifier("continuationExecutor")
    private final Executor continuationExecutor;

    // 可选的请求级缓存组件（仅在启用时注入）
    @Autowired(required = false)
    private RequestValidationCache requestValidationCache;

    @Override
    public ChatResponseDTO processStreamChat(ChatRequestDTO request, Authentication authentication, HttpServletResponse response) {
        // 首先从认证信息中获取真实的用户ID
        Long authenticatedUserId = getCurrentUserId(authentication);
        if (authenticatedUserId == null) {
            LogUtils.logUserWarning(log, null, "聊天开始", "用户未认证，拒绝请求");
            try {
                chatStreamEventWriter.writeErrorResponse(response, "用户未认证");
            } catch (IOException e) {
                LogUtils.logUserError(log, null, "发送错误响应", "发送错误响应失败", e);
            }
            return ChatResponseDTO.failure("用户未认证");
        }

        // 使用认证用户ID覆盖请求中的用户ID，确保安全性
        request.setUserId(authenticatedUserId);

        LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天开始", "模型ID：{}", request.getModelId());

        try {
            // 设置响应头
            chatStreamEventWriter.setStreamResponseHeaders(response);

            // 获取并验证模型配置（需要提前获取以便保存消息）
            AiModel model = validateAndGetModel(request.getModelId(), request.getSessionId(), request.getUserId(), response);
            if (model == null) {
                LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天结束", "结果：模型验证失败");
                return ChatResponseDTO.errorWithContent("Request failed. Please try again later.");
            }

            // 先保存用户消息到数据库（确保即使权限验证失败也能保存对话记录）
            String userMessageId = saveUserMessageEarly(request, model, response);
            if (userMessageId == null) {
                LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天结束", "结果：用户消息保存失败");
                return ChatResponseDTO.failure("保存用户消息失败");
            }

            // 执行前置验证（权限检查等）
            ChatResponseDTO validationResult = performPreValidationWithMessageSaving(request, authentication, response, model, userMessageId);
            if (validationResult != null) {
                LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天结束", "结果：前置验证失败");
                return validationResult;
            }

            // 更新会话更新时间
            chatSessionService.updateSessionUpdateTime(request.getSessionId());

            // 构建AI请求消息（用户消息已保存，直接构建完整上下文）
            PreparedTurn preparedTurn = chatPrepareHandler.prepareContext(request, model, userMessageId);
            if (!preparedTurn.isSuccess()) {
                String errorContent = preparedTurn.getErrorMessage() != null
                        ? preparedTurn.getErrorMessage() : "会话中没有可用的消息";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
                log.info("【聊天结束】用户ID：{}，会话ID：{}，结果：消息构建失败",
                        request.getUserId(), request.getSessionId());
                return ChatResponseDTO.failure(errorContent);
            }

            // 更新request中的messages为从数据库构建的完整上下文
            List<Map<String, String>> messages = preparedTurn.getMessages();
            request.setMessages(messages);
            // 兼容标题/联网落库：仍写入 DTO 运行时字段，构建逻辑已迁出
            request.setOriginalMessages(preparedTurn.getOriginalMessages());
            request.setNetworkSearchResults(preparedTurn.getNetworkSearchResults());

            log.info("【聊天开始】用户ID：{}，会话ID：{}，模型：{}，消息数：{}",
                    request.getUserId(), request.getSessionId(), model.getModelCode(), messages.size());

            // 执行AI流式响应
            ChatResponseDTO result = executeAiStreamResponse(request, model, response);

            // 注释掉原有的保存逻辑，现在在handleStreamCompletionWithInterruptStatus中提前保存
            // handleAiResponseProcessing(result, request, model);

            log.info("【聊天结束】用户ID：{}，会话ID：{}，结果：{}，内容长度：{}",
                    request.getUserId(), request.getSessionId(),
                    result.isSuccess() ? "成功" : (result.isInterrupted() ? "中断" : "失败"),
                    result.getAiResponseContent() != null ? result.getAiResponseContent().length() : 0);

            return result;

        } catch (Exception e) {
            log.error("【聊天异常】用户ID：{}，会话ID：{}，错误：{}",
                    request.getUserId(), request.getSessionId(), e.getMessage(), e);
            return handleStreamChatException(e, request, response);
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

        // 验证认证用户ID与请求用户ID是否一致（额外安全检查）
        Long authenticatedUserId = getCurrentUserId(authentication);
        if (authenticatedUserId == null) {
            return "无法获取认证用户信息";
        }

        if (!authenticatedUserId.equals(userId)) {
            log.warn("用户ID不匹配，认证用户ID：{}，请求用户ID：{}", authenticatedUserId, userId);
            return "用户权限验证失败";
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
        log.debug("【消息保存】开始保存用户消息，用户ID：{}，会话ID：{}，模型ID：{}", userId, sessionId, modelId);

        try {
            if (messages == null || messages.isEmpty() || sessionId == null || userId == null) {
                log.warn("【消息保存】参数验证失败，用户ID：{}，会话ID：{}，消息数：{}",
                        userId, sessionId, messages != null ? messages.size() : 0);
                return null;
            }

            // 提取最后一条用户消息内容
            String lastUserMessage = extractLatestUserMessage(messages);
            if (lastUserMessage != null && !lastUserMessage.trim().isEmpty()) {
                Long messageDbId = chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId, modelId);
                if (messageDbId != null) {
                    // 获取刚创建的消息以获取messageId
                    ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                    if (savedMessage != null) {
                        log.info("【消息保存】用户消息保存成功，用户ID：{}，会话ID：{}，消息ID：{}，内容长度：{}",
                                userId, sessionId, savedMessage.getMessageId(), lastUserMessage.length());
                        log.debug("【消息保存】详细信息 - 模型ID：{}，内容预览：{}",
                                modelId, lastUserMessage.length() > 50 ? lastUserMessage.substring(0, 50) + "..." : lastUserMessage);
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
     * 执行前置验证（请求参数、用户权限、频率限制、活跃会话检查）
     *
     * @param request 聊天请求
     * @param authentication 用户认证信息
     * @param response HTTP响应
     * @return 如果验证失败返回错误响应，验证通过返回null
     */
    private ChatResponseDTO performPreValidation(ChatRequestDTO request, Authentication authentication, HttpServletResponse response) {
        try {
            // 验证请求参数
            String validationError = validateChatRequest(request);
            if (validationError != null) {
                chatStreamEventWriter.writeErrorResponse(response, validationError);
                return ChatResponseDTO.failure(validationError);
            }

            // 验证用户权限
            String permissionError = validateUserPermission(authentication, request.getUserId());
            if (permissionError != null) {
                chatStreamEventWriter.writeErrorResponse(response, permissionError);
                return ChatResponseDTO.failure(permissionError);
            }

            // 验证用户聊天权限（套餐、次数等）
            String chatPermissionError = validateChatPermission(request.getUserId(), request.getSessionId(), request.getModelId());
            if (chatPermissionError != null) {
                try {
                    if ("PACKAGE_EXPIRED".equals(chatPermissionError)) {
                        chatStreamEventWriter.writeCustomErrorResponse(response, "Your package has expired.", 0);
                    } else if ("MODEL_LIMIT_EXCEEDED".equals(chatPermissionError)) {
                        chatStreamEventWriter.writeCustomErrorResponse(response, "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.", 0);
                    } else {
                        chatStreamEventWriter.writeErrorResponse(response, "Request failed. Please try again later.");
                    }
                    return ChatResponseDTO.failure(chatPermissionError);
                } catch (IOException e) {
                    log.error("发送权限验证错误响应失败", e);
                    return ChatResponseDTO.failure("System error");
                }
            }

            // 检查聊天频率限制
            ChatResponseDTO rateLimitResult = validateChatRateLimit(request.getUserId(), request.getSessionId(), request.getModelId(), response);
            if (rateLimitResult != null) {
                return rateLimitResult;
            }

            // 检查用户是否已有活跃会话（每个用户只能有一个活跃对话）
            if (chatInterruptService.hasActiveSession(request.getUserId())) {
                String errorMessage = "Currently chatting, please end the chat and try again";
                chatStreamEventWriter.writeErrorResponse(response, errorMessage);
                return ChatResponseDTO.failure(errorMessage);
            }

            return null; // 验证通过
        } catch (Exception e) {
            log.error("前置验证异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
                return ChatResponseDTO.errorWithContent(errorContent);
            } catch (IOException ioException) {
                log.error("发送错误响应失败", ioException);
                return ChatResponseDTO.failure("System error");
            }
        }
    }

    /**
     * 验证并获取AI模型配置
     *
     * @param modelId 模型ID
     * @param sessionId 会话ID（用于保存错误响应）
     * @param userId 用户ID（用于保存错误响应）
     * @param response HTTP响应
     * @return 如果验证通过返回模型对象，否则返回null
     */
    private AiModel validateAndGetModel(Long modelId, String sessionId, Long userId, HttpServletResponse response) {
        try {
            AiModel model = aiModelService.getById(modelId);
            if (model == null || model.getIsAvailable() != 1) {
                String errorContent = "Request failed. Please try again later.";

                // 尝试获取模型信息，如果模型不存在则使用默认模型ID 1
                Long modelIdForSave = (model != null) ? model.getId() : 1L;
                handleErrorResponse(response, errorContent, sessionId, userId, modelIdForSave);
                log.info("AI模型不可用错误响应已保存到数据库，会话ID：{}，用户ID：{}", sessionId, userId);

                return null;
            }
            return model;
        } catch (Exception e) {
            log.error("验证AI模型配置异常", e);
            String errorContent = "Request failed. Please try again later.";
            handleErrorResponse(response, errorContent, sessionId, userId, 1L);
            return null;
        }
    }

    /**
     * 早期保存用户消息到数据库
     * 在权限验证之前就保存用户消息，确保完整的对话记录
     * 
     * @param request 聊天请求
     * @param model AI模型信息
     * @param response HTTP响应
     * @return 用户消息ID，如果保存失败返回null
     */
    private String saveUserMessageEarly(ChatRequestDTO request, AiModel model, HttpServletResponse response) {
        try {
            // 如果没有sessionId，创建新会话
            if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
                String newSessionId = chatSessionService.createSession(request.getUserId(), model.getId());
                request.setSessionId(newSessionId);
                log.info("为用户{}创建新会话，会话ID：{}", request.getUserId(), newSessionId);
            }

            // 从前端messages参数中提取最新用户消息
            String newUserMessageContent = extractLatestUserMessage(request.getMessages());
            if (newUserMessageContent == null || newUserMessageContent.trim().isEmpty()) {
                String errorContent = "用户消息内容不能为空";
                try {
                    chatStreamEventWriter.writeErrorResponse(response, errorContent);
                } catch (IOException e) {
                    log.error("发送错误响应失败", e);
                }
                return null;
            }

            // 检查是否有文件附件
            boolean hasAttachments = request.getFilesId() != null && !request.getFilesId().trim().isEmpty();

            // 保存新的用户消息到数据库（不包含文件内容）
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

            if (userMessageId != null) {
                // 立即更新文件关联信息（避免权限验证失败时文件关联丢失）
                if (request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                    updateFilesAssociationIfNeeded(request.getFilesId(), userMessageId, request.getSessionId(), request.getUserId());
                    log.info("用户{}文件关联已更新，消息ID：{}", request.getUserId(), userMessageId);
                }
            }

            return userMessageId;

        } catch (Exception e) {
            log.error("早期保存用户消息失败，用户ID：{}，会话ID：{}", request.getUserId(), request.getSessionId(), e);
            try {
                chatStreamEventWriter.writeErrorResponse(response, "保存用户消息失败");
            } catch (IOException ioException) {
                log.error("发送错误响应失败", ioException);
            }
            return null;
        }
    }

    /**
     * 执行前置验证并在权限失败时保存AI限制回复
     * 
     * @param request 聊天请求
     * @param authentication 认证信息
     * @param response HTTP响应
     * @param model AI模型信息
     * @param userMessageId 已保存的用户消息ID
     * @return 如果验证失败返回错误响应，验证通过返回null
     */
    private ChatResponseDTO performPreValidationWithMessageSaving(ChatRequestDTO request, Authentication authentication, 
                                                                 HttpServletResponse response, AiModel model, String userMessageId) {
        try {
            // 验证请求参数
            String validationError = validateChatRequest(request);
            if (validationError != null) {
                return saveErrorResponseAndReturn(response, validationError, request, model);
            }

            // 验证用户权限
            String permissionError = validateUserPermission(authentication, request.getUserId());
            if (permissionError != null) {
                return saveErrorResponseAndReturn(response, permissionError, request, model);
            }

            // 验证用户聊天权限（套餐、次数等）- 使用优化版本（如果缓存可用）
            String chatPermissionError;
            if (requestValidationCache != null) {
                // 使用优化版本，带缓存
                chatPermissionError = validateChatPermissionOptimized(request.getUserId(), request.getSessionId(), request.getModelId());
            } else {
                // 使用原有版本，无缓存
                chatPermissionError = validateChatPermission(request.getUserId(), request.getSessionId(), request.getModelId());
            }
            if (chatPermissionError != null) {
                String customErrorMessage;
                if ("PACKAGE_EXPIRED".equals(chatPermissionError)) {
                    customErrorMessage = "Your package has expired.";
                } else if ("MODEL_LIMIT_EXCEEDED".equals(chatPermissionError)) {
                    customErrorMessage = "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.";
                } else {
                    customErrorMessage = "Request failed. Please try again later.";
                }
                return saveErrorResponseAndReturn(response, customErrorMessage, request, model);
            }

            // 检查聊天频率限制
            ChatResponseDTO rateLimitResult = validateChatRateLimitWithSaving(request.getUserId(), request.getSessionId(), 
                                                                             request.getModelId(), response, request, model);
            if (rateLimitResult != null) {
                return rateLimitResult;
            }

            // 检查用户是否已有活跃会话（每个用户只能有一个活跃对话）
            if (chatInterruptService.hasActiveSession(request.getUserId())) {
                String errorMessage = "Currently chatting, please end the chat and try again";
                return saveErrorResponseAndReturn(response, errorMessage, request, model);
            }

            return null; // 验证通过
        } catch (Exception e) {
            log.error("前置验证异常", e);
            
            // 检查异常是否在权限验证阶段发生，如果是则尝试进行错误码转换
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("PACKAGE_EXPIRED") || exceptionMessage.contains("套餐已过期")) {
                    return saveErrorResponseAndReturn(response, "Your package has expired.", request, model);
                } else if (exceptionMessage.contains("MODEL_LIMIT_EXCEEDED") || exceptionMessage.contains("模型使用次数已达上限")) {
                    return saveErrorResponseAndReturn(response, "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.", request, model);
                }
            }
            
            String errorContent = "Request failed. Please try again later.";
            return saveErrorResponseAndReturn(response, errorContent, request, model);
        }
    }

    /**
     * 保存错误响应到数据库并返回失败结果
     * 
     * @param response HTTP响应
     * @param errorMessage 错误消息
     * @param request 聊天请求
     * @param model AI模型信息
     * @return ChatResponseDTO失败结果
     */
    private ChatResponseDTO saveErrorResponseAndReturn(HttpServletResponse response, String errorMessage, 
                                                      ChatRequestDTO request, AiModel model) {
        try {
            // 发送流式错误响应给用户
            chatStreamEventWriter.writeErrorResponse(response, errorMessage);
            
            // 保存错误响应到数据库，使用status=3避免进入后续聊天上下文
            saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorMessage);
            
            return ChatResponseDTO.failure(errorMessage);
        } catch (IOException e) {
            log.error("发送错误响应失败，用户ID：{}，会话ID：{}", request.getUserId(), request.getSessionId(), e);
            
            // 即使发送响应失败，也要保存到数据库
            saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorMessage);
            
            return ChatResponseDTO.failure("System error");
        }
    }

    /**
     * 验证聊天频率限制并在失败时保存响应
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param modelId 模型ID
     * @param response HTTP响应
     * @param request 聊天请求
     * @param model AI模型信息
     * @return 如果验证失败返回错误响应，验证通过返回null
     */
    private ChatResponseDTO validateChatRateLimitWithSaving(Long userId, String sessionId, Long modelId, 
                                                           HttpServletResponse response, ChatRequestDTO request, AiModel model) {
        try {
            // 检查用户聊天频率限制（每分钟限制）
            if (!chatRateLimitService.checkUserChatRateLimit(userId)) {
                log.warn("用户聊天频率超限，用户ID: {}", userId);
                String errorContent = "The request frequency is too high. Please try again later.";
                return saveErrorResponseAndReturn(response, errorContent, request, model);
            }

            // 检查IP聊天频率限制（每分钟限制）
            String clientIp = chatRateLimitService.getClientIpAddress();
            if (!chatRateLimitService.checkIpChatRateLimit(clientIp)) {
                log.warn("IP聊天频率超限，IP: {}, 用户ID: {}", clientIp, userId);
                String errorContent = "The request frequency is too high. Please try again later.";
                return saveErrorResponseAndReturn(response, errorContent, request, model);
            }

            // 检查用户每日聊天限制
            if (!chatRateLimitService.checkDailyChatLimit(userId)) {
                log.warn("用户每日聊天次数超限，用户ID: {}", userId);
                String errorContent = "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.";
                return saveErrorResponseAndReturn(response, errorContent, request, model);
            }

            return null; // 验证通过
        } catch (Exception e) {
            log.error("验证聊天频率限制时发生异常，用户ID: {}", userId, e);
            String errorContent = "Request failed. Please try again later.";
            return saveErrorResponseAndReturn(response, errorContent, request, model);
        }
    }

    /**
     * 验证用户聊天权限和套餐限制（优化版本，使用缓存）
     *
     * @param userId 用户ID
     * @param sessionId 会话ID（用于日志）
     * @param modelId 模型ID（用于模型特定权限检查）
     * @return 如果验证失败返回错误原因，验证通过返回null
     */
    private String validateChatPermissionOptimized(Long userId, String sessionId, Long modelId) {
        log.debug("【权限验证-优化】开始验证聊天权限，用户ID：{}，会话ID：{}，模型ID：{}", userId, sessionId, modelId);

        try {
            // 基础权限检查（套餐、过期等）- 使用缓存避免重复查询
            ChatLimitVO limitCheck;
            if (requestValidationCache != null) {
                String cacheKey = "chat_permission:" + userId;
                limitCheck = requestValidationCache.getOrCompute(cacheKey, 
                    () -> chatLimitService.validateChatPermission(userId));
            } else {
                // 缓存未启用时，使用原有逻辑
                limitCheck = chatLimitService.validateChatPermission(userId);
            }
            
            if (!limitCheck.isCanChat()) {
                String reason = limitCheck.getLimitReason();
                log.info("【权限验证-优化】基础权限验证失败，用户ID：{}，会话ID：{}，原因：{}", userId, sessionId, reason);
                return reason; // 返回失败原因
            }

            // 模型权限检查
            if (modelId != null) {
                // 获取模型信息 - 使用缓存
                AiModel model;
                if (requestValidationCache != null) {
                    String modelCacheKey = "ai_model:" + modelId;
                    model = requestValidationCache.getOrCompute(modelCacheKey, 
                        () -> aiModelService.getById(modelId));
                } else {
                    model = aiModelService.getById(modelId);
                }
                
                if (model != null) {
                    // 模型权限验证 - 使用缓存
                    ChatLimitVO modelLimitCheck;
                    if (requestValidationCache != null) {
                        String modelPermissionCacheKey = "model_permission:" + userId + ":" + modelId;
                        modelLimitCheck = requestValidationCache.getOrCompute(modelPermissionCacheKey,
                            () -> chatLimitService.validateModelChatPermission(userId, modelId.toString()));
                    } else {
                        modelLimitCheck = chatLimitService.validateModelChatPermission(userId, modelId.toString());
                    }
                    
                    if (!modelLimitCheck.isCanChat()) {
                        String reason = modelLimitCheck.getLimitReason();
                        log.info("【权限验证-优化】模型权限验证失败，用户ID：{}，会话ID：{}，模型ID：{}，模型代码：{}，原因：{}", 
                                userId, sessionId, modelId, model.getModelCode(), reason);
                        // 检查是否是模型限制超限错误
                        if ("MODEL_LIMIT_EXCEEDED".equals(reason)) {
                            return "MODEL_LIMIT_EXCEEDED";
                        }
                        return reason; // 返回失败原因
                    }
                }
            }

            log.info("【权限验证-优化】验证通过，用户ID：{}，套餐：{}，限制类型：{}",
                    userId, limitCheck.getPackageName(), limitCheck.getLimitType());
            log.debug("【权限验证-优化】详细信息 - 会话ID：{}，剩余次数：{}，每日限制：{}",
                    sessionId, limitCheck.getRemainingToday(), limitCheck.getDailyLimit());

            return null; // 验证通过
        } catch (Exception e) {
            log.error("【权限验证-优化】验证异常，用户ID：{}，会话ID：{}", userId, sessionId, e);
            
            // 检查异常消息中是否包含模型限制相关的关键词
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("模型使用次数已达上限") || 
                    exceptionMessage.contains("模型使用超限") ||
                    exceptionMessage.contains("每日聊天次数已达到套餐限制") ||
                    exceptionMessage.contains("MODEL_LIMIT_EXCEEDED") ||
                    exceptionMessage.contains("使用限制")) {
                    return "MODEL_LIMIT_EXCEEDED";
                } else if (exceptionMessage.contains("PACKAGE_EXPIRED") || exceptionMessage.contains("套餐已过期")) {
                    return "PACKAGE_EXPIRED";
                }
            }
            
            return "Request failed. Please try again later."; // 返回通用错误
        }
    }

    /**
     * 验证用户聊天权限和套餐限制
     *
     * @param userId 用户ID
     * @param sessionId 会话ID（用于日志）
     * @param modelId 模型ID（用于模型特定权限检查）
     * @return 如果验证失败返回错误原因，验证通过返回null
     */
    private String validateChatPermission(Long userId, String sessionId, Long modelId) {
        log.debug("【权限验证】开始验证聊天权限，用户ID：{}，会话ID：{}，模型ID：{}", userId, sessionId, modelId);

        try {
            // 基础权限检查（套餐、过期等）
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.isCanChat()) {
                String reason = limitCheck.getLimitReason();
                log.info("【权限验证】基础权限验证失败，用户ID：{}，会话ID：{}，原因：{}", userId, sessionId, reason);
                return reason; // 返回失败原因
            }

            // 模型权限检查
            if (modelId != null) {
                // 获取模型信息
                AiModel model = aiModelService.getById(modelId);
                if (model != null) {
                    ChatLimitVO modelLimitCheck = chatLimitService.validateModelChatPermission(userId, modelId.toString());
                    if (!modelLimitCheck.isCanChat()) {
                        String reason = modelLimitCheck.getLimitReason();
                        log.info("【权限验证】模型权限验证失败，用户ID：{}，会话ID：{}，模型ID：{}，模型代码：{}，原因：{}", 
                                userId, sessionId, modelId, model.getModelCode(), reason);
                        // 检查是否是模型限制超限错误
                        if ("MODEL_LIMIT_EXCEEDED".equals(reason)) {
                            return "MODEL_LIMIT_EXCEEDED";
                        }
                        return reason; // 返回失败原因
                    }
                }
            }

            log.info("【权限验证】验证通过，用户ID：{}，套餐：{}，限制类型：{}",
                    userId, limitCheck.getPackageName(), limitCheck.getLimitType());
            log.debug("【权限验证】详细信息 - 会话ID：{}，剩余次数：{}，每日限制：{}",
                    sessionId, limitCheck.getRemainingToday(), limitCheck.getDailyLimit());

            return null; // 验证通过
        } catch (Exception e) {
            log.error("【权限验证】验证异常，用户ID：{}，会话ID：{}", userId, sessionId, e);
            
            // 检查异常消息中是否包含模型限制相关的关键词
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("模型使用次数已达上限") || 
                    exceptionMessage.contains("模型使用超限") ||
                    exceptionMessage.contains("每日聊天次数已达到套餐限制") ||
                    exceptionMessage.contains("MODEL_LIMIT_EXCEEDED") ||
                    exceptionMessage.contains("使用限制")) {
                    return "MODEL_LIMIT_EXCEEDED";
                } else if (exceptionMessage.contains("PACKAGE_EXPIRED") || exceptionMessage.contains("套餐已过期")) {
                    return "PACKAGE_EXPIRED";
                }
            }
            
            return "Request failed. Please try again later."; // 返回通用错误
        }
    }

    /**
     * 从前端messages参数中提取最新用户消息内容
     *
     * @param messages 前端传递的消息列表
     * @return 最新用户消息内容，如果没有找到则返回null
     */
    private String extractLatestUserMessage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        // 从消息列表中找到最后一条用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> message = messages.get(i);
            if ("user".equals(message.get("role"))) {
                return message.get("content");
            }
        }
        return null;
    }



    /**
     * 准备会话和消息（创建会话、保存用户消息、更新文件关联）
     *
     * @param request 聊天请求
     * @param model AI模型
     * @param response HTTP响应
     * @return 如果处理失败返回错误响应，处理成功返回null
     */
    private ChatResponseDTO prepareSessionAndMessage(ChatRequestDTO request, AiModel model, HttpServletResponse response) {
        try {
            // 如果没有sessionId，创建新会话
            if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
                String newSessionId = chatSessionService.createSession(request.getUserId(), model.getId());
                request.setSessionId(newSessionId);
                log.info("为用户{}创建新会话，会话ID：{}", request.getUserId(), newSessionId);
            }

            // 从前端messages参数中提取最新用户消息
            String newUserMessageContent = extractLatestUserMessage(request.getMessages());
            if (newUserMessageContent == null || newUserMessageContent.trim().isEmpty()) {
                String errorContent = "用户消息内容不能为空";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
                return ChatResponseDTO.failure(errorContent);
            }

            // 检查是否有文件附件
            boolean hasAttachments = request.getFilesId() != null && !request.getFilesId().trim().isEmpty();

            // 保存新的用户消息到数据库（不包含文件内容）
            Long messageDbId = chatMessageService.sendMessage(request.getSessionId(), "user", newUserMessageContent, request.getUserId(), model.getId(), hasAttachments ? 1 : 0);
            String userMessageId = null;
            if (messageDbId != null) {
                ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                if (savedMessage != null) {
                    userMessageId = savedMessage.getMessageId();
                    log.debug("用户消息已保存到数据库，会话ID：{}，用户ID：{}，消息ID：{}，模型ID：{}，附件：{}，内容长度：{}",
                            request.getSessionId(), request.getUserId(), userMessageId, model.getId(), hasAttachments ? "有" : "无", newUserMessageContent.length());
                }
            }

            if (userMessageId == null) {
                String errorContent = "保存用户消息失败";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
                return ChatResponseDTO.failure(errorContent);
            }

            // 立即更新文件关联信息（避免AI错误时文件关联丢失）
            if (request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                updateFilesAssociationIfNeeded(request.getFilesId(), userMessageId, request.getSessionId(), request.getUserId());
                log.info("用户{}文件关联已更新，消息ID：{}", request.getUserId(), userMessageId);
            }

            // 注意：联网查询结果的保存已移到buildAiRequestMessages方法之后进行
            // 因为需要先执行联网搜索才能获取到搜索结果

            return null; // 处理成功
        } catch (Exception e) {
            log.error("准备会话和消息异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
                return ChatResponseDTO.errorWithContent(errorContent);
            } catch (IOException ioException) {
                log.error("发送错误响应失败", ioException);
                return ChatResponseDTO.failure("System error");
            }
        }
    }

        private List<Map<String, String>> buildAiRequestMessages(ChatRequestDTO request) {
        PreparedChatContext prepared = chatContextBuilder.buildFromSessionHistory(request);
        if (prepared == null) {
            return null;
        }
        request.setOriginalMessages(prepared.getOriginalMessages());
        request.setNetworkSearchResults(prepared.getNetworkSearchResults());
        return prepared.getAiMessages();
    }

    /**
     * 执行AI流式响应（带重试机制）
     *
     * @param request 聊天请求
     * @param model AI模型
     * @param response HTTP响应
     * @return AI响应结果
     */
    private ChatResponseDTO executeAiStreamResponse(ChatRequestDTO request, AiModel model, HttpServletResponse response) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                // 判断是否会生成标题
                boolean willGenerateTitle = request.getSessionId() != null && isFirstAiResponse(request.getMessages());

                // 调用AI服务进行流式响应
                return streamChatWithAI(request, model, response, willGenerateTitle, "chat");
            } catch (ContentPolicy.ChatRetryException e) {
                retryCount++;
                log.warn("检测到需要重试的错误消息，第{}次重试，最大重试次数: {}", retryCount, maxRetries);

                if (retryCount > maxRetries) {
                    log.error("重试次数已达上限，停止重试");
                    try {
                        String errorContent = "Request failed. Please try again later.";
                        chatStreamEventWriter.writeErrorResponse(response, errorContent);
                        return ChatResponseDTO.errorWithContent(errorContent);
                    } catch (IOException ioException) {
                        log.error("发送错误响应失败", ioException);
                        return ChatResponseDTO.failure("System error");
                    }
                }

                // 等待一段时间后重试
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("重试等待被中断");
                    break;
                }
            } catch (Exception e) {
                log.error("执行AI流式响应异常", e);
                try {
                    String errorContent = "Request failed. Please try again later.";
                    chatStreamEventWriter.writeErrorResponse(response, errorContent);
                    return ChatResponseDTO.errorWithContent(errorContent);
                } catch (IOException ioException) {
                    log.error("发送错误响应失败", ioException);
                    return ChatResponseDTO.failure("System error");
                }
            }
        }

        // 如果到这里说明重试失败
        try {
            String errorContent = "Request failed. Please try again later.";
            chatStreamEventWriter.writeErrorResponse(response, errorContent);
            return ChatResponseDTO.errorWithContent(errorContent);
        } catch (IOException ioException) {
            log.error("发送错误响应失败", ioException);
            return ChatResponseDTO.failure("System error");
        }
    }



    /**
     * 处理流式聊天异常
     *
     * @param e 异常
     * @param request 聊天请求
     * @param response HTTP响应
     * @return 错误响应
     */
    private ChatResponseDTO handleStreamChatException(Exception e, ChatRequestDTO request, HttpServletResponse response) {
        log.error("处理流式聊天请求异常", e);
        try {
            String errorContent = "Request failed. Please try again later.";
            chatStreamEventWriter.writeErrorResponse(response, errorContent);

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
        // 调用新方法保存AI响应，但不需要返回messageId
        // 这个方法主要用于错误响应保存，不涉及联网信息
        saveAiResponseAndGetMessageId(sessionId, userId, modelId, content);
    }

    /**
     * 保存AI响应并返回消息ID
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param modelId 模型ID
     * @param content 内容
     * @return AI响应消息ID，失败时返回null
     */
    public String saveAiResponseAndGetMessageId(String sessionId, Long userId, Long modelId, String content) {
        return aiResponseCompletionHandler.persistAssistantContent(sessionId, userId, modelId, content);
    }


    @Override
    public void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent) {
        try {
            if (sessionId != null && userId != null && modelId != null) {
                chatMessageService.sendErrorResponseMessage(sessionId, userId, modelId, errorContent);
                log.info("错误响应已保存到数据库，会话ID：{}，用户ID：{}，模型ID：{}，状态：3，内容：{}", sessionId, userId, modelId, errorContent);
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
     * 更新会话模型（如果需要）
     */
    private void updateSessionModelIfNeeded(String sessionId, Long newModelId, Long currentModelId) {
        if (newModelId != null && !newModelId.equals(currentModelId)) {
            chatSessionService.updateSessionModel(sessionId, newModelId);
            log.info("会话{}的模型已更新为：{}", sessionId, newModelId);
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


        private ChatValidationResult(boolean success, String errorMessage, Long userId,
                                   ChatSession chatSession) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.userId = userId;
            this.chatSession = chatSession;
        }

        public static ChatValidationResult success(Long userId, ChatSession chatSession, ChatMessage targetMessage) {
            return new ChatValidationResult(true, null, userId, chatSession);
        }

        public static ChatValidationResult error(String errorMessage) {
            return new ChatValidationResult(false, errorMessage, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Long getUserId() { return userId; }
        public ChatSession getChatSession() { return chatSession; }

    }

                /**
     * 统一的错误响应处理
     *
     * @param response HTTP响应
     * @param errorMessage 错误消息
     * @param sessionId 会话ID（可选）
     * @param userId 用户ID（可选）
     * @param modelId 模型ID（可选）
     * @return 错误响应DTO
     */
    private ChatResponseDTO handleErrorResponse(HttpServletResponse response, String errorMessage,
                                              String sessionId, Long userId, Long modelId) {
        try {
            chatStreamEventWriter.writeErrorResponse(response, errorMessage);

            // 如果有会话信息，保存错误响应到数据库
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

    /**
     * 调用AI服务进行流式响应
     */
    private ChatResponseDTO streamChatWithAI(ChatRequestDTO request, AiModel model, HttpServletResponse response, boolean willGenerateTitle, String interfaceType) {
        return aiStreamProcessor.stream(
                request,
                model,
                response,
                willGenerateTitle,
                interfaceType,
                this::handleStreamCompletionWithInterruptStatus,
                this::saveErrorResponse,
                (requestBody, m) -> {
                    logAiRequestDetails(requestBody, m, request.getUserId());
                    return null;
                }
        );
    }

    private boolean handleStreamCompletionWithInterruptStatus(HttpServletResponse response, boolean willGenerateTitle, boolean isRealAiResponse,
                                      ChatRequestDTO request, StringBuilder aiResponseContentRef, AiModel model, String interfaceType, boolean wasManuallyInterrupted) {
        // 使用局部变量跟踪最终的中断状态（包括主流程中断和续写中断）
        boolean finalInterruptedStatus = wasManuallyInterrupted;

        log.debug("handleStreamCompletionWithInterruptStatus 开始 - wasManuallyInterrupted: {}, finalInterruptedStatus: {}",
                 wasManuallyInterrupted, finalInterruptedStatus);

        try {
            String currentContent = aiResponseContentRef.toString();

            // 检查是否应用截断检测
            boolean shouldApply = truncationDetectionService.shouldApplyTruncationDetection(interfaceType);

            if (wasManuallyInterrupted) {
                log.info("检测到用户手动中断聊天，跳过截断检测，会话ID：{}", request.getSessionId());
            }

            if (shouldApply && isRealAiResponse && currentContent != null && !currentContent.trim().isEmpty() && !wasManuallyInterrupted) {
                // 计算Token数量
                int tokenCount = calculateTokenCount(currentContent);

                // 进行截断检测（带Token限制）
                log.debug("开始截断检测，会话ID：{}，内容长度：{}，Token数量：{}",
                        request.getSessionId(), currentContent.length(), tokenCount);

                boolean isComplete = truncationDetectionService.isContentComplete(currentContent, tokenCount);

                if (!isComplete) {
                    log.info("检测到内容被截断，开始续写流程，会话ID：{}", request.getSessionId());

                    // 执行多次续写（最多2次）
                    String finalContent = performMultipleContinuations(response, request, model, currentContent, aiResponseContentRef);

                    // 检查续写结果
                    if (finalContent != null) {
                        if (finalContent.startsWith("CONTINUATION_INTERRUPTED:")) {
                            String actualContent = finalContent.substring("CONTINUATION_INTERRUPTED:".length());
                            log.info("续写被中断，会话ID：{}，部分续写内容长度：{}",
                                    request.getSessionId(), actualContent.length());

                            // 设置最终中断状态为true（续写被中断）
                            finalInterruptedStatus = true;
                            log.info("设置 finalInterruptedStatus = true (续写中断)");
                        } else {
                            log.info("续写流程完成，会话ID：{}，最终内容长度：{}",
                                    request.getSessionId(), aiResponseContentRef.length());
                        }
                    } else {
                        log.warn("续写操作失败或无内容生成，继续正常流程，会话ID：{}", request.getSessionId());
                    }
                }
            }

            // ticket 06：续写完成后的副作用统一走 completeSideEffects
            String contentToSave = aiResponseContentRef.toString();
            boolean shouldPersist = isRealAiResponse || finalInterruptedStatus;
            if (shouldPersist && contentToSave != null && !contentToSave.trim().isEmpty()) {
                aiResponseCompletionHandler.completeSideEffects(
                        response,
                        request,
                        model,
                        willGenerateTitle,
                        isRealAiResponse,
                        contentToSave,
                        finalInterruptedStatus,
                        true
                );
            } else {
                aiResponseCompletionHandler.writeCompletion(response, willGenerateTitle, isRealAiResponse, null);
            }

            // 返回最终中断状态：包括用户手动中断和续写中断
            log.debug("handleStreamCompletionWithInterruptStatus 返回 finalInterruptedStatus: {}", finalInterruptedStatus);
            return finalInterruptedStatus;

        } catch (Exception e) {
            log.error("处理完成标记时发生异常", e);
            try {
                log.info("处理完成标记异常，跳过联网搜索结果发送");
                aiResponseCompletionHandler.writeCompletion(response, false, false, null);
            } catch (Exception ex) {
                log.error("发送异常完成标记失败", ex);
            }
            return finalInterruptedStatus;
        } finally {
            // 会话状态清理现在由ChatSessionManager自动处理
            // 这里不再手动清理，避免重复清理
            log.debug("续写完成，会话状态将由ChatSessionManager自动清理，会话ID：{}", request.getSessionId());
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
                                         int[] cacheBlockCount, boolean[] cleaningDone) throws ContentPolicy.ChatRetryException {
        // 简化续写JSON处理日志

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonData);

            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");

                if (delta != null && delta.has("content")) {
                    // 跳过 JSON null content（推理模型思考阶段发 content:null + reasoning_content:文本），
                    // Jackson asText() 对 JSON null 返回字符串 "null"（非空），会被误当内容发给前端 → nullnull... 串
                    JsonNode contentNode = delta.get("content");
                    if (contentNode != null && contentNode.isTextual()) {
                        String content = contentNode.asText();
                        if (content != null && !content.isEmpty()) {
                            // 移除详细的续写内容日志

                            // 如果还没有开始缓存，初始化缓存
                            if (cacheStartTime[0] == 0) {
                                cacheStartTime[0] = System.currentTimeMillis();
                                log.info("续写缓存开始，时间: {}", cacheStartTime[0]);
                            }

                            // 如果清理还没完成，继续缓存
                            if (!cleaningDone[0]) {
                                cacheBuffer.append(content);
                                cacheBlockCount[0]++;
                                // 简化续写缓存日志
                                log.debug("续写缓存中，块数: {}, 缓存长度: {}", cacheBlockCount[0], cacheBuffer.length());

                                // 检查是否应该停止缓存并进行清理
                                if (shouldStopCaching(cacheBuffer, cacheStartTime[0], cacheBlockCount[0])) {
                                    // 一次性清理所有标签
                                    String cachedContent = cacheBuffer.toString();
                                    try {
                                        String cleanedCache = cleanAllTagsOnce(cachedContent);
                                        log.debug("续写缓存清理完成，原长度: {}, 清理后长度: {}, 缓存时间: {}ms",
                                                cachedContent.length(), cleanedCache.length(),
                                                System.currentTimeMillis() - cacheStartTime[0]);

                                        cleaningDone[0] = true;
                                    cacheBuffer.setLength(0); // 清空缓存

                                        // 处理清理后的内容
                                        if (!cleanedCache.isEmpty()) {
                                            return processCachedContent(cleanedCache, originalContent, continuationContent,
                                                                      alignedContentForSaving, alignmentDone, response, mapper);
                                        } else {
                                            // 如果清理后内容为空（被过滤），不进行任何处理
                                            log.debug("续写缓存内容被过滤，跳过处理");
                                            return null;
                                        }
                                    } catch (ContentPolicy.ChatRetryException e) {
                                        log.warn("续写缓存清理时检测到需要重试的错误消息，抛出重试异常");
                                        throw e; // 重新抛出异常，让上层处理重试
                                    }
                                }
                                return null; // 继续缓存
                            } else {
                                // 清理已完成，后续内容需要进行过滤检测

                                if (!content.isEmpty()) {
                                    // 检查后续内容是否包含需要重试的错误消息
                                    if (contentPolicy.shouldRestartChat(content)) {
                                        log.warn("续写后续内容检测到需要重试的错误消息，抛出重试异常");
                                        throw new ContentPolicy.ChatRetryException("续写后续内容检测到需要重试的错误消息");
                                    }

                                    // 检查后续内容是否包含需要过滤的内容
                                    if (contentPolicy.shouldFilterContent(content)) {
                                        log.info("续写后续内容检测到需要过滤的AI响应内容，已阻止向前端发送且不保存到数据库");
                                        // 不累积到内容中，也不发送给前端
                                        return null; // 返回null，不累积任何内容
                                    }

                                    // 对齐完成后，直接添加内容（不再清理）
                                    alignedContentForSaving.append(content);
                                    continuationContent.append(content);

                                    // 发送后续内容给前端
                                    chatStreamEventWriter.writeContent(content, response);
                                    return content;
                                }
                            }
                        } else {
                            log.debug("续写原始content为空或null");
                        }
                    } else {
                        log.debug("续写delta节点content为JSON null（推理阶段），跳过");
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
     * 续写路径一次性清理：先检测重试/过滤，再清洗标签。
     */
    private String cleanAllTagsOnce(String content) throws ContentPolicy.ChatRetryException {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        log.debug("开始一次性清理所有标签，原始长度: {}", content.length());
        if (contentPolicy.shouldRestartChat(content)) {
            log.warn("续写内容检测到需要重试的错误消息，抛出重试异常");
            throw new ContentPolicy.ChatRetryException("续写检测到需要重试的错误消息");
        }
        if (contentPolicy.shouldFilterContent(content)) {
            log.info("续写内容检测到需要过滤的AI响应内容，已阻止向前端发送");
            return "";
        }
        String cleaned = contentPolicy.cleanAllTagsOnce(content);
        log.debug("一次性清理完成，原长度: {}, 清理后长度: {}, 移除字符数: {}",
                content.length(), cleaned == null ? 0 : cleaned.length(),
                content.length() - (cleaned == null ? 0 : cleaned.length()));
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
                log.debug("续写发送对齐后内容给前端，使用智能分块: {}", true);
                chatStreamEventWriter.writeContent(contentToSend, response);
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

            log.debug("续写发送后续内容给前端，使用智能分块: {}", true);
            chatStreamEventWriter.writeContent(cleanedCache, response);
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
                    // 跳过 JSON null content（推理模型思考阶段发 content:null + reasoning_content:文本），
                    // Jackson asText() 对 JSON null 返回字符串 "null"（非空），会被误当内容发给前端 → nullnull... 串
                    JsonNode contentNode = delta.get("content");
                    if (contentNode != null && contentNode.isTextual()) {
                        String content = contentNode.asText();
                        if (content != null && !content.isEmpty()) {
                            ContentPolicy.ContentDetectionResult detectionResult = contentPolicy.detectContentAndGetResult(content);
                            String cleanContent = detectionResult.getCleanedContent();

                            // 如果需要重试，抛出特殊异常
                            if (detectionResult.needsRetry()) {
                                log.warn("检测到需要重试的错误消息，抛出重试异常: [{}]",
                                    content.length() > 50 ? content.substring(0, 50) + "..." : content);
                                throw new ContentPolicy.ChatRetryException("检测到需要重试的错误消息");
                            }

                            // 如果内容被过滤，不返回任何内容（既不发送给前端，也不累积到数据库）
                            if (detectionResult.wasFiltered()) {
                                log.debug("AI响应内容被过滤，不发送给前端且不保存到数据库: [{}]",
                                    content.length() > 50 ? content.substring(0, 50) + "..." : content);
                                return null; // 返回null，不累积到aiResponseContent
                            }

                            if (!cleanContent.isEmpty()) {
                                log.debug("AI响应内容: [{}]", cleanContent.length() > 50 ? cleanContent.substring(0, 50) + "..." : cleanContent);

                                chatStreamEventWriter.writeContent(cleanContent, response);
                                return cleanContent;
                            }
                        }
                    } else {
                        log.debug("完整响应delta节点content为JSON null（推理阶段），跳过");
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
     * 执行多次续写（最多2次）
     * @param response HTTP响应对象
     * @param request 聊天请求
     * @param model AI模型
     * @param originalContent 原始内容
     * @param aiResponseContentRef 用于累积内容的StringBuilder引用
     * @return 最终状态标识，如果被中断返回CONTINUATION_INTERRUPTED:内容，否则返回"SUCCESS"
     */
    private String performMultipleContinuations(HttpServletResponse response, ChatRequestDTO request,
                                              AiModel model, String originalContent, StringBuilder aiResponseContentRef) {
        final int maxContinuations = 2; // 最多续写2次
        String currentContent = originalContent;

        for (int round = 1; round <= maxContinuations; round++) {
            final int continuationRound = round; // 创建final变量供lambda使用
            final String contentForThisRound = currentContent; // 创建final变量供lambda使用

            log.info("开始第{}次续写，会话ID：{}，当前内容长度：{}",
                    continuationRound, request.getSessionId(), contentForThisRound.length());

            // 使用线程池执行续写操作，避免在reactor线程中阻塞
            CompletableFuture<String> continuationFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.debug("第{}次续写任务开始执行，线程：{}, 会话ID：{}",
                            continuationRound, Thread.currentThread().getName(), request.getSessionId());
                    return continueAiResponse(response, request, model, contentForThisRound);
                }, continuationExecutor)
                .exceptionally(throwable -> {
                    log.error("第{}次续写操作异常，线程：{}, 会话ID：{}",
                            continuationRound, Thread.currentThread().getName(), request.getSessionId(), throwable);
                    return null;
                });

            try {
                // 等待续写完成
                String continuationContent = continuationFuture.join();

                if (continuationContent != null && !continuationContent.trim().isEmpty()) {
                    log.debug("第{}次续写返回内容检查 - 内容长度: {}, 是否以CONTINUATION_INTERRUPTED开头: {}",
                             continuationRound, continuationContent.length(),
                             continuationContent.startsWith("CONTINUATION_INTERRUPTED:"));

                    // 检查是否是续写中断标记
                    if (continuationContent.startsWith("CONTINUATION_INTERRUPTED:")) {
                        String actualContent = continuationContent.substring("CONTINUATION_INTERRUPTED:".length());
                        log.info("第{}次续写被中断，会话ID：{}，部分续写内容长度：{}",
                                continuationRound, request.getSessionId(), actualContent.length());

                        // 将部分续写内容拼接到原内容后面
                        aiResponseContentRef.append(actualContent);

                        log.debug("第{}次续写中断内容拼接完成 - 原内容长度: {}, 部分续写长度: {}, 最终长度: {}",
                                continuationRound, contentForThisRound.length(), actualContent.length(), aiResponseContentRef.length());

                        // 续写被中断，直接返回中断标记
                        return "CONTINUATION_INTERRUPTED:" + actualContent;
                    } else {
                        log.info("第{}次续写操作完成，会话ID：{}，续写内容长度：{}",
                                continuationRound, request.getSessionId(), continuationContent.length());

                        // 将续写内容拼接到原内容后面
                        aiResponseContentRef.append(continuationContent);

                        // 更新当前内容为拼接后的完整内容，用于下一轮检测
                        currentContent = aiResponseContentRef.toString();

                        log.debug("第{}次续写内容拼接完成 - 续写长度: {}, 最终长度: {}",
                                continuationRound, continuationContent.length(), aiResponseContentRef.length());

                        // 如果这是最后一次续写，直接返回成功，不再检测截断
                        if (continuationRound >= maxContinuations) {
                            log.info("已完成最大续写次数({})，不再检测截断，会话ID：{}",
                                    maxContinuations, request.getSessionId());
                            return "SUCCESS";
                        }

                        // 检测续写后的内容是否仍然被截断
                        int tokenCount = calculateTokenCount(currentContent);
                        log.info("第{}次续写完成后进行截断检测，内容长度：{}，Token数量：{}",
                                continuationRound, currentContent.length(), tokenCount);

                        boolean isStillTruncated = !truncationDetectionService.isContentComplete(currentContent, tokenCount);

                        if (isStillTruncated) {
                            log.info("第{}次续写后内容仍被截断，准备进行第{}次续写，会话ID：{}",
                                    continuationRound, continuationRound + 1, request.getSessionId());
                            // 继续下一轮续写
                            continue;
                        } else {
                            log.info("第{}次续写后内容已完整，无需继续续写，会话ID：{}",
                                    continuationRound, request.getSessionId());
                            return "SUCCESS";
                        }
                    }
                } else {
                    log.warn("第{}次续写操作失败或无内容生成，会话ID：{}",
                            continuationRound, request.getSessionId());
                    return null;
                }
            } catch (Exception e) {
                log.error("第{}次续写操作异常，会话ID：{}", continuationRound, request.getSessionId(), e);
                return null;
            }
        }

        // 如果执行到这里，说明已完成所有续写轮次
        log.info("续写流程完成，共执行{}次续写，会话ID：{}", maxContinuations, request.getSessionId());
        return "SUCCESS";
    }

    /**
     * 执行AI响应续写（带重试机制）
     * @return 续写内容，如果失败返回null
     */
    private String continueAiResponse(HttpServletResponse response, ChatRequestDTO request, AiModel model, String originalContent) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                log.debug("开始执行续写操作，会话ID：{}，原内容长度：{}，第{}次尝试",
                    request.getSessionId(), originalContent.length(), retryCount + 1);

                // 在续写开始前检查是否已被用户中断
                if (request.getSessionId() != null && chatInterruptService.isInterrupted(request.getSessionId())) {
                    log.info("续写开始前检测到用户已中断聊天，取消续写操作，会话ID：{}", request.getSessionId());
                    return null; // 直接返回，不执行续写
                }

                // 构建续写请求的消息列表
                List<Map<String, String>> continuationMessages = buildContinuationMessages(request.getMessages(), originalContent);

                // 创建续写请求
                ChatRequestDTO continuationRequest = new ChatRequestDTO();
                continuationRequest.setMessages(continuationMessages);
                continuationRequest.setUserId(request.getUserId());
                continuationRequest.setModelId(model.getId());
                continuationRequest.setSessionId(request.getSessionId());
                continuationRequest.setDeepThinking(request.getDeepThinking());
                continuationRequest.setNetwork(request.getNetwork());

                // 用于收集续写内容和对齐状态
                final StringBuilder continuationContent = new StringBuilder();
                final StringBuilder alignedContentForSaving = new StringBuilder();
                final boolean[] hasContentSent = {false};
                final boolean[] isRealAiResponseArray = {false};
                final boolean[] alignmentDone = {false};
                final boolean[] wasInterrupted = {false}; // 记录续写过程中是否被中断

                // 执行续写请求（带对齐的流式处理）
                boolean success = executeContinuationRequestWithAlignment(continuationRequest, model, response,
                        originalContent, continuationContent, alignedContentForSaving, hasContentSent, isRealAiResponseArray, alignmentDone, wasInterrupted);

                // 如果续写过程中被中断，返回已生成的部分内容给主流程处理
                if (wasInterrupted[0] && hasContentSent[0]) {
                    String partialContinuationContent = continuationContent.toString();
                    log.info("续写过程中被用户中断，返回部分续写内容给主流程处理，会话ID：{}，部分内容长度：{}",
                            request.getSessionId(), partialContinuationContent.length());

                    // 返回特殊标记，表示续写被中断，让主流程知道需要设置中断状态
                    return "CONTINUATION_INTERRUPTED:" + partialContinuationContent;
                }

                if (success && hasContentSent[0]) {
                    // 续写成功，返回续写内容给主线程进行统一保存
                    String continuationContentStr = continuationContent.toString();

                    log.debug("续写操作成功 - 原内容长度: {}, 续写长度: {}",
                            originalContent.length(), continuationContentStr.length());
                    log.info("续写操作成功，返回续写内容给主线程统一保存");

                    return continuationContentStr;
                }

                log.warn("续写操作失败或无内容生成，准备重试");
                // 继续到重试逻辑

            } catch (ContentPolicy.ChatRetryException e) {
                retryCount++;
                log.warn("续写检测到需要重试的错误消息，第{}次重试，最大重试次数: {}", retryCount, maxRetries);

                if (retryCount > maxRetries) {
                    log.error("续写重试次数已达上限，停止重试");
                    return null;
                }

                // 等待一段时间后重试
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("续写重试等待被中断");
                    return null;
                }
                continue; // 继续重试循环

            } catch (Exception e) {
                log.error("续写操作失败", e);
                return null;
            }

            // 如果到这里说明需要重试
            retryCount++;
            if (retryCount > maxRetries) {
                log.error("续写重试次数已达上限，停止重试");
                return null;
            }

            // 等待一段时间后重试
            try {
                Thread.sleep(1000 * retryCount);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("续写重试等待被中断");
                return null;
            }
        }

        // 如果到这里说明重试失败
        log.error("续写操作在多次重试后仍然失败");
        return null;
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
                                                          boolean[] isRealAiResponse, boolean[] alignmentDone, boolean[] wasInterrupted) throws ContentPolicy.ChatRetryException {
        log.debug("开始执行续写请求，用户ID: {}, 模型: {}, 原内容长度: {}", request.getUserId(), model.getModelCode(), originalContent.length());

        // 初始化缓存相关变量
        StringBuilder cacheBuffer = new StringBuilder();
        long[] cacheStartTime = {0};
        int[] cacheBlockCount = {0};
        boolean[] cleaningDone = {false};

        try {
            // 构建请求体（阶段3：委托 AiChatClient）
            Map<String, Object> requestBody = aiChatClient.buildStreamRequestBody(model, request.getMessages());

            log.debug("构建续写消息列表完成，原始消息数：{}，添加截断内容长度：{}，最终消息数：{}",
                    request.getMessages().size() - 2, originalContent.length(), request.getMessages().size());

            // 记录详细的AI请求信息
            logAiRequestDetails(requestBody, model, request.getUserId());

            log.debug("发送续写请求到AI API: {}", model.getBaseUrl());

            // 发送请求并处理流式响应
            final int[] chunkCount = {0};
            final int[] totalBytes = {0};

            // 添加缓冲区机制（参考主聊天，防止数据丢失）
            StringBuilder jsonBuffer = new StringBuilder();
            StringBuilder lineBuffer = new StringBuilder();

            log.debug("开始发送续写WebClient请求");

            aiChatClient.stream(model, requestBody, 60)  // 续写设置更长的超时时间
                    .doOnSubscribe(subscription -> log.debug("续写WebClient订阅成功，开始接收数据流"))
                    .doOnRequest(n -> log.debug("续写WebClient请求 {} 个数据块", n))
                    .map(dataBuffer -> {
                        int bufferSize = dataBuffer.readableByteCount();
                        byte[] bytes = new byte[bufferSize];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String chunk = new String(bytes, StandardCharsets.UTF_8);

                        chunkCount[0]++;
                        totalBytes[0] += bufferSize;
                        // 简化数据块接收日志

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
                    .takeWhile(line -> {
                        // 检查是否被用户中断，如果中断则停止整个流
                        if (request.getSessionId() != null && chatInterruptService.isInterrupted(request.getSessionId())) {
                            log.info("检测到续写过程中用户中断聊天，停止续写流，会话ID：{}", request.getSessionId());
                            wasInterrupted[0] = true; // 设置中断标志
                            return false; // 停止整个流
                        }
                        return true; // 继续处理
                    })
                    .doOnNext(line -> {
                        try {

                            // 简化续写处理日志

                            if (line.startsWith("data: ")) {
                                if (line.equals("data: [DONE]")) {
                                    log.info("续写接收到完成标记 [DONE]，流式响应结束");
                                    return; // 续写完成
                                }

                                String jsonData = line.substring(6).trim();
                                // 简化JSON提取日志

                                // 使用JSON缓冲区处理（参考主聊天）
                                jsonBuffer.append(jsonData);

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    // JSON验证通过，开始处理
                                    try {
                                        String contentSent = processContinuationJson(bufferedJson, response,
                                                originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone);
                                        if (contentSent != null) {
                                            hasContentSent[0] = true;
                                            isRealAiResponse[0] = true;
                                            // 注意：contentSent已经在processContinuationJson中添加到continuationContent了

                                            // 续写内容处理成功
                                        } else {
                                            log.debug("续写处理返回null，可能是空内容或缓存中");
                                        }
                                    } catch (ContentPolicy.ChatRetryException e) {
                                        log.warn("续写处理检测到需要重试的错误消息，抛出重试异常");
                                        throw e; // 重新抛出异常，让上层处理重试
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
                                    } catch (ContentPolicy.ChatRetryException e) {
                                        log.warn("续写跨行JSON处理检测到需要重试的错误消息，抛出重试异常");
                                        throw e; // 重新抛出异常，让上层处理重试
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
                        log.debug("续写WebClient流式响应完成，总接收数据块: {}, 总字节数: {}", chunkCount[0], totalBytes[0]);
                        log.debug("续写完成状态 - hasContentSent: {}, isRealAiResponse: {}, alignmentDone: {}",
                                hasContentSent[0], isRealAiResponse[0], alignmentDone[0]);
                        log.debug("续写内容统计 - 续写内容长度: {}, 对齐后内容长度: {}",
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
                                                } catch (ContentPolicy.ChatRetryException e) {
                                                    log.warn("续写剩余JSON处理检测到需要重试的错误消息，抛出重试异常");
                                                    throw e; // 重新抛出异常，让上层处理重试
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
                            chatStreamEventWriter.writeContent(errorContent, response);
                            hasContentSent[0] = true;
                            log.info("续写错误消息已发送给前端");
                        } catch (Exception e) {
                            log.error("发送续写错误消息失败", e);
                        }
                    })
                    .onErrorReturn("")  // 错误时返回空字符串，避免异常传播
                    .blockLast();

            log.debug("续写WebClient.blockLast()执行完成，最终状态 - hasContentSent: {}", hasContentSent[0]);

            return hasContentSent[0];

        } catch (Exception e) {
            log.error("执行续写请求失败", e);
            return false;
        }
    }



            /**
     * 构建标题生成消息：用户原始内容 + 当前AI响应
     */
    private List<Map<String, String>> buildTitleMessages(ChatRequestDTO request, String aiResponse) {
        return chatTitleService.buildTitleMessages(request, aiResponse);
    }

    /**
     * 提取用户原始内容，移除文件和联网搜索内容
     */
    private String extractOriginalUserContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        String originalContent = content.trim();

        // 移除文件内容：格式为 "原始内容。以下是文件内容：[...]"
        if (originalContent.contains("。以下是文件内容：[")) {
            int fileIndex = originalContent.indexOf("。以下是文件内容：[");
            originalContent = originalContent.substring(0, fileIndex).trim();
        }

        // 移除联网搜索结果：格式为 "原始内容以下是你自己联网搜索得到的结果：[...]"
        if (originalContent.contains("以下是你自己联网搜索得到的结果：[")) {
            int searchIndex = originalContent.indexOf("以下是你自己联网搜索得到的结果：[");
            originalContent = originalContent.substring(0, searchIndex).trim();
        }

        return originalContent.isEmpty() ? content : originalContent;
    }

    @Override
    public String generateSessionTitle(String sessionId, List<Map<String, String>> messages) {
        return chatTitleService.generateSessionTitle(sessionId, messages);
    }

    @Override
    public List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId) {
        return chatContextBuilder.processFilesContent(messages, filesId, userId);
    }

        private NetworkSearchProcessResult processNetworkContent(List<Map<String, String>> messages, String query) {
        return networkSearchOrchestrator.processNetworkContent(messages, query);
    }

    /**
     * 将WebSearchResult列表转换为SearchResponse格式
     * 用于兼容现有的存储逻辑
     */
    private com.instser.chat.entity.SearchResponse convertWebSearchResultsToSearchResponse(List<WebSearchResult> webSearchResults) {
        com.instser.chat.entity.SearchResponse searchResponse = new com.instser.chat.entity.SearchResponse();

        if (webSearchResults != null && !webSearchResults.isEmpty()) {
            searchResponse.setResults(webSearchResults);
        }

        return searchResponse;
    }

        private String extractUserQuery(List<Map<String, String>> messages) {
        return chatContextBuilder.extractUserQuery(messages);
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
                  .replace("\t", "\\t")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("/", "\\/")
                  // 移除或替换可能导致问题的控制字符
                  .replaceAll("[\\x00-\\x1F\\x7F]", " ");
    }



    /**
     * 记录详细的AI请求信息
     */
    private void logAiRequestDetails(Map<String, Object> requestBody, AiModel model, Long userId) {
        try {
            log.debug("【AI请求】用户ID：{}，模型：{}（{}）", userId, model.getModelCode(), model.getBaseUrl());

            // 处理请求体，截取content字段
            Map<String, Object> processedRequestBody = truncateRequestBodyContent(requestBody);

            // 输出处理后的原始请求体
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBodyJson = objectMapper.writeValueAsString(processedRequestBody);
            log.debug("【AI请求】请求体：{}", LogUtils.truncateContent(requestBodyJson));

        } catch (Exception e) {
            log.warn("【AI请求】记录请求详细信息失败：{}", e.getMessage());
        }
    }

    /**
     * 截取请求体中messages里的content字段，只保留前30和后30个字符
     */
    @SuppressWarnings("unchecked")
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
    public void processRetryChat(String sessionId, String messageId, Long modelId, Boolean network, Authentication authentication, HttpServletResponse response) {
        log.info("【重试开始】用户重试请求，会话ID：{}，消息ID：{}，模型ID：{}，联网：{}",
                sessionId, messageId, modelId, network);

        try {
            chatSessionService.updateSessionUpdateTime(sessionId);
            chatStreamEventWriter.setStreamResponseHeaders(response);

            ChatValidationResult validation = validateChatPermissions(sessionId, messageId, "assistant", authentication);
            if (!validation.isSuccess()) {
                chatStreamEventWriter.writeErrorResponse(response, validation.getErrorMessage());
                log.info("【重试结束】验证失败，会话ID：{}，消息ID：{}，原因：{}",
                        sessionId, messageId, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            LogUtils.logUserSessionOperation(log, userId, sessionId, "重试验证", "所有权限验证通过，准备删除消息，用户ID: {}，消息ID: {}", userId, messageId);

            // 1) 删除旧消息 2) 限流 3) Preparer 重建上下文
            int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
            if (deletedCount == 0) {
                LogUtils.logUserSessionError(log, userId, sessionId, "重试执行", "删除消息失败，没有找到指定的消息或删除操作失败，消息ID: {}", null, messageId);
                chatStreamEventWriter.writeErrorResponse(response, "删除消息失败");
                return;
            }
            LogUtils.logUserSessionOperation(log, userId, sessionId, "重试执行", "成功删除{}条消息，消息删除安全完成，准备重新生成AI回复", deletedCount);

            Long rateLimitModelId = modelId != null ? modelId : chatSession.getModelId();
            ChatResponseDTO rateLimitResult = validateChatRateLimit(userId, sessionId, rateLimitModelId, response);
            if (rateLimitResult != null) {
                return;
            }

            PreparedTurn preparedTurn = retryPrepareHandler.prepareAfterDelete(sessionId, userId, modelId, network, chatSession);
            if (!preparedTurn.isSuccess()) {
                chatStreamEventWriter.writeErrorResponse(response, preparedTurn.getErrorMessage());
                return;
            }

            Long targetModelId = modelId != null ? modelId : chatSession.getModelId();
            AiModel model = validateAndGetModel(targetModelId, sessionId, chatSession.getUserId(), response);
            if (model == null) {
                log.info("重试请求AI模型不可用，会话ID：{}，用户ID：{}", sessionId, chatSession.getUserId());
                return;
            }

            updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            String permissionError = validateChatPermission(userId, sessionId, modelId);
            if (permissionError != null) {
                log.info("重试请求聊天权限验证失败，会话ID：{}，用户ID：{}，原因：{}", sessionId, userId, permissionError);
                String errorContent;
                try {
                    if ("PACKAGE_EXPIRED".equals(permissionError)) {
                        errorContent = "Your package has expired.";
                    } else if ("MODEL_LIMIT_EXCEEDED".equals(permissionError)) {
                        errorContent = "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.";
                    } else {
                        errorContent = "Request failed. Please try again later.";
                    }
                    chatStreamEventWriter.writeErrorResponse(response, errorContent);
                    saveErrorResponse(sessionId, userId, model.getId(), errorContent);
                } catch (IOException e) {
                    log.error("发送重试权限错误响应失败", e);
                } catch (Exception e) {
                    log.error("保存重试权限错误响应失败", e);
                }
                return;
            }

            ChatRequestDTO retryRequest = new ChatRequestDTO();
            retryRequest.setMessages(preparedTurn.getMessages());
            retryRequest.setUserId(userId);
            retryRequest.setModelId(model.getId());
            retryRequest.setSessionId(sessionId);
            retryRequest.setDeepThinking(false);
            retryRequest.setNetwork(network != null ? network : false);
            retryRequest.setNetworkSearchResults(preparedTurn.getNetworkSearchResults());
            retryRequest.setOriginalMessages(preparedTurn.getOriginalMessages());

            boolean willGenerateTitle = preparedTurn.isWillGenerateTitle();
            log.info("重新生成请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                preparedTurn.getMessages().stream().filter(msg -> "user".equals(msg.get("role"))).count());

            streamChatWithAI(retryRequest, model, response, willGenerateTitle, "retry");

        } catch (Exception e) {
            log.error("处理重新生成请求异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
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
    public void processEditResendChat(String sessionId, String messageId, String newContent, String filesId, Long modelId, Boolean network, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理编辑重发请求，sessionId：{}，messageId：{}，newContent长度：{}，filesId：{}，modelId：{}，network：{}", sessionId, messageId, newContent.length(), filesId, modelId, network);

        try {
            chatSessionService.updateSessionUpdateTime(sessionId);
            chatStreamEventWriter.setStreamResponseHeaders(response);

            ChatValidationResult validation = validateChatPermissions(sessionId, messageId, "user", authentication);
            if (!validation.isSuccess()) {
                chatStreamEventWriter.writeErrorResponse(response, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            LogUtils.logUserSessionOperation(log, userId, sessionId, "编辑重发", "所有权限验证通过，用户ID: {}，消息ID: {}", userId, messageId);

            // 1) 先替换用户消息 2) 限流 3) 再建上下文（保证限制场景也保留新编辑内容）
            PreparedTurn replaced = editResendPrepareHandler.replaceUserMessage(
                    sessionId, messageId, newContent, userId, chatSession);
            if (!replaced.isSuccess()) {
                chatStreamEventWriter.writeErrorResponse(response, replaced.getErrorMessage());
                return;
            }

            Long rateLimitModelId = modelId != null ? modelId : chatSession.getModelId();
            ChatResponseDTO rateLimitResult = validateChatRateLimit(userId, sessionId, rateLimitModelId, response);
            if (rateLimitResult != null) {
                return;
            }

            PreparedTurn preparedTurn = editResendPrepareHandler.buildContext(
                    sessionId, userId, chatSession, network, replaced.getUserMessageId());
            if (!preparedTurn.isSuccess()) {
                chatStreamEventWriter.writeErrorResponse(response, preparedTurn.getErrorMessage());
                return;
            }

            Long targetModelId = modelId != null ? modelId : chatSession.getModelId();
            AiModel model = validateAndGetModel(targetModelId, sessionId, chatSession.getUserId(), response);
            if (model == null) {
                log.info("编辑重发请求AI模型不可用，会话ID：{}，用户ID：{}", sessionId, chatSession.getUserId());
                return;
            }

            updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            String permissionError = validateChatPermission(userId, sessionId, modelId);
            if (permissionError != null) {
                log.info("编辑重发请求聊天权限验证失败，会话ID：{}，用户ID：{}，原因：{}", sessionId, userId, permissionError);
                String errorContent;
                try {
                    if ("PACKAGE_EXPIRED".equals(permissionError)) {
                        errorContent = "Your package has expired.";
                    } else if ("MODEL_LIMIT_EXCEEDED".equals(permissionError)) {
                        errorContent = "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.";
                    } else {
                        errorContent = "Request failed. Please try again later.";
                    }
                    chatStreamEventWriter.writeErrorResponse(response, errorContent);
                    saveErrorResponse(sessionId, userId, model.getId(), errorContent);
                } catch (IOException e) {
                    log.error("发送编辑重发权限错误响应失败", e);
                } catch (Exception e) {
                    log.error("保存编辑重发权限错误响应失败", e);
                }
                return;
            }

            ChatRequestDTO editResendRequest = new ChatRequestDTO();
            editResendRequest.setMessages(preparedTurn.getMessages());
            editResendRequest.setUserId(userId);
            editResendRequest.setModelId(model.getId());
            editResendRequest.setSessionId(sessionId);
            editResendRequest.setDeepThinking(false);
            editResendRequest.setNetwork(network != null ? network : false);
            editResendRequest.setNetworkSearchResults(preparedTurn.getNetworkSearchResults());
            editResendRequest.setOriginalMessages(preparedTurn.getOriginalMessages());

            boolean willGenerateTitle = preparedTurn.isWillGenerateTitle();
            log.info("编辑重发请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                preparedTurn.getMessages().stream().filter(msg -> "user".equals(msg.get("role"))).count());

            streamChatWithAI(editResendRequest, model, response, willGenerateTitle, "edit-resend");

        } catch (Exception e) {
            log.error("处理编辑重发请求异常", e);
            try {
                String errorContent = "Request failed. Please try again later.";
                chatStreamEventWriter.writeErrorResponse(response, errorContent);
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
     * 验证聊天频率限制
     *
     * @param userId 用户ID
     * @param sessionId 会话ID（用于保存错误响应）
     * @param modelId 模型ID（用于保存错误响应）
     * @param response HTTP响应
     * @return 如果验证失败返回错误响应，验证通过返回null
     */
    private ChatResponseDTO validateChatRateLimit(Long userId, String sessionId, Long modelId, HttpServletResponse response) {
        try {
            // 检查用户聊天频率限制（每分钟限制）
            if (!chatRateLimitService.checkUserChatRateLimit(userId)) {
                log.warn("用户聊天频率超限，用户ID: {}", userId);
                String errorContent = "The request frequency is too high. Please try again later.";
                return handleErrorResponse(response, errorContent, sessionId, userId, modelId);
            }

            // 检查IP聊天频率限制（每分钟限制）
            String clientIp = chatRateLimitService.getClientIpAddress();
            if (!chatRateLimitService.checkIpChatRateLimit(clientIp)) {
                log.warn("IP聊天频率超限，IP: {}, 用户ID: {}", clientIp, userId);
                String errorContent = "The request frequency is too high. Please try again later.";
                return handleErrorResponse(response, errorContent, sessionId, userId, modelId);
            }

            // 检查用户每日聊天限制
            if (!chatRateLimitService.checkDailyChatLimit(userId)) {
                log.warn("用户每日聊天次数超限，用户ID: {}", userId);
                String errorContent = "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.";
                return handleErrorResponse(response, errorContent, sessionId, userId, modelId);
            }

            return null; // 验证通过
        } catch (Exception e) {
            log.error("验证聊天频率限制时发生异常，用户ID: {}", userId, e);
            String errorContent = "Request failed. Please try again later.";
            return handleErrorResponse(response, errorContent, sessionId, userId, modelId);
        }
    }

    /**
     * 保存联网查询结果到AI响应消息
     *
     * @param request 聊天请求
     * @param aiResponseMessageId AI响应消息ID
     */
    private void saveWebSearchResultsToAiResponse(ChatRequestDTO request, String aiResponseMessageId) {
        // 阶段4：委托完成副作用处理器
        aiResponseCompletionHandler.bindNetworkResults(request, aiResponseMessageId);
    }








}
