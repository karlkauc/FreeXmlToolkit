package org.fxt.freexmltoolkit.debugger;

/**
 * What the editor's breakpoint gutter talks to: something that knows whether a
 * {@code file:line} has a breakpoint and can toggle one. Implemented by the live
 * {@link DebugSession} while a debug session runs and by {@link FileBreakpoints} for a
 * stylesheet document that is merely open in the editor, so breakpoints can be set before
 * the debugger starts.
 */
public interface BreakpointTarget {

    /**
     * @return {@code true} when an <em>enabled</em> breakpoint exists at {@code filePath:line}
     *         (use {@link #breakpointAt} to see disabled ones too)
     */
    boolean hasBreakpoint(String filePath, int line);

    /** Toggles the breakpoint at {@code filePath:line}; returns the new state (true = set). */
    boolean toggleBreakpoint(String filePath, int line);

    /** @return the breakpoint at {@code filePath:line}, or {@code null} (also when unsupported). */
    default Breakpoint breakpointAt(String filePath, int line) {
        return null;
    }

    /**
     * Replaces the breakpoint at {@code filePath:line} with {@code change} applied to it,
     * creating an enabled, unconditional one first when the line has none.
     *
     * @return {@code true} when the target supports editing breakpoints and applied it
     */
    default boolean updateBreakpoint(String filePath, int line, java.util.function.UnaryOperator<Breakpoint> change) {
        return false;
    }

    /**
     * @return the condition of the breakpoint at {@code filePath:line}, {@code ""} for an
     *         unconditional one, or {@code null} when there is no breakpoint
     */
    default String conditionAt(String filePath, int line) {
        Breakpoint bp = breakpointAt(filePath, line);
        if (bp != null) {
            return bp.condition();
        }
        return hasBreakpoint(filePath, line) ? "" : null;
    }

    /**
     * Sets the condition of the breakpoint at {@code filePath:line}, creating the breakpoint if
     * the line has none ({@code null} / blank = unconditional).
     *
     * @return {@code true} when the target supports conditions and applied it
     */
    default boolean setCondition(String filePath, int line, String condition) {
        return updateBreakpoint(filePath, line, bp -> bp.withCondition(condition));
    }

    /** Sets the hit rule of the breakpoint at {@code filePath:line} (creating it if needed). */
    default boolean setHitCondition(String filePath, int line, HitCondition hit) {
        return updateBreakpoint(filePath, line, bp -> bp.withHit(hit));
    }

    /**
     * Enables or disables the existing breakpoint at {@code filePath:line} (keeping its
     * condition and hit rule).
     *
     * @return {@code false} when the line has no breakpoint or editing is unsupported
     */
    default boolean setEnabled(String filePath, int line, boolean enabled) {
        return breakpointAt(filePath, line) != null
                && updateBreakpoint(filePath, line, bp -> bp.withEnabled(enabled));
    }

    /** @return whether all breakpoints are temporarily muted (a debug session's toggle). */
    default boolean isMuted() {
        return false;
    }

    /** @return how often the breakpoint at {@code filePath:line} was hit in the current run, or -1. */
    default int hitCount(String filePath, int line) {
        return -1;
    }
}
