package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression checks of scenarios against an expected result ({@link ScenarioCheck}). */
class ScenarioCheckTest {

    private static final String XSL = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output indent="yes"/>
              <xsl:template match="/order">
                <summary order="{@id}" created="{current-dateTime()}">
                  <total><xsl:value-of select="sum(item/@price)"/></total>
                </summary>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @TempDir
    Path dir;
    private FxtProject project;
    private FxtProject.Scenario scenario;

    @BeforeEach
    void setUp() throws IOException {
        write("sum.xsl", XSL);
        write("in/a.xml", "<order id=\"7\"><item price=\"10\"/><item price=\"2.5\"/></order>");
        project = FxtProject.create(dir.resolve("p.fxtproj"), "p");
        scenario = new FxtProject.Scenario("Summary", "sum.xsl", "${file}", Map.of(), null, null);
        scenario.expected = "expected/${name}.xml";
    }

    private Path write(String relative, String text) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void passesWithPlaceholdersAndFailsOnARealChange() throws IOException {
        write("expected/a.xml", "<summary order=\"7\" created=\"${xmlunit.isDateTime}\"><total>12.5</total></summary>");
        ScenarioCheck.Result ok = ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml"));
        assertEquals(ScenarioCheck.Status.PASSED, ok.status(), ok.summary() + " " + ok.differences());

        write("in/a.xml", "<order id=\"7\"><item price=\"10\"/><item price=\"3.5\"/></order>");
        ScenarioCheck.Result failed = ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml"));
        assertEquals(ScenarioCheck.Status.FAILED, failed.status());
        assertEquals(1, failed.differences().size(), failed.differences().toString());
        assertTrue(failed.differences().get(0).describe().contains("13.5"), failed.differences().toString());

        scenario.compare = new FxtProject.CompareSettings();
        scenario.compare.tolerance = 1.5;
        assertTrue(ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml")).passed(), "within the tolerance");
        scenario.compare.tolerance = null;
        scenario.compare.ignore = List.of("//total");
        assertTrue(ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml")).passed(), "ignored");
    }

    @Test
    void missingExpectedFileThenAcceptKeepsPlaceholders() throws IOException {
        ScenarioCheck.Result missing = ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml"));
        assertEquals(ScenarioCheck.Status.NO_EXPECTED, missing.status());
        Path written = ScenarioCheck.accept(project, scenario, dir.resolve("in/a.xml"), missing.actual());
        assertEquals(dir.resolve("expected/a.xml"), written);

        // a placeholder is added by hand; a later change is accepted without losing it
        String expected = Files.readString(written).replaceAll("created=\"[^\"]*\"", "created=\"\\${xmlunit.isDateTime}\"");
        Files.writeString(written, expected);
        write("in/a.xml", "<order id=\"7\"><item price=\"1\"/></order>");
        ScenarioCheck.Result changed = ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml"));
        assertEquals(ScenarioCheck.Status.FAILED, changed.status());
        ScenarioCheck.accept(project, scenario, dir.resolve("in/a.xml"), changed.actual());
        String accepted = Files.readString(written);
        assertTrue(accepted.contains("${xmlunit.isDateTime}"), accepted);
        assertTrue(accepted.contains("<total>1</total>"), accepted);
        assertTrue(ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml")).passed());
    }

    @Test
    void acceptRefusesToWriteOutsideTheProject() {
        scenario.expected = "../outside.xml";
        assertThrows(IOException.class, () -> ScenarioCheck.accept(project, scenario, dir.resolve("in/a.xml"), "<x/>"));
        scenario.expected = null;
        assertEquals(ScenarioCheck.Status.NO_EXPECTED, ScenarioCheck.check(project, scenario, dir.resolve("in/a.xml")).status());
    }

    @Test
    void textResultsAreComparedLineByLine() {
        ScenarioCheck.Result same = ScenarioCheck.compare("a\r\nb  \n", "a\nb\n", dir.resolve("e.txt"),
                org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options.DEFAULTS);
        assertTrue(same.passed(), "line endings and trailing blanks do not count");
        ScenarioCheck.Result changed = ScenarioCheck.compare("a\nb\nc", "a\nB", dir.resolve("e.txt"),
                org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options.DEFAULTS);
        assertEquals(2, changed.differences().size(), changed.differences().toString());
        assertEquals("Line 2", changed.differences().get(0).name());
    }

    @Test
    void projectFileKeepsExpectedAndCompareSettings() throws IOException {
        scenario.compare = new FxtProject.CompareSettings();
        scenario.compare.ignore = List.of("//@created");
        scenario.compare.tolerance = 0.01;
        FxtProject.Scenario plain = new FxtProject.Scenario("Plain", "sum.xsl", "in/a.xml", Map.of(), null, null);
        plain.compare = new FxtProject.CompareSettings();   // only defaults: not written
        project.setScenarios(List.of(scenario, plain));
        project.save();
        String json = Files.readString(dir.resolve("p.fxtproj"));
        assertTrue(json.contains("\"expected\": \"expected/${name}.xml\""), json);
        assertFalse(json.contains("\"compare\": {}"), json);

        FxtProject loaded = FxtProject.load(dir.resolve("p.fxtproj"));
        FxtProject.Scenario back = loaded.scenario("Summary").orElseThrow();
        assertEquals("expected/${name}.xml", back.expected);
        assertEquals(List.of("//@created"), back.compare.ignore);
        assertEquals(0.01, back.compare.tolerance);
        assertNull(loaded.scenario("Plain").orElseThrow().compare);
        assertEquals("expected/a.xml", loaded.scenarioForFile(back, dir.resolve("in/a.xml")).expected);
        assertEquals(0.01, FxtProject.CompareSettings.toOptions(back.compare).numericTolerance());
    }
}
