package org.fxt.freexmltoolkit.service.query;

import java.util.List;

import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * A failed XPath / XQuery run or check, structured for display: the processor's error code, what kind of
 * error it is, where in the query it happened, a cleaned message, a hint for the usual cause and concrete
 * corrections. {@link #format()} renders it as plain text for the CLI, the output panel and the clipboard.
 *
 * @param code        the error code without prefix ({@code XPTY0019}), or {@code null} when the processor gave none
 * @param kind        what kind of error it is
 * @param line        1-based line in the query, or -1 when unknown
 * @param column      1-based column in that line, or -1 when unknown
 * @param length      the number of characters the error covers from there (at least 1 when the position is known)
 * @param message     the processor's message without Saxon notation
 * @param hint        the usual cause and how to write it instead, or {@code null}
 * @param suggestions corrections that can be applied to the query
 * @param query       the query text the positions refer to
 * @param language    the query language
 * @param documentLine   for a {@link Kind#DOCUMENT} error: 1-based line in the queried document, or -1
 * @param documentColumn its column, or -1
 */
public record QueryError(String code, Kind kind, int line, int column, int length, String message, String hint,
                         List<Suggestion> suggestions, String query, QueryLanguage language, int documentLine,
                         int documentColumn) {

    /** An error located in the query only. */
    public QueryError(String code, Kind kind, int line, int column, int length, String message, String hint,
                      List<Suggestion> suggestions, String query, QueryLanguage language) {
        this(code, kind, line, column, length, message, hint, suggestions, query, language, -1, -1);
    }

    /** What kind of error it is, from the error code's family. */
    public enum Kind {
        /** The query does not parse (XPST0003). */
        SYNTAX,
        /** Another static error: unknown function, variable, prefix, type (XPST*, XQST*). */
        STATIC,
        /** A value of the wrong type at run time (XPTY*, XQTY*, FOTY*). */
        TYPE,
        /** Another error while running: conversion, cardinality, missing document (FO*, XPDY*, …). */
        DYNAMIC,
        /** The queried document is not well-formed XML (SXXP0003): the query is not at fault. */
        DOCUMENT,
        /** No error code. */
        OTHER;

        /** @return the kind of an error code */
        public static Kind of(String code) {
            if (code == null || code.isBlank()) {
                return OTHER;
            }
            if (code.equals("XPST0003")) {
                return SYNTAX;
            }
            if (code.equals("SXXP0003")) {
                return DOCUMENT;
            }
            if (code.startsWith("XPST") || code.startsWith("XQST") || code.equals("FXT-COMPAT")) {
                return STATIC;
            }
            if (code.startsWith("XPTY") || code.startsWith("XQTY") || code.startsWith("FOTY")) {
                return TYPE;
            }
            return DYNAMIC;
        }

        /** @return the localized label, e.g. "type error" */
        public String label() {
            return I18n.t("QueryError.kind." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /**
     * A correction: replace {@code [start, end)} of the query with {@code replacement}.
     *
     * @param label       what the user sees, e.g. "distinct-values"
     * @param start       offset in the query
     * @param end         end offset (exclusive)
     * @param replacement the new text
     */
    public record Suggestion(String label, int start, int end, String replacement) {

        /** @return {@code query} with this correction applied */
        public String applyTo(String query) {
            return query.substring(0, start) + replacement + query.substring(end);
        }
    }

    public QueryError {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        query = query == null ? "" : query;
    }

    /** @return whether the error has a position in the query */
    public boolean hasPosition() {
        return line > 0 && column > 0;
    }

    /** @return the offset of the error's position in {@link #query}, or -1 */
    public int offset() {
        return hasPosition() ? QueryText.offsetOf(query, line, column) : -1;
    }

    /** @return whether the error has a position in the queried document ({@link Kind#DOCUMENT}) */
    public boolean hasDocumentPosition() {
        return documentLine > 0;
    }

    /**
     * @return "line 3, column 15" (or just the column for one-line queries), "document line 3, column 14" for an
     *         error in the document, or {@code ""} without a position
     */
    public String positionLabel() {
        if (!hasPosition()) {
            if (hasDocumentPosition()) {
                return documentColumn > 0 ? I18n.t("QueryError.document-line-column", documentLine, documentColumn)
                        : I18n.t("QueryError.document-line", documentLine);
            }
            return "";
        }
        return query.indexOf('\n') < 0 ? I18n.t("QueryError.column", column)
                : I18n.t("QueryError.line-column", line, column);
    }

    /** @return the W3C error index entry for the code, or {@code null} for codes outside the specifications */
    public String specUrl() {
        if (code == null || !code.matches("(XP|XQ|FO)[A-Z]{2}\\d{4}")) {
            return null;
        }
        if (code.startsWith("FO")) {
            return "https://www.w3.org/TR/xpath-functions-31/#ERR" + code;
        }
        return code.startsWith("XQ") || language == QueryLanguage.XQUERY
                ? "https://www.w3.org/TR/xquery-31/#ERR" + code
                : "https://www.w3.org/TR/xpath-31/#ERR" + code;
    }

    /** @return the header line, e.g. "XPTY0019 · type error · line 1, column 23" */
    public String headline() {
        StringBuilder b = new StringBuilder();
        if (code != null) {
            b.append(code).append(" · ");
        }
        b.append(kind.label());
        String position = positionLabel();
        if (!position.isEmpty()) {
            b.append(" · ").append(position);
        }
        return b.toString();
    }

    /**
     * The query line of the error and a line with a caret under its column, each indented by two spaces;
     * {@code ""} without a position.
     */
    public String excerpt() {
        if (!hasPosition()) {
            return "";
        }
        String text = QueryText.line(query, line);
        int col = Math.min(column, text.length() + 1);
        StringBuilder marker = new StringBuilder();
        for (int i = 0; i < col - 1; i++) {
            marker.append(text.charAt(i) == '\t' ? '\t' : ' ');
        }
        int width = Math.max(1, Math.min(length, text.length() - (col - 1)));
        marker.append("^".repeat(width));
        return "  " + text.stripTrailing() + "\n  " + marker;
    }

    /** @return the whole error as plain text: headline, message, excerpt, hint and suggestions */
    public String format() {
        StringBuilder b = new StringBuilder(headline()).append('\n').append(message);
        String excerpt = excerpt();
        if (!excerpt.isEmpty()) {
            b.append('\n').append(excerpt);
        }
        if (hint != null) {
            String label = I18n.t("QueryErrorHints.label");
            b.append('\n').append(label).append(' ')
                    .append(hint.replace("\n", "\n" + " ".repeat(label.length() + 1)));
        }
        if (!suggestions.isEmpty()) {
            b.append('\n').append(I18n.t("QueryError.did-you-mean")).append(' ')
                    .append(String.join(" " + I18n.t("QueryError.or") + " ",
                            suggestions.stream().map(Suggestion::label).toList()));
        }
        return b.toString();
    }
}
