package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.diff.DiffView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the EditorHost can compare the active document against a chosen file,
 * opening a {@link DiffView} (reusing the existing diff engine/view).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostDiffTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void comparesActiveDocumentWithChosenFile(@TempDir Path tmp) throws Exception {
        Path left = tmp.resolve("left.xml");
        Path right = tmp.resolve("right.xml");
        Files.writeString(left, "<root>\n  <a>1</a>\n</root>\n");
        Files.writeString(right, "<root>\n  <a>2</a>\n</root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(left));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<a>1</a>")).orElse(false));

        DiffView diff = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openDiffWithFile(right.toFile()));

        assertNotNull(diff, "a diff view should be created for the active document");
        assertFalse(diff.getChunksForTesting().isEmpty(),
                "differing documents must produce at least one diff chunk");
    }

    @Test
    void returnsNullWithoutAnActiveDocument() {
        // A fresh host has no open document, so there is nothing to compare.
        DiffView diff = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> new EditorHost().openDiffWithFile(new File("/tmp/whatever.xml")));
        assertNull(diff);
    }
}
