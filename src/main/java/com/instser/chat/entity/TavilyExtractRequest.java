package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily Extract请求实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TavilyExtractRequest {

    /**
     * 要提取内容的URL列表
     */
    @JsonProperty("urls")
    private String[] urls;

    /**
     * 是否包含图片
     */
    @JsonProperty("include_images")
    @Builder.Default
    private Boolean includeImages = false;

    /**
     * 是否包含网站图标
     */
    @JsonProperty("include_favicon")
    @Builder.Default
    private Boolean includeFavicon = false;

    /**
     * 提取深度
     */
    @JsonProperty("extract_depth")
    @Builder.Default
    private String extractDepth = "basic";

    /**
     * 输出格式
     */
    @JsonProperty("format")
    @Builder.Default
    private String format = "markdown";
}
