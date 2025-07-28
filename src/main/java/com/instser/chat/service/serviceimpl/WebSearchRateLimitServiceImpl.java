package com.instser.chat.service.serviceimpl;

import com.instser.chat.service.WebSearchRateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 网络搜索内容修改频率限制服务实现
 * 与聊天频率限制保持一致的策略
 *
 * @author instser
 * @since 2025-07-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchRateLimitServiceImpl implements WebSearchRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 内存降级缓存：key -> 时间戳列表
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> fallbackCache = new ConcurrentHashMap<>();

    // 频率限制配置（与聊天请求保持一致）
    private static final int USER_RATE_LIMIT_PER_MINUTE = 20;

    // Redis可用性状态管理
    private volatile boolean redisAvailable = true;
    private volatile long lastRedisCheckTime = 0;
    private static final long REDIS_CHECK_INTERVAL = 30000; // 30秒检查一次

    @Override
    public boolean checkUserUpdateRateLimit(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，拒绝网络搜索内容修改请求");
            return false;
        }

        String key = "web_search_update:rate_limit:user:" + userId;

        // 检查Redis可用性
        if (shouldCheckRedisAvailability()) {
            checkRedisAvailability();
        }

        if (redisAvailable) {
            try {
                return checkRateLimitWithRedis(key, USER_RATE_LIMIT_PER_MINUTE);
            } catch (Exception e) {
                log.warn("Redis频率限制失败，切换到内存降级: {}", e.getMessage());
                redisAvailable = false;
                return checkRateLimitWithMemory(key, USER_RATE_LIMIT_PER_MINUTE);
            }
        } else {
            return checkRateLimitWithMemory(key, USER_RATE_LIMIT_PER_MINUTE);
        }
    }

    /**
     * 使用Redis进行频率限制检查
     */
    private boolean checkRateLimitWithRedis(String key, int maxRequests) {
        long currentTime = System.currentTimeMillis();
        String timeWindow = String.valueOf(currentTime / 60000); // 分钟级窗口
        String redisKey = key + ":" + timeWindow;

        Long count = redisTemplate.opsForValue().increment(redisKey);
        redisTemplate.expire(redisKey, 60, TimeUnit.SECONDS);

        boolean allowed = count <= maxRequests;
        
        if (!allowed) {
            log.warn("用户网络搜索内容修改频率超限，用户ID: {}, 当前次数: {}, 限制: {}次/分钟", 
                    extractUserIdFromKey(key), count, maxRequests);
        } else {
            log.debug("用户网络搜索内容修改频率检查通过，用户ID: {}, 当前次数: {}/{}", 
                    extractUserIdFromKey(key), count, maxRequests);
        }

        return allowed;
    }

    /**
     * 使用内存缓存进行频率限制检查（降级策略）
     */
    private boolean checkRateLimitWithMemory(String key, int maxRequests) {
        long currentTime = System.currentTimeMillis();
        long timeWindow = 60; // 60秒窗口

        ConcurrentHashMap<Long, Boolean> requests = fallbackCache.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        // 清理过期记录
        requests.entrySet().removeIf(entry -> currentTime - entry.getKey() > timeWindow * 1000);

        // 记录当前请求
        requests.put(currentTime, true);

        boolean allowed = requests.size() <= maxRequests;
        
        if (!allowed) {
            log.warn("用户网络搜索内容修改频率超限（内存降级），用户ID: {}, 当前次数: {}, 限制: {}次/分钟", 
                    extractUserIdFromKey(key), requests.size(), maxRequests);
        } else {
            log.debug("用户网络搜索内容修改频率检查通过（内存降级），用户ID: {}, 当前次数: {}/{}", 
                    extractUserIdFromKey(key), requests.size(), maxRequests);
        }

        return allowed;
    }

    /**
     * 检查Redis可用性
     */
    private void checkRedisAvailability() {
        try {
            redisTemplate.opsForValue().get("health_check");
            if (!redisAvailable) {
                log.info("Redis连接恢复正常，网络搜索频率限制切换回Redis");
            }
            redisAvailable = true;
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis连接仍然异常，继续使用内存降级: {}", e.getMessage());
        }
        lastRedisCheckTime = System.currentTimeMillis();
    }

    /**
     * 是否需要检查Redis可用性
     */
    private boolean shouldCheckRedisAvailability() {
        return !redisAvailable && (System.currentTimeMillis() - lastRedisCheckTime > REDIS_CHECK_INTERVAL);
    }

    /**
     * 从Redis键中提取用户ID
     */
    private String extractUserIdFromKey(String key) {
        try {
            String[] parts = key.split(":");
            return parts[parts.length - 1];
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void cleanExpiredCache() {
        try {
            long currentTime = System.currentTimeMillis();
            long oneHourAgo = currentTime - TimeUnit.HOURS.toMillis(1);
            
            fallbackCache.entrySet().removeIf(entry -> {
                ConcurrentHashMap<Long, Boolean> requests = entry.getValue();
                requests.entrySet().removeIf(requestEntry -> requestEntry.getKey() < oneHourAgo);
                return requests.isEmpty();
            });

            log.debug("网络搜索频率限制缓存清理完成");
        } catch (Exception e) {
            log.error("清理网络搜索频率限制缓存失败", e);
        }
    }

    @Override
    public String getCacheStats() {
        return String.format("WebSearchRateLimit缓存: %d个键, Redis状态: %s", 
                fallbackCache.size(), redisAvailable ? "可用" : "降级");
    }
}
