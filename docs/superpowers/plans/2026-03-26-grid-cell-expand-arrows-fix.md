# Grid Cell Expand Arrows Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix expand/collapse arrows in RepeatingElementsTable grid cells — they are invisible because they use Unicode characters that don't render in Monospaced font on Canvas. Replace with Canvas-drawn polygon triangles.

**Architecture:** Replace `gc.fillText(arrow, ...)` calls for cell expand indicators with `gc.fillPolygon()` calls that draw triangles directly, matching the approach used for tree expand bars.

**Tech Stack:** Java 25, JavaFX 24.0.1, Canvas (GraphicsContext)

**Spec:** `docs/superpowers/specs/2026-03-26-grid-cell-expansion-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java` | Modify | Replace Unicode arrows with Canvas-drawn polygons in `renderInlineTable()` |

---

### Task 1: Replace Unicode arrows with Canvas-drawn polygons in grid cells

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Replace the expand arrow rendering in `renderInlineTable()`**

In `renderInlineTable()` (around line 1080-1088), replace the Unicode-based arrow drawing:

```java
// CURRENT (broken — Unicode chars don't render in Monospaced on Canvas):
if (isComplex) {
    gc.setFont(SMALL_FONT);
    gc.setFill(TEXT_SECONDARY);
    gc.setTextAlign(TextAlignment.LEFT);
    gc.setTextBaseline(VPos.CENTER);
    String arrow = row.isColumnExpanded(colName) ? "\u25BC" : "\u25B6";
    gc.fillText(arrow, cellX + 2, cellCenterY);
}
```

With Canvas-drawn polygon triangles (same approach as tree expand bars at line 768-778):

```java
if (isComplex) {
    double arrowX = cellX + 8;
    double arrowY = cellCenterY;
    double arrowSize = 3;
    gc.setFill(TEXT_SECONDARY);

    if (row.isColumnExpanded(colName)) {
        // Down-pointing triangle ▼ (expanded)
        gc.fillPolygon(
                new double[]{arrowX - arrowSize, arrowX, arrowX + arrowSize},
                new double[]{arrowY - arrowSize / 2, arrowY + arrowSize, arrowY - arrowSize / 2},
                3
        );
    } else {
        // Right-pointing triangle ► (collapsed)
        gc.fillPolygon(
                new double[]{arrowX - arrowSize / 2, arrowX + arrowSize, arrowX - arrowSize / 2},
                new double[]{arrowY - arrowSize, arrowY, arrowY + arrowSize},
                3
        );
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "fix: replace Unicode arrows with Canvas-drawn polygons in grid cells"
```
