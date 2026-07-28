package com.instser.chat.service.serviceimpl;

import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatTitleService;
import com.instser.chat.service.completion.StreamCompletionCoordinator;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.persistence.ChatPersistenceService;
import com.instser.chat.service.prepare.ChatPrepareHandler;
import com.instser.chat.service.prepare.EditResendPrepareHandler;
import com.instser.chat.service.prepare.PreparedTurn;
import com.instser.chat.service.prepare.RetryPrepareHandler;
import com.instser.chat.service.stream.AiRequestLogger;
import com.instser.chat.service.stream.AiStreamProcessor;
import com.instser.chat.service.stream.ChatStreamSink;
import com.instser.chat.service.validation.ChatRequestValidator;
import com.instser.chat.service.validation.SessionCommandValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 聊天应用编排服务（终态）：只串联校验、准备、流式、完成副作用，不承载细节实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final AiModelService aiModelService;
    private final ChatTitleService chatTitleService;
    private final ChatContextBuilder chatContextBuilder;
    private final ChatPrepareHandler chatPrepareHandler;
    private final RetryPrepareHandler retryPrepareHandler;
    private final EditResendPrepareHandler editResendPrepareHandler;
    private final ChatRequestValidator chatRequestValidator;
    private final ChatPersistenceService chatPersistenceService;
    private final AiStreamProcessor aiStreamProcessor;
    private final StreamCompletionCoordinator streamCompletionCoordinator;
    private final AiRequestLogger aiRequestLogger;

    @Override
    public ChatResponseDTO processStreamChat(ChatRequestDTO request, Authentication authentication, ChatStreamSink sink) {
        Long authenticatedUserId = chatRequestValidator.getCurrentUserId(authentication);
        if (authenticatedUserId == null) {
            LogUtils.logUserWarning(log, null, "聊天开始", "用户未认证，拒绝请求");
            try {
                sink.writeError("用户未认证");
            } catch (IOException e) {
                LogUtils.logUserError(log, null, "发送错误响应", "发送错误响应失败", e);
            }
            return ChatResponseDTO.failure("用户未认证");
        }
        request.setUserId(authenticatedUserId);
        LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天开始", "模型ID：{}", request.getModelId());

        try {
            sink.setHeaders();

            AiModel model = chatRequestValidator.validateAndGetModel(
                    request.getModelId(), request.getSessionId(), request.getUserId(), sink);
            if (model == null) {
                LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天结束", "结果：模型验证失败");
                return ChatResponseDTO.errorWithContent("Request failed. Please try again later.");
            }

            String userMessageId = chatPersistenceService.saveUserMessageEarly(request, model, sink);
            if (userMessageId == null) {
                LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天结束", "结果：用户消息保存失败");
                return ChatResponseDTO.failure("保存用户消息失败");
            }

            ChatResponseDTO validationResult = chatRequestValidator.performPreValidationWithMessageSaving(
                    request, authentication, sink, model);
            if (validationResult != null) {
                LogUtils.logUserSessionOperation(log, request.getUserId(), request.getSessionId(), "聊天结束", "结果：前置验证失败");
                return validationResult;
            }

            chatSessionService.updateSessionUpdateTime(request.getSessionId());

            PreparedTurn preparedTurn = chatPrepareHandler.prepareContext(request, model, userMessageId);
            if (!preparedTurn.isSuccess()) {
                String errorContent = preparedTurn.getErrorMessage() != null
                        ? preparedTurn.getErrorMessage() : "会话中没有可用的消息";
                sink.writeError(errorContent);
                log.info("【聊天结束】用户ID：{}，会话ID：{}，结果：消息构建失败",
                        request.getUserId(), request.getSessionId());
                return ChatResponseDTO.failure(errorContent);
            }

            applyPreparedTurn(request, preparedTurn);
            log.info("【聊天开始】用户ID：{}，会话ID：{}，模型：{}，消息数：{}",
                    request.getUserId(), request.getSessionId(), model.getModelCode(), request.getMessages().size());

            ChatResponseDTO result = executeAiStreamResponse(request, model, sink);
            log.info("【聊天结束】用户ID：{}，会话ID：{}，结果：{}，内容长度：{}",
                    request.getUserId(), request.getSessionId(),
                    result.isSuccess() ? "成功" : (result.isInterrupted() ? "中断" : "失败"),
                    result.getAiResponseContent() != null ? result.getAiResponseContent().length() : 0);
            return result;
        } catch (Exception e) {
            log.error("【聊天异常】用户ID：{}，会话ID：{}，错误：{}",
                    request.getUserId(), request.getSessionId(), e.getMessage(), e);
            return chatPersistenceService.handleStreamChatException(e, request, sink, aiModelService::getById);
        }
    }

    @Override
    public String validateChatRequest(ChatRequestDTO request) {
        return chatRequestValidator.validateChatRequest(request);
    }

    @Override
    public String validateUserPermission(Authentication authentication, Long userId) {
        return chatRequestValidator.validateUserPermission(authentication, userId);
    }

    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId) {
        return chatPersistenceService.saveUserMessage(messages, sessionId, userId);
    }

    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId, Long modelId) {
        return chatPersistenceService.saveUserMessage(messages, sessionId, userId, modelId);
    }

    @Override
    public void saveAiResponse(String sessionId, Long userId, Long modelId, String content) {
        chatPersistenceService.saveAiResponse(sessionId, userId, modelId, content);
    }

    @Override
    public void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent) {
        chatPersistenceService.saveErrorResponse(sessionId, userId, modelId, errorContent);
    }

    @Override
    public boolean isFirstAiResponse(List<Map<String, String>> messages) {
        return chatRequestValidator.isFirstAiResponse(messages);
    }

    @Override
    public String generateSessionTitle(String sessionId, List<Map<String, String>> messages) {
        return chatTitleService.generateSessionTitle(sessionId, messages);
    }

    @Override
    public List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId) {
        return chatContextBuilder.processFilesContent(messages, filesId, userId);
    }

    @Override
    public void savePartialAiResponse(String sessionId, Long userId, Long modelId, String partialContent, boolean isInterrupted) {
        chatPersistenceService.savePartialAiResponse(sessionId, userId, modelId, partialContent, isInterrupted);
    }

    @Override
    public void processRetryChat(String sessionId, String messageId, Long modelId, Boolean network,
                                 Authentication authentication, ChatStreamSink sink) {
        log.info("【重试开始】用户重试请求，会话ID：{}，消息ID：{}，模型ID：{}，联网：{}",
                sessionId, messageId, modelId, network);
        try {
            chatSessionService.updateSessionUpdateTime(sessionId);
            sink.setHeaders();

            SessionCommandValidation validation = chatRequestValidator.validateSessionCommand(
                    sessionId, messageId, "assistant", authentication);
            if (!validation.isSuccess()) {
                sink.writeError(validation.getErrorMessage());
                log.info("【重试结束】验证失败，会话ID：{}，消息ID：{}，原因：{}",
                        sessionId, messageId, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            LogUtils.logUserSessionOperation(log, userId, sessionId, "重试验证",
                    "所有权限验证通过，准备删除消息，用户ID: {}，消息ID: {}", userId, messageId);

            // retry 时序：先删后限流
            int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
            if (deletedCount == 0) {
                LogUtils.logUserSessionError(log, userId, sessionId, "重试执行",
                        "删除消息失败，没有找到指定的消息或删除操作失败，消息ID: {}", null, messageId);
                sink.writeError("删除消息失败");
                return;
            }
            LogUtils.logUserSessionOperation(log, userId, sessionId, "重试执行",
                    "成功删除{}条消息，消息删除安全完成，准备重新生成AI回复", deletedCount);

            Long rateLimitModelId = modelId != null ? modelId : chatSession.getModelId();
            ChatResponseDTO rateLimitResult = chatRequestValidator.validateChatRateLimit(
                    userId, sessionId, rateLimitModelId, sink);
            if (rateLimitResult != null) {
                return;
            }

            PreparedTurn preparedTurn = retryPrepareHandler.prepareAfterDelete(sessionId, userId, modelId, network, chatSession);
            if (!preparedTurn.isSuccess()) {
                sink.writeError(preparedTurn.getErrorMessage());
                return;
            }

            Long targetModelId = modelId != null ? modelId : chatSession.getModelId();
            AiModel model = chatRequestValidator.validateAndGetModel(targetModelId, sessionId, chatSession.getUserId(), sink);
            if (model == null) {
                log.info("重试请求AI模型不可用，会话ID：{}，用户ID：{}", sessionId, chatSession.getUserId());
                return;
            }

            chatPersistenceService.updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            String permissionError = chatRequestValidator.validateChatPermission(userId, sessionId, modelId);
            if (permissionError != null) {
                log.info("重试请求聊天权限验证失败，会话ID：{}，用户ID：{}，原因：{}", sessionId, userId, permissionError);
                chatRequestValidator.writePermissionFailure(sink, sessionId, userId, model, permissionError);
                return;
            }

            ChatRequestDTO retryRequest = buildCommandRequest(sessionId, userId, model, network, preparedTurn);
            boolean willGenerateTitle = preparedTurn.isWillGenerateTitle();
            log.info("重新生成请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                    preparedTurn.getMessages().stream().filter(msg -> "user".equals(msg.get("role"))).count());
            streamChatWithAI(retryRequest, model, sink, willGenerateTitle, "retry");
        } catch (Exception e) {
            log.error("处理重新生成请求异常", e);
            writeCommandException(sessionId, sink);
        }
    }

    @Override
    public void processEditResendChat(String sessionId, String messageId, String newContent, String filesId,
                                      Long modelId, Boolean network, Authentication authentication, ChatStreamSink sink) {
        log.info("开始处理编辑重发请求，sessionId：{}，messageId：{}，newContent长度：{}，filesId：{}，modelId：{}，network：{}",
                sessionId, messageId, newContent.length(), filesId, modelId, network);
        try {
            chatSessionService.updateSessionUpdateTime(sessionId);
            sink.setHeaders();

            SessionCommandValidation validation = chatRequestValidator.validateSessionCommand(
                    sessionId, messageId, "user", authentication);
            if (!validation.isSuccess()) {
                sink.writeError(validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            LogUtils.logUserSessionOperation(log, userId, sessionId, "编辑重发",
                    "所有权限验证通过，用户ID: {}，消息ID: {}", userId, messageId);

            // edit-resend 时序：先替换用户消息再限流
            PreparedTurn replaced = editResendPrepareHandler.replaceUserMessage(
                    sessionId, messageId, newContent, userId, chatSession);
            if (!replaced.isSuccess()) {
                sink.writeError(replaced.getErrorMessage());
                return;
            }

            Long rateLimitModelId = modelId != null ? modelId : chatSession.getModelId();
            ChatResponseDTO rateLimitResult = chatRequestValidator.validateChatRateLimit(
                    userId, sessionId, rateLimitModelId, sink);
            if (rateLimitResult != null) {
                return;
            }

            PreparedTurn preparedTurn = editResendPrepareHandler.buildContext(
                    sessionId, userId, chatSession, network, replaced.getUserMessageId());
            if (!preparedTurn.isSuccess()) {
                sink.writeError(preparedTurn.getErrorMessage());
                return;
            }

            Long targetModelId = modelId != null ? modelId : chatSession.getModelId();
            AiModel model = chatRequestValidator.validateAndGetModel(targetModelId, sessionId, chatSession.getUserId(), sink);
            if (model == null) {
                log.info("编辑重发请求AI模型不可用，会话ID：{}，用户ID：{}", sessionId, chatSession.getUserId());
                return;
            }

            chatPersistenceService.updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            String permissionError = chatRequestValidator.validateChatPermission(userId, sessionId, modelId);
            if (permissionError != null) {
                log.info("编辑重发请求聊天权限验证失败，会话ID：{}，用户ID：{}，原因：{}", sessionId, userId, permissionError);
                chatRequestValidator.writePermissionFailure(sink, sessionId, userId, model, permissionError);
                return;
            }

            ChatRequestDTO editResendRequest = buildCommandRequest(sessionId, userId, model, network, preparedTurn);
            boolean willGenerateTitle = preparedTurn.isWillGenerateTitle();
            log.info("编辑重发请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                    preparedTurn.getMessages().stream().filter(msg -> "user".equals(msg.get("role"))).count());
            streamChatWithAI(editResendRequest, model, sink, willGenerateTitle, "edit-resend");
        } catch (Exception e) {
            log.error("处理编辑重发请求异常", e);
            writeCommandException(sessionId, sink);
        }
    }

    private ChatResponseDTO executeAiStreamResponse(ChatRequestDTO request, AiModel model, ChatStreamSink sink) {
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount <= maxRetries) {
            try {
                boolean willGenerateTitle = request.getSessionId() != null
                        && chatRequestValidator.isFirstAiResponse(request.getMessages());
                return streamChatWithAI(request, model, sink, willGenerateTitle, "chat");
            } catch (ContentPolicy.ChatRetryException e) {
                retryCount++;
                log.warn("检测到需要重试的错误消息，第{}次重试，最大重试次数: {}", retryCount, maxRetries);
                if (retryCount > maxRetries) {
                    log.error("重试次数已达上限，停止重试");
                    return writeGenericStreamError(sink);
                }
                try {
                    Thread.sleep(1000L * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("重试等待被中断");
                    break;
                }
            } catch (Exception e) {
                log.error("执行AI流式响应异常", e);
                return writeGenericStreamError(sink);
            }
        }
        return writeGenericStreamError(sink);
    }

    private ChatResponseDTO streamChatWithAI(ChatRequestDTO request, AiModel model, ChatStreamSink sink,
                                             boolean willGenerateTitle, String interfaceType) {
        return aiStreamProcessor.stream(
                request,
                model,
                sink,
                willGenerateTitle,
                interfaceType,
                streamCompletionCoordinator::complete,
                chatPersistenceService::saveErrorResponse,
                (requestBody, m) -> {
                    aiRequestLogger.logAiRequestDetails(requestBody, m, request.getUserId());
                    return null;
                }
        );
    }

    private void applyPreparedTurn(ChatRequestDTO request, PreparedTurn preparedTurn) {
        request.setMessages(preparedTurn.getMessages());
        request.setOriginalMessages(preparedTurn.getOriginalMessages());
        request.setNetworkSearchResults(preparedTurn.getNetworkSearchResults());
    }

    private ChatRequestDTO buildCommandRequest(String sessionId, Long userId, AiModel model,
                                               Boolean network, PreparedTurn preparedTurn) {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessages(preparedTurn.getMessages());
        request.setUserId(userId);
        request.setModelId(model.getId());
        request.setSessionId(sessionId);
        request.setDeepThinking(false);
        request.setNetwork(network != null ? network : false);
        request.setNetworkSearchResults(preparedTurn.getNetworkSearchResults());
        request.setOriginalMessages(preparedTurn.getOriginalMessages());
        return request;
    }

    private ChatResponseDTO writeGenericStreamError(ChatStreamSink sink) {
        try {
            String errorContent = "Request failed. Please try again later.";
            sink.writeError(errorContent);
            return ChatResponseDTO.errorWithContent(errorContent);
        } catch (IOException ioException) {
            log.error("发送错误响应失败", ioException);
            return ChatResponseDTO.failure("System error");
        }
    }

    private void writeCommandException(String sessionId, ChatStreamSink sink) {
        try {
            String errorContent = "Request failed. Please try again later.";
            sink.writeError(errorContent);
            ChatSession chatSession = chatSessionService.getSessionInfo(sessionId);
            if (chatSession != null) {
                AiModel model = aiModelService.getById(chatSession.getModelId());
                if (model != null) {
                    chatPersistenceService.saveErrorResponse(sessionId, chatSession.getUserId(), model.getId(), errorContent);
                }
            }
        } catch (Exception ex) {
            log.error("发送命令错误消息失败", ex);
        }
    }
}
