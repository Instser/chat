package com.instser.chat.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Gemini Grounding响应实体类
 * 用于接收Gemini API grounding功能返回的结构化响应
 *
 * @author instser
 * @since 2025-07-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiGroundingResponse {

    /**
     * 主要响应文本
     */
    private String responseText;

    /**
     * 搜索查询列表
     */
    private List<String> searchQueries;

    /**
     * 总来源数量
     */
    private Integer totalSources;

    /**
     * 来源列表
     */
    private List<GeminiSource> sources;

    /**
     * 文本段落列表（包含引用信息）
     */
    private List<GeminiTextSegment> textSegments;
}
