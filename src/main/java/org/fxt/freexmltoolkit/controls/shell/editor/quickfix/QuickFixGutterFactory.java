package org.fxt.freexmltoolkit.controls.shell.editor.quickfix;

import java.util.List;
import java.util.function.IntFunction;

import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;

/**
 * Renders a clickable lightbulb in the editor gutter on every line that has at
 * least one validation problem with quick fixes. Clicking it opens the
 * {@link QuickFixPopup} anchored at the icon.
 */
public final class QuickFixGutterFactory implements IntFunction<Node> {

    /** Callback invoked when the lightbulb of a line is clicked. */
    public interface LightbulbClickHandler {
        /**
         * @param line    the 1-based line
         * @param fixes   the fixes available on that line
         * @param screenX anchor x (screen coordinates)
         * @param screenY anchor y (screen coordinates)
         */
        void onLightbulbClicked(int line, List<SqfFixSuggestion> fixes, double screenX, double screenY);
    }

    private final IntFunction<List<SqfFixSuggestion>> fixesForLine;
    private final LightbulbClickHandler clickHandler;

    /**
     * @param fixesForLine returns the fixes available on a 1-based line (empty = no icon)
     * @param clickHandler invoked when the user clicks a lightbulb
     */
    public QuickFixGutterFactory(IntFunction<List<SqfFixSuggestion>> fixesForLine,
                                 LightbulbClickHandler clickHandler) {
        this.fixesForLine = fixesForLine;
        this.clickHandler = clickHandler;
    }

    @Override
    public Node apply(int lineIndex) {
        int line = lineIndex + 1; // paragraph index → 1-based line
        List<SqfFixSuggestion> fixes = fixesForLine.apply(line);
        if (fixes == null || fixes.isEmpty()) {
            return null;
        }
        StackPane pane = new StackPane(QuickFixIcons.lightbulb(14));
        pane.getStyleClass().add("quickfix-gutter-icon");
        pane.setCursor(Cursor.HAND);
        Tooltip.install(pane, new Tooltip(fixes.size() == 1
                ? "Quick fix: " + fixes.get(0).title()
                : fixes.size() + " quick fixes available"));
        pane.setOnMouseClicked(event -> {
            Bounds bounds = pane.localToScreen(pane.getBoundsInLocal());
            if (bounds != null && clickHandler != null) {
                clickHandler.onLightbulbClicked(line, fixes, bounds.getMinX(), bounds.getMaxY());
            }
            event.consume();
        });
        return pane;
    }
}
