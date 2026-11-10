package org.fxt.freexmltoolkit.service.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SseReaderTest {

    private static SseReader reader(String text) {
        return new SseReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void eventsDataCommentsAndEnd() throws Exception {
        SseReader r = reader(": keep-alive\n\nevent: message_start\ndata: {\"a\":1}\n\ndata: line1\ndata: line2\n\r\n"
                + "data:no-space\n\ndata: [DONE]\n\n");
        SseReader.Event first = r.next();
        assertEquals("message_start", first.event());
        assertEquals("{\"a\":1}", first.data());
        SseReader.Event second = r.next();
        assertEquals("message", second.event());
        assertEquals("line1\nline2", second.data());
        assertEquals("no-space", r.next().data());
        assertEquals("[DONE]", r.next().data());
        assertNull(r.next());
    }

    @Test
    void aLastEventWithoutBlankLineIsReturned() throws Exception {
        SseReader r = reader("data: x");
        assertEquals("x", r.next().data());
        assertNull(r.next());
    }

    @Test
    void crlfLineEndings() throws Exception {
        SseReader r = reader("event: e\r\ndata: d\r\n\r\n");
        SseReader.Event e = r.next();
        assertEquals("e", e.event());
        assertEquals("d", e.data());
    }
}
