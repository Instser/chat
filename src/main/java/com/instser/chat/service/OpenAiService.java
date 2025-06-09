package com.instser.chat.service;

import com.instser.chat.entity.AiModel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface OpenAiService {

    /**
     * 调用OpenAI API进行流式聊天
     *
     * @param messages 消息列表
     * @param model AI模型配置
     * @param emitter SSE发射器
     */
    void streamChat(List<Map<String, String>> messages, AiModel model, SseEmitter emitter);

    /**
     * 调用OpenAI API进行单次聊天
     *
     * @param messages 消息列表
     * @param model AI模型配置
     * @return AI回复内容
     */
    String chat(List<Map<String, String>> messages, AiModel model);
}
