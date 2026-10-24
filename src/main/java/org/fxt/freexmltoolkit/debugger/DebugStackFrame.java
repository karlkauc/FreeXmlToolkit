package org.fxt.freexmltoolkit.debugger;

import java.util.List;
import java.util.Map;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

/**
 * Immutable snapshot of a single call stack frame at a pause point.
 *
 * @param description human-readable frame label (template match pattern, function name, …)
 * @param systemId    the module the frame's instruction lives in (Saxon systemId, may be empty)
 * @param lineNumber  1-based line of the instruction
 * @param variables      the frame's local variables as table rows (empty when not captured)
 * @param kind           what the frame is: a template rule, a named template / function, or a
 *                       plain instruction inside one of those
 * @param contextXdm     the frame's context item at the pause ({@code null} when none / not
 *                       captured), from the debugged run's processor
 * @param localVariables the frame's local variables as grounded values (locals of the
 *                       template / function the frame belongs to)
 */
public record DebugStackFrame(String description, String systemId, int lineNumber,
                              List<VariableBinding> variables, FrameKind kind,
                              XdmItem contextXdm, Map<QName, XdmValue> localVariables) {

    /** Classification of a stack frame. */
    public enum FrameKind { TEMPLATE, FUNCTION, INSTRUCTION }

    public DebugStackFrame {
        if (description == null) description = "";
        if (systemId == null) systemId = "";
        variables = variables == null ? List.of() : List.copyOf(variables);
        if (kind == null) kind = FrameKind.INSTRUCTION;
        localVariables = localVariables == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(localVariables));
    }

    /** A frame without captured values (location only). */
    public DebugStackFrame(String description, String systemId, int lineNumber,
                           List<VariableBinding> variables, FrameKind kind) {
        this(description, systemId, lineNumber, variables, kind, null, Map.of());
    }

    public DebugStackFrame(String description, String systemId, int lineNumber,
                           List<VariableBinding> variables) {
        this(description, systemId, lineNumber, variables, FrameKind.INSTRUCTION);
    }

    /** @return whether the frame's context item and variables were captured at the pause. */
    public boolean hasValues() {
        return contextXdm != null || !localVariables.isEmpty();
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
