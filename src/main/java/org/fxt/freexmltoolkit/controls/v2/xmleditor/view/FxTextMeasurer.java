package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.text.Text;

/**
 * {@link TextMeasurer} backed by one reusable {@link Text} node per {@link GridFont}.
 *
 * <p>The grid fonts are monospaced, so for printable ASCII the width is
 * {@code length * advance} where the advance is measured once per font — no layout pass.
 * Every other string (non-ASCII, control characters) is measured with the node and the
 * result cached in a small per-font LRU map. Must be used on the JavaFX thread.</p>
 */
final class FxTextMeasurer implements TextMeasurer {

    private static final int CACHE_LIMIT = 20_000;

    private final Map<GridFont, Text> nodes = new EnumMap<>(GridFont.class);
    private final Map<GridFont, Double> asciiAdvance = new EnumMap<>(GridFont.class);
    private final Map<GridFont, Map<String, Double>> cache = new EnumMap<>(GridFont.class);

    @Override
    public double width(String text, GridFont font) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double advance = advanceFor(font);
        if (advance > 0 && isPrintableAscii(text)) {
            return text.length() * advance;
        }
        Map<String, Double> fontCache = cache.computeIfAbsent(font, f -> new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                return size() > CACHE_LIMIT;
            }
        });
        Double cached = fontCache.get(text);
        if (cached != null) {
            return cached;
        }
        double w = measure(text, font);
        fontCache.put(text, w);
        return w;
    }

    /**
     * @return the per-glyph advance for printable ASCII, or 0 when the font turned out not
     * to be monospaced (then every string goes through the node)
     */
    private double advanceFor(GridFont font) {
        Double known = asciiAdvance.get(font);
        if (known != null) {
            return known;
        }
        double wide = measure("M", font);
        double narrow = measure("i", font);
        double advance = Math.abs(wide - narrow) < 0.01 ? wide : 0;
        asciiAdvance.put(font, advance);
        return advance;
    }

    private double measure(String text, GridFont font) {
        Text node = nodes.computeIfAbsent(font, f -> {
            Text t = new Text();
            t.setFont(f.toFont());
            return t;
        });
        node.setText(text);
        return node.getLayoutBounds().getWidth();
    }

    private static boolean isPrintableAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }
}
