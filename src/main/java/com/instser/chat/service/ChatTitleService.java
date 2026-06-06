package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;

import java.util.List;
import java.util.Map;

/**
 * 聊天标题生成服务。
 */
public interface ChatTitleService {

    List<Map<String, String>> buildTitleMessages(ChatRequestDTO request, String aiResponse);

    String generateSessionTitle(String sessionId, List<Map<String, String>> messages);
}
