package com.instser.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网搜索和内容提取综合结果
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchExtractResult {

    /**
     * 搜索响应结果
     */
    private TavilySearchResponse searchResponse;

    /**
     * 内容提取响应结果
     */
    private TavilyExtractResponse extractResponse;
}
