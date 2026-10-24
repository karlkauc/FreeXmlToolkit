package org.fxt.freexmltoolkit.debugger;

/**
 * Scope classification for a debugger variable binding.
 */
public enum VariableScope {
    GLOBAL,
    LOCAL,
    PARAMETER,
    /** XSLT context: the mode, current-group(), regex groups, accumulator values. */
    XSLT
}
