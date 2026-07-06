package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * Builds the star quick-select menus that let a side panel pick a file straight
 * from the FAVORITES store — the same affordance the Validation panel's XSD row
 * and the Transform panel pioneered, extracted so every file-pick row (Schematron,
 * XSL-FO, keystores, …) can offer it consistently.
 */
public final class FavoritesMenu {

    private FavoritesMenu() {
    }

    /**
     * Creates a star {@link MenuButton} that repopulates itself from the favorites
     * store every time it is opened.
     *
     * @param type    the favorite type to list
     * @param tooltip the button tooltip (e.g. "Schematron favorites")
     * @param onPick  receives the picked favorite's file
     * @return the ready-to-place menu button (styled like the existing star menus)
     */
    public static MenuButton create(FileFavorite.FileType type, String tooltip, Consumer<File> onPick) {
        MenuButton menu = new MenuButton();
        IconifyIcon star = new IconifyIcon("bi-star");
        star.setIconSize(13);
        menu.setGraphic(star);
        menu.getStyleClass().add("fxt-vp-source-fav");
        menu.setTooltip(new Tooltip(tooltip));
        menu.setOnShowing(e -> populate(menu, type, onPick));
        populate(menu, type, onPick);
        return menu;
    }

    /**
     * Populates the menu with the existing favorites of {@code type}: a flat item
     * list while everything is uncategorized, one submenu per folder otherwise.
     * The menu is disabled while there is nothing to pick.
     */
    public static void populate(MenuButton menu, FileFavorite.FileType type, Consumer<File> onPick) {
        menu.getItems().clear();
        List<FileFavorite> favorites;
        try {
            favorites = FavoritesService.getInstance().getFavoritesByType(type).stream()
                    .filter(FileFavorite::fileExists)
                    .toList();
        } catch (Throwable t) {
            favorites = List.of();
        }
        if (favorites.isEmpty()) {
            menu.setDisable(true);
            return;
        }
        menu.setDisable(false);

        Map<String, List<FileFavorite>> byFolder = new LinkedHashMap<>();
        for (FileFavorite favorite : favorites) {
            String folder = favorite.getFolderName() == null || favorite.getFolderName().isBlank()
                    ? "" : favorite.getFolderName();
            byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(favorite);
        }
        if (byFolder.size() == 1) {
            // Single (or no) folder: flat list, no submenu indirection.
            for (FileFavorite favorite : favorites) {
                menu.getItems().add(item(favorite, onPick));
            }
            return;
        }
        for (var entry : byFolder.entrySet()) {
            Menu submenu = new Menu(entry.getKey().isEmpty() ? "Uncategorized" : entry.getKey());
            for (FileFavorite favorite : entry.getValue()) {
                submenu.getItems().add(item(favorite, onPick));
            }
            menu.getItems().add(submenu);
        }
    }

    /** @return the pickable leaf labels (flat items + submenu items) — for tests/observers. */
    public static List<String> leafNames(MenuButton menu) {
        List<String> names = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu sub) {
                for (MenuItem leaf : sub.getItems()) {
                    names.add(leaf.getText());
                }
            } else {
                names.add(item.getText());
            }
        }
        return names;
    }

    private static MenuItem item(FileFavorite favorite, Consumer<File> onPick) {
        String name = favorite.getName() != null && !favorite.getName().isBlank()
                ? favorite.getName() : new File(favorite.getFilePath()).getName();
        MenuItem item = new MenuItem(name);
        item.setOnAction(e -> onPick.accept(new File(favorite.getFilePath())));
        return item;
    }
}
