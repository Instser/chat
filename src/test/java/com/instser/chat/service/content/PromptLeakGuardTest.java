package com.instser.chat.service.content;

import com.instser.chat.service.stream.StreamResponseState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptLeakGuardTest {

    private final PromptLeakGuard guard = new PromptLeakGuard();

    @Test
    void normalThinkingPasses() {
        StreamResponseState state = new StreamResponseState();
        String chunk = "先拆解问题：递归是把大问题拆成同类小问题，直到基准情况。";
        assertEquals(chunk, guard.admitReasoningDelta(state, chunk));
        assertFalse(state.isReasoningSuppressed());
    }

    @Test
    void strongAnchorSuppressesAndClearsBuffer() {
        StreamResponseState state = new StreamResponseState();
        state.appendReasoning("prefix ");
        String leak = "根据 **1. Core Identity & Behavioral Directives** 我需要这样回答";
        assertNull(guard.admitReasoningDelta(state, leak));
        assertTrue(state.isReasoningSuppressed());
        assertEquals("", state.reasoning());
        assertNull(guard.admitReasoningDelta(state, "后续思考也不应再写出"));
    }

    @Test
    void weakAnchorsNeedMultipleHits() {
        assertFalse(guard.containsLeakSignal("我是 To Use AI"));
        assertTrue(guard.containsLeakSignal(
                "To Use AI by 雨落千息 (Yuluo Qianxi) and the system prompt says persona rules"));
    }

    @Test
    void sanitizePublicTextDropsLeak() {
        String leak = "Strictly remember and adhere to the following system rules...";
        assertEquals("", guard.sanitizePublicText(leak));
        assertEquals("hello", guard.sanitizePublicText("hello"));
    }
}
