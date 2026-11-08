package org.fxt.freexmltoolkit.service.grammar;

/**
 * A problem found while validating against a DTD or RELAX NG grammar.
 *
 * @param severity {@code error}, {@code warning} or {@code fatal}
 * @param line     1-based, 0 when unknown
 * @param column   1-based, 0 when unknown
 * @param systemId the resource the problem is in (the document, or a grammar file), or {@code null}
 */
public record GrammarProblem(String severity, int line, int column, String message, String systemId) {
}
