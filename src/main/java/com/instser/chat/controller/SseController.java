package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.result.Result;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * SSE流式响应控制器 - 已禁用
 * 所有聊天请求现在统一使用 /api/chat 端点
 * 专门处理SSE流式响应，避免Spring Security异步检查问题
 */
@Slf4j
// @RestController  // 禁用此控制器
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final OpenAiService openAiService;
    private final AiModelService aiModelService;

    /**
     * SSE流式聊天接口 - POST方式
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, Object> request, Authentication authentication, HttpServletResponse response) {
        log.info("收到SSE流式聊天请求：{}, 认证信息：{}", request, authentication);

        // 创建SseEmitter，设置较长的超时时间
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        try {
            // 设置Vercel AI SDK响应头
            response.setHeader("x-vercel-ai-data-stream", "v1");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
            response.setHeader("Access-Control-Allow-Credentials", "true");

            // 验证认证状态
            if (authentication == null || !authentication.isAuthenticated()) {
                emitter.send(SseEmitter.event().data("错误：用户未认证"));
                emitter.complete();
                return emitter;
            }

            // 解析请求参数
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

            // 设置异常处理回调
            emitter.onError(throwable -> {
                log.error("SSE流式响应发生错误", throwable);
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE发射器失败", e);
                }
            });

            emitter.onTimeout(() -> {
                log.warn("SSE流式响应超时");
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE发射器失败", e);
                }
            });

            emitter.onCompletion(() -> {
                log.info("SSE流式响应完成");
            });

            // 立即开始AI服务调用，避免异步权限检查
            log.info("开始调用AI服务，模型：{}，用户：{}", model.getModelName(), userId);
            
            // 在新线程中执行AI调用，但不使用Spring的异步机制
            Thread aiThread = new Thread(() -> {
                try {
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
            
            aiThread.setName("SSE-AI-Thread-" + userId);
            aiThread.setDaemon(true);
            aiThread.start();

        } catch (Exception e) {
            log.error("SSE聊天接口异常", e);
            try {
                emitter.send(SseEmitter.event().data("系统错误：" + e.getMessage()));
                emitter.complete();
            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
                emitter.completeWithError(ioException);
            }
        }

        return emitter;
    }

    /**
     * SSE流式聊天接口 - GET方式（支持EventSource）
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatGet(@RequestParam("data") String requestData, Authentication authentication, HttpServletResponse response) {
        log.info("收到SSE EventSource聊天请求：{}, 认证信息：{}", requestData, authentication);

        // 创建SseEmitter，设置较长的超时时间
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        try {
            // 设置Vercel AI SDK响应头
            response.setHeader("x-vercel-ai-data-stream", "v1");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
            response.setHeader("Access-Control-Allow-Credentials", "true");

            // 验证认证状态
            if (authentication == null || !authentication.isAuthenticated()) {
                emitter.send(SseEmitter.event().data("错误：用户未认证"));
                emitter.complete();
                return emitter;
            }

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

            // 设置异常处理回调
            emitter.onError(throwable -> {
                log.error("SSE EventSource流式响应发生错误", throwable);
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE发射器失败", e);
                }
            });

            emitter.onTimeout(() -> {
                log.warn("SSE EventSource流式响应超时");
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE发射器失败", e);
                }
            });

            emitter.onCompletion(() -> {
                log.info("SSE EventSource流式响应完成");
            });

            // 立即开始AI服务调用，避免异步权限检查
            log.info("开始调用AI服务，模型：{}，用户：{}", model.getModelName(), userId);
            
            // 在新线程中执行AI调用，但不使用Spring的异步机制
            Thread aiThread = new Thread(() -> {
                try {
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
            
            aiThread.setName("SSE-EventSource-AI-Thread-" + userId);
            aiThread.setDaemon(true);
            aiThread.start();

        } catch (Exception e) {
            log.error("SSE EventSource聊天接口异常", e);
            try {
                emitter.send(SseEmitter.event().data("系统错误：" + e.getMessage()));
                emitter.complete();
            } catch (IOException ioException) {
                log.error("发送系统错误消息失败", ioException);
                emitter.completeWithError(ioException);
            }
        }

        return emitter;
    }
}
