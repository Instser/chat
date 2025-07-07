package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily搜索结果项实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchResult {

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
     * 原始内容（通常为null）
     */
    @JsonProperty("raw_content")
    private String rawContent;

    /**
     * 网站图标URL
     */
    @JsonProperty("favicon")
    private String favicon;
}
