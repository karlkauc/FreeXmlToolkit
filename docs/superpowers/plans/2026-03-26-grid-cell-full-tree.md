# Full Tree Behavior in Grid Cell Expansion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expanded complex cells in the RepeatingElementsTable should behave exactly like the normal tree view — with recursive expand/collapse, expand bars, tree lines, nested grids for repeating sub-elements, and full inline editing.

**Architecture:** Replace the simplified sub-row rendering in `renderInlineTable()` with calls to the same `drawRow()`, `drawExpandBars()`, `drawTreeLines()` methods used by the main view, but offset to render within cell bounds. Use `FlatRow.toggleExpand()` on the cell's sub-row list for expand/collapse. Attach `RepeatingElementsTable` instances to sub-rows that have repeating children.

**Tech Stack:** Java 25, JavaFX 24.0.1, Canvas (GraphicsContext)

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java` | Modify | Fix `flattenElement()` to set parentRow references for proper tree structure |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java` | Modify | Update `calculateRowHeight()` to account for visible sub-rows only; add method to attach repeating tables to cell rows |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java` | Modify | Replace simplified cell rendering with full tree rendering; add cell-level mouse handling for expand/collapse |

---

### Task 1: Fix FlatRow.flattenElement() for proper tree structure

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java`

The current `flattenElement(XmlElement)` creates rows with `parentRow = null`. For tree behavior (expand bars, tree lines, ancestor-based visibility), sub-rows need proper parentRow references. Also, child elements should start **collapsed** (not expanded) so the user can expand them on demand.

- [ ] **Step 1: Rewrite `flattenElement(XmlElement)`**

```java
public static List<FlatRow> flattenElement(XmlElement element) {
    List<FlatRow> rows = new ArrayList<>();
    // Create a virtual root row for the element itself (not rendered, just for parentRow references)
    FlatRow rootRow = new FlatRow(RowType.ELEMENT, -1, element, null, element.getName(), null, 0);

    // Add attributes of the element at depth 0
    int attrIdx = 0;
    for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
        FlatRow attrRow = new FlatRow(RowType.ATTRIBUTE, 0, element, rootRow,
                attr.getKey(), attr.getValue(), 0);
        attrRow.setAttributeIndex(attrIdx++);
        rows.add(attrRow);
    }

    // Flatten children using the same private method as the main flatten
    // This gives proper parentRow, depth, childCount, and repeating element detection
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement childEl) {
            flattenElement(childEl, 0, rootRow, rows, false); // collapsed by default
        } else if (child instanceof XmlText text) {
            if (text.getText() != null && !text.getText().isBlank()) {
                rows.add(new FlatRow(RowType.TEXT, 0, text, rootRow,
                        "#text", text.getText().strip(), 0));
            }
        } else if (child instanceof XmlComment comment) {
            rows.add(new FlatRow(RowType.COMMENT, 0, comment, rootRow,
                    "<!-- -->", comment.getText(), 0));
        } else if (child instanceof XmlCData cdata) {
            rows.add(new FlatRow(RowType.CDATA, 0, cdata, rootRow,
                    "<![CDATA[]]>", cdata.getText(), 0));
        }
    }
    return rows;
}
```

Note: The private `flattenElement(element, depth, parentRow, rows, expandByDefault)` already handles proper childCount, parentRow, and nested elements. By passing `expandByDefault=false`, sub-elements start collapsed and can be expanded by the user.

- [ ] **Step 2: Verify build**

Run: `./gradlew compileJava`

- [ ] **Step 3: Run tests**

Run: `./gradlew test`

- [ ] **Step 4: Commit**

```bash
git commit -m "fix: set proper parentRow references in FlatRow.flattenElement()"
```

---

