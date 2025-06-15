package com.instser.chat.controller;

import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI SDK兼容的聊天控制器 - 已禁用
 * 所有聊天请求现在统一使用 /api/chat 端点
 * 提供标准的 /api/chat 端点，兼容 AI React SDK
 */
@Slf4j
// @RestController  // 禁用此控制器
@RequestMapping("/ai-chat")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"}, allowCredentials = "false")
public class AiSdkController {

    private final OpenAiService openAiService;
    private final AiModelService aiModelService;

    /**
     * AI SDK兼容的聊天接口
     * 接收标准的OpenAI格式请求，返回流式响应
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chat(@RequestBody Map<String, Object> request) {
        log.info("收到AI SDK聊天请求：{}", request);

        try {
            // 提取消息列表
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawMessages = (List<Map<String, Object>>) request.get("messages");
            log.info("原始消息列表：{}", rawMessages);

            // 转换为标准OpenAI格式
            List<Map<String, String>> messages = new ArrayList<>();
            for (Map<String, Object> rawMessage : rawMessages) {
                Map<String, String> message = new HashMap<>();
                message.put("role", (String) rawMessage.get("role"));
                message.put("content", (String) rawMessage.get("content"));
                messages.add(message);
            }
            log.info("转换后的消息列表：{}", messages);

            // 默认使用模型ID 1
            Long modelId = 1L;
            Long userId = 1L; // 默认用户ID，实际应用中应从认证信息获取

            log.info("开始调用AI服务，模型ID：{}，用户ID：{}", modelId, userId);

            // 获取AI模型配置
            AiModel aiModel = aiModelService.getById(modelId);
            if (aiModel == null) {
                log.error("AI模型不存在，模型ID：{}", modelId);
                throw new RuntimeException("AI模型不存在");
            }
            log.info("获取到AI模型配置：{}", aiModel.getModelCode());

            // 创建SSE发射器
            SseEmitter emitter = new SseEmitter(60000L);
            log.info("创建SSE发射器成功，超时时间：60秒");

            // 调用AI服务
            log.info("开始调用流式聊天服务");
            openAiService.streamChat(messages, aiModel, emitter);
            log.info("流式聊天服务调用完成，返回SSE发射器");

            // 返回带有AI SDK必需头的响应
            return ResponseEntity.ok()
                    .header("x-vercel-ai-data-stream", "v1")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .body(emitter);

        } catch (Exception e) {
            log.error("AI SDK聊天请求处理失败", e);
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data("抱歉，处理您的请求时发生错误，请稍后重试。"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
                emitter.completeWithError(ex);
            }
            return ResponseEntity.status(500)
                    .header("x-vercel-ai-data-stream", "v1")
                    .body(emitter);
        }
    }
}
