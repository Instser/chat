package com.instser.chat.parser.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.parser.DocumentParser;
import com.instser.chat.util.Base64Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片文档解析器
 * 支持各种图片格式的AI解析
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class ImageDocumentParser implements DocumentParser {

    /**
     * AI服务配置 - 从test_file.txt获取
     */
    private static final String AI_API_URL = "http://203.0.113.15:3000/v1/chat/completions";
    private static final String AI_API_KEY = "a2366021253";
    private static final String AI_MODEL = "qwen2.5-omni-7b";
    private static final String AI_PROMPT = "Please analyze this image and provide the content in the following format: First line should be 'Here's what the text in the image says.' followed by a JSON object describing the image content. Do not use markdown code blocks or ```json formatting, just provide plain text with the JSON object.";

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("图片文件内容不能为空");
        }

        log.info("开始解析图片文件: {} (类型: {}, 大小: {} bytes)", fileName, fileType, fileBytes.length);

        try {
            // 1. 将图片转换为Base64编码
            String base64Image = Base64Util.encodeToString(fileBytes);
            log.debug("图片Base64编码完成，编码长度: {} 字符", base64Image.length());

            // 2. 构建data URL格式
            String dataUrl = Base64Util.encodeImageToDataUrl(fileBytes, fileType);
            log.debug("图片data URL构建完成");

            // 3. 调用AI服务解析图片
            String aiResponse = callAiForImageAnalysis(dataUrl);
            
            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                throw new RuntimeException("AI服务返回空响应");
            }

            log.info("图片解析成功: {} (AI响应长度: {} 字符)", fileName, aiResponse.length());
            return aiResponse;

        } catch (Exception e) {
            log.error("图片解析失败: {} - {}", fileName, e.getMessage(), e);
            throw new RuntimeException("图片解析失败：" + e.getMessage());
        }
    }

    /**
     * 调用AI服务进行图片分析
     *
     * @param dataUrl 图片的data URL
     * @return AI分析结果
     */
    private String callAiForImageAnalysis(String dataUrl) throws Exception {
        log.info("开始调用AI服务进行图片分析");

        try {
            // 构建请求体 - 按照test_file.txt的格式
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", AI_MODEL);
            requestBody.put("stream", false);

            // 构建消息内容
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            // 构建content数组
            List<Map<String, Object>> content = new ArrayList<>();
            
            // 文本内容
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", AI_PROMPT);
            content.add(textContent);

            // 图片内容
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", dataUrl);
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);

            message.put("content", content);
            messages.add(message);
            requestBody.put("messages", messages);

            log.debug("AI请求体构建完成，消息数量: {}", messages.size());

            // 创建RestTemplate
            RestTemplate restTemplate = new RestTemplate();

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(AI_API_KEY);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("发送AI请求到: {}", AI_API_URL);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(
                AI_API_URL,
                entity,
                String.class
            );

            String responseBody = response.getBody();
            log.info("收到AI服务响应，状态码: {}, 响应长度: {} 字符", 
                response.getStatusCode(), responseBody != null ? responseBody.length() : 0);

            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new RuntimeException("AI服务返回空响应");
            }

            // 解析AI响应
            return parseAiResponse(responseBody);

        } catch (Exception e) {
            log.error("调用AI服务失败", e);
            throw new RuntimeException("调用AI服务失败：" + e.getMessage());
        }
    }

    /**
     * 解析AI服务响应
     *
     * @param responseBody AI服务响应体
     * @return 解析后的内容
     */
    private String parseAiResponse(String responseBody) throws Exception {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(responseBody);

            JsonNode choices = responseJson.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String content = message.get("content").asText();

                    // 处理AI返回的内容格式
                    String processedContent = processAiContent(content);

                    log.info("AI图片分析结果解析成功，内容长度: {} 字符", processedContent.length());
                    return processedContent;
                } else {
                    log.warn("AI响应中message节点不存在或没有content字段");
                }
            } else {
                log.warn("AI响应中choices节点为空或不是数组");
            }

            log.warn("AI服务响应格式异常，完整响应: {}", responseBody);
            throw new RuntimeException("AI服务响应格式异常");

        } catch (Exception e) {
            log.error("解析AI响应失败", e);
            throw new RuntimeException("解析AI响应失败：" + e.getMessage());
        }
    }

    /**
     * 处理AI返回的内容，确保格式符合要求
     *
     * @param aiContent AI原始返回内容
     * @return 处理后的内容
     */
    public String processAiContent(String aiContent) {
        if (aiContent == null || aiContent.trim().isEmpty()) {
            return "Below is the textual content in the image, which you analyse as complete image information, not as json text.\n{}";
        }

        String content = aiContent.trim();

        // 移除markdown代码块格式
        content = content.replaceAll("```json\\s*", "");
        content = content.replaceAll("```\\s*$", "");
        content = content.replaceAll("^```\\s*", "");
        content = content.trim(); // 再次trim移除可能的空白字符

        // 检查是否已经包含说明文字
        if (!content.startsWith("Below is the textual content in the image, which you analyse as complete image information, not as json text.")) {
            // 如果内容直接是JSON格式，添加说明文字
            if (content.trim().startsWith("{")) {
                content = "Below is the textual content in the image, which you analyse as complete image information, not as json text.\n" + content;
            } else {
                // 如果内容不是JSON格式，尝试提取JSON部分
                String jsonPart = extractJsonFromContent(content);
                if (jsonPart != null && !jsonPart.isEmpty()) {
                    content = "Below is the textual content in the image, which you analyse as complete image information, not as json text.\n" + jsonPart;
                } else {
                    // 如果没有找到JSON，创建一个简单的JSON格式
                    content = "Below is the textual content in the image, which you analyse as complete image information, not as json text.\n{\"description\": \"" +
                             content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
                }
            }
        }

        log.debug("处理后的AI内容格式: {}", content.length() > 100 ? content.substring(0, 100) + "..." : content);
        return content;
    }

    /**
     * 从AI返回的内容中提取JSON部分
     *
     * @param content AI返回的原始内容
     * @return 提取的JSON字符串，如果没有找到则返回null
     */
    private String extractJsonFromContent(String content) {
        try {
            // 查找第一个{和最后一个}之间的内容
            int firstBrace = content.indexOf('{');
            int lastBrace = content.lastIndexOf('}');

            if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
                String jsonCandidate = content.substring(firstBrace, lastBrace + 1);

                // 验证是否为有效的JSON
                ObjectMapper mapper = new ObjectMapper();
                mapper.readTree(jsonCandidate);

                return jsonCandidate;
            }
        } catch (Exception e) {
            log.debug("提取JSON失败: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/svg+xml"
        };
    }
}
