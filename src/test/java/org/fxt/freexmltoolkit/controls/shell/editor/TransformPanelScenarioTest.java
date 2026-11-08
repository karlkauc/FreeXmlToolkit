package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Transform panel's SCENARIO section: shown with a project, load / save / delete / run scenarios. */
@ExtendWith(ApplicationExtension.class)
class TransformPanelScenarioTest {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:param name="label" select="'n'"/>
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="$label"/>=<xsl:value-of select="count(//i)"/></xsl:template>
            </xsl:stylesheet>
            """;

    @TempDir
    Path dir;

    private EditorHost host;
    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 700));
        stage.show();
    }

    @AfterEach
    void closeProject() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> ProjectService.getInstance().close());
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, action);
    }

    @Test
    void scenariosLoadSaveDeleteAndRun() throws Exception {
        Files.createDirectories(dir.resolve("xsl"));
        Files.writeString(dir.resolve("xsl/count.xsl"), XSLT);
        Files.writeString(dir.resolve("a.xml"), "<r><i/><i/><i/></r>");
        assertFalse(fx(() -> panel.scenarioSectionForTesting().isVisible()), "no project, no SCENARIO section");

        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "Orders");
        project.putScenario(new FxtProject.Scenario("Count", "xsl/count.xsl", "a.xml", Map.of("label", "items"),
                "TEXT", "out/count.txt"));
        project.save();
        fx(() -> ProjectService.getInstance().open(project.file()));
        assertTrue(fx(() -> panel.scenarioSectionForTesting().isVisible()));
        assertEquals("SCENARIO · Orders", fx(() -> panel.scenarioSectionForTesting().getTitle()));
        assertEquals(List.of("Count"), fx(() -> List.copyOf(panel.scenarioChoiceForTesting().getItems())));

        // Choosing it fills the panel
        fx(() -> {
            panel.scenarioChoiceForTesting().setValue("Count");
            panel.onScenarioChosen();
            return null;
        });
        assertEquals(dir.resolve("xsl/count.xsl").toFile(), fx(panel::currentXsltFile));
        assertEquals(dir.resolve("a.xml").toFile(), fx(panel::currentInputOverride));
        assertEquals(Map.of("label", "items"), fx(panel::collectParameters));
        assertEquals(OutputFormat.TEXT, fx(() -> panel.getFormatChoice().format));
        assertEquals("Output: out/count.txt", fx(panel::scenarioInfoForTesting));

        // Run Scenario writes the output file and shows the result
        fx(() -> {
            panel.onRunScenario();
            return null;
        });
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Files.isRegularFile(dir.resolve("out/count.txt")));
        assertEquals("items=3", Files.readString(dir.resolve("out/count.txt")));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(panel::scenarioInfoForTesting).startsWith("Written"));
        assertEquals("Written to out/count.txt", fx(panel::scenarioInfoForTesting));

        // Save the current settings under a new name: stored relative to the project
        fx(() -> {
            panel.addParameter("year", "2026");
            panel.saveScenario(ProjectService.getInstance().current().orElseThrow(), "Copy", null);
            return null;
        });
        FxtProject reloaded = FxtProject.load(dir.resolve("p.fxtproj"));
        FxtProject.Scenario copy = reloaded.scenario("Copy").orElseThrow();
        assertEquals("xsl/count.xsl", copy.stylesheet);
        assertEquals("a.xml", copy.input);
        assertEquals(Map.of("label", "items", "year", "2026"), copy.parameters);
        assertEquals("TEXT", copy.outputFormat);
        assertNull(copy.output);
        assertEquals(List.of("Count", "Copy"), fx(() -> List.copyOf(panel.scenarioChoiceForTesting().getItems())));
        assertEquals("Copy", fx(() -> panel.scenarioChoiceForTesting().getValue()));

        fx(() -> {
            panel.deleteScenario(ProjectService.getInstance().current().orElseThrow(), "Copy");
            return null;
        });
        assertTrue(FxtProject.load(dir.resolve("p.fxtproj")).scenario("Copy").isEmpty());
        assertEquals(List.of("Count"), fx(() -> List.copyOf(panel.scenarioChoiceForTesting().getItems())));

        fx(() -> {
            ProjectService.getInstance().close();
            return null;
        });
        assertFalse(fx(() -> panel.scenarioSectionForTesting().isVisible()), "closing the project hides it");
    }
}
