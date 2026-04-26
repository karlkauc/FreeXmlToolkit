package org.fxt.freexmltoolkit.debugger;

import java.util.List;

/**
 * Immutable snapshot taken on the Saxon transformation thread before it
 * blocks at a pause point. Read by the UI thread to populate debugger panels.
 */
public record PausedSnapshot(String systemId,
                             int lineNumber,
                             List<DebugStackFrame> callStack,
                             List<VariableBinding> variables,
                             String contextItem) {

    public PausedSnapshot {
        if (systemId == null) systemId = "";
        callStack = callStack == null ? List.of() : List.copyOf(callStack);
        variables = variables == null ? List.of() : List.copyOf(variables);
        if (contextItem == null) contextItem = "";
    }
}
