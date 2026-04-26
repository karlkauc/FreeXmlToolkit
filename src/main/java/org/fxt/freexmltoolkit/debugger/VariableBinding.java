package org.fxt.freexmltoolkit.debugger;

/**
 * Snapshot of a single variable visible to the debugger at a pause point.
 *
 * <p>{@code value} is a stringified, length-capped representation suitable
 * for display in a {@code TableView}. {@code type} reflects the Saxon item
 * type at the time of capture.</p>
 */
public record VariableBinding(String name, String value, String type, VariableScope scope) {

    /** Maximum number of characters preserved when stringifying values. */
    public static final int MAX_VALUE_LENGTH = 200;

    public VariableBinding {
        if (name == null) name = "";
        if (value == null) value = "";
        if (type == null) type = "";
        if (scope == null) scope = VariableScope.LOCAL;
        if (value.length() > MAX_VALUE_LENGTH) {
            value = value.substring(0, MAX_VALUE_LENGTH) + "…";
        }
    }
}
