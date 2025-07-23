package com.instser.chat.service;

/**
 * 网络搜索内容修改频率限制服务接口
 *
 * @author instser
 * @since 2025-07-20
 */
public interface WebSearchRateLimitService {

    /**
     * 检查用户网络搜索内容修改频率限制
     *
     * @param userId 用户ID
     * @return 是否允许修改
     */
    boolean checkUserUpdateRateLimit(Long userId);

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    String getCacheStats();

    /**
     * 清理过期的缓存数据
     */
    void cleanExpiredCache();
}
