package com.instser.chat.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.entity.AiModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 请求体日志（截断 content，避免污染主流程）。
 */
@Slf4j
@Component
public class AiRequestLogger {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void logAiRequestDetails(Map<String, Object> requestBody, AiModel model, Long userId) {
        try {
            log.debug("【AI请求】用户ID：{}，模型：{}（{}）", userId, model.getModelCode(), model.getBaseUrl());
            Map<String, Object> processedRequestBody = truncateRequestBodyContent(requestBody);
            String requestBodyJson = objectMapper.writeValueAsString(processedRequestBody);
            log.debug("【AI请求】请求体：{}", LogUtils.truncateContent(requestBodyJson));
        } catch (Exception e) {
            log.warn("【AI请求】记录请求详细信息失败：{}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> truncateRequestBodyContent(Map<String, Object> requestBody) {
        try {
            String jsonString = objectMapper.writeValueAsString(requestBody);
            Map<String, Object> processedBody = objectMapper.readValue(jsonString, Map.class);
            Object messagesObj = processedBody.get("messages");
            if (messagesObj instanceof List) {
                List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesObj;
                for (Map<String, Object> message : messages) {
                    Object contentObj = message.get("content");
                    if (contentObj instanceof String) {
                        message.put("content", truncateContent((String) contentObj, 30, 30));
                    }
                }
            }
            return processedBody;
        } catch (Exception e) {
            log.warn("处理请求体content字段失败: {}", e.getMessage());
            return requestBody;
        }
    }

    private String truncateContent(String content, int prefixLength, int suffixLength) {
        if (content == null) {
            return null;
        }
        if (content.length() <= prefixLength + suffixLength) {
            return content;
        }
        String prefix = content.substring(0, prefixLength);
        String suffix = content.substring(content.length() - suffixLength);
        return prefix + "..." + suffix;
    }
}
