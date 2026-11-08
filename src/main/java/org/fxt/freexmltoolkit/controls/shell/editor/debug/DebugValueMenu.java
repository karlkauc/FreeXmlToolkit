package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;

/** Context menu of the Variables and Watch tables: Open Value… and Copy Value for the selected row. */
final class DebugValueMenu {

    private DebugValueMenu() {
    }

    static <T> ContextMenu create(Supplier<T> selected, Consumer<T> open, Consumer<T> copy) {
        return create(selected, open, copy, null, t -> false);
    }

    /**
     * Like {@link #create(Supplier, Consumer, Consumer)} plus an "Add to Watches" item for the
     * rows {@code watchable} accepts.
     */
    static <T> ContextMenu create(Supplier<T> selected, Consumer<T> open, Consumer<T> copy,
                                  Consumer<T> addToWatches, java.util.function.Predicate<T> watchable) {
        MenuItem openItem = new MenuItem(I18n.t("DebugValueMenu.open-value"), icon("bi-box-arrow-up-right", ActionColor.NAVIGATE));
        openItem.setOnAction(e -> run(selected, open));
        MenuItem copyItem = new MenuItem(I18n.t("DebugValueMenu.copy-value"), icon("bi-clipboard", ActionColor.TOOL));
        copyItem.setOnAction(e -> run(selected, copy));
        ContextMenu menu = new ContextMenu(openItem, copyItem);
        MenuItem watchItem = null;
        if (addToWatches != null) {
            watchItem = new MenuItem(I18n.t("DebugValueMenu.add-to-watches"), icon("bi-eye", ActionColor.CREATE));
            watchItem.setOnAction(e -> run(selected, addToWatches));
            menu.getItems().add(watchItem);
        }
        final MenuItem watch = watchItem;
        menu.setOnShowing(e -> {
            T item = selected.get();
            boolean none = item == null;
            openItem.setDisable(none);
            copyItem.setDisable(none);
            if (watch != null) {
                watch.setDisable(none || !watchable.test(item));
            }
        });
        return menu;
    }

    private static <T> void run(Supplier<T> selected, Consumer<T> action) {
        T item = selected.get();
        if (item != null) {
            action.accept(item);
        }
    }

    private static IconifyIcon icon(String literal, ActionColor color) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return SemanticIcon.paint(icon, color);
    }
}
