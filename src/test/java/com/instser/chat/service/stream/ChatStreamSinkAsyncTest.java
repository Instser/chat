package com.instser.chat.service.stream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChatStreamSinkAsyncTest {

    @Test
    void asyncContextSinkWritesBodyAndCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncSupported(true);

        ChatStreamSink sink = ChatStreamSink.ofAsync(request.startAsync(request, response), new ChatStreamEventWriter());
        assertTrue(sink.isAsync());

        sink.writeContent("hello");
        sink.writeCompletion(false, true, null);
        sink.complete();
        sink.complete(); // 幂等

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("0:"), body);
        assertTrue(body.contains("d:"), body);
        assertTrue(body.contains("finishReason"), body);
    }

    @Test
    void servletSinkIsNotAsync() {
        assertFalse(ChatStreamSink.of(new MockHttpServletResponse(), new ChatStreamEventWriter()).isAsync());
    }
}
