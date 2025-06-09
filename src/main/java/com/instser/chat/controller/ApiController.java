package com.instser.chat.controller;

import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.common.result.Result;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.User;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.UserService;
import com.instser.chat.vo.AiModelVO;
import com.instser.chat.vo.ChatMessageVO;
import com.instser.chat.vo.ChatSessionVO;
import com.instser.chat.vo.UserVO;
import com.instser.chat.vo.converter.VOConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
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

    private final UserService userService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final AiModelService aiModelService;

    // ==================== 认证相关接口 ====================

    /**
     * 获取当前用户信息
     */
    @GetMapping("/auth/me")
    public Result<UserVO> getCurrentUser(@RequestParam(required = false) Long userId) {
        // TODO: 从JWT token中获取用户ID，这里暂时使用参数
        // 如果没有提供userId，返回未认证状态
        if (userId == null) {
            return Result.failed("用户未认证");
        }

        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        UserVO userVO = VOConverter.toUserVO(user);
        return Result.success(userVO, "获取用户信息成功");
    }

    /**
     * 用户登录
     */
    @PostMapping("/auth/login")
    public Result<UserVO> login(@RequestBody Map<String, Object> request) {
        String usernameOrEmail = (String) request.get("usernameOrEmail");
        String password = (String) request.get("password");

        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty()) {
            return Result.validateFailed("用户名或邮箱不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.validateFailed("密码不能为空");
        }

        try {
            // 判断输入的是邮箱还是用户名
            User user;
            if (usernameOrEmail.contains("@")) {
                // 邮箱登录
                user = userService.login(usernameOrEmail, password);
            } else {
                // 用户名登录，先根据用户名获取邮箱
                User userByUsername = userService.getUserByUsername(usernameOrEmail);
                if (userByUsername == null) {
                    return Result.failed("用户不存在");
                }
                user = userService.login(userByUsername.getEmail(), password);
            }

            UserVO userVO = VOConverter.toUserVO(user);
            log.info("用户登录成功，用户ID：{}", user.getId());
            return Result.success(userVO, "登录成功");
        } catch (BusinessException e) {
            log.warn("用户登录失败：{}", e.getMessage());
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("用户登录异常", e);
            return Result.failed("登录失败，请重试");
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/auth/register")
    public Result<UserVO> register(@RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        if (username == null || username.trim().isEmpty()) {
            return Result.validateFailed("用户名不能为空");
        }
        if (email == null || email.trim().isEmpty()) {
            return Result.validateFailed("邮箱不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.validateFailed("密码不能为空");
        }

        try {
            // 调用真实的注册逻辑
            Long userId = userService.registerUser(username, email, password);
            User user = userService.getById(userId);

            UserVO userVO = VOConverter.toUserVO(user);
            log.info("用户注册成功，用户ID：{}，用户名：{}", userId, username);
            return Result.success(userVO, "注册成功");
        } catch (BusinessException e) {
            log.warn("用户注册失败：{}", e.getMessage());
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("用户注册异常", e);
            return Result.failed("注册失败，请重试");
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        // TODO: 实现登出逻辑，清除JWT token
        return Result.success(null, "登出成功");
    }

    // ==================== 聊天会话相关接口 ====================

    /**
     * 获取用户的聊天列表
     */
    @GetMapping("/chats")
    public Result<List<ChatSessionVO>> getChats(@RequestParam(defaultValue = "1") Long userId) {
        // TODO: 从JWT token中获取用户ID
        List<ChatSession> chatSessions = chatSessionService.getUserSessions(userId);
        List<ChatSessionVO> chatSessionVOs = VOConverter.toChatSessionVOList(chatSessions);
        return Result.success(chatSessionVOs, "获取聊天列表成功");
    }

    /**
     * 创建新聊天
     */
    @PostMapping("/chats")
    public Result<ChatSessionVO> createChat(@RequestBody Map<String, Object> request,
                                           @RequestParam(defaultValue = "1") Long userId) {
        String title = (String) request.getOrDefault("title", "新对话");
        Long modelId = Long.valueOf(request.getOrDefault("modelId", "1").toString());

        // TODO: 从JWT token中获取用户ID
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
                                        @RequestParam(defaultValue = "1") Long userId) {
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
                                           @RequestParam(defaultValue = "1") Long userId) {
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
                                  @RequestParam(defaultValue = "1") Long userId) {
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
     * 获取聊天的所有消息
     */
    @GetMapping("/chats/{chatId}/messages")
    public Result<List<ChatMessageVO>> getChatMessages(@PathVariable @NotBlank String chatId,
                                                      @RequestParam(defaultValue = "1") Long userId) {
        // 先验证聊天是否属于当前用户
        ChatSession chatSession = chatSessionService.getSessionInfo(chatId);
        if (chatSession == null) {
            return Result.failed("聊天不存在");
        }
        if (!chatSession.getUserId().equals(userId)) {
            return Result.failed("无权访问此聊天");
        }

        List<ChatMessage> chatMessages = chatMessageService.getSessionMessages(chatId);
        List<ChatMessageVO> chatMessageVOs = VOConverter.toChatMessageVOList(chatMessages);
        return Result.success(chatMessageVOs, "获取消息列表成功");
    }

    /**
     * 添加新消息到聊天
     */
    @PostMapping("/chats/{chatId}/messages")
    public Result<ChatMessageVO> addMessage(@PathVariable @NotBlank String chatId,
                                           @RequestBody Map<String, Object> request,
                                           @RequestParam(defaultValue = "1") Long userId) {
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

        // TODO: 从JWT token中获取用户ID
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
