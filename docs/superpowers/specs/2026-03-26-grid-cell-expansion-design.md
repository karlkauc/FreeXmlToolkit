# Inline Expansion for Complex Table Cells

**Date:** 2026-03-26
**Status:** Approved

## Summary

In the RepeatingElementsTable (grid view), table cells containing complex XML elements (elements with children) currently show only a summary text. Users cannot see or interact with the child nodes. This spec adds inline expand/collapse to those cells, rendering children as flat rows within the cell.

## Current Behavior

A column like "Bond" that contains `<Bond><Issuer>ABC</Issuer><Coupon>5.0</Coupon></Bond>` shows only `"Issuer, Coupon"` (a summary of child element names). The children are invisible and not editable.

## New Behavior

### Expand Indicator

- Cells with complex elements show a small expand arrow (►) to the left of the summary text.
- Clicking the arrow expands the cell. The arrow becomes ▼.
- Clicking again collapses back to the summary.

### Expanded Cell Content

When expanded, the cell grows vertically and shows the child nodes as flat rows using the same XMLSpy-style rendering:
- `<>` icon + name + value for child elements
- `=` icon + name + value for attributes of the complex element
- `T` icon for text nodes
- Indentation for nested levels
- Recursive expansion: child elements with their own children can also be expanded.

### Row Height

- The table row height becomes the maximum of all cell heights in that row.
- Non-expanded cells in the same row are top-aligned.
- The total table height is recalculated when any cell expands/collapses.

### Interaction

- **Single click on arrow:** Toggle expand/collapse for that cell.
- **Double-click on a value** in the expanded content: Start inline editing (same as in the tree view — uses Commands for undo/redo).
- **Context menu** on expanded content: Same as regular tree view context menu.

## Affected Files

### RepeatingElementsTable.java
- Re-add expand state tracking per cell: `TableRow.expandedColumns` (Set<String>) and `TableRow.isColumnExpanded(columnName)`/`toggleColumnExpanded(columnName)`.
- Store flattened child rows per expanded cell: `TableRow.expandedCellRows` (Map<String, List<FlatRow>>).
- `calculateRowHeight(TableRow)` returns dynamic height based on expanded cells.
- `getHeight()` accounts for variable row heights.

### XmlCanvasView.java
- Render expanded cell content: iterate `FlatRow` list for the cell and draw each row with icon, indent, name, value.
- Draw expand/collapse arrow in complex cells.
- Hit-testing: detect clicks on cell expand arrows vs. cell content vs. expanded sub-rows.
- Inline editing: `startEditingTableCell` must handle clicks on expanded sub-rows (delegate to `startEditingValue` for the sub-row's FlatRow).
- Recalculate `rowYPositions` when cell expansion changes table height.

### FlatRow.java
- No changes needed. The existing `flatten()` method can be used to flatten a single `XmlElement` into rows for cell content. Add a convenience method: `static List<FlatRow> flattenElement(XmlElement element)`.

## Performance

- Expansion is lazy: child rows are only created when the cell is first expanded.
- Cell rows are cached in `TableRow.expandedCellRows` and cleared on model changes.
