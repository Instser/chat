package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 旧版聊天控制器（兼容路径）。
 * <p>所有 userId 必须以 JWT 为准，禁止信任请求参数中的 userId 做越权操作。</p>
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
@CrossOrigin(originPatterns = {
    "http://localhost:*", "https://localhost:*",
    "http://127.0.0.1:*", "https://127.0.0.1:*",
    "http://192.168.1.100:*", "https://192.168.1.100:*",
    "http://192.168.1.101:*", "https://192.168.1.101:*",
    "https://chat.instser.cn", "http://chat.instser.cn",
    "http://203.0.113.16:3000"
}, allowedHeaders = "*", allowCredentials = "true")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;

    /**
     * 创建新会话 — 仅能为当前登录用户创建
     */
    @PostMapping("/session/create")
    public Result<String> createSession(@RequestParam(required = false) Long userId,
                                        @RequestParam(defaultValue = "1") Long modelId,
                                        Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        if (userId != null && !currentUserId.equals(userId)) {
            LogUtils.logUserWarning(log, currentUserId, "创建会话", "拒绝为他人创建会话，请求userId={}", userId);
            return Result.forbidden("无权为其他用户创建会话");
        }
        LogUtils.logUserOperation(log, currentUserId, "创建会话", "开始创建新会话，模型ID：{}", modelId);
        String sessionId = chatSessionService.createSession(currentUserId, modelId);
        LogUtils.logUserSessionOperation(log, currentUserId, sessionId, "创建会话", "会话创建成功");
        return Result.success(sessionId, "会话创建成功");
    }

    /**
     * 获取用户会话列表 — 仅能查自己
     */
    @GetMapping("/session/list")
    public Result<List<ChatSession>> getUserSessions(@RequestParam(required = false) Long userId,
                                                     Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        if (userId != null && !currentUserId.equals(userId)) {
            LogUtils.logUserWarning(log, currentUserId, "获取会话列表", "拒绝查询他人会话，请求userId={}", userId);
            return Result.forbidden("无权查询其他用户会话");
        }
        LogUtils.logUserOperation(log, currentUserId, "获取会话列表", "开始获取用户会话列表");
        List<ChatSession> sessions = chatSessionService.getUserSessions(currentUserId);
        LogUtils.logUserOperation(log, currentUserId, "获取会话列表", "获取会话列表成功，会话数量：{}", sessions.size());
        return Result.success(sessions);
    }

    /**
     * 获取会话信息 — 仅所有者
     */
    @GetMapping("/session/{sessionId}")
    public Result<ChatSession> getSessionInfo(@PathVariable @NotBlank String sessionId,
                                              Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        ChatSession session = chatSessionService.getSessionInfo(sessionId);
        if (session == null) {
            return Result.failed("会话不存在");
        }
        if (!currentUserId.equals(session.getUserId())) {
            return Result.forbidden("无权访问该会话");
        }
        return Result.success(session);
    }

    /**
     * 更新会话标题 — 仅所有者
     */
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateSessionTitle(@PathVariable @NotBlank String sessionId,
                                           @RequestBody Map<String, String> request,
                                           Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        Result<Void> ownership = assertSessionOwner(sessionId, currentUserId);
        if (ownership != null) {
            return ownership;
        }
        String title = request.get("title");
        if (title == null || title.trim().isEmpty()) {
            return Result.validateFailed("标题不能为空");
        }
        boolean updated = chatSessionService.updateSessionTitle(sessionId, title);
        if (!updated) {
            return Result.failed("标题更新失败");
        }
        return Result.success(null, "标题更新成功");
    }

    /**
     * 删除会话 — 仅所有者
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable @NotBlank String sessionId,
                                      Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        Result<Void> ownership = assertSessionOwner(sessionId, currentUserId);
        if (ownership != null) {
            return ownership;
        }
        boolean deleted = chatSessionService.deleteSession(sessionId);
        if (!deleted) {
            return Result.failed("会话删除失败");
        }
        return Result.success(null, "会话删除成功");
    }

    /**
     * 发送消息 — userId 以 JWT 为准
     */
    @PostMapping("/message/send")
    public Result<Long> sendMessage(@RequestBody Map<String, Object> request,
                                    Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        String sessionId = (String) request.get("sessionId");
        String content = (String) request.get("content");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.validateFailed("会话ID不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.validateFailed("消息内容不能为空");
        }
        if (request.get("userId") != null) {
            Long bodyUserId = Long.valueOf(request.get("userId").toString());
            if (!currentUserId.equals(bodyUserId)) {
                return Result.forbidden("无权以其他用户身份发送消息");
            }
        }
        Result<Void> ownership = assertSessionOwner(sessionId, currentUserId);
        if (ownership != null) {
            return Result.failed(ownership.getMessage());
        }

        Long messageId = chatMessageService.sendMessage(sessionId, content, currentUserId);
        chatSessionService.updateLastActiveTime(sessionId, content);
        return Result.success(messageId, "消息发送成功");
    }

    /**
     * 获取会话消息列表 — 仅所有者
     */
    @GetMapping("/message/list/{sessionId}")
    public Result<List<ChatMessage>> getSessionMessages(@PathVariable @NotBlank String sessionId,
                                                       Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        Result<Void> ownership = assertSessionOwner(sessionId, currentUserId);
        if (ownership != null) {
            return Result.failed(ownership.getMessage());
        }
        List<ChatMessage> messages = chatMessageService.getSessionMessages(sessionId);
        return Result.success(messages);
    }

    /**
     * 获取用户最近消息 — 仅能查自己
     */
    @GetMapping("/message/recent")
    public Result<List<ChatMessage>> getRecentMessages(@RequestParam(required = false) Long userId,
                                                        @RequestParam(defaultValue = "10") Integer limit,
                                                        Authentication authentication) {
        Long currentUserId = requireUserId(authentication);
        if (currentUserId == null) {
            return Result.unauthorized("用户未认证");
        }
        if (userId != null && !currentUserId.equals(userId)) {
            return Result.forbidden("无权查询其他用户消息");
        }
        List<ChatMessage> messages = chatMessageService.getRecentMessages(currentUserId, limit);
        return Result.success(messages);
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsServiceImpl.CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    /** @return null if ok; otherwise failed/forbidden result */
    private Result<Void> assertSessionOwner(String sessionId, Long currentUserId) {
        ChatSession session = chatSessionService.getSessionInfo(sessionId);
        if (session == null) {
            return Result.failed("会话不存在");
        }
        if (!currentUserId.equals(session.getUserId())) {
            LogUtils.logUserSessionWarning(log, currentUserId, sessionId, "API拒绝", "无权操作他人会话");
            return Result.forbidden("无权访问该会话");
        }
        return null;
    }
}
