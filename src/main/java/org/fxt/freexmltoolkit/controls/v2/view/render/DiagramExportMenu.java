package org.fxt.freexmltoolkit.controls.v2.view.render;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * The "Export View…" / "Copy View as Image" items of the views' own context menus. The views
 * live below the shell, so the shell registers what the items do ({@link #setHandler}); without a
 * handler (a view used on its own, tests) the menus get no export items.
 */
public final class DiagramExportMenu {

    /** What the items do; implemented by the shell. */
    public interface Handler {
        /** Asks for options and file and exports the view. */
        void export(ExportableDiagram diagram, Node anchor);

        /** Puts the view on the clipboard as an image. */
        void copy(ExportableDiagram diagram, Node anchor);
    }

    private static volatile Handler handler;

    private DiagramExportMenu() {
    }

    /** @param newHandler what the items do, or null to leave them out */
    public static void setHandler(Handler newHandler) {
        handler = newHandler;
    }

    /**
     * @param diagram the view the menu belongs to
     * @param anchor  a node of that view (its window owns dialogs)
     * @return a separator and the two items, or an empty list without a handler
     */
    public static List<MenuItem> items(ExportableDiagram diagram, Node anchor) {
        Handler current = handler;
        if (current == null || diagram == null) {
            return List.of();
        }
        MenuItem export = item(I18n.t("ViewExport.menu.export"), "bi-image", "diagram-export-view",
                () -> current.export(diagram, anchor));
        MenuItem copy = item(I18n.t("ViewExport.menu.copy"), "bi-clipboard", "diagram-copy-view",
                () -> current.copy(diagram, anchor));
        return List.of(new SeparatorMenuItem(), export, copy);
    }

    private static MenuItem item(String text, String icon, String id, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(12);
        item.setGraphic(SemanticIcon.paint(graphic, ActionColor.TOOL));
        // The XSD menus disable everything not marked safe in read-only mode; exporting is safe
        item.getProperties().put("fxt.readOnlySafe", Boolean.TRUE);
        item.setOnAction(e -> action.run());
        return item;
    }
}
