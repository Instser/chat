package com.instser.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.TavilyExtractRequest;
import com.instser.chat.entity.TavilyExtractResponse;
import com.instser.chat.entity.TavilySearchRequest;
import com.instser.chat.entity.TavilySearchResponse;
import com.instser.chat.service.serviceimpl.WebSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 联网搜索服务测试类
 *
 * @author instser
 * @since 2024-01-01
 */
@ExtendWith(MockitoExtension.class)
class WebSearchServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private WebSearchServiceImpl webSearchService;

    @BeforeEach
    void setUp() {
        webSearchService = new WebSearchServiceImpl(restTemplate, objectMapper);
    }

    @Test
    void testSearchWithQuery_Success() throws Exception {
        // 准备测试数据
        String query = "test query";
        String mockResponseBody = "{\"query\":\"test query\",\"results\":[]}";
        TavilySearchResponse mockResponse = new TavilySearchResponse();
        mockResponse.setQuery(query);

        // 模拟HTTP响应
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(responseEntity);

        // 模拟JSON解析
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(mockResponseBody, TavilySearchResponse.class))
                .thenReturn(mockResponse);

        // 执行测试
        TavilySearchResponse result = webSearchService.search(query);

        // 验证结果
        assertNotNull(result);
        assertEquals(query, result.getQuery());

        // 验证方法调用
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));
        verify(objectMapper, times(1)).writeValueAsString(any());
        verify(objectMapper, times(1)).readValue(mockResponseBody, TavilySearchResponse.class);
    }

    @Test
    void testSearchWithRequest_Success() throws Exception {
        // 准备测试数据
        TavilySearchRequest request = TavilySearchRequest.builder()
                .query("test query")
                .maxResults("5")
                .build();

        String mockResponseBody = "{\"query\":\"test query\",\"results\":[]}";
        TavilySearchResponse mockResponse = new TavilySearchResponse();
        mockResponse.setQuery("test query");

        // 模拟HTTP响应
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(responseEntity);

        // 模拟JSON解析
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(mockResponseBody, TavilySearchResponse.class))
                .thenReturn(mockResponse);

        // 执行测试
        TavilySearchResponse result = webSearchService.search(request);

        // 验证结果
        assertNotNull(result);
        assertEquals("test query", result.getQuery());
    }

    @Test
    void testSearchWithEmptyQuery_ThrowsException() {
        // 测试空查询
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.search("");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.search((String) null);
        });
    }

    @Test
    void testSearchWithNullRequest_ThrowsException() {
        // 测试空请求
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.search((TavilySearchRequest) null);
        });
    }

    @Test
    void testIsServiceAvailable_Success() throws Exception {
        // 准备测试数据
        String mockResponseBody = "{\"query\":\"test\",\"results\":[]}";
        TavilySearchResponse mockResponse = new TavilySearchResponse();
        mockResponse.setQuery("test");

        // 模拟HTTP响应
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(responseEntity);

        // 模拟JSON解析
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(mockResponseBody, TavilySearchResponse.class))
                .thenReturn(mockResponse);

        // 执行测试
        boolean result = webSearchService.isServiceAvailable();

        // 验证结果
        assertTrue(result);
    }

    @Test
    void testIsServiceAvailable_Failure() throws Exception {
        // 模拟异常
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // 执行测试
        boolean result = webSearchService.isServiceAvailable();

        // 验证结果
        assertFalse(result);
    }

    @Test
    void testExtractWithUrls_Success() throws Exception {
        // 准备测试数据
        String[] urls = {"https://example.com", "https://test.com"};
        String mockResponseBody = "{\"results\":[],\"failed_results\":[],\"response_time\":0.1}";
        TavilyExtractResponse mockResponse = new TavilyExtractResponse();

        // 模拟HTTP响应
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(responseEntity);

        // 模拟JSON解析
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(mockResponseBody, TavilyExtractResponse.class))
                .thenReturn(mockResponse);

        // 执行测试
        TavilyExtractResponse result = webSearchService.extract(urls);

        // 验证结果
        assertNotNull(result);

        // 验证方法调用
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));
        verify(objectMapper, times(1)).writeValueAsString(any());
        verify(objectMapper, times(1)).readValue(mockResponseBody, TavilyExtractResponse.class);
    }

    @Test
    void testExtractWithRequest_Success() throws Exception {
        // 准备测试数据
        TavilyExtractRequest request = TavilyExtractRequest.builder()
                .urls(new String[]{"https://example.com"})
                .includeImages(true)
                .format("markdown")
                .build();

        String mockResponseBody = "{\"results\":[],\"failed_results\":[],\"response_time\":0.1}";
        TavilyExtractResponse mockResponse = new TavilyExtractResponse();

        // 模拟HTTP响应
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(responseEntity);

        // 模拟JSON解析
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(mockResponseBody, TavilyExtractResponse.class))
                .thenReturn(mockResponse);

        // 执行测试
        TavilyExtractResponse result = webSearchService.extract(request);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testExtractWithEmptyUrls_ThrowsException() {
        // 测试空URL数组
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.extract(new String[]{});
        });

        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.extract((String[]) null);
        });
    }

    @Test
    void testExtractWithNullRequest_ThrowsException() {
        // 测试空请求
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.extract((TavilyExtractRequest) null);
        });

        // 测试空URL的请求
        TavilyExtractRequest emptyRequest = TavilyExtractRequest.builder()
                .urls(new String[]{})
                .build();
        assertThrows(IllegalArgumentException.class, () -> {
            webSearchService.extract(emptyRequest);
        });
    }
}
