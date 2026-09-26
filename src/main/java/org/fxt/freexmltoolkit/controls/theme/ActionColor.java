package org.fxt.freexmltoolkit.controls.theme;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken;

/**
 * The action-colour roles for menu items and panel action rows (design spec
 * {@code docs/superpowers/specs/2026-09-26-workflow-colours-branding-design.md} §3).
 * One rule for every context menu and side-panel action list:
 * <ul>
 *   <li>{@link #CREATE} — Add, Insert, New, Paste, Duplicate, Validate, Generate</li>
 *   <li>{@link #DELETE} — Delete, Remove, Clear, Cut</li>
 *   <li>{@link #MODIFY} — Rename, Edit value, Comment toggle</li>
 *   <li>{@link #NAVIGATE} — Go to Definition, Reveal, Open referenced/report/tool, Find</li>
 *   <li>{@link #STRUCTURE} — Change Type, Cardinality, Move Up/Down, Reorder</li>
 *   <li>{@link #TOOL} — Format, Minify, Sort, Run/Execute in menus, Open/Save/Export, Download</li>
 *   <li>{@link #NEUTRAL} — Copy (all variants), Expand/Collapse, Select all, Undo/Redo, Settings</li>
 * </ul>
 * Roles map onto the existing semantic tokens; TEAL and INDIGO are not used for actions.
 * Paint an icon with {@code SemanticIcon.paint(icon, ActionColor.DELETE)} or bind it with
 * {@code SemanticIcon.bind(icon, ActionColor.DELETE)} where CSS would otherwise override it.
 */
public enum ActionColor {
    CREATE(ColorToken.SUCCESS),
    DELETE(ColorToken.DANGER),
    MODIFY(ColorToken.ACCENT),
    NAVIGATE(ColorToken.INFO),
    STRUCTURE(ColorToken.PURPLE),
    TOOL(ColorToken.PRIMARY),
    NEUTRAL(ColorToken.NEUTRAL);

    private final ColorToken token;

    ActionColor(ColorToken token) {
        this.token = token;
    }

    /** @return the theme-aware token this role paints with. */
    public ColorToken token() {
        return token;
    }
}
