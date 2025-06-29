package com.instser.chat.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压缩文件检测测试
 *
 * @author instser
 * @since 2025-06-29
 */
class CompressedFileDetectionTest {

    @Test
    void testCompressedFileDetection() {
        // 测试各种压缩文件类型的MIME类型检测
        assertTrue(MagicNumberDetector.isCompressedFile("application/zip"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/x-rar-compressed"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/x-7z-compressed"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/x-tar"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/gzip"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/x-bzip2"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/x-xz"));
        assertTrue(MagicNumberDetector.isCompressedFile("application/x-lzma"));
        
        // 测试非压缩文件类型
        assertFalse(MagicNumberDetector.isCompressedFile("application/pdf"));
        assertFalse(MagicNumberDetector.isCompressedFile("text/plain"));
        assertFalse(MagicNumberDetector.isCompressedFile("image/jpeg"));
        assertFalse(MagicNumberDetector.isCompressedFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertFalse(MagicNumberDetector.isCompressedFile("application/msword"));
        
        // 测试null和空字符串
        assertFalse(MagicNumberDetector.isCompressedFile(null));
        assertFalse(MagicNumberDetector.isCompressedFile(""));
        assertFalse(MagicNumberDetector.isCompressedFile("   "));
    }

    @Test
    void testFileExtensionGuessing() {
        // 测试压缩文件扩展名识别
        assertEquals("application/zip", MagicNumberDetector.guessTypeFromFilename("test.zip"));
        assertEquals("application/x-rar-compressed", MagicNumberDetector.guessTypeFromFilename("test.rar"));
        assertEquals("application/x-7z-compressed", MagicNumberDetector.guessTypeFromFilename("test.7z"));
        assertEquals("application/x-tar", MagicNumberDetector.guessTypeFromFilename("test.tar"));
        assertEquals("application/gzip", MagicNumberDetector.guessTypeFromFilename("test.gz"));
        assertEquals("application/x-bzip2", MagicNumberDetector.guessTypeFromFilename("test.bz2"));
        assertEquals("application/x-xz", MagicNumberDetector.guessTypeFromFilename("test.xz"));
        assertEquals("application/x-lzma", MagicNumberDetector.guessTypeFromFilename("test.lzma"));
        
        // 测试Office文档扩展名识别（应该不被识别为压缩文件）
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
                    MagicNumberDetector.guessTypeFromFilename("test.docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                    MagicNumberDetector.guessTypeFromFilename("test.xlsx"));
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", 
                    MagicNumberDetector.guessTypeFromFilename("test.pptx"));
    }

    @Test
    void testOfficeDocumentDetection() {
        // 测试Office文档类型识别
        assertTrue(MagicNumberDetector.isOfficeDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(MagicNumberDetector.isOfficeDocument("application/msword"));
        assertTrue(MagicNumberDetector.isOfficeDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(MagicNumberDetector.isOfficeDocument("application/vnd.ms-excel"));
        assertTrue(MagicNumberDetector.isOfficeDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertTrue(MagicNumberDetector.isOfficeDocument("application/vnd.ms-powerpoint"));
        
        // 测试非Office文档类型
        assertFalse(MagicNumberDetector.isOfficeDocument("application/zip"));
        assertFalse(MagicNumberDetector.isOfficeDocument("application/pdf"));
        assertFalse(MagicNumberDetector.isOfficeDocument("text/plain"));
        assertFalse(MagicNumberDetector.isOfficeDocument(null));
    }

    @Test
    void testZipBasedOfficeDocumentDetection() {
        // 测试基于ZIP的Office文档检测
        // 模拟.docx文件（实际上是ZIP格式）
        MockMultipartFile docxFile = new MockMultipartFile(
            "file", 
            "test.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createZipMagicBytes()
        );
        
        String detectedType = MagicNumberDetector.detectFileType(docxFile);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", detectedType);
        
        // 测试普通ZIP文件
        MockMultipartFile zipFile = new MockMultipartFile(
            "file", 
            "test.zip", 
            "application/zip",
            createZipMagicBytes()
        );
        
        detectedType = MagicNumberDetector.detectFileType(zipFile);
        assertEquals("application/zip", detectedType);
    }

    @Test
    void testCompressedFileMagicNumbers() {
        // 测试RAR文件魔数
        byte[] rarBytes = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00};
        assertEquals("application/x-rar-compressed", MagicNumberDetector.detectFileType(rarBytes));
        
        // 测试7Z文件魔数
        byte[] sevenZBytes = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
        assertEquals("application/x-7z-compressed", MagicNumberDetector.detectFileType(sevenZBytes));
        
        // 测试GZIP文件魔数
        byte[] gzipBytes = {0x1F, (byte) 0x8B};
        assertEquals("application/gzip", MagicNumberDetector.detectFileType(gzipBytes));
        
        // 测试BZIP2文件魔数
        byte[] bzip2Bytes = {0x42, 0x5A, 0x68};
        assertEquals("application/x-bzip2", MagicNumberDetector.detectFileType(bzip2Bytes));
    }

    /**
     * 创建ZIP文件的魔数字节
     */
    private byte[] createZipMagicBytes() {
        return new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
    }
}
