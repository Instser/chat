package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.constants.PromptConstants;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
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

            // 保存用户消息到数据库
            if (request.getSessionId() != null) {
                saveUserMessage(request.getMessages(), request.getSessionId(), request.getUserId());
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
    public void saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId) {
        try {
            if (messages == null || messages.isEmpty() || sessionId == null || userId == null) {
                log.warn("无法保存用户消息到数据库，缺少必要参数");
                return;
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
                chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId);
                log.info("用户消息已保存到数据库，会话ID：{}，用户ID：{}，内容长度：{}", sessionId, userId, lastUserMessage.length());
            } else {
                log.warn("未找到有效的用户消息，跳过保存");
            }
        } catch (Exception e) {
            log.error("保存用户消息到数据库失败", e);
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

            // 添加JSON数据缓冲区，用于处理分片数据
            StringBuilder jsonBuffer = new StringBuilder();

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
                        String[] lines = chunk.split("\n");
                        List<String> validLines = new ArrayList<>();
                        for (String line : lines) {
                            if (!line.trim().isEmpty()) {
                                validLines.add(line);
                            }
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
                            }
                        } catch (Exception e) {
                            log.error("处理流式数据失败: {}", e.getMessage());
                            jsonBuffer.setLength(0);
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
            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    String content = delta.get("content").asText();
                    if (content != null && !content.isEmpty()) {
                        String cleanContent = cleanContent(content);

                        if (!cleanContent.isEmpty()) {
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
            log.error("处理完整JSON数据失败: {}", e.getMessage());
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
}