### Task 2: Add repeating table detection and visible-only height calculation for cell rows

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java`

- [ ] **Step 1: Add method to attach repeating tables to cell sub-rows**

```java
// In TableRow class:
public void attachRepeatingTablesToExpandedRows(String columnName, int baseDepth) {
    List<FlatRow> cellRows = expandedCellRows.get(columnName);
    if (cellRows == null) return;

    for (FlatRow row : cellRows) {
        if (row.getType() != FlatRow.RowType.ELEMENT) continue;
        if (row.getParentRow() == null) continue;

        XmlNode model = row.getModelNode();
        if (!(model instanceof XmlElement elem)) continue;

        // Check for repeating children
        java.util.Map<String, java.util.List<XmlElement>> byName = new java.util.LinkedHashMap<>();
        for (XmlNode child : elem.getChildren()) {
            if (child instanceof XmlElement ce) {
                byName.computeIfAbsent(ce.getName(), k -> new java.util.ArrayList<>()).add(ce);
            }
        }
        for (var entry : byName.entrySet()) {
            if (entry.getValue().size() >= 2) {
                RepeatingElementsTable subTable = new RepeatingElementsTable(
                        entry.getKey(), entry.getValue(), baseDepth + row.getDepth(), () -> {});
                row.setRepeatingTable(subTable);
                break; // Only first repeating group for now
            }
        }
    }
}
```

- [ ] **Step 2: Update toggleColumnExpanded() to attach repeating tables**

After creating the cell rows, call `attachRepeatingTablesToExpandedRows()`.

- [ ] **Step 3: Update calculateRowHeight() to count only VISIBLE sub-rows**

```java
public double calculateRowHeight(TableRow row) {
    double maxCellHeight = ROW_HEIGHT;
    for (String colName : row.getExpandedColumns()) {
        List<FlatRow> cellRows = row.getExpandedCellRows(colName);
        // Count only visible rows
        long visibleCount = cellRows.stream().filter(FlatRow::isVisible).count();
        double cellHeight = ROW_HEIGHT + visibleCount * ROW_HEIGHT;
        // Add height for expanded repeating tables within cell
        for (FlatRow cr : cellRows) {
            if (cr.isVisible() && cr.hasRepeatingTable() && cr.isExpanded()) {
                cellHeight += cr.getRepeatingTable().getHeight();
            }
        }
        maxCellHeight = Math.max(maxCellHeight, cellHeight);
    }
    return maxCellHeight;
}
```

- [ ] **Step 4: Verify build and tests**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add repeating table detection and visible-only height for cell sub-rows"
```

---

### Task 3: Replace simplified cell rendering with full tree rendering

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Create `renderCellTree()` method**

Replace the current simplified sub-row rendering block (lines 1122-1167) with a call to a new method that renders a full mini-tree within the cell bounds:

```java
private void renderCellTree(List<FlatRow> cellRows, double cellX, double cellY, double cellWidth) {
    // Filter to visible rows only
    List<FlatRow> visibleCellRows = cellRows.stream()
            .filter(FlatRow::isVisible)
            .collect(java.util.stream.Collectors.toList());

    if (visibleCellRows.isEmpty()) return;

    double currentY = cellY;
    double cellPadding = RepeatingElementsTable.CELL_PADDING;

    // Calculate name column width for this cell's content
    double cellNameColWidth = 0;
    for (FlatRow row : visibleCellRows) {
        double indent = row.getDepth() * INDENT + ICON_AREA_WIDTH;
        double labelW = (row.getLabel() != null ? row.getLabel().length() * 7.2 : 0);
        cellNameColWidth = Math.max(cellNameColWidth, indent + labelW + 20);
    }
    cellNameColWidth = Math.min(cellNameColWidth, cellWidth * 0.5);

    // Draw tree lines within cell
    drawCellTreeLines(visibleCellRows, cellX + cellPadding, currentY, cellWidth);

    // Draw each visible sub-row
    for (int i = 0; i < visibleCellRows.size(); i++) {
        FlatRow row = visibleCellRows.get(i);
        double rowY = currentY + i * ROW_HEIGHT;
        double rowCenterY = rowY + ROW_HEIGHT / 2;

        double contentX = cellX + cellPadding + row.getDepth() * INDENT;

        // Draw expand bar for expandable elements
        if (row.isExpandable()) {
            drawCellExpandIndicator(row, contentX, rowY, cellRows);
        }

        // Draw icon
        double iconX = contentX + (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);
        drawRowIcon(row.getType(), iconX, rowCenterY);

        // Draw label
        double labelX = iconX + ICON_AREA_WIDTH;
        gc.setFont(ROW_FONT);
        gc.setFill(getRowLabelColor(row.getType()));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        if (row.getLabel() != null) {
            gc.fillText(truncateText(row.getLabel(), cellNameColWidth - row.getDepth() * INDENT - ICON_AREA_WIDTH),
                    labelX, rowCenterY);
        }

        // Draw child count
        if (row.isExpandable()) {
            gc.setFont(SMALL_FONT);
            gc.setFill(TEXT_SECONDARY);
            double afterLabel = labelX + (row.getLabel() != null ? row.getLabel().length() * 7.2 : 0) + 4;
            gc.fillText("(" + row.getChildCount() + ")", afterLabel, rowCenterY);
        }

        // Draw value
        double valueX = cellX + cellPadding + cellNameColWidth;
        if (row.getValue() != null) {
            gc.setFont(ROW_FONT);
            gc.setFill(getRowValueColor(row.getType()));
            double availValue = cellWidth - cellPadding * 2 - cellNameColWidth;
            gc.fillText(truncateText(row.getValue(), availValue), valueX, rowCenterY);
        }

        // Row separator
        gc.setStroke(ROW_SEPARATOR);
        gc.setLineWidth(0.3);
        gc.strokeLine(cellX + cellPadding, rowY + ROW_HEIGHT, cellX + cellWidth - cellPadding, rowY + ROW_HEIGHT);
    }
}
```

