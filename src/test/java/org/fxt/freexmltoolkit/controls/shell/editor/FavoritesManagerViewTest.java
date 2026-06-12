package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the main-area favorites manager: inline rename, folder
 * lifecycle (create / move into / rename / delete), and search — all persisted
 * through the shared {@link FavoritesService} store.
 */
@ExtendWith(ApplicationExtension.class)
class FavoritesManagerViewTest {

    private FavoritesManagerView view;
    private Path alphaXml;
    private Path betaXml;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        alphaXml = Files.createTempFile("fxt-mgr-alpha", ".xml");
        Files.writeString(alphaXml, "<a/>");
        betaXml = Files.createTempFile("fxt-mgr-beta", ".xml");
        Files.writeString(betaXml, "<b/>");
        FavoritesService.getInstance().addFavorite(alphaXml.toFile());
        FavoritesService.getInstance().addFavorite(betaXml.toFile());
        view = new FavoritesManagerView(new EditorHost());
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupSeededFavorites() throws Exception {
        FavoritesService.getInstance().removeFavoriteByPath(alphaXml.toString());
        FavoritesService.getInstance().removeFavoriteByPath(betaXml.toString());
        Files.deleteIfExists(alphaXml);
        Files.deleteIfExists(betaXml);
    }

    private FileFavorite seeded(Path path) {
        return FavoritesService.getInstance().getAllFavorites().stream()
                .filter(f -> path.toString().equals(f.getFilePath()))
                .findFirst().orElseThrow();
    }

    @Test
    void renamePersistsToTheStore() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.renameFavorite(seeded(alphaXml), "Monthly Report");
            return null;
        });

        assertEquals("Monthly Report", seeded(alphaXml).getName(),
                "the rename must persist in the shared store");
        assertTrue(view.visibleFavorites().stream()
                .anyMatch(f -> "Monthly Report".equals(f.getName())));
    }

    @Test
    void folderLifecycleCreateMoveRenameDelete() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.createFolder("Reports");
            view.moveToFolder(seeded(alphaXml), "Reports");
            return null;
        });
        assertEquals("Reports", seeded(alphaXml).getFolderName());
        assertTrue(view.folderEntries().contains("Reports"));

        // Folder filter: only the moved favorite shows.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.selectFolder("Reports");
            return null;
        });
        assertEquals(1, view.visibleFavorites().stream()
                .filter(f -> f.getFilePath().startsWith(System.getProperty("java.io.tmpdir"))).count());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.renameFolder("Reports", "Q3 Reports");
            return null;
        });
        assertEquals("Q3 Reports", seeded(alphaXml).getFolderName(),
                "renaming a folder must follow its favorites");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.deleteFolder("Q3 Reports");
            return null;
        });
        assertNull(seeded(alphaXml).getFolderName(),
                "deleting a folder moves its favorites to Uncategorized, nothing is lost");
        assertTrue(FavoritesService.getInstance().getAllFavorites().stream()
                .anyMatch(f -> alphaXml.toString().equals(f.getFilePath())),
                "the favorite itself must survive the folder deletion");
    }

    @Test
    void searchFiltersTheTable() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setSearch("fxt-mgr-alpha");
            return null;
        });
        assertTrue(view.visibleFavorites().stream()
                .allMatch(f -> f.getFilePath().contains("fxt-mgr-alpha")));
        assertEquals(1, view.visibleFavorites().size());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setSearch("");
            return null;
        });
        assertTrue(view.visibleFavorites().size() >= 2, "clearing the search shows everything again");
    }
}
