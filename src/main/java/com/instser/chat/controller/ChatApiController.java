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
import com.instser.chat.service.OpenAiService;
import com.instser.chat.vo.ChatLimitVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    private final OpenAiService openAiService;

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

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");
            Object deepThinkingObj = request.get("deepThinking");
            sessionId = (String) request.get("sessionId"); // 提取sessionId

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

            // 硬编码使用ID为1的模型（已配置为qwen3-235b-a22b）
            Long modelId = 2L;
            userId = Long.valueOf(userIdObj.toString());

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
            boolean willGenerateTitle = sessionId != null && isFirstAiResponse(sessionId);

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
                                    // 如果需要生成标题且是真实AI响应，立即生成标题
                                    String generatedTitle = null;
                                    if (willGenerateTitle && isRealAiResponse[0]) {
                                        try {
                                            generatedTitle = generateTitleSync(sessionId, messages, model);
                                            log.info("同步生成标题成功: {}", generatedTitle);
                                        } catch (Exception e) {
                                            log.error("同步生成标题失败", e);
                                            generatedTitle = null;
                                        }
                                    }

                                    // 发送Vercel AI SDK完成标记，包含标题信息
                                    String completionData = buildCompletionData(willGenerateTitle, isRealAiResponse[0], generatedTitle);
                                    response.getWriter().write(completionData);
                                    response.getWriter().flush();

                                    if (willGenerateTitle && isRealAiResponse[0]) {
                                        if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                                            log.info("Vercel AI SDK完成标记发送成功，title: {}", generatedTitle);
                                        } else {
                                            log.info("Vercel AI SDK完成标记发送成功，title: false (生成失败)");
                                        }
                                    } else {
                                        log.info("Vercel AI SDK完成标记发送成功，title: false (不生成标题)");
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
                                    boolean contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true; // 标记为真实AI响应
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
                                    boolean contentSent = processCompleteJson(bufferedJson, response);
                                    if (contentSent) {
                                        hasContentSent[0] = true;
                                        isRealAiResponse[0] = true; // 标记为真实AI响应
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
                    .blockLast(); // 阻塞等待完成

            // 返回聊天响应结果，包含标题生成信息
            return new ChatResponseResult(hasContentSent[0], isRealAiResponse[0], willGenerateTitle);

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

                // 异常情况下返回错误响应结果，有内容发送但不是真实AI响应，不生成标题
                return new ChatResponseResult(true, false, false);
            } catch (IOException ioException) {
                log.error("发送异常消息失败", ioException);
                // 如果连错误消息都发送失败，返回完全失败的结果
                return new ChatResponseResult(false, false, false);
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
     * @return true-成功发送了AI内容，false-没有发送内容
     */
    private boolean processCompleteJson(String jsonData, HttpServletResponse response) {
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
                            // 成功发送了内容
                            return true;
                        }
                    }
                }
            }
            // 没有发送内容
            return false;
        } catch (Exception e) {
            log.error("处理完整JSON数据失败: {}", e.getMessage());
            // 处理失败，没有发送内容
            return false;
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
     * 通过检查数据库中该会话是否已有AI消息来判断
     */
    private boolean isFirstAiResponse(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        // 检查数据库中该会话是否已有AI消息
        // 如果没有AI消息，说明这是首次AI响应，应该生成标题
        return !chatMessageService.hasAssistantMessage(sessionId);
    }

    /**
     * 异步生成并保存对话标题
     */
    private void generateAndSaveTitle(String sessionId, List<Map<String, String>> messages, AiModel model) {
        // 异步执行，不阻塞主流程
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始为会话{}生成标题", sessionId);

                // 构建对话内容
                String conversationContent = buildConversationContent(messages);
                if (conversationContent == null || conversationContent.trim().isEmpty()) {
                    log.warn("无法构建对话内容，跳过标题生成");
                    return;
                }

                // 构建标题生成提示词
                String titlePrompt = buildTitlePrompt(conversationContent);

                // 构建标题生成消息列表
                List<Map<String, String>> titleMessages = new ArrayList<>();
                Map<String, String> titleMessage = new HashMap<>();
                titleMessage.put("role", "user");
                titleMessage.put("content", titlePrompt);
                titleMessages.add(titleMessage);

                // 调用AI生成标题（使用非流式chat方法）
                String title = openAiService.chat(titleMessages, model);

                // 清理标题（移除引号和多余空格）
                title = cleanTitle(title);

                // 保存标题到会话
                boolean success = chatSessionService.updateSessionTitle(sessionId, title);
                if (success) {
                    log.info("会话{}标题生成成功: {}", sessionId, title);
                } else {
                    log.warn("会话{}标题保存失败", sessionId);
                }

            } catch (Exception e) {
                log.warn("生成对话标题失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 提取用户消息内容
     */
    private String extractUserMessage(List<Map<String, String>> messages) {
        for (Map<String, String> message : messages) {
            if ("user".equals(message.get("role"))) {
                return message.get("content");
            }
        }
        return null;
    }

    /**
     * 构建对话内容
     */
    private String buildConversationContent(List<Map<String, String>> messages) {
        StringBuilder conversationBuilder = new StringBuilder();

        for (Map<String, String> message : messages) {
            String role = message.get("role");
            String content = message.get("content");

            if ("user".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("User: ").append(content.trim()).append("\n");
            } else if ("assistant".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("AI: ").append(content.trim()).append("\n");
            }
        }

        return conversationBuilder.toString().trim();
    }

    /**
     * 构建标题生成提示词
     */
    private String buildTitlePrompt(String conversationContent) {
        return PromptConstants.TITLE_GENERATION_PROMPT
            .replace("{conversationContent}", conversationContent);
    }

    /**
     * 清理英文标题内容
     */
    private String cleanTitle(String title) {
        if (title == null) {
            return "New Chat";
        }

        // 移除引号、特殊符号和多余空格
        title = title.trim()
            .replaceAll("^[\"']+|[\"']+$", "") // 移除开头和结尾的引号
            .replaceAll("[^a-zA-Z0-9\\s]", "") // 移除非英文字母、数字和空格的字符
            .replaceAll("\\s+", " ") // 合并多个空格
            .trim();

        // 验证是否为有效的英文标题（3-8个单词）
        String[] words = title.split("\\s+");
        if (words.length < 3 || words.length > 8 || title.isEmpty()) {
            return "New Chat";
        }

        // 确保每个单词都是有效的英文单词（至少包含一个字母）
        for (String word : words) {
            if (!word.matches(".*[a-zA-Z].*")) {
                return "New Chat";
            }
        }

        // 首字母大写处理
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String word = words[i].toLowerCase();
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.toString();
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
        private final boolean willGenerateTitle;

        public ChatResponseResult(boolean hasContentSent, boolean isRealAiResponse) {
            this.hasContentSent = hasContentSent;
            this.isRealAiResponse = isRealAiResponse;
            this.willGenerateTitle = false;
        }

        public ChatResponseResult(boolean hasContentSent, boolean isRealAiResponse, boolean willGenerateTitle) {
            this.hasContentSent = hasContentSent;
            this.isRealAiResponse = isRealAiResponse;
            this.willGenerateTitle = willGenerateTitle;
        }

        public boolean hasContentSent() {
            return hasContentSent;
        }

        public boolean isRealAiResponse() {
            return isRealAiResponse;
        }

        public boolean willGenerateTitle() {
            return willGenerateTitle;
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
                    // 应该生成标题但生成失败，设置为false
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
     * 同步生成标题（不保存到数据库）
     * @param sessionId 会话ID
     * @param messages 消息列表
     * @param model AI模型
     * @return 生成的标题，失败时返回null
     */
    private String generateTitleSync(String sessionId, List<Map<String, String>> messages, AiModel model) {
        try {
            // 构建对话内容
            String conversationContent = buildConversationContent(messages);
            if (conversationContent == null || conversationContent.trim().isEmpty()) {
                log.warn("无法构建对话内容，跳过标题生成");
                return null;
            }

            // 构建标题生成提示词
            String titlePrompt = buildTitlePrompt(conversationContent);

            // 构建标题生成消息列表
            List<Map<String, String>> titleMessages = new ArrayList<>();
            Map<String, String> titleMessage = new HashMap<>();
            titleMessage.put("role", "user");
            titleMessage.put("content", titlePrompt);
            titleMessages.add(titleMessage);

            // 调用AI服务生成标题
            String rawTitle = callAiForTitle(titleMessages, model);
            if (rawTitle == null || rawTitle.trim().isEmpty()) {
                log.warn("AI服务返回空标题");
                return null;
            }

            // 清理标题
            String cleanedTitle = cleanTitle(rawTitle);
            log.info("标题生成成功: {} -> {}", rawTitle, cleanedTitle);

            // 异步保存标题到数据库
            CompletableFuture.runAsync(() -> {
                try {
                    chatSessionService.updateSessionTitle(sessionId, cleanedTitle);
                    log.info("标题已异步保存到数据库: {}", cleanedTitle);
                } catch (Exception e) {
                    log.error("异步保存标题失败", e);
                }
            });

            return cleanedTitle;

        } catch (Exception e) {
            log.error("同步生成标题异常", e);
            return null;
        }
    }

    /**
     * 调用AI服务生成标题
     * @param titleMessages 标题生成消息列表
     * @param model AI模型
     * @return 生成的原始标题，失败时返回null
     */
    private String callAiForTitle(List<Map<String, String>> titleMessages, AiModel model) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", titleMessages);
            requestBody.put("stream", false); // 非流式请求
            requestBody.put("max_tokens", 50); // 限制标题长度
            requestBody.put("temperature", 0.7); // 适中的创造性

            // 创建WebClient
            WebClient webClient = WebClient.builder()
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .build();

            log.info("调用AI服务生成标题: {}", model.getBaseUrl());

            // 发送请求并获取响应
            String responseBody = webClient.post()
                    .uri(model.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10)) // 10秒超时
                    .block();

            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.warn("AI服务返回空响应");
                return null;
            }

            // 解析响应
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(responseBody);
            JsonNode choices = responseJson.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String title = message.get("content").asText();
                    log.info("AI服务返回标题: {}", title);
                    return title;
                }
            }

            log.warn("AI服务响应格式异常: {}", responseBody);
            return null;

        } catch (Exception e) {
            log.error("调用AI服务生成标题失败", e);
            return null;
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

}
