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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    /**
     * 流式聊天接口 - 兼容前端 (POST)
     */
    @PostMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> request) {
        log.info("收到聊天请求：{}", request);

        SseEmitter emitter = new SseEmitter(60000L); // 增加超时时间到60秒

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");

            if (messages == null || messages.isEmpty()) {
                emitter.send(SseEmitter.event().data("错误：消息列表不能为空"));
                emitter.complete();
                return emitter;
            }

            if (userIdObj == null) {
                emitter.send(SseEmitter.event().data("错误：用户ID不能为空"));
                emitter.complete();
                return emitter;
            }

            // 硬编码使用ID为1的模型（已配置为qwen3-235b-a22b）
            Long modelId = 1L;
            Long userId = Long.valueOf(userIdObj.toString());

            // 获取模型配置
            AiModel model = aiModelService.getById(modelId);
            if (model == null || model.getIsAvailable() != 1) {
                emitter.send(SseEmitter.event().data("错误：AI模型不可用"));
                emitter.complete();
                return emitter;
            }

            // 验证消息格式
            Map<String, String> lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.get("role");

            if (!"user".equals(role)) {
                emitter.send(SseEmitter.event().data("错误：最后一条消息必须是用户消息"));
                emitter.complete();
                return emitter;
            }

            // 异步调用AI服务
            CompletableFuture.runAsync(() -> {
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
                    }
                }
            });

        } catch (Exception e) {
            log.error("聊天接口异常", e);
            try {
                emitter.send(SseEmitter.event().data("系统错误：" + e.getMessage()));
                emitter.complete();
            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
            }
        }

        return emitter;
    }

    /**
     * 流式聊天接口 - 支持EventSource (GET)
     */
    @GetMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatGet(@RequestParam("data") String requestData) {
        log.info("收到EventSource聊天请求：{}", requestData);

        SseEmitter emitter = new SseEmitter(60000L);

        try {
            // 解析JSON数据
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(requestData, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
            Object userIdObj = request.get("userId");

            if (messages == null || messages.isEmpty()) {
                emitter.send(SseEmitter.event().data("错误：消息列表不能为空"));
                emitter.complete();
                return emitter;
            }

            if (userIdObj == null) {
                emitter.send(SseEmitter.event().data("错误：用户ID不能为空"));
                emitter.complete();
                return emitter;
            }

            // 硬编码使用ID为1的模型
            Long modelId = 1L;
            Long userId = Long.valueOf(userIdObj.toString());

            // 获取模型配置
            AiModel model = aiModelService.getById(modelId);
            if (model == null || model.getIsAvailable() != 1) {
                emitter.send(SseEmitter.event().data("错误：AI模型不可用"));
                emitter.complete();
                return emitter;
            }

            // 验证消息格式
            Map<String, String> lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.get("role");

            if (!"user".equals(role)) {
                emitter.send(SseEmitter.event().data("错误：最后一条消息必须是用户消息"));
                emitter.complete();
                return emitter;
            }

            // 异步调用AI服务
            CompletableFuture.runAsync(() -> {
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
                    }
                }
            });

        } catch (Exception e) {
            log.error("EventSource聊天接口异常", e);
            try {
                emitter.send(SseEmitter.event().data("系统错误：" + e.getMessage()));
                emitter.complete();
            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
            }
        }

        return emitter;
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


}
