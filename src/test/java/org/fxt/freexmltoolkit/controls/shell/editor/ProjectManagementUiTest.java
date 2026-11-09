package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Managing projects in the UI: Project Settings (bindings, scenarios, renames), the binding and
 * scenario dialogs, the Explorer's project actions and tooltips, the Transform panel choosing a
 * file's bound scenario, and Validate Project.
 */
@ExtendWith(ApplicationExtension.class)
class ProjectManagementUiTest {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="count(//i)"/></xsl:template>
            </xsl:stylesheet>
            """;

    @TempDir
    Path dir;

    private EditorHost host;
    private ExplorerPanel explorer;
    private TransformPanel transform;
    private ValidationPanel validation;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        explorer = new ExplorerPanel(host);
        transform = new TransformPanel(host);
        validation = new ValidationPanel(host);
        stage.setScene(new Scene(new HBox(host, explorer, transform, validation), 1200, 700));
        stage.show();
    }

    @AfterEach
    void closeProject() throws Exception {
        fx(() -> {
            ProjectService.getInstance().close();
            return null;
        });
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, action);
    }

    private FxtProject project() throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.createDirectories(dir.resolve("xsl"));
        Files.writeString(dir.resolve("xsl/count.xsl"), XSLT);
        Files.writeString(dir.resolve("data/a.xml"), "<r><i/></r>");
        Files.writeString(dir.resolve("data/b.xml"), "<r><i/><i/></r>");
        Files.writeString(dir.resolve("s.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="r"><xs:complexType><xs:sequence>
                    <xs:element name="i" maxOccurs="1"/></xs:sequence></xs:complexType></xs:element>
                </xs:schema>
                """);
        Files.writeString(dir.resolve("rules.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern><rule context="r"><assert test="i">needs an i</assert></rule></pattern>
                </schema>
                """);
        FxtProject project = FxtProject.create(dir.resolve("p.fxtproj"), "P");
        project.putScenario(new FxtProject.Scenario("Count", "xsl/count.xsl", FxtProject.VAR_FILE, Map.of(), "TEXT",
                "out/${name}.txt"));
        project.bind(new FxtProject.SchemaBinding("data/*.xml", "s.xsd", "rules.sch", "Count"));
        project.save();
        fx(() -> ProjectService.getInstance().open(project.file()));
        return ProjectService.getInstance().current().orElseThrow();
    }

    @Test
    void settingsEditBindingsAndScenarios() throws Exception {
        FxtProject project = project();
        ProjectSettingsDialog dialog = fx(() -> new ProjectSettingsDialog(project));
        fx(() -> {
            // the editors are stubbed: they answer as the user would
            dialog.scenarioEditor = (scenario, others) -> {
                FxtProject.Scenario renamed = scenario.copy();
                renamed.name = "Count items";
                return Optional.of(renamed);
            };
            dialog.scenarioTable().getSelectionModel().select(0);
            dialog.onEditScenario();
            dialog.bindingEditor = binding -> Optional.of(new FxtProject.SchemaBinding("**/*.xml", null, "rules.sch", null));
            dialog.onAddBinding();
            dialog.bindingTable().getSelectionModel().select(1);
            dialog.onBindingUp();
            dialog.addFolder("data");
            dialog.applyTo(project);
            return null;
        });
        assertEquals("Count items", project.scenarios().get(0).name);
        assertEquals("Count items", project.schemaBindings().get(1).scenario, "the binding follows the rename");
        assertEquals("**/*.xml", project.schemaBindings().get(0).files, "moved up");
        assertEquals(List.of(".", "data"), project.folderEntries());

        fx(() -> {
            dialog.scenarioTable().getSelectionModel().select(0);
            dialog.onDuplicateScenario();
            dialog.scenarioTable().getSelectionModel().select(0);
            dialog.onRemoveScenario();
            dialog.applyTo(project);
            return null;
        });
        assertEquals(1, project.scenarios().size());
        assertTrue(project.scenarios().get(0).name.startsWith("Count items "), project.scenarios().get(0).name);
        assertNull(project.schemaBindings().get(1).scenario, "the removed scenario is gone from its binding");
        assertEquals("s.xsd", project.schemaBindings().get(1).schema, "the binding keeps its other parts");
    }

    @Test
    void bindingAndScenarioDialogs() throws Exception {
        FxtProject project = project();
        BindSchemaDialog bind = fx(() -> new BindSchemaDialog(project, new FxtProject.SchemaBinding("data/*.xml", null)));
        assertNotNull(fx(bind::validate), "nothing assigned yet");
        fx(() -> {
            bind.fillForTesting("data/*.xml", "", "rules.sch", "Count");
            return null;
        });
        BindSchemaDialog.Request request = fx(bind::requestForTesting);
        assertEquals(dir.resolve("rules.sch").toAbsolutePath().normalize(), request.schematron().toAbsolutePath().normalize());
        assertEquals("Count", request.scenario());
        assertNull(request.schema());
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(bind::matchesText).contains("2"));
        fx(() -> {
            bind.fillForTesting("data/[x", "", "rules.sch", "");
            return null;
        });
        assertNotNull(fx(bind::validate), "an invalid pattern");
        fx(() -> {
            bind.fillForTesting("data/*.xml", "missing.xsd", "", "");
            return null;
        });
        assertNotNull(fx(bind::validate), "a missing schema");

        ScenarioEditDialog edit = fx(() -> new ScenarioEditDialog(project,
                new FxtProject.Scenario(null, null, FxtProject.VAR_FILE, null, null, null), java.util.Set.of("Count")));
        fx(() -> {
            edit.fillForTesting("Count", "xsl/count.xsl", "${file}", "", "", "");
            return null;
        });
        assertNotNull(fx(edit::validate), "the name is taken");
        fx(() -> {
            edit.fillForTesting("Pdf", "xsl/count.xsl", "${file}", "a = 1\n\nb=x=y", "PDF", "");
            return null;
        });
        assertNotNull(fx(edit::validate), "PDF needs an output file");
        fx(() -> {
            edit.fillForTesting("Pdf", "xsl/count.xsl", "${file}", "a = 1\n\nb=x=y", "PDF", "out/${name}.pdf");
            return null;
        });
        FxtProject.Scenario scenario = fx(edit::scenarioForTesting);
        assertEquals(Map.of("a", "1", "b", "x=y"), scenario.parameters);
        assertEquals("PDF", scenario.outputFormat);
        assertEquals("out/${name}.pdf", scenario.output);
    }

    @Test
    void explorerShowsAndRunsTheProject() throws Exception {
        FxtProject project = project();
        String note = fx(() -> ExplorerPanel.projectNote(dir.resolve("data/a.xml")));
        assertTrue(note.contains("s.xsd") && note.contains("rules.sch") && note.contains("Count"), note);
        assertNull(fx(() -> ExplorerPanel.projectNote(dir.resolve("other.xml"))));

        List<MenuItem> items = fx(() -> explorer.projectContextMenuItems(List.of(dir.resolve("data/a.xml"))));
        List<String> ids = items.stream().map(MenuItem::getId).filter(java.util.Objects::nonNull).toList();
        assertEquals(List.of("explorer-ctx-project-bind", "explorer-ctx-project-run", "explorer-ctx-project-validate",
                "explorer-ctx-project-settings"), ids);
        javafx.scene.control.Menu run = (javafx.scene.control.Menu) items.stream()
                .filter(i -> "explorer-ctx-project-run".equals(i.getId())).findFirst().orElseThrow();
        assertTrue(run.getItems().get(0).getText().startsWith("Count"), "the bound scenario comes first");

        // Running it for both files writes one output each
        fx(() -> {
            ProjectActions.runScenario(host, project, project.scenario("Count").orElseThrow(),
                    List.of(dir.resolve("data/a.xml"), dir.resolve("data/b.xml")));
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.isRegularFile(dir.resolve("out/b.txt")));
        assertEquals("1", Files.readString(dir.resolve("out/a.txt")));
        assertEquals("2", Files.readString(dir.resolve("out/b.txt")));

        assertEquals("data/*.xml", ProjectActions.patternFor(project,
                List.of(dir.resolve("data/a.xml"), dir.resolve("data/b.xml"))));
        assertEquals("**/*.xml", ProjectActions.patternFor(project,
                List.of(dir.resolve("data/a.xml"), dir.resolve("x.xml"))));
        assertEquals("data/a.xml", ProjectActions.patternFor(project, List.of(dir.resolve("data/a.xml"))));

        fx(() -> {
            ProjectService.getInstance().close();
            return null;
        });
        assertTrue(fx(() -> explorer.projectContextMenuItems(List.of(dir.resolve("data/a.xml")))).isEmpty());
        // a project file in the workspace opens as the project
        fx(() -> {
            explorer.openWorkspaceFile(dir.resolve("p.fxtproj"));
            return null;
        });
        assertEquals("P", ProjectService.getInstance().current().map(FxtProject::name).orElse(null));
    }

    @Test
    void transformPanelChoosesTheBoundScenario() throws Exception {
        project();
        fx(() -> {
            host.openFile(dir.resolve("data/a.xml"));
            return null;
        });
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> "Count".equals(fx(() -> transform.scenarioChoiceForTesting().getValue())));
        assertEquals("Count", fx(transform::boundScenarioChoice));
        assertEquals(dir.resolve("xsl/count.xsl").toFile(), fx(transform::currentXsltFile));
        assertNull(fx(transform::currentInputOverride), "${file}: the active document is the input");

        fx(() -> {
            transform.onRunScenario();
            return null;
        });
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Files.isRegularFile(dir.resolve("out/a.txt")));
        assertEquals("1", Files.readString(dir.resolve("out/a.txt")));
    }

    @Test
    void validateProjectChecksEachFileWithItsBindings() throws Exception {
        FxtProject project = project();
        var results = ValidationRunner.batchProject(project,
                List.of(dir.resolve("data/a.xml").toFile(), dir.resolve("data/b.xml").toFile()), null, null);
        assertFalse(results.get(0).failed(), results.get(0).problems().toString());
        assertTrue(results.get(1).failed(), "b.xml has two i, the schema allows one");

        fx(() -> {
            validation.runProjectBatch();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> fx(validation::batchResultCount) == 2);
        assertEquals(1L, fx(validation::batchFailedCount));
        assertTrue(fx(validation::isBatchMode));
    }
}
