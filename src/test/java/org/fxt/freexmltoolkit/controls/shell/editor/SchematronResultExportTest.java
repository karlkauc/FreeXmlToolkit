package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorSeverity;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorType;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;
import org.junit.jupiter.api.Test;

/**
 * Verifies the shell's Schematron result export (CSV / JSON), which ports the
 * legacy SchematronController's report export to the shell's result model.
 */
class SchematronResultExportTest {

    private static final List<SchematronError> ISSUES = List.of(
            new SchematronError(ErrorType.XML_SYNTAX, 2, 1, "Unclosed element", ErrorSeverity.ERROR),
            new SchematronError(ErrorType.STRUCTURAL, 5, 3, "Message, with \"quotes\"", ErrorSeverity.WARNING));

    @Test
    void csvHasHeaderAndOneEscapedRowPerIssue() {
        String csv = SchematronResultExport.toCsv(ISSUES);
        String[] lines = csv.split("\n");
        assertEquals("Severity,Type,Line,Column,Message", lines[0], "header row");
        assertEquals("ERROR,XML_SYNTAX,2,1,Unclosed element", lines[1]);
        // commas + quotes must be CSV-escaped (wrapped, inner quotes doubled)
        assertEquals("WARNING,STRUCTURAL,5,3,\"Message, with \"\"quotes\"\"\"", lines[2]);
    }

    @Test
    void emptyIssuesProduceHeaderOnlyCsv() {
        assertEquals("Severity,Type,Line,Column,Message", SchematronResultExport.toCsv(List.of()).trim());
    }

    @Test
    void jsonEscapesQuotesAndContainsEveryIssue() {
        String json = SchematronResultExport.toJson(ISSUES);
        assertTrue(json.trim().startsWith("["), "JSON is an array");
        assertTrue(json.contains("\"severity\": \"ERROR\""), "first issue severity present");
        assertTrue(json.contains("\"type\": \"STRUCTURAL\""), "second issue type present");
        assertTrue(json.contains("\"line\": 2"), "numeric line is unquoted");
        // double quotes inside the message must be JSON-escaped
        assertTrue(json.contains("Message, with \\\"quotes\\\""), "message quotes JSON-escaped");
    }
}
