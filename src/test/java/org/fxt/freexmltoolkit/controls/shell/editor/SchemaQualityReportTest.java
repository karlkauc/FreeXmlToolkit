package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The shell's Schema Quality report wraps the V2 XsdQualityChecker into a readable text report. */
class SchemaQualityReportTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="myElement" type="xs:string"/>
              <xs:element name="Another" type="xs:string"/>
              <xs:element name="third_one" type="xs:string"/>
            </xs:schema>
            """;

    @Test
    void reportHasScoreChecksAndIssuesStructure() {
        String report = SchemaActionRunner.qualityReport(XSD);
        assertFalse(report.startsWith("ERROR"), report);
        assertTrue(report.contains("Schema Quality"), report);
        assertTrue(report.contains("Score:"), report);
        assertTrue(report.contains("Checks passed:"), report);
        assertTrue(report.contains("Issues:"), report);
        // The score is rendered as "<n> / 100".
        assertTrue(report.matches("(?s).*Score:\\s+\\d+ / 100.*"), report);
    }

    @Test
    void invalidSchemaYieldsError() {
        assertTrue(SchemaActionRunner.qualityReport("not xml").startsWith("ERROR"));
    }
}
