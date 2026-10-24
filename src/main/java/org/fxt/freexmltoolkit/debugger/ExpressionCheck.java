package org.fxt.freexmltoolkit.debugger;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;

/**
 * A static check of breakpoint conditions and logpoint messages while they are edited, so a
 * typo shows up in the Breakpoint Properties dialog instead of pausing the run later.
 *
 * <p>Only what can be known without the stylesheet's context is reported: syntax errors and
 * other static errors. Variables are not checked (any {@code $name} may be in scope at the line),
 * nor are namespace prefixes and user functions (they come from the stylesheet).</p>
 */
public final class ExpressionCheck {

    private static final Processor PROCESSOR = new Processor(false);

    private ExpressionCheck() {
    }

    /** @return {@code null} when {@code expression} compiles (or is blank), else the error message. */
    public static String checkXPath(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        XPathCompiler compiler = PROCESSOR.newXPathCompiler();
        compiler.setLanguageVersion("3.1");
        compiler.setAllowUndeclaredVariables(true);
        compiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        compiler.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
        compiler.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
        compiler.declareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
        try {
            compiler.compile(expression.strip());
            return null;
        } catch (SaxonApiException e) {
            String code = e.getErrorCode() == null ? "" : e.getErrorCode().getLocalName();
            // Prefixes and functions of the stylesheet are unknown here - not an error yet.
            if ("XPST0081".equals(code) || "XPST0017".equals(code)) {
                return null;
            }
            String message = e.getMessage() == null ? "invalid XPath" : e.getMessage().strip();
            return code.isEmpty() ? message : code + ": " + message;
        }
    }

    /**
     * Checks a logpoint message: every {@code {xpath}} part must compile and every {@code {} be
     * closed ({@code {{} and {@code }}} are literal braces).
     *
     * @return {@code null} when fine, else the first problem
     */
    public static String checkLogMessage(String template) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if ((c == '{' || c == '}') && i + 1 < n && template.charAt(i + 1) == c) {
                i += 2;
            } else if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end < 0) {
                    return "The { at position " + (i + 1) + " is not closed (write {{ for a literal brace).";
                }
                String expr = template.substring(i + 1, end);
                if (expr.isBlank()) {
                    return "Empty {} at position " + (i + 1) + ".";
                }
                String error = checkXPath(expr);
                if (error != null) {
                    return "{" + expr.strip() + "}: " + error;
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return null;
    }
}
