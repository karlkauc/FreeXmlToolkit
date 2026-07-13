package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Menu;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link FavoritesMenu#submenu}: it lists the favorites of the requested
 * type as pickable items (for embedding in the Explorer's Schematron picker) and
 * is disabled while there is nothing to pick.
 */
class FavoritesMenuSubmenuTest {

    private final List<String> seededPaths = new ArrayList<>();

    @AfterEach
    void removeSeededFavorites() {
        for (String path : seededPaths) {
            try {
                FavoritesService.getInstance().removeFavoriteByPath(path);
            } catch (Throwable ignored) {
                // best-effort cleanup
            }
        }
    }

    @Test
    void listsSchematronFavoritesAndPicksTheFile(@TempDir Path tmp) throws Exception {
        Path sch = Files.writeString(tmp.resolve("rules.sch"), "<x/>");
        FavoritesService.getInstance().addFavorite(sch.toFile());
        seededPaths.add(sch.toString());

        List<File> picked = new ArrayList<>();
        Menu menu = FavoritesMenu.submenu("Favorites",
                FileFavorite.FileType.SCHEMATRON, picked::add);

        assertEquals("Favorites", menu.getText());
        assertFalse(menu.isDisable(), "with a favorite present the submenu is pickable");
        // addFavorite(File) strips the extension for the display name.
        assertTrue(FavoritesMenu.leafNames(menu).contains("rules"),
                "leaf names were: " + FavoritesMenu.leafNames(menu));

        // Firing the leaf hands the favorite's file to the picker.
        menu.getItems().stream()
                .filter(i -> "rules".equals(i.getText()))
                .findFirst().orElseThrow()
                .fire();
        assertEquals(List.of(sch.toFile().getAbsoluteFile()),
                picked.stream().map(File::getAbsoluteFile).toList());
    }

    @Test
    void submenuIsDisabledWhileThereAreNoFavoritesOfTheType() {
        Menu menu = FavoritesMenu.submenu("Favorites",
                FileFavorite.FileType.XPATH, f -> fail("nothing to pick"));
        assertTrue(menu.isDisable());
        assertTrue(menu.getItems().isEmpty());
    }
}
