package org.fxt.freexmltoolkit.service.xslt;

import java.util.List;

import org.fxt.freexmltoolkit.service.query.QueryError;

/**
 * A static error or warning of a stylesheet, placed in the module it is in: the processor's code, a cleaned message,
 * the range in that module's text (the failing token of an XPath expression, a wrong attribute, else the
 * instruction's start tag), a hint for the usual cause and corrections.
 *
 * @param code        error code without prefix ({@code XTSE0010}), or {@code null}
 * @param warning     a warning (the stylesheet still compiles)
 * @param message     the message without Saxon notation
 * @param systemId    the module's URI, or {@code null}
 * @param module      the module's file name when it is not the main stylesheet, else {@code null}
 * @param line        1-based line in the module, or -1
 * @param column      1-based column, or -1
 * @param offset      offset in the module's text, or -1 when the text was not available
 * @param length      characters from {@code offset} (at least 1 with an offset)
 * @param hint        the usual cause and what to write instead, or {@code null}
 * @param suggestions corrections; their offsets are offsets in the module's text
 * @param sourceLine  the text of {@code line} in the module (for a caret display), or {@code null}
 */
public record XsltDiagnostic(String code, boolean warning, String message, String systemId, String module, int line,
                             int column, int offset, int length, String hint,
                             List<QueryError.Suggestion> suggestions, String sourceLine) {

    public XsltDiagnostic {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /** A diagnostic without the source line. */
    public XsltDiagnostic(String code, boolean warning, String message, String systemId, String module, int line,
                          int column, int offset, int length, String hint, List<QueryError.Suggestion> suggestions) {
        this(code, warning, message, systemId, module, line, column, offset, length, hint, suggestions, null);
    }

    /**
     * Diagnostics as a plain-text report for the output panel, the command line and the clipboard: each with its
     * position, the source line with a caret under the range, and the hint.
     */
    public static String report(List<XsltDiagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (XsltDiagnostic d : diagnostics) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(d.format()).append('\n');
            if (d.sourceLine() != null && d.column() > 0) {
                String shown = d.sourceLine().replace('\t', ' ');
                int indent = shown.length() - shown.stripLeading().length();
                int from = Math.max(0, d.column() - 1 - indent);
                sb.append("    ").append(shown.strip()).append('\n');
                sb.append("    ").append(" ".repeat(Math.min(from, shown.strip().length())))
                        .append("^".repeat(Math.max(1, Math.min(d.length(), Math.max(1, shown.length() - d.column() + 1)))))
                        .append('\n');
            }
            if (d.hint() != null) {
                for (String hintLine : d.hint().split("\n")) {
                    sb.append("    ").append(hintLine).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** @return the {@code xsl:message}-like level of the diagnostic for message lists: ERROR / WARNING [code] */
    public String level() {
        return (warning ? "WARNING" : "ERROR") + (code == null ? "" : " [" + code + "]");
    }

    /** @return the location as message lists show it: {@code file.xsl:line:column} */
    public String location() {
        String file = module != null ? module : systemId == null ? "stylesheet" : XsltErrors.fileName(systemId);
        return line <= 0 ? file : column > 0 ? file + ":" + line + ":" + column : file + ":" + line;
    }

    /** @return whether the error is in the main stylesheet (not in an included or imported module) */
    public boolean inMain() {
        return module == null;
    }

    /** @return "line 12, column 5" or "x.xsl, line 12" for a module; {@code ""} without a position */
    public String positionLabel() {
        if (line <= 0) {
            return module == null ? "" : module;
        }
        String at = column > 0 ? "line " + line + ", column " + column : "line " + line;
        return module == null ? at : module + ", " + at;
    }

    /** @return one line: {@code [CODE] message (position)} */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (warning) {
            sb.append("Warning ");
        }
        if (code != null) {
            sb.append('[').append(code).append("] ");
        }
        sb.append(message);
        String at = positionLabel();
        if (!at.isEmpty()) {
            sb.append(" (").append(at).append(')');
        }
        return sb.toString();
    }
}