- [ ] **Step 2: Implement `drawCellExpandIndicator()`**

```java
private void drawCellExpandIndicator(FlatRow row, double x, double y, List<FlatRow> cellRows) {
    double arrowX = x + EXPAND_BAR_WIDTH / 2;
    double arrowY = y + ROW_HEIGHT / 2;
    double arrowSize = 3;
    gc.setFill(EXPAND_BAR_ARROW);

    if (row.isExpanded()) {
        // Down triangle
        gc.fillPolygon(
                new double[]{arrowX - arrowSize, arrowX, arrowX + arrowSize},
                new double[]{arrowY - arrowSize / 2, arrowY + arrowSize, arrowY - arrowSize / 2},
                3);
    } else {
        // Right triangle
        gc.fillPolygon(
                new double[]{arrowX - arrowSize / 2, arrowX + arrowSize, arrowX - arrowSize / 2},
                new double[]{arrowY - arrowSize, arrowY, arrowY + arrowSize},
                3);
    }
}
```

- [ ] **Step 3: Implement `drawCellTreeLines()`**

```java
private void drawCellTreeLines(List<FlatRow> visibleCellRows, double baseX, double baseY, double cellWidth) {
    gc.setStroke(TREE_LINE_COLOR);
    gc.setLineWidth(0.5);

    for (int i = 0; i < visibleCellRows.size(); i++) {
        FlatRow row = visibleCellRows.get(i);
        FlatRow parent = row.getParentRow();
        if (parent == null || parent.getDepth() < 0) continue; // Skip virtual root

        double rowY = baseY + i * ROW_HEIGHT + ROW_HEIGHT / 2;
        double parentBarX = baseX + parent.getDepth() * INDENT + EXPAND_BAR_WIDTH / 2;
        double iconX = baseX + row.getDepth() * INDENT + (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);

        // Horizontal branch
        gc.strokeLine(parentBarX, rowY, iconX, rowY);
    }
}
```

- [ ] **Step 4: Update the cell rendering call in `renderInlineTable()`**

Replace the existing sub-row rendering block with:

```java
if (row.isColumnExpanded(colName)) {
    List<FlatRow> cellRows = row.getExpandedCellRows(colName);
    renderCellTree(cellRows, cellX, rowTop + RepeatingElementsTable.ROW_HEIGHT, colWidth);
}
```

- [ ] **Step 5: Verify build and tests**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: render full tree with expand bars and tree lines in expanded grid cells"
```

---

### Task 4: Handle mouse interaction for cell sub-rows

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Update `handleTableClick()` to handle sub-row expand/collapse**

In the data row click section, after detecting a click on a complex cell's expand arrow, also check if the click is within the expanded content area and hitting a sub-row's expand indicator:

```java
// After the existing expand arrow check for the cell itself:
// Check if click is within an expanded cell's sub-row expand indicator
if (row.isColumnExpanded(col.getName())) {
    List<FlatRow> cellRows = row.getExpandedCellRows(col.getName());
    double cellTop = /* calculate Y of this cell's expanded content */;
    double cellLeft = table.getColumnX(col.getName());
    double colWidth = col.getWidth();

    // Find which sub-row was clicked
    List<FlatRow> visibleCellRows = cellRows.stream()
            .filter(FlatRow::isVisible).collect(Collectors.toList());
    double subRowTop = cellTop + RepeatingElementsTable.ROW_HEIGHT; // after summary
    int subRowIdx = (int) ((my - subRowTop) / RepeatingElementsTable.ROW_HEIGHT);

    if (subRowIdx >= 0 && subRowIdx < visibleCellRows.size()) {
        FlatRow subRow = visibleCellRows.get(subRowIdx);

        // Check if click is on sub-row's expand indicator
        double subContentX = cellLeft + RepeatingElementsTable.CELL_PADDING + subRow.getDepth() * INDENT;
        if (subRow.isExpandable() && mx >= subContentX && mx <= subContentX + EXPAND_BAR_WIDTH) {
            FlatRow.toggleExpand(subRow, cellRows);
            table.recalculateColumnWidths();
            recalculateVisibleRows();
            updateScrollBars();
            render();
            return;
        }

        // Double-click on sub-row value: start editing
        if (event.getClickCount() == 2 && subRow.getValue() != null) {
            // Position edit field within cell at sub-row position
            double editY = subRowTop + subRowIdx * RepeatingElementsTable.ROW_HEIGHT;
            double editX = cellLeft + RepeatingElementsTable.CELL_PADDING + /* nameColWidth */;
            // ... create edit field
        }
    }
}
```

- [ ] **Step 2: Verify build and tests**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: handle expand/collapse clicks on sub-rows within grid cells"
```

---

### Task 5: Verify and commit

- [ ] **Step 1: Full build and test**

Run: `./gradlew clean test`
Expected: All tests pass

- [ ] **Step 2: Final commit and push**

```bash
git push
```
