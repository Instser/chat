package com.instser.chat.service;

import com.instser.chat.entity.TavilyExtractResponse;

/**
 * 联网搜索和内容提取综合服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface WebSearchExtractService {

    /**
     * 搜索并提取详细内容
     * 先执行搜索获取URL列表，然后对前N个结果执行内容提取
     *
     * @param query 搜索查询内容
     * @param maxResults 最大搜索结果数量
     * @param extractCount 要提取详细内容的结果数量
     * @return 内容提取结果
     */
    TavilyExtractResponse searchAndExtract(String query, int maxResults, int extractCount);

    /**
     * 搜索并提取详细内容（使用默认参数）
     * 默认搜索20个结果，提取所有返回链接的详细内容
     *
     * @param query 搜索查询内容
     * @return 内容提取结果
     */
    TavilyExtractResponse searchAndExtract(String query);


}
