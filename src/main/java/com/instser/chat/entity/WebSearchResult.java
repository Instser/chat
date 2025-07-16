package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网搜索结果项实体类
 * 重构后的统一搜索结果格式，去除Tavily字样
 *
 * @author instser
 * @since 2025-07-12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchResult {

    /**
     * 结果URL
     */
    @JsonProperty("url")
    private String url;

    /**
     * 页面标题
     */
    @JsonProperty("title")
    private String title;

    /**
     * 内容摘要
     */
    @JsonProperty("content")
    private String content;

    /**
     * 相关性评分
     */
    @JsonProperty("score")
    private Double score;

    /**
     * 原始内容（用于AI请求拼接）
     */
    @JsonProperty("raw_content")
    private String rawContent;

    /**
     * 网站图标URL
     */
    @JsonProperty("favicon")
    private String favicon;
}
