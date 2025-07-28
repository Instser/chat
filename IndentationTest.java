/**
 * 简单的缩进修复测试
 */
public class IndentationTest {

    /**
     * 模拟detectContentAndGetResult方法中的缩进修复逻辑
     */
    private static String fixIndentation(String content) {
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

    public static void main(String[] args) {
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

        System.out.println("=== 原始HTML ===");
        System.out.println("长度: " + testHtml.length());
        System.out.println(testHtml);
        
        // 模拟原来的错误处理
        String oldCleaned = testHtml.replaceAll("[\uFFFD\u0000]", "");
        oldCleaned = oldCleaned.replaceAll(" {2,}", " ");
        
        System.out.println("\n=== 原来的错误处理结果 ===");
        System.out.println("长度: " + oldCleaned.length());
        System.out.println(oldCleaned);
        
        // 使用新的修复方法
        String result = fixIndentation(testHtml);
        
        System.out.println("\n=== 新的修复处理结果 ===");
        System.out.println("长度: " + result.length());
        System.out.println(result);
        
        // 验证结果
        System.out.println("\n=== 验证结果 ===");
        
        // 验证缩进是否保留
        String[] originalLines = testHtml.split("\n");
        String[] resultLines = result.split("\n");
        
        boolean indentationPreserved = true;
        for (int i = 0; i < originalLines.length && i < resultLines.length; i++) {
            String origLine = originalLines[i];
            String resultLine = resultLines[i];
            
            // 计算缩进
            int origIndent = 0;
            while (origIndent < origLine.length() && origLine.charAt(origIndent) == ' ') {
                origIndent++;
            }
            
            int resultIndent = 0;
            while (resultIndent < resultLine.length() && resultLine.charAt(resultIndent) == ' ') {
                resultIndent++;
            }
            
            if (origIndent != resultIndent) {
                System.out.println("❌ 第" + (i+1) + "行缩进不匹配: 原始" + origIndent + "空格 -> 修复后" + resultIndent + "空格");
                indentationPreserved = false;
            }
        }
        
        if (indentationPreserved) {
            System.out.println("✅ 所有行的缩进都得到了正确保留");
        }
        
        // 验证行内多余空格是否被清理
        if (result.contains("<h1>Hello World</h1>") && result.contains("<p>This has multiple spaces</p>")) {
            System.out.println("✅ 行内多余空格被正确清理");
        } else {
            System.out.println("❌ 行内多余空格清理失败");
        }
        
        // 验证缩进空格没有被清理
        if (result.contains("  <meta charset=\"UTF-8\">") && 
            result.contains("    body {") && 
            result.contains("      margin: 0;")) {
            System.out.println("✅ 行首缩进得到正确保留");
        } else {
            System.out.println("❌ 行首缩进保留失败");
        }
        
        System.out.println("\n🎉 缩进修复测试完成！");
    }
}
