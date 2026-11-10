package org.fxt.freexmltoolkit.service.fo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.FopEvent;

/**
 * Turns the console output of an external XSL-FO processor into {@link FopEvent}s: a line that
 * mentions an error ({@code error}, {@code fatal}, {@code severe}) is {@code ERROR}, one that
 * mentions a warning is {@code WARN}, any other non-blank line {@code INFO}. Lines the preset
 * knows to be log headers are left out.
 */
public final class OutputLineParser {

    private static final Pattern ERROR = Pattern.compile("(?i)(^|[^a-z])(error|fatal|severe|exception)([^a-z]|$)");
    private static final Pattern WARNING = Pattern.compile("(?i)(^|[^a-z])(warn|warning)([^a-z]|$)");
    /** Phrases that contain "error" but report none, e.g. AH's "ErrorLevel=0" or "0 errors". */
    private static final Pattern NO_ERROR = Pattern.compile("(?i)(\\b0 errors?\\b|\\bno errors?\\b|errorlevel\\s*=\\s*0)");

    private OutputLineParser() {
    }

    /**
     * @param lines   the program's output lines
     * @param preset  the processor's preset (ignored log header lines)
     * @param source  the processor name, used as the event id
     * @return one event per line that carries a message
     */
    public static List<FopEvent> parse(List<String> lines, FoProcessorPreset preset, String source) {
        List<FopEvent> events = new ArrayList<>();
        Pattern ignored = preset == null ? null : preset.ignoredLines();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.strip();
            if (line.isEmpty() || (ignored != null && ignored.matcher(line).matches())) {
                continue;
            }
            events.add(new FopEvent(severityOf(line), source, line));
        }
        return events;
    }

    /** @return {@code ERROR}, {@code WARN} or {@code INFO} for one output line */
    static String severityOf(String line) {
        if (ERROR.matcher(line).find() && !NO_ERROR.matcher(line).find()) {
            return "ERROR";
        }
        if (WARNING.matcher(line).find()) {
            return "WARN";
        }
        return "INFO";
    }
}
