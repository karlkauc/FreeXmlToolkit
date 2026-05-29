package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link SchemaActionRunner#generateXsdFromXml(String)} (no UI / no service
 * registry — the generation engine is a singleton).
 */
class SchemaActionRunnerTest {

    @Test
    void generatesXsdFromXml() {
        String xsd = SchemaActionRunner.generateXsdFromXml("<root><name>x</name><age>30</age></root>");
        assertFalse(xsd.startsWith("ERROR:"), xsd);
        assertTrue(xsd.contains("schema"), "generated XSD should contain a schema element: " + xsd);
        assertTrue(xsd.contains("root"), "generated XSD should reference the root element: " + xsd);
    }

    @Test
    void invalidXmlReturnsError() {
        assertTrue(SchemaActionRunner.generateXsdFromXml("<not-closed>").startsWith("ERROR:"));
    }
}
