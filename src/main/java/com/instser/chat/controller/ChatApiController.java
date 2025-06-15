package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.result.Result;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
    private final OpenAiService openAiService;

    @Qualifier("sseBypassExecutor")
    private final Executor sseBypassExecutor;

    /**
     * 流式聊天接口 - 兼容前端 (POST)
     */
    @PostMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chat(@RequestBody Map<String, Object> request, Authentication authentication, HttpServletResponse response) {
        log.info("收到聊天请求：{}, 认证信息：{}", request, authentication);

        // 设置Vercel AI SDK响应头和字符编码
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-vercel-ai-data-stream", "v1");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");

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

            log.info("所有验证通过，开始直接流式响应");

            // 直接在当前线程中调用AI服务，避免异步问题
            log.info("开始调用AI服务，模型：{}，用户：{}", model.getModelName(), userId);
            // 直接调用AI服务，传入response对象进行直接写入
            streamChatDirect(messages, model, response);

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
     * 流式聊天接口 - 支持EventSource (GET)
     */
    @GetMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatGet(@RequestParam("data") String requestData, Authentication authentication, HttpServletResponse response) {
        log.info("收到EventSource聊天请求：{}, 认证信息：{}", requestData, authentication);

        // 设置Vercel AI SDK响应头和字符编码
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-vercel-ai-data-stream", "v1");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        // 在创建SseEmitter之前进行所有验证和安全检查
        try {
            // 解析JSON数据
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(requestData, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");

            if (messages == null || messages.isEmpty()) {
                throw new IllegalArgumentException("消息列表不能为空");
            }

            if (userIdObj == null) {
                throw new IllegalArgumentException("用户ID不能为空");
            }

            // 硬编码使用ID为1的模型
            Long modelId = 1L;
            Long userId = Long.valueOf(userIdObj.toString());

            // 获取模型配置
            AiModel model = aiModelService.getById(modelId);
            if (model == null || model.getIsAvailable() != 1) {
                throw new IllegalArgumentException("AI模型不可用");
            }

            // 验证消息格式
            Map<String, String> lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.get("role");

            if (!"user".equals(role)) {
                throw new IllegalArgumentException("最后一条消息必须是用户消息");
            }

            // 验证用户权限（确保认证用户有权限访问）
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new SecurityException("用户未认证");
            }

            log.info("EventSource所有验证通过，开始创建SSE流式响应");

            // 创建SseEmitter（此时所有验证都已完成）
            SseEmitter emitter = new SseEmitter(60000L);

            // 设置异常处理回调
            emitter.onError(throwable -> {
                log.error("EventSource SSE流式响应发生错误", throwable);
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE发射器失败", e);
                }
            });

            emitter.onTimeout(() -> {
                log.warn("EventSource SSE流式响应超时");
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE发射器失败", e);
                }
            });

            // 使用SSE专用执行器，绕过Spring Security异步检查
            sseBypassExecutor.execute(() -> {
                try {
                    log.info("开始调用AI服务，模型：{}，用户：{}", model.getModelName(), userId);
                    openAiService.streamChat(messages, model, emitter);
                } catch (Exception e) {
                    log.error("AI服务调用异常", e);
                    try {
                        emitter.send(SseEmitter.event().data("抱歉，AI服务出现异常，请稍后重试。"));
                        emitter.complete();
                    } catch (IOException ioException) {
                        log.error("发送错误消息失败", ioException);
                        emitter.completeWithError(ioException);
                    }
                }
            });

            return emitter;

        } catch (IllegalArgumentException e) {
            log.warn("EventSource请求参数错误: {}", e.getMessage());
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().data("错误：" + e.getMessage()));
                errorEmitter.complete();
            } catch (IOException ioException) {
                log.error("发送错误消息失败", ioException);
                errorEmitter.completeWithError(ioException);
            }
            return errorEmitter;
        } catch (SecurityException e) {
            log.warn("EventSource安全验证失败: {}", e.getMessage());
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().data("错误：" + e.getMessage()));
                errorEmitter.complete();
            } catch (IOException ioException) {
                log.error("发送错误消息失败", ioException);
                errorEmitter.completeWithError(ioException);
            }
            return errorEmitter;
        } catch (Exception e) {
            log.error("EventSource聊天接口异常", e);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().data("系统错误：" + e.getMessage()));
                errorEmitter.complete();
            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
                errorEmitter.completeWithError(ioException);
            }
            return errorEmitter;
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
     */
    private void streamChatDirect(List<Map<String, String>> messages, AiModel model, HttpServletResponse response) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messages);
            requestBody.put("stream", true);
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            // 创建WebClient，确保UTF-8编码
            WebClient webClient = WebClient.builder()
                    .baseUrl(model.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                    .defaultHeader("Accept-Charset", "UTF-8")
                    .build();

            log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());

            // 发送流式请求并直接处理响应 - 使用阻塞方式
            webClient.post()
                    .uri("/chat/completions")
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
                        String chunk = new String(bytes, StandardCharsets.UTF_8);
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

                                // 解析JSON数据
                                String jsonData = line.substring(6).trim();
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode jsonNode = mapper.readTree(jsonData);
                                JsonNode choices = jsonNode.get("choices");

                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").asText();
                                        if (content != null && !content.isEmpty()) {
                                            // 发送Vercel AI SDK格式: 0:"content"\n
                                            String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(content));
                                            response.getWriter().write(vercelFormat);
                                            response.getWriter().flush();
                                            log.info("Vercel AI SDK格式数据发送成功: [{}]", vercelFormat.trim());
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理流式数据失败: {}", e.getMessage());
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

        } catch (Exception e) {
            log.error("直接流式聊天异常", e);
            try {
                response.getWriter().write("3:\"流式聊天服务异常\"\n");
                response.getWriter().flush();
            } catch (IOException ioException) {
                log.error("发送异常消息失败", ioException);
            }
        }
    }

}
