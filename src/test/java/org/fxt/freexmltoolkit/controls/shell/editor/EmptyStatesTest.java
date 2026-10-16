package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.search.SearchResultsTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Empty states name the next step (UX plan 1.3): the Explorer workspace offers
 * "Open folder…" while no folder is open, and the search result area shows a hint
 * instead of a blank box.
 */
@ExtendWith(ApplicationExtension.class)
class EmptyStatesTest {

    private final AtomicInteger openFolder = new AtomicInteger();
    private WorkspaceTree workspace;
    private SearchResultsTree results;

    @Start
    void start(Stage stage) {
        workspace = new WorkspaceTree(path -> { });
        workspace.setOnOpenFolder(openFolder::incrementAndGet);
        results = new SearchResultsTree();
        results.setEmptyHint("Results appear here.");
        stage.setScene(new Scene(new VBox(workspace, results), 300, 400));
        stage.show();
    }

    @Test
    void workspaceWithoutFolderOffersOpenFolder() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(workspace.isEmptyStateShown());
        Button open = (Button) workspace.lookup("#explorer-workspace-open-folder");
        assertNotNull(open, "the empty workspace must offer Open folder…");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            open.fire();
            return null;
        });
        assertEquals(1, openFolder.get());
    }

    @Test
    void openingAFolderReplacesTheEmptyState(@TempDir Path dir) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            workspace.setRootFolder(dir);
            return null;
        });
        assertFalse(workspace.isEmptyStateShown());
        assertEquals(dir, workspace.getRootFolder());
    }

    @Test
    void searchResultsShowTheHintUntilResultsArrive() {
        WaitForAsyncUtils.waitForFxEvents();
        var children = results.getRoot().getChildren();
        assertEquals(1, children.size());
        assertInstanceOf(SearchResultsTree.NoteRow.class, children.get(0).getValue());
        assertEquals("Results appear here.", ((SearchResultsTree.NoteRow) children.get(0).getValue()).text());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            results.setResults(List.of(new SearchResultsTree.FileEntry(Path.of("a.xml"), null, false, List.of())));
            return null;
        });
        assertInstanceOf(SearchResultsTree.FileRow.class, results.getRoot().getChildren().get(0).getValue());
        assertTrue(results.getCheckedMatches().isEmpty(), "the hint never counts as a match");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            results.clearResults();
            return null;
        });
        assertInstanceOf(SearchResultsTree.NoteRow.class, results.getRoot().getChildren().get(0).getValue());
    }
}
