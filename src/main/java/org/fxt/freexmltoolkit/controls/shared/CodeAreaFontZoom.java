package org.fxt.freexmltoolkit.controls.shared;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;

import org.fxmisc.richtext.CodeArea;

/**
 * Shared utility for the smaller code areas (query console, output panels, value viewer): they
 * take the user's editor font ({@link EditorFont}: family, line spacing, and its size in px) and
 * follow changes live. Ctrl+MouseWheel zooms one area on its own, Ctrl+0 returns it to the
 * editor font's size. The zoom is not stored.
 */
public final class CodeAreaFontZoom {

    public static final int DEFAULT_FONT_SIZE = EditorFont.DEFAULT_SIZE;
    public static final int MIN_FONT_SIZE = EditorFont.MIN_SIZE;
    public static final int MAX_FONT_SIZE = EditorFont.MAX_SIZE;

    /** Properties key under which an area's own zoom (Integer, px) is stored. */
    private static final Object SIZE_KEY = new Object();
    /** Properties key holding the area's editor-font listener (kept alive with the area). */
    private static final Object LISTENER_KEY = new Object();

    private CodeAreaFontZoom() {
        // Utility class - no instantiation
    }

    /**
     * Applies the editor font to {@code area}, follows its changes, and installs Ctrl+MouseWheel
     * zoom and Ctrl+0 reset. A plain mouse wheel (without Ctrl) keeps scrolling the area as usual.
     *
     * @param area the code area to make zoomable
     */
    public static void install(CodeArea area) {
        ChangeListener<EditorFont.Settings> listener = (obs, old, value) -> apply(area);
        area.getProperties().put(LISTENER_KEY, listener);
        EditorFont.settingsProperty().addListener(new WeakChangeListener<>(listener));
        apply(area);
        area.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown()) {
                return;
            }
            if (event.getDeltaY() > 0) {
                setFontSize(area, fontSizeOf(area) + 1);
            } else if (event.getDeltaY() < 0) {
                setFontSize(area, fontSizeOf(area) - 1);
            }
            event.consume();
        });
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown()
                    && (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0)) {
                area.getProperties().remove(SIZE_KEY);
                apply(area);
                event.consume();
            }
        });
    }

    /**
     * The current font size of {@code area} in px (the editor font's size until the user zooms
     * this area).
     */
    public static int fontSizeOf(CodeArea area) {
        return area.getProperties().get(SIZE_KEY) instanceof Integer size ? size : EditorFont.current().size();
    }

    /** Clamps {@code size} to [{@link #MIN_FONT_SIZE}, {@link #MAX_FONT_SIZE}] and applies it. */
    private static void setFontSize(CodeArea area, int size) {
        area.getProperties().put(SIZE_KEY, Math.clamp(size, MIN_FONT_SIZE, MAX_FONT_SIZE));
        apply(area);
    }

    private static void apply(CodeArea area) {
        EditorFont.Settings font = EditorFont.current();
        int size = fontSizeOf(area);
        area.setStyle(EditorFont.inlineStyle(font, size, "px"));
        EditorFont.applyLineSpacing(area, size, font.lineSpacing());
    }
}
