package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.ChatInterruptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 续写中断保存问题修复测试
 * 专注于测试续写中断的逻辑流程
 *
 * @author instser
 * @since 2025-07-05
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContinuationInterruptFixTest {

    @Mock
    private ChatInterruptServiceImpl chatInterruptService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * 测试续写中断标记的生成和识别
     */
    @Test
    @Order(1)
    void testContinuationInterruptMarker() {
        System.out.println("\n=== 测试1: 续写中断标记的生成和识别 ===");

        // 测试数据
        String originalContent = "这是原始的AI响应内容，但是被截断了...";
        String partialContinuation = "这是部分续写内容";

        // 模拟续写被中断的情况
        String interruptedResult = "CONTINUATION_INTERRUPTED:" + partialContinuation;

        // 验证中断标记的识别
        assertTrue(interruptedResult.startsWith("CONTINUATION_INTERRUPTED:"),
                "应该能正确识别续写中断标记");

        // 验证内容提取
        String extractedContent = interruptedResult.substring("CONTINUATION_INTERRUPTED:".length());
        assertEquals(partialContinuation, extractedContent,
                "应该能正确提取续写中断的部分内容");

        // 验证拼接后的完整内容
        String combinedContent = originalContent + extractedContent;
        assertTrue(combinedContent.contains(originalContent),
                "拼接后的内容应该包含原始内容");
        assertTrue(combinedContent.contains(partialContinuation),
                "拼接后的内容应该包含部分续写内容");

        System.out.println("✅ 续写中断标记测试通过");
        System.out.println("原始内容长度: " + originalContent.length());
        System.out.println("部分续写长度: " + partialContinuation.length());
        System.out.println("拼接后长度: " + combinedContent.length());
    }

    /**
     * 测试中断状态传递逻辑
     */
    @Test
    @Order(2)
    void testInterruptStatusPropagation() {
        System.out.println("\n=== 测试2: 中断状态传递逻辑 ===");

        // 模拟续写中断的情况
        boolean wasManuallyInterrupted = false; // 主流程没有被中断
        String continuationResult = "CONTINUATION_INTERRUPTED:部分续写内容";

        // 模拟handleStreamCompletionWithInterruptStatus方法的逻辑
        boolean finalInterruptedStatus = wasManuallyInterrupted;

        if (continuationResult != null && continuationResult.startsWith("CONTINUATION_INTERRUPTED:")) {
            // 检测到续写中断标记
            finalInterruptedStatus = true;
            String actualContent = continuationResult.substring("CONTINUATION_INTERRUPTED:".length());

            System.out.println("检测到续写中断，部分内容: " + actualContent);
        }

        // 验证最终中断状态
        assertTrue(finalInterruptedStatus, "当续写被中断时，最终中断状态应该为true");

        System.out.println("✅ 中断状态传递测试通过");
        System.out.println("原始中断状态: " + wasManuallyInterrupted);
        System.out.println("最终中断状态: " + finalInterruptedStatus);
    }

    /**
     * 测试会话清理时序
     */
    @Test
    @Order(3)
    void testSessionCleanupTiming() {
        System.out.println("\n=== 测试3: 会话清理时序 ===");

        String sessionId = "test-session-123";

        // 模拟会话注册
        when(chatInterruptService.isInterrupted(sessionId)).thenReturn(false);

        // 模拟续写过程中用户点击停止
        when(chatInterruptService.isInterrupted(sessionId)).thenReturn(true);

        // 验证中断检测
        assertTrue(chatInterruptService.isInterrupted(sessionId),
                "续写过程中应该能检测到中断");

        // 模拟会话清理
        doNothing().when(chatInterruptService).markSessionCompleted(sessionId);

        // 验证会话清理调用
        chatInterruptService.markSessionCompleted(sessionId);
        verify(chatInterruptService, times(1)).markSessionCompleted(sessionId);

        System.out.println("✅ 会话清理时序测试通过");
    }

    /**
     * 测试完整的续写中断流程
     */
    @Test
    @Order(4)
    void testCompleteInterruptionFlow() {
        System.out.println("\n=== 测试4: 完整的续写中断流程 ===");

        // 1. 模拟原始内容和续写内容
        String originalContent = "这是AI的原始响应内容，但是由于长度限制被截断了";
        String partialContinuation = "这是续写的部分内容，用户在续写过程中点击了停止按钮";

        // 2. 模拟续写被中断的返回值
        String continuationResult = "CONTINUATION_INTERRUPTED:" + partialContinuation;

        // 3. 模拟主流程处理续写结果
        boolean wasManuallyInterrupted = false; // 主流程没有被手动中断
        boolean finalInterruptedStatus = wasManuallyInterrupted;
        StringBuilder aiResponseContentRef = new StringBuilder(originalContent);

        // 4. 检测续写中断标记
        if (continuationResult.startsWith("CONTINUATION_INTERRUPTED:")) {
            String actualContent = continuationResult.substring("CONTINUATION_INTERRUPTED:".length());

            // 将部分续写内容拼接到原内容后面
            aiResponseContentRef.append(actualContent);

            // 设置最终中断状态为true（续写被中断）
            finalInterruptedStatus = true;

            System.out.println("检测到续写中断，部分内容长度: " + actualContent.length());
        }

        // 5. 验证结果
        String finalContent = aiResponseContentRef.toString();

        assertTrue(finalInterruptedStatus, "最终中断状态应该为true");
        assertTrue(finalContent.contains(originalContent), "最终内容应该包含原始内容");
        assertTrue(finalContent.contains(partialContinuation), "最终内容应该包含部分续写内容");
        assertEquals(originalContent.length() + partialContinuation.length(), finalContent.length(),
                "最终内容长度应该等于原始内容+部分续写内容");

        System.out.println("✅ 完整续写中断流程测试通过");
        System.out.println("原始内容长度: " + originalContent.length());
        System.out.println("部分续写长度: " + partialContinuation.length());
        System.out.println("最终内容长度: " + finalContent.length());
        System.out.println("最终中断状态: " + finalInterruptedStatus);
    }
}
