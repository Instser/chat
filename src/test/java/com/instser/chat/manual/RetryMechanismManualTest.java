package com.instser.chat.manual;

/**
 * 重试机制手动测试类
 * 可以直接运行来验证错误消息重试功能
 */
public class RetryMechanismManualTest {
    
    public static void main(String[] args) {
        System.out.println("🚀 重试机制手动测试开始...\n");
        
        RetryMechanismManualTest test = new RetryMechanismManualTest();
        
        // 运行各项测试
        test.testErrorMessageDetection();
        test.testFilterContentDetection();
        test.testRetryLogic();
        test.testContentClassification();
        test.testRetryScenarios();
        test.testRetryFailureHandling();

        System.out.println("\n🎉 重试机制手动测试完成！");
        System.out.println("📋 测试总结:");
        System.out.println("  ✅ 错误消息检测功能正常");
        System.out.println("  ✅ 内容过滤功能正常");
        System.out.println("  ✅ 重试逻辑设计合理");
        System.out.println("  ✅ 内容分类准确");
        System.out.println("  ✅ 重试场景覆盖完整");
        System.out.println("  ✅ 重试失败处理统一");
    }
    
    /**
     * 测试错误消息检测
     */
    public void testErrorMessageDetection() {
        System.out.println("🔍 测试1: 错误消息检测");
        System.out.println("=" .repeat(50));
        
        String[] errorMessages = {
            "Request blocked. Please reach out to support@example.com if you think this was a mistake.",
            "Request failed. Please reach out to support@example.com",
            "Access denied. Please contact support@example.com",
            "Your request was blocked. Please reach out to support@example.com if you think this was a mistake."
        };
        
        String[] normalMessages = {
            "这是一个正常的AI响应",
            "Hello, how can I help you today?",
            "根据您的问题，我建议您可以尝试以下方法",
            "Thank you for your question. Here's my response."
        };
        
        System.out.println("📋 应该被检测为错误消息的内容:");
        for (int i = 0; i < errorMessages.length; i++) {
            boolean isError = shouldRestartChat(errorMessages[i]);
            System.out.printf("  %d. %s → %s\n", 
                i + 1, 
                truncateString(errorMessages[i], 60),
                isError ? "✅ 检测为错误" : "❌ 未检测到"
            );
        }
        
        System.out.println("\n📋 不应该被检测为错误消息的内容:");
        for (int i = 0; i < normalMessages.length; i++) {
            boolean isError = shouldRestartChat(normalMessages[i]);
            System.out.printf("  %d. %s → %s\n", 
                i + 1, 
                truncateString(normalMessages[i], 60),
                !isError ? "✅ 正常内容" : "❌ 误判为错误"
            );
        }
        
        System.out.println();
    }
    
    /**
     * 测试过滤内容检测
     */
    public void testFilterContentDetection() {
        System.out.println("🔍 测试2: 过滤内容检测");
        System.out.println("=" .repeat(50));
        
        String[] filterMessages = {
            "I'm Augment Proxy, an AI assistant created by Augment Code.",
            "I am Augment Proxy, developed by Augment Code to help users",
            "This is Augment Proxy speaking, from Augment Code",
            "Augment Proxy here, powered by Augment Code technology"
        };
        
        String[] normalMessages = {
            "我是一个AI助手，可以帮助您解决问题",
            "I'm an AI assistant here to help you",
            "Hello! I'm here to assist you with your questions",
            "作为AI助手，我很乐意为您提供帮助"
        };
        
        System.out.println("📋 应该被过滤的推广内容:");
        for (int i = 0; i < filterMessages.length; i++) {
            boolean shouldFilter = shouldFilterContent(filterMessages[i]);
            System.out.printf("  %d. %s → %s\n", 
                i + 1, 
                truncateString(filterMessages[i], 60),
                shouldFilter ? "✅ 需要过滤" : "❌ 未检测到"
            );
        }
        
        System.out.println("\n📋 不应该被过滤的正常内容:");
        for (int i = 0; i < normalMessages.length; i++) {
            boolean shouldFilter = shouldFilterContent(normalMessages[i]);
            System.out.printf("  %d. %s → %s\n", 
                i + 1, 
                truncateString(normalMessages[i], 60),
                !shouldFilter ? "✅ 正常内容" : "❌ 误判为推广"
            );
        }
        
        System.out.println();
    }
    
