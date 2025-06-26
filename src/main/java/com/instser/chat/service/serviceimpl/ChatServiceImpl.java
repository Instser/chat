package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.constants.PromptConstants;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.*;
import com.instser.chat.vo.ChatLimitVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 聊天业务服务实现类
 *
 * @author instser
 * @since 2025-06-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final AiModelService aiModelService;
    private final ChatLimitService chatLimitService;
    private final FileUploadService fileUploadService;

    // 智能分块响应配置
    private static final boolean ENABLE_SMART_CHUNKING = true;
    private static final long CHUNK_DELAY_MS = 30;
    private static final int MIN_CHUNK_SIZE = 5;
    private static final int MAX_CHUNK_SIZE = 25;

    @Override
    public ChatResponseDTO processStreamChat(ChatRequestDTO request, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理流式聊天请求，用户ID：{}，模型ID：{}", request.getUserId(), request.getModelId());

        try {
            // 设置响应头
            setStreamResponseHeaders(response);

            // 验证请求参数
            String validationError = validateChatRequest(request);
            if (validationError != null) {
                writeErrorResponse(response, validationError);
                return ChatResponseDTO.failure(validationError);
            }

            // 验证用户权限
            String permissionError = validateUserPermission(authentication, request.getUserId());
            if (permissionError != null) {
                writeErrorResponse(response, permissionError);
                return ChatResponseDTO.failure(permissionError);
            }

            // 获取模型配置
            AiModel model = aiModelService.getById(request.getModelId());
            if (model == null || model.getIsAvailable() != 1) {
                String error = "AI模型不可用";
                writeErrorResponse(response, error);
                return ChatResponseDTO.failure(error);
            }

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(request.getUserId());
            if (!limitCheck.getCanChat()) {
                String errorMessage = limitCheck.getLimitReason() != null ?
                    limitCheck.getLimitReason() : "聊天权限验证失败";
                writeErrorResponse(response, errorMessage);
                return ChatResponseDTO.failure(errorMessage);
            }

            log.info("用户{}聊天权限验证通过，套餐：{}，限制类型：{}",
                    request.getUserId(), limitCheck.getPackageName(), limitCheck.getLimitType());

            // 处理文件内容并附加到用户消息中
            List<Map<String, String>> processedMessages = processFilesContent(request.getMessages(), request.getFilesId(), request.getUserId());
            request.setMessages(processedMessages);

            // 保存用户消息到数据库
            String userMessageId = null;
            if (request.getSessionId() != null) {
                userMessageId = saveUserMessage(request.getMessages(), request.getSessionId(), request.getUserId());
            }

            // 判断是否会生成标题
            boolean willGenerateTitle = request.getSessionId() != null && isFirstAiResponse(request.getMessages());

            // 调用AI服务进行流式响应
            ChatResponseDTO result = streamChatWithAI(request, model, response, willGenerateTitle);

            // 处理响应结果
            if (result.isRealAiResponse()) {
                // 记录使用次数
                boolean recordSuccess = chatLimitService.recordChatUsage(request.getUserId());
                if (recordSuccess) {
                    log.info("用户{}聊天使用次数记录成功", request.getUserId());
                } else {
                    log.warn("用户{}聊天使用次数记录失败", request.getUserId());
                }

                // 保存AI响应到数据库
                if (request.getSessionId() != null && result.getAiResponseContent() != null && !result.getAiResponseContent().trim().isEmpty()) {
                    saveAiResponse(request.getSessionId(), request.getUserId(), model.getId(), result.getAiResponseContent());
                }

                // AI响应成功后，更新文件的关联信息
                if (userMessageId != null && request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                    updateFilesAssociation(request.getFilesId(), userMessageId, request.getSessionId(), request.getUserId());
                }

                log.info("用户{}AI响应成功", request.getUserId());
            } else if (result.isHasContentSent()) {
                // 有内容发送（错误响应），但不记录使用次数
                log.info("用户{}收到错误响应，已保存聊天记录但不记录使用次数", request.getUserId());
            }

            return result;

        } catch (Exception e) {
            log.error("处理流式聊天请求异常", e);
            try {
                String errorContent = "请求错误";
                writeErrorResponse(response, errorContent);
                
                // 保存错误响应到数据库
                if (request.getSessionId() != null && request.getUserId() != null) {
                    AiModel model = aiModelService.getById(request.getModelId());
                    if (model != null) {
                        saveErrorResponse(request.getSessionId(), request.getUserId(), model.getId(), errorContent);
                    }
                }
                
                return ChatResponseDTO.errorWithContent(errorContent);
            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
                return ChatResponseDTO.failure("系统错误");
            }
        }
    }

    @Override
    public String validateChatRequest(ChatRequestDTO request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "消息列表不能为空";
        }

        if (request.getUserId() == null) {
            return "用户ID不能为空";
        }

        if (request.getModelId() == null) {
            return "AI模型ID不能为空";
        }

        // 验证最后一条消息必须是用户消息
        Map<String, String> lastMessage = request.getMessages().get(request.getMessages().size() - 1);
        String role = lastMessage.get("role");
        if (!"user".equals(role)) {
            return "最后一条消息必须是用户消息";
        }

        return null; // 验证通过
    }

    @Override
    public String validateUserPermission(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "用户未认证";
        }
        return null; // 验证通过
    }

    @Override
    public String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId) {
        try {
            if (messages == null || messages.isEmpty() || sessionId == null || userId == null) {
                log.warn("无法保存用户消息到数据库，缺少必要参数");
                return null;
            }

            // 从消息列表中找到最后一条用户消息
            String lastUserMessage = null;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> message = messages.get(i);
                if ("user".equals(message.get("role"))) {
                    lastUserMessage = message.get("content");
                    break;
                }
            }

            if (lastUserMessage != null && !lastUserMessage.trim().isEmpty()) {
                Long messageDbId = chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId);
                if (messageDbId != null) {
                    // 获取刚创建的消息以获取messageId
                    ChatMessage savedMessage = chatMessageService.getById(messageDbId);
                    if (savedMessage != null) {
                        log.info("用户消息已保存到数据库，会话ID：{}，用户ID：{}，消息ID：{}，内容长度：{}",
                                sessionId, userId, savedMessage.getMessageId(), lastUserMessage.length());
                        return savedMessage.getMessageId();
                    }
                }
            } else {
                log.warn("未找到有效的用户消息，跳过保存");
            }
        } catch (Exception e) {
            log.error("保存用户消息到数据库失败", e);
        }
        return null;
    }

    /**
     * 更新文件的关联信息（在AI响应成功后调用）
     *
     * @param filesId 文件ID列表（逗号分隔）
     * @param messageId 消息ID
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    private void updateFilesAssociation(String filesId, String messageId, String sessionId, Long userId) {
        try {
            if (filesId == null || filesId.trim().isEmpty() || messageId == null || sessionId == null || userId == null) {
                log.warn("无法更新文件关联信息，缺少必要参数");
                return;
            }

            // 解析文件ID列表
            String[] fileIds = filesId.split(",");
            int successCount = 0;
            int totalCount = fileIds.length;

            for (String fileId : fileIds) {
                fileId = fileId.trim();
                if (fileId.isEmpty()) {
                    continue;
                }

                try {
                    // 更新文件的messageId和sessionId
                    boolean updated = fileUploadService.updateFileAssociation(fileId, messageId, sessionId, userId);
                    if (updated) {
                        successCount++;
                        log.info("文件{}关联信息已更新，消息ID：{}，会话ID：{}，用户ID：{}", fileId, messageId, sessionId, userId);
                    } else {
                        log.warn("文件{}关联信息更新失败，用户ID：{}", fileId, userId);
                    }
                } catch (Exception e) {
                    log.error("文件{}关联信息更新时发生异常，用户ID：{}", fileId, userId, e);
                }
            }

            log.info("文件关联信息更新完成，成功：{}/{}，消息ID：{}，会话ID：{}，用户ID：{}",
                    successCount, totalCount, messageId, sessionId, userId);

        } catch (Exception e) {
            log.error("更新文件关联信息失败", e);
        }
    }

    @Override
    public void saveAiResponse(String sessionId, Long userId, Long modelId, String content) {
        try {
            if (sessionId != null && userId != null && modelId != null && content != null && !content.trim().isEmpty()) {
                chatMessageService.sendMessage(sessionId, "assistant", content, userId);
                log.info("AI响应已保存到数据库，会话ID：{}，用户ID：{}，内容长度：{}", sessionId, userId, content.length());
            } else {
                log.warn("无法保存AI响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}, contentLength={}",
                        sessionId, userId, modelId, content != null ? content.length() : 0);
            }
        } catch (Exception e) {
            log.error("保存AI响应到数据库失败", e);
        }
    }

    @Override
    public void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent) {
        try {
            if (sessionId != null && userId != null && modelId != null) {
                chatMessageService.sendMessage(sessionId, "assistant", errorContent, userId);
                log.info("错误响应已保存到数据库，会话ID：{}，用户ID：{}，内容：{}", sessionId, userId, errorContent);
            } else {
                log.warn("无法保存错误响应到数据库，缺少必要参数：sessionId={}, userId={}, modelId={}",
                        sessionId, userId, modelId);
            }
        } catch (Exception e) {
            log.error("保存错误响应到数据库失败", e);
        }
    }

    @Override
    public boolean isFirstAiResponse(List<Map<String, String>> messages) {
        // 统计用户消息数量
        long userMessageCount = messages.stream()
            .filter(msg -> "user".equals(msg.get("role")))
            .count();

        // 如果只有1条用户消息，说明这是首次AI响应
        return userMessageCount == 1;
    }

    /**
     * 通用的聊天权限和会话验证
     */
    private ChatValidationResult validateChatPermissions(String sessionId, String messageId, String expectedRole,
                                                        Authentication authentication) {
        // 验证用户认证
        if (authentication == null || !authentication.isAuthenticated()) {
            return ChatValidationResult.error("用户未认证");
        }

        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ChatValidationResult.error("无法获取用户信息");
        }

        // 验证会话是否存在
        ChatSession chatSession = chatSessionService.getSessionInfo(sessionId);
        if (chatSession == null) {
            return ChatValidationResult.error("会话不存在");
        }

        // 验证会话是否属于当前用户
        if (!chatSession.getUserId().equals(userId)) {
            return ChatValidationResult.error("无权访问此会话");
        }

        // 验证消息是否存在
        ChatMessage targetMessage = chatMessageService.getByMessageId(messageId);
        if (targetMessage == null) {
            return ChatValidationResult.error("消息不存在");
        }

        // 验证消息是否属于此会话
        if (!targetMessage.getSessionId().equals(sessionId)) {
            return ChatValidationResult.error("消息不属于此会话");
        }

        // 验证消息角色
        if (expectedRole != null && !expectedRole.equals(targetMessage.getRole())) {
            String errorMsg = "assistant".equals(expectedRole) ? "只能重新生成AI消息" : "只能编辑用户消息";
            return ChatValidationResult.error(errorMsg);
        }

        return ChatValidationResult.success(userId, chatSession, targetMessage);
    }

    /**
     * 获取并验证AI模型
     */
    private AiModel getAndValidateModel(Long modelId, ChatSession chatSession) {
        Long targetModelId = modelId != null ? modelId : chatSession.getModelId();
        AiModel model = aiModelService.getById(targetModelId);

        if (model == null || model.getIsAvailable() != 1) {
            return null;
        }

        return model;
    }

    /**
     * 更新会话模型（如果需要）
     */
    private void updateSessionModelIfNeeded(String sessionId, Long newModelId, Long currentModelId) {
        if (newModelId != null && !newModelId.equals(currentModelId)) {
            chatSessionService.updateSessionModel(sessionId, newModelId);
            log.info("会话{}的模型已更新为：{}", sessionId, newModelId);
        }
    }

    /**
     * 处理AI响应结果的通用逻辑
     */
    private void handleAiResponseResult(ChatResponseDTO result, Long userId, String sessionId,
                                      AiModel model, String operationType) {
        if (result.isRealAiResponse()) {
            // 记录使用次数
            boolean recordSuccess = chatLimitService.recordChatUsage(userId);
            if (recordSuccess) {
                log.info("用户{}{}聊天使用次数记录成功", userId, operationType);
            } else {
                log.warn("用户{}{}聊天使用次数记录失败", userId, operationType);
            }

            // 保存AI响应到数据库
            if (result.getAiResponseContent() != null && !result.getAiResponseContent().trim().isEmpty()) {
                saveAiResponse(sessionId, userId, model.getId(), result.getAiResponseContent());
            }

            log.info("用户{}{}AI响应成功", userId, operationType);
        } else if (result.isHasContentSent()) {
            // 有内容发送（错误响应），但不记录使用次数
            log.info("用户{}{}收到错误响应，已保存聊天记录但不记录使用次数", userId, operationType);
        }
    }

    /**
     * 聊天验证结果内部类
     */
    private static class ChatValidationResult {
        private final boolean success;
        private final String errorMessage;
        private final Long userId;
        private final ChatSession chatSession;
        private final ChatMessage targetMessage;

        private ChatValidationResult(boolean success, String errorMessage, Long userId,
                                   ChatSession chatSession, ChatMessage targetMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.userId = userId;
            this.chatSession = chatSession;
            this.targetMessage = targetMessage;
        }

        public static ChatValidationResult success(Long userId, ChatSession chatSession, ChatMessage targetMessage) {
            return new ChatValidationResult(true, null, userId, chatSession, targetMessage);
        }

        public static ChatValidationResult error(String errorMessage) {
            return new ChatValidationResult(false, errorMessage, null, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Long getUserId() { return userId; }
        public ChatSession getChatSession() { return chatSession; }
        public ChatMessage getTargetMessage() { return targetMessage; }
    }

    /**
     * 设置流式响应头
     */
    private void setStreamResponseHeaders(HttpServletResponse response) {
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-vercel-ai-data-stream", "v1");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, String errorMessage) throws IOException {
        response.getWriter().write("3:\"" + errorMessage + "\"\n");
        response.getWriter().flush();
    }

    /**
     * 调用AI服务进行流式响应
     */
    private ChatResponseDTO streamChatWithAI(ChatRequestDTO request, AiModel model, HttpServletResponse response, boolean willGenerateTitle) {
        // 用于跟踪是否有AI内容被成功发送和是否为真实AI响应
        final boolean[] hasContentSent = {false};
        final boolean[] isRealAiResponse = {false};
        // 用于收集完整的AI响应内容
        final StringBuilder aiResponseContent = new StringBuilder();

        try {
            // 添加系统提示词到消息列表开头
            List<Map<String, String>> messagesWithPrompt = new ArrayList<>();

            // 根据deepthink参数选择提示词
            boolean isDeepThink = request.getDeepThinking() != null && request.getDeepThinking();
            String systemPrompt = isDeepThink ? PromptConstants.DEEP_THINKING_PROMPT : PromptConstants.DEFAULT_SYSTEM_PROMPT;

            // 添加系统提示词
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesWithPrompt.add(systemMessage);

            // 添加用户的消息
            messagesWithPrompt.addAll(request.getMessages());

            log.info("使用{}提示词，消息总数: {}", isDeepThink ? "深度思考" : "默认", messagesWithPrompt.size());

            // 记录详细的AI请求信息
            logAiRequestDetails(messagesWithPrompt, model, request.getUserId());

            // 添加JSON数据缓冲区，用于处理分片数据
            StringBuilder jsonBuffer = new StringBuilder();
            // 添加行缓冲区，用于处理被分割的行
            StringBuilder lineBuffer = new StringBuilder();

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messagesWithPrompt);
            requestBody.put("stream", true);
            requestBody.put("reasoning_effort", "none");

            // 创建WebClient
            WebClient webClient = WebClient.builder()
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .defaultHeader("Accept-Charset", "UTF-8")
                    .defaultHeader("User-Agent", "OpenAI/PowerShell")
                    .build();

            log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());

            // 发送流式请求并处理响应
            webClient.post()
                    .uri(model.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofSeconds(30))
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String chunk = new String(bytes, StandardCharsets.UTF_8);
                        return chunk.replaceAll("[\uFFFD\u0000]", "");
                    })
                    .flatMap(chunk -> {
                        // 将chunk添加到行缓冲区
                        lineBuffer.append(chunk);

                        // 按换行符分割，但保留最后一个不完整的行
                        String bufferedContent = lineBuffer.toString();
                        String[] lines = bufferedContent.split("\n", -1);
                        List<String> validLines = new ArrayList<>();

                        // 处理除最后一行外的所有行（这些都是完整的行）
                        for (int i = 0; i < lines.length - 1; i++) {
                            String line = lines[i].trim();
                            if (!line.isEmpty()) {
                                validLines.add(line);
                            }
                        }

                        // 最后一行可能不完整，保留在缓冲区中
                        String lastLine = lines[lines.length - 1];
                        lineBuffer.setLength(0);
                        if (!lastLine.isEmpty()) {
                            lineBuffer.append(lastLine);
                        }

                        return Flux.fromIterable(validLines);
                    })
                    .doOnNext(line -> {
                        try {
                            log.info("收到AI响应行: [{}]", line);

                            if (line.startsWith("data: ")) {
                                if (line.equals("data: [DONE]")) {
                                    log.info("收到AI响应完成标记，开始处理完成逻辑");
                                    handleStreamCompletion(response, willGenerateTitle, isRealAiResponse[0], request);
                                    return;
                                }

                                // 提取JSON数据部分
                                String jsonData = line.substring(6).trim();
                                jsonBuffer.append(jsonData);

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    String contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent != null) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true;
                                        aiResponseContent.append(contentSent);
                                    }
                                    jsonBuffer.setLength(0);
                                }
                            } else if (!line.trim().isEmpty() && jsonBuffer.length() > 0) {
                                // 处理跨行的JSON片段（不以data:开头但缓冲区有数据）
                                log.debug("处理跨行JSON片段: [{}]", line);

                                // 防止缓冲区无限增长
                                if (jsonBuffer.length() > 10000) {
                                    log.warn("JSON缓冲区过大，清空重新开始: {}", jsonBuffer.length());
                                    jsonBuffer.setLength(0);
                                    return;
                                }

                                jsonBuffer.append(line.trim());

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    log.debug("跨行JSON重组成功，长度: {}", bufferedJson.length());
                                    String contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent != null) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true;
                                        aiResponseContent.append(contentSent);
                                    }
                                    jsonBuffer.setLength(0);
                                } else {
                                    log.debug("跨行JSON仍不完整，继续等待: 当前长度 {}", bufferedJson.length());
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理流式数据失败: {}, 缓冲区内容: [{}]", e.getMessage(), jsonBuffer.toString());
                            // 不要立即清空缓冲区，给跨行JSON一个机会
                            // jsonBuffer.setLength(0);
                        }
                    })
                    .doOnError(error -> {
                        log.error("AI API流式响应处理失败", error);
                        try {
                            String errorContent = "请求错误";
                            String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                            response.getWriter().write(vercelFormat);

                            String completionData = buildCompletionData(false, false, null);
                            response.getWriter().write(completionData);
                            response.getWriter().flush();

                            hasContentSent[0] = true;
                        } catch (Exception e) {
                            log.error("发送错误消息失败", e);
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("AI API流式响应处理完成");

                        // 处理剩余的行缓冲区数据
                        if (lineBuffer.length() > 0) {
                            String remainingLine = lineBuffer.toString().trim();
                            if (!remainingLine.isEmpty()) {
                                log.info("处理流完成时剩余的行数据: [{}]", remainingLine);
                                try {
                                    // 处理剩余的行数据
                                    if (remainingLine.startsWith("data: ")) {
                                        if (remainingLine.equals("data: [DONE]")) {
                                            log.info("收到AI响应完成标记（来自剩余数据）");
                                            handleStreamCompletion(response, willGenerateTitle, isRealAiResponse[0], request);
                                        } else {
                                            String jsonData = remainingLine.substring(6).trim();
                                            jsonBuffer.append(jsonData);

                                            String bufferedJson = jsonBuffer.toString();
                                            if (isValidJson(bufferedJson)) {
                                                String contentSent = processCompleteJson(bufferedJson, response);
                                                if (contentSent != null) {
                                                    hasContentSent[0] = true;
                                                    isRealAiResponse[0] = true;
                                                    aiResponseContent.append(contentSent);
                                                }
                                            }
                                        }
                                    } else if (jsonBuffer.length() > 0) {
                                        // 处理跨行的JSON片段
                                        jsonBuffer.append(remainingLine);
                                        String bufferedJson = jsonBuffer.toString();
                                        if (isValidJson(bufferedJson)) {
                                            String contentSent = processCompleteJson(bufferedJson, response);
                                            if (contentSent != null) {
                                                hasContentSent[0] = true;
                                                isRealAiResponse[0] = true;
                                                aiResponseContent.append(contentSent);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("处理剩余行数据失败: {}", e.getMessage());
                                }
                            }
                            lineBuffer.setLength(0);
                        }

                        // 清理JSON缓冲区
                        if (jsonBuffer.length() > 0) {
                            log.warn("流完成时JSON缓冲区仍有数据，长度: {}, 内容: [{}]", jsonBuffer.length(), jsonBuffer.toString());
                            jsonBuffer.setLength(0);
                        }
                    })
                    .onErrorReturn("")
                    .blockLast();

            return ChatResponseDTO.success(hasContentSent[0], isRealAiResponse[0], aiResponseContent.toString());

        } catch (Exception e) {
            log.error("流式聊天异常", e);
            try {
                String errorContent = "请求错误";
                String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                response.getWriter().write(vercelFormat);

                String completionData = buildCompletionData(false, false, null);
                response.getWriter().write(completionData);
                response.getWriter().flush();

                return ChatResponseDTO.errorWithContent(errorContent);
            } catch (IOException ioException) {
                log.error("发送异常消息失败", ioException);
                return ChatResponseDTO.failure("系统错误");
            }
        }
    }

    /**
     * 处理流式响应完成
     */
    private void handleStreamCompletion(HttpServletResponse response, boolean willGenerateTitle, boolean isRealAiResponse, ChatRequestDTO request) {
        try {
            String generatedTitle = null;

            // 如果需要生成标题且是真实AI响应，等待标题生成完成
            if (willGenerateTitle && isRealAiResponse) {
                log.info("开始生成标题，会话ID: {}", request.getSessionId());
                try {
                    CompletableFuture<String> titleFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return generateSessionTitle(request.getSessionId(), request.getMessages());
                        } catch (Exception e) {
                            log.error("后台标题生成失败", e);
                            return null;
                        }
                    });
                    generatedTitle = titleFuture.get(5, TimeUnit.SECONDS);

                    if (generatedTitle != null && !generatedTitle.equals("New Chat")) {
                        log.info("标题生成成功: {}", generatedTitle);
                    } else {
                        log.info("标题生成失败或返回默认标题: {}", generatedTitle);
                        generatedTitle = null;
                    }
                } catch (TimeoutException e) {
                    log.warn("标题生成超时，继续发送响应");
                    generatedTitle = null;
                } catch (Exception e) {
                    log.error("标题生成异常", e);
                    generatedTitle = null;
                }
            }

            // 发送完成标记
            String completionData = buildCompletionData(willGenerateTitle, isRealAiResponse, generatedTitle);
            response.getWriter().write(completionData);
            response.getWriter().flush();

        } catch (Exception e) {
            log.error("处理完成标记时发生异常", e);
            try {
                String completionData = buildCompletionData(false, false, null);
                response.getWriter().write(completionData);
                response.getWriter().flush();
            } catch (Exception ex) {
                log.error("发送异常完成标记失败", ex);
            }
        }
    }

    /**
     * 验证JSON字符串是否完整有效
     */
    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 处理完整的JSON数据
     */
    private String processCompleteJson(String jsonData, HttpServletResponse response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonData);

            log.debug("处理AI API响应: {}", jsonData.length() > 200 ? jsonData.substring(0, 200) + "..." : jsonData);

            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    String content = delta.get("content").asText();
                    if (content != null && !content.isEmpty()) {
                        String cleanContent = cleanContent(content);

                        if (!cleanContent.isEmpty()) {
                            log.debug("AI响应内容: [{}]", cleanContent.length() > 50 ? cleanContent.substring(0, 50) + "..." : cleanContent);

                            if (ENABLE_SMART_CHUNKING) {
                                sendContentBySmartChunking(cleanContent, response, mapper);
                            } else {
                                sendContentAsBlock(cleanContent, response, mapper);
                            }
                            return cleanContent;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("处理完整JSON数据失败: {}, JSON内容: {}", e.getMessage(), jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
            return null;
        }
    }

    /**
     * 清理内容，移除无效字符
     */
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }

        String cleaned = content.replaceAll("[\uFFFD\u0000]", "");
        cleaned = cleaned.replaceAll(" {2,}", " ");
        return cleaned;
    }

    /**
     * 智能分块发送内容
     */
    private void sendContentBySmartChunking(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            int optimalChunkSize = getOptimalChunkSize(content);
            int[] codePoints = content.codePoints().toArray();
            StringBuilder chunk = new StringBuilder();
            int sentChars = 0;

            for (int i = 0; i < codePoints.length; i++) {
                int codePoint = codePoints[i];
                String character = new String(Character.toChars(codePoint));

                if (codePoint != 0xFFFD && codePoint != 0x0000) {
                    chunk.append(character);
                }

                if (chunk.length() >= optimalChunkSize || i == codePoints.length - 1) {
                    try {
                        String chunkContent = chunk.toString();
                        String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(chunkContent));
                        response.getWriter().write(vercelFormat);
                        response.getWriter().flush();

                        sentChars += chunkContent.length();
                        chunk.setLength(0);

                        if (CHUNK_DELAY_MS > 0) {
                            Thread.sleep(CHUNK_DELAY_MS);
                        }
                    } catch (InterruptedException e) {
                        log.warn("分块发送被中断: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        log.error("发送分块内容失败: {}", e.getMessage());
                        chunk.setLength(0);
                        continue;
                    }
                }
            }

            log.info("完成智能分块流式发送，总字符数: {}, 实际发送: {}, 分块大小: {}", codePoints.length, sentChars, optimalChunkSize);

        } catch (Exception e) {
            log.error("智能分块发送内容失败: {}", e.getMessage());
            try {
                String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(content));
                response.getWriter().write(vercelFormat);
                response.getWriter().flush();
                log.info("回退到块发送模式: [{}]", content);
            } catch (Exception fallbackException) {
                log.error("回退发送也失败: {}", fallbackException.getMessage());
            }
        }
    }

    /**
     * 传统块发送模式
     */
    private void sendContentAsBlock(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            String cleanContent = cleanContent(content);
            if (!cleanContent.isEmpty()) {
                String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(cleanContent));
                response.getWriter().write(vercelFormat);
                response.getWriter().flush();
                log.info("块发送模式数据发送成功: [{}]", cleanContent);
            } else {
                log.debug("块发送模式：内容为空，跳过发送");
            }
        } catch (Exception e) {
            log.error("块发送模式失败: {}", e.getMessage());
        }
    }

    /**
     * 根据内容长度计算最优分块大小
     */
    private int getOptimalChunkSize(String content) {
        int length = content.length();

        if (length <= 20) {
            return length;
        } else if (length <= 100) {
            return Math.max(MIN_CHUNK_SIZE, Math.min(length / 4, 10));
        } else if (length <= 300) {
            return Math.max(10, Math.min(length / 8, 15));
        } else {
            return Math.max(15, Math.min(MAX_CHUNK_SIZE, length / 12));
        }
    }

    /**
     * 构建完成标记数据
     */
    private String buildCompletionData(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) {
        try {
            Map<String, Object> completionMap = new HashMap<>();
            completionMap.put("finishReason", "stop");
            completionMap.put("usage", Map.of(
                "promptTokens", 10,
                "completionTokens", 20
            ));

            if (isRealAiResponse && willGenerateTitle) {
                if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                    completionMap.put("title", generatedTitle.trim());
                } else {
                    completionMap.put("title", false);
                }
            } else {
                completionMap.put("title", false);
            }

            return String.format("d:%s\n", new ObjectMapper().writeValueAsString(completionMap));
        } catch (Exception e) {
            log.error("构建完成标记数据失败", e);
            return "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":10,\"completionTokens\":20},\"title\":false}\n";
        }
    }

    @Override
    public String generateSessionTitle(String sessionId, List<Map<String, String>> messages) {
        log.info("=== 开始后台同步标题生成流程 ===");
        log.info("会话ID: {}, 消息数量: {}", sessionId, messages.size());

        try {
            // 获取固定的标题生成模型（ID为1L）
            AiModel titleModel = aiModelService.getById(1L);
            if (titleModel == null || titleModel.getIsAvailable() != 1) {
                log.error("标题生成模型（ID=1L）不可用，跳过标题生成");
                return null;
            }
            log.info("标题生成模型: {}", titleModel.getModelName());

            // 构建对话内容
            String conversationContent = buildConversationContent(messages);
            if (conversationContent == null || conversationContent.trim().isEmpty()) {
                log.warn("无法构建对话内容，跳过标题生成");
                return null;
            }
            log.info("对话内容构建成功，长度: {}字符", conversationContent.length());

            // 构建标题生成提示词
            String titlePrompt = buildTitlePrompt(conversationContent);
            log.info("标题提示词构建成功，长度: {}字符", titlePrompt.length());

            // 调用AI服务生成标题
            String rawTitle = callAiForTitleWithHttpClient(titlePrompt, titleModel);
            if (rawTitle == null || rawTitle.trim().isEmpty()) {
                log.warn("AI服务返回空标题");
                return null;
            }
            log.info("AI服务返回原始标题: [{}]", rawTitle);

            // 清理标题
            String cleanedTitle = cleanTitle(rawTitle);
            log.info("标题清理完成: [{}] -> [{}]", rawTitle, cleanedTitle);

            // 保存标题到数据库
            if (cleanedTitle != null && !cleanedTitle.equals("New Chat")) {
                boolean saved = chatSessionService.updateSessionTitle(sessionId, cleanedTitle);
                if (saved) {
                    log.info("标题保存到数据库成功: {}", cleanedTitle);
                } else {
                    log.error("标题保存到数据库失败: {}", cleanedTitle);
                }
            } else {
                log.warn("标题为空或为默认标题，不保存到数据库: [{}]", cleanedTitle);
            }

            log.info("=== 后台同步标题生成流程完成，最终标题: [{}] ===", cleanedTitle);
            return cleanedTitle;

        } catch (Exception e) {
            log.error("后台同步标题生成过程中发生异常", e);
            return null;
        }
    }

    /**
     * 构建对话内容
     */
    private String buildConversationContent(List<Map<String, String>> messages) {
        log.info("开始构建对话内容，消息数量: {}", messages != null ? messages.size() : 0);

        if (messages == null || messages.isEmpty()) {
            log.warn("消息列表为空，无法构建对话内容");
            return null;
        }

        StringBuilder conversationBuilder = new StringBuilder();
        int userMessageCount = 0;
        int assistantMessageCount = 0;

        for (Map<String, String> message : messages) {
            String role = message.get("role");
            String content = message.get("content");

            if ("user".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("User: ").append(content.trim()).append("\n");
                userMessageCount++;
            } else if ("assistant".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("AI: ").append(content.trim()).append("\n");
                assistantMessageCount++;
            }
        }

        String result = conversationBuilder.toString().trim();
        log.info("对话内容构建完成，用户消息: {}条，AI消息: {}条，总长度: {}字符",
                userMessageCount, assistantMessageCount, result.length());

        if (result.isEmpty()) {
            log.warn("构建的对话内容为空");
            return null;
        }

        return result;
    }

    /**
     * 构建标题生成提示词
     */
    private String buildTitlePrompt(String conversationContent) {
        log.info("构建标题生成提示词，对话内容长度: {}字符", conversationContent != null ? conversationContent.length() : 0);

        String prompt = PromptConstants.TITLE_GENERATION_PROMPT
            .replace("{conversationContent}", conversationContent);

        log.debug("标题生成提示词构建完成，总长度: {}字符", prompt.length());
        return prompt;
    }

    /**
     * 清理标题内容
     */
    private String cleanTitle(String title) {
        log.info("开始清理标题，原始标题: [{}]", title);

        if (title == null || title.trim().isEmpty()) {
            log.warn("标题为null或空，返回默认标题");
            return "New Chat";
        }

        // 基础清理：移除引号和多余空格
        String cleaned = title.trim()
            .replaceAll("^[\"']+|[\"']+$", "") // 移除开头和结尾的引号
            .replaceAll("\\s+", " ") // 合并多个空格
            .trim();

        log.info("标题清理完成: [{}] -> [{}]", title, cleaned);

        // 如果清理后为空，返回默认标题
        if (cleaned.isEmpty()) {
            log.warn("清理后标题为空，返回默认标题");
            return "New Chat";
        }

        return cleaned;
    }

    /**
     * 使用HTTP客户端调用AI服务生成标题
     */
    private String callAiForTitleWithHttpClient(String titlePrompt, AiModel model) {
        try {
            log.info("开始使用HTTP客户端调用AI服务生成标题");

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());

            List<Map<String, String>> titleMessages = new ArrayList<>();
            Map<String, String> titleMessage = new HashMap<>();
            titleMessage.put("role", "user");
            titleMessage.put("content", titlePrompt);
            titleMessages.add(titleMessage);

            requestBody.put("messages", titleMessages);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 50);
            requestBody.put("temperature", 0.7);

            // 记录详细的AI标题生成请求信息
            logAiTitleRequestDetails(titleMessages, model);

            log.info("AI标题生成请求体构建完成");

            // 使用RestTemplate进行HTTP调用
            RestTemplate restTemplate = new RestTemplate();

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(model.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("开始发送HTTP请求到: {}", model.getBaseUrl());

            // 发送请求并获取响应
            ResponseEntity<String> response = restTemplate.postForEntity(
                model.getBaseUrl(),
                entity,
                String.class
            );

            String responseBody = response.getBody();
            log.info("收到AI服务响应，响应长度: {}字符", responseBody != null ? responseBody.length() : 0);

            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.warn("AI服务返回空响应");
                return null;
            }

            // 解析响应
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(responseBody);
            log.info("AI服务响应JSON解析成功");

            JsonNode choices = responseJson.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String title = message.get("content").asText();
                    log.info("AI服务返回标题: [{}]", title);
                    return title;
                } else {
                    log.warn("message节点不存在或没有content字段");
                }
            } else {
                log.warn("choices节点为空或不是数组");
            }

            log.warn("AI服务响应格式异常，完整响应: {}", responseBody);
            return null;

        } catch (Exception e) {
            log.error("使用HTTP客户端调用AI服务生成标题失败", e);
            return null;
        }
    }

    @Override
    public List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId) {
        // 如果没有文件ID或消息列表为空，直接返回原消息列表
        if (filesId == null || filesId.trim().isEmpty() || messages == null || messages.isEmpty()) {
            return messages;
        }

        try {
            // 解析文件ID列表
            String[] fileIds = filesId.split(",");
            if (fileIds.length == 0) {
                return messages;
            }

            // 获取文件内容
            StringBuilder fileContentBuilder = new StringBuilder();
            fileContentBuilder.append("以下是文件内容：[");

            boolean hasValidFiles = false;
            for (int i = 0; i < fileIds.length; i++) {
                String fileId = fileIds[i].trim();
                if (fileId.isEmpty()) {
                    continue;
                }

                try {
                    // 获取文件信息和内容
                    String fileContent = fileUploadService.getFileContent(fileId, userId);
                    if (fileContent != null && !fileContent.trim().isEmpty()) {
                        // 获取文件名
                        com.instser.chat.entity.FileUpload fileUpload = fileUploadService.getByFileId(fileId);
                        String fileName = fileUpload != null ? fileUpload.getFileName() : "unknown";

                        if (hasValidFiles) {
                            fileContentBuilder.append(",");
                        }

                        // 构建文件内容格式：{"fileId_123_name":"fileId_123_content"}
                        fileContentBuilder.append("{\"fileId_").append(fileId).append("_").append(fileName)
                                         .append("\":\"").append(escapeJsonString(fileContent)).append("\"}");

                        hasValidFiles = true;
                        log.info("成功获取文件内容，文件ID：{}，文件名：{}，内容长度：{}", fileId, fileName, fileContent.length());
                    } else {
                        log.warn("文件内容为空或无法获取，文件ID：{}", fileId);
                    }
                } catch (Exception e) {
                    log.error("获取文件内容失败，文件ID：{}", fileId, e);
                }
            }

            fileContentBuilder.append("]");

            // 如果有有效的文件内容，则附加到最后一条用户消息中
            if (hasValidFiles) {
                // 找到最后一条用户消息
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, String> message = messages.get(i);
                    if ("user".equals(message.get("role"))) {
                        String originalContent = message.get("content");
                        String newContent = originalContent + "。" + fileContentBuilder.toString();
                        message.put("content", newContent);

                        log.info("已将文件内容附加到用户消息中，原始内容长度：{}，附加后长度：{}",
                                originalContent.length(), newContent.length());
                        break;
                    }
                }
            }

            return messages;

        } catch (Exception e) {
            log.error("处理文件内容失败", e);
            return messages; // 出错时返回原消息列表
        }
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 记录详细的AI请求信息
     */
    private void logAiRequestDetails(List<Map<String, String>> messages, AiModel model, Long userId) {
        try {
            log.info("=== AI请求详细信息 ===");
            log.info("用户ID: {}", userId);
            log.info("模型: {} ({})", model.getModelCode(), model.getBaseUrl());
            log.info("消息总数: {}", messages.size());

            // 记录每条消息的详细信息
            for (int i = 0; i < messages.size(); i++) {
                Map<String, String> message = messages.get(i);
                String role = message.get("role");
                String content = message.get("content");

                if (content != null) {
                    String truncatedContent = truncateContent(content);
                    log.info("消息[{}] - 角色: {}, 内容: {}", i + 1, role, truncatedContent);
                } else {
                    log.info("消息[{}] - 角色: {}, 内容: null", i + 1, role);
                }
            }

            log.info("=== AI请求信息结束 ===");
        } catch (Exception e) {
            log.warn("记录AI请求详细信息失败: {}", e.getMessage());
        }
    }

    /**
     * 记录详细的AI标题生成请求信息
     */
    private void logAiTitleRequestDetails(List<Map<String, String>> messages, AiModel model) {
        try {
            log.info("=== AI标题生成请求详细信息 ===");
            log.info("模型: {} ({})", model.getModelCode(), model.getBaseUrl());
            log.info("消息总数: {}", messages.size());

            // 记录每条消息的详细信息
            for (int i = 0; i < messages.size(); i++) {
                Map<String, String> message = messages.get(i);
                String role = message.get("role");
                String content = message.get("content");

                if (content != null) {
                    String truncatedContent = truncateContent(content);
                    log.info("标题生成消息[{}] - 角色: {}, 提示词: {}", i + 1, role, truncatedContent);
                } else {
                    log.info("标题生成消息[{}] - 角色: {}, 提示词: null", i + 1, role);
                }
            }

            log.info("=== AI标题生成请求信息结束 ===");
        } catch (Exception e) {
            log.warn("记录AI标题生成请求详细信息失败: {}", e.getMessage());
        }
    }

    /**
     * 截取内容，保留前15和后15个字符
     */
    private String truncateContent(String content) {
        if (content == null) {
            return "null";
        }

        if (content.length() <= 30) {
            return content;
        }

        String prefix = content.substring(0, 15);
        String suffix = content.substring(content.length() - 15);
        return prefix + "..." + suffix + " (总长度: " + content.length() + "字符)";
    }

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("认证信息为空或未认证");
            return null;
        }
        try {
            // 检查是否是CustomUserDetails类型
            Object principal = authentication.getPrincipal();
            log.debug("认证主体类型：{}", principal != null ? principal.getClass().getName() : "null");
            if (principal instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) principal;
                Long userId = userDetails.getUserId();
                log.debug("从认证信息中获取到用户ID：{}", userId);
                return userId;
            }
            // 如果不是CustomUserDetails，返回null表示认证失败
            log.warn("认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return null;
        }
    }

    @Override
    public void processRetryChat(String sessionId, String messageId, Long modelId, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理重新生成请求，sessionId：{}，messageId：{}，modelId：{}", sessionId, messageId, modelId);

        try {
            // 设置响应头
            setStreamResponseHeaders(response);

            // 验证聊天权限和会话
            ChatValidationResult validation = validateChatPermissions(sessionId, messageId, "assistant", authentication);
            if (!validation.isSuccess()) {
                writeErrorResponse(response, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            log.info("重新生成请求 - 验证通过，用户ID：{}，会话ID：{}", userId, sessionId);

            // 删除指定AI消息及之后的所有消息
            int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
            if (deletedCount == 0) {
                writeErrorResponse(response, "删除消息失败");
                return;
            }

            log.info("成功删除{}条消息，准备重新生成AI回复", deletedCount);

            // 获取删除后的会话消息，构建新的聊天请求
            List<ChatMessage> remainingMessages = chatMessageService.getSessionMessages(sessionId);
            if (remainingMessages.isEmpty()) {
                writeErrorResponse(response, "会话中没有可用的消息");
                return;
            }

            // 找到最后一条用户消息
            ChatMessage lastUserMessage = null;
            for (int i = remainingMessages.size() - 1; i >= 0; i--) {
                ChatMessage message = remainingMessages.get(i);
                if ("user".equals(message.getRole())) {
                    lastUserMessage = message;
                    break;
                }
            }

            if (lastUserMessage == null) {
                writeErrorResponse(response, "未找到用户消息");
                return;
            }

            // 构建消息列表用于AI请求
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage message : remainingMessages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", message.getRole());
                msgMap.put("content", message.getContent());
                messages.add(msgMap);
            }

            // 获取并验证AI模型
            AiModel model = getAndValidateModel(modelId, chatSession);
            if (model == null) {
                writeErrorResponse(response, "AI模型不可用");
                return;
            }

            // 更新会话模型（如果需要）
            updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.getCanChat()) {
                String errorMessage = limitCheck.getLimitReason() != null ?
                    limitCheck.getLimitReason() : "聊天权限验证失败";
                writeErrorResponse(response, errorMessage);
                return;
            }

            // 构建ChatRequestDTO
            ChatRequestDTO retryRequest = new ChatRequestDTO();
            retryRequest.setMessages(messages);
            retryRequest.setUserId(userId);
            retryRequest.setModelId(model.getId());
            retryRequest.setSessionId(sessionId);
            retryRequest.setDeepThinking(false); // 重新生成时使用默认模式

            // 判断是否需要生成标题（如果删除后只剩一条用户消息，则需要生成标题）
            boolean willGenerateTitle = isFirstAiResponse(messages);
            log.info("重新生成请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                messages.stream().filter(msg -> "user".equals(msg.get("role"))).count());

            // 调用AI服务进行流式响应
            ChatResponseDTO result = streamChatWithAI(retryRequest, model, response, willGenerateTitle);

            // 处理响应结果
            handleAiResponseResult(result, userId, sessionId, model, "重新生成");

        } catch (Exception e) {
            log.error("处理重新生成请求异常", e);
            try {
                String errorContent = "请求错误";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                var chatSession = chatSessionService.getSessionInfo(sessionId);
                if (chatSession != null) {
                    AiModel model = aiModelService.getById(chatSession.getModelId());
                    if (model != null) {
                        saveErrorResponse(sessionId, chatSession.getUserId(), model.getId(), errorContent);
                    }
                }
            } catch (Exception ex) {
                log.error("发送重新生成错误消息失败", ex);
            }
        }
    }

    @Override
    public void processEditResendChat(String sessionId, String messageId, String newContent, Long modelId, Authentication authentication, HttpServletResponse response) {
        log.info("开始处理编辑重发请求，sessionId：{}，messageId：{}，newContent长度：{}，modelId：{}", sessionId, messageId, newContent.length(), modelId);

        try {
            // 设置响应头
            setStreamResponseHeaders(response);

            // 验证聊天权限和会话
            ChatValidationResult validation = validateChatPermissions(sessionId, messageId, "user", authentication);
            if (!validation.isSuccess()) {
                writeErrorResponse(response, validation.getErrorMessage());
                return;
            }

            Long userId = validation.getUserId();
            ChatSession chatSession = validation.getChatSession();
            log.info("编辑重发请求 - 验证通过，用户ID：{}，会话ID：{}", userId, sessionId);

            // 删除指定用户消息及之后的所有消息
            int deletedCount = chatMessageService.deleteMessagesFromPoint(messageId, sessionId);
            if (deletedCount == 0) {
                writeErrorResponse(response, "删除消息失败");
                return;
            }

            log.info("成功删除{}条消息，准备保存新的用户消息", deletedCount);

            // 保存新的用户消息
            ChatMessage newUserMessage = new ChatMessage();
            newUserMessage.setMessageId(java.util.UUID.randomUUID().toString());
            newUserMessage.setSessionId(sessionId);
            newUserMessage.setRole("user");
            newUserMessage.setContent(newContent);
            newUserMessage.setMessageType("text");
            newUserMessage.setUserId(userId);
            newUserMessage.setModelId(chatSession.getModelId());
            newUserMessage.setStatus(1); // 成功状态

            boolean saved = chatMessageService.save(newUserMessage);
            if (!saved) {
                writeErrorResponse(response, "保存用户消息失败");
                return;
            }

            log.info("成功保存新的用户消息，messageId：{}", newUserMessage.getMessageId());

            // 获取更新后的会话消息，构建新的聊天请求
            List<ChatMessage> remainingMessages = chatMessageService.getSessionMessages(sessionId);
            if (remainingMessages.isEmpty()) {
                writeErrorResponse(response, "会话中没有可用的消息");
                return;
            }

            // 构建消息列表用于AI请求
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage message : remainingMessages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", message.getRole());
                msgMap.put("content", message.getContent());
                messages.add(msgMap);
            }

            // 获取并验证AI模型
            AiModel model = getAndValidateModel(modelId, chatSession);
            if (model == null) {
                writeErrorResponse(response, "AI模型不可用");
                return;
            }

            // 更新会话模型（如果需要）
            updateSessionModelIfNeeded(sessionId, modelId, chatSession.getModelId());

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.getCanChat()) {
                String errorMessage = limitCheck.getLimitReason() != null ?
                    limitCheck.getLimitReason() : "聊天权限验证失败";
                writeErrorResponse(response, errorMessage);
                return;
            }

            // 构建ChatRequestDTO
            ChatRequestDTO editResendRequest = new ChatRequestDTO();
            editResendRequest.setMessages(messages);
            editResendRequest.setUserId(userId);
            editResendRequest.setModelId(model.getId());
            editResendRequest.setSessionId(sessionId);
            editResendRequest.setDeepThinking(false); // 编辑重发时使用默认模式

            // 判断是否需要生成标题（如果编辑的是第一条用户消息，则需要生成标题）
            boolean willGenerateTitle = isFirstAiResponse(messages);
            log.info("编辑重发请求 - 是否需要生成标题：{}，用户消息数量：{}", willGenerateTitle,
                messages.stream().filter(msg -> "user".equals(msg.get("role"))).count());

            // 调用AI服务进行流式响应
            ChatResponseDTO result = streamChatWithAI(editResendRequest, model, response, willGenerateTitle);

            // 处理响应结果
            handleAiResponseResult(result, userId, sessionId, model, "编辑重发");

        } catch (Exception e) {
            log.error("处理编辑重发请求异常", e);
            try {
                String errorContent = "请求错误";
                writeErrorResponse(response, errorContent);

                // 保存错误响应到数据库
                var chatSession = chatSessionService.getSessionInfo(sessionId);
                if (chatSession != null) {
                    AiModel model = aiModelService.getById(chatSession.getModelId());
                    if (model != null) {
                        saveErrorResponse(sessionId, chatSession.getUserId(), model.getId(), errorContent);
                    }
                }
            } catch (Exception ex) {
                log.error("发送编辑重发错误消息失败", ex);
            }
        }
    }
}
