package com.instser.chat.parser;

import com.instser.chat.parser.impl.ImageDocumentParser;
import com.instser.chat.util.Base64Util;
import com.instser.chat.util.MagicNumberDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片文档解析器测试
 *
 * @author instser
 * @since 2025-06-27
 */
public class ImageDocumentParserTest {

    private ImageDocumentParser parser;

    @BeforeEach
    public void setUp() {
        parser = new ImageDocumentParser();
    }

    @Test
    public void testGetSupportedTypes() {
        String[] supportedTypes = parser.getSupportedTypes();
        
        assertNotNull(supportedTypes);
        assertTrue(supportedTypes.length > 0);
        
        // 验证包含常见图片格式
        boolean containsPng = false;
        boolean containsJpeg = false;
        for (String type : supportedTypes) {
            if ("image/png".equals(type)) containsPng = true;
            if ("image/jpeg".equals(type)) containsJpeg = true;
        }
        
        assertTrue(containsPng, "应该支持PNG格式");
        assertTrue(containsJpeg, "应该支持JPEG格式");
    }

    @Test
    public void testParseDocumentWithEmptyBytes() {
        // 测试空字节数组
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parseDocument(new byte[0], "test.png", "image/png");
        });
        
        assertTrue(exception.getMessage().contains("图片文件内容不能为空"));
    }

    @Test
    public void testParseDocumentWithNullBytes() {
        // 测试null字节数组
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parseDocument(null, "test.png", "image/png");
        });
        
        assertTrue(exception.getMessage().contains("图片文件内容不能为空"));
    }

    @Test
    public void testCreateMockPngImage() {
        // 创建一个模拟的PNG图片字节数组
        byte[] mockPngBytes = createMockPngBytes();
        
        assertNotNull(mockPngBytes);
        assertTrue(mockPngBytes.length > 0);
        
        // 验证魔数检测能识别为PNG
        String detectedType = MagicNumberDetector.detectFileType(mockPngBytes);
        assertEquals("image/png", detectedType);
    }

    @Test
    public void testBase64Encoding() {
        // 测试Base64编码功能
        byte[] testBytes = "Hello World".getBytes();
        String encoded = Base64Util.encodeToString(testBytes);
        
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());
        
        // 测试解码
        byte[] decoded = Base64Util.decodeFromString(encoded);
        assertArrayEquals(testBytes, decoded);
    }

    @Test
    public void testDataUrlCreation() {
        // 测试data URL创建
        byte[] testBytes = createMockPngBytes();
        String dataUrl = Base64Util.encodeImageToDataUrl(testBytes, "image/png");
        
        assertNotNull(dataUrl);
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        
        // 测试提取Base64数据
        String base64Data = Base64Util.extractBase64FromDataUrl(dataUrl);
        assertNotNull(base64Data);
        assertFalse(base64Data.isEmpty());
        
        // 验证提取的数据可以解码
        byte[] decoded = Base64Util.decodeFromString(base64Data);
        assertArrayEquals(testBytes, decoded);
    }

    @Test
    public void testDocumentParserFactory() {
        // 测试工厂模式能正确返回图片解析器
        DocumentParser pngParser = DocumentParserFactory.getParser("image/png");
        assertNotNull(pngParser);
        assertTrue(pngParser instanceof ImageDocumentParser);
        
        DocumentParser jpegParser = DocumentParserFactory.getParser("image/jpeg");
        assertNotNull(jpegParser);
        assertTrue(jpegParser instanceof ImageDocumentParser);
        
        // 测试isDocumentFile方法
        assertTrue(DocumentParserFactory.isDocumentFile("image/png"));
        assertTrue(DocumentParserFactory.isDocumentFile("image/jpeg"));
        assertTrue(DocumentParserFactory.isDocumentFile("image/gif"));
    }

    /**
     * 创建模拟的PNG图片字节数组
     * PNG魔数: 89 50 4E 47 0D 0A 1A 0A
     */
    private byte[] createMockPngBytes() {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG魔数
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,          // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,          // 1x1像素
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, (byte) 0x77, (byte) 0x53, (byte) 0xDE, // IHDR数据
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,          // IDAT chunk
            0x08, (byte) 0x99, 0x01, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, // IDAT数据
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,          // IEND chunk
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82                     // IEND CRC
        };
    }

    /**
     * 注意：实际的AI调用测试需要真实的网络环境和API密钥
     * 这里只测试基础功能，不进行实际的AI调用
     */
    @Test
    public void testImageParserIntegration() {
        // 这个测试验证整个流程的集成，但不实际调用AI
        byte[] mockImage = createMockPngBytes();

        // 验证文件类型检测
        String detectedType = MagicNumberDetector.detectFileType(mockImage);
        assertEquals("image/png", detectedType);

        // 验证工厂能返回正确的解析器
        DocumentParser parser = DocumentParserFactory.getParser(detectedType);
        assertTrue(parser instanceof ImageDocumentParser);

        // 验证Base64编码
        String base64 = Base64Util.encodeToString(mockImage);
        assertNotNull(base64);
        assertFalse(base64.isEmpty());

        // 验证data URL创建
        String dataUrl = Base64Util.encodeImageToDataUrl(mockImage, detectedType);
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));

        System.out.println("✅ 图片解析器集成测试通过");
        System.out.println("📸 检测到的文件类型: " + detectedType);
        System.out.println("🔧 使用的解析器: " + parser.getClass().getSimpleName());
        System.out.println("📊 Base64编码长度: " + base64.length() + " 字符");
        System.out.println("🔗 Data URL长度: " + dataUrl.length() + " 字符");
    }

    @Test
    public void testContentFormatProcessing() {
        ImageDocumentParser parser = new ImageDocumentParser();

        // 测试处理markdown格式的JSON
        String markdownJson = "```json\n{\"test\": \"value\"}\n```";
        String processed = parser.processAiContent(markdownJson);
        assertTrue(processed.startsWith("Here's what the text in the image says."));
        assertTrue(processed.contains("{\"test\": \"value\"}"));
        assertFalse(processed.contains("```"));

        // 测试处理纯JSON格式
        String pureJson = "{\"plan\": {\"name\": \"Free\"}}";
        String processedPure = parser.processAiContent(pureJson);
        assertTrue(processedPure.startsWith("Here's what the text in the image says."));
        assertTrue(processedPure.contains("{\"plan\": {\"name\": \"Free\"}}"));

        // 测试处理已经包含说明文字的内容
        String withDescription = "Here's what the text in the image says.\n{\"existing\": \"content\"}";
        String processedExisting = parser.processAiContent(withDescription);
        assertEquals(withDescription, processedExisting);

        System.out.println("✅ 内容格式处理测试通过");
        System.out.println("📝 Markdown JSON处理: " + processed.substring(0, Math.min(50, processed.length())) + "...");
        System.out.println("📝 纯JSON处理: " + processedPure.substring(0, Math.min(50, processedPure.length())) + "...");
    }
}
