package com.instser.chat.service;

import com.instser.chat.entity.TavilyExtractRequest;
import com.instser.chat.entity.TavilyExtractResponse;
import com.instser.chat.entity.TavilySearchRequest;
import com.instser.chat.entity.TavilySearchResponse;

/**
 * 联网搜索服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface WebSearchService {

    /**
     * 执行Tavily搜索
     *
     * @param query 搜索查询内容
     * @return 搜索响应结果
     */
    TavilySearchResponse search(String query);

    /**
     * 执行Tavily搜索（自定义请求参数）
     *
     * @param request 搜索请求对象
     * @return 搜索响应结果
     */
    TavilySearchResponse search(TavilySearchRequest request);

    /**
     * 检查搜索服务是否可用
     *
     * @return 服务是否可用
     */
    boolean isServiceAvailable();

    /**
     * 执行Tavily Extract内容提取
     *
     * @param urls 要提取内容的URL数组
     * @return 内容提取响应结果
     */
    TavilyExtractResponse extract(String[] urls);

    /**
     * 执行Tavily Extract内容提取（自定义请求参数）
     *
     * @param request 内容提取请求对象
     * @return 内容提取响应结果
     */
    TavilyExtractResponse extract(TavilyExtractRequest request);
}
