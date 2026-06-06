package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.entity.GeminiSource;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.GeminiNetworkService;
import com.instser.chat.service.NetworkAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 联网AI服务实现类
 * 重构为使用Gemini API的grounding功能
 *
 * @author instser
 * @since 2025-07-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkAiServiceImpl implements NetworkAiService {

    private final GeminiNetworkService geminiNetworkService;



    @Override
    public List<WebSearchResult> queryWithNetworkAsWebSearchResults(String userContext) {
        if (userContext == null || userContext.trim().isEmpty()) {
            log.warn("【联网AI】用户上下文为空，返回空结果");
            return new ArrayList<>();
        }

        try {
            log.info("【Gemini联网】开始处理联网查询，会话上下文长度：{}", userContext.length());

            // 使用Gemini服务替代原有实现
            GeminiGroundingResponse geminiResponse = geminiNetworkService.queryWithGrounding(userContext);

            if (geminiResponse == null || geminiResponse.getSources() == null) {
                log.warn("【Gemini联网】Gemini响应为空，返回空结果列表");
                return new ArrayList<>();
            }

            log.info("【Gemini联网】成功获取{}个联网搜索结果", geminiResponse.getSources().size());

            // 转换Gemini sources为WebSearchResult（仅用于数据库存储）
            List<WebSearchResult> webSearchResults = geminiResponse.getSources().stream()
                .filter(this::isValidGeminiSource)
                .limit(20) // 限制最多20个结果
                .map(this::convertGeminiSourceToWebSearchResult)
                .collect(Collectors.toList());

            log.info("【Gemini联网】成功转换{}个联网搜索结果为WebSearchResult格式", webSearchResults.size());
            return webSearchResults;

        } catch (Exception e) {
            log.error("【Gemini联网】查询失败：{}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public GeminiGroundingResponse queryWithGeminiGrounding(String userContext) {
        return geminiNetworkService.queryWithGrounding(userContext);
    }

    /**
     * 验证Gemini来源是否有效
     */
    private boolean isValidGeminiSource(GeminiSource source) {
        return source != null
            && source.getUrl() != null && !source.getUrl().trim().isEmpty()
            && source.getTitle() != null && !source.getTitle().trim().isEmpty();
    }

    /**
     * 将GeminiSource转换为WebSearchResult
     * 只包含title和url字段
     */
    private WebSearchResult convertGeminiSourceToWebSearchResult(GeminiSource source) {
        WebSearchResult result = new WebSearchResult();
        result.setUrl(source.getUrl());
        result.setTitle(source.getTitle());

        // 不设置其他字段，保持为null
        return result;
    }





    @Override
    public boolean isServiceAvailable() {
        try {
            // 检查Gemini服务是否可用
            return geminiNetworkService.isServiceAvailable();
        } catch (Exception e) {
            log.warn("【Gemini联网】服务不可用：{}", e.getMessage());
            return false;
        }
    }
}