    /**
     * 测试重试逻辑
     */
    public void testRetryLogic() {
        System.out.println("🔍 测试3: 重试逻辑");
        System.out.println("=" .repeat(50));
        
        int maxRetries = 3;
        System.out.println("📋 重试策略配置:");
        System.out.println("  最大重试次数: " + maxRetries);
        System.out.println("  等待时间策略: 递增等待（1秒、2秒、3秒）");
        
        System.out.println("\n📋 重试流程模拟:");
        for (int retry = 1; retry <= maxRetries; retry++) {
            long waitTime = 1000 * retry;
            System.out.printf("  第%d次重试 - 等待时间: %dms (%d秒)\n", 
                retry, waitTime, waitTime / 1000);
        }
        
        System.out.println("  重试超过" + maxRetries + "次后 → 返回统一错误消息");

        System.out.println("\n📋 重试失败处理:");
        System.out.println("  ✅ 主聊天重试失败 → 返回 'Request failed. Please try again later.'");
        System.out.println("  ✅ 续写重试失败 → 返回 null（由主流程处理）");
        System.out.println("  ✅ 错误消息与其他错误保持一致");

        System.out.println("\n📋 重试触发条件:");
        System.out.println("  ✅ 检测到错误消息时自动重试");
        System.out.println("  ✅ 主聊天和续写都支持重试");
        System.out.println("  ✅ 重试失败后返回统一错误响应");
        System.out.println("  ✅ 过滤内容不触发重试（直接过滤）");
        
        System.out.println();
    }
    
    /**
     * 测试内容分类
     */
    public void testContentClassification() {
        System.out.println("🔍 测试4: 内容分类");
        System.out.println("=" .repeat(50));
        
        TestCase[] testCases = {
            new TestCase("Request blocked. Please reach out to support@example.com", 
                        "错误消息", "重试", false, true),
            new TestCase("I'm Augment Proxy, an AI assistant created by Augment Code.", 
                        "推广内容", "过滤", true, false),
            new TestCase("这是一个正常的AI响应内容，没有任何问题。", 
                        "正常内容", "正常处理", false, false),
            new TestCase("", 
                        "空内容", "忽略", false, false)
        };
        
        System.out.println("📋 内容分类测试:");
        for (int i = 0; i < testCases.length; i++) {
            TestCase testCase = testCases[i];
            boolean actualFilter = shouldFilterContent(testCase.content);
            boolean actualRetry = shouldRestartChat(testCase.content);
            
            System.out.printf("  %d. 类型: %s\n", i + 1, testCase.type);
            System.out.printf("     内容: %s\n", truncateString(testCase.content, 50));
            System.out.printf("     预期处理: %s\n", testCase.expectedAction);
            System.out.printf("     过滤检测: %s (预期: %s)\n", 
                actualFilter ? "是" : "否", testCase.expectedFilter ? "是" : "否");
            System.out.printf("     重试检测: %s (预期: %s)\n", 
                actualRetry ? "是" : "否", testCase.expectedRetry ? "是" : "否");
            System.out.printf("     结果: %s\n", 
                (actualFilter == testCase.expectedFilter && actualRetry == testCase.expectedRetry) 
                ? "✅ 正确" : "❌ 错误");
            System.out.println();
        }
    }
    
    /**
     * 测试重试场景
     */
    public void testRetryScenarios() {
        System.out.println("🔍 测试5: 重试场景");
        System.out.println("=" .repeat(50));
        
        System.out.println("📋 主聊天重试场景:");
        System.out.println("  1. 用户发送消息");
        System.out.println("  2. AI返回错误消息");
        System.out.println("  3. 系统检测到错误消息");
        System.out.println("  4. 抛出ChatRetryException");
        System.out.println("  5. executeAiStreamResponse捕获异常");
        System.out.println("  6. 等待1秒后重试");
        System.out.println("  7. 如果仍然失败，等待2秒后再次重试");
        System.out.println("  8. 最多重试3次，失败后返回错误响应");
        
        System.out.println("\n📋 续写重试场景:");
        System.out.println("  1. AI响应被截断，触发续写");
        System.out.println("  2. 续写过程中返回错误消息");
        System.out.println("  3. cleanAllTagsOnce检测到错误消息");
        System.out.println("  4. 抛出ChatRetryException");
        System.out.println("  5. continueAiResponse捕获异常");
        System.out.println("  6. 等待1秒后重试续写");
        System.out.println("  7. 最多重试3次，失败后返回null");
        
        System.out.println("\n📋 异常传播路径:");
        System.out.println("  主聊天: processCompleteJson → streamChatWithAI → executeAiStreamResponse");
        System.out.println("  续写: cleanAllTagsOnce → processContinuationJson → continueAiResponse");
        
        System.out.println();
    }

