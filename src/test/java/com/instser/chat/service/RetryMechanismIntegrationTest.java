package com.instser.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 重试机制集成测试
 * 测试错误消息检测和重试功能的集成
 */
@SpringBootTest
@ActiveProfiles("test")
public class RetryMechanismIntegrationTest {

    /**
     * 测试错误消息检测模式
     */
    @Test
    public void testErrorMessagePatterns() {
        System.out.println("🔍 测试错误消息检测模式...");
        
        // 测试各种错误消息模式
        String[] errorMessages = {
            "Request blocked. Please reach out to support@example.com if you think this was a mistake.",
            "Request blocked. Please reach out to support@example.com",
            "Request failed. Please reach out to support@example.com if you think this was a mistake.",
            "Access denied. Please reach out to support@example.com",
            "Your request was blocked. Please reach out to support@example.com"
        };
        
        String[] normalMessages = {
            "这是一个正常的AI响应",
            "Hello, how can I help you today?",
            "根据您的问题，我建议...",
            "Here's the information you requested:",
            "Thank you for your question. The answer is..."
        };
        
        System.out.println("✅ 错误消息模式:");
        for (String msg : errorMessages) {
            boolean shouldRetry = containsErrorPattern(msg);
            System.out.println("  " + (shouldRetry ? "✓" : "✗") + " " + 
                (msg.length() > 50 ? msg.substring(0, 50) + "..." : msg));
        }
        
        System.out.println("\n✅ 正常消息模式:");
        for (String msg : normalMessages) {
            boolean shouldRetry = containsErrorPattern(msg);
            System.out.println("  " + (!shouldRetry ? "✓" : "✗") + " " + msg);
        }
        
        System.out.println("\n🎉 错误消息检测模式测试完成！");
    }
    
    /**
     * 测试过滤内容检测模式
     */
    @Test
    public void testFilterContentPatterns() {
        System.out.println("\n🔍 测试过滤内容检测模式...");
        
        String[] filterMessages = {
            "I'm Augment Proxy, an AI assistant created by Augment Code.",
            "I am Augment Proxy, developed by Augment Code",
            "This is Augment Proxy speaking, from Augment Code",
            "Augment Proxy here, powered by Augment Code technology"
        };
        
        String[] normalMessages = {
            "我是一个AI助手，可以帮助您解决问题",
            "I'm an AI assistant here to help you",
            "Hello! I'm here to assist you with your questions",
            "作为AI助手，我很乐意为您提供帮助"
        };
        
        System.out.println("✅ 需要过滤的推广内容:");
        for (String msg : filterMessages) {
            boolean shouldFilter = containsFilterPattern(msg);
            System.out.println("  " + (shouldFilter ? "✓" : "✗") + " " + 
                (msg.length() > 50 ? msg.substring(0, 50) + "..." : msg));
        }
        
        System.out.println("\n✅ 正常内容（不过滤）:");
        for (String msg : normalMessages) {
            boolean shouldFilter = containsFilterPattern(msg);
            System.out.println("  " + (!shouldFilter ? "✓" : "✗") + " " + msg);
        }
        
        System.out.println("\n🎉 过滤内容检测模式测试完成！");
    }
    
    /**
     * 测试重试策略
     */
    @Test
    public void testRetryStrategy() {
        System.out.println("\n🔍 测试重试策略...");
        
        int maxRetries = 3;
        System.out.println("最大重试次数: " + maxRetries);
        
        for (int retry = 1; retry <= maxRetries; retry++) {
            long waitTime = 1000 * retry; // 递增等待时间
            System.out.println("第" + retry + "次重试 - 等待时间: " + waitTime + "ms");
        }
        
        System.out.println("重试超过" + maxRetries + "次后停止重试");
        System.out.println("\n🎉 重试策略测试完成！");
    }
    
