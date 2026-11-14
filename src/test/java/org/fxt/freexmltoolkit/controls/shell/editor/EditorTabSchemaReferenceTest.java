package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link EditorTab#referencesOtherSchemas} decides whether a small XSD is parsed off the FX thread
 * (because parsing reads other files).
 */
class EditorTabSchemaReferenceTest {

    @Test
    void directivesWithAnyPrefixCount() {
        assertTrue(EditorTab.referencesOtherSchemas("<xs:schema><xs:include schemaLocation=\"a.xsd\"/></xs:schema>"));
        assertTrue(EditorTab.referencesOtherSchemas("<xsd:import namespace=\"urn:x\" schemaLocation=\"b.xsd\"/>"));
        assertTrue(EditorTab.referencesOtherSchemas("<redefine\n schemaLocation=\"c.xsd\">"));
        assertTrue(EditorTab.referencesOtherSchemas("<xs:override schemaLocation=\"d.xsd\"></xs:override>"));
        assertTrue(EditorTab.referencesOtherSchemas("<xs:import/>"));
    }

    @Test
    void otherNamesDoNot() {
        assertFalse(EditorTab.referencesOtherSchemas(null));
        assertFalse(EditorTab.referencesOtherSchemas("<xs:schema><xs:element name=\"include\"/></xs:schema>"));
        assertFalse(EditorTab.referencesOtherSchemas("<xs:element name=\"importDate\" type=\"xs:date\"/>"));
        assertFalse(EditorTab.referencesOtherSchemas("<xs:includeX/>"));
        assertFalse(EditorTab.referencesOtherSchemas("</xs:include>"));
    }
}