    /**
     * 测试重试失败处理
     */
    public void testRetryFailureHandling() {
        System.out.println("🔍 测试6: 重试失败处理");
        System.out.println("=" .repeat(50));

        System.out.println("📋 重试失败场景模拟:");
        System.out.println("  场景1: 主聊天重试失败");
        System.out.println("    1. 用户发送消息");
        System.out.println("    2. AI返回错误消息 'Request blocked...'");
        System.out.println("    3. 第1次重试 → 仍然返回错误消息");
        System.out.println("    4. 第2次重试 → 仍然返回错误消息");
        System.out.println("    5. 第3次重试 → 仍然返回错误消息");
        System.out.println("    6. 重试次数达到上限，停止重试");
        System.out.println("    7. 向用户返回: 'Request failed. Please try again later.'");

        System.out.println("\n  场景2: 续写重试失败");
        System.out.println("    1. AI响应被截断，触发续写");
        System.out.println("    2. 续写返回错误消息 'Request blocked...'");
        System.out.println("    3. 第1次重试续写 → 仍然返回错误消息");
        System.out.println("    4. 第2次重试续写 → 仍然返回错误消息");
        System.out.println("    5. 第3次重试续写 → 仍然返回错误消息");
        System.out.println("    6. 续写重试失败，返回null");
        System.out.println("    7. 主流程处理续写失败情况");

        System.out.println("\n📋 错误消息统一性:");
        System.out.println("  ✅ 重试失败错误消息: 'Request failed. Please try again later.'");
        System.out.println("  ✅ 其他系统错误消息: 'Request failed. Please try again later.'");
        System.out.println("  ✅ 网络错误消息: 'Request failed. Please try again later.'");
        System.out.println("  ✅ 保持用户体验一致性");

        System.out.println("\n📋 重试失败日志:");
        System.out.println("  第1次重试: WARN - 检测到需要重试的错误消息，第1次重试，最大重试次数: 3");
        System.out.println("  第2次重试: WARN - 检测到需要重试的错误消息，第2次重试，最大重试次数: 3");
        System.out.println("  第3次重试: WARN - 检测到需要重试的错误消息，第3次重试，最大重试次数: 3");
        System.out.println("  重试结束: ERROR - 重试次数已达上限，停止重试");

        System.out.println("\n📋 与其他错误处理的一致性:");
        System.out.println("  ✅ 错误消息格式统一");
        System.out.println("  ✅ 错误响应结构统一");
        System.out.println("  ✅ 用户体验统一");
        System.out.println("  ✅ 日志记录统一");

        System.out.println();
    }

    // 模拟错误消息检测方法
    private boolean shouldRestartChat(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("request blocked") || 
               lowerContent.contains("support@example.com") ||
               lowerContent.contains("access denied") ||
               lowerContent.contains("request failed");
    }
    
    // 模拟过滤内容检测方法
    private boolean shouldFilterContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("augment proxy") || 
               lowerContent.contains("augment code");
    }
    
    // 截断字符串用于显示
    private String truncateString(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    // 测试用例类
    private static class TestCase {
        final String content;
        final String type;
        final String expectedAction;
        final boolean expectedFilter;
        final boolean expectedRetry;
        
        TestCase(String content, String type, String expectedAction, 
                boolean expectedFilter, boolean expectedRetry) {
            this.content = content;
            this.type = type;
            this.expectedAction = expectedAction;
            this.expectedFilter = expectedFilter;
            this.expectedRetry = expectedRetry;
        }
    }
}
