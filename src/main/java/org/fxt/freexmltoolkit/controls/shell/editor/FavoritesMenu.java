package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * Builds the star quick-select menus that let a side panel pick a file straight
 * from the FAVORITES store — the same affordance the Validation panel's XSD row
 * and the Transform panel pioneered, extracted so every file-pick row (Schematron,
 * XSL-FO, keystores, …) can offer it consistently.
 *
 * <p>It also builds the type-independent <em>quick access</em> menu (every favorite, plus "Add
 * Active Document" and "Manage Favorites…") behind the header bar's star, the Explorer's star
 * and File → Open Favorite, so favorites are reachable from every activity.
 */
public final class FavoritesMenu {

    /** Style class of a star {@link MenuButton} without the drop-down arrow. */
    public static final String QUICK_ACCESS_STYLE = "fxt-fav-quick-access";

    /**
     * The shell actions behind the quick access menu.
     *
     * @param open         opens a picked favorite's file
     * @param addActive    adds the active document to favorites
     * @param canAddActive whether a saved document is active (enables "Add Active Document")
     * @param manage       shows the Favorites activity
     */
    public record QuickAccess(Consumer<File> open, Runnable addActive, BooleanSupplier canAddActive,
                              Runnable manage) {
    }

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
        List<MenuItem> items = buildItems(type, onPick);
        menu.getItems().setAll(items);
        menu.setDisable(items.isEmpty());
    }

    /**
     * Builds a {@link Menu} listing the favorites of {@code type} — for embedding
     * inside another menu (e.g. the Explorer's Schematron picker). Same structure
     * as {@link #populate}: a flat item list while everything is uncategorized,
     * one nested submenu per folder otherwise. Disabled while there is nothing
     * to pick.
     *
     * @param text   the submenu title (e.g. "Favorites")
     * @param type   the favorite type to list
     * @param onPick receives the picked favorite's file
     * @return the ready-to-embed submenu
     */
    public static Menu submenu(String text, FileFavorite.FileType type, Consumer<File> onPick) {
        Menu menu = new Menu(text);
        List<MenuItem> items = buildItems(type, onPick);
        menu.getItems().setAll(items);
        menu.setDisable(items.isEmpty());
        return menu;
    }

    /**
     * Builds the pickable favorite items of {@code type}: a flat list while
     * everything is uncategorized, one {@link Menu} per folder otherwise.
     * Empty when there are no (existing) favorites of the type.
     */
    private static List<MenuItem> buildItems(FileFavorite.FileType type, Consumer<File> onPick) {
        List<FileFavorite> favorites;
        try {
            favorites = FavoritesService.getInstance().getFavoritesByType(type);
        } catch (Throwable t) {
            favorites = List.of();
        }
        return groupedItems(favorites, onPick, false);
    }

    /**
     * Builds the pickable items of <em>all</em> (existing) file favorites, whatever their type:
     * a flat list while everything is uncategorized, one {@link Menu} per folder otherwise.
     * Each item carries its type's icon. Empty when there are no favorites.
     *
     * @param onPick receives the picked favorite's file
     * @return the items, ready for a menu
     */
    public static List<MenuItem> allItems(Consumer<File> onPick) {
        List<FileFavorite> favorites;
        try {
            favorites = FavoritesService.getInstance().getAllFavorites();
        } catch (Throwable t) {
            favorites = List.of();
        }
        return groupedItems(favorites, onPick, true);
    }

    /**
     * Builds the quick access menu: every favorite ({@link #allItems}, or a disabled "No
     * Favorites Yet" entry), then "Add Active Document" and "Manage Favorites…". Opening a
     * favorite also records the access (feeds the "Recently Used" statistics).
     *
     * @param actions the shell actions behind the items
     * @return the items, ready for a menu
     */
    public static List<MenuItem> quickAccessItems(QuickAccess actions) {
        List<MenuItem> items = new ArrayList<>(allItems(recordingAccess(actions.open())));
        if (items.isEmpty()) {
            MenuItem none = new MenuItem("No Favorites Yet", icon("bi-star", ActionColor.NEUTRAL));
            none.setId("favorites-quick-none");
            none.setDisable(true);
            items.add(none);
        }
        items.add(new SeparatorMenuItem());
        MenuItem add = new MenuItem("Add Active Document  (Ctrl+D)", icon("bi-star-fill", ActionColor.CREATE));
        add.setId("favorites-quick-add");
        add.setMnemonicParsing(false);
        add.setDisable(!actions.canAddActive().getAsBoolean());
        add.setOnAction(e -> actions.addActive().run());
        MenuItem manage = new MenuItem("Manage Favorites…", icon("bi-star", ActionColor.NAVIGATE));
        manage.setId("favorites-quick-manage");
        manage.setOnAction(e -> actions.manage().run());
        items.add(add);
        items.add(manage);
        return items;
    }

    /**
     * Creates a star {@link MenuButton} (no drop-down arrow) that rebuilds the quick access
     * menu every time it opens — the store is not observable.
     *
     * @param id         the node id (tests look it up)
     * @param styleClass the button chrome (e.g. {@code fxt-header-icon}, {@code fxt-sp-action})
     * @param iconSize   the star size
     * @param actions    the shell actions behind the items
     * @return the ready-to-place button
     */
    public static MenuButton quickAccessButton(String id, String styleClass, int iconSize, QuickAccess actions) {
        MenuButton button = new MenuButton();
        button.setId(id);
        IconifyIcon star = new IconifyIcon("bi-star");
        star.setIconSize(iconSize);
        button.setGraphic(star);
        button.getStyleClass().addAll(styleClass, QUICK_ACCESS_STYLE);
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip("Favorites"));
        installQuickAccess(button, actions);
        return button;
    }

    /**
     * Makes {@code button} (e.g. one declared in FXML) the quick access menu: its items are
     * rebuilt every time it opens.
     *
     * @param button  the menu button
     * @param actions the shell actions behind the items
     */
    public static void installQuickAccess(MenuButton button, QuickAccess actions) {
        button.setOnShowing(e -> button.getItems().setAll(quickAccessItems(actions)));
        // An empty popup never shows: start with the current items.
        button.getItems().setAll(quickAccessItems(actions));
    }

    /**
     * @param open opens a picked favorite's file
     * @return {@code open}, recording the access first (feeds "Recently Used" / "Most Popular")
     */
    public static Consumer<File> recordingAccess(Consumer<File> open) {
        return file -> {
            try {
                FavoritesService.getInstance().recordAccess(file.getPath());
            } catch (Throwable ignored) {
                // statistics only
            }
            open.accept(file);
        };
    }

    /** Groups existing favorites by folder: flat while there is at most one folder. */
    private static List<MenuItem> groupedItems(List<FileFavorite> all, Consumer<File> onPick, boolean typeIcons) {
        List<FileFavorite> favorites = all.stream().filter(FileFavorite::fileExists).toList();
        if (favorites.isEmpty()) {
            return List.of();
        }
        Map<String, List<FileFavorite>> byFolder = new LinkedHashMap<>();
        for (FileFavorite favorite : favorites) {
            String folder = favorite.getFolderName() == null || favorite.getFolderName().isBlank()
                    ? "" : favorite.getFolderName();
            byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(favorite);
        }
        List<MenuItem> items = new ArrayList<>();
        if (byFolder.size() == 1) {
            // Single (or no) folder: flat list, no submenu indirection.
            for (FileFavorite favorite : favorites) {
                items.add(item(favorite, onPick, typeIcons));
            }
            return items;
        }
        for (var entry : byFolder.entrySet()) {
            Menu submenu = new Menu(entry.getKey().isEmpty() ? "Uncategorized" : entry.getKey());
            submenu.setMnemonicParsing(false);
            if (typeIcons) {
                submenu.setGraphic(icon("bi-folder", ActionColor.NAVIGATE));
            }
            for (FileFavorite favorite : entry.getValue()) {
                submenu.getItems().add(item(favorite, onPick, typeIcons));
            }
            items.add(submenu);
        }
        return items;
    }

    /** @return the pickable leaf labels (flat items + submenu items) — for tests/observers. */
    public static List<String> leafNames(MenuButton menu) {
        return leafNames(menu.getItems());
    }

    /** @return the pickable leaf labels of a favorites submenu — for tests/observers. */
    public static List<String> leafNames(Menu menu) {
        return leafNames(menu.getItems());
    }

    private static List<String> leafNames(List<MenuItem> items) {
        List<String> names = new ArrayList<>();
        for (MenuItem item : items) {
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

    private static MenuItem item(FileFavorite favorite, Consumer<File> onPick, boolean typeIcon) {
        String name = favorite.getName() != null && !favorite.getName().isBlank()
                ? favorite.getName() : new File(favorite.getFilePath()).getName();
        MenuItem item = new MenuItem(name);
        item.setMnemonicParsing(false); // names may contain underscores
        if (typeIcon) {
            item.setGraphic(typeIcon(favorite.getFileType()));
        }
        item.setOnAction(e -> onPick.accept(new File(favorite.getFilePath())));
        return item;
    }

    /**
     * @return the type's icon, its color bound so a CSS {@code -fx-icon-color} rule cannot
     * override it
     */
    static IconifyIcon typeIcon(FileFavorite.FileType type) {
        FileFavorite.FileType t = type != null ? type : FileFavorite.FileType.OTHER;
        IconifyIcon icon = new IconifyIcon(t.getIconLiteral());
        icon.setIconSize(14);
        try {
            icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                    javafx.scene.paint.Color.web(t.getDefaultColor())));
        } catch (IllegalArgumentException ignored) {
            // unparsable color: keep the default icon color
        }
        return icon;
    }

    private static IconifyIcon icon(String literal, ActionColor color) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return SemanticIcon.paint(icon, color);
    }
}
