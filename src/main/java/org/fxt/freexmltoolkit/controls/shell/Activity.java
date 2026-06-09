package org.fxt.freexmltoolkit.controls.shell;

import java.util.Optional;

/**
 * The activities of the new Unified shell, shown as icons in the left Activity
 * Bar (VS-Code style). The declaration order is the Activity Bar order and
 * matches the Figma mockups (file "FreeXmlToolkit — UI Modernization").
 * <p>
 * Each activity carries a stable {@link #id()} (used for persistence and
 * {@link #fromId(String)} lookup), a human-readable {@link #label()}, and a
 * Bootstrap {@link #icon()} literal resolvable by {@code IconifyIcon}.
 */
public enum Activity {
    EXPLORER("explorer", "Explorer", "bi-folder2-open"),
    FAVORITES("favorites", "Favorites", "bi-star"),
    VALIDATION("validation", "Validation", "bi-check2-circle"),
    TRANSFORM("transform", "Transform", "bi-arrow-repeat"),
    SCHEMA("schema", "Schema", "bi-diagram-3"),
    PDF_FOP("pdf", "PDF / FOP", "bi-file-earmark-pdf"),
    SIGNATURE("signature", "Signature", "bi-shield-lock"),
    FUNDSXML("fundsxml", "FundsXML", "bi-file-earmark-code"),
    HELP("help", "Help", "bi-question-circle"),
    SETTINGS("settings", "Settings", "bi-gear");

    private final String id;
    private final String label;
    private final String icon;

    Activity(String id, String label, String icon) {
        this.id = id;
        this.label = label;
        this.icon = icon;
    }

    /** @return the stable identifier (used for persistence and lookup). */
    public String id() {
        return id;
    }

    /** @return the human-readable label shown in tooltips/headers. */
    public String label() {
        return label;
    }

    /** @return the Bootstrap icon literal (e.g. {@code bi-gear}). */
    public String icon() {
        return icon;
    }

    /** @return the activity shown when the shell first opens. */
    public static Activity defaultActivity() {
        return EXPLORER;
    }

    /**
     * Resolves an activity by its {@link #id()}.
     *
     * @param id the id, may be {@code null}
     * @return the matching activity, or {@link Optional#empty()} if none / {@code null}
     */
    public static Optional<Activity> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Activity a : values()) {
            if (a.id.equals(id)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
