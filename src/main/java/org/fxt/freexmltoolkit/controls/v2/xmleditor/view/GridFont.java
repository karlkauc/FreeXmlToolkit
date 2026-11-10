package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * The fonts used by the grid, as a toolkit-free handle.
 *
 * <p>Layout code (and its pure-Java unit tests) refers to fonts through this enum so
 * that {@link TextMeasurer} implementations can work without a JavaFX toolkit. The
 * real {@link Font} is created lazily by {@link #toFont()} and must only be requested
 * from FX-side code.</p>
 */
public enum GridFont {
    /** Regular row/value font (editor font, 12). */
    ROW(12, FontWeight.NORMAL),
    /** Semi-bold label font (editor font, 12). */
    ROW_BOLD(12, FontWeight.SEMI_BOLD),
    /** Small secondary font for counts and hints (editor font, 10). */
    SMALL(10, FontWeight.NORMAL),
    /** Bold icon-glyph font (editor font, 11). */
    ICON(11, FontWeight.BOLD);

    /**
     * The editor font's family (Settings → Editor → Editor font), resolved to an installed one.
     * The painter caches its fonts and text widths, so a change reaches the grid after a restart.
     */
    private static final String FAMILY = editorFamily();

    private static String editorFamily() {
        try {
            var font = org.fxt.freexmltoolkit.controls.shared.EditorFont.current();
            return org.fxt.freexmltoolkit.controls.shared.EditorFont.resolveFamily(font.family());
        } catch (RuntimeException | Error e) {
            return "Monospaced";
        }
    }

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
