package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.TavilyExtractRequest;
import com.instser.chat.entity.TavilyExtractResponse;
import com.instser.chat.entity.TavilySearchRequest;
import com.instser.chat.entity.TavilySearchResponse;
import com.instser.chat.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 联网搜索服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchServiceImpl implements WebSearchService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Tavily Search API URL
     */
    private static final String TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";

    /**
     * Tavily Extract API URL
     */
    private static final String TAVILY_EXTRACT_API_URL = "https://api.tavily.com/extract";

    /**
     * Tavily API Token
     */
    private static final String TAVILY_API_TOKEN = "your-tavily-api-key";

    @Override
    public TavilySearchResponse search(String query) {
        if (!StringUtils.hasText(query)) {
            log.warn("搜索查询内容为空");
            throw new IllegalArgumentException("搜索查询内容不能为空");
        }

        // 构建默认搜索请求
        TavilySearchRequest request = TavilySearchRequest.builder()
                .query(query)
                .build();

        return search(request);
    }

    @Override
    public TavilySearchResponse search(TavilySearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuery())) {
            log.warn("搜索请求无效");
            throw new IllegalArgumentException("搜索请求不能为空且必须包含查询内容");
        }

        try {
            log.info("开始执行Tavily搜索，查询内容: {}", request.getQuery());

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(TAVILY_API_TOKEN);

            // 构建请求体
            String requestBody = objectMapper.writeValueAsString(request);
            HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);

            log.debug("Tavily搜索请求: {}", requestBody);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    TAVILY_SEARCH_API_URL,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 解析响应
                TavilySearchResponse searchResponse = objectMapper.readValue(
                        response.getBody(),
                        TavilySearchResponse.class
                );

                log.info("Tavily搜索成功，查询: {}，结果数量: {}，响应时间: {}秒",
                        searchResponse.getQuery(),
                        searchResponse.getResults() != null ? searchResponse.getResults().size() : 0,
                        searchResponse.getResponseTime());

                return searchResponse;
            } else {
                log.error("Tavily搜索失败，HTTP状态码: {}, 响应体: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("搜索服务返回错误状态: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("执行Tavily搜索时发生异常，查询内容: {}", request.getQuery(), e);
            throw new RuntimeException("搜索服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public TavilyExtractResponse extract(String[] urls) {
        if (urls == null || urls.length == 0) {
            log.warn("URL列表为空");
            throw new IllegalArgumentException("URL列表不能为空");
        }

        // 构建默认提取请求
        TavilyExtractRequest request = TavilyExtractRequest.builder()
                .urls(urls)
                .build();

        return extract(request);
    }

    @Override
    public TavilyExtractResponse extract(TavilyExtractRequest request) {
        if (request == null || request.getUrls() == null || request.getUrls().length == 0) {
            log.warn("提取请求无效");
            throw new IllegalArgumentException("提取请求不能为空且必须包含URL列表");
        }

        try {
            log.info("开始执行Tavily Extract，URL数量: {}", request.getUrls().length);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(TAVILY_API_TOKEN);

            // 构建请求体
            String requestBody = objectMapper.writeValueAsString(request);
            HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);

            log.debug("Tavily Extract请求: {}", requestBody);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    TAVILY_EXTRACT_API_URL,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 解析响应
                TavilyExtractResponse extractResponse = objectMapper.readValue(
                        response.getBody(),
                        TavilyExtractResponse.class
                );

                log.info("Tavily Extract成功，URL数量: {}，成功提取: {}，失败: {}，响应时间: {}秒",
                        request.getUrls().length,
                        extractResponse.getResults() != null ? extractResponse.getResults().size() : 0,
                        getFailedResultsCount(extractResponse.getFailedResults()),
                        extractResponse.getResponseTime());

                return extractResponse;
            } else {
                log.error("Tavily Extract失败，HTTP状态码: {}, 响应体: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("内容提取服务返回错误状态: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("执行Tavily Extract时发生异常，URL数量: {}", request.getUrls().length, e);
            throw new RuntimeException("内容提取服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isServiceAvailable() {
        try {
            // 执行一个简单的测试搜索来检查服务可用性
            TavilySearchRequest testRequest = TavilySearchRequest.builder()
                    .query("test")
                    .maxResults("1")
                    .build();

            search(testRequest);
            return true;
        } catch (Exception e) {
            log.warn("Tavily搜索服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取失败结果的数量
     * 处理不同类型的failedResults字段
     *
     * @param failedResults 失败结果对象
     * @return 失败结果数量
     */
    private int getFailedResultsCount(Object failedResults) {
        if (failedResults == null) {
            return 0;
        }

        if (failedResults instanceof java.util.List) {
            return ((java.util.List<?>) failedResults).size();
        }

        if (failedResults instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) failedResults).size();
        }

        // 如果是其他类型，返回1表示有一个失败结果
        return 1;
    }
}
