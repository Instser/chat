package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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

class ChatTitleServiceImplTest {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatTitleServiceImpl service = new ChatTitleServiceImpl(aiModelService, chatSessionService, restTemplate);

    @Test
    void buildTitleMessagesShouldUseOnlyOriginalUserMessagesAndCurrentAiResponse() {
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
        when(aiModelService.getById(4L)).thenReturn(null);

        String generatedTitle = service.generateSessionTitle("session-title", List.of(
                Map.of("role", "user", "content", "hello")
        ));

        assertNull(generatedTitle);
        verify(aiModelService).getById(4L);
        verifyNoInteractions(chatSessionService);
    }
}
