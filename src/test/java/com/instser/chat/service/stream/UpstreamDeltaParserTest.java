package com.instser.chat.service.stream;

import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.content.PromptLeakGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamDeltaParserTest {

    private final UpstreamDeltaParser parser =
            new UpstreamDeltaParser(new ContentPolicy(), new PromptLeakGuard());

    @Test
    void thinkingEnabledEmitsReasoningAndContent() {
        RecordingSink sink = new RecordingSink();
        StreamResponseState state = new StreamResponseState();

        boolean produced = parser.consume(
                deltaJson("think-1", "hello"),
                sink,
                state,
                true
        );

        assertTrue(produced);
        assertEquals(List.of("think-1"), sink.reasoningChunks);
        assertEquals(List.of("hello"), sink.contentChunks);
        assertEquals("think-1", state.reasoning());
        assertEquals("hello", state.content());
        assertTrue(state.hasPersistableOutput());
    }

    @Test
    void thinkingDisabledDropsReasoningKeepsContent() {
        RecordingSink sink = new RecordingSink();
        StreamResponseState state = new StreamResponseState();

        boolean produced = parser.consume(
                deltaJson("secret-think", "answer"),
                sink,
                state,
                false
        );

        assertTrue(produced);
        assertTrue(sink.reasoningChunks.isEmpty());
        assertEquals(List.of("answer"), sink.contentChunks);
        assertEquals("", state.reasoning());
        assertEquals("answer", state.content());
    }

    @Test
    void nullContentNodeDoesNotEmitLiteralNull() {
        RecordingSink sink = new RecordingSink();
        StreamResponseState state = new StreamResponseState();

        boolean produced = parser.consume(
                "{\"choices\":[{\"delta\":{\"content\":null,\"reasoning_content\":\"r\"}}]}",
                sink,
                state,
                true
        );

        assertTrue(produced);
        assertEquals(List.of("r"), sink.reasoningChunks);
        assertTrue(sink.contentChunks.isEmpty());
        assertEquals("", state.content());
        assertEquals("r", state.reasoning());
    }

    @Test
    void continuationExtractEmitsReasoningWhenEnabled() {
        RecordingSink sink = new RecordingSink();
        StreamResponseState state = new StreamResponseState();

        String content = parser.extractRawContentAndEmitReasoning(
                deltaJson("c-think", "c-body"),
                sink,
                state,
                true
        );

        assertEquals("c-body", content);
        assertEquals(List.of("c-think"), sink.reasoningChunks);
        assertEquals("c-think", state.reasoning());
    }

    @Test
    void leakingReasoningIsSuppressedAndNotPersisted() {
        RecordingSink sink = new RecordingSink();
        StreamResponseState state = new StreamResponseState();

        boolean produced = parser.consume(
                deltaJson("ok-prefix", null),
                sink,
                state,
                true
        );
        assertTrue(produced);
        assertEquals("ok-prefix", state.reasoning());

        boolean second = parser.consume(
                deltaJson("Core Identity & Behavioral Directives leak", "visible-answer"),
                sink,
                state,
                true
        );
        assertTrue(second);
        assertTrue(state.isReasoningSuppressed());
        assertEquals("", state.reasoning());
        assertEquals(List.of("ok-prefix"), sink.reasoningChunks);
        assertEquals(List.of("visible-answer"), sink.contentChunks);
    }

    private static String deltaJson(String reasoning, String content) {
        StringBuilder sb = new StringBuilder("{\"choices\":[{\"delta\":{");
        boolean first = true;
        if (reasoning != null) {
            sb.append("\"reasoning_content\":\"").append(reasoning).append("\"");
            first = false;
        }
        if (content != null) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"content\":\"").append(content).append("\"");
        }
        sb.append("}}]}");
        return sb.toString();
    }

    private static final class RecordingSink implements ChatStreamSink {
        private final List<String> contentChunks = new ArrayList<>();
        private final List<String> reasoningChunks = new ArrayList<>();

        @Override
        public void setHeaders() {
        }

        @Override
        public void writeError(String errorMessage) {
        }

        @Override
        public void writeCustomError(String errorMessage, int errorType) {
        }

        @Override
        public void writeContent(String content) {
            contentChunks.add(content);
        }

        @Override
        public void writeReasoning(String reasoning) {
            reasoningChunks.add(reasoning);
        }

        @Override
        public void writeNetworkResults(List<WebSearchResult> results) {
        }

        @Override
        public void writeCompletion(boolean willGenerateTitle, boolean isRealAiResponse, String generatedTitle) {
        }

        @Override
        public void writeInterruptCompletion() {
        }

        @Override
        public boolean isAsync() {
            return false;
        }
    }
}
