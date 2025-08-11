package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.GeminiNetworkService;
import com.instser.chat.service.GeminiResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini联网服务实现类
 * 基于gemini-test.py的实现方式，使用HTTP调用Gemini API
 *
 * @author instser
 * @since 2025-07-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiNetworkServiceImpl implements GeminiNetworkService {

    @Qualifier("geminiRestTemplate")
    private final RestTemplate restTemplate;

    private final AiModelService aiModelService;
    private final GeminiResponseParser responseParser;

    private static final Long WEB_SEARCH_MODEL_ID = 6L;


    @Override
    public GeminiGroundingResponse queryWithGrounding(String userContext) {
        if (userContext == null || userContext.trim().isEmpty()) {
            log.warn("【Gemini联网】会话上下文为空，返回空结果");
            return null;
        }

        try {
            // 从数据库获取联网模型配置
            AiModel webSearchModel = aiModelService.getById(WEB_SEARCH_MODEL_ID);
            if (webSearchModel == null) {
                log.error("【Gemini联网】未找到ID为{}的联网模型配置，请检查数据库。", WEB_SEARCH_MODEL_ID);
                throw new RuntimeException("联网服务配置异常，请联系管理员");
            }

            log.info("【Gemini联网】开始处理联网查询，使用模型：{}，会话上下文长度：{}", webSearchModel.getModelCode(), userContext.length());
            log.debug("【Gemini联网】发送的完整上下文内容：\n{}", userContext);

            // 构建请求体
            Map<String, Object> requestBody = buildGeminiRequest(userContext);

            // 设置请求头，使用Bearer认证
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(webSearchModel.getApiKey());

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            // 构建请求URL
            String requestUrl = String.format("%s/models/%s:generateContent",
                    webSearchModel.getBaseUrl(), webSearchModel.getModelCode());

            log.info("【Gemini联网】发送请求到：{}，模型：{}", webSearchModel.getBaseUrl(), webSearchModel.getModelCode());

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("【Gemini联网】请求失败，状态码：{}，响应：{}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Gemini API请求失败：" + response.getStatusCode());
            }

            log.info("【Gemini联网】API调用成功，开始解析响应");

            // 解析响应
            GeminiGroundingResponse result = responseParser.parseResponse(response.getBody());

            if (result != null && result.getSources() != null) {
                log.info("【联网搜索结果】获取到{}个搜索来源，解析成功", result.getSources().size());
            } else {
                log.warn("【联网搜索结果】未获取到有效的搜索来源");
            }

            return result;

        } catch (Exception e) {
            log.error("【Gemini联网】查询失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建Gemini API请求体
     * 基于gemini-test.py的实现方式，使用硬编码参数
     */
    private Map<String, Object> buildGeminiRequest(String userContext) {
        Map<String, Object> requestBody = new HashMap<>();

        // 构建contents
        Map<String, Object> part = new HashMap<>();
        part.put("text", userContext);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        requestBody.put("contents", List.of(content));

        // 构建tools（grounding工具）
        Map<String, Object> googleSearch = new HashMap<>();
        // 空的googleSearch对象，启用默认的Google搜索

        Map<String, Object> tool = new HashMap<>();
        tool.put("googleSearch", googleSearch);

        requestBody.put("tools", List.of(tool));

        return requestBody;
    }

    @Override
    public boolean isServiceAvailable() {
        try {
            // 执行一个简单的测试查询
            GeminiGroundingResponse response = queryWithGrounding("test");
            return response != null;
        } catch (Exception e) {
            log.warn("【Gemini联网】服务不可用：{}", e.getMessage());
            return false;
        }
    }
}
