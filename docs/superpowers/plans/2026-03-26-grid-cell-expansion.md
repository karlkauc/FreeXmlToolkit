# Grid Cell Expansion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable inline expand/collapse for complex table cells in the RepeatingElementsTable grid view, so users can see and edit child nodes directly within the table.

**Architecture:** Add cell-level expand state to `TableRow`, use `FlatRow.flattenElement()` to produce child rows on demand, render them inline within the cell with dynamic row heights, and handle click/edit interactions on the expanded content.

**Tech Stack:** Java 25, JavaFX 24.0.1, Canvas (GraphicsContext), JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-26-grid-cell-expansion-design.md`

**Status:** Already implemented (commit `19b7eb4a`). This plan documents what was built.

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java` | Modify | Add `flattenElement(XmlElement)` convenience method |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java` | Modify | Add cell expand state to TableRow, variable row heights |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java` | Modify | Render expanded cells, handle expand clicks, variable positioning |

---

### Task 1: Add FlatRow.flattenElement() convenience method

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java`

- [x] **Step 1: Add public static flattenElement(XmlElement) method**

```java
public static List<FlatRow> flattenElement(XmlElement element) {
    List<FlatRow> rows = new ArrayList<>();
    // Add attributes of the element itself at depth 0
    int attrIdx = 0;
    for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
        FlatRow attrRow = new FlatRow(RowType.ATTRIBUTE, 0, element, null,
                attr.getKey(), attr.getValue(), 0);
        attrRow.setAttributeIndex(attrIdx++);
        rows.add(attrRow);
    }
    // Flatten children recursively
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement childEl) {
            flattenElement(childEl, 0, null, rows, true);
        } else if (child instanceof XmlText text) {
            if (text.getText() != null && !text.getText().isBlank()) {
                rows.add(new FlatRow(RowType.TEXT, 0, text, null,
                        "#text", text.getText().strip(), 0));
            }
        } else if (child instanceof XmlComment comment) {
            rows.add(new FlatRow(RowType.COMMENT, 0, comment, null,
                    null, comment.getText(), 0));
        } else if (child instanceof XmlCData cdata) {
            rows.add(new FlatRow(RowType.CDATA, 0, cdata, null,
                    null, cdata.getText(), 0));
        }
    }
    return rows;
}
```

- [x] **Step 2: Verify build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java
git commit -m "feat: add FlatRow.flattenElement() for cell expansion"
```

---

### Task 2: Add cell expansion state to RepeatingElementsTable.TableRow

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java`

- [x] **Step 1: Add expansion fields and methods to TableRow**

```java
// In TableRow inner class:
private final Set<String> expandedColumns = new HashSet<>();
private final Map<String, List<FlatRow>> expandedCellRows = new LinkedHashMap<>();

public boolean isColumnExpanded(String columnName) {
    return expandedColumns.contains(columnName);
}

public void toggleColumnExpanded(String columnName) {
    if (expandedColumns.contains(columnName)) {
        expandedColumns.remove(columnName);
        expandedCellRows.remove(columnName);
    } else {
        expandedColumns.add(columnName);
        XmlElement child = complexChildren.get(columnName);
        if (child != null) {
            expandedCellRows.put(columnName, FlatRow.flattenElement(child));
        }
    }
}

public List<FlatRow> getExpandedCellRows(String columnName) {
    return expandedCellRows.getOrDefault(columnName, List.of());
}

public Set<String> getExpandedColumns() {
    return expandedColumns;
}
```

- [x] **Step 2: Update calculateRowHeight() to return variable height**

```java
public double calculateRowHeight(TableRow row) {
    double maxCellHeight = ROW_HEIGHT;
    for (String colName : row.getExpandedColumns()) {
        List<FlatRow> cellRows = row.getExpandedCellRows(colName);
        double cellHeight = ROW_HEIGHT + cellRows.size() * ROW_HEIGHT;
        maxCellHeight = Math.max(maxCellHeight, cellHeight);
    }
    return maxCellHeight;
}
```

- [x] **Step 3: Update getHeight() to use variable row heights**

The `calculateHeight()` method iterates rows and calls `calculateRowHeight(row)` for each, accumulating the total.

- [x] **Step 4: Update getRowIndexAtY() and getRowY() for cumulative heights**

Both methods iterate with `calculateRowHeight()` instead of dividing by fixed `ROW_HEIGHT`.

- [x] **Step 5: Verify build and tests**

Run: `./gradlew compileJava && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [x] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java
git commit -m "feat: add cell expansion state and variable row heights to RepeatingElementsTable"
```

---

### Task 3: Render expanded cells and handle interaction in XmlCanvasView

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [x] **Step 1: Update renderInlineTable() for cumulative row positioning**

Replace `dataY + r * ROW_HEIGHT` with cumulative `currentRowY` variable that uses `table.calculateRowHeight(row)`.

- [x] **Step 2: Draw expand arrows for complex cells**

Before drawing cell value, check `row.hasComplexChild(col.getName())`. Draw ▼ if expanded, ► if collapsed, using `gc.fillText()`.

- [x] **Step 3: Render expanded cell sub-rows**

After drawing cell summary, if `row.isColumnExpanded(colName)`:
- Get `row.getExpandedCellRows(colName)`
- Draw each FlatRow with: icon (`drawRowIcon()`), label (colored by type), value
- Position below summary line at `subRowY` increments of `ROW_HEIGHT`
- Apply depth-based indentation

- [x] **Step 4: Handle click on cell expand arrow**

In `handleTableClick()`, for single-clicks on data rows:
- Check if click is within arrow zone (first 16px of complex cell)
- If so, call `row.toggleColumnExpanded(col.getName())`
- Trigger `recalculateVisibleRows()`, `updateScrollBars()`, `render()`

- [x] **Step 5: Update getTableRowIndexAtScreenY() for variable heights**

Iterate with cumulative `calculateRowHeight()` instead of fixed division.

- [x] **Step 6: Update startEditingTableCell() for variable positioning**

Calculate cell Y position with cumulative row heights for rows before the target.

- [x] **Step 7: Verify build and tests**

Run: `./gradlew compileJava && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [x] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "feat: render expanded cells and handle expand interaction in grid view"
```
