package org.fxt.freexmltoolkit.debugger;

import java.util.List;

/**
 * Immutable snapshot of a single call stack frame at a pause point.
 *
 * @param description human-readable frame label (template match pattern, function name, …)
 * @param systemId    the module the frame's instruction lives in (Saxon systemId, may be empty)
 * @param lineNumber  1-based line of the instruction
 * @param variables   reserved for per-frame variables (currently empty)
 * @param kind        what the frame is: a template rule, a named template / function, or a
 *                    plain instruction inside one of those
 */
public record DebugStackFrame(String description, String systemId, int lineNumber,
                              List<VariableBinding> variables, FrameKind kind) {

    /** Classification of a stack frame. */
    public enum FrameKind { TEMPLATE, FUNCTION, INSTRUCTION }

    public DebugStackFrame {
        if (description == null) description = "";
        if (systemId == null) systemId = "";
        variables = variables == null ? List.of() : List.copyOf(variables);
        if (kind == null) kind = FrameKind.INSTRUCTION;
    }

    public DebugStackFrame(String description, String systemId, int lineNumber,
                           List<VariableBinding> variables) {
        this(description, systemId, lineNumber, variables, FrameKind.INSTRUCTION);
    }

    /** @return the normalised file path of the frame, or {@code ""}. */
    public String filePath() {
        return SourceLocations.normalize(systemId);
    }

    /** @return the file name of the frame's module, or {@code ""} for in-memory stylesheets. */
    public String fileName() {
        return SourceLocations.fileName(systemId);
    }
}
