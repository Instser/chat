package com.instser.chat.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 魔数检测工具类测试
 *
 * @author instser
 * @since 2025-06-27
 */
public class MagicNumberDetectorTest {

    @Test
    public void testDetectTextFile() {
        // 测试文本文件
        String textContent = "这是一个测试文本文件\nHello World\n测试中文内容";
        byte[] textBytes = textContent.getBytes();
        
        String detectedType = MagicNumberDetector.detectFileType(textBytes);
        assertEquals("text/plain", detectedType);
    }

    @Test
    public void testDetectPNGFile() {
        // PNG文件魔数: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngBytes = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            // 添加一些模拟的PNG数据
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        
        String detectedType = MagicNumberDetector.detectFileType(pngBytes);
        assertEquals("image/png", detectedType);
    }

    @Test
    public void testDetectJPEGFile() {
        // JPEG文件魔数: FF D8 FF
        byte[] jpegBytes = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            // 添加一些模拟的JPEG数据
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
        };
        
        String detectedType = MagicNumberDetector.detectFileType(jpegBytes);
        assertEquals("image/jpeg", detectedType);
    }

    @Test
    public void testDetectPDFFile() {
        // PDF文件魔数: 25 50 44 46
        byte[] pdfBytes = {
            0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34,
            // 添加一些模拟的PDF数据
            0x0A, 0x25, (byte) 0xE2, (byte) 0xE3
        };
        
        String detectedType = MagicNumberDetector.detectFileType(pdfBytes);
        assertEquals("application/pdf", detectedType);
    }

    @Test
    public void testDetectMultipartFile() {
        // 测试MultipartFile检测
        String content = "测试文件内容\nTest file content";
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.txt", 
            "text/plain", 
            content.getBytes()
        );
        
        String detectedType = MagicNumberDetector.detectFileType(file);
        assertEquals("text/plain", detectedType);
    }

    @Test
    public void testGuessTypeFromFilename() {
        // 测试文件名类型猜测
        assertEquals("text/plain", MagicNumberDetector.guessTypeFromFilename("test.txt"));
        assertEquals("application/json", MagicNumberDetector.guessTypeFromFilename("data.json"));
        assertEquals("image/png", MagicNumberDetector.guessTypeFromFilename("image.png"));
        assertEquals("application/pdf", MagicNumberDetector.guessTypeFromFilename("document.pdf"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
                     MagicNumberDetector.guessTypeFromFilename("document.docx"));
        
        // 测试无扩展名文件
        assertEquals("application/octet-stream", MagicNumberDetector.guessTypeFromFilename("noextension"));
        assertEquals("application/octet-stream", MagicNumberDetector.guessTypeFromFilename(null));
    }

    @Test
    public void testDetectEmptyFile() {
        // 测试空文件
        byte[] emptyBytes = new byte[0];
        String detectedType = MagicNumberDetector.detectFileType(emptyBytes);
        assertEquals("application/octet-stream", detectedType);
    }

    @Test
    public void testDetectBinaryFile() {
        // 测试二进制文件（大量控制字符）
        byte[] binaryBytes = new byte[100];
        for (int i = 0; i < 100; i++) {
            binaryBytes[i] = (byte) (i % 256);
        }
        
        String detectedType = MagicNumberDetector.detectFileType(binaryBytes);
        assertEquals("application/octet-stream", detectedType);
    }

    @Test
    public void testDetectGIFFile() {
        // GIF文件魔数: 47 49 46 38 37/39 61
        byte[] gifBytes = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
            // 添加一些模拟的GIF数据
            0x00, 0x00, 0x00, 0x00
        };
        
        String detectedType = MagicNumberDetector.detectFileType(gifBytes);
        assertEquals("image/gif", detectedType);
    }

    @Test
    public void testDetectWebPFile() {
        // WebP文件魔数: 52 49 46 46 ... 57 45 42 50
        byte[] webpBytes = {
            0x52, 0x49, 0x46, 0x46,  // RIFF
            0x00, 0x00, 0x00, 0x00,  // 文件大小占位符
            0x57, 0x45, 0x42, 0x50   // WEBP
        };
        
        String detectedType = MagicNumberDetector.detectFileType(webpBytes);
        assertEquals("image/webp", detectedType);
    }
}
