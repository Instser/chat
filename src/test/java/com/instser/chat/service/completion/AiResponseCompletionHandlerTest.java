package com.instser.chat.service.completion;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.MessageSaveResult;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.SearchResponse;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.ChatTitleService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import com.instser.chat.service.stream.ChatStreamSink;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiResponseCompletionHandlerTest {

    @Test
    void persistAssistantContentShouldRecordTokenUsageWhenPresent() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        when(chatMessageService.sendMessageWithTokenInfo("s1", "assistant", "hi", 1L, 2L))
                .thenReturn(MessageSaveResult.success(10L, "uuid-1", 12));
        when(chatLimitService.recordTokenUsage(1L, 12)).thenReturn(true);

        AiResponseCompletionHandler handler = new AiResponseCompletionHandler(
                chatMessageService,
                chatLimitService,
                mock(ChatTitleService.class),
                mock(WebSearchContentService.class)
        );

        String id = handler.persistAssistantContent("s1", 1L, 2L, "hi");
        assertEquals("uuid-1", id);
        verify(chatLimitService).recordTokenUsage(1L, 12);
    }

    @Test
    void bindNetworkResultsShouldSkipWhenNetworkDisabled() {
        WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        AiResponseCompletionHandler handler = new AiResponseCompletionHandler(
                chatMessageService,
                mock(ChatLimitService.class),
                mock(ChatTitleService.class),
                webSearchContentService
        );

        ChatRequestDTO request = new ChatRequestDTO();
        request.setNetwork(false);
        request.setNetworkSearchResults(List.of(new WebSearchResult("https://a", "t", null, null, null, null)));

        handler.bindNetworkResults(request, "ai-1");
        verify(webSearchContentService, never()).saveWebSearchContent(anyString(), any(), anyLong(), anyString());
        verify(chatMessageService, never()).updateWebSearchFlag(anyString(), anyBoolean());
    }

    @Test
    void completeSideEffectsShouldPersistBindTitleAndWriteCompletion() throws Exception {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        ChatTitleService chatTitleService = mock(ChatTitleService.class);
        WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);

        when(chatMessageService.sendMessageWithTokenInfo(eq("s1"), eq("assistant"), anyString(), eq(9L), eq(3L)))
                .thenReturn(MessageSaveResult.success(1L, "ai-msg", 5));
        when(chatLimitService.recordTokenUsage(9L, 5)).thenReturn(true);
        when(webSearchContentService.saveWebSearchContent(eq("ai-msg"), any(SearchResponse.class), eq(9L), eq("s1")))
                .thenReturn(true);
        when(chatTitleService.buildTitleMessages(org.mockito.ArgumentMatchers.<java.util.List<java.util.Map<String, String>>>any(), anyString()))
                .thenReturn(List.of(MapLike.user("u"), MapLike.assistant("a")));
        when(chatTitleService.generateSessionTitle(eq("s1"), any())).thenReturn("Nice Title");

        AiResponseCompletionHandler handler = new AiResponseCompletionHandler(
                chatMessageService,
                chatLimitService,
                chatTitleService,
                webSearchContentService
        );

        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("s1");
        request.setUserId(9L);
        request.setNetwork(true);
        request.setNetworkSearchResults(List.of(new WebSearchResult("https://a", "t", null, null, null, null)));
        request.setOriginalMessages(List.of(MapLike.user("u")));

        AiModel model = new AiModel();
        model.setId(3L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        String messageId = handler.completeSideEffects(ChatStreamSink.of(response, new ChatStreamEventWriter()), request, model, true, true, "answer", false);

        assertEquals("ai-msg", messageId);
        verify(webSearchContentService).saveWebSearchContent(eq("ai-msg"), any(SearchResponse.class), eq(9L), eq("s1"));
        verify(chatMessageService).updateWebSearchFlag("ai-msg", true);
        assertTrue(response.getContentAsString().contains("d:"));
        assertTrue(response.getContentAsString().contains("Nice Title") || response.getContentAsString().contains("title"));
    }

    @Test
    void completeSideEffectsWhenInterruptedShouldPersistButNotWriteNormalCompletionFrame() throws Exception {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ChatLimitService chatLimitService = mock(ChatLimitService.class);
        ChatTitleService chatTitleService = mock(ChatTitleService.class);
        WebSearchContentService webSearchContentService = mock(WebSearchContentService.class);

        when(chatMessageService.sendMessageWithTokenInfo(eq("s1"), eq("assistant"), anyString(), eq(9L), eq(3L)))
                .thenReturn(MessageSaveResult.success(1L, "ai-msg-int", 5));
        when(chatLimitService.recordTokenUsage(9L, 5)).thenReturn(true);
        when(chatLimitService.recordModelChatUsage(9L, "3")).thenReturn(true);

        AiResponseCompletionHandler handler = new AiResponseCompletionHandler(
                chatMessageService,
                chatLimitService,
                chatTitleService,
                webSearchContentService
        );

        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("s1");
        request.setUserId(9L);
        request.setNetwork(true);
        request.setNetworkSearchResults(List.of(new WebSearchResult("https://a", "t", null, null, null, null)));

        AiModel model = new AiModel();
        model.setId(3L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        String messageId = handler.completeSideEffects(
                ChatStreamSink.of(response, new ChatStreamEventWriter()),
                request, model, true, true, "partial answer", true);

        assertEquals("ai-msg-int", messageId);
        // 中断内容应落库并带 [已中断] 标记
        verify(chatMessageService).sendMessageWithTokenInfo(eq("s1"), eq("assistant"),
                eq("partial answer [已中断]"), eq(9L), eq(3L));
        // 中断不绑联网
        verify(webSearchContentService, never()).saveWebSearchContent(anyString(), any(), anyLong(), anyString());
        // 禁止再写普通 d:stop / 任何 d: 完成帧（中断帧由流层单独写）
        assertEquals("", response.getContentAsString());
        verify(chatTitleService, never()).generateSessionTitle(anyString(), any());
    }

    /** tiny map helper without importing extra utils */
    private static final class MapLike {
        static java.util.Map<String, String> user(String c) {
            return java.util.Map.of("role", "user", "content", c);
        }

        static java.util.Map<String, String> assistant(String c) {
            return java.util.Map.of("role", "assistant", "content", c);
        }
    }
}
