package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息管理控制器
 * 处理消息删除等管理功能
 *
 * @author instser
 * @since 2025-06-14
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class MessageManagementController {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        UserDetailsServiceImpl.CustomUserDetails userDetails =
            (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    /**
     * 批量删除消息
     */
    @PostMapping("/delete")
    public Result<Map<String, Object>> batchDeleteMessages(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        @SuppressWarnings("unchecked")
        List<String> messageIds = (List<String>) request.get("messageIds");
        String chatId = (String) request.get("chatId");

        if (messageIds == null || messageIds.isEmpty()) {
            return Result.validateFailed("消息ID列表不能为空");
        }
        if (chatId == null || chatId.trim().isEmpty()) {
            return Result.validateFailed("聊天ID不能为空");
        }

        // 先验证聊天是否属于当前用户
        ChatSession chatSession = chatSessionService.getSessionInfo(chatId);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        // 验证所有消息都属于该聊天
        List<Long> messageIdLongs = messageIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        List<ChatMessage> messages = chatMessageService.listByIds(messageIdLongs);
        for (ChatMessage message : messages) {
            if (!message.getSessionId().equals(chatId)) {
                return Result.failed("部分消息不属于此聊天");
            }
        }

        // 执行批量删除
        boolean deleted = chatMessageService.removeByIds(messageIdLongs);
        if (!deleted) {
            return Result.failed("删除消息失败");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deletedCount", messages.size());

        log.info("批量删除消息成功，聊天ID：{}，删除数量：{}", chatId, messages.size());
        return Result.success(response, "批量删除消息成功");
    }

    /**
     * 删除指定消息及之后的消息
     */
    @DeleteMapping("/delete")
    public Result<Map<String, Object>> deleteMessagesFromPoint(
            @RequestParam @NotBlank String messageId,
            @RequestParam @NotBlank String chatId,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        // 先验证聊天是否属于当前用户
        ChatSession chatSession = chatSessionService.getSessionInfo(chatId);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        // 获取指定消息
        ChatMessage targetMessage = chatMessageService.getById(Long.valueOf(messageId));
        if (targetMessage == null) {
            return Result.failed("消息不存在");
        }
        if (!targetMessage.getSessionId().equals(chatId)) {
            return Result.failed("消息不属于此聊天");
        }

        // 获取该聊天的所有消息
        List<ChatMessage> allMessages = chatMessageService.getSessionMessages(chatId);

        // 找到目标消息的位置，删除该消息及之后的所有消息
        boolean foundTarget = false;
        List<Long> messagesToDelete = new java.util.ArrayList<>();

        for (ChatMessage message : allMessages) {
            if (message.getId().equals(Long.valueOf(messageId))) {
                foundTarget = true;
            }
            if (foundTarget) {
                messagesToDelete.add(message.getId());
            }
        }

        if (messagesToDelete.isEmpty()) {
            return Result.failed("未找到要删除的消息");
        }

        // 执行删除
        boolean deleted = chatMessageService.removeByIds(messagesToDelete);
        if (!deleted) {
            return Result.failed("删除消息失败");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deletedCount", messagesToDelete.size());

        log.info("范围删除消息成功，聊天ID：{}，起始消息ID：{}，删除数量：{}",
                chatId, messageId, messagesToDelete.size());
        return Result.success(response, "范围删除消息成功");
    }
}
