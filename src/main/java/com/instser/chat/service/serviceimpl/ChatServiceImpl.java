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
 * 聊天应用编排服务（方案终态的 Application 层）。
 *
 * <p><b>职责边界</b>：只串联「校验 → 准备 → 流式 → 完成副作用」，不实现校验细节、落库细节、
 * Provider 协议或 SSE 字符串拼装。</p>
 *
 * <p><b>三条入口共用执行核</b>：
 * {@link #processStreamChat} / {@link #processRetryChat} / {@link #processEditResendChat}
 * 最终都进入 {@link #streamChatWithAI} → {@link AiStreamProcessor} + {@link StreamCompletionCoordinator}。</p>
 *
 * <p><b>关键时序（不得回退）</b>：
 * <ul>
 *   <li>chat：先 {@link ChatPersistenceService#saveUserMessageEarly}，再限流/套餐校验</li>
 *   <li>retry：先 {@link ChatMessageService#deleteMessagesFromPoint}，再限流</li>
 *   <li>edit-resend：先 {@link EditResendPrepareHandler#replaceUserMessage}，再限流</li>
 * </ul>
 * </p>
 *
 * @see ChatService 对外业务接口（Controller 依赖）
 * @see ChatRequestValidator 参数/用户/模型/套餐/频控
 * @see ChatPersistenceService 消息与错误落库
 * @see ChatPrepareHandler 普通聊天上下文准备
 * @see RetryPrepareHandler 重试上下文准备
 * @see EditResendPrepareHandler 编辑重发上下文准备
 * @see AiStreamProcessor 上游 AI 流消费
 * @see StreamCompletionCoordinator 续写 + 完成副作用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** retry 删除点后消息；引用：{@link ChatMessageService#deleteMessagesFromPoint} */
    private final ChatMessageService chatMessageService;
    /** 会话更新时间 / 异常时查会话；引用：{@link ChatSessionService} */
    private final ChatSessionService chatSessionService;
    /** 异常路径按会话 modelId 取模型；引用：{@link AiModelService#getById} */
    private final AiModelService aiModelService;
    /** 标题生成门面委托；引用：{@link ChatTitleService#generateSessionTitle} */
    private final ChatTitleService chatTitleService;
    /** 文件内容拼接到 messages；引用：{@link ChatContextBuilder#processFilesContent} */
    private final ChatContextBuilder chatContextBuilder;
    /** 普通 chat 的 prepare（历史 + 文件 + 联网）；引用：{@link ChatPrepareHandler#prepareContext} */
    private final ChatPrepareHandler chatPrepareHandler;
    /** retry 删除后重建上下文；引用：{@link RetryPrepareHandler#prepareAfterDelete} */
    private final RetryPrepareHandler retryPrepareHandler;
    /** edit-resend 替换用户消息 + 建上下文；引用：{@link EditResendPrepareHandler} */
    private final EditResendPrepareHandler editResendPrepareHandler;
    /** 请求/权限/模型/频控校验；引用：{@link ChatRequestValidator} */
    private final ChatRequestValidator chatRequestValidator;
    /** 用户/AI/错误/部分消息落库与文件关联；引用：{@link ChatPersistenceService} */
    private final ChatPersistenceService chatPersistenceService;
    /** AI 流式消费主路径；引用：{@link AiStreamProcessor#stream} */
    private final AiStreamProcessor aiStreamProcessor;
    /** 流结束后的续写 + completeSideEffects；引用：{@link StreamCompletionCoordinator#complete} */
    private final StreamCompletionCoordinator streamCompletionCoordinator;
    /** AI 请求体截断日志；引用：{@link AiRequestLogger#logAiRequestDetails} */
    private final AiRequestLogger aiRequestLogger;

    /**
     * 普通流式聊天主入口（Controller → 本方法）。
     *
     * <p><b>编排步骤</b>：
     * 1. 从认证取 userId 写回 request<br>
     * 2. SSE 头 → 校验模型 → 早存用户消息 → 前置校验<br>
     * 3. 更新会话时间 → Preparer 构建上下文 → 流式执行
     * </p>
     *
     * <p><b>内部引用</b>：
     * {@link ChatRequestValidator#getCurrentUserId} /
     * {@link ChatRequestValidator#validateAndGetModel} /
     * {@link ChatPersistenceService#saveUserMessageEarly} /
     * {@link ChatRequestValidator#performPreValidationWithMessageSaving} /
     * {@link ChatSessionService#updateSessionUpdateTime} /
     * {@link ChatPrepareHandler#prepareContext} /
     * {@link #applyPreparedTurn} /
     * {@link #executeAiStreamResponse} /
     * {@link ChatPersistenceService#handleStreamChatException}
     * </p>
     *
     * @param request        前端聊天请求（messages/modelId/sessionId/files/network 等）
     * @param authentication Spring Security 认证主体
     * @param sink           业务流写出端口（不直接碰 HttpServletResponse）
     * @return 成功/失败/中断等业务结果 DTO
     */
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
        // 安全：以认证用户覆盖请求体 userId，防止伪造
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

            // 时序：权限/限流前先落用户消息，保证限制场景也有完整对话记录
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

    /**
     * 校验聊天请求基础参数（messages / userId / modelId / 最后一条须为 user）。
     *
     * <p><b>内部引用</b>：{@link ChatRequestValidator#validateChatRequest}</p>
     *
     * @return null 表示通过；非 null 为错误文案
     */
    @Override
    public String validateChatRequest(ChatRequestDTO request) {
        return chatRequestValidator.validateChatRequest(request);
    }

    /**
     * 校验认证用户与请求 userId 是否一致。
     *
     * <p><b>内部引用</b>：{@link ChatRequestValidator#validateUserPermission}</p>
     *
     * @return null 表示通过；非 null 为错误文案
     */
    @Override
    public String validateUserPermission(Authentication authentication, Long userId) {
        return chatRequestValidator.validateUserPermission(authentication, userId);
    }

    /**
     * 保存用户消息（modelId 从会话推导）。
     *
     * <p><b>内部引用</b>：{@link ChatPersistenceService#saveUserMessage(List, String, Long)}</p>
     *
     * @return 保存后的 messageId；失败返回 null
     */
    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId) {
        return chatPersistenceService.saveUserMessage(messages, sessionId, userId);
    }

    /**
     * 保存用户消息（显式指定 modelId）。
     *
     * <p><b>内部引用</b>：{@link ChatPersistenceService#saveUserMessage(List, String, Long, Long)}</p>
     *
     * @return 保存后的 messageId；失败返回 null
     */
    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId, Long modelId) {
        return chatPersistenceService.saveUserMessage(messages, sessionId, userId, modelId);
    }

    /**
     * 保存完整 AI 响应（含 token 记录，不记模型次数）。
     *
     * <p><b>内部引用</b>：{@link ChatPersistenceService#saveAiResponse}
     * → {@code AiResponseCompletionHandler.persistAssistantContent}</p>
     */
    @Override
    public void saveAiResponse(String sessionId, Long userId, Long modelId, String content) {
        chatPersistenceService.saveAiResponse(sessionId, userId, modelId, content);
    }

    /**
     * 保存错误响应（status=3，不进入后续聊天上下文、不扣次）。
     *
     * <p><b>内部引用</b>：{@link ChatPersistenceService#saveErrorResponse}</p>
     */
    @Override
    public void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent) {
        chatPersistenceService.saveErrorResponse(sessionId, userId, modelId, errorContent);
    }

    /**
     * 判断是否为会话首次 AI 响应（仅 1 条 user 消息时需要生成标题）。
     *
     * <p><b>内部引用</b>：{@link ChatRequestValidator#isFirstAiResponse}</p>
     */
    @Override
    public boolean isFirstAiResponse(List<Map<String, String>> messages) {
        return chatRequestValidator.isFirstAiResponse(messages);
    }

    /**
     * 生成并落库会话标题（固定标题模型策略在 TitleService 内）。
     *
     * <p><b>内部引用</b>：{@link ChatTitleService#generateSessionTitle}</p>
     *
     * @return 生成标题；失败/不可用返回 null
     */
    @Override
    public String generateSessionTitle(String sessionId, List<Map<String, String>> messages) {
        return chatTitleService.generateSessionTitle(sessionId, messages);
    }

    /**
     * 将 filesId 对应文件内容拼进 messages（标题上下文不得走此污染路径）。
     *
     * <p><b>内部引用</b>：{@link ChatContextBuilder#processFilesContent}</p>
     */
    @Override
    public List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId) {
        return chatContextBuilder.processFilesContent(messages, filesId, userId);
    }

    /**
     * 保存部分 AI 响应（中断场景；可追加「[已中断]」并记 token）。
     *
     * <p><b>内部引用</b>：{@link ChatPersistenceService#savePartialAiResponse}</p>
     */
    @Override
    public void savePartialAiResponse(String sessionId, Long userId, Long modelId, String partialContent, boolean isInterrupted) {
        chatPersistenceService.savePartialAiResponse(sessionId, userId, modelId, partialContent, isInterrupted);
    }

    /**
     * 重新生成 AI 回复入口（retry）。
     *
     * <p><b>编排步骤</b>：
     * 会话校验(assistant) → <b>先删点后消息</b> → 限流 → Preparer 重建上下文 →
     * 校验模型 → 可选换模 → 套餐权限 → 共用 {@link #streamChatWithAI}(interfaceType=retry)
     * </p>
     *
     * <p><b>内部引用</b>：
     * {@link ChatSessionService#updateSessionUpdateTime} /
     * {@link ChatRequestValidator#validateSessionCommand} /
     * {@link ChatMessageService#deleteMessagesFromPoint} /
     * {@link ChatRequestValidator#validateChatRateLimit} /
     * {@link RetryPrepareHandler#prepareAfterDelete} /
     * {@link ChatRequestValidator#validateAndGetModel} /
     * {@link ChatPersistenceService#updateSessionModelIfNeeded} /
     * {@link ChatRequestValidator#validateChatPermission} /
     * {@link ChatRequestValidator#writePermissionFailure} /
     * {@link #buildCommandRequest} /
     * {@link #streamChatWithAI} /
     * {@link #writeCommandException}
     * </p>
     *
     * @param sessionId      会话 ID（前端 chatId）
     * @param messageId      目标 assistant 消息 ID（从此点起删除）
     * @param modelId        可选新模型；null 用会话原模型
     * @param network        是否联网；null 按 false
     * @param authentication 认证信息
     * @param sink           流写出端口
     */
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

            // 关键时序：先删后限流，避免错误响应重试时旧错误累加
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

    /**
     * 编辑用户消息并重新生成入口（edit-resend）。
     *
     * <p><b>编排步骤</b>：
     * 会话校验(user) → <b>先替换用户消息</b> → 限流 → 建上下文 →
     * 校验模型 → 可选换模 → 套餐权限 → 共用 {@link #streamChatWithAI}(interfaceType=edit-resend)
     * </p>
     *
     * <p><b>内部引用</b>：
     * {@link ChatSessionService#updateSessionUpdateTime} /
     * {@link ChatRequestValidator#validateSessionCommand} /
     * {@link EditResendPrepareHandler#replaceUserMessage} /
     * {@link ChatRequestValidator#validateChatRateLimit} /
     * {@link EditResendPrepareHandler#buildContext} /
     * {@link ChatRequestValidator#validateAndGetModel} /
     * {@link ChatPersistenceService#updateSessionModelIfNeeded} /
     * {@link ChatRequestValidator#validateChatPermission} /
     * {@link ChatRequestValidator#writePermissionFailure} /
     * {@link #buildCommandRequest} /
     * {@link #streamChatWithAI} /
     * {@link #writeCommandException}
     * </p>
     *
     * @param sessionId      会话 ID
     * @param messageId      目标 user 消息 ID
     * @param newContent     编辑后的用户内容
     * @param filesId        附件 ID 列表（逗号分隔，可空；由 Preparer 处理迁移）
     * @param modelId        可选新模型
     * @param network        是否联网
     * @param authentication 认证信息
     * @param sink           流写出端口
     */
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

            // 关键时序：先替换用户消息再限流，保证限制场景仍保留新编辑内容
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

    /**
     * 普通 chat 的流式执行包装：判断是否首轮标题，并处理 ContentPolicy 触发的有限次重试。
     *
     * <p><b>内部引用</b>：
     * {@link ChatRequestValidator#isFirstAiResponse} /
     * {@link #streamChatWithAI} /
     * {@link ContentPolicy.ChatRetryException} /
     * {@link #writeGenericStreamError}
     * </p>
     */
    private ChatResponseDTO executeAiStreamResponse(ChatRequestDTO request, AiModel model, ChatStreamSink sink) {
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount <= maxRetries) {
            try {
                boolean willGenerateTitle = request.getSessionId() != null
                        && chatRequestValidator.isFirstAiResponse(request.getMessages());
                return streamChatWithAI(request, model, sink, willGenerateTitle, "chat");
            } catch (ContentPolicy.ChatRetryException e) {
                // 上游返回可重试错误内容时，由 ContentPolicy 抛出；此处做有限次退避重试
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

    /**
     * 三入口共用的 AI 流式执行核。
     *
     * <p>把完成回调、错误落库、请求日志全部接到已提取组件，本类不再解析流或拼 SSE。</p>
     *
     * <p><b>内部引用</b>：
     * {@link AiStreamProcessor#stream} /
     * {@link StreamCompletionCoordinator#complete}（completion 回调）/
     * {@link ChatPersistenceService#saveErrorResponse}（error 落库回调）/
     * {@link AiRequestLogger#logAiRequestDetails}（请求日志回调）
     * </p>
     *
     * @param interfaceType 业务入口标识：chat / retry / edit-resend（影响截断检测等策略）
     */
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

    /**
     * 将 Preparer 产出写回 request，供流式/完成链路读取。
     *
     * <p>兼容期仍写入 DTO 运行时字段 {@code originalMessages}/{@code networkSearchResults}
     *（已 @Deprecated，标题/联网绑定仍依赖）。</p>
     *
     * <p><b>内部引用</b>：{@link PreparedTurn#getMessages} /
     * {@link PreparedTurn#getOriginalMessages} /
     * {@link PreparedTurn#getNetworkSearchResults}</p>
     */
    private void applyPreparedTurn(ChatRequestDTO request, PreparedTurn preparedTurn) {
        request.setMessages(preparedTurn.getMessages());
        request.setOriginalMessages(preparedTurn.getOriginalMessages());
        request.setNetworkSearchResults(preparedTurn.getNetworkSearchResults());
    }

    /**
     * 为 retry / edit-resend 组装进入流式核的 {@link ChatRequestDTO}。
     *
     * <p><b>内部引用</b>：{@link PreparedTurn} 的 messages / 联网结果 / 原始消息快照</p>
     */
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

    /**
     * 流式执行失败时的统一错误写出（只写流，不强制落库；落库由调用方或 completion 路径决定）。
     *
     * <p><b>内部引用</b>：{@link ChatStreamSink#writeError}</p>
     */
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

    /**
     * retry / edit-resend 顶层异常：写错误流，并尽量按会话模型落 status=3 错误消息。
     *
     * <p><b>内部引用</b>：
     * {@link ChatStreamSink#writeError} /
     * {@link ChatSessionService#getSessionInfo} /
     * {@link AiModelService#getById} /
     * {@link ChatPersistenceService#saveErrorResponse}
     * </p>
     */
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
