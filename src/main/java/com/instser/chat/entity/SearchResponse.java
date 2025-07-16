package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索响应实体类
 * 重构后的简化版本，去除Tavily字样
 *
 * @author instser
 * @since 2025-07-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /**
     * 搜索查询内容
     */
    @JsonProperty("query")
    private String query;

    /**
     * 搜索结果列表
     */
    @JsonProperty("results")
    private List<WebSearchResult> results;

    /**
     * 响应时间（秒）
     */
    @JsonProperty("response_time")
    private Double responseTime;
}
