package org.fxt.freexmltoolkit.controls.v2.editor.commands;

/**
 * Everything the user entered for a new attribute: {@link AddAttributeCommand} creates the
 * attribute with all of it in one step. {@code null} or blank strings mean "not set".
 *
 * @param name          the attribute name
 * @param type          the type reference, {@code null} for none
 * @param use           optional / required / prohibited, {@code null} for the default (optional)
 * @param documentation the documentation text
 * @param defaultValue  default value
 * @param fixed         fixed value
 * @param form          qualified / unqualified, {@code null} for the schema default
 */
public record NewAttributeSpec(String name, String type, String use, String documentation,
                               String defaultValue, String fixed, String form) {

    /** @return a spec with only a name */
    public static NewAttributeSpec named(String name) {
        return new NewAttributeSpec(name, null, null, null, null, null, null);
    }
}
