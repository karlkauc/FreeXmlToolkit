# JSON Grid View — a shared grid canvas for XML and JSON

**Date:** 2026-09-08
**Status:** Approved

## Summary

The Unified Shell's Graphic view shows an XMLSpy-style grid for XML documents
(`XmlGridView` → `XmlCanvasView`): expand/collapse, inline editing, a context menu,
repeating siblings rendered as embedded tables, undo/redo, Ctrl+F search and a round-trip
into the text editor. JSON documents (`.json`, `.jsonc`, `.json5`) only had Text and a
read-only Tree; Graphic was disabled by `EditorTab.supportsView`.

This spec adds the same grid for JSON with full feature parity, by generalising the
existing XML canvas into a model-agnostic base instead of copying it. XML and JSON share
one rendering/interaction implementation; each supplies only flattening, commands and
its context menu. Recognisability is guaranteed by construction.

## Mapping JSON onto the grid

| XML grid today | JSON |
|---|---|
| Element with children (expandable) | Object / array |
| Leaf element with text (`name = "value"`) | Property with a scalar (`key = "value"`) |
| Repeating same-name siblings → embedded table | Array whose items are all objects → embedded table (columns = merged key order) |
| Attributes | — |
| Mixed content / comments / PIs | — (JSONC comments are not part of the model) |
| XSD-driven type widgets | Type of the value itself (string / number / boolean / null) |

## Architecture

### Shared base (`controls/v2/xmleditor/view/`)

- **`GridCanvasView<N>`** — the former `XmlCanvasView` body: Canvas rendering, scrollbars,
  hit-testing, expand bars, tree lines, embedded-table rendering/click/hover/cell edit,
  keyboard navigation, inline edit chrome, toast, flash highlight, `XmlSearchTarget`
  search. Everything model-specific goes through a `GridModelAdapter<N>`.
- **`GridModelAdapter<N>`** — `hasDocument`, `flatten`, `attachTables`, `addModelListener`,
  `installViewHooks`, `nodeOf(row)`, `nodeOf(tableRow)`, `select`, `decorateLeafValue`,
  `canEditName`, `canEditValue`, `editSpec`, `cellEditSpec`, `commitRowEdit`,
  `commitCellEdit`, `sortTable`, `serialize`, `createContextMenu`, `emptyStateText`.
  `commitRowEdit`/`commitCellEdit` return `false` to keep the inline editor open when a
  value is rejected (the adapter tells the user via toast).
- **`GridContextMenu<N>`** — `show(...)`, `show(..., table, rowIndex, column)`, `hide`,
  `handleKeyPress`, `hasClipboard`.
- **`FlatRow`** — `RowType` keeps the XML constants and gains a `Category`
  (`CONTAINER`, `LEAF`, `ATTRIBUTE`, `NOTE`) plus `JSON_OBJECT`, `JSON_ARRAY`,
  `JSON_STRING`, `JSON_NUMBER`, `JSON_BOOLEAN`, `JSON_NULL`. `modelNode` is an `Object`.
  `isExpandable()` = container with children; `isLeafWithValue()` covers ELEMENT and the
  JSON scalar types.
- **`GridRecord`** — model-agnostic data source of one embedded-table row (column keys,
  cell values, complex children, attribute suffixes, `flattenComplexChild`).
  `XmlGridRecord` wraps one `XmlElement`; `RepeatingElementsTable` is built from records
  (`ofRecords(...)`), the `List<XmlElement>` constructor is kept for the XML grid and tests.
- **`GridColumnSort`** — the pure numeric/date/string comparator extracted from
  `SortElementsCommand`, reused by the JSON `SortArrayCommand`.
- **`XmlGridAdapter`** — the XML coupling points moved out of the canvas verbatim
  (flatten via `FlatRow.flatten`, same-name sibling tables, mixed-content warning,
  schema-driven widgets + documentation tooltips, XML command mapping, `SortElementsCommand`).
- **`XmlCanvasView extends GridCanvasView<XmlNode>`** — a thin subclass keeping the name.
- **`GridViewShell`** (`controls/shell/editor/`) — the shared header ("Grid view",
  Collapse all) and placeholder; `XmlGridView` and `JsonGridView` extend it.

### JSON side (`controls/jsoneditor/`)

- **`JsonEditorContext`** — document + `JsonCommandManager` (generic
  `AbstractCommandManager<JsonCommand>`) + `NodeSelectionModel<JsonNode>`; events
  `document`, `dirty`, `canUndo`, `canRedo`, `modelChanged`, `lossyFormatDetected`.
  `serializeToString()` uses the JSON indent setting and keeps a trailing newline.
- **Commands** — `SetPrimitiveValueCommand`, `RenameKeyCommand`, `AddPropertyCommand`,
  `AddArrayItemCommand`, `DeleteNodeCommand`, `MoveNodeCommand`, `DuplicateNodeCommand`,
  `ReplaceNodeCommand`, `ChangeValueTypeCommand`, `SortArrayCommand`.
- **`JsonGridAdapter` / `JsonGridRecord`** (`controls/jsoneditor/grid/`) — flatten rules:

  | Node | Row |
  |---|---|
  | Root object/array | `JSON_OBJECT`/`JSON_ARRAY`, depth 0, label `$`, expanded |
  | Property with scalar | `JSON_STRING/NUMBER/BOOLEAN/NULL`, label = key, value = raw text |
  | Property with container | `JSON_OBJECT/JSON_ARRAY`, label = key, collapsed, childCount = size |
  | Array item | as above with label `[i]` |
  | Array with ≥ 2 items, all objects | one `JSON_ARRAY` row + embedded table |
  | Empty object/array | container row with childCount 0 |

- **`JsonGridContextMenu`** — same structure and accelerators as the XML menu: Add
  (Property / Array Item / Sibling Before / After), Rename Key, Duplicate, Copy/Cut/Paste,
  Copy Cell Content, Copy JSONPath, Copy Node (JSON), Change Type, Move Up/Down,
  Expand/Collapse All, Sort Column, Delete.

### Rendering

Strings are drawn quoted (`= "Alice"`) like XML leaf values; numbers, booleans and `null`
raw, each in a type colour. Icons: `{}` object, `[]` array, `"` string, `#` number,
`T`/`F` boolean, `∅` null. Keys use the element colour; `[i]` labels the child-count colour.

### Inline editing

| Type | Widget | Commit |
|---|---|---|
| STRING | text field | `SetPrimitiveValueCommand` |
| NUMBER | text field | parsed (`BigInteger`/`BigDecimal`); invalid → toast, editor stays open |
| BOOLEAN | `BooleanToggle` | `SetPrimitiveValueCommand` |
| NULL | text field | `null` stays null, other text becomes a string |
| Key | text field | `RenameKeyCommand`; duplicate → toast |
| Table cell without the property | text field | `AddPropertyCommand` |

### Shell integration

`EditorTab` gets a `JsonEditorContext` shared across Text/Tree/Graphic (`lastParsedJsonText`
guard, `roundTripJsonModelToText` with a minimal replace), `supportsView(GRAPHIC)` includes
JSON, `getActiveSearchTarget()` returns the JSON canvas, `undoActive()` prefers the JSON /
XML model stack in structured views, the Inspector edits JSON keys and values, and the JSON
Tree view listens to the shared context instead of re-parsing.

### JSONC / JSON5

Comments and JSON5 syntax are not part of the model: the first grid edit rewrites the file
as standard JSON. The grid stays editable and shows a single warning toast per session when
constructs would actually be lost.
