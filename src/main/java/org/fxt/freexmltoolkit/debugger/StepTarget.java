package org.fxt.freexmltoolkit.debugger;

/**
 * A place "Smart Step Into" can run to from the paused line: a function, a named template, the
 * templates of a mode, or - for an {@code xsl:apply-templates} without a mode - the next template
 * rule that is entered.
 *
 * @param label  the menu text, e.g. {@code function fxt:csv()}
 * @param symbol the component to stop in, or {@code null} for "the next template rule"
 */
public record StepTarget(String label, SymbolBreakpoint symbol) {

    /** @return whether entering the given component reaches this target (see {@link SymbolBreakpoint#matches}). */
    public boolean matches(String match, String template, String function, String mode) {
        if (symbol == null) {
            return match != null;
        }
        return symbol.withEnabled(true).matches(match, template, function, mode);
    }
}
