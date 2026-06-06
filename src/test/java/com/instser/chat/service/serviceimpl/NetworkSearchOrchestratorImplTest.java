package com.instser.chat.service.serviceimpl;

import com.instser.chat.dto.NetworkSearchProcessResult;
import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.entity.GeminiSource;
import com.instser.chat.entity.GeminiTextSegment;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.NetworkAiService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkSearchOrchestratorImplTest {

    private final NetworkAiService networkAiService = mock(NetworkAiService.class);
    private final NetworkSearchOrchestratorImpl orchestrator = new NetworkSearchOrchestratorImpl(networkAiService);

    @Test
    void processNetworkContentShouldAppendFormattedGeminiResponseAndReturnSearchResults() {
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
        when(networkAiService.queryWithGeminiGrounding(contains("用户: What changed?"))).thenReturn(response);

        NetworkSearchProcessResult result = orchestrator.processNetworkContent(messages, "What changed?");

        assertNotSame(messages, result.getMessages());
        assertEquals(2, result.getMessages().size());
        String updatedUserContent = result.getMessages().get(1).get("content");
        assertTrue(updatedUserContent.startsWith("What changed?\n\nBelow is the networking content you get from your own search:\n\n"));
        assertTrue(updatedUserContent.contains("Fresh answer"));
        assertTrue(updatedUserContent.contains("Search Queries:\n- latest query"));
        assertTrue(updatedUserContent.contains("Sources:\n[1] Source A - https://example.test/a"));
        assertTrue(updatedUserContent.contains("Citation Details:\n\"Evidence sentence\" (Sources: [1] )"));
        assertEquals(1, result.getSearchResults().size());
        WebSearchResult searchResult = result.getSearchResults().get(0);
        assertEquals("Source A", searchResult.getTitle());
        assertEquals("https://example.test/a", searchResult.getUrl());
        assertNull(searchResult.getContent());
    }

    @Test
    void processNetworkContentShouldReturnOriginalMessagesAndEmptyResultsWhenGeminiFails() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "query"));
        when(networkAiService.queryWithGeminiGrounding(contains("用户: query"))).thenThrow(new RuntimeException("network down"));

        NetworkSearchProcessResult result = orchestrator.processNetworkContent(messages, "query");

        assertSame(messages, result.getMessages());
        assertTrue(result.getSearchResults().isEmpty());
    }
}
