package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

/**
 * Measures the rendered width of a string in one of the {@link GridFont}s, in unscaled
 * pixels. Layout code never estimates widths from character counts; it asks a measurer.
 *
 * <p>Two implementations exist: {@link #estimating()} is toolkit-free (used as the
 * default inside model classes and by unit tests) and {@link #fx()} measures with a real
 * JavaFX {@code Text} node.</p>
 */
public interface TextMeasurer {

    /** Glyph-width factor of the estimating measurer relative to the font size. */
    double ESTIMATE_FACTOR = 0.6;

    /**
     * @param text the text to measure ({@code null} and empty measure as 0)
     * @param font the font to measure in
     * @return the width in unscaled pixels
     */
    double width(String text, GridFont font);

    /**
     * @return a toolkit-free measurer that assumes {@code size * 0.6} px per glyph (7.2 px
     * for the 12 pt row font, 6.0 px for the 10 pt small font)
     */
    static TextMeasurer estimating() {
        return (text, font) -> text == null ? 0 : text.length() * font.size() * ESTIMATE_FACTOR;
    }

    /**
     * @return a measurer backed by a JavaFX {@code Text} node; must be used on the FX thread
     */
    static TextMeasurer fx() {
        return new FxTextMeasurer();
    }
}
