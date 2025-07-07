package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily搜索请求实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchRequest {

    /**
     * 搜索查询内容
     */
    @JsonProperty("query")
    private String query;

    /**
     * 自动参数设置
     */
    @JsonProperty("auto_parameters")
    @Builder.Default
    private Boolean autoParameters = true;

    /**
     * 搜索深度
     */
    @JsonProperty("search_depth")
    @Builder.Default
    private String searchDepth = "basic";

    /**
     * 每个来源的块数
     */
    @JsonProperty("chunks_per_source")
    @Builder.Default
    private String chunksPerSource = "1";

    /**
     * 最大结果数
     */
    @JsonProperty("max_results")
    @Builder.Default
    private String maxResults = "20";

    /**
     * 是否包含答案
     */
    @JsonProperty("include_answer")
    @Builder.Default
    private Boolean includeAnswer = false;

    /**
     * 是否包含原始内容
     */
    @JsonProperty("include_raw_content")
    @Builder.Default
    private Boolean includeRawContent = false;

    /**
     * 是否包含图片
     */
    @JsonProperty("include_images")
    @Builder.Default
    private Boolean includeImages = false;

    /**
     * 是否包含图片描述
     */
    @JsonProperty("include_image_descriptions")
    @Builder.Default
    private Boolean includeImageDescriptions = false;

    /**
     * 是否包含网站图标
     */
    @JsonProperty("include_favicon")
    @Builder.Default
    private Boolean includeFavicon = true;
}
