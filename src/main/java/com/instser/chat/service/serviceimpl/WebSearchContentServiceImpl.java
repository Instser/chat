package com.instser.chat.service.serviceimpl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.annotation.WebSearchMonitoring;
import com.instser.chat.common.exception.WebSearchErrorCode;
import com.instser.chat.common.exception.WebSearchException;
import com.instser.chat.dao.WebSearchContentDao;
import com.instser.chat.entity.SearchResponse;
import com.instser.chat.entity.WebSearchContent;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.metrics.WebSearchMetrics;
import com.instser.chat.service.WebSearchCacheService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.service.WebSearchRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 联网查询内容服务实现类
 *
 * @author instser
 * @since 2025-07-10
 */
@Slf4j
@Service
public class WebSearchContentServiceImpl extends ServiceImpl<WebSearchContentDao, WebSearchContent> implements WebSearchContentService {

    @Autowired
    private WebSearchContentDao webSearchContentDao;

    @Autowired
    private WebSearchRateLimitService webSearchRateLimitService;

    @Autowired
    private WebSearchCacheService webSearchCacheService;

    @Autowired
    private WebSearchMetrics webSearchMetrics;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;



    @Override
    @Transactional
    public boolean saveWebSearchContent(String messageId,
                                      SearchResponse searchResponse, Long userId, String sessionId) {
        try {
            // 参数验证
            if (messageId == null || messageId.trim().isEmpty()) {
                log.error("【联网存储】参数验证失败：消息ID为空，用户ID：{}", userId);
                return false;
            }
            if (searchResponse == null) {
                log.error("【联网存储】参数验证失败：搜索响应为空，用户ID：{}，消息ID：{}", userId, messageId);
                return false;
            }
            if (userId == null) {
                log.error("【联网存储】参数验证失败：用户ID为空，消息ID：{}", messageId);
                return false;
            }
            if (sessionId == null || sessionId.trim().isEmpty()) {
                log.error("【联网存储】参数验证失败：会话ID为空，用户ID：{}，消息ID：{}", userId, messageId);
                return false;
            }

            log.info("【联网存储】开始保存联网查询结果，用户ID：{}，消息ID：{}", userId, messageId);

            // 1. 格式化搜索结果为优化JSON
            String contentJson = formatSearchResponseToOptimizedJson(searchResponse);
            int resultCount = getValidResultCount(searchResponse);

            // 2. 创建联网内容记录
            WebSearchContent webSearchContent = new WebSearchContent();
            webSearchContent.setWebSearchId(UUID.randomUUID().toString());
            webSearchContent.setUserId(userId);
            webSearchContent.setSessionId(sessionId);
            webSearchContent.setMessageId(messageId);
            webSearchContent.setContent(contentJson);
            webSearchContent.setResultCount(resultCount);
            webSearchContent.setStatus(1);
            webSearchContent.setDeleted(0);

            // 3. 使用重试机制保存联网内容
            boolean saved = saveWebSearchContentWithRetry(webSearchContent, 3);

            // 4. 记录保存结果
            if (saved) {
                log.info("【联网存储】联网查询结果保存成功，用户ID：{}，消息ID：{}，结果数量：{}", userId, messageId, resultCount);
            } else {
                log.error("【联网存储】联网查询结果保存失败，用户ID：{}，消息ID：{}", userId, messageId);
            }

            return saved;
        } catch (Exception e) {
            log.error("【联网存储】保存联网查询结果异常，消息ID：{}，用户ID：{}", messageId, userId, e);
            return false;
        }
    }

