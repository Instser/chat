package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tavily搜索响应实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchResponse {

    /**
     * 搜索查询内容
     */
    @JsonProperty("query")
    private String query;

    /**
     * 后续问题建议（通常为null）
     */
    @JsonProperty("follow_up_questions")
    private List<String> followUpQuestions;

    /**
     * 搜索答案（通常为null）
     */
    @JsonProperty("answer")
    private String answer;

    /**
     * 图片结果（通常为空数组）
     */
    @JsonProperty("images")
    private List<String> images;

    /**
     * 搜索结果列表
     */
    @JsonProperty("results")
    private List<TavilySearchResult> results;

    /**
     * 自动参数
     */
    @JsonProperty("auto_parameters")
    private TavilyAutoParameters autoParameters;

    /**
     * 响应时间（秒）
     */
    @JsonProperty("response_time")
    private Double responseTime;
}
