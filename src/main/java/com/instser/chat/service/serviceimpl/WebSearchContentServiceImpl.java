package com.instser.chat.service.serviceimpl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.WebSearchContentDao;
import com.instser.chat.entity.TavilySearchResponse;
import com.instser.chat.entity.TavilySearchResult;
import com.instser.chat.entity.WebSearchContent;

import com.instser.chat.service.WebSearchContentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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



    @Override
    @Transactional
    public boolean saveWebSearchContent(String messageId,
                                      TavilySearchResponse searchResponse, Long userId, String sessionId) {
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

            // 3. 保存联网内容
            boolean saved = save(webSearchContent);

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
                                        TavilySearchResponse searchResponse) {
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
     * 格式化搜索结果为优化JSON（保留favicon，限制content为500字符）
     */
    private String formatSearchResponseToOptimizedJson(TavilySearchResponse searchResponse) {
        List<Map<String, String>> validResults = new ArrayList<>();
        if (searchResponse.getResults() != null) {
            for (TavilySearchResult result : searchResponse.getResults()) {
                if (isValidResult(result)) {
                    Map<String, String> resultMap = new HashMap<>();
                    resultMap.put("url", result.getUrl() != null ? result.getUrl() : "");
                    resultMap.put("title", result.getTitle() != null ? result.getTitle() : "");
                    resultMap.put("content", truncateContent(result.getContent()));
                    resultMap.put("favicon", result.getFavicon() != null ? result.getFavicon() : "");
                    validResults.add(resultMap);
                }
            }
        }
        return JSON.toJSONString(validResults);
    }

    private boolean isValidResult(TavilySearchResult result) {
        return result != null &&
               (StringUtils.hasText(result.getUrl()) ||
                StringUtils.hasText(result.getTitle()) ||
                StringUtils.hasText(result.getContent()));
    }

    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 500 ? content.substring(0, 500) + "..." : content;
    }

    private int getValidResultCount(TavilySearchResponse searchResponse) {
        if (searchResponse.getResults() == null) return 0;
        return (int) searchResponse.getResults().stream()
                .filter(this::isValidResult)
                .count();
    }
}
