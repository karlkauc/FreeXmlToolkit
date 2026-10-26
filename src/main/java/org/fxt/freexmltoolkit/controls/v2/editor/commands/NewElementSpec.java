package org.fxt.freexmltoolkit.controls.v2.editor.commands;

/**
 * Everything the user entered for a new element: {@link AddElementCommand} creates the element
 * with all of it in one step. {@code null} or blank strings mean "not set".
 *
 * @param name              the element name
 * @param type              the type reference, {@code null} for none
 * @param minOccurs         minOccurs
 * @param maxOccurs         maxOccurs, {@link ChangeCardinalityCommand#UNBOUNDED} for unbounded
 * @param documentation     the documentation text
 * @param nillable          nillable="true"
 * @param abstractElement   abstract="true" (global elements only)
 * @param defaultValue      default value
 * @param fixed             fixed value
 * @param form              qualified / unqualified, {@code null} for the schema default
 * @param substitutionGroup substitution group head (global elements only)
 */
public record NewElementSpec(String name, String type, int minOccurs, int maxOccurs, String documentation,
                             boolean nillable, boolean abstractElement, String defaultValue, String fixed,
                             String form, String substitutionGroup) {

    /** @return a spec with only a name and type, occurring exactly once */
    public static NewElementSpec of(String name, String type) {
        return new NewElementSpec(name, type, 1, 1, null, false, false, null, null, null, null);
    }

    /** @return a spec with only a name, no type, occurring exactly once */
    public static NewElementSpec named(String name) {
        return of(name, null);
    }
}
