package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The Schema Statistics report surfaces the richer metrics the collector already gathers:
 * identity constraints, assertions, cardinality, documentation coverage and unused types.
 */
class SchemaStatisticsReportTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Unused">
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" type="xs:string" minOccurs="0"/>
                    <xs:element name="title" type="xs:string"/>
                  </xs:sequence>
                  <xs:assert test="true()"/>
                </xs:complexType>
                <xs:key name="bookKey">
                  <xs:selector xpath="book"/>
                  <xs:field xpath="@id"/>
                </xs:key>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void reportIncludesConstraintsCardinalityAndQuality() {
        String report = SchemaActionRunner.statistics(XSD);
        assertFalse(report.startsWith("ERROR"), report);

        // Identity constraints + assertions.
        assertTrue(report.contains("Keys:"), report);
        assertTrue(report.contains("Assertions:"), report);

        // Cardinality.
        assertTrue(report.contains("Optional elements:"), report);
        assertTrue(report.contains("Required elements:"), report);

        // Quality: documentation coverage + unused named types (Unused is never referenced).
        assertTrue(report.contains("Documentation coverage:"), report);
        assertTrue(report.contains("Unused named types:"), report);
        assertTrue(report.contains("Unused"), "the unreferenced 'Unused' type should be listed: " + report);
    }
}
