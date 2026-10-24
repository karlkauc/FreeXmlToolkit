package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;

/** Context menu of the Variables and Watch tables: Open Value… and Copy Value for the selected row. */
final class DebugValueMenu {

    private DebugValueMenu() {
    }

    static <T> ContextMenu create(Supplier<T> selected, Consumer<T> open, Consumer<T> copy) {
        MenuItem openItem = new MenuItem("Open Value…", icon("bi-box-arrow-up-right", ActionColor.NAVIGATE));
        openItem.setOnAction(e -> run(selected, open));
        MenuItem copyItem = new MenuItem("Copy Value", icon("bi-clipboard", ActionColor.TOOL));
        copyItem.setOnAction(e -> run(selected, copy));
        ContextMenu menu = new ContextMenu(openItem, copyItem);
        menu.setOnShowing(e -> {
            boolean none = selected.get() == null;
            openItem.setDisable(none);
            copyItem.setDisable(none);
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
