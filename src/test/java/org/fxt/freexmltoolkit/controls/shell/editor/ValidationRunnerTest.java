package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the Schematron stage of {@link ValidationRunner} (no service registry
 * needed for Schematron). A failing assert produces a Schematron problem; a
 * satisfied one produces none.
 */
class ValidationRunnerTest {

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
    void reportsSchematronAssertionFailures(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);

        List<ValidationProblem> problems = ValidationRunner.run("<root/>", null, sch.toFile());

        assertFalse(problems.isEmpty(), "failing assert must produce a problem");
        assertTrue(problems.stream().anyMatch(p -> "Schematron".equals(p.source())
                && p.message().contains("name child")), problems.toString());
    }

    @Test
    void noSchematronProblemsWhenAssertionHolds(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);

        List<ValidationProblem> problems = ValidationRunner.run("<root><name>x</name></root>", null, sch.toFile());

        assertTrue(problems.stream().noneMatch(p -> "Schematron".equals(p.source())),
                "satisfied assert must not produce a Schematron problem: " + problems);
    }

    @Test
    void nullSchematronSkipsSchematronStage() {
        assertTrue(ValidationRunner.run("<root/>", null, null).stream()
                .noneMatch(p -> "Schematron".equals(p.source())));
    }
}
