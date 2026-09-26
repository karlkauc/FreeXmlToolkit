package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Objects;

import javafx.beans.value.ObservableValue;

import org.fxt.freexmltoolkit.controls.theme.ActionColor;

/**
 * Immutable description of one follow-on action offered by a side panel (e.g. "Generate
 * Documentation…"), rendered by {@link PanelActionList} as a full-width row with icon and
 * label. Build one with {@link #of(String, String, String, Runnable)} and refine it with
 * the wither methods:
 *
 * <pre>{@code
 * PanelAction.of("schema-tool-documentation", "bi-file-earmark-text",
 *         "Generate Documentation…", this::generateDocumentation)
 *     .color(ActionColor.CREATE)
 *     .disabledWhen(noSchemaOpen)
 * }</pre>
 *
 * @param id           the node id of the rendered button (for tests and lookups)
 * @param iconLiteral  a bundled Bootstrap icon name ({@code bi-*})
 * @param label        the visible text; never rely on a tooltip to name the action
 * @param tooltip      optional extra information (multi-select hints, preconditions), or {@code null}
 * @param onAction     what happens on click
 * @param disabledWhen optional binding that disables the row while {@code true}, or {@code null}
 * @param visibleWhen  optional binding that hides (visible + managed) the row while {@code false}, or {@code null}
 * @param primary      whether the row is rendered as the filled primary variant
 * @param color        the action-colour role that tints the row icon (default {@link ActionColor#NEUTRAL})
 */
record PanelAction(String id, String iconLiteral, String label, String tooltip, Runnable onAction,
                   ObservableValue<Boolean> disabledWhen, ObservableValue<Boolean> visibleWhen,
                   boolean primary, ActionColor color) {

    PanelAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(iconLiteral, "iconLiteral");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(onAction, "onAction");
        color = color == null ? ActionColor.NEUTRAL : color;
    }

    /** A plain (non-primary) action without tooltip or bindings. */
    static PanelAction of(String id, String iconLiteral, String label, Runnable onAction) {
        return new PanelAction(id, iconLiteral, label, null, onAction, null, null, false, ActionColor.NEUTRAL);
    }

    /** Adds a tooltip; use only when it says more than the label. */
    PanelAction tooltip(String text) {
        return new PanelAction(id, iconLiteral, label, text, onAction, disabledWhen, visibleWhen, primary, color);
    }

    /** Disables the row while the binding is {@code true}. */
    PanelAction disabledWhen(ObservableValue<Boolean> binding) {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, binding, visibleWhen, primary, color);
    }

    /** Hides the row (visible and managed) while the binding is {@code false}. */
    PanelAction visibleWhen(ObservableValue<Boolean> binding) {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, disabledWhen, binding, primary, color);
    }

    /** Renders the row as the filled primary variant (one per list at most). */
    PanelAction asPrimary() {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, disabledWhen, visibleWhen, true, color);
    }

    /** Sets the action-colour role of the row icon (spec 2026-09-26 §3); ignored on the primary row (white icon). */
    PanelAction color(ActionColor role) {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, disabledWhen, visibleWhen, primary, role);
    }
}
