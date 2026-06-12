package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void schematronProblemsResolveTheirSourceLine(@TempDir Path tmp) throws Exception {
        // The failing element sits on a known line; SVRL has no line number but a
        // location XPath that must be resolved so the problem can navigate there.
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, """
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                  <sch:pattern>
                    <sch:rule context="item">
                      <sch:assert test="@id">item must have an id</sch:assert>
                    </sch:rule>
                  </sch:pattern>
                </sch:schema>
                """);
        String xml = """
                <root>
                  <item id="1"/>
                  <item/>
                </root>
                """;

        List<ValidationProblem> problems = ValidationRunner.run(xml, null, sch.toFile());

        ValidationProblem problem = problems.stream()
                .filter(p -> "Schematron".equals(p.source()) && p.message().contains("id"))
                .findFirst().orElseThrow();
        assertEquals(3, problem.line(), "the problem must point at the failing <item/> line: " + problems);
    }

    @Test
    void nullSchematronSkipsSchematronStage() {
        assertTrue(ValidationRunner.run("<root/>", null, null).stream()
                .noneMatch(p -> "Schematron".equals(p.source())));
    }

    @Test
    void batchReportListsEachFileWithSchematronResult(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "<root/>");
        Path good = tmp.resolve("good.xml");
        Files.writeString(good, "<root><name>x</name></root>");

        String report = ValidationRunner.batchReport(java.util.List.of(bad.toFile(), good.toFile()), null, sch.toFile());

        assertTrue(report.contains("Batch Validation Report"), report);
        assertTrue(report.matches("(?s).*bad\\.xml: \\d+ problem.*"), report);
        assertTrue(report.contains("good.xml: valid"), report);
    }
}
