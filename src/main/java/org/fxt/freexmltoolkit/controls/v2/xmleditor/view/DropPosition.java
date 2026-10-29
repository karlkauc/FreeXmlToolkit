package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

/**
 * Where a drag-and-drop operation in the grid puts its payload relative to the row under the
 * pointer: as the row's previous or next sibling, as its last child, or into its value cell
 * (replacing the value).
 */
public enum DropPosition {
    /** Insert before the target row's node (same parent). */
    BEFORE,
    /** Insert after the target row's node (same parent). */
    AFTER,
    /** Append as the last child of the target row's element. */
    INTO,
    /** Replace the target row's value (external text dropped on a value cell). */
    VALUE
}
