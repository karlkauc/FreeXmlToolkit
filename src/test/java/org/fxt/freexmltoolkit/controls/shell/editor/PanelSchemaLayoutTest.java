package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.search.TextSearchPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The shared panel schema: the tools first and open, then the content, the primary action
 * and its results.
 */
@ExtendWith(ApplicationExtension.class)
class PanelSchemaLayoutTest {

    private EditorHost host;
    private ExplorerPanel explorer;
    private TypeLibraryPanel schema;
    private ValidationPanel validation;
    private TextSearchPane search;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        explorer = new ExplorerPanel(host);
        schema = new TypeLibraryPanel(host);
        validation = new ValidationPanel(host);
        search = new TextSearchPane(host, () -> null);
        // Headless Monocle has a small virtual screen: keep the scene within ~1100 × 600.
        host.setPrefWidth(300);
        for (javafx.scene.layout.Region panel : new javafx.scene.layout.Region[]{explorer, schema, validation, search}) {
            panel.setPrefWidth(200);
        }
        stage.setScene(new Scene(new HBox(host, explorer, schema, validation, search), 1100, 600));
        stage.show();
    }

    @Test
    void explorerShowsItsOpenToolsBeforeTheFiles() {
        WaitForAsyncUtils.waitForFxEvents();
        int open = indexIn(explorer, "#explorer-open-editors-header");
        int workspace = indexIn(explorer, "#explorer-workspace-header");
        int tools = indexIn(explorer, "#explorer-tools-header");
        assertTrue(tools < open && open < workspace,
                "order must be TOOLS, OPEN EDITORS, WORKSPACE: " + tools + "/" + open + "/" + workspace);
        Node openFileRow = explorer.lookup("#explorer-open-file");
        assertTrue(isShowing(openFileRow), "TOOLS starts open");
        assertNotNull(explorer.lookup("#explorer-open-editors-scroll"), "OPEN EDITORS scrolls inside a capped height");
    }

    @Test
    void schemaPanelShowsItsToolsFirstAndKeepsXsdGenerationOutOfThePanel() {
        WaitForAsyncUtils.waitForFxEvents();
        var tools = (SidePanelSection) schema.lookup("#schema-tools-section");
        assertNotNull(tools);
        assertFalse(tools.isCollapsed(), "the schema TOOLS start open");
        Node filter = schema.lookup("#schema-filter");
        assertTrue(tools.localToScene(0, 0).getY() < filter.localToScene(0, 0).getY(),
                "the tools come before the type filter and lists");
        assertNull(schema.lookup("#schema-tool-generate"), "Generate XSD from XML moved to the Schema ▾ menu");
    }

    @Test
    void validationShowsResultsOnlyInBatchModeAndOffersAJumpToTheDock(@TempDir Path tmp) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        Node results = validation.lookup("#validation-results-section");
        assertFalse(results.isVisible(), "single-file mode has no RESULTS section");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            validation.setBatchMode(true);
            return null;
        });
        assertTrue(results.isVisible(), "batch mode shows RESULTS");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            validation.setBatchMode(false);
            return null;
        });

        Path xml = tmp.resolve("broken.xml");
        Files.writeString(xml, "<root><unclosed></root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("unclosed")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            validation.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> validation.getProblemCount() > 0);
        Button jump = (Button) validation.lookup("#validation-show-problems");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, jump::isVisible);
        assertTrue(jump.getText().contains("show below the editor"), jump.getText());
        int before = host.problemsRevealRequestsProperty().get();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            jump.fire();
            return null;
        });
        assertEquals(before + 1, host.problemsRevealRequestsProperty().get(), "the jump reveals the PROBLEMS dock");
    }

    @Test
    void validationToolsComeInTwoOpenGroupsAboveTheSources() {
        WaitForAsyncUtils.waitForFxEvents();
        var reports = (SidePanelSection) validation.lookup("#validation-reports-section");
        var authoring = (SidePanelSection) validation.lookup("#validation-authoring-section");
        assertNotNull(reports);
        assertNotNull(authoring);
        assertFalse(reports.isCollapsed() || authoring.isCollapsed());
        Node sources = validation.lookup("#validation-xsd-row");
        assertTrue(reports.localToScene(0, 0).getY() < authoring.localToScene(0, 0).getY());
        assertTrue(authoring.localToScene(0, 0).getY() < sources.localToScene(0, 0).getY(),
                "the tools come before the sources");
        assertNotNull(authoring.lookup("#validation-tool-builder"), "Rule Builder is a Schematron authoring tool");
        assertNotNull(reports.lookup("#validation-export-problems"), "the export is a report");
    }

    @Test
    void validationKeepsItsRunButtonInsideThePanel(@TempDir Path tmp) throws Exception {
        // A document shows its source rows, the tallest state of the fixed part of the panel.
        Path xml = tmp.resolve("sample.xml");
        Files.writeString(xml, "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        Node run = validation.lookup("#validation-run");
        double bottom = validation.sceneToLocal(run.localToScene(run.getBoundsInLocal())).getMaxY();
        assertTrue(bottom <= validation.getHeight(),
                "Run Validation ends at " + bottom + " of " + validation.getHeight());
    }

    @Test
    void searchOptionsAreLabelledSwitches() {
        WaitForAsyncUtils.waitForFxEvents();
        for (String[] option : new String[][]{{"#search-opt-case", "Match case"}, {"#search-opt-word", "Whole word"},
                {"#search-opt-regex", "Regex"}, {"#search-replace-toggle", "Replace"}}) {
            ToggleButton toggle = (ToggleButton) search.lookup(option[0]);
            assertNotNull(toggle, option[0]);
            assertEquals(option[1], toggle.getText());
        }
    }

    private static int indexIn(javafx.scene.Parent parent, String selector) {
        Node node = parent.lookup(selector);
        assertNotNull(node, selector);
        return parent.getChildrenUnmodifiable().indexOf(node);
    }

    /** @return whether {@code node} and all its ancestors are visible */
    private static boolean isShowing(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }
}
