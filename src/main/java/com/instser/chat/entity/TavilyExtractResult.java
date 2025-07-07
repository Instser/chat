package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tavily Extract单个结果实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilyExtractResult {

    /**
     * 提取的URL
     */
    @JsonProperty("url")
    private String url;

    /**
     * 提取的原始内容
     */
    @JsonProperty("raw_content")
    private String rawContent;

    /**
     * 图片列表
     */
    @JsonProperty("images")
    private List<String> images;

    /**
     * 网站图标URL
     */
    @JsonProperty("favicon")
    private String favicon;
}
