package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.vo.converter.VOConverter;

import com.instser.chat.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端兼容控制器
 * 提供前端API文档中缺失的接口，保持后端Result响应格式
 *
 * @author instser
 * @since 2025-06-14
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class FrontendCompatController {

    private final ApiController apiController;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        UserDetailsServiceImpl.CustomUserDetails userDetails =
            (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    // ==================== 聊天管理相关接口 ====================

    /**
     * 获取聊天列表 - 支持分页
     */
    @GetMapping("/chats")
    public Result<Map<String, Object>> getChats(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            Authentication authentication) {

        // 调用原有接口获取数据
        Result<List<ChatSessionVO>> result = apiController.getChats(authentication);

        if (result.getCode() == 200) {
            List<ChatSessionVO> allChats = result.getData();

            // 实现分页逻辑
            int start = Math.min(offset, allChats.size());
            int end = Math.min(offset + limit, allChats.size());
            List<ChatSessionVO> pagedChats = allChats.subList(start, end);

            // 构建分页响应
            Map<String, Object> response = new HashMap<>();
            response.put("chats", pagedChats);
            response.put("total", allChats.size());
            response.put("hasMore", end < allChats.size());

            return Result.success(response, "获取聊天列表成功");
        } else {
            return Result.failed(result.getMessage());
        }
    }

    /**
     * AI自动生成聊天标题
     */
    @PostMapping("/chats/{chatId}/generate-title")
    public Result<Map<String, Object>> generateTitle(
            @PathVariable @NotBlank String chatId,
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

        // 获取聊天消息
        List<ChatMessage> messages = chatMessageService.getSessionMessages(chatId);
        if (messages.isEmpty()) {
            return Result.failed("没有消息可生成标题");
        }

        // 简单的标题生成逻辑（基于第一条用户消息）
        String generatedTitle = generateTitleFromMessages(messages);

        // 更新聊天标题
        chatSessionService.updateSessionTitle(chatId, generatedTitle);

        // 获取更新后的聊天信息
        ChatSession updatedSession = chatSessionService.getSessionInfo(chatId);
        ChatSessionVO chatSessionVO = VOConverter.toChatSessionVO(updatedSession, null);

        Map<String, Object> response = new HashMap<>();
        response.put("chat", chatSessionVO);
        response.put("generatedTitle", generatedTitle);

        return Result.success(response, "标题生成成功");
    }

    // ==================== 消息管理相关接口 ====================

    /**
     * 获取聊天消息 - 支持分页
     */
    @GetMapping("/chats/{chatId}/messages")
    public Result<Map<String, Object>> getChatMessages(
            @PathVariable @NotBlank String chatId,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            Authentication authentication) {

        // 调用原有接口获取数据（传递分页参数）
        Result<Map<String, Object>> result = apiController.getChatMessages(chatId, limit, offset, authentication);

        return result;
    }

    /**
     * 更新消息内容
     */
    @PutMapping("/chats/{chatId}/messages/{messageId}")
    public Result<Void> updateMessage(
            @PathVariable @NotBlank String chatId,
            @PathVariable @NotBlank String messageId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        String content = (String) request.get("content");

        if (content == null || content.trim().isEmpty()) {
            return Result.validateFailed("消息内容不能为空");
        }

        // 先验证聊天是否属于当前用户
        ChatSession chatSession = chatSessionService.getSessionInfo(chatId);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        // 获取消息并验证
        ChatMessage message = chatMessageService.getById(Long.valueOf(messageId));
        if (message == null) {
            return Result.failed("消息不存在");
        }
        if (!message.getSessionId().equals(chatId)) {
            return Result.failed("消息不属于此聊天");
        }
        if (!"user".equals(message.getRole())) {
            return Result.failed("只能编辑用户消息");
        }

        // 更新消息内容
        message.setContent(content);
        message.setUpdateTime(java.time.LocalDateTime.now());
        chatMessageService.updateById(message);

        return Result.success(null, "消息更新成功");
    }

    /**
     * 从消息中生成标题
     */
    private String generateTitleFromMessages(List<ChatMessage> messages) {
        // 找到第一条用户消息
        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole()) && message.getContent() != null) {
                String content = message.getContent().trim();
                // 截取前30个字符作为标题
                if (content.length() > 30) {
                    return content.substring(0, 30) + "...";
                } else {
                    return content;
                }
            }
        }
        return "新对话";
    }
}
