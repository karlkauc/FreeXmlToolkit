package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;

/**
 * The right-click menu of an editor tab: Reload, Close, Close Others, Save, Save As, Copy Path and
 * the two Reveal actions. Every action targets the right-clicked tab, not the active one. Tabs that
 * show no document (tool tabs, diff, PDF preview) only get Close / Close Others. The entries are
 * rebuilt each time the menu opens so their enabled state follows the document.
 */
final class EditorTabMenu {

    /** What the menu needs from the host; implemented by {@link EditorHost}. */
    interface Actions {
        /** @return the document the tab shows, or {@code null} for a tab without one */
        OpenDocument documentOf(Tab tab);

        /** @return whether another closable tab than {@code tab} is open */
        boolean hasOtherClosableTabs(Tab tab);

        void reload(Tab tab);

        void close(Tab tab);

        void closeOthers(Tab tab);

        void save(Tab tab);

        void saveAs(Tab tab);

        void copyPath(Tab tab);

        void revealInFileManager(Tab tab);

        void revealInExplorer(Tab tab);
    }

    private EditorTabMenu() {
    }

    /** Puts a context menu on {@code tab} whose entries are rebuilt on every opening. */
    static void install(Tab tab, Actions actions) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("fxt-editor-tab-menu");
        menu.setOnShowing(e -> menu.getItems().setAll(items(tab, actions)));
        tab.setContextMenu(menu);
    }

    /** @return the entries for {@code tab} in their current state (package-private for tests) */
    static List<MenuItem> items(Tab tab, Actions actions) {
        OpenDocument doc = actions.documentOf(tab);
        boolean titled = doc != null && !doc.isUntitled();

        MenuItem close = item("tab-ctx-close", "Close", "bi-x-lg", ActionColor.NEUTRAL,
                () -> actions.close(tab));
        close.setDisable(!tab.isClosable());
        MenuItem closeOthers = item("tab-ctx-close-others", "Close Others", "bi-x-square", ActionColor.NEUTRAL,
                () -> actions.closeOthers(tab));
        closeOthers.setDisable(!actions.hasOtherClosableTabs(tab));

        List<MenuItem> items = new ArrayList<>();
        if (doc == null) {
            items.add(close);
            items.add(closeOthers);
            return items;
        }

        MenuItem reload = item("tab-ctx-reload", "Reload from Disk", "bi-arrow-clockwise", ActionColor.NAVIGATE,
                () -> actions.reload(tab));
        // No disk check here (network drives): a missing file is reported by the reload itself.
        reload.setDisable(!titled);
        MenuItem save = item("tab-ctx-save", "Save", "bi-save", ActionColor.CREATE, () -> actions.save(tab));
        save.setDisable(titled && !doc.isDirty());
        MenuItem saveAs = item("tab-ctx-save-as", "Save As…", "bi-save2", ActionColor.CREATE,
                () -> actions.saveAs(tab));
        MenuItem copyPath = item("tab-ctx-copy-path", "Copy Path", "bi-clipboard", ActionColor.NEUTRAL,
                () -> actions.copyPath(tab));
        copyPath.setDisable(!titled);
        MenuItem revealOs = item("tab-ctx-reveal-os", "Reveal in File Manager", "bi-folder2-open",
                ActionColor.NAVIGATE, () -> actions.revealInFileManager(tab));
        revealOs.setDisable(!titled);
        MenuItem revealExplorer = item("tab-ctx-reveal-explorer", "Reveal in Explorer Panel", "bi-layout-sidebar",
                ActionColor.NAVIGATE, () -> actions.revealInExplorer(tab));
        revealExplorer.setDisable(!titled);

        items.addAll(List.of(reload, new SeparatorMenuItem(), close, closeOthers, new SeparatorMenuItem(),
                save, saveAs, new SeparatorMenuItem(), copyPath, revealOs, revealExplorer));
        return items;
    }

    private static MenuItem item(String id, String text, String iconLiteral, ActionColor color, Runnable action) {
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(16);
        MenuItem item = new MenuItem(text, SemanticIcon.bind(icon, color));
        item.setId(id);
        item.setOnAction(e -> action.run());
        return item;
    }
}
