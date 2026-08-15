package org.fxt.freexmltoolkit.controls.shell.editor.quickfix;

import java.util.function.Consumer;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;

/**
 * Builds the "Quick Fix" submenu offered on validation problem rows — shared by
 * the Validation panel, the Problems strip and the Schematron report view.
 */
public final class QuickFixMenuFactory {

    private QuickFixMenuFactory() {
    }

    /**
     * @param problem the problem whose fixes are offered (must have fixes)
     * @param onPick  invoked with the chosen fix
     * @return the ready-made submenu
     */
    public static Menu buildQuickFixMenu(ValidationProblem problem, Consumer<SqfFixSuggestion> onPick) {
        Menu menu = new Menu("Quick Fix");
        menu.setGraphic(QuickFixIcons.lightbulb(16));
        for (SqfFixSuggestion fix : problem.fixes()) {
            MenuItem item = new MenuItem(fix.title()
                    + (fix.needsUserInput() ? "…" : ""));
            item.setGraphic(QuickFixIcons.lightbulb(16));
            item.setOnAction(e -> onPick.accept(fix));
            menu.getItems().add(item);
        }
        return menu;
    }
}
