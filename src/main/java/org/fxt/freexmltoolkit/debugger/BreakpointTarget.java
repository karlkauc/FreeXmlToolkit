package org.fxt.freexmltoolkit.debugger;

/**
 * What the editor's breakpoint gutter talks to: something that knows whether a
 * {@code file:line} has a breakpoint and can toggle one. Implemented by the live
 * {@link DebugSession} while a debug session runs and by {@link FileBreakpoints} for a
 * stylesheet document that is merely open in the editor, so breakpoints can be set before
 * the debugger starts.
 */
public interface BreakpointTarget {

    /** @return {@code true} when an enabled breakpoint exists at {@code filePath:line}. */
    boolean hasBreakpoint(String filePath, int line);

    /** Toggles the breakpoint at {@code filePath:line}; returns the new state (true = set). */
    boolean toggleBreakpoint(String filePath, int line);

    /**
     * @return the condition of the breakpoint at {@code filePath:line}, {@code ""} for an
     *         unconditional one, or {@code null} when there is no breakpoint
     */
    default String conditionAt(String filePath, int line) {
        return hasBreakpoint(filePath, line) ? "" : null;
    }

    /**
     * Sets the condition of the breakpoint at {@code filePath:line}, creating the breakpoint if
     * the line has none ({@code null} / blank = unconditional).
     *
     * @return {@code true} when the target supports conditions and applied it
     */
    default boolean setCondition(String filePath, int line, String condition) {
        return false;
    }
}
