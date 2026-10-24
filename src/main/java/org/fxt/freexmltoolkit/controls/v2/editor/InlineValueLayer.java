package org.fxt.freexmltoolkit.controls.v2.editor;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import org.fxmisc.richtext.CodeArea;

/**
 * A mouse-transparent layer over a {@link CodeArea} that shows short texts after the end of
 * given lines - the XSLT debugger's inline variable values while paused. RichTextFX has no
 * inline decorations, so the labels are positioned from the area's character bounds and
 * re-positioned whenever the viewport changes (scroll, resize, folding). Any edit clears the
 * values: their lines no longer match the paused run.
 */
public final class InlineValueLayer extends Pane {

    /** Style class of each value label. */
    public static final String VALUE_CLASS = "fxt-debug-inline-value";

    private static final double GAP = 18;

    private final CodeArea area;
    private Map<Integer, String> values = Map.of();
    private boolean layoutPending;

    public InlineValueLayer(CodeArea area) {
        this.area = area;
        getStyleClass().add("fxt-debug-inline-layer");
        setMouseTransparent(true);
        setPickOnBounds(false);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        area.viewportDirtyEvents().subscribe(e -> scheduleLayout());
        area.plainTextChanges().subscribe(change -> {
            if (!change.getInserted().equals(change.getRemoved())) {
                setValues(Map.of());
            }
        });
        widthProperty().addListener((obs, o, n) -> scheduleLayout());
        heightProperty().addListener((obs, o, n) -> scheduleLayout());
    }

    /** Shows {@code lineValues} (1-based line → text); an empty map removes all values. */
    public void setValues(Map<Integer, String> lineValues) {
        values = lineValues == null || lineValues.isEmpty() ? Map.of() : new TreeMap<>(lineValues);
        scheduleLayout();
    }

    /** @return the shown values (1-based line → text). */
    public Map<Integer, String> getValues() {
        return values;
    }

    private void scheduleLayout() {
        if (layoutPending) {
            return;
        }
        layoutPending = true;
        // Outside the area's own layout pass: reading character bounds from within it is unreliable.
        Platform.runLater(() -> {
            layoutPending = false;
            placeLabels();
        });
    }

    /** Re-creates the labels of the lines that are on screen. */
    void placeLabels() {
        getChildren().clear();
        if (values.isEmpty() || getScene() == null) {
            return;
        }
        String fontStyle = fontStyle();
        int paragraphs = area.getParagraphs().size();
        for (Map.Entry<Integer, String> entry : values.entrySet()) {
            int p = entry.getKey() - 1;
            if (p < 0 || p >= paragraphs || area.allParToVisibleParIndex(p).isEmpty()) {
                continue;
            }
            Optional<Bounds> end = lineEndOnScreen(p);
            if (end.isEmpty()) {
                continue;
            }
            Bounds local = screenToLocal(end.get());
            if (local == null) {
                continue;
            }
            Label label = new Label(entry.getValue());
            label.getStyleClass().add(VALUE_CLASS);
            label.setStyle(fontStyle);
            label.setMinHeight(local.getHeight());
            label.setPrefHeight(local.getHeight());
            label.setMaxHeight(local.getHeight());
            label.relocate(local.getMaxX() + GAP, local.getMinY());
            getChildren().add(label);
        }
    }

    /** Screen bounds of the end of paragraph {@code p}'s text (its last character, or its start when empty). */
    private Optional<Bounds> lineEndOnScreen(int p) {
        int start = area.getAbsolutePosition(p, 0);
        int length = area.getParagraphLength(p);
        // Trailing whitespace does not count as the end of the code on that line.
        String text = area.getParagraph(p).getText();
        while (length > 0 && Character.isWhitespace(text.charAt(length - 1))) {
            length--;
        }
        if (length == 0) {
            return area.getParagraphBoundsOnScreen(area.allParToVisibleParIndex(p).get());
        }
        return area.getCharacterBoundsOnScreen(start + length - 1, start + length);
    }

    /** The code's font as an inline style (inline styles win over theme rules for labels). */
    private String fontStyle() {
        Node text = area.lookup(".text");
        Font font = text instanceof Text t ? t.getFont() : null;
        if (font == null) {
            return "-fx-font-style: italic;";
        }
        return "-fx-font-family: '" + font.getFamily() + "'; -fx-font-size: " + font.getSize()
                + "px; -fx-font-style: italic;";
    }
}
