package org.fxt.freexmltoolkit.service.ai.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads a {@code text/event-stream}: events separated by blank lines, {@code event:} names, {@code data:} lines
 * (several are joined with a newline), comments starting with {@code :} ignored.
 */
public final class SseReader implements AutoCloseable {

    /** One event; {@code event} is {@code "message"} when the stream names none. */
    public record Event(String event, String data) {
    }

    private final BufferedReader reader;

    public SseReader(InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** The next event, or {@code null} at the end of the stream. */
    public Event next() throws IOException {
        String event = null;
        StringBuilder data = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data != null) {
                    return new Event(event == null ? "message" : event, data.toString());
                }
                event = null;
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            switch (field) {
                case "event" -> event = value;
                case "data" -> {
                    if (data == null) {
                        data = new StringBuilder(value);
                    } else {
                        data.append('\n').append(value);
                    }
                }
                default -> {
                    // id, retry: not needed
                }
            }
        }
        return data != null ? new Event(event == null ? "message" : event, data.toString()) : null;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
