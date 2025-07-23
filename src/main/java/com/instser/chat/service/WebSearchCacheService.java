package com.instser.chat.service;

/**
 * 网络搜索内容缓存服务接口
 *
 * @author instser
 * @since 2025-07-20
 */
public interface WebSearchCacheService {

    /**
     * 检查用户是否有权限访问指定消息的网络搜索内容
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     * @return 是否有权限
     */
    boolean hasPermission(Long userId, String messageId);

    /**
     * 清除权限缓存
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     */
    void clearPermissionCache(Long userId, String messageId);

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    String getCacheStats();
}
