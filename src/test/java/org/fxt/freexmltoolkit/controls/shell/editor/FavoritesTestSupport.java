package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * Test support for the favorites store: the panel tests exercise the real
 * {@link FavoritesService} singleton (backed by the test-run-isolated
 * {@code user.home}, see build.gradle.kts). Starting each test class from an
 * empty store makes them independent of leftovers a crashed earlier class may
 * have failed to remove in its {@code finally} cleanup.
 */
final class FavoritesTestSupport {

    private FavoritesTestSupport() {
    }

    /** Removes every file and folder favorite from the (isolated) store. */
    static void purgeAll() {
        FavoritesService svc = FavoritesService.getInstance();
        for (FileFavorite favorite : List.copyOf(svc.getAllFavorites())) {
            svc.removeFavorite(favorite);
        }
        for (FileFavorite.FileType type : FileFavorite.FileType.values()) {
            for (FileFavorite folder : List.copyOf(svc.getFolderFavorites(type))) {
                svc.removeFolderFavorite(folder.getId());
            }
        }
    }
}
