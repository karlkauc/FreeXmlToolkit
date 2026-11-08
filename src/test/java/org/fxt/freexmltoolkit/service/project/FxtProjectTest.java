package org.fxt.freexmltoolkit.service.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.controls.shell.editor.ScenarioRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests project files, the project service and running scenarios. */
@DisplayName("FxtProject")
class FxtProjectTest {

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        ProjectService.getInstance().resetForTesting();
    }

    @Test
    @DisplayName("save and load keep relative paths; schema bindings by glob; scenarios replace by name")
    void roundTrip() throws Exception {
        Path file = dir.resolve("orders.fxtproj");
        FxtProject project = FxtProject.create(file, "Orders");
        project.bindSchema("data/**/*.xml", dir.resolve("schemas/order.xsd"));
        project.bindSchema("special.xml", dir.resolve("schemas/special.xsd"));
        project.addCatalog(dir.resolve("schemas/catalog.xml"));
        project.putScenario(new FxtProject.Scenario("Report", "xsl/report.xsl", "data/a.xml", Map.of("year", "2026"),
                "HTML", "out/report.html"));
        project.putScenario(new FxtProject.Scenario("Report", "xsl/report2.xsl", "data/a.xml", Map.of(), null, null));
        project.save();
        String json = Files.readString(file);
        assertTrue(json.contains("\"schema\": \"schemas/order.xsd\"") && !json.contains(dir.toString()), json);

        FxtProject loaded = FxtProject.load(file);
        assertEquals("Orders", loaded.name());
        assertEquals(List.of(dir.toAbsolutePath().normalize()), loaded.folders());
        assertEquals(Optional.of(dir.resolve("schemas/order.xsd").toAbsolutePath().normalize()),
                loaded.schemaFor(dir.resolve("data/2026/a.xml")));
        assertEquals(Optional.of(dir.resolve("schemas/order.xsd").toAbsolutePath().normalize()),
                loaded.schemaFor(dir.resolve("data/a.xml")), "**/ also matches the folder itself");
        assertEquals(Optional.of(dir.resolve("schemas/special.xsd").toAbsolutePath().normalize()),
                loaded.schemaFor(dir.resolve("special.xml")));
        assertEquals(Optional.empty(), loaded.schemaFor(dir.resolve("other/x.xml")));
        assertEquals(Optional.empty(), loaded.schemaFor(dir.getParent().resolve("outside.xml")));
        assertEquals(1, loaded.scenarios().size());
        assertEquals("xsl/report2.xsl", loaded.scenario("Report").orElseThrow().stylesheet);
        assertEquals(List.of(dir.resolve("schemas/catalog.xml").toAbsolutePath().normalize()), loaded.catalogs());
        assertTrue(FxtProject.isProjectFile(file));

        Files.writeString(dir.resolve("broken.fxtproj"), "{ not json");
        assertThrows(IOException.class, () -> FxtProject.load(dir.resolve("broken.fxtproj")));
    }

    @Test
    @DisplayName("the service opens, reopens the last project and closes it")
    void service() throws Exception {
        ProjectService service = ProjectService.getInstance();
        Path file = dir.resolve("p.fxtproj");
        FxtProject[] heard = {null};
        boolean[] closed = {false};
        service.addListener(p -> {
            heard[0] = p;
            closed[0] = p == null;
        });
        service.create(file, "P");
        assertEquals("P", heard[0].name());
        service.current().orElseThrow().bindSchema("*.xml", dir.resolve("s.xsd"));
        service.save();
        assertEquals(Optional.of(dir.resolve("s.xsd").toAbsolutePath().normalize()), service.schemaFor(dir.resolve("a.xml")));

        service.resetForTesting();
        assertTrue(service.current().isEmpty());
        assertEquals("P", service.reopenLast().orElseThrow().name(), "the last project opens again");
        service.close();
        assertTrue(closed[0]);
        assertTrue(service.reopenLast().isEmpty(), "closing forgets it");
    }

    @Test
    @DisplayName("scenarios run XSLT and XQuery and write their output")
    void scenarios() throws Exception {
        Files.createDirectories(dir.resolve("xsl"));
        Files.writeString(dir.resolve("xsl/count.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:param name="label" select="'n'"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="$label"/>=<xsl:value-of select="count(//i)"/></xsl:template>
                </xsl:stylesheet>
                """);
        Files.writeString(dir.resolve("data.xml"), "<r><i/><i/><i/></r>");
        Files.writeString(dir.resolve("sum.xq"), "sum(//i ! 1)");
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");

        var xslt = ScenarioRunner.run(project, new FxtProject.Scenario("count", "xsl/count.xsl", "data.xml",
                Map.of("label", "items"), null, "out/count.txt"));
        assertTrue(xslt.ok(), xslt.error());
        assertEquals("items=3", xslt.text());
        assertEquals("items=3", Files.readString(dir.resolve("out/count.txt")));

        var xquery = ScenarioRunner.run(project, new FxtProject.Scenario("sum", "sum.xq", "data.xml", Map.of(), "TEXT", null));
        assertTrue(xquery.ok(), xquery.error());
        assertEquals("3", xquery.text().strip());
        assertEquals(null, xquery.written());

        var missing = ScenarioRunner.run(project, new FxtProject.Scenario("x", "nope.xsl", "data.xml", Map.of(), null, null));
        assertFalse(missing.ok());
        assertTrue(missing.error().startsWith("Not found"), missing.error());

        // A project file must not write outside its folder (../.bashrc, absolute paths)
        for (String output : List.of("../escape.txt", "out/../../escape.txt", dir.getParent().resolve("abs.txt").toString())) {
            var escaping = ScenarioRunner.run(project, new FxtProject.Scenario("x", "xsl/count.xsl", "data.xml",
                    Map.of(), null, output));
            assertFalse(escaping.ok(), output);
            assertTrue(escaping.error().contains("Refused"), escaping.error());
        }
        assertFalse(Files.exists(dir.getParent().resolve("escape.txt")));
        assertFalse(Files.exists(dir.getParent().resolve("abs.txt")));
    }
}
