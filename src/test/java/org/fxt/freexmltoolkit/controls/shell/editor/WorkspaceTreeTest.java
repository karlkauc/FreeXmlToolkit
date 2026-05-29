package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFX verification of {@link WorkspaceTree}: the root shows only allowed file
 * types (plus folders), and activating a file calls the opener.
 */
@ExtendWith(ApplicationExtension.class)
class WorkspaceTreeTest {

    private final AtomicReference<Path> opened = new AtomicReference<>();
    private WorkspaceTree tree;

    @Start
    void start(Stage stage) {
        tree = new WorkspaceTree(opened::set);
        stage.setScene(new Scene(tree, 300, 500));
        stage.show();
    }

    @Test
    void rootShowsAllowedFilesAndFoldersOnly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "<a/>");
        Files.writeString(dir.resolve("note.txt"), "ignored");
        Files.createDirectory(dir.resolve("sub"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setRootFolder(dir));
        WaitForAsyncUtils.waitForFxEvents();

        var names = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                tree.listTopLevelNames());

        assertTrue(names.contains("a.xml"), "allowed file must be shown");
        assertTrue(names.contains("sub"), "folders must be shown");
        assertFalse(names.contains("note.txt"), "disallowed extension must be filtered out");
    }

    @Test
    void activatingAFileCallsTheOpener(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("doc.xml");
        Files.writeString(xml, "<a/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setRootFolder(dir));
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectPath(xml);
            tree.openSelected();
            return null;
        });

        assertEquals(xml, opened.get());
    }
}
