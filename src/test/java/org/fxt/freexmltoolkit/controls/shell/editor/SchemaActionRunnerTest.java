package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                  <xs:element name="age" type="xs:integer"/>
                </xs:sequence>
              </xs:complexType>
              <xs:element name="person" type="PersonType"/>
            </xs:schema>
            """;

    @Test
    void statisticsReportsCounts() {
        String report = SchemaActionRunner.statistics(XSD);
        assertFalse(report.startsWith("ERROR:"), report);
        assertTrue(report.contains("Schema Statistics"), report);
        assertTrue(report.contains("Complex types:"), report);
        assertTrue(report.contains("Elements:"), report);
    }

    @Test
    void flattenReSerializesSchema() {
        String flattened = SchemaActionRunner.flatten(XSD, null);
        assertFalse(flattened.startsWith("ERROR:"), flattened);
        assertTrue(flattened.contains("schema"), flattened);
        assertTrue(flattened.contains("PersonType"), flattened);
    }
}
