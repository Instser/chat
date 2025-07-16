package com.instser.chat.service;

import com.instser.chat.entity.WebSearchResult;

import java.util.List;

/**
 * 联网AI服务接口
 * 用于调用联网AI获取结构化的联网搜索结果
 *
 * @author instser
 * @since 2025-07-12
 */
public interface NetworkAiService {

    /**
     * 向联网AI发送查询请求，获取WebSearchResult格式的结果
     *
     * @param userContext 用户有效上下文（原始对话内容，不添加提示词）
     * @return 转换为WebSearchResult格式的联网查询结果
     */
    List<WebSearchResult> queryWithNetworkAsWebSearchResults(String userContext);

    /**
     * 检查联网AI服务是否可用
     *
     * @return 服务是否可用
     */
    boolean isServiceAvailable();
}
