package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.vo.AiModelVO;
import com.instser.chat.vo.ChatMessageVO;
import com.instser.chat.vo.ChatSessionVO;
import com.instser.chat.vo.converter.VOConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端API控制器 - 使用VO响应
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "true")
public class ApiController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final AiModelService aiModelService;

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
     * 获取用户的聊天列表
     */
    @GetMapping("/chats")
    public Result<List<ChatSessionVO>> getChats(Authentication authentication) {
        UserDetailsServiceImpl.CustomUserDetails userDetails =
            (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        List<ChatSession> chatSessions = chatSessionService.getUserSessions(userId);
        List<ChatSessionVO> chatSessionVOs = VOConverter.toChatSessionVOList(chatSessions);
        return Result.success(chatSessionVOs, "获取聊天列表成功");
    }

    /**
     * 获取用户的聊天列表 - 支持分页
     */
    @GetMapping("/chats/paged")
    public Result<Map<String, Object>> getChatsWithPaging(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            Authentication authentication) {
        UserDetailsServiceImpl.CustomUserDetails userDetails =
            (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        List<ChatSession> allChatSessions = chatSessionService.getUserSessions(userId);
        List<ChatSessionVO> allChatSessionVOs = VOConverter.toChatSessionVOList(allChatSessions);

        // 实现分页逻辑
        int start = Math.min(offset, allChatSessionVOs.size());
        int end = Math.min(offset + limit, allChatSessionVOs.size());
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
        List<ChatMessageVO> allMessageVOs = VOConverter.toChatMessageVOList(allMessages);

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
     * 添加新消息到聊天
     */
    @PostMapping("/chats/{chatId}/messages")
    public Result<ChatMessageVO> addMessage(@PathVariable @NotBlank String chatId,
                                           @RequestBody Map<String, Object> request,
                                           Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        String role = (String) request.get("role");
        String content = (String) request.get("content");

        if (role == null || role.trim().isEmpty()) {
            return Result.validateFailed("消息角色不能为空");
        }
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

        Long messageId = chatMessageService.sendMessage(chatId, role, content, userId);
        ChatMessage chatMessage = chatMessageService.getById(messageId);

        if (chatMessage == null) {
            return Result.failed("添加消息失败");
        }

        ChatMessageVO chatMessageVO = VOConverter.toChatMessageVO(chatMessage);
        return Result.success(chatMessageVO, "添加消息成功");
    }

    /**
     * 删除指定消息
     */
    @DeleteMapping("/messages/delete")
    public Result<Void> deleteMessages(@RequestBody Map<String, Object> request) {
        // TODO: 实现删除消息逻辑
        return Result.success(null, "删除消息成功");
    }

    // ==================== AI模型相关接口 ====================

    /**
     * 获取可用的AI模型列表
     */
    @GetMapping("/models")
    public Result<List<AiModelVO>> getModels() {
        List<AiModel> aiModels = aiModelService.getAvailableModels();
        List<AiModelVO> aiModelVOs = VOConverter.toAiModelVOList(aiModels);
        return Result.success(aiModelVOs, "获取模型列表成功");
    }

    /**
     * 获取指定AI模型信息
     */
    @GetMapping("/models/{id}")
    public Result<AiModelVO> getModel(@PathVariable Long id) {
        AiModel aiModel = aiModelService.getById(id);
        if (aiModel == null || aiModel.getIsAvailable() != 1) {
            return Result.failed("模型不存在或不可用");
        }
        AiModelVO aiModelVO = VOConverter.toAiModelVO(aiModel);
        return Result.success(aiModelVO, "获取模型信息成功");
    }
}
