package org.fxt.freexmltoolkit.controls;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.test.FxThreadBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The workspace tree lists folders off the FX thread ({@link FileExplorerTreeItem#withBackgroundListing}):
 * expanding a folder returns at once and the entries (folders first, filtered files, by name) arrive a
 * moment later; an empty folder turns into a leaf once its listing shows it has nothing to show.
 */
@ExtendWith(ApplicationExtension.class)
class FileExplorerBackgroundListingTest {

    private TreeView<Path> tree;

    @Start
    void start(Stage stage) {
        tree = new TreeView<>();
        stage.setScene(new Scene(tree, 300, 400));
        stage.show();
    }

    private static List<String> names(List<TreeItem<Path>> items) {
        return items.stream().map(i -> i.getValue().getFileName().toString()).toList();
    }

    @Test
    void listsInTheBackgroundSortedAndFiltered(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("zeta"));
        Files.createDirectory(dir.resolve("Alpha"));
        Files.writeString(dir.resolve("b.xml"), "<b/>");
        Files.writeString(dir.resolve("A.xsd"), "<xs:schema/>");
        Files.writeString(dir.resolve("notes.txt"), "filtered out");

        FileExplorerTreeItem root = FileExplorerTreeItem.withBackgroundListing(dir, List.of("xml", "xsd"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.setRoot(root);
            root.setExpanded(true);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.getChildren().size() == 4);
        assertEquals(List.of("Alpha", "zeta", "A.xsd", "b.xml"), names(root.getChildren()));

        FileExplorerTreeItem folder = (FileExplorerTreeItem) root.getChildren().get(0);
        assertTrue(folder.isDirectoryItem());
        assertFalse(folder.isLeaf(), "an unlisted folder is assumed to have children");
        WaitForAsyncUtils.waitForAsyncFx(2000, folder::getChildren);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, folder::isLeaf);
        assertTrue(folder.isLeaf(), "an empty folder becomes a leaf once listed");
        assertFalse(((FileExplorerTreeItem) root.getChildren().get(2)).isDirectoryItem());
    }

    @Test
    void expandingALargeFolderStaysOffTheFxThread(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 10_000; i++) {
            Files.writeString(dir.resolve("file" + i + ".xml"), "");
        }
        FileExplorerTreeItem root = FileExplorerTreeItem.withBackgroundListing(dir, List.of("xml"));
        FxThreadBudget.assertFast(50, 1, "expanding a folder with 10,000 entries", root::getChildren);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> root.getChildren().size() == 10_000);
    }

    @Test
    void revealingAPathListsTheFoldersOnTheWayNow(@TempDir Path dir) throws Exception {
        Path nested = Files.createDirectories(dir.resolve("a/b"));
        Path file = Files.writeString(nested.resolve("deep.xml"), "<d/>");
        FileExplorerTreeItem root = FileExplorerTreeItem.withBackgroundListing(dir, List.of("xml"));
        TreeItem<Path> found = WaitForAsyncUtils.waitForAsyncFx(2000, () -> root.expandAndFind(file));
        assertNotNull(found, "reveal must not wait for background listings");
        assertEquals(file, found.getValue());
    }
}
