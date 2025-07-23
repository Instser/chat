package com.instser.chat.service.serviceimpl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.instser.chat.dao.WebSearchContentDao;
import com.instser.chat.service.WebSearchCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 网络搜索内容缓存服务实现
 *
 * @author instser
 * @since 2025-07-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchCacheServiceImpl implements WebSearchCacheService {

    private final WebSearchContentDao webSearchContentDao;

    // 权限缓存：5分钟过期，最大1000个条目
    private final Cache<String, Boolean> permissionCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats() // 重新启用统计功能
            .build();

    @Override
    public boolean hasPermission(Long userId, String messageId) {
        if (userId == null || messageId == null) {
            return false;
        }

        String cacheKey = "perm:" + userId + ":" + messageId;
        try {
            Boolean cached = permissionCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }

            // 缓存未命中，查询数据库并缓存结果
            boolean hasPermission = checkUserPermission(userId, messageId);
            permissionCache.put(cacheKey, hasPermission);
            return hasPermission;
        } catch (Exception e) {
            log.warn("权限缓存查询失败，cacheKey: {}", cacheKey, e);
            // 缓存失败时直接查询数据库
            return checkUserPermission(userId, messageId);
        }
    }

    @Override
    public void clearPermissionCache(Long userId, String messageId) {
        if (userId == null || messageId == null) {
            return;
        }
        
        String cacheKey = "perm:" + userId + ":" + messageId;
        permissionCache.invalidate(cacheKey);
        log.debug("清除权限缓存，cacheKey: {}", cacheKey);
    }

    @Override
    public String getCacheStats() {
        try {
            long size = permissionCache.estimatedSize();

            // 直接调用stats()方法，不使用反射
            var stats = permissionCache.stats();

            if (stats != null) {
                double hitRate = stats.hitRate() * 100;
                long requestCount = stats.requestCount();
                long hitCount = stats.hitCount();
                long missCount = stats.missCount();
                long evictionCount = stats.evictionCount();

                return String.format("权限缓存统计 - 大小: %d, 命中率: %.2f%%, 请求: %d, 命中: %d, 未命中: %d, 驱逐: %d",
                        size, hitRate, requestCount, hitCount, missCount, evictionCount);
            } else {
                return String.format("权限缓存统计 - 大小: %d (统计未启用)", size);
            }
        } catch (Exception e) {
            log.debug("获取缓存详细统计失败，返回基本信息: {}", e.getMessage());
            return String.format("权限缓存统计 - 大小: %d", permissionCache.estimatedSize());
        }
    }



    /**
     * 检查用户权限（数据库查询）
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     * @return 是否有权限
     */
    private boolean checkUserPermission(Long userId, String messageId) {
        try {
            // 查询网络搜索内容是否存在且属于该用户
            var webSearchContent = webSearchContentDao.selectByMessageId(messageId);
            if (webSearchContent == null) {
                log.debug("网络搜索内容不存在，messageId: {}", messageId);
                return false;
            }

            boolean hasPermission = userId.equals(webSearchContent.getUserId());
            log.debug("用户权限检查结果，userId: {}, messageId: {}, hasPermission: {}", 
                    userId, messageId, hasPermission);
            
            return hasPermission;
        } catch (Exception e) {
            log.error("检查用户权限异常，userId: {}, messageId: {}", userId, messageId, e);
            return false;
        }
    }

    /**
     * 缓存统计监控（每5分钟监控一次）
     */
    @Scheduled(fixedRate = 300000)
    public void monitorCacheUsage() {
        try {
            // 使用getCacheStats方法获取完整统计信息
            String statsInfo = getCacheStats();
            log.info("网络搜索权限缓存监控: {}", statsInfo);

            // 额外的性能监控逻辑
            long size = permissionCache.estimatedSize();
            if (size > 800) { // 接近最大容量时告警
                log.warn("权限缓存使用率较高，当前大小: {}/1000", size);
            }
        } catch (Exception e) {
            log.warn("权限缓存统计监控失败", e);
        }
    }
}
