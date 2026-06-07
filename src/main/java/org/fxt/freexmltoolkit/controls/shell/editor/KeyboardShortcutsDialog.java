package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import org.fxt.freexmltoolkit.util.DialogHelper;

/** Shell "Keyboard Shortcuts" reference dialog (ported from the legacy MainController). */
public final class KeyboardShortcutsDialog {

    private KeyboardShortcutsDialog() {
    }

    public static Dialog<ButtonType> build() {
        var features = List.of(
                new String[]{"bi-folder", "File Operations", "Create, open, save, and manage files"},
                new String[]{"bi-pencil", "Edit Operations", "Undo, redo, find and replace text"},
                new String[]{"bi-window", "View Controls", "Toggle full screen and window options"},
                new String[]{"bi-play-circle", "Actions", "Execute operations and manage favorites"}
        );
        var shortcuts = List.of(
                new String[]{"Ctrl+N", "New File"},
                new String[]{"Ctrl+O", "Open File"},
                new String[]{"Ctrl+S", "Save"},
                new String[]{"Ctrl+Shift+S", "Save As"},
                new String[]{"Ctrl+W", "Close"},
                new String[]{"Ctrl+Z", "Undo"},
                new String[]{"Ctrl+Shift+Z", "Redo"},
                new String[]{"Ctrl+F", "Find"},
                new String[]{"Ctrl+H", "Find & Replace"},
                new String[]{"F11", "Toggle Full Screen"},
                new String[]{"F5", "Execute (Validate/Transform)"},
                new String[]{"Ctrl+D", "Add to Favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle Favorites Panel"},
                new String[]{"F1", "Show Help"}
        );
        return DialogHelper.createHelpDialog(
                "Keyboard Shortcuts",
                "Keyboard Shortcuts",
                "Quick reference for all keyboard shortcuts in FreeXmlToolkit",
                "bi-keyboard",
                DialogHelper.HeaderTheme.INFO,
                features,
                shortcuts);
    }

    public static void show() {
        build().showAndWait();
    }
}
