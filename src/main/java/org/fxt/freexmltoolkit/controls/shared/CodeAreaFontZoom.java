package org.fxt.freexmltoolkit.controls.shared;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;

import org.fxmisc.richtext.CodeArea;

/**
 * Shared utility that adds editor-style font zooming to a {@link CodeArea}:
 * Ctrl+MouseWheel grows/shrinks the font, Ctrl+0 resets it. Each area zooms
 * independently and the size is applied as an inline {@code -fx-font-size}
 * style, so the font family keeps coming from the area's CSS style class.
 * The size is not persisted (matching the main text editor).
 */
public final class CodeAreaFontZoom {

    public static final int DEFAULT_FONT_SIZE = 11;
    public static final int MIN_FONT_SIZE = 6;
    public static final int MAX_FONT_SIZE = 72;

    /** Properties key under which the current font size (Integer, px) is stored. */
    private static final Object SIZE_KEY = new Object();

    private CodeAreaFontZoom() {
        // Utility class - no instantiation
    }

    /**
     * Installs Ctrl+MouseWheel zoom and Ctrl+0 reset on {@code area}. A plain
     * mouse wheel (without Ctrl) keeps scrolling the area as usual.
     *
     * @param area the code area to make zoomable
     */
    public static void install(CodeArea area) {
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
                setFontSize(area, DEFAULT_FONT_SIZE);
                event.consume();
            }
        });
    }

    /**
     * The current font size of {@code area} in px ({@link #DEFAULT_FONT_SIZE}
     * until the user zooms).
     */
    public static int fontSizeOf(CodeArea area) {
        return area.getProperties().get(SIZE_KEY) instanceof Integer size ? size : DEFAULT_FONT_SIZE;
    }

    /** Clamps {@code size} to [{@link #MIN_FONT_SIZE}, {@link #MAX_FONT_SIZE}] and applies it. */
    private static void setFontSize(CodeArea area, int size) {
        int clamped = Math.clamp(size, MIN_FONT_SIZE, MAX_FONT_SIZE);
        area.getProperties().put(SIZE_KEY, clamped);
        area.setStyle("-fx-font-size: " + clamped + "px;");
    }
}
