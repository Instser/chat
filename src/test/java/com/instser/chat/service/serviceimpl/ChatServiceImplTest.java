package com.instser.chat.service.serviceimpl;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.User;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.monitor.AiCallMonitor;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatRateLimitService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatTitleService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.NetworkAiService;
import com.instser.chat.service.NetworkSearchOrchestrator;
import com.instser.chat.service.TruncationDetectionService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import com.instser.chat.service.context.ChatContextBuilder;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    @Test
    void streamChatWhenDailyLimitExceededShouldSaveErrorResponseWithStatus3InsteadOfNormalAssistantMessage() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        AiModelService aiModelService = mock(AiModelService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        ChatRateLimitService chatRateLimitService = mock(ChatRateLimitService.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        ChatInterruptService chatInterruptService = mock(ChatInterruptService.class);
        TruncationDetectionService truncationDetectionService = mock(TruncationDetectionService.class);
        WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);
        NetworkAiService networkAiService = mock(NetworkAiService.class);
        WebClient.Builder webClientBuilder = WebClient.builder();
        RestTemplate restTemplate = new RestTemplate();
        Executor continuationExecutor = Runnable::run;
        AiCallMonitor aiCallMonitor = mock(AiCallMonitor.class);

        ChatServiceImpl service = new ChatServiceImpl(
                chatMessageService,
                chatSessionService,
                aiModelService,
                chatLimitService,
                chatRateLimitService,
                fileUploadService,
                chatInterruptService,
                webClientBuilder,
                restTemplate,
                truncationDetectionService,
                webSearchContentService,
                aiCallMonitor,
                mock(ChatTitleService.class),
                mock(NetworkSearchOrchestrator.class),
                new ContentPolicy(),
                new ChatStreamEventWriter(),
                new ChatContextBuilder(chatMessageService, fileUploadService, mock(NetworkSearchOrchestrator.class)),
                continuationExecutor
        );

        Long userId = 1003L;
        String sessionId = "session-chat";
        String userContent = "普通聊天触发限制";

        AiModel model = new AiModel();
        model.setId(7L);
        model.setIsAvailable(1);
        model.setModelCode("test-model");

        ChatMessage savedUserMessage = new ChatMessage();
        savedUserMessage.setId(501L);
        savedUserMessage.setMessageId("msg-user-chat");
        savedUserMessage.setSessionId(sessionId);
        savedUserMessage.setUserId(userId);
        savedUserMessage.setRole("user");
        savedUserMessage.setContent(userContent);

        when(aiModelService.getById(model.getId())).thenReturn(model);
        when(chatMessageService.sendMessage(sessionId, "user", userContent, userId, model.getId(), 0)).thenReturn(501L);
        when(chatMessageService.getById(501L)).thenReturn(savedUserMessage);
        when(chatLimitService.validateChatPermission(userId)).thenReturn(permissionOk());
        when(chatLimitService.validateModelChatPermission(userId, String.valueOf(model.getId()))).thenReturn(permissionOk());
        when(chatRateLimitService.checkUserChatRateLimit(userId)).thenReturn(true);
        when(chatRateLimitService.getClientIpAddress()).thenReturn("127.0.0.1");
        when(chatRateLimitService.checkIpChatRateLimit(anyString())).thenReturn(true);
        when(chatRateLimitService.checkDailyChatLimit(userId)).thenReturn(false);
        when(chatInterruptService.hasActiveSession(userId)).thenReturn(false);

        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId(sessionId);
        request.setModelId(model.getId());
        request.setMessages(List.of(Map.of("role", "user", "content", userContent)));

        HttpServletResponse response = new MockHttpServletResponse();

        ChatResponseDTO result = service.processStreamChat(request, authentication(userId), response);

        assertEquals(false, result.isSuccess());
        verify(chatMessageService).sendErrorResponseMessage(
                sessionId,
                userId,
                model.getId(),
                "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow."
        );
        verify(chatMessageService, never()).sendMessage(
                sessionId,
                "assistant",
                "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow.",
                userId,
                model.getId(),
                0
        );
    }

    @Test
    void retryWhenDailyLimitExceededShouldDeleteTargetMessageBeforeSavingNewErrorResponse() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        AiModelService aiModelService = mock(AiModelService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        ChatRateLimitService chatRateLimitService = mock(ChatRateLimitService.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        ChatInterruptService chatInterruptService = mock(ChatInterruptService.class);
        TruncationDetectionService truncationDetectionService = mock(TruncationDetectionService.class);
        WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);
        NetworkAiService networkAiService = mock(NetworkAiService.class);
        WebClient.Builder webClientBuilder = WebClient.builder();
        RestTemplate restTemplate = new RestTemplate();
        Executor continuationExecutor = Runnable::run;
        AiCallMonitor aiCallMonitor = mock(AiCallMonitor.class);

        ChatServiceImpl service = new ChatServiceImpl(
                chatMessageService,
                chatSessionService,
                aiModelService,
                chatLimitService,
                chatRateLimitService,
                fileUploadService,
                chatInterruptService,
                webClientBuilder,
                restTemplate,
                truncationDetectionService,
                webSearchContentService,
                aiCallMonitor,
                mock(ChatTitleService.class),
                mock(NetworkSearchOrchestrator.class),
                new ContentPolicy(),
                new ChatStreamEventWriter(),
                new ChatContextBuilder(chatMessageService, fileUploadService, mock(NetworkSearchOrchestrator.class)),
                continuationExecutor
        );

        String sessionId = "session-1";
        String messageId = "msg-error";
        Long userId = 1001L;

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setModelId(9L);

        ChatMessage targetMessage = new ChatMessage();
        targetMessage.setMessageId(messageId);
        targetMessage.setSessionId(sessionId);
        targetMessage.setUserId(userId);
        targetMessage.setRole("assistant");
        targetMessage.setStatus(3);

        when(chatSessionService.getSessionInfo(sessionId)).thenReturn(session);
        when(chatMessageService.getByMessageId(messageId)).thenReturn(targetMessage);
        when(chatMessageService.deleteMessagesFromPoint(messageId, sessionId)).thenReturn(1);
        when(chatRateLimitService.checkUserChatRateLimit(userId)).thenReturn(true);
        when(chatRateLimitService.getClientIpAddress()).thenReturn("127.0.0.1");
        when(chatRateLimitService.checkIpChatRateLimit(anyString())).thenReturn(true);
        when(chatRateLimitService.checkDailyChatLimit(userId)).thenReturn(false);

        HttpServletResponse response = new MockHttpServletResponse();

        service.processRetryChat(sessionId, messageId, session.getModelId(), false, authentication(userId), response);

        verify(chatMessageService).deleteMessagesFromPoint(messageId, sessionId);
        verify(chatMessageService).sendErrorResponseMessage(
                sessionId,
                userId,
                session.getModelId(),
                "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow."
        );
        InOrder order = inOrder(chatMessageService);
        order.verify(chatMessageService).deleteMessagesFromPoint(messageId, sessionId);
        order.verify(chatMessageService).sendErrorResponseMessage(
                sessionId,
                userId,
                session.getModelId(),
                "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow."
        );
    }

    @Test
    void editResendWhenDailyLimitExceededShouldReplaceUserMessageBeforeSavingNewErrorResponse() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        AiModelService aiModelService = mock(AiModelService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        ChatRateLimitService chatRateLimitService = mock(ChatRateLimitService.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        ChatInterruptService chatInterruptService = mock(ChatInterruptService.class);
        TruncationDetectionService truncationDetectionService = mock(TruncationDetectionService.class);
        WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);
        NetworkAiService networkAiService = mock(NetworkAiService.class);
        WebClient.Builder webClientBuilder = WebClient.builder();
        RestTemplate restTemplate = new RestTemplate();
        Executor continuationExecutor = Runnable::run;
        AiCallMonitor aiCallMonitor = mock(AiCallMonitor.class);

        ChatServiceImpl service = new ChatServiceImpl(
                chatMessageService,
                chatSessionService,
                aiModelService,
                chatLimitService,
                chatRateLimitService,
                fileUploadService,
                chatInterruptService,
                webClientBuilder,
                restTemplate,
                truncationDetectionService,
                webSearchContentService,
                aiCallMonitor,
                mock(ChatTitleService.class),
                mock(NetworkSearchOrchestrator.class),
                new ContentPolicy(),
                new ChatStreamEventWriter(),
                new ChatContextBuilder(chatMessageService, fileUploadService, mock(NetworkSearchOrchestrator.class)),
                continuationExecutor
        );

        String sessionId = "session-edit";
        String messageId = "msg-user";
        String newContent = "新的编辑内容";
        Long userId = 1002L;

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setModelId(8L);

        ChatMessage targetMessage = new ChatMessage();
        targetMessage.setMessageId(messageId);
        targetMessage.setSessionId(sessionId);
        targetMessage.setUserId(userId);
        targetMessage.setRole("user");
        targetMessage.setStatus(1);

        when(chatSessionService.getSessionInfo(sessionId)).thenReturn(session);
        when(chatMessageService.getByMessageId(messageId)).thenReturn(targetMessage);
        when(chatRateLimitService.checkUserChatRateLimit(userId)).thenReturn(true);
        when(chatRateLimitService.getClientIpAddress()).thenReturn("127.0.0.1");
        when(chatRateLimitService.checkIpChatRateLimit(anyString())).thenReturn(true);
        when(chatRateLimitService.checkDailyChatLimit(userId)).thenReturn(false);
        when(fileUploadService.getFileIdsByMessageId(messageId)).thenReturn(null);
        when(chatMessageService.deleteMessagesFromPoint(messageId, sessionId)).thenReturn(1);
        when(chatMessageService.save(argThat(message ->
                message != null
                        && "user".equals(message.getRole())
                        && newContent.equals(message.getContent())
                        && sessionId.equals(message.getSessionId())
                        && userId.equals(message.getUserId())
        ))).thenReturn(true);

        HttpServletResponse response = new MockHttpServletResponse();

        service.processEditResendChat(sessionId, messageId, newContent, null, session.getModelId(), false, authentication(userId), response);

        verify(chatMessageService).deleteMessagesFromPoint(messageId, sessionId);
        verify(chatMessageService).save(argThat(message -> {
            assertEquals("user", message.getRole());
            assertEquals(newContent, message.getContent());
            assertEquals(sessionId, message.getSessionId());
            assertEquals(userId, message.getUserId());
            return true;
        }));
        verify(chatMessageService).sendErrorResponseMessage(
                sessionId,
                userId,
                session.getModelId(),
                "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow."
        );
        InOrder order = inOrder(chatMessageService);
        order.verify(chatMessageService).deleteMessagesFromPoint(messageId, sessionId);
        order.verify(chatMessageService).save(argThat(message ->
                message != null
                        && "user".equals(message.getRole())
                        && newContent.equals(message.getContent())
        ));
        order.verify(chatMessageService).sendErrorResponseMessage(
                sessionId,
                userId,
                session.getModelId(),
                "Your daily usage limit for this model has been reached. Please try another model or wait until tomorrow."
        );
    }

    private Authentication authentication(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("tester");
        user.setPassword("secret");
        user.setStatus(1);
        user.setRole("user");
        UserDetailsServiceImpl.CustomUserDetails principal = new UserDetailsServiceImpl.CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private com.instser.chat.vo.ChatLimitVO permissionOk() {
        com.instser.chat.vo.ChatLimitVO permission = com.instser.chat.vo.ChatLimitVO.success();
        permission.setPackageName("pkg");
        permission.setLimitType("daily");
        return permission;
    }
}
