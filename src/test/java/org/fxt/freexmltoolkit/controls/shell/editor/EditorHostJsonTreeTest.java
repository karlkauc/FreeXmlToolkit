package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies a JSON document gains Tree and Graphic view modes in the shell; the Tree
 * reuses the existing JSON tree component bound to the shared JSON context.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonTreeTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void jsonDocumentSupportsTreeAndGraphic(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        assertTrue(host.activeSupportsView(ViewMode.TREE), "JSON must support the Tree view");
        assertTrue(host.activeSupportsView(ViewMode.GRAPHIC), "JSON must offer the Graphic (grid) view");
    }

    @Test
    void switchingJsonToTreeRendersATree(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get(),
                "JSON Tree mode must stick (not fall back to Text)");
        assertNotNull(host.lookup(".tree-view"), "a tree view must be rendered for the JSON document");
    }

    @Test
    void writesSnapshotWhenRequested(@TempDir Path tmp) throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        openJson(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.image.WritableImage img = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.snapshot(new javafx.scene.SnapshotParameters(), null));
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png",
                new java.io.File(System.getProperty("java.io.tmpdir"), "fxt_json_tree.png"));
    }

    private void openJson(Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\"fund\":{\"id\":\"XAM\",\"items\":[1,2,3]}}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));
    }

    @Test
    void deleteInTheTreeRemovesThePropertyCompletely(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        showTree();

        selectAndDelete(".tree-view",
                (org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode n) -> "id".equals(n.getKey()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> !t.contains("XAM")).orElse(false));
        String text = host.getActiveText().orElseThrow();
        assertFalse(text.contains("\"id\""), "key and value are gone: " + text);
        assertTrue(text.contains("items"), text);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.undoJson());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("XAM")).orElse(false));
    }

    /** Selects the first tree item whose value matches and presses Delete on the tree. */
    @SuppressWarnings("unchecked")
    private <T> void selectAndDelete(String selector, java.util.function.Predicate<T> match) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            javafx.scene.control.TreeView<T> tree = (javafx.scene.control.TreeView<T>) host.lookup(selector);
            javafx.scene.control.TreeItem<T> item = find(tree.getRoot(), match);
            assertNotNull(item, "tree item to delete");
            tree.getSelectionModel().select(item);
            tree.requestFocus();
            javafx.event.Event.fireEvent(tree, new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.DELETE,
                    false, false, false, false));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static <T> javafx.scene.control.TreeItem<T> find(javafx.scene.control.TreeItem<T> item,
                                                             java.util.function.Predicate<T> match) {
        if (item == null) {
            return null;
        }
        if (item.getValue() != null && match.test(item.getValue())) {
            return item;
        }
        for (javafx.scene.control.TreeItem<T> child : item.getChildren()) {
            javafx.scene.control.TreeItem<T> hit = find(child, match);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void showTree() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
