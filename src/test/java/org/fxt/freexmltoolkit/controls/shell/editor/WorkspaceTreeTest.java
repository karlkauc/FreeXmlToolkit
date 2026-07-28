package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

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
        Files.writeString(dir.resolve("query.xq"), "/root");
        Files.writeString(dir.resolve("expr.xpath"), "/root");
        Files.writeString(dir.resolve("note.txt"), "ignored");
        Files.createDirectory(dir.resolve("sub"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setRootFolder(dir));
        WaitForAsyncUtils.waitForFxEvents();

        var names = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                tree.listTopLevelNames());

        assertTrue(names.contains("a.xml"), "allowed file must be shown");
        assertTrue(names.contains("query.xq"), "XQuery files must be shown");
        assertTrue(names.contains("expr.xpath"), "XPath files must be shown");
        assertTrue(names.contains("sub"), "folders must be shown");
        assertFalse(names.contains("note.txt"), "disallowed extension must be filtered out");
    }

    @Test
    void subfoldersListTheirQueryFiles(@TempDir Path dir) throws Exception {
        // Mirrors the bundled examples layout: examples/xpath/*.xpath, examples/xquery/*.xq.
        Path xpathDir = Files.createDirectory(dir.resolve("xpath"));
        Path xqueryDir = Files.createDirectory(dir.resolve("xquery"));
        Files.writeString(xpathDir.resolve("01-fund-names.xpath"), "/root");
        Files.writeString(xqueryDir.resolve("01-recon.xq"), "/root");
        Files.writeString(xqueryDir.resolve("README.md"), "ignored");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setRootFolder(dir));
        WaitForAsyncUtils.waitForFxEvents();

        var xpathNames = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectPath(xpathDir.resolve("01-fund-names.xpath"));
            return tree.getSelectedFiles();
        });
        assertTrue(xpathNames.contains(xpathDir.resolve("01-fund-names.xpath")),
                ".xpath file inside a subfolder must be present and selectable");

        var xqNames = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectPath(xqueryDir.resolve("01-recon.xq"));
            return tree.getSelectedFiles();
        });
        assertTrue(xqNames.contains(xqueryDir.resolve("01-recon.xq")),
                ".xq file inside a subfolder must be present and selectable");
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

    @Test
    void getSelectedFilesReturnsAllSelectedRegularFiles(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("a.xml");
        Path b = dir.resolve("b.xml");
        Files.writeString(a, "<a/>");
        Files.writeString(b, "<b/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setRootFolder(dir));
        WaitForAsyncUtils.waitForFxEvents();

        var selected = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectPath(a);
            tree.selectPath(b);
            return tree.getSelectedFiles();
        });

        assertTrue(selected.contains(a), "first selected file must be returned");
        assertTrue(selected.contains(b), "second selected file must be returned");
        assertEquals(2, selected.size(), "only the two selected files must be returned");
    }
}
