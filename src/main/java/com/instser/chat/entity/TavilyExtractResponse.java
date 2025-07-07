package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tavily Extract响应实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TavilyExtractResponse {

    /**
     * 成功提取的结果列表
     */
    @JsonProperty("results")
    private List<TavilyExtractResult> results;

    /**
     * 失败的结果列表
     * 注意：Tavily API可能返回不同格式的failed_results，使用Object类型以提高兼容性
     */
    @JsonProperty("failed_results")
    private Object failedResults;

    /**
     * 响应时间（秒）
     */
    @JsonProperty("response_time")
    private Double responseTime;
}
