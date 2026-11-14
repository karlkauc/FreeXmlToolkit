package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Format and Sort Keys on a JSONC document keep its comments; the text editor folds objects and
 * its context menu reaches the shell's actions.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonFormatTest {

    private static final String JSONC = "{\"b\": 1, // about b\n\"a\": {\"y\": [1,\n2], \"x\": true}}\n";

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    private void open(Path tmp) throws Exception {
        Path json = tmp.resolve("data.jsonc");
        Files.writeString(json, JSONC);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("about b")).orElse(false));
    }

    @Test
    void formatKeepsCommentsAndUsesTheIndent(@TempDir Path tmp) throws Exception {
        open(tmp);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.formatActive()).get(5, TimeUnit.SECONDS));
        String text = host.getActiveText().orElseThrow();
        assertTrue(text.contains("\"b\": 1, // about b"), text);
        assertTrue(text.contains("\n  \"a\": {\n    \"y\": ["), text);
    }

    @Test
    void undoRightAfterOpeningKeepsTheText(@TempDir Path tmp) throws Exception {
        open(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().getCodeArea().undo();
            return null;
        });
        assertEquals(JSONC, host.getActiveText().orElseThrow(), "loading the file is no undo step");
    }

    @Test
    void sortKeysKeepsCommentsAndIsOneUndo(@TempDir Path tmp) throws Exception {
        open(tmp);
        WaitForAsyncUtils.sleep(700, TimeUnit.MILLISECONDS); // RichTextFX merges edits closer than 500 ms
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.sortJsonKeysActive(true)).get(5, TimeUnit.SECONDS));
        String text = host.getActiveText().orElseThrow();
        assertTrue(text.indexOf("\"a\"") < text.indexOf("\"b\""), text);
        assertTrue(text.indexOf("\"x\"") < text.indexOf("\"y\""), text);
        assertTrue(text.contains("\"b\": 1 // about b"), text);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().getCodeArea().undo();
            return null;
        });
        assertEquals(JSONC, host.getActiveText().orElseThrow(), "one undo restores the original text");
    }

    @Test
    void textEditorFoldsAndOffersTheActions(@TempDir Path tmp) throws Exception {
        open(tmp);
        JsonEditorView view = (JsonEditorView) WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> view.getEditor().getFoldingManager().isFoldable(0)));
        var menu = view.getEditorContextMenu();
        assertNotNull(menu);
        assertTrue(menu.getItems().stream().map(MenuItem::getText)
                .anyMatch(t -> t != null && t.startsWith("Sort Keys")), "Sort Keys is in the context menu");
        assertTrue(menu.getItems().stream().map(MenuItem::getText)
                .anyMatch("Fold All"::equals), "Fold All is in the context menu");
        MenuItem sort = menu.getItems().stream().filter(i -> "Sort Keys".equals(i.getText())).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            sort.fire();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveText()
                .map(t -> t.indexOf("\"a\"") < t.indexOf("\"b\"")).orElse(false));
    }
}
