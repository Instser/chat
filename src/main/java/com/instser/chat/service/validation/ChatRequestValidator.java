package com.instser.chat.service.validation;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatRateLimitService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.cache.RequestValidationCache;
import com.instser.chat.service.persistence.ChatPersistenceService;
import com.instser.chat.service.stream.ChatStreamSink;
import com.instser.chat.vo.ChatLimitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 聊天请求校验：参数、用户、模型、套餐/次数、频控、活跃会话、会话命令权限。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRequestValidator {

    private final AiModelService aiModelService;
    private final ChatLimitService chatLimitService;
    private final ChatRateLimitService chatRateLimitService;
    private final ChatInterruptService chatInterruptService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatPersistenceService chatPersistenceService;

    @Autowired(required = false)
    private RequestValidationCache requestValidationCache;

    @Autowired(required = false)
    private com.instser.chat.config.TrialProperties trialProperties;

    public Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("认证信息为空或未认证");
            return null;
        }
        try {
            Object principal = authentication.getPrincipal();
            log.debug("认证主体类型：{}", principal != null ? principal.getClass().getName() : "null");
            if (principal instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails userDetails =
                        (com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) principal;
                Long userId = userDetails.getUserId();
                log.debug("从认证信息中获取到用户ID：{}", userId);
                return userId;
            }
            log.warn("认证主体不是CustomUserDetails类型：{}", principal != null ? principal.getClass().getName() : "null");
            return null;
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return null;
        }
    }

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
        Map<String, String> lastMessage = request.getMessages().get(request.getMessages().size() - 1);
        String role = lastMessage.get("role");
        if (!"user".equals(role)) {
            return "最后一条消息必须是用户消息";
        }
        return null;
    }

    public String validateUserPermission(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "用户未认证";
        }
        Long authenticatedUserId = getCurrentUserId(authentication);
        if (authenticatedUserId == null) {
            return "无法获取认证用户信息";
        }
        if (!authenticatedUserId.equals(userId)) {
            log.warn("用户ID不匹配，认证用户ID：{}，请求用户ID：{}", authenticatedUserId, userId);
            return "用户权限验证失败";
        }
        return null;
    }

    public AiModel validateAndGetModel(Long modelId, String sessionId, Long userId, ChatStreamSink sink) {
        try {
            AiModel model = aiModelService.getById(modelId);
            if (model == null || model.getIsAvailable() != 1) {
                String errorContent = "Request failed. Please try again later.";
                Long modelIdForSave = (model != null) ? model.getId() : 1L;
                chatPersistenceService.handleErrorResponse(sink, errorContent, sessionId, userId, modelIdForSave);
                log.info("AI模型不可用错误响应已保存到数据库，会话ID：{}，用户ID：{}", sessionId, userId);
                return null;
            }
            return model;
        } catch (Exception e) {
            log.error("验证AI模型配置异常", e);
            String errorContent = "Request failed. Please try again later.";
            chatPersistenceService.handleErrorResponse(sink, errorContent, sessionId, userId, 1L);
            return null;
        }
    }

    /**
     * chat 入口：用户消息已保存后的前置校验（失败时写流 + 落 status=3）。
     */
    public ChatResponseDTO performPreValidationWithMessageSaving(ChatRequestDTO request,
                                                                 Authentication authentication,
                                                                 ChatStreamSink sink,
                                                                 AiModel model) {
        try {
            String validationError = validateChatRequest(request);
            if (validationError != null) {
                return chatPersistenceService.saveErrorResponseAndReturn(sink, validationError, request, model);
            }

            String permissionError = validateUserPermission(authentication, request.getUserId());
            if (permissionError != null) {
                return chatPersistenceService.saveErrorResponseAndReturn(sink, permissionError, request, model);
            }

            String chatPermissionError = validateChatPermission(request.getUserId(), request.getSessionId(), request.getModelId());
            if (chatPermissionError != null) {
                String customErrorMessage = mapPermissionError(chatPermissionError);
                return chatPersistenceService.saveErrorResponseAndReturn(sink, customErrorMessage, request, model);
            }

            ChatResponseDTO rateLimitResult = validateChatRateLimitWithSaving(
                    request.getUserId(), request.getSessionId(), request.getModelId(), sink, request, model);
            if (rateLimitResult != null) {
                return rateLimitResult;
            }

            if (chatInterruptService.hasActiveSession(request.getUserId())) {
                String errorMessage = "Currently chatting, please end the chat and try again";
                return chatPersistenceService.saveErrorResponseAndReturn(sink, errorMessage, request, model);
            }
            return null;
        } catch (Exception e) {
            log.error("前置验证异常", e);
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("PACKAGE_EXPIRED") || exceptionMessage.contains("套餐已过期")) {
                    return chatPersistenceService.saveErrorResponseAndReturn(sink, "Your package has expired.", request, model);
                } else if (exceptionMessage.contains("MODEL_LIMIT_EXCEEDED") || exceptionMessage.contains("模型使用次数已达上限")) {
                    return chatPersistenceService.saveErrorResponseAndReturn(sink,
                            "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.",
                            request, model);
                }
            }
            return chatPersistenceService.saveErrorResponseAndReturn(sink,
                    "Request failed. Please try again later.", request, model);
        }
    }

    public ChatResponseDTO validateChatRateLimit(Long userId, String sessionId, Long modelId, ChatStreamSink sink) {
        try {
            if (!chatRateLimitService.checkUserChatRateLimit(userId)) {
                log.warn("用户聊天频率超限，用户ID: {}", userId);
                return chatPersistenceService.handleErrorResponse(sink,
                        "The request frequency is too high. Please try again later.", sessionId, userId, modelId);
            }
            String clientIp = chatRateLimitService.getClientIpAddress();
            if (!chatRateLimitService.checkIpChatRateLimit(clientIp)) {
                log.warn("IP聊天频率超限，IP: {}, 用户ID: {}", clientIp, userId);
                return chatPersistenceService.handleErrorResponse(sink,
                        "The request frequency is too high. Please try again later.", sessionId, userId, modelId);
            }
            if (!chatRateLimitService.checkDailyChatLimit(userId)) {
                log.warn("用户每日聊天次数超限，用户ID: {}", userId);
                return chatPersistenceService.handleErrorResponse(sink,
                        "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.",
                        sessionId, userId, modelId);
            }
            return null;
        } catch (Exception e) {
            log.error("验证聊天频率限制时发生异常，用户ID: {}", userId, e);
            return chatPersistenceService.handleErrorResponse(sink,
                    "Request failed. Please try again later.", sessionId, userId, modelId);
        }
    }

    public ChatResponseDTO validateChatRateLimitWithSaving(Long userId, String sessionId, Long modelId,
                                                           ChatStreamSink sink, ChatRequestDTO request, AiModel model) {
        try {
            if (!chatRateLimitService.checkUserChatRateLimit(userId)) {
                log.warn("用户聊天频率超限，用户ID: {}", userId);
                return chatPersistenceService.saveErrorResponseAndReturn(sink,
                        "The request frequency is too high. Please try again later.", request, model);
            }
            String clientIp = chatRateLimitService.getClientIpAddress();
            if (!chatRateLimitService.checkIpChatRateLimit(clientIp)) {
                log.warn("IP聊天频率超限，IP: {}, 用户ID: {}", clientIp, userId);
                return chatPersistenceService.saveErrorResponseAndReturn(sink,
                        "The request frequency is too high. Please try again later.", request, model);
            }
            if (!chatRateLimitService.checkDailyChatLimit(userId)) {
                log.warn("用户每日聊天次数超限，用户ID: {}", userId);
                return chatPersistenceService.saveErrorResponseAndReturn(sink,
                        "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.",
                        request, model);
            }
            return null;
        } catch (Exception e) {
            log.error("验证聊天频率限制时发生异常，用户ID: {}", userId, e);
            return chatPersistenceService.saveErrorResponseAndReturn(sink,
                    "Request failed. Please try again later.", request, model);
        }
    }

    public String validateChatPermission(Long userId, String sessionId, Long modelId) {
        if (requestValidationCache != null) {
            return validateChatPermissionOptimized(userId, sessionId, modelId);
        }
        return validateChatPermissionDirect(userId, sessionId, modelId);
    }

    public String mapPermissionError(String chatPermissionError) {
        if ("PACKAGE_EXPIRED".equals(chatPermissionError)) {
            return "Your package has expired.";
        }
        if ("MODEL_LIMIT_EXCEEDED".equals(chatPermissionError)) {
            return "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.";
        }
        if ("TRIAL_LIMIT_EXCEEDED".equals(chatPermissionError)) {
            // 游客试用额度耗尽：提示注册转化（次数取试用配置，默认 10）
            int limit = trialProperties != null ? trialProperties.getChatLimit() : 10;
            return "游客试用额度已用完（共" + limit + "次），注册账号即可继续畅聊";
        }
        return "Request failed. Please try again later.";
    }

    public void writePermissionFailure(ChatStreamSink sink, String sessionId, Long userId, AiModel model,
                                       String permissionError) {
        String errorContent = mapPermissionError(permissionError);
        try {
            sink.writeError(errorContent);
            if (model != null) {
                chatPersistenceService.saveErrorResponse(sessionId, userId, model.getId(), errorContent);
            }
        } catch (IOException e) {
            log.error("发送权限错误响应失败", e);
        } catch (Exception e) {
            log.error("保存权限错误响应失败", e);
        }
    }

    public SessionCommandValidation validateSessionCommand(String sessionId, String messageId, String expectedRole,
                                                           Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return SessionCommandValidation.error("用户未认证");
        }
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return SessionCommandValidation.error("无法获取用户信息");
        }
        ChatSession chatSession = chatSessionService.getSessionInfo(sessionId);
        if (chatSession == null) {
            return SessionCommandValidation.error("会话不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return SessionCommandValidation.error("无权访问此会话");
        }
        ChatMessage targetMessage = chatMessageService.getByMessageId(messageId);
        if (targetMessage == null) {
            return SessionCommandValidation.error("消息不存在");
        }
        if (!targetMessage.getSessionId().equals(sessionId)) {
            return SessionCommandValidation.error("消息不属于此会话");
        }
        if (expectedRole != null && !expectedRole.equals(targetMessage.getRole())) {
            String errorMsg = "assistant".equals(expectedRole) ? "只能重新生成AI消息" : "只能编辑用户消息";
            return SessionCommandValidation.error(errorMsg);
        }
        return SessionCommandValidation.success(userId, chatSession, targetMessage);
    }

    public boolean isFirstAiResponse(List<Map<String, String>> messages) {
        if (messages == null) {
            return false;
        }
        long userMessageCount = messages.stream()
                .filter(msg -> "user".equals(msg.get("role")))
                .count();
        return userMessageCount == 1;
    }

    private String validateChatPermissionDirect(Long userId, String sessionId, Long modelId) {
        log.debug("【权限验证】开始验证聊天权限，用户ID：{}，会话ID：{}，模型ID：{}", userId, sessionId, modelId);
        try {
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.isCanChat()) {
                String reason = limitCheck.getLimitReason();
                log.info("【权限验证】基础权限验证失败，用户ID：{}，会话ID：{}，原因：{}", userId, sessionId, reason);
                return reason;
            }
            if (modelId != null) {
                AiModel model = aiModelService.getById(modelId);
                if (model != null) {
                    ChatLimitVO modelLimitCheck = chatLimitService.validateModelChatPermission(userId, modelId.toString());
                    if (!modelLimitCheck.isCanChat()) {
                        String reason = modelLimitCheck.getLimitReason();
                        log.info("【权限验证】模型权限验证失败，用户ID：{}，会话ID：{}，模型ID：{}，模型代码：{}，原因：{}",
                                userId, sessionId, modelId, model.getModelCode(), reason);
                        if ("MODEL_LIMIT_EXCEEDED".equals(reason)) {
                            return "MODEL_LIMIT_EXCEEDED";
                        }
                        return reason;
                    }
                }
            }
            log.info("【权限验证】验证通过，用户ID：{}，套餐：{}，限制类型：{}",
                    userId, limitCheck.getPackageName(), limitCheck.getLimitType());
            return null;
        } catch (Exception e) {
            log.error("【权限验证】验证异常，用户ID：{}，会话ID：{}", userId, sessionId, e);
            return mapExceptionToPermissionCode(e);
        }
    }

    private String validateChatPermissionOptimized(Long userId, String sessionId, Long modelId) {
        log.debug("【权限验证-优化】开始验证聊天权限，用户ID：{}，会话ID：{}，模型ID：{}", userId, sessionId, modelId);
        try {
            ChatLimitVO limitCheck;
            if (requestValidationCache != null) {
                String cacheKey = "chat_permission:" + userId;
                limitCheck = requestValidationCache.getOrCompute(cacheKey,
                        () -> chatLimitService.validateChatPermission(userId));
            } else {
                limitCheck = chatLimitService.validateChatPermission(userId);
            }
            if (!limitCheck.isCanChat()) {
                String reason = limitCheck.getLimitReason();
                log.info("【权限验证-优化】基础权限验证失败，用户ID：{}，会话ID：{}，原因：{}", userId, sessionId, reason);
                return reason;
            }
            if (modelId != null) {
                AiModel model;
                if (requestValidationCache != null) {
                    String modelCacheKey = "ai_model:" + modelId;
                    model = requestValidationCache.getOrCompute(modelCacheKey, () -> aiModelService.getById(modelId));
                } else {
                    model = aiModelService.getById(modelId);
                }
                if (model != null) {
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
                        if ("MODEL_LIMIT_EXCEEDED".equals(reason)) {
                            return "MODEL_LIMIT_EXCEEDED";
                        }
                        return reason;
                    }
                }
            }
            log.info("【权限验证-优化】验证通过，用户ID：{}，套餐：{}，限制类型：{}",
                    userId, limitCheck.getPackageName(), limitCheck.getLimitType());
            return null;
        } catch (Exception e) {
            log.error("【权限验证-优化】验证异常，用户ID：{}，会话ID：{}", userId, sessionId, e);
            return mapExceptionToPermissionCode(e);
        }
    }

    private String mapExceptionToPermissionCode(Exception e) {
        String exceptionMessage = e.getMessage();
        if (exceptionMessage != null) {
            if (exceptionMessage.contains("模型使用次数已达上限")
                    || exceptionMessage.contains("模型使用超限")
                    || exceptionMessage.contains("每日聊天次数已达到套餐限制")
                    || exceptionMessage.contains("MODEL_LIMIT_EXCEEDED")
                    || exceptionMessage.contains("使用限制")) {
                return "MODEL_LIMIT_EXCEEDED";
            } else if (exceptionMessage.contains("PACKAGE_EXPIRED") || exceptionMessage.contains("套餐已过期")) {
                return "PACKAGE_EXPIRED";
            }
        }
        return "Request failed. Please try again later.";
    }
}
