package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer "Open Editors" list stays consistent — and does not throw
 * the JavaFX ListViewBehavior crash — when documents open while a row is selected.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelTest {

    private EditorHost host;
    private ExplorerPanel panel;
    private Path favXml;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        // Seed one favorite BEFORE the panel builds, so its FAVORITES section lists it.
        favXml = Files.createTempFile("fxt-explorer-fav", ".xml");
        Files.writeString(favXml, "<fav/>");
        org.fxt.freexmltoolkit.service.FavoritesService.getInstance().addFavorite(favXml.toFile());
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupSeededFavorite() throws Exception {
        org.fxt.freexmltoolkit.service.FavoritesService.getInstance()
                .removeFavoriteByPath(favXml.toString());
        Files.deleteIfExists(favXml);
    }

    @Test
    void favoritesSectionListsAndOpensFavorites() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        @SuppressWarnings("unchecked")
        ListView<org.fxt.freexmltoolkit.domain.FileFavorite> favorites =
                (ListView<org.fxt.freexmltoolkit.domain.FileFavorite>) panel.lookup("#explorer-favorites-list");
        assertNotNull(favorites, "Explorer must offer a FAVORITES section");

        int index = -1;
        for (int i = 0; i < favorites.getItems().size(); i++) {
            if (favXml.toString().equals(favorites.getItems().get(i).getFilePath())) {
                index = i;
                break;
            }
        }
        assertTrue(index >= 0, "the seeded favorite must be listed");

        int select = index;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            favorites.getSelectionModel().select(select);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null && d.getPath().equals(favXml)));
    }

    @Test
    void removingAFavoriteKeepsTheFileOnDisk() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        @SuppressWarnings("unchecked")
        ListView<org.fxt.freexmltoolkit.domain.FileFavorite> favorites =
                (ListView<org.fxt.freexmltoolkit.domain.FileFavorite>) panel.lookup("#explorer-favorites-list");
        int index = -1;
        for (int i = 0; i < favorites.getItems().size(); i++) {
            if (favXml.toString().equals(favorites.getItems().get(i).getFilePath())) {
                index = i;
                break;
            }
        }
        assertTrue(index >= 0, "the seeded favorite must be listed");

        int select = index;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            favorites.getSelectionModel().select(select);
            panel.removeSelectedFavorite();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(favorites.getItems().stream()
                        .noneMatch(f -> favXml.toString().equals(f.getFilePath())),
                "the favorite entry must be gone from the list");
        assertTrue(Files.exists(favXml), "removing a favorite must NOT delete the file itself");
        assertTrue(panel.lookup(".context-menu") != null
                        || favorites.getContextMenu() != null,
                "the FAVORITES list must offer the remove action via its context menu");
    }

    @Test
    void openingDocumentsWhileAnEntryIsSelectedDoesNotCrash(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.xml");
        Path b = tmp.resolve("b.xml");
        Files.writeString(a, "<a/>");
        Files.writeString(b, "<b/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(a));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 1);

        // Select the first entry in the Open Editors list, then open another doc.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            openEditorsList().getSelectionModel().select(0);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(b));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 2);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, openEditorsList().getItems().size(), "Open Editors list mirrors both documents");
    }

    @Test
    void activeDocumentRowFollowsTheHostsActiveTab(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.xml");
        Path b = tmp.resolve("b.xml");
        Files.writeString(a, "<a/>");
        Files.writeString(b, "<b/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(a));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(b));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 2);
        WaitForAsyncUtils.waitForFxEvents();

        // b.xml opened last → it is the active document and the selected row.
        OpenDocument selected = openEditorsList().getSelectionModel().getSelectedItem();
        assertNotNull(selected, "the active document's row must be selected");
        assertEquals("b.xml", selected.getDisplayName());

        // Switching the active document re-selects its row.
        OpenDocument first = host.getOpenDocuments().get(0);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectDocument(first);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            OpenDocument now = openEditorsList().getSelectionModel().getSelectedItem();
            return now != null && "a.xml".equals(now.getDisplayName());
        });
        assertEquals("a.xml", openEditorsList().getSelectionModel().getSelectedItem().getDisplayName());
    }

    @Test
    void workspaceSectionTitleShowsTheFolderName(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.xml"), "<a/>");

        assertEquals("WORKSPACE", panel.getWorkspaceTitle(), "placeholder title while no folder is open");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setWorkspaceFolder(tmp);
            return null;
        });
        assertEquals(tmp.getFileName().toString().toUpperCase(java.util.Locale.ROOT),
                panel.getWorkspaceTitle(), "the workspace section is titled after the folder");
    }

    @Test
    void headerOffersFlatActionsAndOverflowMenu() {
        WaitForAsyncUtils.waitForFxEvents();
        for (String id : new String[]{"#explorer-new-file", "#explorer-open-folder",
                "#explorer-refresh", "#explorer-overflow"}) {
            assertNotNull(panel.lookup(id), "the Explorer header must offer the " + id + " action");
        }
        var texts = panel.overflowMenuItemTexts();
        assertTrue(texts.contains("Open file…"), texts.toString());
        assertTrue(texts.contains("Clear recent"), texts.toString());
    }

    @Test
    void clickingASectionHeaderCollapsesAndExpandsItsContent() {
        WaitForAsyncUtils.waitForFxEvents();
        ListView<?> open = openEditorsList();
        assertTrue(open.isVisible(), "OPEN EDITORS starts expanded");

        var header = panel.lookup("#explorer-open-editors-header");
        assertNotNull(header, "the OPEN EDITORS header must exist");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            fireHeaderClick(header);
            return null;
        });
        assertFalse(open.isVisible(), "clicking the header collapses the section");
        assertFalse(open.isManaged(), "a collapsed section frees its layout space");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            fireHeaderClick(header);
            return null;
        });
        assertTrue(open.isVisible(), "clicking again expands the section");
        assertTrue(open.isManaged());
    }

    /** Fires a primary-button click on a section header (synthetic — headless-safe). */
    private static void fireHeaderClick(javafx.scene.Node header) {
        header.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    @SuppressWarnings("unchecked")
    private ListView<OpenDocument> openEditorsList() {
        // The Open Editors list is the first .fxt-open-editors ListView in the panel.
        return (ListView<OpenDocument>) panel.lookupAll(".fxt-open-editors").stream()
                .filter(n -> n instanceof ListView).findFirst().orElseThrow();
    }
}
