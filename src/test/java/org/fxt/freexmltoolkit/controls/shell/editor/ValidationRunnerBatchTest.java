package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the structured per-file batch results of {@link ValidationRunner#batch}
 * (the data behind the Validation panel's RESULTS list) and that the legacy
 * text report keeps its format by delegating to the structured run.
 */
class ValidationRunnerBatchTest {

    private static final String SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
              <sch:pattern>
                <sch:rule context="root">
                  <sch:assert test="name">root must have a name child</sch:assert>
                </sch:rule>
              </sch:pattern>
            </sch:schema>
            """;

    @Test
    void batchReturnsOneStructuredResultPerFile(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "<root/>");
        Path good = tmp.resolve("good.xml");
        Files.writeString(good, "<root><name>x</name></root>");

        List<ValidationRunner.FileValidationResult> results =
                ValidationRunner.batch(List.of(bad.toFile(), good.toFile()), null, sch.toFile());

        assertEquals(2, results.size());
        ValidationRunner.FileValidationResult first = results.get(0);
        assertEquals("bad.xml", first.file().getName());
        assertTrue(first.failed(), "failing assert must mark the file failed: " + first);
        assertTrue(first.errorCount() >= 1, first.toString());
        ValidationRunner.FileValidationResult second = results.get(1);
        assertEquals("good.xml", second.file().getName());
        assertFalse(second.failed(), second.toString());
        assertEquals(0, second.errorCount());
        assertEquals(0, second.warningCount());
        assertNull(second.readError());
    }

    @Test
    void unreadableFileYieldsReadErrorResult(@TempDir Path tmp) {
        File missing = tmp.resolve("missing.xml").toFile();

        List<ValidationRunner.FileValidationResult> results =
                ValidationRunner.batch(List.of(missing), null, null);

        assertEquals(1, results.size());
        ValidationRunner.FileValidationResult result = results.get(0);
        assertNotNull(result.readError());
        assertTrue(result.failed());
        assertEquals(1, result.errorCount(), "a read error counts as one error");
    }

    @Test
    void warningOnlyProblemsDoNotFailTheFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("warn.xml");
        Files.writeString(file, "<root/>");
        var warning = new ValidationProblem("Schematron", "warning", 1, "just a hint");

        var result = new ValidationRunner.FileValidationResult(file.toFile(), List.of(warning), null);

        assertFalse(result.failed());
        assertEquals(0, result.errorCount());
        assertEquals(1, result.warningCount());
    }

    @Test
    void batchReportKeepsItsTextFormat(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "<root/>");
        Path good = tmp.resolve("good.xml");
        Files.writeString(good, "<root><name>x</name></root>");

        String report = ValidationRunner.batchReport(
                List.of(bad.toFile(), good.toFile()), null, sch.toFile());

        assertTrue(report.contains("Batch Validation Report"), report);
        assertTrue(report.matches("(?s).*bad\\.xml: \\d+ problem.*"), report);
        assertTrue(report.contains("good.xml: valid"), report);
    }
}
