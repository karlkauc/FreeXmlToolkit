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
                new String[]{MAC ? "Cmd+W" : "Ctrl+W / Ctrl+F4", "Close Tab"},
                new String[]{"Ctrl+Tab / Ctrl+Shift+Tab", "Next / previous tab"},
                new String[]{mod(","), "Settings"},
                new String[]{MAC ? "Cmd+Q" : "Ctrl+Q (or Alt+F4)", "Quit FreeXmlToolkit"},
                new String[]{mod("Z"), "Undo"},
                new String[]{mod("Shift+Z"), "Redo"},
                new String[]{mod("F"), "Find"},
                new String[]{MAC ? "Cmd+Alt+F" : "Ctrl+H", "Find & Replace"},
                new String[]{mod("Shift+F"), "Find in Files"},
                new String[]{mod("Shift+H"), "Replace in Files"},
                new String[]{"Shift+Alt+F", "Format Document"},
                new String[]{MAC ? "Ctrl+Cmd+F / F11" : "F11", "Toggle Full Screen"},
                new String[]{"F5", "Run the active XQuery / XPath / XSLT / XProc document"},
                new String[]{"Shift+F5", "Debug with XSLT (the active XSLT, or the Transform / PDF panel's stylesheet)"},
                new String[]{"F8", "Validate the active document"},
                new String[]{"F9", "Toggle the breakpoint on the caret line (saved XSLT document)"},
                new String[]{"F5 / F7 / F8 / Shift+F8", "While paused in the debugger: Continue / Step Into / Step Over / Step Out"},
                new String[]{mod("D"), "Add to Favorites"},
                new String[]{mod("Shift+D"), "Toggle Favorites Panel"},
                new String[]{mod("L"), "Toggle Side Panel"},
                new String[]{mod("Shift+P"), "Toggle Properties Inspector"},
                new String[]{mod("Shift+X"), "Toggle Query Console"},
                new String[]{mod("K"), "Open Query Console and focus the query (header search pill)"},
                new String[]{mod("E"), "Spreadsheet Converter"},
                new String[]{mod("T"), "Insert Template"},
                new String[]{mod("+") + " / " + mod("-"), "Zoom in / out (Text view: font size, Grid view: whole grid)"},
                new String[]{mod("0"), "Grid view: reset zoom to 100%"},
                new String[]{mod("Alt+X"), "Grid view: copy XPath / JSONPath of the selected node"},
                new String[]{mod("Alt+C"), "Grid view: copy the selected node as XML / JSON"},
                new String[]{mod("Shift+C"), "Grid view: copy the cell content"},
                new String[]{"Ctrl+Mouse wheel", "Zoom (Text view: font size, Grid view: whole grid)"},
                new String[]{"F1", "Show the Help menu"}
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
