package com.instser.chat.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelChatProbeSupportTest {

    @Test
    void acceptPureDigitOne() {
        assertTrue(ModelChatProbeSupport.isAcceptableProbeReply("1"));
        assertTrue(ModelChatProbeSupport.isAcceptableProbeReply(" 1\n"));
        assertTrue(ModelChatProbeSupport.isAcceptableProbeReply("1."));
    }

    @Test
    void rejectEmptyOrUnrelated() {
        assertFalse(ModelChatProbeSupport.isAcceptableProbeReply(""));
        assertFalse(ModelChatProbeSupport.isAcceptableProbeReply("hello"));
        assertFalse(ModelChatProbeSupport.isAcceptableProbeReply(null));
    }

    @Test
    void evaluateOpenAiSuccessWithContentOne() {
        String body = """
                {"id":"x","choices":[{"message":{"role":"assistant","content":"1"},"finish_reason":"stop"}]}
                """;
        var check = ModelChatProbeSupport.evaluateResponse(true, 200, body, "openai-chat-completions");
        assertTrue(check.httpOk());
        assertTrue(check.contentOk());
        assertEquals("1", check.extractedText());
    }

    @Test
    void evaluateHttpOkButEmptyContentIsDown() {
        String body = """
                {"id":"x","choices":[{"message":{"role":"assistant","content":""},"finish_reason":"length"}]}
                """;
        var check = ModelChatProbeSupport.evaluateResponse(true, 200, body, "openai-chat-completions");
        assertTrue(check.httpOk());
        assertFalse(check.contentOk());
        assertTrue(check.message().contains("无聊天内容") || check.message().contains("finish_reason"));
    }

    @Test
    void evaluateErrorFieldEvenIfHttp200() {
        String body = """
                {"error":{"message":"model not available","code":"model_not_found"}}
                """;
        var check = ModelChatProbeSupport.evaluateResponse(true, 200, body, "openai-chat-completions");
        assertTrue(check.httpOk());
        assertFalse(check.contentOk());
        assertTrue(check.hasErrorField());
    }

    @Test
    void evaluateGeminiText() {
        String body = """
                {"candidates":[{"content":{"parts":[{"text":"1"}]}}]}
                """;
        var check = ModelChatProbeSupport.evaluateResponse(true, 200, body, "gemini-generateContent");
        assertTrue(check.contentOk());
        assertEquals("1", check.extractedText());
    }
}
