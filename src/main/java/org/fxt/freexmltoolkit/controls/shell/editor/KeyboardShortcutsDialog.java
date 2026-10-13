package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import org.fxt.freexmltoolkit.util.DialogHelper;

/** Shell "Keyboard Shortcuts" reference dialog (ported from the legacy MainController). */
public final class KeyboardShortcutsDialog {

    /** macOS uses Cmd where the other platforms use Ctrl. */
    private static final boolean MAC =
            org.fxt.freexmltoolkit.service.ApplicationLauncherLocator.isMacOS();

    private KeyboardShortcutsDialog() {
    }

    /** @return the platform's spelling of a Ctrl/Cmd shortcut, e.g. {@code Ctrl+S} or {@code Cmd+S} */
    private static String mod(String keys) {
        return (MAC ? "Cmd+" : "Ctrl+") + keys;
    }

    public static Dialog<ButtonType> build() {
        var features = List.of(
                new String[]{"bi-folder", "File Operations", "Create, open, save, and manage files"},
                new String[]{"bi-pencil", "Edit Operations", "Undo, redo, find and replace text"},
                new String[]{"bi-window", "View Controls", "Toggle full screen and window options"},
                new String[]{"bi-play-circle", "Actions", "Execute operations and manage favorites"}
        );
        var shortcuts = List.of(
                new String[]{mod("N"), "New File"},
                new String[]{mod("O"), "Open File"},
                new String[]{mod("S"), "Save"},
                new String[]{mod("Shift+S"), "Save As"},
                new String[]{mod("W"), "Close Tab"},
                new String[]{mod(","), "Settings"},
                new String[]{mod("Z"), "Undo"},
                new String[]{mod("Shift+Z"), "Redo"},
                new String[]{mod("F"), "Find"},
                new String[]{MAC ? "Cmd+Alt+F" : "Ctrl+H", "Find & Replace"},
                new String[]{mod("Shift+F"), "Find in Files"},
                new String[]{mod("Shift+H"), "Replace in Files"},
                new String[]{"Shift+Alt+F", "Format Document"},
                new String[]{MAC ? "Ctrl+Cmd+F / F11" : "F11", "Toggle Full Screen"},
                new String[]{"F5", "Execute: run XQuery/XSLT/XProc, otherwise validate"},
                new String[]{mod("D"), "Add to Favorites"},
                new String[]{mod("Shift+D"), "Toggle Favorites Panel"},
                new String[]{mod("L"), "Toggle Side Panel"},
                new String[]{mod("Shift+P"), "Toggle Properties Inspector"},
                new String[]{mod("Shift+X"), "Toggle Query Console"},
                new String[]{mod("K"), "Open Query Console (search)"},
                new String[]{mod("E"), "Spreadsheet Converter"},
                new String[]{mod("T"), "Insert Template"},
                new String[]{"Ctrl++ / Ctrl+-", "Grid view: zoom in / out"},
                new String[]{mod("0"), "Grid view: reset zoom to 100%"},
                new String[]{"Ctrl+Mouse wheel", "Zoom (Text view: font size, Grid view: whole grid)"},
                new String[]{"F1", "Open the Help panel"}
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
