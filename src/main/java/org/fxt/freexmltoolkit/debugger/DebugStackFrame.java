package org.fxt.freexmltoolkit.debugger;

import java.util.List;

/**
 * Immutable snapshot of a single call stack frame at a pause point.
 */
public record DebugStackFrame(String description, String systemId, int lineNumber,
                              List<VariableBinding> variables) {

    public DebugStackFrame {
        if (description == null) description = "";
        if (systemId == null) systemId = "";
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
