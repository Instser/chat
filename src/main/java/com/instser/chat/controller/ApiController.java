package com.instser.chat.controller;

import com.instser.chat.common.exception.WebSearchException;
import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.ErrorResponseUtil;
import com.instser.chat.dto.UpdateWebSearchContentRequest;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.vo.AiModelVO;
import com.instser.chat.vo.ChatMessageVO;
import com.instser.chat.vo.ChatSessionVO;
import com.instser.chat.vo.converter.VOConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.*;

/**
 * 前端API控制器 - 使用VO响应
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("")
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
public class ApiController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final AiModelService aiModelService;
    private final FileUploadService fileUploadService;
    private final WebSearchContentService webSearchContentService;

    // ==================== 认证相关接口已迁移到AuthController ====================

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        UserDetailsServiceImpl.CustomUserDetails userDetails =
            (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    // ==================== 聊天会话相关接口 ====================

    /**
     * 获取用户的聊天列表 - 默认分页20条记录
     */
    @GetMapping("/chats")
    public Result<Map<String, Object>> getChats(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        List<ChatSession> allChatSessions = chatSessionService.getUserSessions(userId);
        List<ChatSessionVO> allChatSessionVOs = VOConverter.toChatSessionVOList(allChatSessions);

        // 设置默认分页参数，默认每页20条记录
        int actualLimit = limit != null ? limit : 20;
        int actualOffset = offset != null ? offset : 0;

        int start = Math.min(actualOffset, allChatSessionVOs.size());
        int end = Math.min(actualOffset + actualLimit, allChatSessionVOs.size());
        List<ChatSessionVO> pagedChats = allChatSessionVOs.subList(start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("chats", pagedChats);
        response.put("total", allChatSessionVOs.size());
        response.put("hasMore", end < allChatSessionVOs.size());

        return Result.success(response, "获取聊天列表成功");
    }

    /**
     * 创建新聊天
     */
    @PostMapping("/chats")
    public Result<ChatSessionVO> createChat(@RequestBody Map<String, Object> request,
                                           Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        String title = (String) request.getOrDefault("title", "新对话");
        Long modelId = Long.valueOf(request.getOrDefault("modelId", "1").toString());

        String sessionId = chatSessionService.createSession(userId, modelId);
        ChatSession chatSession = chatSessionService.getSessionInfo(sessionId);

        if (chatSession == null) {
            return Result.failed("创建聊天失败");
        }

        // 如果提供了自定义标题，则更新会话标题
        if (!title.equals("新对话")) {
            chatSessionService.updateSessionTitle(sessionId, title);
            chatSession = chatSessionService.getSessionInfo(sessionId); // 重新获取更新后的会话信息
        }

        // 获取模型信息
        AiModel aiModel = aiModelService.getById(chatSession.getModelId());
        ChatSessionVO chatSessionVO = VOConverter.toChatSessionVO(chatSession, aiModel);

        return Result.success(chatSessionVO, "创建聊天成功");
    }

    /**
     * 获取指定聊天信息
     */
    @GetMapping("/chats/{id}")
    public Result<ChatSessionVO> getChat(@PathVariable @NotBlank String id,
                                        Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        ChatSession chatSession = chatSessionService.getSessionInfo(id);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }

        // 验证聊天是否属于当前用户
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        // 获取模型信息
        AiModel aiModel = aiModelService.getById(chatSession.getModelId());
        ChatSessionVO chatSessionVO = VOConverter.toChatSessionVO(chatSession, aiModel);

        return Result.success(chatSessionVO, "获取聊天信息成功");
    }

    /**
     * 更新聊天信息
     */
    @PutMapping("/chats/{id}")
    public Result<ChatSessionVO> updateChat(@PathVariable @NotBlank String id,
                                           @RequestBody Map<String, Object> request,
                                           Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        String title = (String) request.get("title");

        if (title == null || title.trim().isEmpty()) {
            return Result.validateFailed("标题不能为空");
        }

        // 先验证聊天是否属于当前用户
        ChatSession chatSession = chatSessionService.getSessionInfo(id);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        boolean updated = chatSessionService.updateSessionTitle(id, title);
        if (!updated) {
            return Result.failed("更新聊天失败");
        }

        chatSession = chatSessionService.getSessionInfo(id);
        AiModel aiModel = aiModelService.getById(chatSession.getModelId());
        ChatSessionVO chatSessionVO = VOConverter.toChatSessionVO(chatSession, aiModel);

        return Result.success(chatSessionVO, "更新聊天成功");
    }

    /**
     * 删除聊天
     */
    @DeleteMapping("/chats/{id}")
    public Result<Void> deleteChat(@PathVariable @NotBlank String id,
                                  Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        // 先验证聊天是否属于当前用户
        ChatSession chatSession = chatSessionService.getSessionInfo(id);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        boolean deleted = chatSessionService.deleteSession(id);
        if (!deleted) {
            return Result.failed("删除聊天失败");
        }
        return Result.success(null, "删除聊天成功");
    }

    // ==================== 消息相关接口 ====================

    /**
     * 获取聊天消息 - 支持分页参数
     */
    @GetMapping("/chats/{chatId}/messages")
    public Result<Map<String, Object>> getChatMessages(
            @PathVariable @NotBlank String chatId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
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

        List<ChatMessage> allMessages = chatMessageService.getSessionMessages(chatId);
        List<ChatMessageVO> allMessageVOs = VOConverter.toChatMessageVOList(allMessages, fileUploadService, webSearchContentService);

        // 如果没有分页参数，返回所有消息（保持向后兼容）
        if (limit == null && offset == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("messages", allMessageVOs);
            response.put("total", allMessageVOs.size());
            response.put("hasMore", false);
            return Result.success(response, "获取消息列表成功");
        }

        // 有分页参数时，返回分页结果
        int actualLimit = limit != null ? limit : 50;
        int actualOffset = offset != null ? offset : 0;

        int start = Math.min(actualOffset, allMessageVOs.size());
        int end = Math.min(actualOffset + actualLimit, allMessageVOs.size());
        List<ChatMessageVO> pagedMessages = allMessageVOs.subList(start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("messages", pagedMessages);
        response.put("total", allMessageVOs.size());
        response.put("hasMore", end < allMessageVOs.size());

        return Result.success(response, "获取消息成功");
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
        ChatMessage message = chatMessageService.getByMessageId(messageId);
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
     * 删除指定消息及之后的消息
     */
    @Transactional
    @DeleteMapping("/messages/delete")
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
        ChatMessage targetMessage = chatMessageService.getByMessageId(messageId);
        if (targetMessage == null) {
            return Result.failed("消息不存在");
        }
        if (!targetMessage.getSessionId().equals(chatId)) {
            return Result.failed("消息不属于此聊天");
        }

        // 调用ChatMessageService的deleteMessagesFromPoint方法（包含文件删除逻辑）
        int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, chatId);
        if (deletedCount == 0) {
            return Result.failed("删除消息失败");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deletedCount", deletedCount);

        log.info("范围删除消息成功，聊天ID：{}，起始消息ID：{}，删除数量：{}",
                chatId, messageId, deletedCount);
        return Result.success(response, "范围删除消息成功");
    }



    // ==================== AI聊天相关接口 ====================
    // 注意：聊天接口已迁移到ChatApiController，避免路径冲突



    // ==================== AI模型相关接口 ====================

    /**
     * 获取可见的AI模型列表
     */
    @GetMapping("/models")
    public Result<List<AiModelVO>> getModels() {
        List<AiModel> aiModels = aiModelService.getVisibleModels();
        List<AiModelVO> aiModelVOs = VOConverter.toAiModelVOList(aiModels);
        return Result.success(aiModelVOs, "获取模型列表成功");
    }

    /**
     * 获取指定AI模型信息
     */
    @GetMapping("/models/{id}")
    public Result<?> getModel(@PathVariable Long id) {
        AiModel aiModel = aiModelService.getById(id);
        if (aiModel == null) {
            return ErrorResponseUtil.modelNotFound();
        }
        if (aiModel.getIsAvailable() != 1) {
            return ErrorResponseUtil.modelUnavailable();
        }
        AiModelVO aiModelVO = VOConverter.toAiModelVO(aiModel);
        return Result.success(aiModelVO, "获取模型信息成功");
    }

    // ==================== 网络搜索内容修改接口 ====================

    /**
     * 更新网络搜索内容
     */
    @PutMapping("/chat/messages/{messageId}/web-search-content")
    public Result<?> updateWebSearchContent(
            @PathVariable String messageId,
            @RequestBody @Valid UpdateWebSearchContentRequest request,
            Authentication authentication) {

        try {
            Long userId = getCurrentUserId(authentication);
            log.info("【API调用】更新网络搜索内容接口，用户ID：{}，消息ID：{}，内容项数：{}",
                    userId, messageId, request.getContent().size());

            boolean success = webSearchContentService.updateContentByMessageId(
                    messageId, request.getContent(), userId);

            if (success) {
                Map<String, Object> data = new HashMap<>();
                data.put("messageId", messageId);
                data.put("resultCount", request.getContent().size());
                data.put("updateTime", java.time.LocalDateTime.now().toString());

                return Result.success(data, "网络搜索内容更新成功");
            } else {
                return Result.failed("网络搜索内容更新失败");
            }

        } catch (WebSearchException e) {
            log.warn("【API异常】更新网络搜索内容业务异常，消息ID：{}，错误码：{}，错误信息：{}",
                    messageId, e.getCode(), e.getMessage());

            Map<String, Object> errorData = new HashMap<>();
            errorData.put("errorCode", e.getErrorCode().name());
            errorData.put("timestamp", java.time.LocalDateTime.now().toString());

            return new Result<>(e.getCode(), e.getMessage(), errorData);
        } catch (Exception e) {
            log.error("【API异常】更新网络搜索内容系统异常，消息ID：{}", messageId, e);
            return Result.failed("系统异常，请稍后重试");
        }
    }
}
