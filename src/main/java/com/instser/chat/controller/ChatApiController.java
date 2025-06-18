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

    // 快速单字符响应配置
    private static final boolean ENABLE_CHARACTER_STREAMING = true; // 是否启用单字符流式响应
    private static final long CHARACTER_DELAY_MS = 1; // 字符间延迟（毫秒）
    private static final int BATCH_SIZE = 1; // 批量发送字符数，1表示单字符，可设置为2-5实现小批量发送


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

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");
            Object deepThinkingObj = request.get("deepThinking");

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
            Long modelId = 1L;
            Long userId = Long.valueOf(userIdObj.toString());

            // 获取模型配置
            AiModel model = aiModelService.getById(modelId);
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
            // 直接调用AI服务，传入response对象进行直接写入，返回是否成功
            boolean aiServiceSuccess = streamChatDirect(messages, model, response, isDeepThink);

            // 只有AI服务成功返回内容时才记录使用次数
            if (aiServiceSuccess) {
                boolean recordSuccess = chatLimitService.recordChatUsage(userId);
                if (recordSuccess) {
                    log.info("用户{}聊天使用次数记录成功", userId);
                } else {
                    log.warn("用户{}聊天使用次数记录失败", userId);
                }
            } else {
                log.warn("用户{}AI服务调用失败，不记录聊天次数", userId);
            }

        } catch (Exception e) {
            log.error("聊天接口异常", e);
            try {
                response.getWriter().write("3:\"系统错误：" + e.getMessage() + "\"\n");
                response.getWriter().flush();
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
     * 直接流式聊天方法 - 不使用SseEmitter，直接写入HttpServletResponse
     * @return true-AI服务成功返回内容，false-AI服务调用失败或无内容返回
     */
    private boolean streamChatDirect(List<Map<String, String>> messages, AiModel model, HttpServletResponse response, boolean isDeepThink) {
        // 用于跟踪是否有AI内容被成功发送
        final boolean[] hasContentSent = {false};

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
                                    // 发送Vercel AI SDK完成标记
                                    String completionData = String.format("d:%s\n",
                                        new ObjectMapper().writeValueAsString(Map.of(
                                            "finishReason", "stop",
                                            "usage", Map.of(
                                                "promptTokens", 10,
                                                "completionTokens", 20
                                            )
                                        ))
                                    );
                                    response.getWriter().write(completionData);
                                    response.getWriter().flush();
                                    log.info("Vercel AI SDK完成标记发送成功");
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
                            response.getWriter().write("3:\"AI服务调用失败\"\n");
                            response.getWriter().flush();
                        } catch (IOException e) {
                            log.error("发送错误消息失败", e);
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("AI API流式响应处理完成");
                    })
                    .blockLast(); // 阻塞等待完成

            // 返回是否有内容被发送
            return hasContentSent[0];

        } catch (Exception e) {
            log.error("直接流式聊天异常", e);
            try {
                response.getWriter().write("3:\"流式聊天服务异常\"\n");
                response.getWriter().flush();
            } catch (IOException ioException) {
                log.error("发送异常消息失败", ioException);
            }
            // 异常情况下返回false
            return false;
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
                            if (ENABLE_CHARACTER_STREAMING) {
                                // 按字符逐个发送，实现快速单字符响应
                                sendContentByCharacter(cleanContent, response, mapper);
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
     * 按字符逐个发送内容，实现快速单字符响应
     * 支持批量发送和智能字符处理，正确处理emoji等多字节Unicode字符
     */
    protected void sendContentByCharacter(String content, HttpServletResponse response, ObjectMapper mapper) {
        try {
            // 使用codePoints正确处理emoji等多字节Unicode字符
            int[] codePoints = content.codePoints().toArray();
            StringBuilder batch = new StringBuilder();
            int sentChars = 0;

            for (int i = 0; i < codePoints.length; i++) {
                int codePoint = codePoints[i];

                // 将codePoint转换为字符串
                String character = new String(Character.toChars(codePoint));

                // 跳过无效字符（只检查替换字符和空字符）
                if (codePoint != 0xFFFD && codePoint != 0x0000) {
                    batch.append(character);
                }

                // 当达到批量大小或者是最后一个字符时发送
                if (batch.length() >= BATCH_SIZE || i == codePoints.length - 1) {
                    try {
                        String batchContent = batch.toString();

                        // 发送Vercel AI SDK格式: 0:"content"\n
                        String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(batchContent));
                        response.getWriter().write(vercelFormat);
                        response.getWriter().flush();

                        sentChars += batchContent.length();

                        // 记录发送的内容（对特殊字符进行转义显示）
                        String displayContent = batchContent.replace("\n", "\\n")
                                                           .replace("\r", "\\r")
                                                           .replace("\t", "\\t");
                        log.debug("发送批量内容: [{}] ({}字符)", displayContent, batchContent.length());

                        // 清空批量缓冲区
                        batch.setLength(0);

                        // 添加延迟，控制发送速度
                        if (CHARACTER_DELAY_MS > 0) {
                            Thread.sleep(CHARACTER_DELAY_MS);
                        }

                    } catch (InterruptedException e) {
                        log.warn("字符发送被中断: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        log.error("发送批量字符失败: {}", e.getMessage());
                        // 如果批量发送失败，清空缓冲区继续下一批
                        batch.setLength(0);
                        continue;
                    }
                }
            }

            log.info("完成字符流式发送，总字符数: {}, 实际发送: {}", codePoints.length, sentChars);

        } catch (Exception e) {
            log.error("按字符发送内容失败: {}", e.getMessage());
            // 如果按字符发送失败，回退到原来的块发送方式
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



}
