package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.TruncationDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 截断检测服务实现类
 *
 * @author instser
 * @since 2025-07-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TruncationDetectionServiceImpl implements TruncationDetectionService {

    private final AiModelService aiModelService;
    private final RestTemplate restTemplate;

    /**
     * Token数量阈值，只有超过此数量才进行截断检测
     */
    private static final int TOKEN_THRESHOLD = 1500;

    // 截断检测模型ID
    private static final Long DETECTION_MODEL_ID = 5L;
    
    // 截断检测超时时间（毫秒）
    private static final int DETECTION_TIMEOUT = 10000;

    @Override
    public boolean isContentComplete(String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("截断检测：内容为空，默认认为完整");
            return true;
        }

        try {
            log.info("开始截断检测，内容长度：{} 字符", content.length());
            
            // 获取检测模型
            AiModel detectionModel = aiModelService.getById(DETECTION_MODEL_ID);
            if (detectionModel == null || detectionModel.getIsAvailable() != 1) {
                log.error("截断检测模型（ID={}）不可用，默认认为内容完整", DETECTION_MODEL_ID);
                return true;
            }

            // 构建检测提示词
            String detectionPrompt = buildDetectionPrompt(content);
            
            // 调用AI模型进行检测
            String detectionResult = callDetectionModel(detectionModel, detectionPrompt);
            
            // 解析检测结果
            boolean isComplete = parseDetectionResult(detectionResult);
            
            log.info("截断检测完成，结果：{}，AI回复：[{}]", isComplete ? "完整" : "截断", detectionResult);
            return isComplete;
            
        } catch (Exception e) {
            log.error("截断检测过程中发生异常，默认认为内容完整", e);
            return true; // 异常情况下默认认为完整，避免影响正常流程
        }
    }

    @Override
    public boolean isContentComplete(String content, int tokenCount) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("截断检测：内容为空，默认认为完整");
            return true;
        }

        // 检查Token数量是否达到阈值
        if (tokenCount < TOKEN_THRESHOLD) {
            log.info("截断检测：Token数量({})未达到阈值({})，跳过检测，默认认为完整", tokenCount, TOKEN_THRESHOLD);
            return true;
        }

        log.info("截断检测：Token数量({})达到阈值({})，开始检测", tokenCount, TOKEN_THRESHOLD);

        // 调用原有的检测逻辑
        return isContentComplete(content);
    }

    @Override
    public String alignContent(String originalContent, String continuationContent) {
        if (originalContent == null || originalContent.isEmpty()) {
            String cleaned = continuationContent != null ? cleanContinuationContent(continuationContent) : "";
            log.debug("原内容为空，返回清理后的续写内容，长度: {}", cleaned.length());
            return cleaned;
        }

        if (continuationContent == null || continuationContent.isEmpty()) {
            return originalContent;
        }

        try {
            log.info("开始内容对齐，原内容长度：{}，续写内容长度：{}",
                    originalContent.length(), continuationContent.length());

            // 清理续写内容中的AI标记
            String cleanedContinuation = cleanContinuationContent(continuationContent);
            log.debug("清理后续写内容长度: {}", cleanedContinuation.length());

            // 多策略内容对齐
            String alignedContent = performAdvancedAlignment(originalContent, cleanedContinuation);

            log.info("内容对齐完成，最终长度：{}，原始：{}，续写：{}",
                    alignedContent.length(), originalContent.length(), continuationContent.length());

            return alignedContent;

        } catch (Exception e) {
            log.error("内容对齐过程中发生异常，使用简单拼接", e);
            return originalContent + continuationContent;
        }
    }

    @Override
    public boolean shouldApplyTruncationDetection(String interfaceType) {
        // 只在chat、retry、edit-resend接口中应用截断检测
        boolean shouldApply = "chat".equals(interfaceType) ||
                             "retry".equals(interfaceType) ||
                             "edit-resend".equals(interfaceType);

        if (shouldApply) {
            log.info("应用截断检测 - 接口类型：{}", interfaceType);
        } else {
            log.info("跳过截断检测 - 接口类型：{}", interfaceType);
        }

        return shouldApply;
    }

    /**
     * 构建截断检测提示词
     */
    private String buildDetectionPrompt(String content) {
        return "请检查以下内容是否被截断或不完整。如果内容完整，回复\"true\"；如果内容被截断或不完整，回复\"false\"。\n\n" +
               "检测标准：\n" +
               "1. 代码块是否完整闭合\n" +
               "2. 句子是否完整结束\n" +
               "3. 列表、表格等结构是否完整\n" +
               "4. 数学公式是否完整\n" +
               "5. JSON、XML等格式是否完整\n\n" +
               "内容：\n" + content;
    }

    /**
     * 调用检测模型
     */
    private String callDetectionModel(AiModel model, String prompt) throws Exception {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model.getModelCode());
        requestBody.put("stream", false);
        
        // 构建消息
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", List.of(message));
        
        // 只有当provider是Gemini时才添加reasoning_effort参数
        if ("Gemini".equalsIgnoreCase(model.getProvider())) {
            requestBody.put("reasoning_effort", "none");
            log.debug("检测到Gemini模型，添加reasoning_effort=none参数");
        }

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(model.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("发送截断检测请求到：{}", model.getBaseUrl());

        // 发送请求
        ResponseEntity<String> response = restTemplate.postForEntity(
                model.getBaseUrl(),
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("截断检测API调用失败，状态码：" + response.getStatusCode());
        }

        // 解析响应
        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isEmpty()) {
            throw new RuntimeException("截断检测API返回空响应");
        }

        // 从响应中提取内容
        return extractContentFromResponse(responseBody);
    }

    /**
     * 从API响应中提取内容
     */
    private String extractContentFromResponse(String responseBody) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        
        Object choicesObj = responseMap.get("choices");
        if (choicesObj instanceof List) {
            List<?> choices = (List<?>) choicesObj;
            if (!choices.isEmpty() && choices.get(0) instanceof Map) {
                Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
                Object messageObj = firstChoice.get("message");
                if (messageObj instanceof Map) {
                    Map<?, ?> message = (Map<?, ?>) messageObj;
                    Object contentObj = message.get("content");
                    if (contentObj instanceof String) {
                        return ((String) contentObj).trim();
                    }
                }
            }
        }
        
        throw new RuntimeException("无法从API响应中提取内容");
    }

    /**
     * 解析检测结果
     */
    private boolean parseDetectionResult(String result) {
        if (result == null || result.isEmpty()) {
            log.warn("检测结果为空，默认认为完整");
            return true;
        }
        
        String cleanResult = result.toLowerCase().trim();
        
        // 直接匹配true/false
        if ("true".equals(cleanResult)) {
            return true;
        } else if ("false".equals(cleanResult)) {
            return false;
        }
        
        // 模糊匹配 - 先检查否定词，避免被包含的肯定词误判
        if (cleanResult.contains("false") || cleanResult.contains("截断") || cleanResult.contains("不完整") ||
            cleanResult.contains("incomplete") || cleanResult.contains("truncated")) {
            return false;
        } else if (cleanResult.contains("true") || cleanResult.contains("完整") || cleanResult.contains("complete")) {
            return true;
        }
        
        log.warn("无法解析检测结果：[{}]，默认认为完整", result);
        return true;
    }

    /**
     * 执行高级内容对齐算法
     * 考虑多种实际情况：重叠、无重叠、标点符号、空格等
     */
    private String performAdvancedAlignment(String original, String continuation) {
        // 策略1：检查精确重叠（最常见的情况）
        String exactOverlap = findExactOverlap(original, continuation);
        if (!exactOverlap.isEmpty()) {
            log.debug("发现精确重叠：[{}]", exactOverlap);
            return original + continuation.substring(exactOverlap.length());
        }

        // 策略2：检查是否为直接延续（无重叠，直接从截断点继续）
        return handleDirectContinuation(original, continuation);
    }

    /**
     * 查找精确重叠部分
     */
    private String findExactOverlap(String original, String continuation) {
        if (original.isEmpty() || continuation.isEmpty()) {
            return "";
        }

        // 查找最长的重叠部分，从最大可能长度开始
        int maxOverlapLength = Math.min(original.length(), continuation.length());

        // 从最长可能的重叠开始检查，逐渐减少长度
        for (int length = maxOverlapLength; length > 0; length--) {
            if (length <= original.length() && length <= continuation.length()) {
                String originalSuffix = original.substring(original.length() - length);
                String continuationPrefix = continuation.substring(0, length);

                if (originalSuffix.equals(continuationPrefix)) {
                    return originalSuffix;
                }
            }
        }

        return "";
    }

    /**
     * 处理直接延续的情况（无重叠，从截断点直接继续）
     */
    private String handleDirectContinuation(String original, String continuation) {
        if (original.isEmpty()) {
            return continuation;
        }

        if (continuation.isEmpty()) {
            return original;
        }

        char lastChar = original.charAt(original.length() - 1);
        char firstChar = continuation.charAt(0);

        // 情况1：单词内部截断（最常见的AI续写情况）
        // 例如：'hel' + 'lo world' -> 'hello world'
        if (Character.isLetter(lastChar) && Character.isLetter(firstChar)) {
            // 检查是否在引号内（字符串字面量）
            if (isInQuotes(original)) {
                return original + continuation;
            }

            // 检查续写是否看起来像单词的延续
            if (looksLikeWordContinuation(continuation)) {
                return original + continuation;
            }

            // 否则添加空格分隔
            return original + " " + continuation;
        }

        // 情况1.5：字母和非字母的组合，需要添加空格
        if (Character.isLetter(lastChar) && !Character.isLetter(firstChar) && !Character.isWhitespace(firstChar)) {
            return original + " " + continuation;
        }

        // 情况2：数字延续
        // 例如：'123' + '456.789' -> '123456.789'
        if (Character.isDigit(lastChar) && (Character.isDigit(firstChar) || firstChar == '.')) {
            return original + continuation;
        }

        // 情况3：已有空格或标点符号
        if (Character.isWhitespace(lastChar) || Character.isWhitespace(firstChar)) {
            return original + continuation;
        }

        // 情况4：标点符号后需要空格
        if (isPunctuation(lastChar) && !Character.isWhitespace(firstChar)) {
            return original + " " + continuation;
        }

        // 情况5：中文字符直接拼接
        if (isChinese(lastChar) || isChinese(firstChar)) {
            return original + continuation;
        }

        // 默认情况：添加空格
        return original + " " + continuation;
    }

    /**
     * 检查续写内容是否看起来像单词的延续
     */
    private boolean looksLikeWordContinuation(String continuation) {
        if (continuation.length() == 0) {
            return false;
        }

        // 检查前几个字符，如果都是字母且没有空格，可能是单词延续
        int checkLength = Math.min(5, continuation.length());
        for (int i = 0; i < checkLength; i++) {
            char c = continuation.charAt(i);
            if (Character.isWhitespace(c)) {
                return i > 0; // 如果第一个字符后有空格，前面的部分可能是单词延续
            }
            if (!Character.isLetter(c) && c != '\'' && c != '-') { // 允许撇号和连字符
                return false;
            }
        }
        return true;
    }

    /**
     * 检查字符是否为中文字符
     */
    private boolean isChinese(char c) {
        return c >= 0x4e00 && c <= 0x9fff;
    }

    /**
     * 检查字符是否为标点符号
     */
    private boolean isPunctuation(char c) {
        return ".,;:!?，。；：！？".indexOf(c) >= 0;
    }

    /**
     * 检查文本是否在引号内
     */
    private boolean isInQuotes(String text) {
        if (text.length() < 2) {
            return false;
        }

        // 简单检查：统计单引号和双引号的数量
        int singleQuotes = 0;
        int doubleQuotes = 0;

        for (char c : text.toCharArray()) {
            if (c == '\'') singleQuotes++;
            if (c == '"') doubleQuotes++;
        }

        // 如果引号数量为奇数，说明可能在引号内
        return (singleQuotes % 2 == 1) || (doubleQuotes % 2 == 1);
    }

    /**
     * 清理续写内容中的AI标记和代码块标记
     */
    private String cleanContinuationContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        String cleaned = content;
        log.debug("开始清理续写内容，原始长度: {}", content.length());

        // 1. 移除开头的 <thinking>...</thinking> 块
        cleaned = removeThinkingBlocks(cleaned);

        // 2. 移除开头的 <response> 标签
        cleaned = removeResponseTags(cleaned);

        // 3. 移除开头的代码块标记（```javascript, ```html 等）
        cleaned = removeCodeBlockMarkers(cleaned);

        // 4. 清理多余的空白行
        cleaned = cleanExtraWhitespace(cleaned);

        log.debug("续写内容清理完成，清理后长度: {}, 移除字符数: {}",
                cleaned.length(), content.length() - cleaned.length());

        return cleaned;
    }

    /**
     * 移除 <thinking>...</thinking> 块和单独的thinking标记
     */
    private String removeThinkingBlocks(String content) {
        if (content == null) return content;

        String result = content;

        // 1. 移除完整的 thinking 块，支持多行和嵌套
        String completeBlockPattern = "(?s)<thinking>.*?</thinking>\\s*";
        result = result.replaceAll(completeBlockPattern, "");

        // 2. 移除开头的单独 <thinking> 标记
        result = result.replaceAll("^\\s*<thinking>\\s*", "");

        // 3. 移除结尾的单独 </thinking> 标记
        result = result.replaceAll("\\s*</thinking>\\s*$", "");

        if (!result.equals(content)) {
            log.debug("移除了 thinking 相关标记，原长度: {}, 新长度: {}", content.length(), result.length());
        }

        return result;
    }

    /**
     * 移除开头的 <response> 标签
     */
    private String removeResponseTags(String content) {
        if (content == null) return content;

        String result = content;

        // 只移除开头的 <response> 标签
        result = result.replaceAll("^\\s*<response>\\s*", "");

        if (!result.equals(content)) {
            log.debug("移除了开头的 response 标签，原长度: {}, 新长度: {}", content.length(), result.length());
        }

        return result;
    }

    /**
     * 移除开头的代码块标记
     */
    private String removeCodeBlockMarkers(String content) {
        if (content == null) return content;

        String result = content;

        // 只移除开头的代码块标记，如 ```javascript, ```html, ```css, ```json 等
        result = result.replaceAll("^\\s*```\\w*\\s*", "");

        if (!result.equals(content)) {
            log.debug("移除了开头的代码块标记，原长度: {}, 新长度: {}", content.length(), result.length());
        }

        return result;
    }

    /**
     * 清理多余的空白行
     */
    private String cleanExtraWhitespace(String content) {
        if (content == null) return content;

        String result = content;

        // 移除开头的空白行
        result = result.replaceAll("^\\s*\\n+", "");

        // 移除结尾的空白行
        result = result.replaceAll("\\n+\\s*$", "");

        if (!result.equals(content)) {
            log.debug("清理了多余空白，原长度: {}, 新长度: {}", content.length(), result.length());
        }

        return result;
    }
}
