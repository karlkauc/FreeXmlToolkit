package org.fxt.freexmltoolkit.controls.shell.inspector;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathCalculator;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathContext;

/**
 * UI-free derivation of the inspector's "Node &amp; XPath" data from an editor
 * caret position. Wraps the existing {@link XPathCalculator} so the inspector
 * panel stays presentation-only and the logic is unit-testable.
 *
 * @param kind  the node kind ("Element" or "Document")
 * @param name  the current element name (empty at document level)
 * @param xpath the absolute XPath to the caret position (e.g. {@code /root/item})
 * @param depth the element nesting depth at the caret
 */
public record NodeXPathInfo(String kind, String name, String xpath, int depth) {

    /** Derives the info using a throwaway calculator (convenience for callers without one). */
    public static NodeXPathInfo fromCaret(String text, int caret) {
        return fromCaret(new XPathCalculator(), text, caret);
    }

    /** Derives the info using the given (cacheable) calculator. */
    public static NodeXPathInfo fromCaret(XPathCalculator calculator, String text, int caret) {
        String safeText = text != null ? text : "";
        int safeCaret = Math.max(0, Math.min(caret, safeText.length()));
        XPathContext ctx = calculator.calculate(safeText, safeCaret);
        if (ctx == null) {
            // The analyzer yields no context for some inputs (and caches the null) — treat the
            // caret as being at document level rather than crashing the inspector.
            return new NodeXPathInfo("Document", "", "/", 0);
        }

        String current = ctx.getCurrentElement();
        boolean hasElement = current != null && !current.isBlank();
        return new NodeXPathInfo(
                hasElement ? "Element" : "Document",
                hasElement ? current : "",
                ctx.getXPath(),
                ctx.getDepth());
    }
}
