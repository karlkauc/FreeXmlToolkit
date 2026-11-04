package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The file tools of the Explorer's TOOLS list (Open from URL, Compare files, Spreadsheet
 * Converter, Generate XSD from XML files) and their selection-based behaviour, also from the
 * workspace context menu.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelFileToolsTest {

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void twoSelectedFilesAreComparedWithEachOther(@TempDir Path tmp) throws Exception {
        Path a = write(tmp, "a.xml", "<a>1</a>");
        Path b = write(tmp, "b.xml", "<a>2</a>");
        select(tmp, a, b);

        fx(panel::onCompare);

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> tabTexts().contains("Compare: a.xml ↔ b.xml"));
    }

    @Test
    void compareFallsBackToTheShellActionWithoutASelection() {
        AtomicInteger calls = new AtomicInteger();
        fx(() -> {
            panel.setCompareAction(calls::incrementAndGet);
            panel.onCompare();
        });
        assertEquals(1, calls.get(), "without a selection the active document is compared (Tools menu action)");
    }

    @Test
    void compareRowIsEnabledOnlyWithASelectionOrAnActiveDocument(@TempDir Path tmp) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        Button compare = (Button) panel.lookup("#explorer-compare");
        assertTrue(compare.isDisabled(), "nothing to compare without a selection or an active document");

        Path a = write(tmp, "a.xml", "<a/>");
        select(tmp, a);
        assertFalse(compare.isDisabled(), "a selected file can be compared");
    }

    @Test
    void generateXsdUsesTheSelectedXmlFilesOrAsksForFiles(@TempDir Path tmp) throws Exception {
        AtomicReference<List<File>> received = new AtomicReference<>(List.of());
        fx(() -> panel.setXsdBatchAction(received::set));

        fx(panel::onGenerateXsdBatch);
        assertNull(received.get(), "without a selection the user picks the files (null list)");

        Path a = write(tmp, "a.xml", "<a/>");
        Path s = write(tmp, "s.xsd", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        Path b = write(tmp, "b.xml", "<b/>");
        select(tmp, a, s, b);
        fx(panel::onGenerateXsdBatch);
        assertEquals(List.of(a.toFile(), b.toFile()), received.get(), "only the selected XML files are passed on");
    }

    @Test
    void spreadsheetRowRunsTheShellAction() {
        AtomicInteger calls = new AtomicInteger();
        fx(() -> {
            panel.setSpreadsheetAction(calls::incrementAndGet);
            ((Button) panel.lookup("#explorer-spreadsheet")).fire();
        });
        assertEquals(1, calls.get());
    }

    @Test
    void contextMenuOffersCompareAndXsdForTheSelection(@TempDir Path tmp) throws Exception {
        Path a = write(tmp, "a.xml", "<a/>");
        Path b = write(tmp, "b.xml", "<b/>");

        select(tmp, a);
        MenuItem compare = item("explorer-ctx-compare");
        MenuItem xsd = item("explorer-ctx-generate-xsd");
        assertTrue(compare.isDisable(), "comparing selected files needs exactly two");
        assertFalse(xsd.isDisable(), "one selected XML file is enough to infer a schema");

        select(tmp, a, b);
        assertFalse(item("explorer-ctx-compare").isDisable(), "two selected files can be compared");
    }

    // ----- helpers ------------------------------------------------------------------------

    private static Path write(Path dir, String name, String text) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    /** Opens {@code root} as the workspace and selects exactly {@code files} in the tree. */
    private void select(Path root, Path... files) throws Exception {
        if (!root.equals(panel.getWorkspaceFolder())) {
            fx(() -> panel.setWorkspaceFolder(root));
            // The root's children load off the FX thread and replace the items (dropping any
            // selection made before), so wait until every file of the folder is listed.
            long count;
            try (var stream = Files.list(root)) {
                count = stream.count();
            }
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> tree().getRoot() != null && tree().getRoot().getChildren().size() == count));
            WaitForAsyncUtils.waitForFxEvents();
        }
        fx(() -> {
            tree().getSelectionModel().clearSelection();
            for (Path file : files) {
                panel.revealFile(file);
            }
        });
    }

    private javafx.scene.control.TreeView<?> tree() {
        return (javafx.scene.control.TreeView<?>) panel.lookup(".fxt-workspace-tree");
    }

    private MenuItem item(String id) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.fileContextMenuItems().stream()
                .filter(i -> id.equals(i.getId())).findFirst().orElseThrow());
    }

    private List<String> tabTexts() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.lookupAll(".tab-pane").stream()
                .filter(TabPane.class::isInstance)
                .flatMap(n -> ((TabPane) n).getTabs().stream())
                .map(Tab::getText)
                .toList());
    }

    private static void fx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
