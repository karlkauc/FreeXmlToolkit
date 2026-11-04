package org.fxt.freexmltoolkit.service;

/**
 * Tells the XML → spreadsheet export which {@link SpreadsheetCellKind} the value of an element or
 * attribute has, typically from the XSD bound to the document. Called off the FX thread, once per
 * exported value; implementations should cache.
 */
@FunctionalInterface
public interface SpreadsheetTypeResolver {

    /** No schema: every value is text. */
    SpreadsheetTypeResolver NONE = (elementXPath, attributeName) -> SpreadsheetCellKind.TEXT;

    /**
     * @param elementXPath  the positional instance XPath of the element ({@code /a/b[2]/c})
     * @param attributeName the attribute's name for an attribute value, {@code null} for the element's text
     * @return the cell kind, never {@code null}
     */
    SpreadsheetCellKind kindOf(String elementXPath, String attributeName);
}
