# XMLSpy-Style Grid View for Graphical XML Editor

**Date:** 2026-03-26
**Status:** Approved
**Approach:** Canvas-Rendering Umbau (Ansatz A)

## Summary

Redesign the graphical XML view (`XmlCanvasView`) from a nested-grids layout to a flat, row-based layout inspired by Altova XMLSpy's Grid View. The view retains the modern AtlantaFX styling (subtle colors, light rounding) while adopting XMLSpy's structural features: distinctive node icons, tall vertical expand/collapse buttons with tree lines, a flat attribute/element list, and automatic grid/table view for repeating elements.

## Reference

XMLSpy Grid View screenshot: `https://www.altova.com/manual/de/xmlspy/spyprofessional/images/xsxmlgridview01.png`

Key characteristics observed:
- `<>` diamond icons for elements, `=` icons for attributes
- Tall vertical expand/collapse bar spanning the height of all children, with up/down arrows
- Solid vertical/horizontal connection lines between parent and child nodes
- Attributes and child elements in a flat list (attributes first, then children)
- Automatic table view for repeating elements with column headers (showing `=`/`<>` icons), row numbers

## Data Model: FlatRow

Replace `NestedGridNode` (each element = own mini-grid with x/y/width/height) with a flat list of `FlatRow` objects. Every visible item in the view — element, attribute, text, comment — is one `FlatRow`.

```java
FlatRow {
    type:       ELEMENT | ATTRIBUTE | TEXT | COMMENT | CDATA | PI | DOCUMENT
    depth:      int                    // indentation level
    modelNode:  XmlNode                // reference to underlying model
    parentRow:  FlatRow                // for expand/collapse calculations
    label:      String                 // display name ("Company", "xmlns", "#text")
    value:      String                 // null for elements with children, value for attributes/text
    expanded:   boolean                // only meaningful for elements with children
    visible:    boolean                // false when parent is collapsed
    childCount: int                    // number of direct children (for "(3)" display)
}
```

### Flattening Algorithm

An XML tree like:
```xml
<Company xmlns="http://...">
  <Address xsi:type="US-Address">
    <Name>US dependency</Name>
    <Street>Noble Ave.</Street>
  </Address>
</Company>
```

Becomes:
```
Row 0: ELEMENT    depth=0  "Company"              expanded=true
Row 1: ATTRIBUTE  depth=1  "xmlns" = "http://..."
Row 2: ELEMENT    depth=1  "Address"              expanded=true
Row 3: ATTRIBUTE  depth=2  "xsi:type" = "US-Address"
Row 4: ELEMENT    depth=2  "Name" = "US dependency"   (leaf)
Row 5: ELEMENT    depth=2  "Street" = "Noble Ave."    (leaf)
```

The `FlatRow` class contains the static flattening method: `static List<FlatRow> flatten(XmlDocument doc)` which recursively walks the tree. For each element: first emit the element row, then its attribute rows, then recurse into child elements. Repeating elements (multiple children with same tag) are detected during flattening and replaced with a table reference.

Visibility is managed by toggling: when an element is collapsed, all descendant rows have `visible = false`. Only visible rows are rendered.

## Rendering: Row-Based Canvas Drawing

### Row Layout

Every row has a fixed height (`ROW_HEIGHT = 24px`) and is rendered as:

```
| [Expand-Bar] [Indentation] [Icon] [Name]              [Value]          |
|               <- depth*INDENT ->                                        |
```

### Two-Column Layout

- **Name column:** Icon + label, left-aligned. Width dynamically calculated as max of all visible name widths + indentation.
- **Value column:** Attribute value / text content, left-aligned, fills remaining width.

### Icons

| Type | Icon | Color |
|------|------|-------|
| Element | `<>` diamond/angle brackets | Blue `#0066cc` |
| Attribute | `=` equals sign | Brown/Red `#e81416` |
| Text | `T` | Green `#008000` |
| Comment | `<!-- -->` | Teal |
| Processing Instruction | `<??>` | Violet |

### Visual Styling

- **Row separators:** Thin horizontal lines (0.5px, light gray) between every row
- **Background:** Subtle zebra-striping (white / very light gray alternating)
- **Selection:** Blue highlight background with blue border (as currently)
- **Hover:** Light gray background on hovered row
- **Leaf elements:** Single row with name and value (e.g., `<> Name    US dependency`)
- **Fonts:** Monospaced, as currently configured in `XmlCanvasRenderingHelper`

## Expand/Collapse: Vertical Bar with Tree Lines

### Expand Bar

For every element with children, a vertical bar (width ~12px) is drawn at the left edge, spanning the full height of all visible descendant rows:

```
arrow-down  <> Company
|              = xmlns        http://...
|              = xmlns:xsi    http://...
|           arrow-down  <> Address
|           |              = xsi:type  US-Address
|           |              <> Name     US dependency
|           |              <> Street   Noble Ave.
|           arrow-up
|           arrow-down  <> Person (3)  grid-icon
|           |           ... table ...
|           arrow-up
arrow-up
```

### Bar Details

- **Width:** 12px
- **Top:** Down-arrow icon (expanded) or right-arrow icon (collapsed)
- **Bottom:** Up-arrow icon (click target to collapse)
- **Color:** Subtle gray, slightly darker on hover
- **Click behavior:** Clicking anywhere on the bar or on the arrows **recursively** collapses/expands all descendant elements

### Connection Lines

