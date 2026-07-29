package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatService;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import com.instser.chat.service.stream.ChatStreamSink;
import com.instser.chat.service.stream.StreamSubscriptionRegistry;
import com.instser.chat.service.stream.StreamTerminalReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import com.instser.chat.dto.request.DeleteSessionRequest;
import java.util.List;
import java.util.Map;

/**
 * 聊天API控制器 - 兼容前端接口
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {
    "http://localhost:*", "https://localhost:*",
    "http://127.0.0.1:*", "https://127.0.0.1:*",
    "http://192.168.1.100:*", "https://192.168.1.100:*",
    "http://192.168.1.101:*", "https://192.168.1.101:*",
    "https://chat.example.com", "http://chat.example.com",
    "http://203.0.113.16:3000"
}, allowedHeaders = "*", allowCredentials = "true")
public class ChatApiController {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final AiModelService aiModelService;
    private final ChatService chatService;
    private final ChatInterruptService chatInterruptService;
    private final ChatStreamEventWriter chatStreamEventWriter;
    private final StreamSubscriptionRegistry streamSubscriptionRegistry;

    /**
     * 流式聊天接口 - 兼容前端 (POST)
     *
     * <p>返回 {@link ResponseBodyEmitter} 使请求线程可在订阅启动后立即返回。
     * 注意：不能用 SseEmitter，否则会再包一层 data: 帧，破坏 Vercel AI data stream 协议。</p>
     */
    @PostMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter chat(@RequestBody Map<String, Object> request, Authentication authentication,
                                    HttpServletRequest httpRequest, HttpServletResponse response) {
        LogUtils.logUserOperation(log, getCurrentUserId(authentication), "API调用", "聊天接口，请求参数：{}", LogUtils.formatRequestParams(request));

        // 动态设置跨域头 + 流式协议头（必须在返回 emitter 前）
        setCorsHeaders(httpRequest, response);
        chatStreamEventWriter.setStreamResponseHeaders(response);
        response.setHeader("X-Accel-Buffering", "no");

        // 0L = 不由 emitter 主动超时；仍受 MVC/网关超时约束（见非阻塞方案 6.5）
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        ChatStreamSink sink = ChatStreamSink.of(emitter, chatStreamEventWriter);

        String sessionIdForCancel = null;
        try {
            ChatRequestDTO chatRequest = ChatRequestDTO.fromMap(request);
            sessionIdForCancel = chatRequest.getSessionId();
            final String sessionId = sessionIdForCancel;
            registerEmitterCancelHooks(emitter, sessionId, getCurrentUserId(authentication));

            LogUtils.logUserSessionDebug(log, chatRequest.getUserId(), chatRequest.getSessionId(), "聊天处理", "开始处理聊天请求(非阻塞)");

            // 准备段仍同步；流式段在 async sink 下立即返回，不阻塞 Tomcat worker
            ChatResponseDTO chatResponse = chatService.processStreamChat(chatRequest, authentication, sink);

            // 准备失败等同步错误：确保通道关闭；成功启动异步流时由 processor complete
            if (chatResponse != null && !chatResponse.isSuccess() && !chatResponse.isInterrupted()) {
                LogUtils.logUserSessionWarning(log, chatRequest.getUserId(), chatRequest.getSessionId(), "API响应",
                        "聊天请求准备/启动失败，错误：{}",
                        chatResponse.getErrorMessage() != null ? chatResponse.getErrorMessage() : "未知错误");
                try {
                    sink.complete();
                } catch (Exception ignored) {
                }
            } else {
                LogUtils.logUserSessionOperation(log, chatRequest.getUserId(), chatRequest.getSessionId(), "API响应",
                        "聊天流已启动(异步)，最终成功/失败见完成回调日志");
            }

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "API异常", "聊天接口异常：{}", e, e.getMessage());
            if (sessionIdForCancel != null) {
                streamSubscriptionRegistry.cancel(sessionIdForCancel);
            }
            try {
                sink.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
        return emitter;
    }

    /**
     * 获取可见模型列表
     */
    @GetMapping("/models")
    public Result<List<AiModel>> getModels() {
        List<AiModel> models = aiModelService.getVisibleModels();
        return Result.success(models);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public Result<Map<String, String>> createSession(@RequestBody Map<String, Object> request, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        String modelCode = (String) request.getOrDefault("model", "qwen3-235b-a22b");

        LogUtils.logUserOperation(log, userId, "API调用", "创建会话接口，模型：{}", modelCode);

        if (userId == null) {
            LogUtils.logUserWarning(log, null, "API拒绝", "用户未认证");
            return Result.unauthorized("用户未认证");
        }

        try {
            AiModel model = aiModelService.getModelByCode(modelCode);
            if (model == null) {
                LogUtils.logUserWarning(log, userId, "API拒绝", "指定的模型不存在：{}", modelCode);
                return Result.failed("指定的模型不存在");
            }

            LogUtils.logUserDebug(log, userId, "会话创建", "开始创建会话，模型ID：{}", model.getId());
            String sessionId = chatSessionService.createSession(userId, model.getId());

            LogUtils.logUserSessionOperation(log, userId, sessionId, "API响应", "会话创建成功");
            return Result.success(Map.of("sessionId", sessionId));
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "API异常", "创建会话失败，错误：{}", e, e.getMessage());
            return Result.failed("创建会话失败");
        }
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getSessions(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return Result.unauthorized("用户未认证");
        }

        List<ChatSession> sessions = chatSessionService.getUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatMessageService.getSessionMessages(sessionId);
        return Result.success(messages);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        boolean deleted = chatSessionService.deleteSession(sessionId);
        if (!deleted) {
            return Result.failed("会话删除失败");
        }
        return Result.success(null, "会话删除成功");
    }
@DeleteMapping("/sessions")
    public Result<Void> deleteSessions(@RequestBody DeleteSessionRequest request) {
        boolean allDeleted = chatSessionService.deleteSessions(request.getSessionIds());
        if (!allDeleted) {
            return Result.failed("部分会话删除失败");
        }
        return Result.success();
    }

    /**
     * 提取并验证modelId参数
     */
    private Long extractModelId(Map<String, Object> request, HttpServletResponse response) throws Exception {
        Object modelIdObj = request.get("modelId");
        if (modelIdObj == null) {
            return null;
        }

        if (modelIdObj instanceof Number) {
            return ((Number) modelIdObj).longValue();
        } else if (modelIdObj instanceof String) {
            try {
                return Long.parseLong((String) modelIdObj);
            } catch (NumberFormatException e) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 尝试保存错误响应到数据库
                try {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.isAuthenticated()) {
                        Long userId = getCurrentUserId(auth);
                        if (userId != null) {
                            // 由于没有chatId，这里无法保存到数据库，只记录日志
                            LogUtils.logUserWarning(log, userId, "参数验证", "modelId格式错误，错误值：{}", modelIdObj);
                        }
                    }
                } catch (Exception ex) {
                    LogUtils.logUserWarning(log, null, "参数验证", "处理modelId格式错误时获取用户信息失败", ex);
                }
                throw new Exception("modelId格式错误");
            }
        }
        return null;
    }

    /**
     * 提取并验证network参数
     */
    private Boolean extractNetwork(Map<String, Object> request) {
        Object networkObj = request.get("network");
        if (networkObj == null) {
            return false; // 默认禁用网络搜索
        }

        if (networkObj instanceof Boolean) {
            return (Boolean) networkObj;
        } else if (networkObj instanceof String) {
            return "true".equalsIgnoreCase((String) networkObj) || "1".equals(networkObj);
        } else if (networkObj instanceof Number) {
            return ((Number) networkObj).intValue() == 1;
        }

        return false; // 默认禁用网络搜索
    }

    /**
     * 验证基础参数
     */
    private boolean validateBasicParams(String chatId, String messageId, HttpServletResponse response) throws Exception {
        if (chatId == null || chatId.trim().isEmpty()) {
            String errorContent = "Request failed. Please try again later.";
            writeErrorResponse(response, errorContent);

            // 尝试保存错误响应到数据库（如果能获取到用户信息）
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    Long userId = getCurrentUserId(auth);
                    if (userId != null) {
                        chatService.saveErrorResponse(chatId, userId, 1L, errorContent);
                        LogUtils.logUserSessionOperation(log, userId, chatId, "参数验证", "参数验证失败错误响应已保存到数据库，原因：chatId为空");
                    }
                }
            } catch (Exception e) {
                LogUtils.logUserWarning(log, null, "参数验证", "保存参数验证错误响应失败", e);
            }
            return false;
        }

        if (messageId == null || messageId.trim().isEmpty()) {
            String errorContent = "Request failed. Please try again later.";
            writeErrorResponse(response, errorContent);

            // 尝试保存错误响应到数据库（如果能获取到用户信息）
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    Long userId = getCurrentUserId(auth);
                    if (userId != null) {
                        chatService.saveErrorResponse(chatId, userId, 1L, errorContent);
                        LogUtils.logUserSessionOperation(log, userId, chatId, "参数验证", "参数验证失败错误响应已保存到数据库，原因：messageId为空");
                    }
                }
            } catch (Exception e) {
                LogUtils.logUserWarning(log, null, "参数验证", "保存参数验证错误响应失败", e);
            }
            return false;
        }

        return true;
    }

    /**
     * 处理通用异常
     */
    private void handleCommonException(Exception e, String operation, HttpServletResponse response) {
        LogUtils.logUserError(log, null, "API异常", "{}接口异常", e, operation);
        try {
            writeErrorResponse(response, "Request failed. Please try again later.");
        } catch (Exception ex) {
            LogUtils.logUserError(log, null, "API异常", "发送错误响应失败", ex);
        }
    }

    /**
     * 重新生成AI回复
     */
    @PostMapping(value = "/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter retryChat(@RequestBody Map<String, Object> request, Authentication authentication,
                                         HttpServletRequest httpRequest, HttpServletResponse response) {
        LogUtils.logUserOperation(log, getCurrentUserId(authentication), "API调用", "收到重新生成请求：{}", request);

        setCorsHeaders(httpRequest, response);
        chatStreamEventWriter.setStreamResponseHeaders(response);
        response.setHeader("X-Accel-Buffering", "no");

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        ChatStreamSink sink = ChatStreamSink.of(emitter, chatStreamEventWriter);
        String sessionIdForCancel = null;

        try {
            String chatId = (String) request.get("chatId");
            String messageId = (String) request.get("messageId");
            Long modelId = extractModelId(request, response);
            Boolean network = extractNetwork(request);
            sessionIdForCancel = chatId;

            if (!validateBasicParams(chatId, messageId, response)) {
                try {
                    sink.writeError("Request failed. Please try again later.");
                } catch (Exception ignored) {
                }
                sink.complete();
                return emitter;
            }

            final String sessionId = chatId;
            final Long userId = getCurrentUserId(authentication);
            registerEmitterCancelHooks(emitter, sessionId, userId);

            chatService.processRetryChat(chatId, messageId, modelId, network, authentication, sink);
            // retry 为 void：准备失败时 Service 已写错误帧；成功启动后由 processor complete

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "API异常", "重新生成接口异常：{}", e, e.getMessage());
            if (sessionIdForCancel != null) {
                streamSubscriptionRegistry.cancel(sessionIdForCancel);
            }
            try {
                sink.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
        return emitter;
    }

    /**
     * 编辑用户消息并重新生成AI回复
     */
    @PostMapping(value = "/edit-resend", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter editResendChat(@RequestBody Map<String, Object> request, Authentication authentication,
                                              HttpServletRequest httpRequest, HttpServletResponse response) {
        LogUtils.logUserOperation(log, getCurrentUserId(authentication), "API调用", "收到编辑重发请求：{}", request);

        setCorsHeaders(httpRequest, response);
        chatStreamEventWriter.setStreamResponseHeaders(response);
        response.setHeader("X-Accel-Buffering", "no");

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        ChatStreamSink sink = ChatStreamSink.of(emitter, chatStreamEventWriter);
        String sessionIdForCancel = null;

        try {
            String chatId = (String) request.get("chatId");
            String messageId = (String) request.get("messageId");
            String newContent = (String) request.get("newContent");
            String filesId = (String) request.get("filesId");
            Long modelId = extractModelId(request, response);
            Boolean network = extractNetwork(request);
            sessionIdForCancel = chatId;

            if (!validateBasicParams(chatId, messageId, response)) {
                try {
                    sink.writeError("Request failed. Please try again later.");
                } catch (Exception ignored) {
                }
                sink.complete();
                return emitter;
            }

            if (newContent == null || newContent.trim().isEmpty()) {
                String errorContent = "Request failed. Please try again later.";
                try {
                    sink.writeError(errorContent);
                } catch (Exception ignored) {
                }
                try {
                    Long userId = getCurrentUserId(authentication);
                    if (userId != null) {
                        chatService.saveErrorResponse(chatId, userId, 1L, errorContent);
                        LogUtils.logUserSessionOperation(log, userId, chatId, "参数验证", "编辑重发参数验证失败错误响应已保存到数据库，原因：newContent为空");
                    }
                } catch (Exception e) {
                    LogUtils.logUserWarning(log, null, "参数验证", "保存编辑重发参数验证错误响应失败", e);
                }
                sink.complete();
                return emitter;
            }

            final String sessionId = chatId;
            final Long userId = getCurrentUserId(authentication);
            registerEmitterCancelHooks(emitter, sessionId, userId);

            chatService.processEditResendChat(chatId, messageId, newContent, filesId, modelId, network, authentication, sink);

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "API异常", "编辑重发接口异常：{}", e, e.getMessage());
            if (sessionIdForCancel != null) {
                streamSubscriptionRegistry.cancel(sessionIdForCancel);
            }
            try {
                sink.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
        return emitter;
    }

    private void registerEmitterCancelHooks(ResponseBodyEmitter emitter, String sessionId, Long userId) {
        emitter.onCompletion(() -> {
            // 正常 complete 时 registry 多半已 remove；cancel 幂等
            if (sessionId != null) {
                streamSubscriptionRegistry.cancel(sessionId, StreamTerminalReason.CANCELLED);
            }
        });
        emitter.onTimeout(() -> {
            LogUtils.logUserSessionWarning(log, userId, sessionId, "API超时", "流 emitter 超时");
            if (sessionId != null) {
                streamSubscriptionRegistry.cancel(sessionId, StreamTerminalReason.TIMEOUT);
                if (userId != null) {
                    chatInterruptService.interruptChat(sessionId, userId);
                }
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(ex -> {
            LogUtils.logUserSessionWarning(log, userId, sessionId, "API通道错误", "emitter error: {}",
                    ex != null ? ex.getMessage() : "null");
            if (sessionId != null) {
                StreamTerminalReason reason = StreamTerminalReason.FAILURE;
                if (ex instanceof java.io.IOException) {
                    reason = StreamTerminalReason.WRITE_FAILED;
                }
                streamSubscriptionRegistry.cancel(sessionId, reason);
            }
        });
    }

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            // 检查是否是CustomUserDetails类型
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) principal;
                return userDetails.getUserId();
            }
            // 如果不是CustomUserDetails，返回null表示认证失败
            LogUtils.logUserWarning(log, null, "认证", "认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "认证", "获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 写入错误响应（委托统一 SSE Writer）
     */
    private void writeErrorResponse(HttpServletResponse response, String errorMessage) {
        try {
            chatStreamEventWriter.setStreamResponseHeaders(response);
            chatStreamEventWriter.writeErrorResponse(response, errorMessage);
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "API响应", "写入错误响应失败", e);
        }
    }

    /**
     * 动态设置CORS跨域头
     */
    private void setCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");

        // 允许的源列表
        String[] allowedOrigins = {
            "http://localhost:3000",
            "http://localhost:3001",
            "https://localhost:3000",
            "https://localhost:3001",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3001",
            "https://127.0.0.1:3000",
            "https://127.0.0.1:3001",
            "http://192.168.1.100:3000",
            "http://192.168.1.100:3001",
            "https://192.168.1.100:3000",
            "https://192.168.1.100:3001",
            "http://192.168.1.101:3000",
            "http://192.168.1.101:3001",
            "https://192.168.1.101:3000",
            "https://192.168.1.101:3001",
            "https://chat.example.com",
            "http://chat.example.com",
            "http://203.0.113.16:3000"
        };

        // 检查请求来源是否在允许列表中
        if (origin != null) {
            for (String allowedOrigin : allowedOrigins) {
                if (origin.equals(allowedOrigin) || origin.startsWith(allowedOrigin.substring(0, allowedOrigin.lastIndexOf(':')))) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", "*");
                    return;
                }
            }
        }

        // 如果不在允许列表中，设置为localhost默认值
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
    }

    /**
     * 中断聊天接口
     */
    @PostMapping("/stop")
    public Result<String> stopChat(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            // 提取参数
            String sessionId = (String) request.get("sessionId");
            Long userId = getCurrentUserId(authentication);

            // 参数验证
            if (sessionId == null || sessionId.trim().isEmpty()) {
                LogUtils.logUserWarning(log, userId, "参数验证", "中断聊天请求参数错误：sessionId为空");
                return Result.validateFailed("会话ID不能为空");
            }

            if (userId == null) {
                LogUtils.logUserWarning(log, null, "认证", "中断聊天请求认证失败：无法获取用户ID");
                return Result.unauthorized("用户认证失败");
            }

            LogUtils.logUserSessionOperation(log, userId, sessionId, "API调用", "请求中断聊天会话");

            // 执行中断操作：业务标志 + 取消上游订阅（状态机：stop）
            boolean success = chatInterruptService.interruptChat(sessionId, userId);
            streamSubscriptionRegistry.cancel(sessionId, StreamTerminalReason.INTERRUPTED);

            if (success) {
                LogUtils.logUserSessionOperation(log, userId, sessionId, "API响应", "成功中断聊天会话");
                return Result.success("聊天已中断");
            } else {
                LogUtils.logUserSessionWarning(log, userId, sessionId, "API响应", "中断聊天会话失败");
                return Result.failed("中断失败，会话可能已完成或不存在");
            }

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "API异常", "中断聊天请求处理异常", e);
            return Result.failed("系统错误，请稍后重试");
        }
    }

    /**
     * 获取活跃会话状态（调试用）
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getChatStatus(Authentication authentication) {
        try {
            int activeSessionCount = chatInterruptService.getActiveSessionCount();

            Map<String, Object> status = Map.of(
                "activeSessionCount", activeSessionCount,
                "timestamp", System.currentTimeMillis()
            );

            return Result.success(status, "获取状态成功");

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "API异常", "获取聊天状态异常", e);
            return Result.failed("获取状态失败");
        }
    }
}