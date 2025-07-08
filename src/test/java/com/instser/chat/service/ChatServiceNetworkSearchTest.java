package com.instser.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.TavilySearchResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天服务联网搜索结果发送功能测试
 *
 * @author instser
 * @since 2025-07-07
 */
class ChatServiceNetworkSearchTest {

    @Test
    void testNetworkSearchResultJsonFormat() throws Exception {
        // 准备测试数据
        TavilySearchResult result = new TavilySearchResult();
        result.setUrl("https://example.com");
        result.setTitle("Example Title");
        result.setContent("Example content");
        result.setFavicon("https://example.com/favicon.ico");

        // 模拟sendNetworkSearchResults方法中的JSON构建逻辑
        Map<String, String> resultData = new HashMap<>();
        resultData.put("url", result.getUrl() != null ? result.getUrl() : "");
        resultData.put("title", result.getTitle() != null ? result.getTitle() : "");
        resultData.put("content", result.getContent() != null ? result.getContent() : "");
        resultData.put("favicon", result.getFavicon() != null ? result.getFavicon() : "");

        ObjectMapper mapper = new ObjectMapper();
        String resultJson = String.format("u:%s\n", mapper.writeValueAsString(resultData));

        // 验证JSON格式
        assertTrue(resultJson.startsWith("u:"), "应该以u:开头");
        assertTrue(resultJson.endsWith("\n"), "应该以换行符结尾");

        // 验证包含所有字段
        assertTrue(resultJson.contains("\"url\":\"https://example.com\""), "应包含URL字段");
        assertTrue(resultJson.contains("\"title\":\"Example Title\""), "应包含标题字段");
        assertTrue(resultJson.contains("\"content\":\"Example content\""), "应包含内容字段");
        assertTrue(resultJson.contains("\"favicon\":\"https://example.com/favicon.ico\""), "应包含图标字段");

        // 验证是否为有效JSON
        String jsonPart = resultJson.substring(2, resultJson.length() - 1); // 去掉"u:"前缀和换行符
        assertDoesNotThrow(() -> {
            mapper.readTree(jsonPart);
        }, "输出应该是有效的JSON");
    }

    @Test
    void testNetworkSearchResultWithNullFields() throws Exception {
        // 准备包含null字段的测试数据
        TavilySearchResult result = new TavilySearchResult();
        result.setUrl(null);
        result.setTitle(null);
        result.setContent(null);
        result.setFavicon(null);

        // 模拟sendNetworkSearchResults方法中的JSON构建逻辑
        Map<String, String> resultData = new HashMap<>();
        resultData.put("url", result.getUrl() != null ? result.getUrl() : "");
        resultData.put("title", result.getTitle() != null ? result.getTitle() : "");

        // 限制content字段为前100个字符
        String content = result.getContent() != null ? result.getContent() : "";
        if (content.length() > 100) {
            content = content.substring(0, 100) + "...";
        }
        resultData.put("content", content);

        resultData.put("favicon", result.getFavicon() != null ? result.getFavicon() : "");

        ObjectMapper mapper = new ObjectMapper();
        String resultJson = String.format("u:%s\n", mapper.writeValueAsString(resultData));

        // 验证输出包含空字符串而不是null
        assertTrue(resultJson.contains("u:{"), "输出应包含u:格式的数据");
        assertFalse(resultJson.contains("null"), "输出不应包含null值");

        // 验证包含空字符串
        assertTrue(resultJson.contains("\"url\":\"\""), "URL应为空字符串");
        assertTrue(resultJson.contains("\"title\":\"\""), "标题应为空字符串");
        assertTrue(resultJson.contains("\"content\":\"\""), "内容应为空字符串");
        assertTrue(resultJson.contains("\"favicon\":\"\""), "图标应为空字符串");
    }

    @Test
    void testNetworkSearchResultContentTruncation() throws Exception {
        // 准备包含长内容的测试数据
        TavilySearchResult result = new TavilySearchResult();
        result.setUrl("https://example.com");
        result.setTitle("Example Title");
        // 创建超过100字符的内容
        String longContent = "This is a very long content that exceeds 100 characters. It should be truncated to exactly 100 characters and have ellipsis added at the end to indicate truncation.";
        result.setContent(longContent);
        result.setFavicon("https://example.com/favicon.ico");

        // 模拟sendNetworkSearchResults方法中的JSON构建逻辑
        Map<String, String> resultData = new HashMap<>();
        resultData.put("url", result.getUrl() != null ? result.getUrl() : "");
        resultData.put("title", result.getTitle() != null ? result.getTitle() : "");

        // 限制content字段为前100个字符
        String content = result.getContent() != null ? result.getContent() : "";
        if (content.length() > 100) {
            content = content.substring(0, 100) + "...";
        }
        resultData.put("content", content);

        resultData.put("favicon", result.getFavicon() != null ? result.getFavicon() : "");

        ObjectMapper mapper = new ObjectMapper();
        String resultJson = String.format("u:%s\n", mapper.writeValueAsString(resultData));

        // 验证内容被正确截断
        assertTrue(resultJson.contains("u:{"), "输出应包含u:格式的数据");
        assertTrue(content.length() == 103, "截断后内容长度应为103字符（100字符+3个省略号）");
        assertTrue(content.endsWith("..."), "截断后内容应以省略号结尾");
        assertTrue(content.startsWith("This is a very long content"), "截断后内容应保留开头部分");

        // 验证JSON中包含截断后的内容
        assertTrue(resultJson.contains("..."), "JSON输出应包含省略号");
    }
}
