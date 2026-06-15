package org.fxt.freexmltoolkit.controls.shell;

/**
 * Pure mapping helpers for the editor toolbar's display settings (icon size).
 * Kept free of JavaFX so the size logic is unit-testable.
 */
public final class ToolbarDisplay {

    /** Icon edge length in pixels for the small / large toolbar modes. */
    public static final int SMALL_PX = 16;
    public static final int LARGE_PX = 22;

    /** CSS marker classes toggled on each toolbar button per size mode. */
    public static final String SMALL_CLASS = "fxt-tool-small";
    public static final String LARGE_CLASS = "fxt-tool-large";

    private ToolbarDisplay() {
    }

    /** @return the icon pixel size for the given mode. */
    public static int iconSizePx(boolean large) {
        return large ? LARGE_PX : SMALL_PX;
    }

    /** @return the CSS size class for the given mode. */
    public static String sizeStyleClass(boolean large) {
        return large ? LARGE_CLASS : SMALL_CLASS;
    }
}
