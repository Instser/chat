package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.SearchResponse;
import com.instser.chat.entity.WebSearchContent;

import java.util.List;
import java.util.Map;

/**
 * 联网查询内容服务接口
 *
 * @author instser
 * @since 2025-07-10
 */
public interface WebSearchContentService extends IService<WebSearchContent> {

    /**
     * 保存联网查询结果
     *
     * @param messageId      消息ID
     * @param searchResponse 搜索响应
     * @param userId         用户ID
     * @param sessionId      会话ID
     * @return 是否保存成功
     */
    boolean saveWebSearchContent(String messageId,
                                SearchResponse searchResponse, Long userId, String sessionId);

    /**
     * 根据消息ID获取联网内容
     *
     * @param messageId 消息ID
     * @return 联网内容
     */
    WebSearchContent getByMessageId(String messageId);

    /**
     * 更新联网查询结果（用于重新查询）
     *
     * @param oldMessageId   旧消息ID
     * @param newMessageId   新消息ID
     * @param searchResponse 搜索响应
     * @return 是否更新成功
     */
    boolean updateWebSearchContent(String oldMessageId, String newMessageId,
                                  SearchResponse searchResponse);

    /**
     * 删除联网内容（消息删除时自动触发）
     *
     * @param messageId 消息ID
     * @return 是否删除成功
     */
    boolean deleteByMessageId(String messageId);

    /**
     * 批量删除联网内容
     *
     * @param messageIds 消息ID列表
     * @return 删除的记录数
     */
    int deleteByMessageIds(List<String> messageIds);

    /**
     * 获取用户的联网查询历史（简单分页）
     *
     * @param userId 用户ID
     * @param page   页码（从1开始）
     * @param size   页面大小
     * @return 联网内容列表
     */
    List<WebSearchContent> getUserWebSearchHistory(Long userId, int page, int size);

    /**
     * 获取会话的联网内容
     *
     * @param sessionId 会话ID
     * @return 联网内容列表
     */
    List<WebSearchContent> getSessionWebSearchContent(String sessionId);

    /**
     * 更新网络搜索内容（用户修改）
     *
     * @param messageId 消息ID
     * @param content   新的内容数组
     * @param userId    用户ID
     * @return 是否更新成功
     */
    boolean updateContentByMessageId(String messageId, List<Map<String, Object>> content, Long userId);
}
