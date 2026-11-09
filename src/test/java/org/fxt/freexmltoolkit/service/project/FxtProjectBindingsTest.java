package org.fxt.freexmltoolkit.service.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.controls.shell.editor.ScenarioRunner;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Project bindings beyond the schema (Schematron, scenario), scenarios run for files
 * ({@code ${file}}, {@code ${name}}, {@code ${dir}}), the settings setters, recent projects and the
 * reload of a changed project file.
 */
@DisplayName("FxtProject bindings and scenarios for files")
class FxtProjectBindingsTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void services() {
        ServiceRegistry.initialize();
    }

    @AfterEach
    void reset() {
        ProjectService.getInstance().resetForTesting();
    }

    private Path abs(String relative) {
        return dir.resolve(relative).toAbsolutePath().normalize();
    }

    @Test
    @DisplayName("each part comes from the first binding that matches and names it")
    void assignmentPerPart() throws Exception {
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");
        project.bind(new FxtProject.SchemaBinding("data/special.xml", "s/special.xsd", null, null));
        project.bind(new FxtProject.SchemaBinding("data/**/*.xml", "s/order.xsd", "rules/order.sch", "Report"));
        project.bind(new FxtProject.SchemaBinding("**/*.json", "s/order.schema.json", null, null));
        project.save();
        FxtProject loaded = FxtProject.load(project.file());

        FxtProject.Assignment special = loaded.assignmentFor(dir.resolve("data/special.xml"));
        assertEquals(abs("s/special.xsd"), special.schema(), "the narrower binding comes first");
        assertEquals(abs("rules/order.sch"), special.schematron(), "the Schematron of the later binding still applies");
        assertEquals("Report", special.scenario());
        assertEquals(Optional.of(abs("rules/order.sch")), loaded.schematronFor(dir.resolve("data/2026/a.xml")));
        assertEquals(Optional.of(abs("s/order.schema.json")), loaded.schemaFor(dir.resolve("x/a.json")));
        assertTrue(loaded.assignmentFor(dir.resolve("other/a.xml")).isEmpty());
        assertEquals(FxtProject.Assignment.NONE, loaded.assignmentFor(dir.getParent().resolve("outside.xml")));
        assertTrue(loaded.scenarioFor(dir.resolve("data/a.xml")).isEmpty(), "Report does not exist as a scenario");
    }

    @Test
    @DisplayName("old project files load unchanged; bindSchema keeps Schematron and scenario of the pattern")
    void compatible() throws Exception {
        Path file = dir.resolve("old.fxtproj");
        Files.writeString(file, """
                { "name": "Old", "schemaBindings": [ { "files": "*.xml", "schema": "a.xsd" } ] }
                """);
        FxtProject project = FxtProject.load(file);
        assertEquals(Optional.of(abs("a.xsd")), project.schemaFor(dir.resolve("x.xml")));
        project.bindingWithPattern("*.xml").schematron = "r.sch";
        project.bindSchema("*.xml", dir.resolve("b.xsd"));
        assertEquals(1, project.schemaBindings().size());
        assertEquals("b.xsd", project.schemaBindings().get(0).schema);
        assertEquals("r.sch", project.schemaBindings().get(0).schematron);
        project.save();
        assertFalse(Files.readString(file).contains("\"scenario\""), "unset parts are not written");
    }

    @Test
    @DisplayName("a scenario prepared for a file fills ${file}, ${name} and ${dir}")
    void scenarioForFile() {
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");
        FxtProject.Scenario scenario = new FxtProject.Scenario("R", "x.xsl", FxtProject.VAR_FILE,
                Map.of("source", "${file}"), "HTML", "out/${dir}/${name}.html");
        FxtProject.Scenario prepared = project.scenarioForFile(scenario, dir.resolve("data/2026/a.xml"));
        assertEquals("data/2026/a.xml", prepared.input);
        assertEquals("data/2026/a.xml", prepared.parameters.get("source"));
        assertEquals("out/data/2026/a.html", prepared.output);
        assertEquals("${file}", scenario.input, "the scenario itself is not changed");

        FxtProject.Scenario noInput = new FxtProject.Scenario("R", "x.xsl", null, null, null, null);
        assertTrue(noInput.takesFile());
        assertEquals("b.xml", project.scenarioForFile(noInput, dir.resolve("b.xml")).input);
        FxtProject.Scenario fixed = new FxtProject.Scenario("R", "x.xsl", "fixed.xml", null, null, null);
        assertFalse(fixed.takesFile());
        assertEquals("fixed.xml", project.scenarioForFile(fixed, dir.resolve("b.xml")).input);
        assertEquals(".", project.scenarioForFile(
                new FxtProject.Scenario("R", "x.xsl", null, null, null, "${dir}"), dir.resolve("b.xml")).output);
    }

    @Test
    @DisplayName("settings setters drop empty entries; files of a pattern and the validated files are listed")
    void settingsAndFiles() throws Exception {
        Files.createDirectories(dir.resolve("data/sub"));
        Files.createDirectories(dir.resolve(".git"));
        for (String f : List.of("data/a.xml", "data/sub/b.xml", "data/c.txt", ".git/d.xml", "e.xml")) {
            Files.writeString(dir.resolve(f), "<r/>");
        }
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");
        project.setBindings(List.of(new FxtProject.SchemaBinding("data/**/*.xml", "s.xsd"),
                new FxtProject.SchemaBinding("", "x.xsd"), new FxtProject.SchemaBinding("*.xml", null, null, null)));
        assertEquals(1, project.schemaBindings().size());
        project.setFolders(List.of());
        assertEquals(List.of("."), project.folderEntries());
        project.setCatalogs(List.of("c.xml", "c.xml", " "));
        assertEquals(List.of("c.xml"), project.catalogEntries());
        project.setScenarios(List.of(new FxtProject.Scenario(null, "x.xsl", null, null, null, null),
                new FxtProject.Scenario("A", "x.xsl", null, null, null, null)));
        assertEquals(1, project.scenarios().size());

        assertEquals(List.of(abs("data/a.xml"), abs("data/sub/b.xml")), project.filesMatching("data/**/*.xml", 100));
        assertEquals(List.of(abs("data/a.xml"), abs("data/sub/b.xml")), project.validatedFiles(100),
                "hidden folders and unbound files are skipped");
        assertEquals(1, project.filesMatching("**/*.xml", 1).size(), "the limit holds");
        assertFalse(FxtProject.isValidPattern("data/[a"));
        assertTrue(project.filesMatching("data/[a", 10).isEmpty(), "an invalid pattern matches nothing");
    }

    @Test
    @DisplayName("scenarios run for files: one output per file, a second file may not overwrite it")
    void runForFiles() throws Exception {
        Files.createDirectories(dir.resolve("x"));
        Files.writeString(dir.resolve("x/count.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:param name="label"/>
                  <xsl:template match="/"><xsl:value-of select="$label"/>:<xsl:value-of select="count(//i)"/></xsl:template>
                </xsl:stylesheet>
                """);
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("data/a.xml"), "<r><i/></r>");
        Files.writeString(dir.resolve("data/b.xml"), "<r><i/><i/></r>");
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");
        FxtProject.Scenario perFile = new FxtProject.Scenario("count", "x/count.xsl", FxtProject.VAR_FILE,
                Map.of("label", "${file}"), "TEXT", "out/${name}.txt");
        List<Path> files = List.of(dir.resolve("data/a.xml"), dir.resolve("data/b.xml"));

        var results = ScenarioRunner.runAll(project, perFile, files, null, null);
        assertTrue(results.stream().allMatch(r -> r.result().ok()), results.toString());
        assertEquals("data/a.xml:1", Files.readString(dir.resolve("out/a.txt")));
        assertEquals("data/b.xml:2", Files.readString(dir.resolve("out/b.txt")));

        FxtProject.Scenario oneOutput = new FxtProject.Scenario("count", "x/count.xsl", null, null, "TEXT", "out/all.txt");
        results = ScenarioRunner.runAll(project, oneOutput, files, null, null);
        assertTrue(results.get(0).result().ok());
        assertFalse(results.get(1).result().ok());
        assertTrue(results.get(1).result().error().contains("${name}"), results.get(1).result().error());

        var unresolved = ScenarioRunner.run(project, perFile);
        assertFalse(unresolved.ok(), "without a file ${file} cannot be resolved");
        assertTrue(ScenarioRunner.run(project, perFile, dir.resolve("data/a.xml")).ok());

        var cancelled = ScenarioRunner.runAll(project, perFile, files, null, () -> true);
        assertTrue(cancelled.isEmpty());
    }

    @Test
    @DisplayName("a PDF scenario renders the stylesheet's XSL-FO with FOP; it needs an output file")
    void pdfScenario() throws Exception {
        Files.writeString(dir.resolve("fo.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                  <xsl:param name="title" select="'none'"/>
                  <xsl:template match="/">
                    <fo:root>
                      <fo:layout-master-set>
                        <fo:simple-page-master master-name="p" page-height="29.7cm" page-width="21cm">
                          <fo:region-body/>
                        </fo:simple-page-master>
                      </fo:layout-master-set>
                      <fo:page-sequence master-reference="p">
                        <fo:flow flow-name="xsl-region-body"><fo:block><xsl:value-of select="$title"/></fo:block></fo:flow>
                      </fo:page-sequence>
                    </fo:root>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        Files.writeString(dir.resolve("in.xml"), "<r/>");
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");
        var pdf = new FxtProject.Scenario("pdf", "fo.xsl", "${file}", Map.of("title", "Hello"), "PDF", "out/${name}.pdf");
        assertTrue(pdf.isPdf());
        var result = ScenarioRunner.run(project, pdf, dir.resolve("in.xml"));
        assertTrue(result.ok(), result.error());
        assertEquals(abs("out/in.pdf"), result.written());
        byte[] bytes = Files.readAllBytes(dir.resolve("out/in.pdf"));
        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        var noOutput = new FxtProject.Scenario("pdf", "fo.xsl", "in.xml", null, "PDF", null);
        assertFalse(ScenarioRunner.run(project, noOutput).ok());
        var escape = new FxtProject.Scenario("pdf", "fo.xsl", "in.xml", null, "PDF", "../x.pdf");
        assertTrue(ScenarioRunner.run(project, escape).error().contains("Refused"));
    }

    @Test
    @DisplayName("recent projects: newest first, at most eight, missing files left out; a changed file is reloaded")
    void recentAndReload() throws Exception {
        ProjectService service = ProjectService.getInstance();
        service.clearRecent();
        for (int i = 0; i < 10; i++) {
            service.create(dir.resolve("p" + i + ".fxtproj"), "P" + i);
        }
        List<Path> recent = service.recent();
        assertEquals(ProjectService.MAX_RECENT, recent.size());
        assertEquals(abs("p9.fxtproj"), recent.get(0));
        Files.delete(dir.resolve("p8.fxtproj"));
        assertFalse(service.recent().contains(abs("p8.fxtproj")));
        service.open(dir.resolve("p5.fxtproj"));
        assertEquals(abs("p5.fxtproj"), service.recent().get(0));

        FxtProject[] heard = {null};
        java.util.function.Consumer<FxtProject> listener = p -> heard[0] = p;
        service.addListener(listener);
        assertFalse(service.reloadIfChanged(), "unchanged");
        Path file = dir.resolve("p5.fxtproj");
        Files.writeString(file, "{ \"name\": \"Renamed\", \"schemaBindings\": [ { \"files\": \"*.xml\", \"schematron\": \"r.sch\" } ] }");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertTrue(service.reloadIfChanged());
        assertEquals("Renamed", service.current().orElseThrow().name());
        assertEquals("Renamed", heard[0].name());
        assertEquals(abs("r.sch"), service.assignmentFor(dir.resolve("a.xml")).schematron());

        Files.writeString(file, "{ broken");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10000));
        assertFalse(service.reloadIfChanged(), "a broken file keeps the project");
        assertEquals("Renamed", service.current().orElseThrow().name());
        service.removeListener(listener);
        service.clearRecent();
        assertTrue(service.recent().isEmpty());
    }
}
