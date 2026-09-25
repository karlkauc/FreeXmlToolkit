package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * The fonts used by the XMLSpy-style grid, as a toolkit-free handle.
 *
 * <p>Layout code (and its pure-Java unit tests) refers to fonts through this enum so
 * that {@link TextMeasurer} implementations can work without a JavaFX toolkit. The
 * real {@link Font} is created lazily by {@link #toFont()} and must only be requested
 * from FX-side code.</p>
 */
public enum GridFont {
    /** Regular row/value font (Monospaced 12). */
    ROW(12, FontWeight.NORMAL),
    /** Semi-bold label font (Monospaced 12). */
    ROW_BOLD(12, FontWeight.SEMI_BOLD),
    /** Small secondary font for counts and hints (Monospaced 10). */
    SMALL(10, FontWeight.NORMAL),
    /** Bold icon-glyph font (Monospaced 11). */
    ICON(11, FontWeight.BOLD);

    private static final String FAMILY = "Monospaced";

    private final double size;
    private final FontWeight weight;
    private volatile Font font;

    GridFont(double size, FontWeight weight) {
        this.size = size;
        this.weight = weight;
    }

    /** @return the point size of this font */
    public double size() {
        return size;
    }

    /** @return the font weight */
    public FontWeight weight() {
        return weight;
    }

    /**
     * @return the JavaFX font (created on first use; requires an initialized toolkit)
     */
    public Font toFont() {
        Font f = font;
        if (f == null) {
            f = Font.font(FAMILY, weight, size);
            font = f;
        }
        return f;
    }
}
