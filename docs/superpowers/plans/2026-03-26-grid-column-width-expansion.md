# Grid Column Width Expansion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a complex table cell is expanded, the column width should grow to fit the expanded content so that labels and values are fully visible.

**Architecture:** Recalculate column widths after cell expand/collapse, considering the widths needed by expanded sub-rows. Increase `MAX_COLUMN_WIDTH` for expanded columns. Fix sub-row rendering to use the full column width instead of splitting it in half.

**Tech Stack:** Java 25, JavaFX 24.0.1, Canvas (GraphicsContext)

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java` | Modify | Recalculate column widths considering expanded cell content |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java` | Modify | Fix sub-row rendering to use full column width, trigger width recalculation |

---

### Task 1: Recalculate column widths considering expanded content

**Files:**
- Modify: `RepeatingElementsTable.java`
- Modify: `XmlCanvasView.java`

- [ ] **Step 1: Update `calculateColumnWidths()` in RepeatingElementsTable to account for expanded cells**

Add logic: for each column, if any row has that column expanded, calculate the max width needed by the sub-rows (label + value + indent + icon) and use that as the column width. Raise `MAX_COLUMN_WIDTH` to 500 for columns with expanded cells.

- [ ] **Step 2: Fix sub-row rendering in XmlCanvasView `renderInlineTable()`**

Change the sub-row rendering to use a fixed split point (e.g. 40% label, 60% value) of the column width instead of `colWidth / 2`, and remove the overly aggressive truncation.

- [ ] **Step 3: Trigger column width recalculation after cell expand/collapse**

In `handleTableClick()`, after `row.toggleColumnExpanded()`, call `table.recalculateColumnWidths()` before `recalculateVisibleRows()`.

- [ ] **Step 4: Verify build and tests**

- [ ] **Step 5: Commit and push**
