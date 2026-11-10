package org.fxt.freexmltoolkit.service.ai.tool;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounds of one tool call.
 *
 * @param timeout        wall-clock time per call
 * @param maxOutputChars text returned to the caller per output field; longer text is cut and marked
 * @param maxInputBytes  size of an input file read into memory
 * @param maxItems       problems / differences / items returned; the total count is reported as well
 */
public record ToolLimits(Duration timeout, int maxOutputChars, long maxInputBytes, int maxItems) {

    public static final ToolLimits DEFAULTS =
            new ToolLimits(Duration.ofSeconds(30), 64 * 1024, 64L * 1024 * 1024, 200);

    public ToolLimits {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero() || maxOutputChars < 1 || maxInputBytes < 1 || maxItems < 1) {
            throw new IllegalArgumentException("limits must be positive");
        }
    }

    /** Whether {@link #truncate} would cut the text. */
    public boolean isTruncated(String text) {
        return text != null && text.length() > maxOutputChars;
    }

    /** The text, or its first {@code maxOutputChars} characters followed by a marker with the full length. */
    public String truncate(String text) {
        if (text == null || !isTruncated(text)) {
            return text;
        }
        return text.substring(0, maxOutputChars) + "\n… [truncated, " + text.length() + " characters in total]";
    }
}
