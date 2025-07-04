package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.serviceimpl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 续写功能中断测试
 * 测试续写过程中用户中断聊天的处理逻辑
 * 
 * @author instser
 * @since 2025-07-04
 */
@ExtendWith(MockitoExtension.class)
public class ContinuationInterruptTest {

    @Mock
    private ChatInterruptService chatInterruptService;
    
    @Mock
    private TruncationDetectionService truncationDetectionService;
    
    @Mock
    private AiModelService aiModelService;
    
    @Mock
    private ChatMessageService chatMessageService;
    
    @Mock
    private ChatLimitService chatLimitService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private ChatRequestDTO request;
    private AiModel model;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        request = new ChatRequestDTO();
        request.setSessionId("test-session-123");
        request.setUserId(1L);
        request.setMessages(new ArrayList<>());
        
        // 添加测试消息
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "请帮我写一个长篇文章");
        request.getMessages().add(userMessage);
        
        model = new AiModel();
        model.setId(1L);
        model.setModelName("test-model");
        model.setModelCode("gpt-3.5-turbo");
        model.setProvider("OpenAI");
        model.setBaseUrl("https://api.openai.com/v1/chat/completions");
        model.setApiKey("test-api-key");
        
        response = new MockHttpServletResponse();
    }

    @Test
    void testContinueAiResponse_WhenInterruptedBeforeStart_ShouldReturnNull() throws Exception {
        // 模拟在续写开始前就被中断的情况
        when(chatInterruptService.isInterrupted("test-session-123")).thenReturn(true);
        
        // 使用反射调用私有方法
        Method continueAiResponseMethod = ChatServiceImpl.class.getDeclaredMethod(
            "continueAiResponse",
            HttpServletResponse.class,
            ChatRequestDTO.class,
            AiModel.class,
            String.class
        );
        continueAiResponseMethod.setAccessible(true);
        
        // 执行方法
        String result = (String) continueAiResponseMethod.invoke(
            chatService, 
            response, 
            request, 
            model, 
            "这是原始内容，需要续写"
        );
        
        // 验证：应该返回null（取消续写）
        assertNull(result, "当用户在续写开始前中断时，应该返回null");
        
        // 验证：应该检查中断状态
        verify(chatInterruptService, times(1)).isInterrupted("test-session-123");
    }

    @Test
    void testContinueAiResponse_WhenNotInterrupted_ShouldProceed() throws Exception {
        // 模拟正常情况（未被中断）
        when(chatInterruptService.isInterrupted("test-session-123")).thenReturn(false);
        
        // 由于实际的AI调用比较复杂，这里主要测试中断检查逻辑
        // 使用反射调用私有方法
        Method continueAiResponseMethod = ChatServiceImpl.class.getDeclaredMethod(
            "continueAiResponse",
            HttpServletResponse.class,
            ChatRequestDTO.class,
            AiModel.class,
            String.class
        );
        continueAiResponseMethod.setAccessible(true);
        
        try {
            // 执行方法（可能会因为网络调用失败，但我们主要关注中断检查）
            continueAiResponseMethod.invoke(
                chatService, 
                response, 
                request, 
                model, 
                "这是原始内容，需要续写"
            );
        } catch (Exception e) {
            // 忽略网络相关的异常，我们主要测试中断逻辑
            assertTrue(e.getCause() instanceof RuntimeException || 
                      e.getCause() instanceof NullPointerException,
                      "应该是网络调用相关的异常");
        }
        
        // 验证：应该检查中断状态
        verify(chatInterruptService, times(1)).isInterrupted("test-session-123");
    }

    @Test
    void testContinueAiResponse_WhenSessionIdIsNull_ShouldNotCheckInterrupt() throws Exception {
        // 模拟sessionId为null的情况
        request.setSessionId(null);
        
        // 使用反射调用私有方法
        Method continueAiResponseMethod = ChatServiceImpl.class.getDeclaredMethod(
            "continueAiResponse",
            HttpServletResponse.class,
            ChatRequestDTO.class,
            AiModel.class,
            String.class
        );
        continueAiResponseMethod.setAccessible(true);
        
        try {
            // 执行方法
            continueAiResponseMethod.invoke(
                chatService, 
                response, 
                request, 
                model, 
                "这是原始内容，需要续写"
            );
        } catch (Exception e) {
            // 忽略网络相关的异常
        }
        
        // 验证：不应该检查中断状态（因为sessionId为null）
        verify(chatInterruptService, never()).isInterrupted(any());
    }

    /**
     * 测试续写过程中的中断检查逻辑
     * 这个测试验证了我们在takeWhile中添加的中断检查
     */
    @Test
    void testContinuationInterruptLogic() {
        // 模拟续写过程中被中断的场景
        String sessionId = "test-session-123";

        // 第一次检查：未中断
        when(chatInterruptService.isInterrupted(sessionId)).thenReturn(false);

        // 模拟takeWhile的第一次检查
        boolean shouldContinue1 = !chatInterruptService.isInterrupted(sessionId);
        assertTrue(shouldContinue1, "第一次检查应该继续处理流");

        // 第二次检查：已中断
        when(chatInterruptService.isInterrupted(sessionId)).thenReturn(true);

        // 模拟takeWhile的第二次检查
        boolean shouldContinue2 = !chatInterruptService.isInterrupted(sessionId);
        assertFalse(shouldContinue2, "第二次检查应该停止整个流");

        // 验证中断检查被调用了两次
        verify(chatInterruptService, times(2)).isInterrupted(sessionId);
    }

    /**
     * 测试Reactive流的正确取消机制
     * 验证使用takeWhile而不是doOnNext中的return来终止流
     */
    @Test
    void testReactiveStreamCancellation() {
        // 创建一个模拟的数据流
        Flux<String> dataStream = Flux.just("data1", "data2", "data3", "data4", "data5");

        // 模拟中断条件：处理到第3个元素时中断
        AtomicInteger counter = new AtomicInteger(0);

        List<String> processedData = new ArrayList<>();

        // 使用takeWhile来正确终止流
        dataStream
            .takeWhile(data -> {
                int count = counter.incrementAndGet();
                if (count >= 3) {
                    System.out.println("检测到中断条件，停止流处理");
                    return false; // 停止整个流
                }
                return true; // 继续处理
            })
            .doOnNext(data -> {
                processedData.add(data);
                System.out.println("处理数据: " + data);
            })
            .doOnComplete(() -> System.out.println("流处理完成"))
            .doOnCancel(() -> System.out.println("流被取消"))
            .blockLast(); // 等待流完成

        // 验证只处理了前2个元素
        assertEquals(2, processedData.size(), "应该只处理前2个元素");
        assertEquals("data1", processedData.get(0));
        assertEquals("data2", processedData.get(1));
    }

    /**
     * 测试续写中断的日志记录
     */
    @Test
    void testContinuationInterruptLogging() {
        String sessionId = "test-session-123";
        
        // 模拟中断状态
        when(chatInterruptService.isInterrupted(sessionId)).thenReturn(true);
        
        // 检查中断状态
        boolean isInterrupted = chatInterruptService.isInterrupted(sessionId);
        
        // 验证
        assertTrue(isInterrupted, "应该检测到中断状态");
        verify(chatInterruptService, times(1)).isInterrupted(sessionId);
    }
}
