package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlValidator;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

class FundsXmlRunnerTest {

    @Test
    void installedVersionsNeverNull() {
        List<String> versions = FundsXmlRunner.installedVersions();
        assertNotNull(versions, "versions list");
    }

    @Test
    void validateWithNoActiveSchemaReportsClearly() {
        // With no active schema / blank xml, validate returns a non-null human-readable summary
        // rather than throwing.
        String summary = FundsXmlRunner.validateSummary("<root/>");
        assertNotNull(summary);
        assertTrue(summary.length() > 0);
    }

    @Test
    void folderPathsResolve() {
        assertNotNull(FundsXmlRunner.examplesDir());
        assertNotNull(FundsXmlRunner.schemaDir());
        assertNotNull(FundsXmlRunner.schematronDir());
    }

    @Test
    void schemaErrorsBecomeProblemsForTheProblemsPanel() {
        var error = new SAXParseException("cvc-complex-type.2.4.a: Invalid content", null, null, 12, 7);
        var result = FundsXmlRunner.of(FundsXmlValidator.Status.INVALID, "4.2.10", List.of(error), null);

        assertTrue(result.ran());
        assertFalse(result.valid());
        assertEquals(1, result.problems().size());
        ValidationProblem problem = result.problems().getFirst();
        assertEquals("XSD", problem.source());
        assertEquals(12, problem.line());
        assertEquals(7, problem.column());
        assertEquals(ValidationProblem.Level.ERROR, problem.level());
        assertTrue(result.summary().contains("4.2.10"), result.summary());
    }

    @Test
    void validAndNotRunCarryNoProblems() {
        var valid = FundsXmlRunner.of(FundsXmlValidator.Status.VALID, "4.2.10", List.of(), null);
        assertTrue(valid.valid());
        assertTrue(valid.problems().isEmpty());

        var noSchema = FundsXmlRunner.of(FundsXmlValidator.Status.NO_ACTIVE_SCHEMA, null, List.of(), null);
        assertFalse(noSchema.ran());
        assertFalse(noSchema.valid());
    }
}
