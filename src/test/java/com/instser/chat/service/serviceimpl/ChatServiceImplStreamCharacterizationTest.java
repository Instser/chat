package com.instser.chat.service.serviceimpl;

import com.instser.chat.dto.MessageSaveResult;
import com.instser.chat.monitor.AiCallMonitor;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatRateLimitService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatTitleService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.NetworkSearchOrchestrator;
import com.instser.chat.service.TruncationDetectionService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.service.ai.OpenAiCompatibleChatClient;
import com.instser.chat.service.completion.AiResponseCompletionHandler;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.prepare.ChatPrepareHandler;
import com.instser.chat.service.prepare.EditResendPrepareHandler;
import com.instser.chat.service.prepare.RetryPrepareHandler;
import com.instser.chat.service.continuation.ChatContinuationService;
import com.instser.chat.service.stream.AiStreamProcessor;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式相关特征测试：不依赖真实上游 AI，锁住关键副作用语义。
 */
class ChatServiceImplStreamCharacterizationTest {

    @Test
    void saveAiResponseDelegatesToCompletionPersistAndRecordsTokens() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        when(chatMessageService.sendMessageWithTokenInfo("s", "assistant", "ok", 1L, 2L))
                .thenReturn(MessageSaveResult.success(9L, "m-uuid", 8));
        when(chatLimitService.recordTokenUsage(1L, 8)).thenReturn(true);

        ChatServiceImpl service = createService(chatMessageService, chatLimitService);
        String id = service.saveAiResponseAndGetMessageId("s", 1L, 2L, "ok");

        assertEquals("m-uuid", id);
        verify(chatMessageService).sendMessageWithTokenInfo("s", "assistant", "ok", 1L, 2L);
        verify(chatLimitService).recordTokenUsage(1L, 8);
        verify(chatLimitService, never()).recordModelChatUsage(anyLong(), anyString());
    }

    @Test
    void savePartialInterruptedResponseShouldPersistOnlyExplicitCallOnce() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        when(chatMessageService.sendMessageWithTokenInfo(eq("s"), eq("assistant"), anyString(), eq(1L), eq(2L)))
                .thenReturn(MessageSaveResult.success(3L, "p-uuid", 4));

        ChatServiceImpl service = createService(chatMessageService, chatLimitService);
        service.savePartialAiResponse("s", 1L, 2L, "partial", true);

        verify(chatMessageService, times(1))
                .sendMessageWithTokenInfo(eq("s"), eq("assistant"), eq("partial [已中断]"), eq(1L), eq(2L));
        verify(chatLimitService, times(1)).recordTokenUsage(1L, 4);
    }

    @Test
    void saveErrorResponseShouldNotRecordModelUsage() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        ChatServiceImpl service = createService(chatMessageService, chatLimitService);

        service.saveErrorResponse("s", 1L, 2L, "Request failed. Please try again later.");

        verify(chatMessageService).sendErrorResponseMessage("s", 1L, 2L, "Request failed. Please try again later.");
        verify(chatLimitService, never()).recordModelChatUsage(anyLong(), anyString());
        verify(chatLimitService, never()).recordTokenUsage(anyLong(), anyInt());
    }

    private ChatServiceImpl createService(ChatMessageService chatMessageService, ChatLimitService chatLimitService) {
        WebClient.Builder webClientBuilder = WebClient.builder();
        FileUploadService fileUploadService = mock(FileUploadService.class);
        NetworkSearchOrchestrator networkSearchOrchestrator = mock(NetworkSearchOrchestrator.class);
        ChatContextBuilder contextBuilder = new ChatContextBuilder(chatMessageService, fileUploadService, networkSearchOrchestrator);
        return new ChatServiceImpl(
                chatMessageService,
                mock(ChatSessionService.class),
                mock(AiModelService.class),
                chatLimitService,
                mock(ChatRateLimitService.class),
                fileUploadService,
                mock(ChatInterruptService.class),
                mock(TruncationDetectionService.class),
                mock(AiCallMonitor.class),
                mock(ChatTitleService.class),
                networkSearchOrchestrator,
                new ChatStreamEventWriter(),
                new AiStreamProcessor(new OpenAiCompatibleChatClient(WebClient.builder()), new ChatStreamEventWriter(), new ContentPolicy(), mock(ChatInterruptService.class), mock(AiCallMonitor.class)),
                mock(ChatContinuationService.class),
                contextBuilder,
                new ChatPrepareHandler(chatMessageService, fileUploadService, contextBuilder),
                new RetryPrepareHandler(chatMessageService, fileUploadService, contextBuilder),
                new EditResendPrepareHandler(chatMessageService, fileUploadService, contextBuilder),
                new AiResponseCompletionHandler(chatMessageService, chatLimitService, mock(ChatTitleService.class), mock(WebSearchContentService.class), new ChatStreamEventWriter())
        );
    }
}
