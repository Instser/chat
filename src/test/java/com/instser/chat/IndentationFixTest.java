package com.instser.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试缩进修复功能
 */
public class IndentationFixTest {

    /**
     * 模拟detectContentAndGetResult方法中的缩进修复逻辑
     */
    private String fixIndentation(String content) {
        // 基础清理：移除无效字符
        String cleaned = content.replaceAll("[\uFFFD\u0000]", "");
        
        // 🔧 修复缩进问题：逐行处理，只清理行内的多余空格，完全保留行首的缩进空格
        String[] lines = cleaned.split("\n", -1);
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.trim().isEmpty()) {
                // 非空行：找到第一个非空格字符的位置
                int leadingSpaces = 0;
                while (leadingSpaces < line.length() && line.charAt(leadingSpaces) == ' ') {
                    leadingSpaces++;
                }
                
                // 分离缩进和内容
                String indent = line.substring(0, leadingSpaces);
                String content_part = line.substring(leadingSpaces);
                
                // 只对内容部分清理多余空格
                String cleanedContent = content_part.replaceAll(" {2,}", " ");
                
                result.append(indent).append(cleanedContent);
            } else {
                // 空行：保持原样
                result.append(line);
            }
            
            // 添加换行符（除了最后一行）
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }
        
        return result.toString();
    }

    @Test
    public void testIndentationPreservation() {
        String testHtml = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>Test</title>\n" +
                "  <style>\n" +
                "    body {\n" +
                "      margin: 0;\n" +
                "      padding: 0;\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <h1>Hello    World</h1>\n" +
                "  <p>This  has   multiple    spaces</p>\n" +
                "</body>\n" +
                "</html>";

        String result = fixIndentation(testHtml);
        
        // 验证缩进是否保留
        String[] originalLines = testHtml.split("\n");
        String[] resultLines = result.split("\n");
        
        assertEquals(originalLines.length, resultLines.length, "行数应该保持一致");
        
        for (int i = 0; i < originalLines.length; i++) {
            String origLine = originalLines[i];
            String resultLine = resultLines[i];
            
            // 计算原始行的缩进
            int origIndent = 0;
            while (origIndent < origLine.length() && origLine.charAt(origIndent) == ' ') {
                origIndent++;
            }
            
            // 计算结果行的缩进
            int resultIndent = 0;
            while (resultIndent < resultLine.length() && resultLine.charAt(resultIndent) == ' ') {
                resultIndent++;
            }
            
            assertEquals(origIndent, resultIndent, 
                String.format("第%d行的缩进应该保持一致: 原始%d空格, 结果%d空格", 
                    i + 1, origIndent, resultIndent));
        }
        
        // 验证行内多余空格是否被清理
        assertTrue(result.contains("<h1>Hello World</h1>"), 
            "行内多余空格应该被清理");
        assertTrue(result.contains("<p>This has multiple spaces</p>"), 
            "行内多余空格应该被清理");
        
        // 验证缩进空格没有被清理
        assertTrue(result.contains("  <meta charset=\"UTF-8\">"), 
            "行首缩进应该被保留");
        assertTrue(result.contains("    body {"), 
            "CSS缩进应该被保留");
        assertTrue(result.contains("      margin: 0;"), 
            "深层缩进应该被保留");
        
        System.out.println("=== 原始HTML ===");
        System.out.println(testHtml);
        System.out.println("\n=== 修复后HTML ===");
        System.out.println(result);
        System.out.println("\n✅ 缩进修复测试通过！");
    }

    @Test
    public void testEdgeCases() {
        // 测试边界情况
        
        // 1. 只有空格的行
        String onlySpaces = "    \n  content  \n    ";
        String result1 = fixIndentation(onlySpaces);
        assertEquals("    \n  content\n    ", result1);
        
        // 2. 没有缩进的行
        String noIndent = "content    with    spaces";
        String result2 = fixIndentation(noIndent);
        assertEquals("content with spaces", result2);
        
        // 3. 混合tab和空格（虽然不推荐，但要处理）
        String mixedIndent = "  \tcontent    here";
        String result3 = fixIndentation(mixedIndent);
        assertEquals("  \tcontent here", result3);
        
        System.out.println("✅ 边界情况测试通过！");
    }
}
