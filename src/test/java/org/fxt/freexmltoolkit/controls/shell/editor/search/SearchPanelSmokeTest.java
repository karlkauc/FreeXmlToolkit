package org.fxt.freexmltoolkit.controls.shell.editor.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Smoke test for the Search side panel: it builds with the required title
 * style, the Text | XPath mode toggle swaps the panes, and a text search over a
 * folder populates the results tree and navigates into the editor.
 */
@ExtendWith(ApplicationExtension.class)
class SearchPanelSmokeTest {

    private static final AtomicReference<Path> workspace = new AtomicReference<>();

    private EditorHost host;
    private SearchPanel panel;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new SearchPanel(host, workspace::get);
        stage.setScene(new Scene(panel, 420, 640));
        stage.show();
    }

    @Test
    void panelBuildsWithTitleAndModeToggleSwapsPanes() throws Exception {
        Label title = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                (Label) panel.lookupAll(".fxt-side-panel-title").stream()
                        .findFirst().orElse(null));
        assertNotNull(title, "panel must carry the fxt-side-panel-title label");
        assertEquals("SEARCH", title.getText());

        TextSearchPane textPane = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                (TextSearchPane) panel.lookup(".fxt-search-text-pane"));
        XPathSearchPane xpathPane = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                (XPathSearchPane) panel.lookup(".fxt-search-xpath-pane"));
        assertNotNull(textPane);
        assertNotNull(xpathPane);
        assertTrue(textPane.isVisible(), "Text mode is the default");
        assertFalse(xpathPane.isVisible());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((ToggleButton) panel.lookup("#search-mode-xpath")).fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(xpathPane.isVisible());
        assertFalse(textPane.isVisible());
    }

    @Test
    void typingAQueryPopulatesResultsAndNavigates(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        Files.writeString(dir.resolve("a.xml"), "<root>\n  <name>findme</name>\n</root>");
        Files.writeString(dir.resolve("b.xml"), "<root/>");
        workspace.set(dir);

        SearchResultsTree tree = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                (SearchResultsTree) panel.lookup(".fxt-search-results"));
        assertNotNull(tree);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((ToggleButton) panel.lookup("#search-mode-text")).fire();
            ((javafx.scene.control.TextField) panel.lookup("#search-query")).setText("findme");
            return null;
        });

        // debounce (400 ms) + async search → poll the combined condition
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () ->
                tree.getRoot() != null && !tree.getRoot().getChildren().isEmpty());
        assertEquals(1, tree.getRoot().getChildren().size(), "only a.xml matches");
        assertEquals(1, tree.getCheckedMatches().size());
        SearchResultsTree.MatchRow match = tree.getCheckedMatches().get(0);
        assertEquals(2, match.lineNumber());
        assertEquals("findme", match.matched());

        // selecting the match row navigates: the file opens in the editor host
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.getSelectionModel().select(
                    tree.getRoot().getChildren().get(0).getChildren().get(0));
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null
                        && "a.xml".equals(d.getPath().getFileName().toString())));
        assertTrue(host.getOpenDocuments().stream().anyMatch(d -> d.getPath() != null
                && "a.xml".equals(d.getPath().getFileName().toString())));
    }
}
