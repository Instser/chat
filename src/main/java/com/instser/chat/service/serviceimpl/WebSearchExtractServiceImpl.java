package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.TavilyExtractRequest;
import com.instser.chat.entity.TavilyExtractResponse;
import com.instser.chat.entity.TavilySearchRequest;
import com.instser.chat.entity.TavilySearchResponse;
import com.instser.chat.entity.TavilySearchResult;
import com.instser.chat.service.WebSearchExtractService;
import com.instser.chat.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 联网搜索和内容提取综合服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchExtractServiceImpl implements WebSearchExtractService {

    private final WebSearchService webSearchService;

    @Override
    public TavilyExtractResponse searchAndExtract(String query, int maxResults, int extractCount) {
        if (!StringUtils.hasText(query)) {
            log.warn("搜索查询内容为空");
            throw new IllegalArgumentException("搜索查询内容不能为空");
        }

        if (maxResults <= 0 || extractCount <= 0) {
            log.warn("参数无效: maxResults={}, extractCount={}", maxResults, extractCount);
            throw new IllegalArgumentException("maxResults和extractCount必须大于0");
        }

        if (extractCount > maxResults) {
            log.warn("extractCount({})不能大于maxResults({})", extractCount, maxResults);
            extractCount = maxResults;
        }

        try {
            log.info("开始执行搜索和提取，查询: {}, 最大结果数: {}, 提取数量: {}", query, maxResults, extractCount);

            // 第一步：执行搜索
            TavilySearchRequest searchRequest = TavilySearchRequest.builder()
                    .query(query)
                    .maxResults(String.valueOf(maxResults))
                    .searchDepth("basic")
                    .includeFavicon(true)
                    .build();

            TavilySearchResponse searchResponse = webSearchService.search(searchRequest);

            if (searchResponse == null || searchResponse.getResults() == null || searchResponse.getResults().isEmpty()) {
                log.warn("搜索未返回任何结果");
                return null;
            }

            // 第二步：提取前N个结果的URL
            List<TavilySearchResult> searchResults = searchResponse.getResults();
            int actualExtractCount = Math.min(extractCount, searchResults.size());

            String[] urlsToExtract = searchResults.stream()
                    .limit(actualExtractCount)
                    .map(TavilySearchResult::getUrl)
                    .filter(StringUtils::hasText)
                    .toArray(String[]::new);

            if (urlsToExtract.length == 0) {
                log.warn("搜索结果中没有有效的URL可供提取");
                return null;
            }

            log.info("准备提取{}个URL的详细内容", urlsToExtract.length);

            // 第三步：执行内容提取
            TavilyExtractRequest extractRequest = TavilyExtractRequest.builder()
                    .urls(urlsToExtract)
                    .includeImages(false)
                    .includeFavicon(true)
                    .extractDepth("basic")
                    .format("markdown")
                    .build();

            TavilyExtractResponse extractResponse = webSearchService.extract(extractRequest);

            log.info("搜索和提取完成，搜索结果: {}, 提取成功: {}, 提取失败: {}",
                    searchResults.size(),
                    extractResponse != null && extractResponse.getResults() != null ? extractResponse.getResults().size() : 0,
                    extractResponse != null ? getFailedResultsCount(extractResponse.getFailedResults()) : 0);

            return extractResponse;

        } catch (Exception e) {
            log.error("执行搜索和提取时发生异常，查询: {}", query, e);
            throw new RuntimeException("搜索和提取服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public TavilyExtractResponse searchAndExtract(String query) {
        // 使用默认参数：搜索20个结果，提取所有返回链接的详细内容
        return searchAndExtract(query, 20, 20);
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
