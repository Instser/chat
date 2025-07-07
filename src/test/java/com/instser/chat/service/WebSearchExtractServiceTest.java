package com.instser.chat.service;

import com.instser.chat.entity.TavilyExtractRequest;
import com.instser.chat.entity.TavilyExtractResponse;
import com.instser.chat.entity.TavilySearchRequest;
import com.instser.chat.entity.TavilySearchResponse;
import com.instser.chat.entity.TavilySearchResult;

import com.instser.chat.service.serviceimpl.WebSearchExtractServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 联网搜索和内容提取综合服务测试类
 *
 * @author instser
 * @since 2024-01-01
 */
@ExtendWith(MockitoExtension.class)
class WebSearchExtractServiceTest {

    @Mock
    private WebSearchService webSearchService;

    private WebSearchExtractServiceImpl webSearchExtractService;

    @BeforeEach
    void setUp() {
        webSearchExtractService = new WebSearchExtractServiceImpl(webSearchService);
    }

    @Test
    void testSearchAndExtractWithParameters_Success() throws Exception {
        // 准备测试数据
        String query = "test query";
        int maxResults = 5;
        int extractCount = 2;

        // 模拟搜索响应
        TavilySearchResponse searchResponse = new TavilySearchResponse();
        searchResponse.setQuery(query);
        TavilySearchResult result1 = new TavilySearchResult();
        result1.setUrl("https://example1.com");
        result1.setTitle("Example 1");
        TavilySearchResult result2 = new TavilySearchResult();
        result2.setUrl("https://example2.com");
        result2.setTitle("Example 2");
        searchResponse.setResults(Arrays.asList(result1, result2));

        // 模拟提取响应
        TavilyExtractResponse extractResponse = new TavilyExtractResponse();
        extractResponse.setResponseTime(0.5);

        // 模拟服务调用
        when(webSearchService.search(any(TavilySearchRequest.class))).thenReturn(searchResponse);
        when(webSearchService.extract(any(TavilyExtractRequest.class))).thenReturn(extractResponse);

        // 执行测试
        TavilyExtractResponse result = webSearchExtractService.searchAndExtract(query, maxResults, extractCount);

        // 验证结果
        assertNotNull(result);

        // 验证方法调用
        verify(webSearchService, times(1)).search(any(TavilySearchRequest.class));
        verify(webSearchService, times(1)).extract(any(TavilyExtractRequest.class));
    }

    @Test
    void testSearchAndExtractWithDefaultParameters_Success() throws Exception {
        // 准备测试数据
        String query = "test query";

        // 模拟搜索响应
        TavilySearchResponse searchResponse = new TavilySearchResponse();
        searchResponse.setQuery(query);
        TavilySearchResult result1 = new TavilySearchResult();
        result1.setUrl("https://example1.com");
        searchResponse.setResults(Collections.singletonList(result1));

        // 模拟提取响应
        TavilyExtractResponse extractResponse = new TavilyExtractResponse();

        // 模拟服务调用
        when(webSearchService.search(any(TavilySearchRequest.class))).thenReturn(searchResponse);
        when(webSearchService.extract(any(TavilyExtractRequest.class))).thenReturn(extractResponse);

        // 执行测试
        TavilyExtractResponse result = webSearchExtractService.searchAndExtract(query);

        // 验证结果
        assertNotNull(result);

        // 验证方法调用（默认参数：搜索20个结果，提取所有返回链接）
        verify(webSearchService, times(1)).search(any(TavilySearchRequest.class));
        verify(webSearchService, times(1)).extract(any(TavilyExtractRequest.class));
    }

    @Test
    void testSearchAndExtract_NoSearchResults() throws Exception {
        // 准备测试数据
        String query = "test query";

        // 模拟空搜索响应
        TavilySearchResponse searchResponse = new TavilySearchResponse();
        searchResponse.setQuery(query);
        searchResponse.setResults(Collections.emptyList());

        // 模拟服务调用
        when(webSearchService.search(any(TavilySearchRequest.class))).thenReturn(searchResponse);

        // 执行测试
        TavilyExtractResponse result = webSearchExtractService.searchAndExtract(query, 5, 2);

        // 验证结果
        assertNull(result); // 没有搜索结果，返回null

        // 验证方法调用
        verify(webSearchService, times(1)).search(any(TavilySearchRequest.class));
        verify(webSearchService, never()).extract(any(TavilyExtractRequest.class)); // 不应该调用提取
    }

    @Test
    void testSearchAndExtract_EmptyQuery_ThrowsException() {
        // 测试空查询
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchExtractService.searchAndExtract("", 5, 2);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            webSearchExtractService.searchAndExtract(null, 5, 2);
        });
    }

    @Test
    void testSearchAndExtract_InvalidParameters_ThrowsException() {
        // 测试无效参数
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchExtractService.searchAndExtract("test", 0, 2);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            webSearchExtractService.searchAndExtract("test", 5, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            webSearchExtractService.searchAndExtract("test", -1, 2);
        });
    }

    @Test
    void testSearchAndExtract_ExtractCountGreaterThanMaxResults() throws Exception {
        // 准备测试数据
        String query = "test query";
        int maxResults = 2;
        int extractCount = 5; // 大于maxResults

        // 模拟搜索响应
        TavilySearchResponse searchResponse = new TavilySearchResponse();
        searchResponse.setQuery(query);
        TavilySearchResult result1 = new TavilySearchResult();
        result1.setUrl("https://example1.com");
        searchResponse.setResults(Collections.singletonList(result1));

        // 模拟提取响应
        TavilyExtractResponse extractResponse = new TavilyExtractResponse();

        // 模拟服务调用
        when(webSearchService.search(any(TavilySearchRequest.class))).thenReturn(searchResponse);
        when(webSearchService.extract(any(TavilyExtractRequest.class))).thenReturn(extractResponse);

        // 执行测试（应该自动调整extractCount为maxResults）
        TavilyExtractResponse result = webSearchExtractService.searchAndExtract(query, maxResults, extractCount);

        // 验证结果
        assertNotNull(result);

        // 验证方法调用
        verify(webSearchService, times(1)).search(any(TavilySearchRequest.class));
        verify(webSearchService, times(1)).extract(any(TavilyExtractRequest.class));
    }
}
