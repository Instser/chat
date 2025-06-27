package com.instser.chat.parser;

import com.instser.chat.parser.impl.ImageDocumentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片格式处理测试
 * 验证AI返回内容的格式转换功能
 *
 * @author instser
 * @since 2025-06-27
 */
public class ImageFormatTest {

    private ImageDocumentParser parser;

    @BeforeEach
    public void setUp() {
        parser = new ImageDocumentParser();
    }

    @Test
    public void testMarkdownJsonFormatRemoval() {
        // 测试移除markdown格式的JSON
        String markdownInput = "```json\n{\n  \"plan\": {\n    \"name\": \"Free\",\n    \"price\": \"USD 0\"\n  }\n}\n```";

        String result = parser.processAiContent(markdownInput);

        // 验证基本格式要求
        assertTrue(result.startsWith("Here's what the text in the image says."));
        assertFalse(result.contains("```"));
        assertFalse(result.contains("json"));
        assertTrue(result.contains("\"plan\""));
        assertTrue(result.contains("\"name\": \"Free\""));
        assertTrue(result.contains("\"price\": \"USD 0\""));

        System.out.println("✅ Markdown格式移除测试通过");
        System.out.println("输入: " + markdownInput.replace("\n", "\\n"));
        System.out.println("输出: " + result.replace("\n", "\\n"));
    }

    @Test
    public void testPureJsonFormat() {
        // 测试纯JSON格式的处理
        String jsonInput = "{\n  \"plan\": {\n    \"name\": \"Free\",\n    \"price\": \"USD 0\",\n    \"features\": [\n      \"Chat on web, iOS, and Android\",\n      \"Generate code and visualize data\"\n    ]\n  }\n}";
        String expected = "Here's what the text in the image says.\n" + jsonInput;
        
        String result = parser.processAiContent(jsonInput);
        
        assertEquals(expected, result);
        assertTrue(result.startsWith("Here's what the text in the image says."));
        assertTrue(result.contains("\"plan\""));
        assertTrue(result.contains("\"features\""));
        
        System.out.println("✅ 纯JSON格式处理测试通过");
        System.out.println("输入: " + jsonInput.replace("\n", "\\n"));
        System.out.println("输出: " + result.replace("\n", "\\n"));
    }

    @Test
    public void testAlreadyFormattedContent() {
        // 测试已经包含说明文字的内容
        String formattedInput = "Here's what the text in the image says.\n{\n  \"plan\": {\n    \"name\": \"Free\"\n  }\n}";
        
        String result = parser.processAiContent(formattedInput);
        
        assertEquals(formattedInput, result);
        assertTrue(result.startsWith("Here's what the text in the image says."));
        
        System.out.println("✅ 已格式化内容测试通过");
        System.out.println("输入输出一致: " + result.replace("\n", "\\n"));
    }

    @Test
    public void testComplexJsonWithMarkdown() {
        // 测试复杂的JSON结构与markdown格式
        String complexInput = "```json\n{\n  \"plan\": {\n    \"name\": \"Free\",\n    \"price\": \"USD 0\",\n    \"features\": [\n      \"Chat on web, iOS, and Android\",\n      \"Generate code and visualize data\",\n      \"Write, edit, and create content\",\n      \"Analyze text and images\",\n      \"Ability to search the web\"\n    ],\n    \"call_to_action\": \"Stay on Free plan\"\n  }\n}\n```";
        
        String result = parser.processAiContent(complexInput);
        
        assertTrue(result.startsWith("Here's what the text in the image says."));
        assertFalse(result.contains("```"));
        assertFalse(result.contains("json"));
        assertTrue(result.contains("\"call_to_action\": \"Stay on Free plan\""));
        assertTrue(result.contains("\"features\""));
        
        System.out.println("✅ 复杂JSON结构测试通过");
        System.out.println("结果长度: " + result.length() + " 字符");
        System.out.println("包含features数组: " + result.contains("\"features\""));
        System.out.println("包含call_to_action: " + result.contains("\"call_to_action\""));
    }

    @Test
    public void testNonJsonContent() {
        // 测试非JSON内容的处理
        String textInput = "This image shows a pricing plan for a free service.";
        
        String result = parser.processAiContent(textInput);
        
        assertTrue(result.startsWith("Here's what the text in the image says."));
        assertTrue(result.contains("\"description\""));
        assertTrue(result.contains("This image shows a pricing plan for a free service."));
        
        System.out.println("✅ 非JSON内容处理测试通过");
        System.out.println("输入: " + textInput);
        System.out.println("输出: " + result);
    }

    @Test
    public void testEmptyContent() {
        // 测试空内容的处理
        String emptyInput = "";
        
        String result = parser.processAiContent(emptyInput);
        
        assertEquals("Here's what the text in the image says.\n{}", result);
        
        System.out.println("✅ 空内容处理测试通过");
        System.out.println("输出: " + result);
    }

    @Test
    public void testMixedContentWithJson() {
        // 测试包含文本和JSON的混合内容
        String mixedInput = "This image contains a pricing plan. Here's the structure:\n```json\n{\n  \"plan\": {\n    \"name\": \"Free\"\n  }\n}\n```\nThe plan offers basic features.";
        
        String result = parser.processAiContent(mixedInput);
        
        assertTrue(result.startsWith("Here's what the text in the image says."));
        assertFalse(result.contains("```"));
        assertTrue(result.contains("\"plan\""));
        assertTrue(result.contains("\"name\": \"Free\""));
        
        System.out.println("✅ 混合内容处理测试通过");
        System.out.println("结果: " + result.replace("\n", "\\n"));
    }

    @Test
    public void testYourSpecificExample() {
        // 测试您提供的具体示例
        String input = "```json\n{\n  \"plan\": {\n    \"name\": \"Free\",\n    \"price\": \"USD 0\",\n    \"features\": [\n      \"Chat on web, iOS, and Android\",\n      \"Generate code and visualize data\",\n      \"Write, edit, and create content\",\n      \"Analyze text and images\",\n      \"Ability to search the web\"\n    ],\n    \"call_to_action\": \"Stay on Free plan\"\n  }\n}\n```";

        String result = parser.processAiContent(input);

        // 验证格式要求
        assertTrue(result.startsWith("Here's what the text in the image says."));
        assertFalse(result.contains("```"));
        assertFalse(result.contains("json"));
        assertTrue(result.contains("\"plan\""));
        assertTrue(result.contains("\"features\""));
        assertTrue(result.contains("\"call_to_action\": \"Stay on Free plan\""));
        assertTrue(result.contains("Chat on web, iOS, and Android"));

        System.out.println("✅ 您的具体示例测试通过");
        System.out.println("✨ 格式转换成功！");
        System.out.println("📋 输出格式:");
        System.out.println(result);
    }
}
