package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.result.Result;
import com.instser.chat.constants.PromptConstants;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatLimitService;

import com.instser.chat.vo.ChatLimitVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 聊天API控制器 - 兼容前端接口
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "true")
public class ChatApiController {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final AiModelService aiModelService;
    private final ChatLimitService chatLimitService;

    // 智能分块响应配置
    private static final boolean ENABLE_SMART_CHUNKING = true; // 是否启用智能分块流式响应
    private static final long CHUNK_DELAY_MS = 30; // 分块间延迟（毫秒）
    private static final int MIN_CHUNK_SIZE = 5; // 最小分块大小
    private static final int MAX_CHUNK_SIZE = 25; // 最大分块大小


    /**
     * 流式聊天接口 - 兼容前端 (POST)
     */
    @PostMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chat(@RequestBody Map<String, Object> request, Authentication authentication,
                     HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("收到聊天请求：{}, 认证信息：{}", request, authentication);

        // 设置Vercel AI SDK响应头和字符编码
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-vercel-ai-data-stream", "v1");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        // 动态设置跨域头
        setCorsHeaders(httpRequest, response);

        // 声明变量在try块外面，以便在catch块中使用
        String sessionId = null;
        Long userId = null;
        AiModel model = null;
        Long modelId = null;

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");
            Object deepThinkingObj = request.get("deepThinking");
            sessionId = (String) request.get("sessionId"); // 提取sessionId
            Object modelIdObj = request.get("modelId");

            if (messages == null || messages.isEmpty()) {
                response.getWriter().write("3:\"消息列表不能为空\"\n");
                response.getWriter().flush();
                return;
            }

            if (userIdObj == null) {
                response.getWriter().write("3:\"用户ID不能为空\"\n");
                response.getWriter().flush();
                return;
            }

            if (modelIdObj == null) {
                response.getWriter().write("3:\"Ai模型ID不能为空\"\n");
                response.getWriter().flush();
                return;
            }
            // 保存用户消息到数据库 - 在AI响应之前先保存用户消息
            if (sessionId != null) {
                saveUserMessageToDatabase(messages, sessionId, Long.valueOf(userIdObj.toString()));
            }

            // // 硬编码使用ID为1的模型（已配置为qwen3-235b-a22b）
            // Long modelId = 1L;
            userId = Long.valueOf(userIdObj.toString());
            modelId = Long.valueOf(modelIdObj.toString());

            // 获取模型配置
            model = aiModelService.getById(modelId);
            if (model == null || model.getIsAvailable() != 1) {
                response.getWriter().write("3:\"AI模型不可用\"\n");
                response.getWriter().flush();
                return;
            }
            System.out.println("当前模型为：" + model.getModelName() + " url：" + model.getBaseUrl());

            // 验证消息格式
            Map<String, String> lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.get("role");

            if (!"user".equals(role)) {
                response.getWriter().write("3:\"最后一条消息必须是用户消息\"\n");
                response.getWriter().flush();
                return;
            }

            // 验证用户权限（确保认证用户有权限访问）
            if (authentication == null || !authentication.isAuthenticated()) {
                response.getWriter().write("3:\"用户未认证\"\n");
                response.getWriter().flush();
                return;
            }

            // 验证用户聊天权限和套餐限制
            ChatLimitVO limitCheck = chatLimitService.validateChatPermission(userId);
            if (!limitCheck.getCanChat()) {
                String errorMessage = limitCheck.getLimitReason() != null ?
                    limitCheck.getLimitReason() : "聊天权限验证失败";
                response.getWriter().write("3:\"" + errorMessage + "\"\n");
                response.getWriter().flush();
                log.warn("用户{}聊天权限验证失败: {}", userId, errorMessage);
                return;
            }

            log.info("用户{}聊天权限验证通过，套餐：{}，限制类型：{}",
                    userId, limitCheck.getPackageName(), limitCheck.getLimitType());
            log.info("所有验证通过，开始直接流式响应");

            // 解析deepThinking参数
            boolean isDeepThink = false;
            if (deepThinkingObj != null) {
                if (deepThinkingObj instanceof Boolean) {
                    isDeepThink = (Boolean) deepThinkingObj;
                } else if (deepThinkingObj instanceof String) {
                    isDeepThink = "true".equalsIgnoreCase((String) deepThinkingObj) || "1".equals(deepThinkingObj);
                } else if (deepThinkingObj instanceof Number) {
                    isDeepThink = ((Number) deepThinkingObj).intValue() == 1;
                }
            }

            log.info("deepThinking参数: {}, 解析结果: {}", deepThinkingObj, isDeepThink);

            // 直接在当前线程中调用AI服务，避免异步问题
            log.info("开始调用AI服务，模型：{}，用户：{}", model.getModelName(), userId);
            // 判断是否会生成标题（只有真正的AI响应且是第一次AI回复才会生成标题）
            boolean willGenerateTitle = sessionId != null && isFirstAiResponse(messages);

            // 直接调用AI服务，传入response对象进行直接写入，返回结果包含成功状态和是否为真实AI响应
            ChatResponseResult chatResult = streamChatDirectWithResult(messages, model, response, isDeepThink, sessionId, userId, willGenerateTitle);

            // 只有AI服务真正成功返回内容时才记录使用次数
            if (chatResult.isRealAiResponse()) {
                boolean recordSuccess = chatLimitService.recordChatUsage(userId);
                if (recordSuccess) {
                    log.info("用户{}聊天使用次数记录成功", userId);
                } else {
                    log.warn("用户{}聊天使用次数记录失败", userId);
                }

                // 保存AI响应到数据库
                if (sessionId != null && chatResult.getAiResponseContent() != null && !chatResult.getAiResponseContent().trim().isEmpty()) {
                    saveAiResponseToDatabase(sessionId, userId, model, chatResult.getAiResponseContent());
                }

                // 标题生成已在流式响应中完成，这里不需要再次生成
                log.info("用户{}AI响应成功，标题已在流式响应中处理", userId);
            } else if (chatResult.hasContentSent()) {
                // 有内容发送（错误响应），但不记录使用次数，也不生成标题
                log.info("用户{}收到错误响应，已保存聊天记录但不记录使用次数，不生成标题", userId);
            } else {
                log.warn("用户{}AI服务调用失败，不记录聊天次数", userId);
            }

        } catch (Exception e) {
            log.error("聊天接口异常", e);
            try {
                // 返回通用错误消息，不暴露具体错误信息
                String errorContent = "请求错误";
                String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                response.getWriter().write(vercelFormat);

                // 发送完成标记（错误响应，不生成标题）
                String completionData = buildCompletionData(false, false);
                response.getWriter().write(completionData);
                response.getWriter().flush();

                // 保存错误消息作为AI响应到数据库，但不扣减使用次数
                saveErrorResponseToDatabase(sessionId, userId, model, errorContent);

            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
            }
        }
    }



    /**
     * 获取可用模型列表
     */
    @GetMapping("/models")
    public Result<List<AiModel>> getModels() {
        List<AiModel> models = aiModelService.getAvailableModels();
        return Result.success(models);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public Result<Map<String, String>> createSession(@RequestBody Map<String, Object> request) {
        Long userId = 1L; // 暂时使用固定用户ID，后续可以从JWT中获取
        String modelCode = (String) request.getOrDefault("model", "qwen3-235b-a22b");

        AiModel model = aiModelService.getModelByCode(modelCode);
        if (model == null) {
            return Result.failed("指定的模型不存在");
        }

        String sessionId = chatSessionService.createSession(userId, model.getId());
        return Result.success(Map.of("sessionId", sessionId));
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getSessions() {
        Long userId = 1L; // 暂时使用固定用户ID
        List<ChatSession> sessions = chatSessionService.getUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatMessageService.getSessionMessages(sessionId);
        return Result.success(messages);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        boolean deleted = chatSessionService.deleteSession(sessionId);
        if (!deleted) {
            return Result.failed("会话删除失败");
        }
        return Result.success(null, "会话删除成功");
    }



    /**
     * 直接流式聊天方法 - 返回详细结果，区分真实AI响应和错误响应
     * @return ChatResponseResult 包含是否有内容发送、是否为真实AI响应和是否会生成标题
     */
    private ChatResponseResult streamChatDirectWithResult(List<Map<String, String>> messages, AiModel model, HttpServletResponse response, boolean isDeepThink, String sessionId, Long userId, boolean willGenerateTitle) {
        // 用于跟踪是否有AI内容被成功发送和是否为真实AI响应
        final boolean[] hasContentSent = {false};
        final boolean[] isRealAiResponse = {false};
        // 用于收集完整的AI响应内容
        final StringBuilder aiResponseContent = new StringBuilder();

        try {
            // 添加系统提示词到消息列表开头
            List<Map<String, String>> messagesWithPrompt = new ArrayList<>();

            // 根据deepthink参数选择提示词
            String systemPrompt = isDeepThink ? PromptConstants.DEEP_THINKING_PROMPT : PromptConstants.DEFAULT_SYSTEM_PROMPT;

            // 添加系统提示词
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesWithPrompt.add(systemMessage);

            // 添加用户的消息
            messagesWithPrompt.addAll(messages);

            log.info("使用{}提示词，消息总数: {}", isDeepThink ? "深度思考" : "默认", messagesWithPrompt.size());

            // 添加JSON数据缓冲区，用于处理分片数据
            StringBuilder jsonBuffer = new StringBuilder();

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messagesWithPrompt);
            requestBody.put("stream", true);
            requestBody.put("reasoning_effort", "none");

            // 打印最终向AI发送的真实完整JSON（包含完整的系统提示词）
            try {
                ObjectMapper logMapper = new ObjectMapper();
                String actualRequestJson = logMapper.writeValueAsString(requestBody);
                log.info("POST请求完整JSON（真实发送内容）: {}", actualRequestJson);
            } catch (Exception e) {
                log.warn("无法序列化实际请求JSON: {}", e.getMessage());
            }

            // 创建WebClient，确保UTF-8编码 - 使用直接完整URL
            WebClient webClient = WebClient.builder()
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .defaultHeader("Accept-Charset", "UTF-8")
                    .defaultHeader("User-Agent", "OpenAI/PowerShell")  // 添加与成功脚本相同的User-Agent
                    .build();

            log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());

            // 发送流式请求并直接处理响应 - 使用直接完整URL
            webClient.post()
                    .uri(model.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofSeconds(30))
                    .map(dataBuffer -> {
                        // 使用更安全的UTF-8字符编码转换
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        // 创建字符串，保留所有Unicode字符
                        String chunk = new String(bytes, StandardCharsets.UTF_8);

                        // 只移除真正有问题的字符，保留emoji和其他Unicode字符
                        chunk = chunk.replaceAll("[\uFFFD\u0000]", "");

                        return chunk;
                    })
                    .flatMap(chunk -> {
                        // 按换行符分割处理
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

                                    try {
                                        String generatedTitle = null;

                                        // 如果需要生成标题且是真实AI响应，等待标题生成完成
                                        if (willGenerateTitle && isRealAiResponse[0]) {
                                            log.info("开始生成标题，会话ID: {}", sessionId);
                                            try {
                                                // 使用CompletableFuture在后台线程中生成标题，等待完成
                                                // 固定使用ID为1L的模型进行标题生成
                                                CompletableFuture<String> titleFuture = CompletableFuture.supplyAsync(() -> {
                                                    try {
                                                        return generateTitleSyncInBackground(sessionId, messages);
                                                    } catch (Exception e) {
                                                        log.error("后台标题生成失败", e);
                                                        return null;
                                                    }
                                                });
                                                generatedTitle = titleFuture.get(5, TimeUnit.SECONDS); // 最多等待5秒

                                                if (generatedTitle != null && !generatedTitle.equals("New Chat")) {
                                                    log.info("标题生成成功: {}", generatedTitle);
                                                } else {
                                                    log.info("标题生成失败或返回默认标题: {}", generatedTitle);
                                                    generatedTitle = null; // 失败时设置为null，前端会收到false
                                                }
                                            } catch (TimeoutException e) {
                                                log.warn("标题生成超时，继续发送响应");
                                                generatedTitle = null;
                                            } catch (Exception e) {
                                                log.error("标题生成异常", e);
                                                generatedTitle = null;
                                            }
                                        } else {
                                            log.info("不需要生成标题，willGenerateTitle: {}, isRealAiResponse: {}",
                                                    willGenerateTitle, isRealAiResponse[0]);
                                        }

                                        // 发送Vercel AI SDK完成标记，包含生成的标题
                                        String completionData = buildCompletionData(willGenerateTitle, isRealAiResponse[0], generatedTitle);
                                        response.getWriter().write(completionData);
                                        response.getWriter().flush();

                                        if (willGenerateTitle && isRealAiResponse[0]) {
                                            log.info("Vercel AI SDK完成标记发送成功，title: async (异步生成中)");
                                        } else {
                                            log.info("Vercel AI SDK完成标记发送成功，title: false (不生成标题)");
                                        }
                                    } catch (Exception e) {
                                        log.error("处理完成标记时发生异常", e);
                                        // 即使出现异常，也要尝试发送完成标记
                                        try {
                                            String completionData = buildCompletionData(false, false, null);
                                            response.getWriter().write(completionData);
                                            response.getWriter().flush();
                                        } catch (Exception ex) {
                                            log.error("发送异常完成标记失败", ex);
                                        }
                                    }
                                    return;
                                }

                                // 提取JSON数据部分
                                String jsonData = line.substring(6).trim();

                                // 将当前行的JSON数据添加到缓冲区
                                jsonBuffer.append(jsonData);

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    // JSON完整，可以解析
                                    String contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent != null) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true; // 标记为真实AI响应
                                        aiResponseContent.append(contentSent); // 收集AI响应内容
                                    }
                                    // 清空缓冲区
                                    jsonBuffer.setLength(0);
                                } else {
                                    // JSON不完整，等待更多数据
                                    log.debug("JSON数据不完整，等待更多数据: [{}]", bufferedJson);
                                }
                            } else if (!line.startsWith("data:") && jsonBuffer.length() > 0) {
                                // 这可能是分片JSON的剩余部分
                                jsonBuffer.append(line);

                                // 再次尝试解析
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    String contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent != null) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true; // 标记为真实AI响应
                                        aiResponseContent.append(contentSent); // 收集AI响应内容
                                    }
                                    jsonBuffer.setLength(0);
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理流式数据失败: {}", e.getMessage());
                            // 清空缓冲区，避免错误数据影响后续处理
                            jsonBuffer.setLength(0);
                        }
                    })
                    .doOnError(error -> {
                        log.error("AI API流式响应处理失败", error);
                        try {
                            // 返回通用错误消息，不暴露具体错误信息
                            String errorContent = "请求错误";
                            String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                            response.getWriter().write(vercelFormat);

                            // 发送完成标记（错误响应，不生成标题）
                            String completionData = buildCompletionData(false, false);
                            response.getWriter().write(completionData);
                            response.getWriter().flush();

                            // 标记有内容发送，这样会保存聊天记录但不扣减使用次数
                            hasContentSent[0] = true;

                            // 保存错误响应到数据库
                            saveErrorResponseToDatabase(sessionId, userId, model, errorContent);
                        } catch (Exception e) {
                            log.error("发送错误消息失败", e);
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("AI API流式响应处理完成");
                    })
                    .onErrorReturn("") // 提供默认值，避免 fallbackValue must not be null 错误
                    .blockLast(); // 阻塞等待完成

            // 返回聊天响应结果，包含AI响应内容
            return new ChatResponseResult(hasContentSent[0], isRealAiResponse[0], aiResponseContent.toString());

        } catch (Exception e) {
            log.error("直接流式聊天异常", e);
            try {
                // 返回通用错误消息，不暴露具体错误信息
                String errorContent = "请求错误";
                String vercelFormat = String.format("0:%s\n", new ObjectMapper().writeValueAsString(errorContent));
                response.getWriter().write(vercelFormat);

                // 发送完成标记（错误响应，不生成标题）
                String completionData = buildCompletionData(false, false);
                response.getWriter().write(completionData);
                response.getWriter().flush();

                // 保存错误响应到数据库
                saveErrorResponseToDatabase(sessionId, userId, model, errorContent);

                // 异常情况下返回错误响应结果，有内容发送但不是真实AI响应
                return new ChatResponseResult(true, false, "");
            } catch (IOException ioException) {
                log.error("发送异常消息失败", ioException);
                // 如果连错误消息都发送失败，返回完全失败的结果
                return new ChatResponseResult(false, false, "");
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
     * 清理内容，移除无效字符和替换字符
     */
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }

        // 只移除最关键的问题字符，避免过度过滤
        // \uFFFD: 替换字符（�）
        // \u0000: 空字符
        String cleaned = content.replaceAll("[\uFFFD\u0000]", "");

        // 移除连续的多个空格，但保留单个空格和换行
        cleaned = cleaned.replaceAll(" {2,}", " ");

        return cleaned;
    }



    /**
     * 处理完整的JSON数据 - 支持快速单字符响应
     * @return 发送的AI内容，null表示没有发送内容
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
                        // 调试：记录原始内容
                        log.debug("原始AI内容: [{}]", content.replace("\n", "\\n").replace("\r", "\\r"));

                        // 清理内容，移除无效字符
                        String cleanContent = cleanContent(content);

                        // 调试：记录清理后内容
                        if (!content.equals(cleanContent)) {
                            log.debug("内容已清理: [{}] -> [{}]",
                                content.replace("\n", "\\n").replace("\r", "\\r"),
                                cleanContent.replace("\n", "\\n").replace("\r", "\\r"));
                        }

                        if (!cleanContent.isEmpty()) {
                            // 根据配置选择响应模式
                            if (ENABLE_SMART_CHUNKING) {
                                // 智能分块发送，根据内容长度动态调整分块大小
                                sendContentBySmartChunking(cleanContent, response, mapper);
                            } else {
                                // 传统块发送模式
                                sendContentAsBlock(cleanContent, response, mapper);
                            }
                            // 返回发送的内容
                            return cleanContent;
                        }
                    }
                }
            }
            // 没有发送内容
            return null;
        } catch (Exception e) {
            log.error("处理完整JSON数据失败: {}", e.getMessage());
            // 处理失败，没有发送内容
            return null;
        }
    }

    /**
     * 智能分块发送内容，根据内容长度动态调整分块大小
     * 支持智能字符处理，正确处理emoji等多字节Unicode字符
     */
    protected void sendContentBySmartChunking(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            // 根据内容长度计算最优分块大小
            int optimalChunkSize = getOptimalChunkSize(content);

            // 使用codePoints正确处理emoji等多字节Unicode字符
            int[] codePoints = content.codePoints().toArray();
            StringBuilder chunk = new StringBuilder();
            int sentChars = 0;

            for (int i = 0; i < codePoints.length; i++) {
                int codePoint = codePoints[i];

                // 将codePoint转换为字符串
                String character = new String(Character.toChars(codePoint));

                // 跳过无效字符（只检查替换字符和空字符）
                if (codePoint != 0xFFFD && codePoint != 0x0000) {
                    chunk.append(character);
                }

                // 当达到分块大小或者是最后一个字符时发送
                if (chunk.length() >= optimalChunkSize || i == codePoints.length - 1) {
                    try {
                        String chunkContent = chunk.toString();

                        // 发送Vercel AI SDK格式: 0:"content"\n
                        String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(chunkContent));
                        response.getWriter().write(vercelFormat);
                        response.getWriter().flush();

                        sentChars += chunkContent.length();

                        // 记录发送的内容（对特殊字符进行转义显示）
                        String displayContent = chunkContent.replace("\n", "\\n")
                                                           .replace("\r", "\\r")
                                                           .replace("\t", "\\t");
                        log.debug("发送智能分块内容: [{}] ({}字符, 分块大小: {})", displayContent, chunkContent.length(), optimalChunkSize);

                        // 清空分块缓冲区
                        chunk.setLength(0);

                        // 添加延迟，控制发送速度
                        if (CHUNK_DELAY_MS > 0) {
                            Thread.sleep(CHUNK_DELAY_MS);
                        }

                    } catch (InterruptedException e) {
                        log.warn("分块发送被中断: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        log.error("发送分块内容失败: {}", e.getMessage());
                        // 如果分块发送失败，清空缓冲区继续下一块
                        chunk.setLength(0);
                        continue;
                    }
                }
            }

            log.info("完成智能分块流式发送，总字符数: {}, 实际发送: {}, 分块大小: {}", codePoints.length, sentChars, optimalChunkSize);

        } catch (Exception e) {
            log.error("智能分块发送内容失败: {}", e.getMessage());
            // 如果智能分块发送失败，回退到原来的块发送方式
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
     * 根据内容长度计算最优分块大小
     * 智能分块策略：短内容直接发送，中等内容小块发送，长内容中块发送
     */
    private int getOptimalChunkSize(String content) {
        int length = content.length();

        if (length <= 20) {
            // 短内容：直接发送全部内容
            return length;
        } else if (length <= 100) {
            // 中等内容：使用小分块
            return Math.max(MIN_CHUNK_SIZE, Math.min(length / 4, 10));
        } else if (length <= 300) {
            // 较长内容：使用中等分块
            return Math.max(10, Math.min(length / 8, 15));
        } else {
            // 长内容：使用大分块
            return Math.max(15, Math.min(MAX_CHUNK_SIZE, length / 12));
        }
    }

    /**
     * 检测是否为首次AI响应
     * 通过消息列表中用户消息的数量判断
     */
    private boolean isFirstAiResponse(List<Map<String, String>> messages) {
        // 统计用户消息数量
        long userMessageCount = messages.stream()
            .filter(msg -> "user".equals(msg.get("role")))
            .count();

        // 如果只有1条用户消息，说明这是首次AI响应
        return userMessageCount == 1;
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
     * 清理标题内容 - 移除长度校验，直接返回生成的标题
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

        // 直接返回清理后的标题，不进行长度和内容校验
        return cleaned;
    }

    /**
     * 传统块发送模式 - 一次性发送整个content
     */
    protected void sendContentAsBlock(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            // 确保内容已经清理过
            String cleanContent = cleanContent(content);

            if (!cleanContent.isEmpty()) {
                // 发送Vercel AI SDK格式: 0:"content"\n
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
     * 动态设置CORS跨域头
     */
    private void setCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");

        // 允许的源列表
        String[] allowedOrigins = {
            "http://localhost:3000",
            "http://localhost:3001",
            "https://localhost:3000",
            "https://localhost:3001",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3001",
            "https://127.0.0.1:3000",
            "https://127.0.0.1:3001",
            "http://192.168.1.100:3000",
            "http://192.168.1.100:3001",
            "https://192.168.1.100:3000",
            "https://192.168.1.100:3001",
            "http://192.168.1.101:3000",
            "http://192.168.1.101:3001",
            "https://192.168.1.101:3000",
            "https://192.168.1.101:3001"
        };

        // 检查请求来源是否在允许列表中
        if (origin != null) {
            for (String allowedOrigin : allowedOrigins) {
                if (origin.equals(allowedOrigin) || origin.startsWith(allowedOrigin.substring(0, allowedOrigin.lastIndexOf(':')))) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", "*");
                    return;
                }
            }
        }

        // 如果不在允许列表中，设置为localhost默认值
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
    }

    /**
     * 聊天响应结果类
     * 用于区分真正的AI响应和错误响应，以及是否生成标题
     */
    private static class ChatResponseResult {
        private final boolean hasContentSent;
        private final boolean isRealAiResponse;
        private final String aiResponseContent;



        public ChatResponseResult(boolean hasContentSent, boolean isRealAiResponse, String aiResponseContent) {
            this.hasContentSent = hasContentSent;
            this.isRealAiResponse = isRealAiResponse;
            this.aiResponseContent = aiResponseContent != null ? aiResponseContent : "";
        }

        public boolean hasContentSent() {
            return hasContentSent;
        }

        public boolean isRealAiResponse() {
            return isRealAiResponse;
        }



        public String getAiResponseContent() {
            return aiResponseContent;
        }
    }

    /**
     * 构建完成标记数据，包含title字段
     */
    private String buildCompletionData(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) {
        try {
            Map<String, Object> completionMap = new HashMap<>();
            completionMap.put("finishReason", "stop");
            completionMap.put("usage", Map.of(
                "promptTokens", 10,
                "completionTokens", 20
            ));

            // 使用title字段表示是否生成标题和标题内容
            if (isRealAiResponse && willGenerateTitle) {
                if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                    // 生成了标题，直接设置标题内容
                    completionMap.put("title", generatedTitle.trim());
                } else {
                    // 标题生成失败，设置为false
                    completionMap.put("title", false);
                }
            } else {
                // 不需要生成标题，设置为false
                completionMap.put("title", false);
            }

            return String.format("d:%s\n", new ObjectMapper().writeValueAsString(completionMap));
        } catch (Exception e) {
            log.error("构建完成标记数据失败", e);
            // 返回基本的完成标记
            return "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":10,\"completionTokens\":20},\"title\":false}\n";
        }
    }

    /**
     * 构建完成标记数据的重载方法，兼容原有调用
     */
    private String buildCompletionData(boolean willGenerateTitle, boolean isRealAiResponse) {
        return buildCompletionData(willGenerateTitle, isRealAiResponse, null);
    }





    /**
     * 保存用户消息到数据库
     * 从消息列表中提取最后一条用户消息并保存到数据库
     */
    private void saveUserMessageToDatabase(List<Map<String, String>> messages, String sessionId, Long userId) {
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
                // 保存用户消息到数据库
                chatMessageService.sendMessage(sessionId, "user", lastUserMessage, userId);
                log.info("用户消息已保存到数据库，会话ID：{}，用户ID：{}，内容长度：{}", sessionId, userId, lastUserMessage.length());
            } else {
                log.warn("未找到有效的用户消息，跳过保存");
            }
        } catch (Exception e) {
            log.error("保存用户消息到数据库失败", e);
        }
    }

    /**
     * 保存AI响应到数据库
     * 将完整的AI响应内容保存到数据库
     */
    private void saveAiResponseToDatabase(String sessionId, Long userId, AiModel model, String aiResponseContent) {
        try {
            if (sessionId != null && userId != null && model != null && aiResponseContent != null && !aiResponseContent.trim().isEmpty()) {
                // 保存AI响应到数据库
                chatMessageService.sendMessage(sessionId, "assistant", aiResponseContent, userId);
                log.info("AI响应已保存到数据库，会话ID：{}，用户ID：{}，内容长度：{}", sessionId, userId, aiResponseContent.length());
            } else {
                log.warn("无法保存AI响应到数据库，缺少必要参数：sessionId={}, userId={}, model={}, contentLength={}",
                        sessionId, userId, model != null ? model.getModelName() : "null",
                        aiResponseContent != null ? aiResponseContent.length() : 0);
            }
        } catch (Exception e) {
            log.error("保存AI响应到数据库失败", e);
        }
    }



    /**
     * 保存错误响应到数据库
     * 将AI请求错误作为正常的AI响应保存，但不扣减用户使用次数
     */
    private void saveErrorResponseToDatabase(String sessionId, Long userId, AiModel model, String errorContent) {
        try {
            if (sessionId != null && userId != null && model != null) {
                // 保存错误消息作为AI响应到数据库
                chatMessageService.sendMessage(sessionId, "assistant", errorContent, userId);
                log.info("错误响应已保存到数据库，会话ID：{}，用户ID：{}，内容：{}", sessionId, userId, errorContent);
            } else {
                log.warn("无法保存错误响应到数据库，缺少必要参数：sessionId={}, userId={}, model={}",
                        sessionId, userId, model != null ? model.getModelName() : "null");
            }
        } catch (Exception e) {
            log.error("保存错误响应到数据库失败", e);
        }
    }

    /**
     * 在后台线程中同步生成标题（避免阻塞响应式线程）
     * 固定使用ID为1L的模型进行标题生成
     * @param sessionId 会话ID
     * @param messages 消息列表
     * @return 生成的标题，失败时返回null
     */
    private String generateTitleSyncInBackground(String sessionId, List<Map<String, String>> messages) {
        log.info("=== 开始后台同步标题生成流程 ===");
        log.info("会话ID: {}, 消息数量: {}", sessionId, messages.size());

        try {
            // 步骤0: 获取固定的标题生成模型（ID为1L）
            log.info("步骤0: 获取固定的标题生成模型（ID为1L）");
            AiModel titleModel = aiModelService.getById(1L);
            if (titleModel == null || titleModel.getIsAvailable() != 1) {
                log.error("标题生成模型（ID=1L）不可用，跳过标题生成");
                return null;
            }
            log.info("标题生成模型: {}", titleModel.getModelName());

            // 步骤1: 构建对话内容
            log.info("步骤1: 构建对话内容");
            String conversationContent = buildConversationContent(messages);
            if (conversationContent == null || conversationContent.trim().isEmpty()) {
                log.warn("无法构建对话内容，跳过标题生成");
                return null;
            }
            log.info("对话内容构建成功，长度: {}字符", conversationContent.length());

            // 步骤2: 构建标题生成提示词
            log.info("步骤2: 构建标题生成提示词");
            String titlePrompt = buildTitlePrompt(conversationContent);
            log.info("标题提示词构建成功，长度: {}字符", titlePrompt.length());

            // 步骤3: 调用AI服务生成标题（使用HTTP客户端而不是WebClient.block()）
            log.info("步骤3: 调用AI服务生成标题，使用模型: {}", titleModel.getModelName());
            String rawTitle = callAiForTitleWithHttpClient(titlePrompt, titleModel);
            if (rawTitle == null || rawTitle.trim().isEmpty()) {
                log.warn("AI服务返回空标题");
                return null;
            }
            log.info("AI服务返回原始标题: [{}]", rawTitle);

            // 步骤4: 清理标题
            log.info("步骤4: 清理标题");
            String cleanedTitle = cleanTitle(rawTitle);
            log.info("标题清理完成: [{}] -> [{}]", rawTitle, cleanedTitle);

            // 步骤5: 保存标题到数据库
            if (cleanedTitle != null && !cleanedTitle.equals("New Chat")) {
                log.info("步骤5: 保存标题到数据库");
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
     * 使用HTTP客户端调用AI服务生成标题（避免WebClient阻塞问题）
     * @param titlePrompt 标题生成提示词
     * @param model AI模型
     * @return 生成的原始标题，失败时返回null
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
            requestBody.put("stream", false); // 非流式请求
            requestBody.put("max_tokens", 50); // 限制标题长度
            requestBody.put("temperature", 0.7); // 适中的创造性

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
