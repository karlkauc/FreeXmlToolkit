package org.fxt.freexmltoolkit.controls.shell.editor.quickfix;

import javafx.beans.binding.Bindings;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.SemanticColors;

/**
 * Icon helpers for the quick-fix UI. Colors are {@code bind()}-ed, not set —
 * themed CSS ({@code -fx-icon-color}) overrides plainly set styleable properties
 * on every CSS pass.
 */
final class QuickFixIcons {

    /** The warning-yellow lightbulb color shared by all quick-fix affordances. */
    static final Color LIGHTBULB = Color.web(SemanticColors.WARNING);

    private QuickFixIcons() {
    }

    /** @return a lightbulb icon with a CSS-proof bound color */
    static IconifyIcon lightbulb(int size) {
        IconifyIcon icon = new IconifyIcon("bi-lightbulb");
        icon.setIconSize(size);
        icon.iconColorProperty().bind(Bindings.createObjectBinding(() -> LIGHTBULB));
        return icon;
    }
}
