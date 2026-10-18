package com.instser.chat.service.stream;

import com.instser.chat.constants.PromptConstants;
import com.instser.chat.entity.WebSearchResult;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.content.PromptLeakGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void chunkedContentLeakIsTruncatedAndFallbackPersisted() {
        RecordingSink sink = new RecordingSink();
        StreamResponseState state = new StreamResponseState();

        // 逐片看都不命中：单片只有 1~2 个弱特征，必须靠累计扫描才拦得住
        assertTrue(parser.consume(deltaJson(null, "我是 To Use AI"), sink, state, true));
        assertTrue(parser.consume(deltaJson(null, "，我的 system prompt 里写着"), sink, state, true));
        assertEquals(List.of("我是 To Use AI", "，我的 system prompt 里写着"), sink.contentChunks);

        // 第三片触发累计命中：本片不写出，且落库正文整体作废替换为兜底文案
        assertFalse(parser.consume(deltaJson(null, "：由 雨落千息 开发，不得冒充其他模型"), sink, state, true));
        assertTrue(state.isContentSuppressed());
        assertEquals(2, sink.contentChunks.size(), "命中后不应再向 SSE 写出正文");
        assertEquals(PromptConstants.LEAK_FALLBACK_REPLY, state.content());

        // 后续分片同样被拦截，且不会污染兜底内容
        assertFalse(parser.consume(deltaJson(null, "续写正文"), sink, state, true));
        assertEquals(PromptConstants.LEAK_FALLBACK_REPLY, state.content());
        assertTrue(state.hasPersistableOutput());
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