    @Override
    public WebSearchContent getByMessageId(String messageId) {
        try {
            return webSearchContentDao.selectByMessageId(messageId);
        } catch (Exception e) {
            log.error("【联网存储】查询联网内容失败，消息ID：{}", messageId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public boolean updateWebSearchContent(String oldMessageId, String newMessageId,
                                        SearchResponse searchResponse) {
        try {
            // 参数验证
            if (oldMessageId == null || oldMessageId.trim().isEmpty()) {
                log.error("【联网存储】参数验证失败：旧消息ID为空");
                return false;
            }
            if (newMessageId == null || newMessageId.trim().isEmpty()) {
                log.error("【联网存储】参数验证失败：新消息ID为空，旧消息ID：{}", oldMessageId);
                return false;
            }
            if (searchResponse == null) {
                log.error("【联网存储】参数验证失败：搜索响应为空，旧消息ID：{}，新消息ID：{}", oldMessageId, newMessageId);
                return false;
            }

            log.info("【联网存储】开始更新联网内容，旧消息ID：{}，新消息ID：{}", oldMessageId, newMessageId);

            // 1. 格式化搜索结果为优化JSON
            String contentJson = formatSearchResponseToOptimizedJson(searchResponse);
            int resultCount = getValidResultCount(searchResponse);

            // 2. 更新联网内容记录的messageId和内容
            int updated = webSearchContentDao.updateContentAndMessageId(
                oldMessageId, newMessageId, contentJson, resultCount
            );

            if (updated > 0) {
                log.info("【联网存储】联网内容更新成功，旧消息ID：{}，新消息ID：{}", oldMessageId, newMessageId);
                return true;
            } else {
                log.warn("【联网存储】联网内容更新失败，未找到记录，旧消息ID：{}", oldMessageId);
                return false;
            }
        } catch (Exception e) {
            log.error("【联网存储】更新联网查询结果失败，旧消息ID：{}，新消息ID：{}", oldMessageId, newMessageId, e);
            return false;
        }
    }

    @Override
    public boolean deleteByMessageId(String messageId) {
        try {
            int deleted = webSearchContentDao.deleteByMessageId(messageId);
            if (deleted > 0) {
                log.info("【联网存储】联网内容删除成功，消息ID：{}", messageId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("【联网存储】删除联网内容失败，消息ID：{}", messageId, e);
            return false;
        }
    }

    @Override
    public List<WebSearchContent> getUserWebSearchHistory(Long userId, int page, int size) {
        try {
            int offset = (page - 1) * size;
            return webSearchContentDao.selectUserHistory(userId, offset, size);
        } catch (Exception e) {
            log.error("【联网存储】查询用户联网历史失败，用户ID：{}", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<WebSearchContent> getSessionWebSearchContent(String sessionId) {
        try {
            return webSearchContentDao.selectBySessionId(sessionId);
        } catch (Exception e) {
            log.error("【联网存储】查询会话联网内容失败，会话ID：{}", sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 格式化搜索结果为优化JSON（只包含title和url字段）
     */
    private String formatSearchResponseToOptimizedJson(SearchResponse searchResponse) {
        List<Map<String, String>> validResults = new ArrayList<>();
        if (searchResponse.getResults() != null) {
            for (WebSearchResult result : searchResponse.getResults()) {
                if (isValidResult(result)) {
                    Map<String, String> resultMap = new HashMap<>();
                    resultMap.put("url", result.getUrl() != null ? result.getUrl() : "");
                    resultMap.put("title", result.getTitle() != null ? result.getTitle() : "");
                    // 不再包含content和favicon字段
                    validResults.add(resultMap);
                }
            }
        }
        return JSON.toJSONString(validResults);
    }

    private boolean isValidResult(WebSearchResult result) {
        return result != null &&
               (StringUtils.hasText(result.getUrl()) ||
                StringUtils.hasText(result.getTitle()) ||
                StringUtils.hasText(result.getContent()));
    }



    private int getValidResultCount(SearchResponse searchResponse) {
        if (searchResponse.getResults() == null) return 0;
        return (int) searchResponse.getResults().stream()
                .filter(this::isValidResult)
                .count();
    }

    /**
     * 带重试机制的联网内容保存方法
     * @param webSearchContent 要保存的联网内容对象
     * @param maxRetries 最大重试次数
     * @return 保存是否成功
     */
    private boolean saveWebSearchContentWithRetry(WebSearchContent webSearchContent, int maxRetries) {
        int retryCount = 0;
        long retryDelay = 500; // 初始重试延迟500ms

        while (retryCount <= maxRetries) {
            try {
                boolean saved = save(webSearchContent);
                if (saved) {
                    if (retryCount > 0) {
                        log.info("【数据库重试】联网内容保存成功，搜索ID：{}，重试次数：{}", webSearchContent.getWebSearchId(), retryCount);
                    }
                    return true;
                } else {
                    log.warn("【数据库重试】联网内容保存失败，搜索ID：{}，尝试 {}/{}", webSearchContent.getWebSearchId(), retryCount + 1, maxRetries + 1);
                }
            } catch (Exception e) {
                log.error("【数据库重试】联网内容保存异常，搜索ID：{}，尝试 {}/{}", webSearchContent.getWebSearchId(), retryCount + 1, maxRetries + 1, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避，下次等待时间翻倍
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("【数据库重试】联网内容保存重试被中断，搜索ID：{}", webSearchContent.getWebSearchId());
                    break;
                }
            }

            retryCount++;
        }

        log.error("【数据库重试】联网内容保存失败，已达到最大重试次数，搜索ID：{}", webSearchContent.getWebSearchId());
        return false;
    }

    @Override
    @WebSearchMonitoring("updateContentByMessageId")
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContentByMessageId(String messageId, List<Map<String, Object>> content, Long userId) {
        try {
            log.info("【网络搜索修改】开始更新网络搜索内容，用户ID：{}，消息ID：{}", userId, messageId);

            // 1. 预检查阶段 - 快速失败
            validateBasicParameters(messageId, content);

            // 2. 权限验证 - 使用缓存优化
            if (!webSearchCacheService.hasPermission(userId, messageId)) {
                throw new WebSearchException(WebSearchErrorCode.NO_MESSAGE_PERMISSION);
            }

            // 3. 频率限制检查 - 高性能Redis计数
            if (!webSearchRateLimitService.checkUserUpdateRateLimit(userId)) {
                webSearchMetrics.recordRateLimit();
                throw new WebSearchException(WebSearchErrorCode.RATE_LIMIT_EXCEEDED);
            }

            // 4. 并发控制 - 分布式锁
            String lockKey = "web_search_update_lock:" + messageId;
            boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);

            if (!lockAcquired) {
                webSearchMetrics.recordConcurrentConflict();
                throw new WebSearchException(WebSearchErrorCode.CONCURRENT_MODIFICATION);
            }

            try {
                // 5. 数据库更新 - 批量操作
                String newContentJson = JSON.toJSONString(content);
                int updatedRows = webSearchContentDao.updateContentByMessageId(
                        messageId, newContentJson, content.size());

                if (updatedRows == 0) {
                    throw new WebSearchException(WebSearchErrorCode.WEB_SEARCH_CONTENT_NOT_FOUND);
                }

                // 6. 清除权限缓存
                webSearchCacheService.clearPermissionCache(userId, messageId);

                log.info("【网络搜索修改】网络搜索内容更新成功，用户ID：{}，消息ID：{}，结果数量：{}",
                        userId, messageId, content.size());
                return true;

            } finally {
                // 7. 确保锁被释放
                redisTemplate.delete(lockKey);
            }

        } catch (WebSearchException e) {
            log.warn("【网络搜索修改】业务异常，用户ID：{}，消息ID：{}，错误：{}", userId, messageId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("【网络搜索修改】系统异常，用户ID：{}，消息ID：{}", userId, messageId, e);
            throw new WebSearchException(WebSearchErrorCode.DATABASE_ERROR, e);
        }
    }

    /**
     * 基础参数验证 - 快速失败
     */
    private void validateBasicParameters(String messageId, List<Map<String, Object>> content) {
        // UUID格式验证
        try {
            UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            throw new WebSearchException(WebSearchErrorCode.MESSAGE_ID_INVALID);
        }

        // 内容基础验证
        if (content == null || content.isEmpty()) {
            throw new WebSearchException(WebSearchErrorCode.CONTENT_NULL);
        }

        if (content.size() > 50) {
            throw new WebSearchException(WebSearchErrorCode.CONTENT_TOO_MANY_ITEMS);
        }

        // 结构验证
        for (Map<String, Object> item : content) {
            if (item == null || !isValidContentFields(item)) {
                throw new WebSearchException(WebSearchErrorCode.CONTENT_INVALID_STRUCTURE);
            }
        }

        // 大小验证
        String jsonString = JSON.toJSONString(content);
        if (jsonString.getBytes(StandardCharsets.UTF_8).length > 100 * 1024) {
            throw new WebSearchException(WebSearchErrorCode.CONTENT_TOO_LARGE);
        }
    }

    /**
     * 内容字段验证
     */
    private boolean isValidContentFields(Map<String, Object> item) {
        return item.containsKey("url") &&
               item.containsKey("title") &&
               item.containsKey("content") &&
               item.containsKey("favicon");
    }
}
