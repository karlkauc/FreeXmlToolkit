package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.DialogHelper;

/** Shell I18n.t("KeyboardShortcutsDialog.keyboard-shortcuts") reference dialog (ported from the legacy MainController). */
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
                new String[]{"bi-folder", I18n.t("KeyboardShortcutsDialog.file-operations"), I18n.t("KeyboardShortcutsDialog.create-open-save-and-manage")},
                new String[]{"bi-pencil", I18n.t("KeyboardShortcutsDialog.edit-operations"), I18n.t("KeyboardShortcutsDialog.undo-redo-find-and-replace")},
                new String[]{"bi-window", I18n.t("KeyboardShortcutsDialog.view-controls"), I18n.t("KeyboardShortcutsDialog.toggle-full-screen-and-window")},
                new String[]{"bi-play-circle", I18n.t("KeyboardShortcutsDialog.actions"), I18n.t("KeyboardShortcutsDialog.execute-operations-and-manage-favorites")}
        );
        var shortcuts = List.of(
                new String[]{mod("N"), I18n.t("KeyboardShortcutsDialog.new-file")},
                new String[]{mod("O"), I18n.t("KeyboardShortcutsDialog.open-file")},
                new String[]{mod("S"), I18n.t("KeyboardShortcutsDialog.save")},
                new String[]{mod("Shift+S"), I18n.t("KeyboardShortcutsDialog.save-as")},
                new String[]{MAC ? "Cmd+W" : "Ctrl+W / Ctrl+F4", I18n.t("KeyboardShortcutsDialog.close-tab")},
                new String[]{"Ctrl+Tab / Ctrl+Shift+Tab", I18n.t("KeyboardShortcutsDialog.next-previous-tab")},
                new String[]{mod(","), I18n.t("KeyboardShortcutsDialog.settings")},
                new String[]{MAC ? "Cmd+Q" : I18n.t("KeyboardShortcutsDialog.ctrl-q-or-alt-f4"), I18n.t("KeyboardShortcutsDialog.quit-freexmltoolkit")},
                new String[]{mod("Z"), I18n.t("KeyboardShortcutsDialog.undo")},
                new String[]{mod("Shift+Z"), I18n.t("KeyboardShortcutsDialog.redo")},
                new String[]{mod("F"), I18n.t("KeyboardShortcutsDialog.find")},
                new String[]{MAC ? "Cmd+Alt+F" : "Ctrl+H", I18n.t("KeyboardShortcutsDialog.find-replace")},
                new String[]{mod("Shift+F"), I18n.t("KeyboardShortcutsDialog.find-in-files")},
                new String[]{mod("Shift+H"), I18n.t("KeyboardShortcutsDialog.replace-in-files")},
                new String[]{"Shift+Alt+F", I18n.t("KeyboardShortcutsDialog.format-document")},
                new String[]{MAC ? "Ctrl+Cmd+F / F11" : "F11", I18n.t("KeyboardShortcutsDialog.toggle-full-screen")},
                new String[]{"F5", I18n.t("KeyboardShortcutsDialog.run-the-active-xquery-xpath")},
                new String[]{"Shift+F5", I18n.t("KeyboardShortcutsDialog.debug-with-xslt-the-active")},
                new String[]{"F8", I18n.t("KeyboardShortcutsDialog.validate-the-active-document")},
                new String[]{"F9", I18n.t("KeyboardShortcutsDialog.toggle-the-breakpoint-on-the")},
                new String[]{"F5 / F7 / F8 / Shift+F8", I18n.t("KeyboardShortcutsDialog.while-paused-continue-step")},
                new String[]{"Shift+F7", I18n.t("KeyboardShortcutsDialog.while-paused-smart-step-into")},
                new String[]{"F6", I18n.t("KeyboardShortcutsDialog.while-the-debugger-runs-pause")},
                new String[]{"Alt+F8", I18n.t("KeyboardShortcutsDialog.while-paused-evaluate-an-xpath")},
                new String[]{mod("F10"), I18n.t("KeyboardShortcutsDialog.run-to-cursor-starts-the")},
                new String[]{mod("Shift+F5"), I18n.t("KeyboardShortcutsDialog.re-run-the-last-xslt")},
                new String[]{mod("F2"), I18n.t("KeyboardShortcutsDialog.toggle-the-bookmark-on-the")},
                new String[]{mod("Shift+F2"), I18n.t("KeyboardShortcutsDialog.name-the-bookmark-on-the")},
                new String[]{"F2 / Shift+F2", I18n.t("KeyboardShortcutsDialog.text-editor-next-previous-bookmark")},
                new String[]{mod("1") + " … " + mod("9"), I18n.t("KeyboardShortcutsDialog.jump-to-quick-bookmark")},
                new String[]{mod("Shift+1") + " … " + mod("Shift+9"), I18n.t("KeyboardShortcutsDialog.set-remove-quick-bookmark")},
                new String[]{mod("D"), I18n.t("KeyboardShortcutsDialog.add-to-favorites")},
                new String[]{mod("Shift+D"), I18n.t("KeyboardShortcutsDialog.toggle-favorites-panel")},
                new String[]{mod("L"), I18n.t("KeyboardShortcutsDialog.toggle-side-panel")},
                new String[]{mod("Shift+P"), I18n.t("KeyboardShortcutsDialog.toggle-properties-inspector")},
                new String[]{mod("Shift+X"), I18n.t("KeyboardShortcutsDialog.toggle-query-console")},
                new String[]{mod("K"), I18n.t("KeyboardShortcutsDialog.open-query-console-and-focus")},
                new String[]{mod("E"), I18n.t("KeyboardShortcutsDialog.spreadsheet-converter")},
                new String[]{mod("T"), I18n.t("KeyboardShortcutsDialog.insert-template")},
                new String[]{mod("+") + " / " + mod("-"), I18n.t("KeyboardShortcutsDialog.zoom-in-out-text-view")},
                new String[]{mod("0"), I18n.t("KeyboardShortcutsDialog.grid-view-reset-zoom-to")},
                new String[]{mod("Alt+X"), I18n.t("KeyboardShortcutsDialog.grid-view-copy-xpath-jsonpath")},
                new String[]{mod("Alt+C"), I18n.t("KeyboardShortcutsDialog.grid-view-copy-the-selected")},
                new String[]{mod("Shift+C"), I18n.t("KeyboardShortcutsDialog.grid-view-copy-the-cell")},
                new String[]{I18n.t("KeyboardShortcutsDialog.ctrl-mouse-wheel"), I18n.t("KeyboardShortcutsDialog.zoom-text-view-font-size")},
                new String[]{"F1", I18n.t("KeyboardShortcutsDialog.show-the-help-menu")}
        );
        return DialogHelper.createHelpDialog(
                I18n.t("KeyboardShortcutsDialog.keyboard-shortcuts"),
                I18n.t("KeyboardShortcutsDialog.keyboard-shortcuts"),
                I18n.t("KeyboardShortcutsDialog.quick-reference-for-all-keyboard"),
                "bi-keyboard",
                DialogHelper.HeaderTheme.INFO,
                features,
                shortcuts);
    }

    public static void show() {
        build().showAndWait();
    }
}
