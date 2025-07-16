package com.instser.chat.service;

import com.instser.chat.entity.GeminiGroundingResponse;

/**
 * Gemini响应解析器接口
 * 用于解析Gemini API返回的响应数据
 *
 * @author instser
 * @since 2025-07-14
 */
public interface GeminiResponseParser {

    /**
     * 解析Gemini API响应
     *
     * @param responseBody 响应体JSON字符串
     * @return 解析后的Gemini grounding响应
     */
    GeminiGroundingResponse parseResponse(String responseBody);
}
