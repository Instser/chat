package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

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

    /**
     * 流式聊天接口 - 兼容前端 (POST)
     */
    @PostMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chat(@RequestBody Map<String, Object> request, Authentication authentication,
                     HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("收到聊天请求：{}, 认证信息：{}", request, authentication);

        // 动态设置跨域头
        setCorsHeaders(httpRequest, response);

        try {
            // 将请求Map转换为DTO
            ChatRequestDTO chatRequest = ChatRequestDTO.fromMap(request);

            // 调用ChatService处理业务逻辑
            ChatResponseDTO chatResponse = chatService.processStreamChat(chatRequest, authentication, response);

            // 记录处理结果
            if (chatResponse.isSuccess()) {
                log.info("用户{}聊天请求处理成功", chatRequest.getUserId());
            } else {
                log.warn("用户{}聊天请求处理失败: {}", chatRequest.getUserId(), chatResponse.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("聊天接口异常", e);
            // 异常情况下的错误处理已在ChatService中完成
        }
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
        if (userId == null) {
            return Result.unauthorized("用户未认证");
        }

        String modelCode = (String) request.getOrDefault("model", "qwen3-235b-a22b");

        AiModel model = aiModelService.getModelByCode(modelCode);
        if (model == null) {
            return Result.failed("指定的模型不存在");
        }

        String sessionId = chatSessionService.createSession(userId, model.getId());
        return Result.success(Map.of("sessionId", sessionId));
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
                            log.warn("modelId格式错误，用户ID：{}，错误值：{}", userId, modelIdObj);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("处理modelId格式错误时获取用户信息失败", ex);
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
                        log.info("参数验证失败错误响应已保存到数据库，会话ID：{}，用户ID：{}，原因：chatId为空", chatId, userId);
                    }
                }
            } catch (Exception e) {
                log.warn("保存参数验证错误响应失败", e);
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
                        log.info("参数验证失败错误响应已保存到数据库，会话ID：{}，用户ID：{}，原因：messageId为空", chatId, userId);
                    }
                }
            } catch (Exception e) {
                log.warn("保存参数验证错误响应失败", e);
            }
            return false;
        }

        return true;
    }

    /**
     * 处理通用异常
     */
    private void handleCommonException(Exception e, String operation, HttpServletResponse response) {
        log.error("{}接口异常", operation, e);
        try {
            writeErrorResponse(response, "Request failed. Please try again later.");
        } catch (Exception ex) {
            log.error("发送错误响应失败", ex);
        }
    }

    /**
     * 重新生成AI回复
     */
    @PostMapping(value = "/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void retryChat(@RequestBody Map<String, Object> request, Authentication authentication,
                         HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("收到重新生成请求：{}, 认证信息：{}", request, authentication);

        // 动态设置跨域头
        setCorsHeaders(httpRequest, response);

        try {
            // 提取参数 - 前端传入chatId，后端映射为sessionId
            String chatId = (String) request.get("chatId");
            String messageId = (String) request.get("messageId");
            Long modelId = extractModelId(request, response);
            Boolean network = extractNetwork(request);

            // 验证基础参数
            if (!validateBasicParams(chatId, messageId, response)) {
                return;
            }

            // 调用ChatService处理重新生成逻辑（chatId实际对应sessionId）
            chatService.processRetryChat(chatId, messageId, modelId, network, authentication, response);

        } catch (Exception e) {
            handleCommonException(e, "重新生成", response);
        }
    }

    /**
     * 编辑用户消息并重新生成AI回复
     */
    @PostMapping(value = "/edit-resend", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void editResendChat(@RequestBody Map<String, Object> request, Authentication authentication,
                              HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("收到编辑重发请求：{}, 认证信息：{}", request, authentication);

        // 动态设置跨域头
        setCorsHeaders(httpRequest, response);

        try {
            // 提取参数 - 前端传入chatId，后端映射为sessionId
            String chatId = (String) request.get("chatId");
            String messageId = (String) request.get("messageId");
            String newContent = (String) request.get("newContent");
            String filesId = (String) request.get("filesId");
            Long modelId = extractModelId(request, response);
            Boolean network = extractNetwork(request);

            // 验证基础参数
            if (!validateBasicParams(chatId, messageId, response)) {
                return;
            }

            if (newContent == null || newContent.trim().isEmpty()) {
                String errorContent = "Request failed. Please try again later.";
                writeErrorResponse(response, errorContent);

                // 尝试保存错误响应到数据库
                try {
                    Long userId = getCurrentUserId(authentication);
                    if (userId != null) {
                        chatService.saveErrorResponse(chatId, userId, 1L, errorContent);
                        log.info("编辑重发参数验证失败错误响应已保存到数据库，会话ID：{}，用户ID：{}，原因：newContent为空", chatId, userId);
                    }
                } catch (Exception e) {
                    log.warn("保存编辑重发参数验证错误响应失败", e);
                }
                return;
            }

            // 调用ChatService处理编辑重发逻辑（chatId实际对应sessionId）
            chatService.processEditResendChat(chatId, messageId, newContent, filesId, modelId, network, authentication, response);

        } catch (Exception e) {
            handleCommonException(e, "编辑重发", response);
        }
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
            log.warn("认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, String errorMessage) {
        try {
            response.setContentType("text/event-stream; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.getWriter().write("3:\"" + errorMessage + "\"\n");
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("写入错误响应失败", e);
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
                log.warn("中断聊天请求参数错误：sessionId为空");
                return Result.validateFailed("会话ID不能为空");
            }

            if (userId == null) {
                log.warn("中断聊天请求认证失败：无法获取用户ID");
                return Result.unauthorized("用户认证失败");
            }

            log.info("用户{}请求中断聊天会话：sessionId={}", userId, sessionId);

            // 执行中断操作
            boolean success = chatInterruptService.interruptChat(sessionId, userId);

            if (success) {
                log.info("用户{}成功中断聊天会话：sessionId={}", userId, sessionId);
                return Result.success("聊天已中断");
            } else {
                log.warn("用户{}中断聊天会话失败：sessionId={}", userId, sessionId);
                return Result.failed("中断失败，会话可能已完成或不存在");
            }

        } catch (Exception e) {
            log.error("中断聊天请求处理异常", e);
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
            log.error("获取聊天状态异常", e);
            return Result.failed("获取状态失败");
        }
    }
}