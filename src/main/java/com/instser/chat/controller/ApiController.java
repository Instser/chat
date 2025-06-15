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
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "true")
public class ApiController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Value("${file.upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${server.servlet.context-path:}")
    private String contextPath;
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
     * 获取用户的聊天列表 - 支持分页参数
     */
    @GetMapping("/chats")
    public Result<Map<String, Object>> getChats(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        List<ChatSession> allChatSessions = chatSessionService.getUserSessions(userId);
        List<ChatSessionVO> allChatSessionVOs = VOConverter.toChatSessionVOList(allChatSessions);

        // 如果没有分页参数，返回所有聊天（保持向后兼容）
        if (limit == null && offset == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("chats", allChatSessionVOs);
            response.put("total", allChatSessionVOs.size());
            response.put("hasMore", false);
            return Result.success(response, "获取聊天列表成功");
        }

        // 有分页参数时，返回分页结果
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
     * 批量删除消息
     */
    @PostMapping("/messages/delete")
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
                .collect(java.util.stream.Collectors.toList());

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

    // ==================== 文件上传相关接口 ====================

    /**
     * 文件上传接口
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFiles(
            @RequestParam("files") MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return Result.validateFailed("没有文件上传");
        }

        List<Map<String, Object>> uploadedFiles = new ArrayList<>();

        try {
            // 确保上传目录存在
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }

                // 检查文件大小
                if (file.getSize() > maxFileSize) {
                    return Result.failed("文件 " + file.getOriginalFilename() + " 超过最大大小限制");
                }

                // 检查文件类型
                String contentType = file.getContentType();
                if (!isAllowedFileType(contentType)) {
                    return Result.failed("不支持的文件类型：" + contentType);
                }

                // 生成唯一文件名
                String originalFilename = file.getOriginalFilename();
                String fileExtension = getFileExtension(originalFilename);
                String uniqueFilename = generateUniqueFilename(fileExtension);

                // 保存文件
                Path filePath = uploadDir.resolve(uniqueFilename);
                Files.copy(file.getInputStream(), filePath);

                // 构建文件信息
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", uniqueFilename);
                fileInfo.put("originalName", originalFilename);
                fileInfo.put("size", file.getSize());
                fileInfo.put("type", contentType);
                fileInfo.put("url", buildFileUrl(uniqueFilename));
                fileInfo.put("uploadedAt", LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")));

                uploadedFiles.add(fileInfo);

                log.info("文件上传成功：{} -> {}", originalFilename, uniqueFilename);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", uploadedFiles);
            response.put("message", "文件上传成功");

            return Result.success(response, "文件上传成功");

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.failed("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/files/{filename}")
    public Result<Map<String, Object>> getFileInfo(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadPath, filename);
            if (!Files.exists(filePath)) {
                return Result.failed("文件不存在");
            }

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", filename);
            fileInfo.put("size", Files.size(filePath));
            fileInfo.put("url", buildFileUrl(filename));
            fileInfo.put("lastModified", Files.getLastModifiedTime(filePath).toString());

            return Result.success(fileInfo, "获取文件信息成功");

        } catch (IOException e) {
            log.error("获取文件信息失败", e);
            return Result.failed("获取文件信息失败");
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/files/{filename}")
    public Result<Void> deleteFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadPath, filename);
            if (!Files.exists(filePath)) {
                return Result.failed("文件不存在");
            }

            Files.delete(filePath);
            log.info("文件删除成功：{}", filename);

            return Result.success(null, "文件删除成功");

        } catch (IOException e) {
            log.error("文件删除失败", e);
            return Result.failed("文件删除失败");
        }
    }

    /**
     * 检查是否为允许的文件类型
     */
    private boolean isAllowedFileType(String contentType) {
        if (contentType == null) {
            return false;
        }

        // 允许的文件类型
        Set<String> allowedTypes = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "text/plain", "text/csv",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/json"
        );

        return allowedTypes.contains(contentType.toLowerCase());
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * 生成唯一文件名
     */
    private String generateUniqueFilename(String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid + extension;
    }

    /**
     * 构建文件访问URL
     */
    private String buildFileUrl(String filename) {
        String baseUrl = contextPath.isEmpty() ? "" : contextPath;
        return baseUrl + "/uploads/" + filename;
    }

    // ==================== AI聊天相关接口 ====================
    // 注意：聊天接口已迁移到ChatApiController，避免路径冲突



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
