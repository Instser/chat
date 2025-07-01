package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
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
        String sessionId = chatSessionService.createSession(userId, modelId);
        return Result.success(sessionId, "会话创建成功");
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/session/list")
    public Result<List<ChatSession>> getUserSessions(@RequestParam @NotNull Long userId) {
        List<ChatSession> sessions = chatSessionService.getUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/session/{sessionId}")
    public Result<ChatSession> getSessionInfo(@PathVariable @NotBlank String sessionId) {
        ChatSession session = chatSessionService.getSessionInfo(sessionId);
        if (session == null) {
            return Result.failed("会话不存在");
        }
        return Result.success(session);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateSessionTitle(@PathVariable @NotBlank String sessionId,
                                           @RequestBody Map<String, String> request) {
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
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable @NotBlank String sessionId) {
        boolean deleted = chatSessionService.deleteSession(sessionId);
        if (!deleted) {
            return Result.failed("会话删除失败");
        }
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

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.validateFailed("会话ID不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.validateFailed("消息内容不能为空");
        }
        if (userId == null) {
            return Result.validateFailed("用户ID不能为空");
        }

        Long messageId = chatMessageService.sendMessage(sessionId, content, userId);

        // 更新会话最后活跃时间
        chatSessionService.updateLastActiveTime(sessionId, content);

        return Result.success(messageId, "消息发送成功");
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/message/list/{sessionId}")
    public Result<List<ChatMessage>> getSessionMessages(@PathVariable @NotBlank String sessionId) {
        List<ChatMessage> messages = chatMessageService.getSessionMessages(sessionId);
        return Result.success(messages);
    }

    /**
     * 获取用户最近消息
     */
    @GetMapping("/message/recent")
    public Result<List<ChatMessage>> getRecentMessages(@RequestParam @NotNull Long userId,
                                                        @RequestParam(defaultValue = "10") Integer limit) {
        List<ChatMessage> messages = chatMessageService.getRecentMessages(userId, limit);
        return Result.success(messages);
    }
}
