package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;

import java.util.List;
import java.util.Map;

/**
 * 聊天标题生成服务。
 */
public interface ChatTitleService {

    List<Map<String, String>> buildTitleMessages(ChatRequestDTO request, String aiResponse);

    /**
     * 基于原始消息快照构建标题材料，避免依赖 ChatRequestDTO 运行时字段。
     */
    List<Map<String, String>> buildTitleMessages(List<Map<String, String>> originalMessages, String aiResponse);

    String generateSessionTitle(String sessionId, List<Map<String, String>> messages);
}
