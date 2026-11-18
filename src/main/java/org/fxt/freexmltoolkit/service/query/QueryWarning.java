package org.fxt.freexmltoolkit.service.query;

/**
 * A compiler warning: the query runs, but probably not as meant (e.g. Saxon's SXWN9026 "the expression can
 * succeed only if the supplied value is an empty sequence", which predicts an XPTY0019).
 *
 * @param code    the warning code ({@code SXWN9026}), or {@code null}
 * @param line    1-based line, or -1
 * @param column  1-based column, or -1
 * @param length  characters covered from there
 * @param message the cleaned message
 */
public record QueryWarning(String code, int line, int column, int length, String message) {

    /** @return whether the warning has a position in the query */
    public boolean hasPosition() {
        return line > 0 && column > 0;
    }

    /** @return the offset of the warning's position in {@code query}, or -1 */
    public int offset(String query) {
        return hasPosition() && query != null ? QueryText.offsetOf(query, line, column) : -1;
    }
}
