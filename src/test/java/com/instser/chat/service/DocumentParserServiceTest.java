package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.DocumentParserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档解析服务测试
 *
 * @author instser
 * @since 2025-06-27
 */
public class DocumentParserServiceTest {

    private DocumentParserService documentParserService;

    @BeforeEach
    public void setUp() {
        documentParserService = new DocumentParserServiceImpl();
    }

    @Test
    public void testIsDocumentFile() {
        // 测试支持的Office文档类型
        assertTrue(documentParserService.isDocumentFile("application/pdf"));
        assertTrue(documentParserService.isDocumentFile("application/msword"));
        assertTrue(documentParserService.isDocumentFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(documentParserService.isDocumentFile("application/vnd.ms-excel"));
        assertTrue(documentParserService.isDocumentFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(documentParserService.isDocumentFile("application/vnd.ms-powerpoint"));
        assertTrue(documentParserService.isDocumentFile("application/vnd.openxmlformats-officedocument.presentationml.presentation"));

        // 测试支持的文本文件类型
        assertTrue(documentParserService.isDocumentFile("text/plain"));
        assertTrue(documentParserService.isDocumentFile("text/html"));
        assertTrue(documentParserService.isDocumentFile("text/css"));
        assertTrue(documentParserService.isDocumentFile("application/json"));
        assertTrue(documentParserService.isDocumentFile("application/xml"));
        assertTrue(documentParserService.isDocumentFile("application/javascript"));

        // 测试支持的图片文件类型
        assertTrue(documentParserService.isDocumentFile("image/jpeg"));
        assertTrue(documentParserService.isDocumentFile("image/png"));
        assertTrue(documentParserService.isDocumentFile("image/gif"));

        // 测试不支持的文件类型
        assertFalse(documentParserService.isDocumentFile("audio/mp3"));
        assertFalse(documentParserService.isDocumentFile("video/mp4"));
        assertFalse(documentParserService.isDocumentFile(null));
        assertFalse(documentParserService.isDocumentFile(""));
    }

    @Test
    public void testParseDocumentWithInvalidInput() {
        // 测试空文件内容
        assertThrows(RuntimeException.class, () -> {
            documentParserService.parseDocument(null, "test.pdf", "application/pdf");
        });

        assertThrows(RuntimeException.class, () -> {
            documentParserService.parseDocument(new byte[0], "test.pdf", "application/pdf");
        });

        // 测试不支持的文件类型
        assertThrows(RuntimeException.class, () -> {
            documentParserService.parseDocument("test content".getBytes(), "test.txt", "text/plain");
        });
    }

    @Test
    public void testParseDocumentWithInvalidFileContent() {
        // 测试无效的PDF内容
        byte[] invalidPdfContent = "This is not a valid PDF file".getBytes();
        assertThrows(RuntimeException.class, () -> {
            documentParserService.parseDocument(invalidPdfContent, "test.pdf", "application/pdf");
        });

        // 测试无效的Word内容
        byte[] invalidWordContent = "This is not a valid Word file".getBytes();
        assertThrows(RuntimeException.class, () -> {
            documentParserService.parseDocument(invalidWordContent, "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        });
    }

    @Test
    public void testParseTextFile() {
        // 测试解析文本文件
        String textContent = "Hello, World!\nThis is a test text file.\n测试中文内容。";
        byte[] textBytes = textContent.getBytes();

        String result = documentParserService.parseDocument(textBytes, "test.txt", "text/plain");

        assertNotNull(result);
        assertTrue(result.contains("文档类型: 文本文件"));
        assertTrue(result.contains("test.txt"));
        assertTrue(result.contains("Hello, World!"));
        assertTrue(result.contains("测试中文内容"));
    }

    @Test
    public void testParseJsonFile() {
        // 测试解析JSON文件
        String jsonContent = "{\"name\": \"test\", \"value\": 123, \"description\": \"测试JSON文件\"}";
        byte[] jsonBytes = jsonContent.getBytes();

        String result = documentParserService.parseDocument(jsonBytes, "test.json", "application/json");

        assertNotNull(result);
        assertTrue(result.contains("文档类型: 文本文件"));
        assertTrue(result.contains("test.json"));
        assertTrue(result.contains("\"name\": \"test\""));
        assertTrue(result.contains("测试JSON文件"));
    }
}
