package org.fxt.freexmltoolkit.controls.theme;

/**
 * Central catalogue of the application's <em>semantic</em> icon/accent colours as hex
 * strings, so controls stop re-typing raw {@code "#rrggbb"} literals inline (which had
 * drifted into ~500 distinct values across the code base).
 *
 * <p>These are the Bootstrap-derived values that the older {@code controls/} and
 * {@code controls/v2/} code already uses pervasively (and that {@code STYLE_GUIDE.jsonc}
 * documents), so replacing inline literals with these constants is a behaviour-preserving,
 * zero-visual-change refactor.
 *
 * <p><b>Note on the two palettes:</b> the modern Unified Shell themes via the {@code -fxt-*}
 * design tokens in {@code css/design-tokens.css}, whose semantic values differ slightly
 * (e.g. danger {@code #e03131} vs. {@code #dc3545} here). Reconciling the two value sets
 * into a single palette is a separate, visually-scoped step (usability report "#10b") —
 * this class first eliminates the scattered literals without changing any rendered colour.
 *
 * <p>Values are exposed as {@code String} because the icon controls
 * ({@code IconifyIcon}, {@code DialogHelper}, context-menu factories) take a hex string.
 * Use {@link javafx.scene.paint.Color#web(String)} when a {@code Color} is required.
 */
public final class SemanticColors {

    private SemanticColors() {
        // constants holder
    }

    /** Success / positive action (green) — add, valid, save-success. */
    public static final String SUCCESS = "#28a745";
    /** Danger / destructive action (red) — delete, error. */
    public static final String DANGER = "#dc3545";
    /** Warning / caution (amber). */
    public static final String WARNING = "#ffc107";
    /** Informational / secondary emphasis (cyan). */
    public static final String INFO = "#17a2b8";
    /** Primary action / link (blue). */
    public static final String PRIMARY = "#007bff";
    /** Neutral / muted (gray) — structural, non-semantic actions. */
    public static final String NEUTRAL = "#6c757d";
    /** Advanced / special (purple) — e.g. cardinality, XPath/XQuery. */
    public static final String PURPLE = "#6f42c1";
    /** Transform / move (orange) — e.g. cut, rename, XSLT. */
    public static final String ORANGE = "#fd7e14";
    /** Copy / duplicate (teal). */
    public static final String TEAL = "#20c997";
    /** Secondary accent (indigo) — advanced/less-common actions. */
    public static final String INDIGO = "#6610f2";
}
