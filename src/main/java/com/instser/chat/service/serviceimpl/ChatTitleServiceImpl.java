package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.constants.PromptConstants;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatTitleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatTitleServiceImpl implements ChatTitleService {

    private final AiModelService aiModelService;
    private final ChatSessionService chatSessionService;
    private final RestTemplate restTemplate;

    public ChatTitleServiceImpl(AiModelService aiModelService,
                                ChatSessionService chatSessionService,
                                @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.aiModelService = aiModelService;
        this.chatSessionService = chatSessionService;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Map<String, String>> buildTitleMessages(ChatRequestDTO request, String aiResponse) {
        List<Map<String, String>> original = request == null ? null : request.getOriginalMessages();
        if ((original == null || original.isEmpty()) && request != null && request.getMessages() != null) {
            // 兼容旧调用：无原始快照时回退 messages（调用方应尽量传 originalMessages）
            original = request.getMessages();
        }
        return buildTitleMessages(original, aiResponse);
    }

    @Override
    public List<Map<String, String>> buildTitleMessages(List<Map<String, String>> originalMessages, String aiResponse) {
        List<Map<String, String>> messagesForTitle = new ArrayList<>();
        if (originalMessages != null) {
            for (Map<String, String> originalMsg : originalMessages) {
                if ("user".equals(originalMsg.get("role"))) {
                    messagesForTitle.add(new HashMap<>(originalMsg));
                }
            }
        }
        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
            Map<String, String> aiMsg = new HashMap<>();
            aiMsg.put("role", "assistant");
            aiMsg.put("content", aiResponse);
            messagesForTitle.add(aiMsg);
        }
        log.info("构建标题生成消息完成，消息数量: {}（用户消息: {}，AI响应: {}）",
                messagesForTitle.size(),
                messagesForTitle.stream().filter(m -> "user".equals(m.get("role"))).count(),
                messagesForTitle.stream().filter(m -> "assistant".equals(m.get("role"))).count());
        return messagesForTitle;
    }

    @Override
    public String generateSessionTitle(String sessionId, List<Map<String, String>> messages) {
        log.info("=== 开始后台同步标题生成流程 ===");
        log.info("会话ID: {}, 消息数量: {}", sessionId, messages.size());
        try {
            AiModel titleModel = aiModelService.getById(4L);
            if (titleModel == null || titleModel.getIsAvailable() != 1) {
                LogUtils.logUserError(log, null, "标题生成", "标题生成模型（ID=4L）不可用，会话ID: {}，模型状态: {}，跳过标题生成", null,
                        sessionId, titleModel != null ? titleModel.getIsAvailable() : "null");
                return null;
            }
            log.info("标题生成模型: {}", titleModel.getModelName());
            String conversationContent = buildConversationContent(messages);
            if (conversationContent == null || conversationContent.trim().isEmpty()) {
                LogUtils.logUserWarning(log, null, "标题生成", "无法构建对话内容，会话ID: {}，消息数量: {}，跳过标题生成",
                        sessionId, messages.size());
                return null;
            }
            String titlePrompt = buildTitlePrompt(conversationContent);
            String rawTitle = callAiForTitleWithHttpClient(titlePrompt, titleModel);
            if (rawTitle == null || rawTitle.trim().isEmpty()) {
                LogUtils.logUserWarning(log, null, "标题生成", "AI服务返回空标题，会话ID: {}，模型: {}，可能原因: AI模型无响应或网络异常",
                        sessionId, titleModel.getModelName());
                return null;
            }
            String cleanedTitle = cleanTitle(rawTitle);
            if (cleanedTitle != null && !cleanedTitle.equals("新对话")) {
                boolean saved = chatSessionService.updateSessionTitle(sessionId, cleanedTitle);
                if (saved) {
                    log.info("标题保存到数据库成功: {}", cleanedTitle);
                } else {
                    LogUtils.logUserError(log, null, "标题生成", "标题保存到数据库失败，会话ID: {}，标题: [{}]", null,
                            sessionId, cleanedTitle);
                }
            } else {
                LogUtils.logUserWarning(log, null, "标题生成", "标题为空或为默认标题，不保存到数据库，会话ID: {}，清理后标题: [{}]",
                        sessionId, cleanedTitle);
            }
            log.info("=== 后台同步标题生成流程完成，最终标题: [{}] ===", cleanedTitle);
            return cleanedTitle;
        } catch (Exception e) {
            log.error("后台同步标题生成过程中发生异常", e);
            return null;
        }
    }

    private String buildConversationContent(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            log.warn("消息列表为空，无法构建对话内容");
            return null;
        }
        StringBuilder conversationBuilder = new StringBuilder();
        for (Map<String, String> message : messages) {
            String role = message.get("role");
            String content = message.get("content");
            if ("user".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("User: ").append(content.trim()).append("\n");
            } else if ("assistant".equals(role) && content != null && !content.trim().isEmpty()) {
                conversationBuilder.append("AI: ").append(content.trim()).append("\n");
            }
        }
        String result = conversationBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String buildTitlePrompt(String conversationContent) {
        return PromptConstants.TITLE_GENERATION_PROMPT.replace("{conversationContent}", conversationContent);
    }

    private String cleanTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "新对话";
        }
        String cleaned = title.trim()
                .replaceAll("^[\"']+|[\"']+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? "新对话" : cleaned;
    }

    private String callAiForTitleWithHttpClient(String titlePrompt, AiModel model) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            List<Map<String, String>> titleMessages = new ArrayList<>();
            Map<String, String> titleMessage = new HashMap<>();
            titleMessage.put("role", "user");
            titleMessage.put("content", titlePrompt);
            titleMessages.add(titleMessage);
            requestBody.put("messages", titleMessages);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 50);
            requestBody.put("temperature", 0.7);
            if ("Gemini".equalsIgnoreCase(model.getProvider())) {
                requestBody.put("reasoning_effort", "none");
            }
            logAiTitleRequestDetails(requestBody, model);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(model.getApiKey());
            ResponseEntity<String> response = restTemplate.postForEntity(
                    model.getBaseUrl(),
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                LogUtils.logUserWarning(log, null, "标题生成", "AI服务返回空响应，模型: {}，URL: {}，可能原因: 网络异常或API不可用",
                        model.getModelName(), model.getBaseUrl());
                return null;
            }
            JsonNode choices = new ObjectMapper().readTree(responseBody).get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    return message.get("content").asText();
                }
            }
            LogUtils.logUserWarning(log, null, "标题生成", "AI服务响应格式异常，模型: {}，URL: {}，完整响应内容: {}",
                    model.getModelName(), model.getBaseUrl(), responseBody);
            return null;
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "标题生成", "使用HTTP客户端调用AI服务生成标题失败，模型: {}，URL: {}，异常类型: {}，异常消息: {}", e,
                    model.getModelName(), model.getBaseUrl(), e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private void logAiTitleRequestDetails(Map<String, Object> requestBody, AiModel model) {
        try {
            log.info("=== AI标题生成请求详细信息 ===");
            log.info("模型: {} ({})", model.getModelCode(), model.getBaseUrl());
            log.info("原始请求体: {}", new ObjectMapper().writeValueAsString(requestBody));
            log.info("=== AI标题生成请求信息结束 ===");
        } catch (Exception e) {
            log.warn("记录AI标题生成请求详细信息失败: {}", e.getMessage());
        }
    }
}