- **Vertical lines:** Solid, from each expand bar downward through all children
- **Horizontal branches:** At each child row, a horizontal line extends from the vertical line to the child's icon
- **Color:** Light gray (`#d0d0d0`), line width 1px
- **Style:** Solid lines (matching XMLSpy)

## Grid View for Repeating Elements

### Detection

When an element has multiple direct children with the same tag name (e.g., 3x `<Person>`), the table view activates automatically. This is fixed (no toggle between grid and single view).

### Display in Row Flow

The table appears as a block within the flat row list, indented to match the element's depth:

```
|  arrow-down  <> Person (3)  grid-icon
|  |  +------+-----------+--------+-------------+----------+
|  |  |      | = Manager | = Degree| = Programmer| <> First |  <- column headers with icons
|  |  +------+-----------+--------+-------------+----------+
|  |  |  1   | false     | MA     | true        | Alfred   |  <- row numbers
|  |  |  2   | true      | Ph.D   | false       | Colin    |
|  |  |  3   | true      | BA     | false       | Fred     |
|  |  +------+-----------+--------+-------------+----------+
|  arrow-up
```

### Table Details

- **Grid icon (3x3 grid symbol):** Displayed next to element name in the header row
- **Column headers:** Each column shows `=` (attribute) or `<>` (element) icon before the column name
- **Row numbers:** Leftmost column shows 1, 2, 3...
- **Inline editing:** Double-click on a cell opens an edit field (existing behavior preserved)
- **Selection:** Whole row highlighted on click

### Implementation

The existing `RepeatingElementsTable` class is reused for data management. Rendering is adapted to:
1. Add icons to column headers
2. Add row number column
3. Integrate into the flat row flow (the table occupies a vertical block)

## Interaction and Inline Editing

### Click Handling

Hit-detection is simplified by the flat layout:
- `clickedRowIndex = (mouseY + scrollOffsetY) / ROW_HEIGHT`
- Click on expand bar: Recursively toggle children visibility, recalculate flat row list
- Click on name column: Select row
- Double-click on name: Rename element (`RenameNodeCommand`)
- Double-click on value: Edit attribute/text (`SetAttributeCommand` / `SetTextCommand`)

### Inline Edit Field

- `TextField` positioned over the cell: Y = `rowIndex * ROW_HEIGHT - scrollOffsetY`, X = column position
- Enter confirms, Escape cancels (identical to current behavior)
- `TypeAwareWidgetFactory` continues to be supported for type-aware widgets

### Context Menu

- Right-click opens `XmlGridContextMenu` (unchanged)
- All actions (Add Element, Add Attribute, Delete, etc.) work through existing Commands

### Keyboard Navigation

- Arrow up/down: Move to next/previous visible row
- Arrow right: Expand element (if collapsed)
- Arrow left: Collapse element (if expanded), or jump to parent element
- F2: Start editing
- Ctrl+Z/Y: Undo/Redo (unchanged)

### Drag & Drop

Not included in this redesign. Can be added later.

## Affected Files

### New File

- **`FlatRow.java`** in `controls/v2/xmleditor/view/`
  - Data class for a flat row (type, depth, modelNode, parentRow, label, value, expanded, visible, childCount)
  - Static flattening method: `flatten(XmlDocument doc)` -> `List<FlatRow>`
  - Visibility toggling logic

### Major Changes

- **`XmlCanvasView.java`** — Complete rendering overhaul:
  - `renderVisible()` iterates over visible `FlatRow`s instead of nested `NestedGridNode`s
  - New methods: `drawRow()`, `drawExpandBar()`, `drawTreeLines()`, `drawRowIcon()`
  - Replaced methods: `renderGrid()`, `renderGridRecursively()`, `drawGridHeader()`, `drawAttributeRow()`, `drawTextContentRow()`, `drawElementIcon()`
  - Hit-testing and inline editing rewritten for row-based layout
  - Scroll logic simplified (primarily vertical)
  - Table rendering (`renderTable()`, `drawTableHeader()`, etc.) integrated into row flow

### Modifications

- **`XmlCanvasRenderingHelper.java`** — New constants:
  - `EXPAND_BAR_WIDTH` (12px)
  - `TREE_LINE_COLOR` (`#d0d0d0`)
  - `ROW_NUMBER_COLUMN_WIDTH`
  - `ZEBRA_COLOR_EVEN`, `ZEBRA_COLOR_ODD`

- **`RepeatingElementsTable.java`** — Additions:
  - Icons in column headers (`=` for attribute columns, `<>` for element columns)
  - Row number column

### Removed After Completion

- **`NestedGridNode.java`** — Replaced by `FlatRow`. Can remain during transition, then remove.

### Unchanged

- `XmlEditorContext.java` — Coordination layer
- `CommandManager` and all Commands — Model modification
- `SelectionModel` — Selection tracking
- `XmlGridContextMenu` — Context menu
- `ToastNotification` — Notifications
- `TypeAwareWidgetFactory` — Type-aware editing widgets
- Entire `model/` package — XML data model
- `XmlUnifiedTab.java` — Integration with unified editor

## Performance Considerations

- **Virtual scrolling preserved:** Only visible rows are rendered (same approach as current viewport culling)
- **Flat list is faster:** No recursive tree traversal during rendering — just iterate `visibleRows[startIndex..endIndex]`
- **Lazy flattening:** Only recompute flat list on expand/collapse or model changes, not on every render
- **Row count limit:** `MAX_INITIAL_CHILDREN = 50` still applies — "Load more..." row for large documents