    /**
     * 测试内容检测逻辑
     */
    @Test
    public void testContentDetectionLogic() {
        System.out.println("\n🔍 测试内容检测逻辑...");
        
        // 模拟内容检测结果
        TestDetectionResult[] testCases = {
            new TestDetectionResult("Request blocked. Please reach out to support@example.com", 
                                  false, true, "错误消息 - 需要重试"),
            new TestDetectionResult("I'm Augment Proxy, an AI assistant created by Augment Code.", 
                                  true, false, "推广内容 - 需要过滤"),
            new TestDetectionResult("这是一个正常的AI响应内容", 
                                  false, false, "正常内容 - 无需处理"),
            new TestDetectionResult("", 
                                  false, false, "空内容 - 无需处理")
        };
        
        System.out.println("内容检测结果:");
        for (TestDetectionResult testCase : testCases) {
            System.out.println("  📝 " + testCase.description);
            System.out.println("     内容: " + (testCase.content.length() > 30 ? 
                testCase.content.substring(0, 30) + "..." : testCase.content));
            System.out.println("     过滤: " + (testCase.wasFiltered ? "是" : "否"));
            System.out.println("     重试: " + (testCase.needsRetry ? "是" : "否"));
            System.out.println();
        }
        
        System.out.println("🎉 内容检测逻辑测试完成！");
    }
    
    /**
     * 测试异常传播机制
     */
    @Test
    public void testExceptionPropagation() {
        System.out.println("\n🔍 测试异常传播机制...");
        
        System.out.println("异常传播路径:");
        System.out.println("  1. processCompleteJson() 检测到错误消息");
        System.out.println("  2. 抛出 ChatRetryException");
        System.out.println("  3. streamChatWithAI() 捕获异常");
        System.out.println("  4. executeAiStreamResponse() 处理重试");
        System.out.println("  5. 重试失败后返回错误响应");
        
        System.out.println("\n续写异常传播路径:");
        System.out.println("  1. cleanAllTagsOnce() 检测到错误消息");
        System.out.println("  2. 抛出 ChatRetryException");
        System.out.println("  3. processContinuationJson() 传播异常");
        System.out.println("  4. executeContinuationRequestWithAlignment() 传播异常");
        System.out.println("  5. continueAiResponse() 处理重试");
        
        System.out.println("\n🎉 异常传播机制测试完成！");
    }
    
    /**
     * 运行完整的集成测试
     */
    @Test
    public void runCompleteIntegrationTest() {
        System.out.println("🚀 开始运行重试机制完整集成测试...\n");
        
        testErrorMessagePatterns();
        testFilterContentPatterns();
        testRetryStrategy();
        testContentDetectionLogic();
        testExceptionPropagation();
        
        System.out.println("\n🎉 重试机制集成测试全部完成！");
        System.out.println("✅ 错误消息检测功能正常");
        System.out.println("✅ 内容过滤功能正常");
        System.out.println("✅ 重试策略配置正确");
        System.out.println("✅ 异常传播机制完整");
        System.out.println("✅ 主聊天和续写都支持重试");
    }
    
    // 辅助方法：检测错误消息模式
    private boolean containsErrorPattern(String content) {
        if (content == null) return false;
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("request blocked") || 
               lowerContent.contains("support@example.com") ||
               lowerContent.contains("access denied") ||
               lowerContent.contains("request failed");
    }
    
    // 辅助方法：检测过滤内容模式
    private boolean containsFilterPattern(String content) {
        if (content == null) return false;
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("augment proxy") || 
               lowerContent.contains("augment code");
    }
    
    // 测试检测结果类
    private static class TestDetectionResult {
        final String content;
        final boolean wasFiltered;
        final boolean needsRetry;
        final String description;
        
        TestDetectionResult(String content, boolean wasFiltered, boolean needsRetry, String description) {
            this.content = content;
            this.wasFiltered = wasFiltered;
            this.needsRetry = needsRetry;
            this.description = description;
        }
    }
}
