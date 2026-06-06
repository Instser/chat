package com.instser.chat.dto;

import com.instser.chat.entity.WebSearchResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 联网搜索处理结果。
 */
@Getter
@AllArgsConstructor
public class NetworkSearchProcessResult {
    private final List<Map<String, String>> messages;
    private final List<WebSearchResult> searchResults;
}
