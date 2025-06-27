package com.instser.chat.parser;

import com.instser.chat.parser.impl.ImageDocumentParser;
import org.junit.jupiter.api.Test;

/**
 * 简单的格式测试
 * 用于调试格式转换问题
 *
 * @author instser
 * @since 2025-06-27
 */
public class SimpleFormatTest {

    @Test
    public void testSimpleFormat() {
        ImageDocumentParser parser = new ImageDocumentParser();
        
        String input = "```json\n{\"test\": \"value\"}\n```";
        String result = parser.processAiContent(input);
        
        System.out.println("=== 输入 ===");
        System.out.println("'" + input + "'");
        System.out.println("输入长度: " + input.length());
        
        System.out.println("\n=== 输出 ===");
        System.out.println("'" + result + "'");
        System.out.println("输出长度: " + result.length());
        
        System.out.println("\n=== 字符分析 ===");
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c == '\n') {
                System.out.println("位置 " + i + ": \\n");
            } else if (c == '\r') {
                System.out.println("位置 " + i + ": \\r");
            } else if (c == ' ') {
                System.out.println("位置 " + i + ": 空格");
            } else if (c == '\t') {
                System.out.println("位置 " + i + ": \\t");
            }
        }
        
        System.out.println("\n=== 期望格式 ===");
        String expected = "Here's what the text in the image says.\n{\"test\": \"value\"}";
        System.out.println("'" + expected + "'");
        System.out.println("期望长度: " + expected.length());
        
        System.out.println("\n=== 比较结果 ===");
        System.out.println("是否相等: " + result.equals(expected));
        System.out.println("是否以正确文字开头: " + result.startsWith("Here's what the text in the image says."));
        System.out.println("是否包含JSON: " + result.contains("{\"test\": \"value\"}"));
        System.out.println("是否包含markdown: " + result.contains("```"));
    }

    @Test
    public void testComplexFormat() {
        ImageDocumentParser parser = new ImageDocumentParser();
        
        String input = "```json\n{\n  \"plan\": {\n    \"name\": \"Free\"\n  }\n}\n```";
        String result = parser.processAiContent(input);
        
        System.out.println("=== 复杂格式测试 ===");
        System.out.println("输入: " + input.replace("\n", "\\n"));
        System.out.println("输出: " + result.replace("\n", "\\n"));
        
        String expected = "Here's what the text in the image says.\n{\n  \"plan\": {\n    \"name\": \"Free\"\n  }\n}";
        System.out.println("期望: " + expected.replace("\n", "\\n"));
        System.out.println("相等: " + result.equals(expected));
        
        // 逐字符比较
        if (!result.equals(expected)) {
            System.out.println("\n=== 逐字符比较 ===");
            int minLen = Math.min(result.length(), expected.length());
            for (int i = 0; i < minLen; i++) {
                char r = result.charAt(i);
                char e = expected.charAt(i);
                if (r != e) {
                    System.out.println("位置 " + i + " 不同: 实际='" + r + "'(" + (int)r + "), 期望='" + e + "'(" + (int)e + ")");
                }
            }
            if (result.length() != expected.length()) {
                System.out.println("长度不同: 实际=" + result.length() + ", 期望=" + expected.length());
            }
        }
    }
}
