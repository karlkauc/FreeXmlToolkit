package org.fxt.freexmltoolkit.debugger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

/**
 * Immutable snapshot taken on the Saxon transformation thread before it
 * blocks at a pause point. Read by the UI thread to populate debugger panels.
 *
 * <p>Besides the display strings ({@code variables}, {@code contextItem}) the snapshot
 * carries the live values as {@link XdmValue}s — materialised on the Saxon thread, so
 * they are safe to read afterwards — which lets watch expressions be evaluated against
 * the paused context item and the variables in scope.</p>
 *
 * @param systemId        location of the paused instruction (Saxon systemId, may be empty)
 * @param lineNumber      1-based line of the paused instruction
 * @param callStack       frames, innermost first
 * @param variables       display rows for the Variables panel
 * @param contextItem     short text of the context item (for the status line)
 * @param contextXdm      the context item itself, or {@code null} when there is none
 * @param localVariables  stack-frame variables in scope, by name
 * @param globalVariables global variables already evaluated, by name
 * @param position        {@code position()} of the focus, or -1 outside a focus
 * @param last            {@code last()} of the focus, or -1 outside a focus
 */
public record PausedSnapshot(String systemId,
                             int lineNumber,
                             List<DebugStackFrame> callStack,
                             List<VariableBinding> variables,
                             String contextItem,
                             XdmItem contextXdm,
                             Map<QName, XdmValue> localVariables,
                             Map<QName, XdmValue> globalVariables,
                             int position,
                             int last) {

    public PausedSnapshot {
        if (systemId == null) systemId = "";
        callStack = callStack == null ? List.of() : List.copyOf(callStack);
        variables = variables == null ? List.of() : List.copyOf(variables);
        if (contextItem == null) contextItem = "";
        localVariables = localVariables == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(localVariables));
        globalVariables = globalVariables == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(globalVariables));
    }

    /** Display-only snapshot (no live values), as used by the session's fallback and tests. */
    public PausedSnapshot(String systemId, int lineNumber, List<DebugStackFrame> callStack,
                          List<VariableBinding> variables, String contextItem) {
        this(systemId, lineNumber, callStack, variables, contextItem, null, Map.of(), Map.of(), -1, -1);
    }

    /** @return {@code true} when the pause location names a file (not an in-memory stylesheet). */
    public boolean hasFile() {
        return !systemId.isEmpty();
    }

    /** @return the normalised file path of the pause location, or {@code ""}. */
    public String filePath() {
        return SourceLocations.normalize(systemId);
    }
}
