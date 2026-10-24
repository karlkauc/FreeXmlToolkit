package org.fxt.freexmltoolkit.debugger;

/**
 * Line-based breakpoint anchored to an XSLT stylesheet module.
 *
 * <p>{@code filePath} is stored normalised (see {@link SourceLocations#normalize}) so that a
 * breakpoint set on an editor document matches the {@code systemId} Saxon reports for the
 * same module. An empty {@code filePath} indicates the user is debugging an in-memory
 * (unsaved) buffer; in that case the breakpoint matches by line number only.</p>
 *
 * <p>A non-empty {@code condition} is an XPath expression: the run pauses at the line only
 * when its effective boolean value is true at that moment (evaluated against the context item
 * with every variable in scope, see {@link DebugSession#setConditionEvaluator}).</p>
 *
 * <p>{@code hit} restricts pausing by the hit count of the current run (see
 * {@link HitCondition}); the counts themselves live in the {@link DebugSession}.</p>
 *
 * <p>A non-empty {@code logMessage} makes it a <em>logpoint</em>: at every hit the message is
 * written to the debugger's Messages, with {@code {xpath}} parts evaluated at that moment
 * ({@code {{} / {@code }}} for literal braces). {@code suspend = false} logs without pausing.</p>
 */
public record Breakpoint(String filePath, int lineNumber, boolean enabled, String condition, HitCondition hit,
                         String logMessage, boolean suspend) {

    public Breakpoint {
        filePath = SourceLocations.normalize(filePath);
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be >= 1, was " + lineNumber);
        }
        condition = condition == null ? "" : condition.strip();
        hit = hit == null ? HitCondition.ALWAYS : hit;
        logMessage = logMessage == null ? "" : logMessage.strip();
        if (logMessage.isEmpty()) {
            suspend = true; // only a logpoint may run on
        }
    }

    /** A pausing breakpoint with a condition and a hit rule. */
    public Breakpoint(String filePath, int lineNumber, boolean enabled, String condition, HitCondition hit) {
        this(filePath, lineNumber, enabled, condition, hit, "", true);
    }

    /** A breakpoint with a condition that pauses on every hit. */
    public Breakpoint(String filePath, int lineNumber, boolean enabled, String condition) {
        this(filePath, lineNumber, enabled, condition, HitCondition.ALWAYS);
    }

    /** An unconditional breakpoint. */
    public Breakpoint(String filePath, int lineNumber, boolean enabled) {
        this(filePath, lineNumber, enabled, "");
    }

    /** Returns a copy with the enabled flag flipped. */
    public Breakpoint withEnabled(boolean enabled) {
        return new Breakpoint(filePath, lineNumber, enabled, condition, hit, logMessage, suspend);
    }

    /** Returns a copy with {@code condition} ({@code null} / blank = unconditional). */
    public Breakpoint withCondition(String condition) {
        return new Breakpoint(filePath, lineNumber, enabled, condition, hit, logMessage, suspend);
    }

    /** Returns a copy with the hit rule {@code hit} ({@code null} = every hit). */
    public Breakpoint withHit(HitCondition hit) {
        return new Breakpoint(filePath, lineNumber, enabled, condition, hit, logMessage, suspend);
    }

    /**
     * Returns a copy that logs {@code message} at every hit ({@code null} / blank = no logging)
     * and pauses only when {@code suspend} (a plain breakpoint always pauses).
     */
    public Breakpoint withLog(String message, boolean suspend) {
        return new Breakpoint(filePath, lineNumber, enabled, condition, hit, message, suspend);
    }

    /** @return whether this is a logpoint (writes a message at every hit). */
    public boolean isLogpoint() {
        return !logMessage.isEmpty();
    }

    /** @return whether a condition or a hit rule restricts when this breakpoint pauses. */
    public boolean hasRules() {
        return hasCondition() || hit.isSet() || isLogpoint();
    }

    /** @return whether the breakpoint only pauses when its condition holds. */
    public boolean hasCondition() {
        return !condition.isEmpty();
    }

    /**
     * Tests whether this breakpoint matches the given location. An empty location on
     * either side (in-memory stylesheet, or Saxon without a systemId) falls back to
     * line-only matching; otherwise the normalised paths must be identical, so a module
     * {@code lib/a.xsl} never triggers a breakpoint set in another {@code a.xsl}.
     */
    public boolean matches(String systemId, int line) {
        return lineNumber == line && matchesNormalized(SourceLocations.normalize(systemId), line);
    }

    /**
     * Like {@link #matches} for a location that is already normalised (hot path: the
     * session normalises a systemId once per traced instruction, not once per breakpoint).
     */
    public boolean matchesNormalized(String normalizedPath, int line) {
        if (lineNumber != line) return false;
        if (filePath.isEmpty() || normalizedPath == null || normalizedPath.isEmpty()) {
            return true;
        }
        return filePath.equals(normalizedPath);
    }
}
