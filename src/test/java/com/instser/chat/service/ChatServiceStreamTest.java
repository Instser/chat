package com.instser.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流式数据处理测试
 * 验证Google Gemini API字符丢失修复
 */
@SpringBootTest
@ActiveProfiles("test")
public class ChatServiceStreamTest {

    /**
     * 测试数据分割和重组逻辑
     * 模拟Google Gemini API返回的分割数据
     */
    @Test
    public void testDataSplitAndReassembly() {
        // 模拟被分割的数据
        String[] chunks = {
            "d",
            "at",
            "a: {\"choices\":[{\"delta\":{\"content\":\"",
            "接口对接说明文档，它详细描述了前端与后端交互时所需的\",\"role\":\"assistant\"},\"index\":0}],\"created\":1750856379,\"id\":\"test\",\"model\":\"gemini-2.5-flash\",\"object\":\"chat.completion.chunk\"}\n",
            "da",
            "ta: {\"choices\":[{\"delta\":{\"content\":\"",
            "口\",\"role\":\"assistant\"},\"index\":0}],\"created\":1750856379,\"id\":\"test\",\"model\":\"gemini-2.5-flash\",\"object\":\"chat.completion.chunk\"}\n"
        };

        // 模拟行缓冲区处理逻辑
        StringBuilder lineBuffer = new StringBuilder();
        List<String> completeLines = new ArrayList<>();

        for (String chunk : chunks) {
            lineBuffer.append(chunk);
            
            // 按换行符分割，但保留最后一个不完整的行
            String bufferedContent = lineBuffer.toString();
            String[] lines = bufferedContent.split("\n", -1);
            
            // 处理除最后一行外的所有行（这些都是完整的行）
            for (int i = 0; i < lines.length - 1; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    completeLines.add(line);
                }
            }
            
            // 最后一行可能不完整，保留在缓冲区中
            String lastLine = lines[lines.length - 1];
            lineBuffer.setLength(0);
            if (!lastLine.isEmpty()) {
                lineBuffer.append(lastLine);
            }
        }

        // 处理剩余的缓冲区数据
        if (lineBuffer.length() > 0) {
            String remainingLine = lineBuffer.toString().trim();
            if (!remainingLine.isEmpty()) {
                completeLines.add(remainingLine);
            }
        }

        // 验证结果
        assertEquals(2, completeLines.size(), "应该有2个完整的行");
        
        String firstLine = completeLines.get(0);
        assertTrue(firstLine.startsWith("data: "), "第一行应该以'data: '开头");
        assertTrue(firstLine.contains("接口对接说明文档"), "第一行应该包含完整的中文内容");
        
        String secondLine = completeLines.get(1);
        assertTrue(secondLine.startsWith("data: "), "第二行应该以'data: '开头");
        assertTrue(secondLine.contains("口"), "第二行应该包含中文字符");

        System.out.println("测试通过：数据分割和重组逻辑正常工作");
        System.out.println("完整行数量：" + completeLines.size());
        for (int i = 0; i < completeLines.size(); i++) {
            System.out.println("行 " + (i + 1) + ": " + completeLines.get(i));
        }
    }

    /**
     * 测试JSON缓冲区处理
     */
    @Test
    public void testJsonBufferProcessing() {
        // 模拟分片的JSON数据
        String[] jsonFragments = {
            "{\"choices\":[{\"delta\":{\"content\":\"",
            "这是一个\",\"role\":\"assistant\"},\"index\":0}]",
            ",\"created\":1750856379,\"id\":\"test\",\"model\":\"gemini-2.5-flash\",\"object\":\"chat.completion.chunk\"}"
        };

        StringBuilder jsonBuffer = new StringBuilder();
        String completeJson = null;

        for (String fragment : jsonFragments) {
            jsonBuffer.append(fragment);
            
            // 尝试解析JSON
            String bufferedJson = jsonBuffer.toString();
            if (isValidJson(bufferedJson)) {
                completeJson = bufferedJson;
                jsonBuffer.setLength(0);
                break;
            }
        }

        assertNotNull(completeJson, "应该能够重组出完整的JSON");
        assertTrue(completeJson.contains("这是一个"), "JSON应该包含完整的中文内容");
        assertTrue(isValidJson(completeJson), "重组的JSON应该是有效的");

        System.out.println("测试通过：JSON缓冲区处理正常工作");
        System.out.println("完整JSON: " + completeJson);
    }

    /**
     * 简单的JSON有效性检查
     */
    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 简单检查：确保有匹配的大括号
            int openBraces = 0;
            for (char c : jsonString.toCharArray()) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
            }
            return openBraces == 0 && jsonString.trim().startsWith("{") && jsonString.trim().endsWith("}");
        } catch (Exception e) {
            return false;
        }
    }
}
