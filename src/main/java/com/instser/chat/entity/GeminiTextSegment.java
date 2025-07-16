package com.instser.chat.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Gemini文本段落实体类
 * 用于表示Gemini grounding返回的文本段落及其引用信息
 *
 * @author instser
 * @since 2025-07-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiTextSegment {

    /**
     * 支持索引
     */
    private Integer supportIndex;

    /**
     * 文本内容
     */
    private String text;

    /**
     * 开始索引
     */
    private Integer startIndex;

    /**
     * 结束索引
     */
    private Integer endIndex;

    /**
     * 来源索引列表
     */
    private List<Integer> sourceIndices;

    /**
     * 来源信息列表
     */
    private List<GeminiSource> sources;
}
