package com.instser.chat.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatApiController 智能分块功能测试
 * 验证智能分块策略的正确性
 */
@SpringBootTest
@ActiveProfiles("test")
public class ChatApiControllerSmartChunkingTest {

    /**
     * 测试短内容的分块策略
     */
    @Test
    public void testShortContentChunking() {
        String shortContent = "Hello World!"; // 12字符
        int chunkSize = getOptimalChunkSize(shortContent);
        
        // 短内容应该直接发送全部内容
        assertEquals(shortContent.length(), chunkSize, "短内容应该直接发送全部内容");
        assertTrue(chunkSize <= 20, "短内容分块大小应该不超过20");
    }

    /**
     * 测试中等内容的分块策略
     */
    @Test
    public void testMediumContentChunking() {
        String mediumContent = "这是一个中等长度的测试内容，用于验证智能分块策略的正确性。"; // 约30字符
        int chunkSize = getOptimalChunkSize(mediumContent);
        
        // 中等内容应该使用小分块
        assertTrue(chunkSize >= 5, "中等内容分块大小应该至少为5");
        assertTrue(chunkSize <= 10, "中等内容分块大小应该不超过10");
    }

    /**
     * 测试较长内容的分块策略
     */
    @Test
    public void testLongContentChunking() {
        String longContent = "这是一个较长的测试内容，用于验证智能分块策略在处理较长文本时的表现。" +
                           "它应该使用中等大小的分块来平衡响应速度和流畅度。" +
                           "这样可以确保用户体验的最优化。"; // 约150字符
        int chunkSize = getOptimalChunkSize(longContent);
        
        // 较长内容应该使用中等分块
        assertTrue(chunkSize >= 10, "较长内容分块大小应该至少为10");
        assertTrue(chunkSize <= 15, "较长内容分块大小应该不超过15");
    }

    /**
     * 测试超长内容的分块策略
     */
    @Test
    public void testVeryLongContentChunking() {
        StringBuilder veryLongContent = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            veryLongContent.append("这是一个非常长的测试内容，用于验证智能分块策略在处理超长文本时的表现。");
        }
        String content = veryLongContent.toString(); // 约1500字符
        int chunkSize = getOptimalChunkSize(content);
        
        // 超长内容应该使用大分块
        assertTrue(chunkSize >= 15, "超长内容分块大小应该至少为15");
        assertTrue(chunkSize <= 25, "超长内容分块大小应该不超过25");
    }

    /**
     * 测试边界值情况
     */
    @Test
    public void testBoundaryValues() {
        // 测试20字符边界
        String boundary20 = "12345678901234567890"; // 正好20字符
        assertEquals(20, getOptimalChunkSize(boundary20), "20字符内容应该直接发送");
        
        // 测试21字符
        String boundary21 = "123456789012345678901"; // 21字符
        int chunk21 = getOptimalChunkSize(boundary21);
        assertTrue(chunk21 >= 5 && chunk21 <= 10, "21字符内容应该使用小分块");
        
        // 测试100字符边界
        StringBuilder boundary100 = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            boundary100.append("a");
        }
        int chunk100 = getOptimalChunkSize(boundary100.toString());
        assertTrue(chunk100 >= 5 && chunk100 <= 10, "100字符内容应该使用小分块");
        
        // 测试101字符
        StringBuilder boundary101 = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            boundary101.append("a");
        }
        int chunk101 = getOptimalChunkSize(boundary101.toString());
        assertTrue(chunk101 >= 10 && chunk101 <= 15, "101字符内容应该使用中等分块");
    }

    /**
     * 测试空内容和极短内容
     */
    @Test
    public void testEmptyAndVeryShortContent() {
        // 空内容
        assertEquals(0, getOptimalChunkSize(""), "空内容分块大小应该为0");
        
        // 单字符
        assertEquals(1, getOptimalChunkSize("a"), "单字符内容分块大小应该为1");
        
        // 两字符
        assertEquals(2, getOptimalChunkSize("ab"), "两字符内容分块大小应该为2");
    }

    /**
     * 复制ChatApiController中的getOptimalChunkSize方法用于测试
     */
    private int getOptimalChunkSize(String content) {
        int length = content.length();
        int MIN_CHUNK_SIZE = 5;
        int MAX_CHUNK_SIZE = 25;
        
        if (length <= 20) {
            // 短内容：直接发送全部内容
            return length;
        } else if (length <= 100) {
            // 中等内容：使用小分块
            return Math.max(MIN_CHUNK_SIZE, Math.min(length / 4, 10));
        } else if (length <= 300) {
            // 较长内容：使用中等分块
            return Math.max(10, Math.min(length / 8, 15));
        } else {
            // 长内容：使用大分块
            return Math.max(15, Math.min(MAX_CHUNK_SIZE, length / 12));
        }
    }
}
