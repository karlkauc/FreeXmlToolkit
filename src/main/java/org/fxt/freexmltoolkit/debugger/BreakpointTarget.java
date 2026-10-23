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
}
