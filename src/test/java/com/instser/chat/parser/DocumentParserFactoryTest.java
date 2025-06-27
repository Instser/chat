package com.instser.chat.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档解析器工厂测试
 *
 * @author instser
 * @since 2025-06-27
 */
public class DocumentParserFactoryTest {

    @Test
    public void testIsDocumentFile() {
        // 测试支持的Office文档类型
        assertTrue(DocumentParserFactory.isDocumentFile("application/pdf"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/msword"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.ms-excel"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.ms-powerpoint"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.openxmlformats-officedocument.presentationml.presentation"));

        // 测试支持的文本文件类型
        assertTrue(DocumentParserFactory.isDocumentFile("text/plain"));
        assertTrue(DocumentParserFactory.isDocumentFile("text/html"));
        assertTrue(DocumentParserFactory.isDocumentFile("text/css"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/json"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/xml"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/javascript"));

        // 测试不支持的文件类型
        assertFalse(DocumentParserFactory.isDocumentFile("image/jpeg"));
        assertFalse(DocumentParserFactory.isDocumentFile("audio/mp3"));
        assertFalse(DocumentParserFactory.isDocumentFile("video/mp4"));
        assertFalse(DocumentParserFactory.isDocumentFile(null));
        assertFalse(DocumentParserFactory.isDocumentFile(""));
    }

    @Test
    public void testGetParser() {
        // 测试获取文本解析器
        assertNotNull(DocumentParserFactory.getParser("text/plain"));
        assertNotNull(DocumentParserFactory.getParser("application/json"));

        // 测试获取Office文档解析器
        assertNotNull(DocumentParserFactory.getParser("application/msword"));
        assertNotNull(DocumentParserFactory.getParser("application/vnd.ms-excel"));
        assertNotNull(DocumentParserFactory.getParser("application/vnd.ms-powerpoint"));

        // 测试获取PDF解析器
        assertNotNull(DocumentParserFactory.getParser("application/pdf"));

        // 测试不支持的类型
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("image/jpeg");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser(null);
        });
    }
}
