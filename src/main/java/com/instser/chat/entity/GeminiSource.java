package com.instser.chat.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Gemini来源信息实体类
 * 用于表示Gemini grounding返回的单个来源信息
 *
 * @author instser
 * @since 2025-07-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiSource {

    /**
     * 块索引
     */
    private Integer chunkIndex;

    /**
     * 页面标题
     */
    private String title;

    /**
     * 页面URL
     */
    private String url;


}
