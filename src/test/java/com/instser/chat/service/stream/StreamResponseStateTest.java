package com.instser.chat.service.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamResponseStateTest {

    @Test
    void interruptCanBeSavedOnlyOnce() {
        StreamResponseState state = new StreamResponseState();
        assertFalse(state.canSaveInterrupt());

        state.markInterrupted();
        assertTrue(state.canSaveInterrupt());

        state.markInterruptSaved();
        assertFalse(state.canSaveInterrupt());
    }

    @Test
    void completionHandledGuardsDuplicateFinish() {
        StreamResponseState state = new StreamResponseState();
        assertTrue(state.canHandleCompletion());

        state.markCompletionHandled();
        assertFalse(state.canHandleCompletion());
        assertTrue(state.isCompletionHandled());
    }

    @Test
    void tryHandleCompletionSucceedsOnlyOnce() {
        StreamResponseState state = new StreamResponseState();
        assertTrue(state.tryHandleCompletion());
        assertFalse(state.tryHandleCompletion());
        assertTrue(state.isCompletionHandled());
        assertFalse(state.canHandleCompletion());
    }

    @Test
    void firstTokenMarksContentAndRealResponseHelpers() {
        StreamResponseState state = new StreamResponseState();
        state.markFirstToken();
        assertTrue(state.isFirstTokenMarked());
        assertTrue(state.hasContentSent());

        state.markRealAiResponse();
        assertTrue(state.isRealAiResponse());
        assertTrue(state.hasContentSent());
    }
}
