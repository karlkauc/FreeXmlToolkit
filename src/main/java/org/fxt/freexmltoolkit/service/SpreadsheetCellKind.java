package org.fxt.freexmltoolkit.service;

import java.util.Locale;

/**
 * How a value is stored in an exported spreadsheet cell, derived from its XSD built-in type.
 * Everything without a numeric, boolean or date/time value space stays {@link #TEXT}.
 */
public enum SpreadsheetCellKind {
    TEXT, INTEGER, DECIMAL, DOUBLE, BOOLEAN, DATE, DATE_TIME, TIME;

    /**
     * @param builtin the local name of an XSD built-in type ({@code decimal}, {@code int}, …), may be null
     * @return the cell kind of that type's values
     */
    public static SpreadsheetCellKind fromBuiltin(String builtin) {
        if (builtin == null) {
            return TEXT;
        }
        return switch (builtin.toLowerCase(Locale.ROOT)) {
            case "integer", "int", "long", "short", "byte", "nonpositiveinteger", "negativeinteger",
                 "nonnegativeinteger", "positiveinteger", "unsignedlong", "unsignedint", "unsignedshort",
                 "unsignedbyte" -> INTEGER;
            case "decimal" -> DECIMAL;
            case "float", "double" -> DOUBLE;
            case "boolean" -> BOOLEAN;
            case "date" -> DATE;
            case "datetime", "datetimestamp" -> DATE_TIME;
            case "time" -> TIME;
            default -> TEXT;
        };
    }
}
