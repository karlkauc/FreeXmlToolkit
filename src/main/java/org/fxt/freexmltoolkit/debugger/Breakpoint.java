package org.fxt.freexmltoolkit.debugger;

import java.util.Objects;

/**
 * Line-based breakpoint anchored to an XSLT stylesheet.
 *
 * <p>An empty {@code filePath} indicates the user is debugging an in-memory
 * (unsaved) buffer; in that case the breakpoint matches by line number only.</p>
 */
public record Breakpoint(String filePath, int lineNumber, boolean enabled) {

    public Breakpoint {
        if (filePath == null) filePath = "";
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be >= 1, was " + lineNumber);
        }
    }

    /** Returns a copy with the enabled flag flipped. */
    public Breakpoint withEnabled(boolean enabled) {
        return new Breakpoint(filePath, lineNumber, enabled);
    }

    /**
     * Tests whether this breakpoint matches the given location.
     * Empty {@code systemId} on either side falls back to line-only matching.
     */
    public boolean matches(String systemId, int line) {
        if (lineNumber != line) return false;
        if (filePath.isEmpty() || systemId == null || systemId.isEmpty()) {
            return true;
        }
        return Objects.equals(filePath, systemId)
                || filePath.endsWith(systemId)
                || systemId.endsWith(filePath);
    }
}
