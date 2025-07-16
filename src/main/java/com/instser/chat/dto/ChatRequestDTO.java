package com.instser.chat.dto;

import com.instser.chat.entity.WebSearchResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 聊天请求DTO
 *
 * @author instser
 * @since 2025-06-25
 */
@Data
public class ChatRequestDTO {

    /**
     * 消息列表
     */
    private List<Map<String, String>> messages;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 是否深度思考
     */
    private Boolean deepThinking;

    /**
     * 是否启用网络搜索
     */
    private Boolean network;

    /**
     * 文件ID列表（逗号分隔的字符串）
     */
    private String filesId;

    /**
     * 联网搜索结果（内部使用，不从请求中解析）
     */
    private List<WebSearchResult> networkSearchResults;

    /**
     * 原始消息列表（用于标题生成，不包含联网搜索内容，内部使用）
     */
    private List<Map<String, String>> originalMessages;

    /**
     * 从Map构建ChatRequestDTO
     *
     * @param requestMap 请求Map
     * @return ChatRequestDTO对象
     */
    @SuppressWarnings("unchecked")
    public static ChatRequestDTO fromMap(Map<String, Object> requestMap) {
        ChatRequestDTO dto = new ChatRequestDTO();
        
        // 设置消息列表
        dto.setMessages((List<Map<String, String>>) requestMap.get("messages"));
        
        // 设置用户ID
        Object userIdObj = requestMap.get("userId");
        if (userIdObj != null) {
            dto.setUserId(Long.valueOf(userIdObj.toString()));
        }
        
        // 设置会话ID
        dto.setSessionId((String) requestMap.get("sessionId"));
        
        // 设置模型ID
        Object modelIdObj = requestMap.get("modelId");
        if (modelIdObj != null) {
            dto.setModelId(Long.valueOf(modelIdObj.toString()));
        }
        
        // 设置深度思考参数
        Object deepThinkingObj = requestMap.get("deepThinking");
        if (deepThinkingObj != null) {
            if (deepThinkingObj instanceof Boolean) {
                dto.setDeepThinking((Boolean) deepThinkingObj);
            } else if (deepThinkingObj instanceof String) {
                dto.setDeepThinking("true".equalsIgnoreCase((String) deepThinkingObj) || "1".equals(deepThinkingObj));
            } else if (deepThinkingObj instanceof Number) {
                dto.setDeepThinking(((Number) deepThinkingObj).intValue() == 1);
            }
        }

        // 设置网络搜索参数
        Object networkObj = requestMap.get("network");
        if (networkObj != null) {
            if (networkObj instanceof Boolean) {
                dto.setNetwork((Boolean) networkObj);
            } else if (networkObj instanceof String) {
                dto.setNetwork("true".equalsIgnoreCase((String) networkObj) || "1".equals(networkObj));
            } else if (networkObj instanceof Number) {
                dto.setNetwork(((Number) networkObj).intValue() == 1);
            }
        }

        // 设置文件ID列表
        dto.setFilesId((String) requestMap.get("filesId"));

        return dto;
    }
}
