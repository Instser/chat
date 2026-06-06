package com.instser.chat.service;

import com.instser.chat.dto.NetworkSearchProcessResult;

import java.util.List;
import java.util.Map;

/**
 * 联网搜索业务编排服务。
 */
public interface NetworkSearchOrchestrator {

    NetworkSearchProcessResult processNetworkContent(List<Map<String, String>> messages, String query);
}
