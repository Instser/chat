package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 聊天控制器
 *
 * @author instser
 * @since 2024-01-01
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
    "https://chat.example.com", "http://chat.example.com",
    "http://203.0.113.16:3000"
}, allowedHeaders = "*", allowCredentials = "true")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;

    /**
     * 创建新会话
     */
    @PostMapping("/session/create")
    public Result<String> createSession(@RequestParam @NotNull Long userId,
                                        @RequestParam(defaultValue = "1") Long modelId) {
        LogUtils.logUserOperation(log, userId, "创建会话", "开始创建新会话，模型ID：{}", modelId);
        String sessionId = chatSessionService.createSession(userId, modelId);
        LogUtils.logUserSessionOperation(log, userId, sessionId, "创建会话", "会话创建成功");
        return Result.success(sessionId, "会话创建成功");
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/session/list")
    public Result<List<ChatSession>> getUserSessions(@RequestParam @NotNull Long userId) {
        LogUtils.logUserOperation(log, userId, "获取会话列表", "开始获取用户会话列表");
        List<ChatSession> sessions = chatSessionService.getUserSessions(userId);
        LogUtils.logUserOperation(log, userId, "获取会话列表", "获取会话列表成功，会话数量：{}", sessions.size());
        return Result.success(sessions);
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/session/{sessionId}")
    public Result<ChatSession> getSessionInfo(@PathVariable @NotBlank String sessionId) {
        LogUtils.logUserSessionOperation(log, null, sessionId, "获取会话信息", "开始获取会话信息");
        ChatSession session = chatSessionService.getSessionInfo(sessionId);
        if (session == null) {
            LogUtils.logUserSessionWarning(log, null, sessionId, "获取会话信息", "会话不存在");
            return Result.failed("会话不存在");
        }
        LogUtils.logUserSessionOperation(log, session.getUserId(), sessionId, "获取会话信息", "获取会话信息成功");
        return Result.success(session);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateSessionTitle(@PathVariable @NotBlank String sessionId,
                                           @RequestBody Map<String, String> request) {
        String title = request.get("title");
        LogUtils.logUserSessionOperation(log, null, sessionId, "更新会话标题", "开始更新会话标题：{}", title);
        if (title == null || title.trim().isEmpty()) {
            LogUtils.logUserSessionWarning(log, null, sessionId, "更新会话标题", "标题不能为空");
            return Result.validateFailed("标题不能为空");
        }
        boolean updated = chatSessionService.updateSessionTitle(sessionId, title);
        if (!updated) {
            LogUtils.logUserSessionWarning(log, null, sessionId, "更新会话标题", "标题更新失败");
            return Result.failed("标题更新失败");
        }
        LogUtils.logUserSessionOperation(log, null, sessionId, "更新会话标题", "标题更新成功");
        return Result.success(null, "标题更新成功");
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable @NotBlank String sessionId) {
        LogUtils.logUserSessionOperation(log, null, sessionId, "删除会话", "开始删除会话");
        boolean deleted = chatSessionService.deleteSession(sessionId);
        if (!deleted) {
            LogUtils.logUserSessionWarning(log, null, sessionId, "删除会话", "会话删除失败");
            return Result.failed("会话删除失败");
        }
        LogUtils.logUserSessionOperation(log, null, sessionId, "删除会话", "会话删除成功");
        return Result.success(null, "会话删除成功");
    }

    /**
     * 发送消息
     */
    @PostMapping("/message/send")
    public Result<Long> sendMessage(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String content = (String) request.get("content");
        Long userId = Long.valueOf(request.get("userId").toString());

        LogUtils.logUserSessionOperation(log, userId, sessionId, "发送消息", "开始发送消息，内容长度：{}", content != null ? content.length() : 0);

        if (sessionId == null || sessionId.trim().isEmpty()) {
            LogUtils.logUserWarning(log, userId, "发送消息", "会话ID不能为空");
            return Result.validateFailed("会话ID不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            LogUtils.logUserSessionWarning(log, userId, sessionId, "发送消息", "消息内容不能为空");
            return Result.validateFailed("消息内容不能为空");
        }
        if (userId == null) {
            LogUtils.logUserSessionWarning(log, null, sessionId, "发送消息", "用户ID不能为空");
            return Result.validateFailed("用户ID不能为空");
        }

        Long messageId = chatMessageService.sendMessage(sessionId, content, userId);

        // 更新会话最后活跃时间
        chatSessionService.updateLastActiveTime(sessionId, content);

        LogUtils.logUserSessionOperation(log, userId, sessionId, "发送消息", "消息发送成功，消息ID：{}", messageId);
        return Result.success(messageId, "消息发送成功");
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/message/list/{sessionId}")
    public Result<List<ChatMessage>> getSessionMessages(@PathVariable @NotBlank String sessionId) {
        LogUtils.logUserSessionOperation(log, null, sessionId, "获取会话消息", "开始获取会话消息列表");
        List<ChatMessage> messages = chatMessageService.getSessionMessages(sessionId);
        LogUtils.logUserSessionOperation(log, null, sessionId, "获取会话消息", "获取会话消息列表成功，消息数量：{}", messages.size());
        return Result.success(messages);
    }

    /**
     * 获取用户最近消息
     */
    @GetMapping("/message/recent")
    public Result<List<ChatMessage>> getRecentMessages(@RequestParam @NotNull Long userId,
                                                        @RequestParam(defaultValue = "10") Integer limit) {
        LogUtils.logUserOperation(log, userId, "获取最近消息", "开始获取用户最近消息，限制数量：{}", limit);
        List<ChatMessage> messages = chatMessageService.getRecentMessages(userId, limit);
        LogUtils.logUserOperation(log, userId, "获取最近消息", "获取用户最近消息成功，消息数量：{}", messages.size());
        return Result.success(messages);
    }
}
