package com.instser.chat.service.serviceimpl;

import com.instser.chat.service.stream.AiRequestLogger;

import com.instser.chat.service.usage.ChatUsageRecorder;

import com.instser.chat.service.completion.StreamCompletionCoordinator;

import com.instser.chat.service.persistence.ChatPersistenceService;

import com.instser.chat.service.validation.ChatRequestValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.entity.GeminiSource;
import com.instser.chat.entity.GeminiTextSegment;
import com.instser.chat.entity.SearchResponse;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.monitor.AiCallMonitor;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatRateLimitService;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatTitleService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.GeminiNetworkService;
import com.instser.chat.service.NetworkAiService;
import com.instser.chat.service.NetworkSearchOrchestrator;
import com.instser.chat.service.TruncationDetectionService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.continuation.ChatContinuationService;
import com.instser.chat.service.stream.AiStreamProcessor;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import com.instser.chat.service.context.ChatContextBuilder;
import com.instser.chat.service.prepare.ChatPrepareHandler;
import com.instser.chat.service.prepare.EditResendPrepareHandler;
import com.instser.chat.service.prepare.RetryPrepareHandler;
import com.instser.chat.service.completion.AiResponseCompletionHandler;
import com.instser.chat.service.ai.OpenAiCompatibleChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChatServiceImplStageOneCharacterizationTest {

    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final AiModelService aiModelService = mock(AiModelService.class);
    private final ChatLimitService chatLimitService = mock(ChatLimitService.class);
    private final ChatRateLimitService chatRateLimitService = mock(ChatRateLimitService.class);
    private final FileUploadService fileUploadService = mock(FileUploadService.class);
    private final ChatInterruptService chatInterruptService = mock(ChatInterruptService.class);
    private final TruncationDetectionService truncationDetectionService = mock(TruncationDetectionService.class);
    private final WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final Executor continuationExecutor = Runnable::run;
    private final AiCallMonitor aiCallMonitor = mock(AiCallMonitor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatTitleService chatTitleService = new ChatTitleServiceImpl(aiModelService, chatSessionService, restTemplate);

    @Test
    void buildTitleMessagesShouldUseOnlyOriginalUserMessagesAndCurrentAiResponse() {
        ChatTitleService service = chatTitleService;
        ChatRequestDTO request = new ChatRequestDTO();
        request.setOriginalMessages(List.of(
                Map.of("role", "user", "content", "first user question"),
                Map.of("role", "assistant", "content", "old assistant answer"),
                Map.of("role", "user", "content", "second user question")
        ));

        List<Map<String, String>> titleMessages = service.buildTitleMessages(request, "current ai answer");

        assertEquals(3, titleMessages.size());
        assertEquals(Map.of("role", "user", "content", "first user question"), titleMessages.get(0));
        assertEquals(Map.of("role", "user", "content", "second user question"), titleMessages.get(1));
        assertEquals(Map.of("role", "assistant", "content", "current ai answer"), titleMessages.get(2));
        assertFalse(titleMessages.stream().anyMatch(message -> "old assistant answer".equals(message.get("content"))));
    }

    @Test
    void generateSessionTitleShouldUseFixedModel4CleanTitleAndPersistIt() throws Exception {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ChatServiceImpl service = createService(mock(NetworkAiService.class));
        String sessionId = "session-title";

        AiModel titleModel = new AiModel();
        titleModel.setId(4L);
        titleModel.setModelCode("title-model");
        titleModel.setModelName("Title Model");
        titleModel.setProvider("Gemini");
        titleModel.setApiKey("title-api-key");
        titleModel.setBaseUrl("https://example.test/v1/chat/completions");
        titleModel.setIsAvailable(1);

        when(aiModelService.getById(4L)).thenReturn(titleModel);
        when(chatSessionService.updateSessionTitle(sessionId, "Refactor Roadmap")).thenReturn(true);

        server.expect(requestTo(titleModel.getBaseUrl()))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer title-api-key"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    JsonNode root = objectMapper.readTree(body);
                    assertEquals("title-model", root.get("model").asText());
                    assertEquals(false, root.get("stream").asBoolean());
                    assertEquals(50, root.get("max_tokens").asInt());
                    assertEquals("none", root.get("reasoning_effort").asText());
                    assertTrue(root.get("messages").get(0).get("content").asText().contains("User: how to decouple"));
                    assertTrue(root.get("messages").get(0).get("content").asText().contains("AI: extract services"));
                })
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"\\"Refactor Roadmap\\""}}]}
                        """, APPLICATION_JSON));

        String generatedTitle = service.generateSessionTitle(sessionId, List.of(
                Map.of("role", "user", "content", "how to decouple"),
                Map.of("role", "assistant", "content", "extract services")
        ));

        assertEquals("Refactor Roadmap", generatedTitle);
        InOrder order = inOrder(aiModelService, chatSessionService);
        order.verify(aiModelService).getById(4L);
        order.verify(chatSessionService).updateSessionTitle(sessionId, "Refactor Roadmap");
        server.verify();
    }

    @Test
    void generateSessionTitleShouldSkipAiCallWhenFixedModel4Unavailable() {
        ChatServiceImpl service = createService(mock(NetworkAiService.class));

        when(aiModelService.getById(4L)).thenReturn(null);

        String generatedTitle = service.generateSessionTitle("session-title", List.of(
                Map.of("role", "user", "content", "hello")
        ));

        assertNull(generatedTitle);
        verify(aiModelService).getById(4L);
        verifyNoInteractions(chatSessionService);
    }

    @Test
    void processNetworkContentShouldAppendFormattedGeminiResponseAndReturnSearchResults() {
        GeminiNetworkService geminiNetworkService = mock(GeminiNetworkService.class);
        NetworkSearchOrchestrator orchestrator = new NetworkSearchOrchestratorImpl(new NetworkAiServiceImpl(geminiNetworkService));
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "assistant", "content", "previous answer"));
        messages.add(new java.util.HashMap<>(Map.of("role", "user", "content", "What changed?")));

        GeminiSource source = new GeminiSource(0, "Source A", "https://example.test/a");
        GeminiTextSegment segment = new GeminiTextSegment(0, "Evidence sentence", 0, 17, List.of(0), List.of(source));
        GeminiGroundingResponse response = new GeminiGroundingResponse(
                "Fresh answer",
                List.of("latest query"),
                1,
                List.of(source),
                List.of(segment)
        );
        when(geminiNetworkService.queryWithGrounding(any())).thenReturn(response);

        var networkResult = orchestrator.processNetworkContent(messages, "What changed?");
        List<Map<String, String>> updatedMessages = networkResult.getMessages();
        List<WebSearchResult> searchResults = networkResult.getSearchResults();

        assertNotSame(messages, updatedMessages);
        assertEquals(2, updatedMessages.size());
        String updatedUserContent = updatedMessages.get(1).get("content");
        assertTrue(updatedUserContent.startsWith("What changed?\n\nBelow is the networking content you get from your own search:\n\n"));
        assertTrue(updatedUserContent.contains("Fresh answer"));
        assertTrue(updatedUserContent.contains("Search Queries:\n- latest query"));
        assertTrue(updatedUserContent.contains("Sources:\n[1] Source A - https://example.test/a"));
        assertTrue(updatedUserContent.contains("Citation Details:\n\"Evidence sentence\" (Sources: [1] )"));
        assertEquals(1, searchResults.size());
        assertEquals("Source A", searchResults.get(0).getTitle());
        assertEquals("https://example.test/a", searchResults.get(0).getUrl());
        assertNull(searchResults.get(0).getContent());
    }

    @Test
    void processNetworkContentShouldReturnOriginalMessagesAndEmptyResultsWhenGeminiFails() {
        GeminiNetworkService geminiNetworkService = mock(GeminiNetworkService.class);
        NetworkSearchOrchestrator orchestrator = new NetworkSearchOrchestratorImpl(new NetworkAiServiceImpl(geminiNetworkService));
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "query"));

        when(geminiNetworkService.queryWithGrounding(any())).thenThrow(new RuntimeException("network down"));

        var networkResult = orchestrator.processNetworkContent(messages, "query");
        List<Map<String, String>> updatedMessages = networkResult.getMessages();
        List<WebSearchResult> searchResults = networkResult.getSearchResults();

        assertSame(messages, updatedMessages);
        assertTrue(searchResults.isEmpty());
    }

    @Test
    void saveWebSearchResultsToAiResponseShouldPersistResultsAndMarkAiMessageWhenNetworkEnabled() {
        AiResponseCompletionHandler handler = new AiResponseCompletionHandler(
                chatMessageService, chatLimitService, chatTitleService, webSearchContentService);
        ChatRequestDTO request = new ChatRequestDTO();
        request.setNetwork(true);
        request.setUserId(1001L);
        request.setSessionId("session-network");
        request.setNetworkSearchResults(List.of(new WebSearchResult(
                "https://example.test/a",
                "Source A",
                null,
                null,
                null,
                null
        )));

        when(webSearchContentService.saveWebSearchContent(
                eq("ai-message-id"),
                any(SearchResponse.class),
                eq(1001L),
                eq("session-network")
        )).thenReturn(true);
        when(chatMessageService.updateWebSearchFlag("ai-message-id", true)).thenReturn(true);

        handler.bindNetworkResults(request, "ai-message-id");

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        InOrder order = inOrder(webSearchContentService, chatMessageService);
        order.verify(webSearchContentService).saveWebSearchContent(
                eq("ai-message-id"),
                responseCaptor.capture(),
                eq(1001L),
                eq("session-network")
        );
        assertEquals(1, responseCaptor.getValue().getResults().size());
        assertEquals("Source A", responseCaptor.getValue().getResults().get(0).getTitle());
        order.verify(chatMessageService).updateWebSearchFlag("ai-message-id", true);
    }

    @Test
    void saveWebSearchResultsToAiResponseShouldDoNothingWhenNetworkDisabled() {
        AiResponseCompletionHandler handler = new AiResponseCompletionHandler(
                chatMessageService, chatLimitService, chatTitleService, webSearchContentService);
        ChatRequestDTO request = new ChatRequestDTO();
        request.setNetwork(false);
        request.setUserId(1001L);
        request.setSessionId("session-network");
        request.setNetworkSearchResults(List.of(new WebSearchResult(
                "https://example.test/a",
                "Source A",
                null,
                null,
                null,
                null
        )));

        handler.bindNetworkResults(request, "ai-message-id");

        verify(webSearchContentService, never()).saveWebSearchContent(any(), any(), any(), any());
        verify(chatMessageService, never()).updateWebSearchFlag(any(), eq(true));
    }

    private ChatServiceImpl createService(NetworkAiService networkAiService) {
        NetworkSearchOrchestrator networkSearchOrchestrator = new NetworkSearchOrchestratorImpl(networkAiService);
        return new ChatServiceImpl(
                chatMessageService,
                chatSessionService,
                aiModelService,
                chatTitleService,
                new ChatContextBuilder(chatMessageService, fileUploadService, networkSearchOrchestrator),
                new ChatPrepareHandler(chatMessageService, fileUploadService, new ChatContextBuilder(chatMessageService, fileUploadService, networkSearchOrchestrator)),
                new RetryPrepareHandler(chatMessageService, fileUploadService, new ChatContextBuilder(chatMessageService, fileUploadService, networkSearchOrchestrator)),
                new EditResendPrepareHandler(chatMessageService, fileUploadService, new ChatContextBuilder(chatMessageService, fileUploadService, networkSearchOrchestrator)),
                new ChatRequestValidator(aiModelService, chatLimitService, chatRateLimitService, chatInterruptService, chatSessionService, chatMessageService, new ChatPersistenceService(chatMessageService, chatSessionService, fileUploadService, chatLimitService, new AiResponseCompletionHandler(chatMessageService, chatLimitService, chatTitleService, webSearchContentService))),
                new ChatPersistenceService(chatMessageService, chatSessionService, fileUploadService, chatLimitService, new AiResponseCompletionHandler(chatMessageService, chatLimitService, chatTitleService, webSearchContentService)),
                new AiStreamProcessor(new OpenAiCompatibleChatClient(WebClient.builder()), new com.instser.chat.service.stream.UpstreamDeltaParser(new ContentPolicy()), chatInterruptService, aiCallMonitor, new com.instser.chat.service.stream.StreamSubscriptionRegistry()),
                new StreamCompletionCoordinator(truncationDetectionService, mock(ChatContinuationService.class), new AiResponseCompletionHandler(chatMessageService, chatLimitService, chatTitleService, webSearchContentService), new ChatUsageRecorder()),
                new AiRequestLogger()
        );
    }
}
