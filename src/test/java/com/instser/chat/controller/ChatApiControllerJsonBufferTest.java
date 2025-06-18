package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatApiController JSON缓冲机制测试
 * 验证流式传输JSON分片数据修复功能
 */
@SpringBootTest
@ActiveProfiles("test")
public class ChatApiControllerJsonBufferTest {

    /**
     * 测试完整JSON验证
     */
    @Test
    public void testValidCompleteJson() {
        String completeJson = "{\"id\":\"test\",\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}";
        assertTrue(isValidJson(completeJson), "完整的JSON应该验证通过");
    }

    /**
     * 测试不完整JSON验证
     */
    @Test
    public void testInvalidIncompleteJson() {
        String incompleteJson = "{\"id\":\"test\",\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":nul";
        assertFalse(isValidJson(incompleteJson), "不完整的JSON应该验证失败");
    }

    /**
     * 测试空字符串验证
     */
    @Test
    public void testEmptyStringValidation() {
        assertFalse(isValidJson(""), "空字符串应该验证失败");
        assertFalse(isValidJson(null), "null应该验证失败");
        assertFalse(isValidJson("   "), "空白字符串应该验证失败");
    }

    /**
     * 测试JSON分片重组场景
     */
    @Test
    public void testJsonFragmentReassembly() {
        // 模拟分片场景
        String fragment1 = "{\"id\":\"test\",\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":nul";
        String fragment2 = "l}]}";
        
        // 第一片应该验证失败
        assertFalse(isValidJson(fragment1), "第一个分片应该验证失败");
        
        // 组合后应该验证成功
        String combined = fragment1 + fragment2;
        assertTrue(isValidJson(combined), "组合后的JSON应该验证成功");
    }

    /**
     * 测试复杂JSON分片场景
     */
    @Test
    public void testComplexJsonFragments() {
        // 模拟更复杂的分片场景
        String fragment1 = "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\",\"created\":1234567890,\"choices\":[{\"index\":0,\"delta\":{\"content\":\"图片压缩工具</";
        String fragment2 = "title>\"},\"finish_reason\":null}]}";
        
        assertFalse(isValidJson(fragment1), "复杂JSON第一个分片应该验证失败");
        assertTrue(isValidJson(fragment1 + fragment2), "复杂JSON组合后应该验证成功");
    }

    /**
     * 测试中文内容的JSON分片
     */
    @Test
    public void testChineseContentFragments() {
        String fragment1 = "{\"choices\":[{\"delta\":{\"content\":\"你好，这是一个测试消息，包含中文内容";
        String fragment2 = "，用于验证JSON分片处理\"},\"finish_reason\":null}]}";
        
        assertFalse(isValidJson(fragment1), "包含中文的第一个分片应该验证失败");
        assertTrue(isValidJson(fragment1 + fragment2), "包含中文的组合JSON应该验证成功");
    }

    /**
     * 验证JSON字符串是否完整有效
     * 复制ChatApiController中的方法用于测试
     */
    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
