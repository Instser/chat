package com.instser.chat.service;

import com.instser.chat.entity.GeminiGroundingResponse;

/**
 * Gemini联网服务接口
 * 用于调用Gemini API的grounding功能进行联网搜索
 *
 * @author instser
 * @since 2025-07-14
 */
public interface GeminiNetworkService {

    /**
     * 使用Gemini grounding功能进行联网查询
     *
     * @param userContext 用户查询上下文
     * @return Gemini grounding响应
     */
    GeminiGroundingResponse queryWithGrounding(String userContext);

    /**
     * 检查Gemini服务是否可用
     *
     * @return 服务是否可用
     */
    boolean isServiceAvailable();
}
