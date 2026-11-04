package org.fxt.freexmltoolkit.service;

/**
 * Tells the XML → spreadsheet export which XSD built-in type the value of an element or attribute
 * has, typically from the XSD bound to the document. Called off the FX thread, once per exported
 * value; implementations should cache.
 */
@FunctionalInterface
public interface SpreadsheetTypeResolver {

    /** No schema: every value is text. */
    SpreadsheetTypeResolver NONE = (elementXPath, attributeName) -> null;

    /**
     * @param elementXPath  the positional instance XPath of the element ({@code /a/b[2]/c})
     * @param attributeName the attribute's name for an attribute value, {@code null} for the element's text
     * @return the local name of the built-in type the value is of ({@code decimal}, {@code date}, …),
     *         or {@code null} when the schema does not give one (undeclared, list, union, mixed content)
     */
    String builtinTypeOf(String elementXPath, String attributeName);

    /** @return the cell kind of the value, never {@code null} */
    default SpreadsheetCellKind kindOf(String elementXPath, String attributeName) {
        return SpreadsheetCellKind.fromBuiltin(builtinTypeOf(elementXPath, attributeName));
    }

    /** @return whether this resolver can type anything (it is not {@link #NONE}) */
    default boolean isSchemaBased() {
        return this != NONE;
    }
}
