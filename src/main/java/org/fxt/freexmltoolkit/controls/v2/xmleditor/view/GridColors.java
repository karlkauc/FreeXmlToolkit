package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;

/**
 * The user-configurable cell colours of the grid's embedded tables (XMLSpy: Fonts and Colors →
 * Grid View → Cell Colors), per theme. Without an override a slot uses its design-token
 * default, so both themes look right out of the box. Canvases register a (weakly held)
 * listener and repaint when an override changes.
 */
public final class GridColors {

    /** The configurable colour slots. */
    public enum Slot {
        /** Column headers, row numbers and the corner cell. */
        TABLE_HEADER("Table header"),
        /** Cells whose member lacks the column's attribute/element. */
        MISSING_CELL("Missing cell"),
        /** Selected rows and cells. */
        SELECTION("Selection"),
        /** The frame of the focused cell and selected rows. */
        FOCUS("Focus frame");

        private final String label;

        Slot(String label) {
            this.label = label;
        }

        /** @return the user-facing name */
        public String label() {
            return label;
        }
    }

    private static final Map<DesignTokens.Theme, EnumMap<Slot, Color>> OVERRIDES = new EnumMap<>(DesignTokens.Theme.class);
    private static final CopyOnWriteArrayList<WeakReference<Runnable>> LISTENERS = new CopyOnWriteArrayList<>();

    private GridColors() {
    }

    /** @return the design-token default of a slot in a theme */
    public static Color defaultColor(DesignTokens.Theme theme, Slot slot) {
        return switch (slot) {
            case TABLE_HEADER -> DesignTokens.ColorToken.BG_SURFACE_2.color(theme);
            case MISSING_CELL -> DesignTokens.ColorToken.BG_CANVAS.color(theme);
            case SELECTION -> DesignTokens.ColorToken.BG_SUBTLE.color(theme);
            case FOCUS -> DesignTokens.ColorToken.PRIMARY.color(theme);
        };
    }

    /** @return the user's colour of a slot, or {@code null} for the default */
    public static synchronized Color override(DesignTokens.Theme theme, Slot slot) {
        EnumMap<Slot, Color> map = OVERRIDES.get(theme);
        return map == null ? null : map.get(slot);
    }

    /** @return the effective colour of a slot: the user's or the default */
    public static Color resolve(DesignTokens.Theme theme, Slot slot) {
        Color c = override(theme, slot);
        return c != null ? c : defaultColor(theme, slot);
    }

    /**
     * Sets or clears the user's colour of a slot and repaints the grids.
     *
     * @param color the colour, or {@code null} to return to the default
     */
    public static void setOverride(DesignTokens.Theme theme, Slot slot, Color color) {
        synchronized (GridColors.class) {
            EnumMap<Slot, Color> map = OVERRIDES.computeIfAbsent(theme, t -> new EnumMap<>(Slot.class));
            if (color == null) {
                map.remove(slot);
            } else {
                map.put(slot, color);
            }
        }
        notifyListeners();
    }

    /** Clears every override of every theme. */
    public static void clearAll() {
        synchronized (GridColors.class) {
            OVERRIDES.clear();
        }
        notifyListeners();
    }

    /** Registers a weakly held repaint listener; the caller must keep a strong reference. */
    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(new WeakReference<>(listener));
        }
    }

    private static void notifyListeners() {
        for (WeakReference<Runnable> ref : LISTENERS) {
            Runnable listener = ref.get();
            if (listener == null) {
                LISTENERS.remove(ref);
                continue;
            }
            try {
                listener.run();
            } catch (RuntimeException e) {
                // a misbehaving grid must not break the others
            }
        }
    }

    /** @return the property key of a slot ({@code tableHeader}, {@code missingCell}, …) */
    public static String key(Slot slot) {
        return switch (slot) {
            case TABLE_HEADER -> "tableHeader";
            case MISSING_CELL -> "missingCell";
            case SELECTION -> "selection";
            case FOCUS -> "focus";
        };
    }

    /** @return the property suffix of a theme ({@code light} / {@code dark}) */
    public static String key(DesignTokens.Theme theme) {
        return theme == DesignTokens.Theme.DARK ? "dark" : "light";
    }

    /** @return {@code #rrggbb} for a colour */
    public static String toHex(Color c) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
