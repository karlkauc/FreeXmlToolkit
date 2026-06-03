package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorSeverity;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;
import org.junit.jupiter.api.Test;

/**
 * UI-free runner that detects errors/warnings/best-practice issues in a Schematron document, reusing
 * the legacy {@code SchematronErrorDetector}. Foundation for the shell's "Check Rules" tool.
 */
class SchematronCheckRunnerTest {

    @Test
    void malformedSchematronYieldsErrors() {
        // Unclosed <sch:pattern> — an XML syntax error the detector must flag.
        String bad = "<sch:schema xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\">"
                + "<sch:pattern></sch:schema>";
        List<SchematronError> issues = SchematronCheckRunner.check(bad);
        assertFalse(issues.isEmpty(), "a malformed Schematron must produce issues");
        assertTrue(issues.stream().anyMatch(i -> i.severity() == ErrorSeverity.ERROR),
                "an unclosed element must be an ERROR, was: " + issues);
    }

    @Test
    void blankInputYieldsNoIssues() {
        assertTrue(SchematronCheckRunner.check("").isEmpty());
        assertTrue(SchematronCheckRunner.check(null).isEmpty());
    }
}
