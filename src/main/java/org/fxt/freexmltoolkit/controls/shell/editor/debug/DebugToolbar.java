package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/** Builds the compact icon+text buttons of the debug panels' toolbars (Watch, Breakpoints). */
final class DebugToolbar {

    private DebugToolbar() {
    }

    static Button action(String text, String icon, String tooltip) {
        Button button = new Button(text);
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(14);
        button.setGraphic(graphic);
        button.getStyleClass().addAll("fxt-sp-action", "fxt-output-action");
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }
}
