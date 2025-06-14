package com.instser.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI聊天路径兼容控制器
 * 提供前端期望的 /api/chat 路径支持
 *
 * @author instser
 * @since 2025-06-14
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ChatPathCompatController {

    private final ChatApiController chatApiController;

    /**
     * AI聊天接口 - 前端兼容版本
     * 路径：POST /api/chat
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatFrontend(@RequestBody Map<String, Object> request) {
        log.info("收到前端兼容聊天请求：{}", request);
        
        // 转换请求参数格式以适配原有接口
        Map<String, Object> adaptedRequest = adaptChatRequest(request);
        
        // 调用原有的聊天接口
        return chatApiController.chat(adaptedRequest);
    }

    /**
     * 转换前端请求参数为后端格式
     */
    private Map<String, Object> adaptChatRequest(Map<String, Object> frontendRequest) {
        Map<String, Object> backendRequest = new java.util.HashMap<>(frontendRequest);
        
        // 前端可能发送的参数：
        // {
        //   "messages": [...],
        //   "deepThinking": boolean
        // }
        
        // 后端期望的参数：
        // {
        //   "messages": [...],
        //   "sessionId": string,
        //   "modelCode": string
        // }
        
        // 如果前端没有提供sessionId，使用默认值或生成新的
        if (!backendRequest.containsKey("sessionId")) {
            backendRequest.put("sessionId", generateDefaultSessionId());
        }
        
        // 如果前端没有提供modelCode，使用默认模型
        if (!backendRequest.containsKey("modelCode")) {
            backendRequest.put("modelCode", "qwen3-235b-a22b");
        }
        
        // 保留deepThinking参数（如果有的话）
        // 这个参数可能在后端实现中使用
        
        log.debug("适配后的请求参数：{}", backendRequest);
        return backendRequest;
    }

    /**
     * 生成默认会话ID
     */
    private String generateDefaultSessionId() {
        // 生成一个临时会话ID
        return "temp_" + System.currentTimeMillis() + "_" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * AI聊天接口 - GET方式（用于EventSource）
     * 路径：GET /api/chat
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatFrontendGet(@RequestParam("data") String requestData) {
        log.info("收到前端兼容EventSource聊天请求：{}", requestData);
        
        // 调用原有的GET接口
        return chatApiController.chatGet(requestData);
    }
}
