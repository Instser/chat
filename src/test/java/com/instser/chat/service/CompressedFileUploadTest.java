package com.instser.chat.service;

import com.instser.chat.common.exception.FileUploadException;
import com.instser.chat.parser.DocumentParserFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压缩文件上传测试
 * 验证系统正确拒绝压缩文件上传
 *
 * @author instser
 * @since 2025-06-29
 */
class CompressedFileUploadTest {

    @Test
    void testCompressedFileRejection() {
        // 测试ZIP文件被拒绝
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/zip");
        });

        // 测试RAR文件被拒绝
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/x-rar-compressed");
        });

        // 测试7Z文件被拒绝
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/x-7z-compressed");
        });

        // 测试TAR文件被拒绝
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/x-tar");
        });

        // 测试GZIP文件被拒绝
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/gzip");
        });

        // 测试BZIP2文件被拒绝
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/x-bzip2");
        });
    }

    @Test
    void testOfficeDocumentAcceptance() {
        // 测试Office文档被接受
        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        });

        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/msword");
        });

        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        });

        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/vnd.ms-excel");
        });

        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        });

        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/vnd.ms-powerpoint");
        });

        assertDoesNotThrow(() -> {
            DocumentParserFactory.getParser("application/pdf");
        });
    }

    @Test
    void testIsDocumentFileWithCompressedFiles() {
        // 测试压缩文件不被认为是支持的文档类型
        assertFalse(DocumentParserFactory.isDocumentFile("application/zip"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/x-rar-compressed"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/x-7z-compressed"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/x-tar"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/gzip"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/x-bzip2"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/x-xz"));
        assertFalse(DocumentParserFactory.isDocumentFile("application/x-lzma"));
    }

    @Test
    void testIsDocumentFileWithOfficeDocuments() {
        // 测试Office文档被认为是支持的文档类型
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/msword"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.ms-excel"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/vnd.ms-powerpoint"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/pdf"));
        
        // 测试其他支持的文档类型
        assertTrue(DocumentParserFactory.isDocumentFile("text/plain"));
        assertTrue(DocumentParserFactory.isDocumentFile("image/jpeg"));
        assertTrue(DocumentParserFactory.isDocumentFile("image/png"));
        assertTrue(DocumentParserFactory.isDocumentFile("application/json"));
    }

    @Test
    void testCompressedFileErrorMessage() {
        // 测试压缩文件的错误消息
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            DocumentParserFactory.getParser("application/zip");
        });
        
        assertTrue(exception.getMessage().contains("不支持压缩文件类型"));
        assertTrue(exception.getMessage().contains("application/zip"));
    }

    @Test
    void testMixedFileTypes() {
        // 测试混合文件类型的处理
        
        // 支持的文件类型
        String[] supportedTypes = {
            "application/pdf",
            "text/plain",
            "image/jpeg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword"
        };
        
        for (String type : supportedTypes) {
            assertTrue(DocumentParserFactory.isDocumentFile(type), 
                      "应该支持文件类型: " + type);
        }
        
        // 不支持的压缩文件类型
        String[] unsupportedTypes = {
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/gzip"
        };
        
        for (String type : unsupportedTypes) {
            assertFalse(DocumentParserFactory.isDocumentFile(type), 
                       "不应该支持压缩文件类型: " + type);
        }
    }
}
