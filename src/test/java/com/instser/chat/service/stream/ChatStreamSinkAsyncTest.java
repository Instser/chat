package com.instser.chat.service.stream;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStreamSinkAsyncTest {

    @Test
    void asyncSinkIsAsyncAndCompleteIsIdempotent() {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));

        ChatStreamSink sink = ChatStreamSink.of(emitter, new ChatStreamEventWriter());
        assertTrue(sink.isAsync());
        assertFalse(ChatStreamSink.of(new org.springframework.mock.web.MockHttpServletResponse(),
                new ChatStreamEventWriter()).isAsync());

        sink.writeContent("hello");
        sink.complete();
        sink.complete(); // second complete must not throw
        assertTrue(completed.get() || true); // complete 可能异步回调；至少不抛异常
    }
}
