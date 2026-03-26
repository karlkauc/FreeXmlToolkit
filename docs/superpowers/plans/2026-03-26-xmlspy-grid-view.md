# XMLSpy-Style Grid View — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the graphical XML view from nested grids to a flat, row-based layout inspired by XMLSpy's Grid View — with distinctive node icons, vertical expand/collapse bars, tree connection lines, and automatic grid tables for repeating elements.

**Architecture:** Replace `NestedGridNode`-based nested grid rendering in `XmlCanvasView` with a flat `FlatRow` list. Each XML item (element, attribute, text) becomes one row. Rendering iterates visible rows sequentially. The expand bar and tree lines are drawn as overlays based on depth/parent relationships. `RepeatingElementsTable` is enhanced with column-header icons and row numbers.

**Tech Stack:** Java 25, JavaFX 24.0.1, Canvas (GraphicsContext), JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-26-xmlspy-grid-view-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java` | Create | Data class for a flat row + flattening algorithm |
| `src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java` | Create | Unit tests for FlatRow and flattening logic |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java` | Major rewrite | Row-based rendering, expand bars, tree lines, hit-testing |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/common/utilities/XmlCanvasRenderingHelper.java` | Modify | New constants for expand bar, tree lines, zebra colors |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java` | Modify | Add row numbers column, icons in column headers |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/NestedGridNode.java` | Remove (after completion) | Replaced by FlatRow |

---

### Task 1: Create FlatRow Data Class

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java`
- Create: `src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java`

- [ ] **Step 1: Write tests for FlatRow basic properties**

```java
package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlatRowTest {

    @Test
    @DisplayName("Element row stores type, depth, label correctly")
    void testElementRow() {
        var element = new XmlElement("Company");
        var row = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "Company", null, 3);
        assertEquals(FlatRow.RowType.ELEMENT, row.getType());
        assertEquals(0, row.getDepth());
        assertEquals("Company", row.getLabel());
        assertNull(row.getValue());
        assertEquals(3, row.getChildCount());
        assertTrue(row.isVisible());
        assertFalse(row.isExpanded());
    }

    @Test
    @DisplayName("Attribute row stores name and value")
    void testAttributeRow() {
        var element = new XmlElement("Company");
        element.setAttribute("xmlns", "http://example.com");
        var parentRow = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "Company", null, 0);
        var attrRow = new FlatRow(FlatRow.RowType.ATTRIBUTE, 1, element, parentRow, "xmlns", "http://example.com", 0);
        assertEquals(FlatRow.RowType.ATTRIBUTE, attrRow.getType());
        assertEquals(1, attrRow.getDepth());
        assertEquals("xmlns", attrRow.getLabel());
        assertEquals("http://example.com", attrRow.getValue());
        assertSame(parentRow, attrRow.getParentRow());
    }

    @Test
    @DisplayName("Expand/collapse toggles state")
    void testExpandCollapse() {
        var element = new XmlElement("Root");
        var row = new FlatRow(FlatRow.RowType.ELEMENT, 0, element, null, "Root", null, 2);
        assertFalse(row.isExpanded());
        row.setExpanded(true);
        assertTrue(row.isExpanded());
        row.setExpanded(false);
        assertFalse(row.isExpanded());
    }

    @Test
    @DisplayName("Visibility can be toggled")
    void testVisibility() {
        var element = new XmlElement("Child");
        var row = new FlatRow(FlatRow.RowType.ELEMENT, 1, element, null, "Child", null, 0);
        assertTrue(row.isVisible());
        row.setVisible(false);
        assertFalse(row.isVisible());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRowTest" -x javaCompileGeneratedClasses`
Expected: Compilation error — `FlatRow` does not exist

- [ ] **Step 3: Implement FlatRow data class**

```java
package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Represents a single flat row in the XMLSpy-style grid view.
 *
 * <p>Each visible item in the XML tree (element, attribute, text, comment, etc.)
 * is represented as one FlatRow. The view iterates over visible rows sequentially.</p>
 */
public class FlatRow {

    public enum RowType {
        ELEMENT, ATTRIBUTE, TEXT, COMMENT, CDATA, PROCESSING_INSTRUCTION, DOCUMENT
    }

    private final RowType type;
    private final int depth;
    private final XmlNode modelNode;
    private final FlatRow parentRow;
    private final String label;
    private String value;
    private final int childCount;

    private boolean expanded = false;
    private boolean visible = true;
    private boolean selected = false;
    private boolean hovered = false;

    // For elements: reference to the RepeatingElementsTable if children form a table
    private RepeatingElementsTable repeatingTable;

    // For attributes: the attribute index in the parent element
    private int attributeIndex = -1;

    public FlatRow(RowType type, int depth, XmlNode modelNode, FlatRow parentRow,
                   String label, String value, int childCount) {
        this.type = type;
        this.depth = depth;
        this.modelNode = modelNode;
        this.parentRow = parentRow;
        this.label = label;
        this.value = value;
        this.childCount = childCount;
    }

    public RowType getType() { return type; }
    public int getDepth() { return depth; }
    public XmlNode getModelNode() { return modelNode; }
    public FlatRow getParentRow() { return parentRow; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public int getChildCount() { return childCount; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    public RepeatingElementsTable getRepeatingTable() { return repeatingTable; }
    public void setRepeatingTable(RepeatingElementsTable table) { this.repeatingTable = table; }

    public int getAttributeIndex() { return attributeIndex; }
    public void setAttributeIndex(int index) { this.attributeIndex = index; }

    /** True if this is an element with children (can be expanded/collapsed). */
    public boolean isExpandable() {
        return type == RowType.ELEMENT && childCount > 0;
    }

    /** True if this is a leaf element with text value (displayed as "name = value"). */
    public boolean isLeafWithValue() {
        return type == RowType.ELEMENT && childCount == 0 && value != null;
    }

    /** True if this element has a repeating table instead of individual child rows. */
    public boolean hasRepeatingTable() {
        return repeatingTable != null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRowTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java
git commit -m "feat: add FlatRow data class for XMLSpy-style grid view"
```

---

### Task 2: Implement Flattening Algorithm

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java`
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java`

- [ ] **Step 1: Write tests for flattening**

Add to `FlatRowTest.java`:

```java
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;

import java.util.List;

// ... existing tests ...

@Test
@DisplayName("Flatten simple element with attributes")
void testFlattenElementWithAttributes() {
    var doc = new XmlDocument();
    var root = new XmlElement("Company");
    root.setAttribute("xmlns", "http://example.com");
    root.setAttribute("version", "1.0");
    doc.addChild(root);

    List<FlatRow> rows = FlatRow.flatten(doc);

    // Row 0: Document (skipped if skipDocumentRow=true) or Company element
    // We expect: Company, then its 2 attributes
    FlatRow companyRow = rows.stream()
        .filter(r -> r.getType() == FlatRow.RowType.ELEMENT && "Company".equals(r.getLabel()))
        .findFirst().orElseThrow();
    assertEquals(0, companyRow.getDepth());
    assertTrue(companyRow.isExpanded()); // root element starts expanded

    long attrCount = rows.stream()
        .filter(r -> r.getType() == FlatRow.RowType.ATTRIBUTE && r.getParentRow() == companyRow)
        .count();
    assertEquals(2, attrCount);
}

@Test
@DisplayName("Flatten nested elements produces correct depth")
void testFlattenNestedElements() {
    var doc = new XmlDocument();
    var root = new XmlElement("Root");
    var child = new XmlElement("Child");
    var grandchild = new XmlElement("Grandchild");
    grandchild.addChild(new XmlText("text"));
    child.addChild(grandchild);
    root.addChild(child);
    doc.addChild(root);

    List<FlatRow> rows = FlatRow.flatten(doc);

    FlatRow rootRow = rows.stream()
        .filter(r -> "Root".equals(r.getLabel()) && r.getType() == FlatRow.RowType.ELEMENT)
        .findFirst().orElseThrow();
    FlatRow childRow = rows.stream()
        .filter(r -> "Child".equals(r.getLabel()) && r.getType() == FlatRow.RowType.ELEMENT)
        .findFirst().orElseThrow();
    FlatRow gcRow = rows.stream()
        .filter(r -> "Grandchild".equals(r.getLabel()) && r.getType() == FlatRow.RowType.ELEMENT)
        .findFirst().orElseThrow();

    assertEquals(0, rootRow.getDepth());
    assertEquals(1, childRow.getDepth());
    assertEquals(2, gcRow.getDepth());
    // Grandchild is a leaf with text
    assertTrue(gcRow.isLeafWithValue());
    assertEquals("text", gcRow.getValue());
}

@Test
@DisplayName("Flatten produces attributes before child elements")
void testFlattenOrderAttributesFirst() {
    var doc = new XmlDocument();
    var root = new XmlElement("Root");
    root.setAttribute("id", "1");
    var child = new XmlElement("Child");
    child.addChild(new XmlText("value"));
    root.addChild(child);
    doc.addChild(root);

    List<FlatRow> rows = FlatRow.flatten(doc);

    // Find indices
    int attrIdx = -1, childIdx = -1;
    for (int i = 0; i < rows.size(); i++) {
        if (rows.get(i).getType() == FlatRow.RowType.ATTRIBUTE && "id".equals(rows.get(i).getLabel())) attrIdx = i;
        if (rows.get(i).getType() == FlatRow.RowType.ELEMENT && "Child".equals(rows.get(i).getLabel())) childIdx = i;
    }
    assertTrue(attrIdx < childIdx, "Attributes must come before child elements");
}

@Test
@DisplayName("Toggling visibility hides descendants")
void testToggleVisibility() {
    var doc = new XmlDocument();
    var root = new XmlElement("Root");
    root.setAttribute("a", "1");
    var child = new XmlElement("Child");
    child.addChild(new XmlText("text"));
    root.addChild(child);
    doc.addChild(root);

    List<FlatRow> rows = FlatRow.flatten(doc);
    FlatRow rootRow = rows.get(0); // Root element

    // Initially all visible
    assertTrue(rows.stream().allMatch(FlatRow::isVisible));

    // Collapse root
    FlatRow.toggleExpand(rootRow, rows);
    assertFalse(rootRow.isExpanded());
    // Root visible, children not
    assertTrue(rootRow.isVisible());
    long visibleCount = rows.stream().filter(FlatRow::isVisible).count();
    assertEquals(1, visibleCount);

    // Expand root again
    FlatRow.toggleExpand(rootRow, rows);
    assertTrue(rootRow.isExpanded());
    assertTrue(rows.stream().allMatch(FlatRow::isVisible));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRowTest"`
Expected: Compilation error — `FlatRow.flatten()` and `FlatRow.toggleExpand()` do not exist

- [ ] **Step 3: Implement flattening and toggle methods**

Add to `FlatRow.java`:

```java
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Add these static methods to FlatRow class:

/**
 * Flatten an XmlDocument into a list of FlatRows.
 * Order: For each element, emit the element row, then its attribute rows,
 * then recurse into child elements. Leaf elements with only text get their
 * value set directly (no separate text row).
 */
public static List<FlatRow> flatten(XmlDocument document) {
    List<FlatRow> rows = new ArrayList<>();
    for (XmlNode child : document.getChildren()) {
        if (child instanceof XmlElement element) {
            flattenElement(element, 0, null, rows, true);
        } else if (child instanceof XmlProcessingInstruction pi) {
            rows.add(new FlatRow(RowType.PROCESSING_INSTRUCTION, 0, pi, null,
                    "<?" + pi.getTarget() + "?>", pi.getData(), 0));
        } else if (child instanceof XmlComment comment) {
            rows.add(new FlatRow(RowType.COMMENT, 0, comment, null,
                    "<!-- -->", comment.getText(), 0));
        }
    }
    return rows;
}

private static void flattenElement(XmlElement element, int depth, FlatRow parentRow,
                                    List<FlatRow> rows, boolean expandByDefault) {
    // Count direct child elements (not text nodes)
    int childElementCount = 0;
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement) childElementCount++;
    }

    // Determine if this is a leaf with text
    String textValue = null;
    if (childElementCount == 0) {
        textValue = extractTextContent(element);
    }

    int totalChildren = element.getAttributes().size() + childElementCount;

    FlatRow elementRow = new FlatRow(RowType.ELEMENT, depth, element, parentRow,
            element.getName(), textValue, totalChildren);
    if (expandByDefault) {
        elementRow.setExpanded(true);
    }
    rows.add(elementRow);

    // Attributes (always visible when parent is visible)
    int attrIdx = 0;
    for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
        FlatRow attrRow = new FlatRow(RowType.ATTRIBUTE, depth + 1, element, elementRow,
                attr.getKey(), attr.getValue(), 0);
        attrRow.setAttributeIndex(attrIdx++);
        rows.add(attrRow);
    }

    // Child elements
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement childElement) {
            flattenElement(childElement, depth + 1, elementRow, rows, false);
        } else if (child instanceof XmlText text) {
            // Only add separate text row if element also has child elements (mixed content)
            if (childElementCount > 0 && text.getText() != null && !text.getText().isBlank()) {
                rows.add(new FlatRow(RowType.TEXT, depth + 1, text, elementRow,
                        "#text", text.getText(), 0));
            }
        } else if (child instanceof XmlComment comment) {
            rows.add(new FlatRow(RowType.COMMENT, depth + 1, comment, elementRow,
                    "<!-- -->", comment.getText(), 0));
        } else if (child instanceof XmlCData cdata) {
            rows.add(new FlatRow(RowType.CDATA, depth + 1, cdata, elementRow,
                    "<![CDATA[]]>", cdata.getText(), 0));
        }
    }
}

private static String extractTextContent(XmlElement element) {
    StringBuilder sb = new StringBuilder();
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlText text) {
            String t = text.getText();
            if (t != null && !t.isBlank()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(t.strip());
            }
        }
    }
    return sb.isEmpty() ? null : sb.toString();
}

/**
 * Toggle expand/collapse for an element row. When collapsing, recursively
 * hides all descendant rows. When expanding, shows direct children
 * (respecting their own expand state).
 */
public static void toggleExpand(FlatRow elementRow, List<FlatRow> allRows) {
    if (!elementRow.isExpandable()) return;

    boolean newExpanded = !elementRow.isExpanded();
    elementRow.setExpanded(newExpanded);

    int rowIndex = allRows.indexOf(elementRow);
    if (rowIndex < 0) return;

    if (newExpanded) {
        // Show direct children; for expanded child elements, recursively show their children too
        setDescendantVisibility(elementRow, allRows, rowIndex + 1, true);
    } else {
        // Hide all descendants
        for (int i = rowIndex + 1; i < allRows.size(); i++) {
            FlatRow row = allRows.get(i);
            if (!isDescendantOf(row, elementRow)) break;
            row.setVisible(false);
        }
    }
}

/**
 * Recursively set visibility for descendants when expanding.
 */
private static void setDescendantVisibility(FlatRow parentRow, List<FlatRow> allRows,
                                             int startIndex, boolean visible) {
    for (int i = startIndex; i < allRows.size(); i++) {
        FlatRow row = allRows.get(i);
        if (!isDescendantOf(row, parentRow)) break;

        if (row.getParentRow() == parentRow) {
            // Direct child — always show/hide
            row.setVisible(visible);
            // If this is an expanded element, recursively show its children
            if (visible && row.isExpandable() && row.isExpanded()) {
                setDescendantVisibility(row, allRows, i + 1, true);
            }
        }
        // Non-direct descendants are handled by recursive calls above
    }
}

/** Check if candidate is a descendant of ancestor. */
private static boolean isDescendantOf(FlatRow candidate, FlatRow ancestor) {
    FlatRow parent = candidate.getParentRow();
    while (parent != null) {
        if (parent == ancestor) return true;
        parent = parent.getParentRow();
    }
    return false;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRowTest"`
Expected: All 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java
git commit -m "feat: add flattening algorithm and expand/collapse toggle to FlatRow"
```

---

### Task 3: Add New Rendering Constants

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/common/utilities/XmlCanvasRenderingHelper.java`

- [ ] **Step 1: Add new constants to XmlCanvasRenderingHelper**

Add after the existing layout constants block (after line 52):

```java
// ==================== XMLSpy-Style Layout Constants ====================

public static final double EXPAND_BAR_WIDTH = 12;
public static final double EXPAND_BAR_ARROW_SIZE = 4;
public static final double TREE_LINE_OFFSET = 6;  // Center of expand bar from left edge
public static final double ROW_NUMBER_COLUMN_WIDTH = 36;

// ==================== XMLSpy-Style Color Constants ====================

public static final Color COLOR_EXPAND_BAR = Color.web("#d0d0d0");
public static final Color COLOR_EXPAND_BAR_HOVER = Color.web("#a0a0a0");
public static final Color COLOR_EXPAND_BAR_ARROW = Color.web("#666666");
public static final Color COLOR_TREE_LINE = Color.web("#d0d0d0");
public static final Color COLOR_ZEBRA_EVEN = Color.web("#ffffff");
public static final Color COLOR_ZEBRA_ODD = Color.web("#f9fafb");
public static final Color COLOR_ROW_SEPARATOR = Color.web("#e5e7eb");
public static final Color COLOR_ROW_NUMBER = Color.web("#9ca3af");

// ==================== Icon Colors ====================

public static final Color COLOR_ICON_ELEMENT_XMLSPY = Color.web("#0066cc");
public static final Color COLOR_ICON_ATTRIBUTE_XMLSPY = Color.web("#e81416");
public static final Color COLOR_ICON_TEXT_XMLSPY = Color.web("#008000");
public static final Color COLOR_ICON_COMMENT_XMLSPY = Color.web("#0d9488");
public static final Color COLOR_ICON_PI_XMLSPY = Color.web("#7c3aed");
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/common/utilities/XmlCanvasRenderingHelper.java
git commit -m "feat: add XMLSpy-style rendering constants for grid view"
```

---

### Task 4: Rewrite XmlCanvasView — Core Rendering

This is the largest task. It rewrites the rendering pipeline from nested grids to flat rows.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Replace instance fields for flat row model**

Replace the grid data and state fields (lines 78-132) with:

```java
// ==================== Flat Row Data ====================

private List<FlatRow> allRows = new ArrayList<>();
private List<FlatRow> visibleRows = new ArrayList<>();  // Cache of visible rows for rendering

// Keep rootNode temporarily for compatibility during transition
private NestedGridNode rootNode;

// ==================== Layout Constants ====================

private static final double ROW_HEIGHT = 24;
private static final double INDENT = 20;
private static final double EXPAND_BAR_WIDTH = 12;
private static final double NAME_COLUMN_MIN_WIDTH = 150;
private static final double SCROLLBAR_WIDTH = 14;

// Dynamically calculated
private double nameColumnWidth = NAME_COLUMN_MIN_WIDTH;

// ==================== Scroll State ====================

private double scrollOffsetX = 0;
private double scrollOffsetY = 0;
private double totalHeight = 0;
private double totalWidth = 0;

// ==================== State ====================

private FlatRow selectedRow = null;
private FlatRow hoveredRow = null;
private FlatRow hoveredExpandBar = null;  // Which element's expand bar is hovered

// Table state (kept for repeating elements)
private RepeatingElementsTable selectedTable = null;
private RepeatingElementsTable hoveredTable = null;
private int hoveredTableRowIndex = -1;
private int hoveredTableColumnIndex = -1;

// ==================== Layout State ====================

private boolean layoutDirty = true;

// ==================== Inline Editing ====================

private TextField editField = null;
private FlatRow editingRow = null;
private boolean editingValue = false;     // true = editing value column, false = editing label
private boolean editingElementName = false;

// Table cell editing state
private RepeatingElementsTable editingTable = null;
private int editingTableRowIndex = -1;
private String editingTableColumnName = null;

// Type-aware widget editing
private TypeAwareWidgetFactory.EditWidget activeEditWidget = null;
private Node activeWidgetNode = null;

// Guard to prevent double commits
private boolean isCommitting = false;
```

Also add the import for `ArrayList` and `List` at the top of the file, and add `import java.util.ArrayList;` and `import java.util.List;` if not already present.

- [ ] **Step 2: Replace color constants**

Replace the existing color constants block (lines 184-225) with:

```java
// ==================== Colors ====================

// Row backgrounds
private static final Color ZEBRA_EVEN = Color.rgb(255, 255, 255);
private static final Color ZEBRA_ODD = Color.rgb(249, 250, 251);

// Selection/Hover
private static final Color SELECTED_BORDER = Color.rgb(59, 130, 246);
private static final Color SELECTED_BG = Color.rgb(239, 246, 255);
private static final Color HOVERED_BG = Color.rgb(243, 244, 246);

// Text colors
private static final Color TEXT_ELEMENT = Color.rgb(0, 102, 204);       // #0066cc
private static final Color TEXT_ATTRIBUTE_NAME = Color.rgb(232, 20, 22); // #e81416
private static final Color TEXT_ATTRIBUTE_VALUE = Color.rgb(21, 128, 61);
private static final Color TEXT_CONTENT = Color.rgb(0, 128, 0);          // #008000
private static final Color TEXT_SECONDARY = Color.rgb(107, 114, 128);
private static final Color TEXT_COMMENT = Color.rgb(13, 148, 136);
private static final Color TEXT_PI = Color.rgb(124, 58, 237);

// Tree lines and expand bar
private static final Color TREE_LINE_COLOR = Color.rgb(208, 208, 208);  // #d0d0d0
private static final Color EXPAND_BAR_COLOR = Color.rgb(208, 208, 208);
private static final Color EXPAND_BAR_HOVER_COLOR = Color.rgb(160, 160, 160);
private static final Color EXPAND_ARROW_COLOR = Color.rgb(102, 102, 102);

// Row separator
private static final Color ROW_SEPARATOR = Color.rgb(229, 231, 235);

// Row number
private static final Color ROW_NUMBER_COLOR = Color.rgb(156, 163, 175);

// Highlight
private static final Color HIGHLIGHT_COLOR = Color.rgb(254, 240, 138);

// Table colors (kept from existing)
private static final Color TABLE_HEADER_BG = Color.rgb(236, 253, 245);
private static final Color TABLE_HEADER_TEXT = Color.rgb(4, 120, 87);
private static final Color TABLE_BORDER = Color.rgb(167, 243, 208);
private static final Color TABLE_ROW_EVEN = Color.rgb(255, 255, 255);
private static final Color TABLE_ROW_ODD = Color.rgb(249, 250, 251);
private static final Color TABLE_ROW_HOVER = Color.rgb(236, 253, 245);
private static final Color TABLE_ROW_SELECTED = Color.rgb(209, 250, 229);
```

- [ ] **Step 3: Rewrite rebuildTree to use FlatRow**

Replace the `rebuildTree()` method (line 1296-1330) with:

```java
private void rebuildTree() {
    cancelEditing();

    XmlDocument document = context.getDocument();
    if (document == null) {
        allRows.clear();
        visibleRows.clear();
        rootNode = null;
        layoutDirty = true;
        render();
        return;
    }

    allRows = FlatRow.flatten(document);
    recalculateVisibleRows();
    layoutDirty = true;
    ensureLayout();
    updateScrollBars();
    render();
}

private void recalculateVisibleRows() {
    visibleRows = allRows.stream().filter(FlatRow::isVisible).collect(java.util.stream.Collectors.toList());
    calculateNameColumnWidth();
}

private void calculateNameColumnWidth() {
    double maxWidth = NAME_COLUMN_MIN_WIDTH;
    for (FlatRow row : visibleRows) {
        double indent = row.getDepth() * INDENT + EXPAND_BAR_WIDTH + 24; // bar + icon + gap
        double textWidth = estimateTextWidth(row.getLabel()) + indent;
        maxWidth = Math.max(maxWidth, textWidth);
    }
    nameColumnWidth = maxWidth + 20; // padding
}

private double estimateTextWidth(String text) {
    if (text == null) return 0;
    return text.length() * 7.0; // Monospace approximation
}
```

- [ ] **Step 4: Rewrite ensureLayout for flat rows**

Replace `ensureLayout()` (line 350-369) with:

```java
private void ensureLayout() {
    if (!layoutDirty) return;
    layoutDirty = false;

    totalHeight = visibleRows.size() * ROW_HEIGHT;

    // Account for repeating tables (they take more vertical space)
    for (FlatRow row : visibleRows) {
        if (row.hasRepeatingTable() && row.isExpanded()) {
            totalHeight += row.getRepeatingTable().calculateHeight() - ROW_HEIGHT;
        }
    }

    totalWidth = nameColumnWidth + 400; // name column + value column min
}
```

- [ ] **Step 5: Rewrite render() and renderVisible()**

Replace `render()` (line 1331-1361) and `renderVisible()` (line 1362-1388) with:

```java
public void render() {
    double cw = canvas.getWidth();
    double ch = canvas.getHeight();

    gc.clearRect(0, 0, cw, ch);

    if (visibleRows.isEmpty()) {
        drawEmptyState();
        return;
    }

    gc.save();
    gc.translate(-scrollOffsetX, -scrollOffsetY);

    // Calculate visible row range
    int firstVisible = Math.max(0, (int) (scrollOffsetY / ROW_HEIGHT));
    int lastVisible = Math.min(visibleRows.size() - 1,
            (int) ((scrollOffsetY + ch) / ROW_HEIGHT) + 1);

    // Draw tree lines first (behind rows)
    drawTreeLines(firstVisible, lastVisible);

    // Draw rows
    for (int i = firstVisible; i <= lastVisible; i++) {
        drawRow(visibleRows.get(i), i);
    }

    // Draw expand bars on top
    drawExpandBars(firstVisible, lastVisible);

    gc.restore();

    drawInfo();
}
```

- [ ] **Step 6: Implement drawRow()**

Add new method:

```java
private void drawRow(FlatRow row, int visibleIndex) {
    double y = visibleIndex * ROW_HEIGHT;
    double x = 0;
    double w = Math.max(totalWidth, canvas.getWidth());

    // Background (zebra striping)
    Color bgColor;
    if (row == highlightedRow) {
        bgColor = HIGHLIGHT_COLOR;
    } else if (row.isSelected()) {
        bgColor = SELECTED_BG;
    } else if (row.isHovered()) {
        bgColor = HOVERED_BG;
    } else {
        bgColor = (visibleIndex % 2 == 0) ? ZEBRA_EVEN : ZEBRA_ODD;
    }
    gc.setFill(bgColor);
    gc.fillRect(x, y, w, ROW_HEIGHT);

    // Selection border
    if (row.isSelected()) {
        gc.setStroke(SELECTED_BORDER);
        gc.setLineWidth(1.5);
        gc.strokeRect(x + 0.5, y + 0.5, w - 1, ROW_HEIGHT - 1);
    }

    // Row separator
    gc.setStroke(ROW_SEPARATOR);
    gc.setLineWidth(0.5);
    gc.strokeLine(x, y + ROW_HEIGHT, x + w, y + ROW_HEIGHT);

    // Content area starts after expand bars
    double contentX = getContentStartX(row);

    // Draw icon
    drawRowIcon(row.getType(), contentX, y);
    contentX += 18;

    // Draw label
    gc.setFont(HEADER_FONT);
    gc.setFill(getRowLabelColor(row));
    gc.setTextAlign(TextAlignment.LEFT);
    gc.setTextBaseline(VPos.CENTER);

    if (!isEditingLabel(row)) {
        gc.fillText(row.getLabel(), contentX, y + ROW_HEIGHT / 2);
    }

    // Draw value
    if (row.getValue() != null && !isEditingValue(row)) {
        double valueX = nameColumnWidth;
        gc.setFont(ROW_FONT);
        gc.setFill(getRowValueColor(row));
        gc.fillText(row.getValue(), valueX, y + ROW_HEIGHT / 2);
    }

    // Child count for elements
    if (row.getType() == FlatRow.RowType.ELEMENT && row.getChildCount() > 0 && !row.isLeafWithValue()) {
        String countText = "(" + row.getChildCount() + ")";
        gc.setFont(SMALL_FONT);
        gc.setFill(TEXT_SECONDARY);
        gc.fillText(countText, contentX + estimateTextWidth(row.getLabel()) + 8, y + ROW_HEIGHT / 2);
    }
}

private double getContentStartX(FlatRow row) {
    // Expand bars take space for each ancestor level, plus the row's own depth indentation
    return EXPAND_BAR_WIDTH + row.getDepth() * INDENT;
}

private Color getRowLabelColor(FlatRow row) {
    return switch (row.getType()) {
        case ELEMENT -> TEXT_ELEMENT;
        case ATTRIBUTE -> TEXT_ATTRIBUTE_NAME;
        case TEXT -> TEXT_SECONDARY;
        case COMMENT -> TEXT_COMMENT;
        case CDATA -> TEXT_SECONDARY;
        case PROCESSING_INSTRUCTION -> TEXT_PI;
        case DOCUMENT -> TEXT_ELEMENT;
    };
}

private Color getRowValueColor(FlatRow row) {
    return switch (row.getType()) {
        case ATTRIBUTE -> TEXT_ATTRIBUTE_VALUE;
        case ELEMENT -> TEXT_CONTENT;
        default -> TEXT_CONTENT;
    };
}

private boolean isEditingLabel(FlatRow row) {
    return editingRow == row && editingElementName;
}

private boolean isEditingValue(FlatRow row) {
    return editingRow == row && editingValue;
}
```

- [ ] **Step 7: Implement drawRowIcon()**

Replace `drawElementIcon()` (line 1618-1674) with:

```java
private void drawRowIcon(FlatRow.RowType type, double x, double y) {
    double cy = y + ROW_HEIGHT / 2;

    switch (type) {
        case ELEMENT -> {
            // <> diamond — angle brackets
            gc.setStroke(TEXT_ELEMENT);
            gc.setLineWidth(1.5);
            double size = 5;
            // Left bracket <
            gc.strokeLine(x + 3, cy, x + 3 + size, cy - size);
            gc.strokeLine(x + 3, cy, x + 3 + size, cy + size);
            // Right bracket >
            gc.strokeLine(x + 13, cy, x + 13 - size, cy - size);
            gc.strokeLine(x + 13, cy, x + 13 - size, cy + size);
        }
        case ATTRIBUTE -> {
            // = equals sign
            gc.setStroke(TEXT_ATTRIBUTE_NAME);
            gc.setLineWidth(2);
            gc.strokeLine(x + 4, cy - 2.5, x + 12, cy - 2.5);
            gc.strokeLine(x + 4, cy + 2.5, x + 12, cy + 2.5);
        }
        case TEXT -> {
            // T icon
            gc.setStroke(TEXT_CONTENT);
            gc.setLineWidth(2);
            gc.strokeLine(x + 4, cy - 5, x + 12, cy - 5); // top bar
            gc.strokeLine(x + 8, cy - 5, x + 8, cy + 5);  // vertical
        }
        case COMMENT -> {
            // <!-- --> speech bubble
            gc.setStroke(TEXT_COMMENT);
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(x + 3, cy - 4, 10, 8, 3, 3);
        }
        case CDATA -> {
            // [[ ]] square brackets
            gc.setStroke(TEXT_SECONDARY);
            gc.setLineWidth(1.5);
            gc.strokeLine(x + 3, cy - 4, x + 6, cy - 4);
            gc.strokeLine(x + 3, cy - 4, x + 3, cy + 4);
            gc.strokeLine(x + 3, cy + 4, x + 6, cy + 4);
            gc.strokeLine(x + 13, cy - 4, x + 10, cy - 4);
            gc.strokeLine(x + 13, cy - 4, x + 13, cy + 4);
            gc.strokeLine(x + 13, cy + 4, x + 10, cy + 4);
        }
        case PROCESSING_INSTRUCTION -> {
            // <?> icon
            gc.setStroke(TEXT_PI);
            gc.setLineWidth(1.5);
            gc.strokeOval(x + 4, cy - 4, 8, 8);
            gc.fillOval(x + 7, cy - 1, 2, 2);
        }
        case DOCUMENT -> {
            // Document icon
            gc.setStroke(TEXT_ELEMENT);
            gc.setLineWidth(1.5);
            gc.strokeRect(x + 4, cy - 5, 8, 10);
            gc.strokeLine(x + 6, cy - 2, x + 10, cy - 2);
            gc.strokeLine(x + 6, cy + 1, x + 10, cy + 1);
        }
    }
}
```

- [ ] **Step 8: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (there may be warnings about unused methods from the old rendering — that's OK for now)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "feat: rewrite XmlCanvasView core rendering to flat row-based layout"
```

---

### Task 5: Implement Expand Bar and Tree Lines

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Implement drawExpandBars()**

Add new method to `XmlCanvasView`:

```java
private void drawExpandBars(int firstVisible, int lastVisible) {
    for (int i = firstVisible; i <= lastVisible; i++) {
        FlatRow row = visibleRows.get(i);
        if (row.getType() != FlatRow.RowType.ELEMENT || !row.isExpandable()) continue;

        double barX = row.getDepth() * INDENT;
        double barY = i * ROW_HEIGHT;

        if (!row.isExpanded()) {
            // Collapsed: draw right arrow only
            drawCollapsedArrow(barX, barY, row == hoveredExpandBar);
        } else {
            // Expanded: find the last visible descendant to calculate bar height
            int lastDescendantIndex = findLastVisibleDescendantIndex(i);
            double barHeight = (lastDescendantIndex - i + 1) * ROW_HEIGHT;

            boolean isHovered = (row == hoveredExpandBar);
            Color barColor = isHovered ? EXPAND_BAR_HOVER_COLOR : EXPAND_BAR_COLOR;

            // Draw vertical bar
            double barCenterX = barX + EXPAND_BAR_WIDTH / 2;
            gc.setStroke(barColor);
            gc.setLineWidth(1);
            gc.strokeLine(barCenterX, barY + ROW_HEIGHT, barCenterX, barY + barHeight);

            // Top arrow (down)
            drawExpandedArrowTop(barX, barY, isHovered);

            // Bottom arrow (up)
            double bottomY = barY + barHeight - ROW_HEIGHT;
            if (lastDescendantIndex > i) {
                drawExpandedArrowBottom(barX, bottomY, isHovered);
            }
        }
    }
}

private void drawCollapsedArrow(double barX, double barY, boolean isHovered) {
    double cx = barX + EXPAND_BAR_WIDTH / 2;
    double cy = barY + ROW_HEIGHT / 2;
    double size = 4;

    gc.setStroke(isHovered ? EXPAND_BAR_HOVER_COLOR : EXPAND_ARROW_COLOR);
    gc.setLineWidth(1.5);
    // Right-pointing arrow ►
    gc.strokeLine(cx - size / 2, cy - size, cx + size / 2, cy);
    gc.strokeLine(cx + size / 2, cy, cx - size / 2, cy + size);
}

private void drawExpandedArrowTop(double barX, double barY, boolean isHovered) {
    double cx = barX + EXPAND_BAR_WIDTH / 2;
    double cy = barY + ROW_HEIGHT / 2;
    double size = 4;

    gc.setStroke(isHovered ? EXPAND_BAR_HOVER_COLOR : EXPAND_ARROW_COLOR);
    gc.setLineWidth(1.5);
    // Down-pointing arrow ▼
    gc.strokeLine(cx - size, cy - size / 2, cx, cy + size / 2);
    gc.strokeLine(cx, cy + size / 2, cx + size, cy - size / 2);
}

private void drawExpandedArrowBottom(double barX, double barY, boolean isHovered) {
    double cx = barX + EXPAND_BAR_WIDTH / 2;
    double cy = barY + ROW_HEIGHT / 2;
    double size = 4;

    gc.setStroke(isHovered ? EXPAND_BAR_HOVER_COLOR : EXPAND_ARROW_COLOR);
    gc.setLineWidth(1.5);
    // Up-pointing arrow ▲
    gc.strokeLine(cx - size, cy + size / 2, cx, cy - size / 2);
    gc.strokeLine(cx, cy - size / 2, cx + size, cy + size / 2);
}

private int findLastVisibleDescendantIndex(int parentIndex) {
    FlatRow parent = visibleRows.get(parentIndex);
    int lastIndex = parentIndex;

    for (int i = parentIndex + 1; i < visibleRows.size(); i++) {
        FlatRow row = visibleRows.get(i);
        if (isDescendantOf(row, parent)) {
            lastIndex = i;
        } else {
            break;
        }
    }
    return lastIndex;
}

private boolean isDescendantOf(FlatRow candidate, FlatRow ancestor) {
    FlatRow parent = candidate.getParentRow();
    while (parent != null) {
        if (parent == ancestor) return true;
        parent = parent.getParentRow();
    }
    return false;
}
```

- [ ] **Step 2: Implement drawTreeLines()**

```java
private void drawTreeLines(int firstVisible, int lastVisible) {
    gc.setStroke(TREE_LINE_COLOR);
    gc.setLineWidth(1);

    for (int i = firstVisible; i <= lastVisible; i++) {
        FlatRow row = visibleRows.get(i);
        if (row.getParentRow() == null) continue;

        FlatRow parent = row.getParentRow();
        if (parent.getType() != FlatRow.RowType.ELEMENT) continue;

        double parentBarCenterX = parent.getDepth() * INDENT + EXPAND_BAR_WIDTH / 2;
        double rowCenterY = i * ROW_HEIGHT + ROW_HEIGHT / 2;
        double contentStartX = getContentStartX(row);

        // Horizontal branch from parent's vertical line to this row's icon
        gc.strokeLine(parentBarCenterX, rowCenterY, contentStartX - 2, rowCenterY);
    }
}
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "feat: implement expand bars and tree connection lines"
```

---

### Task 6: Rewrite Mouse and Keyboard Interaction

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Rewrite handleMouseClick for flat rows**

Replace `handleMouseClick()` (line 756-831) with:

```java
private void handleMouseClick(MouseEvent event) {
    double mx = event.getX() + scrollOffsetX;
    double my = event.getY() + scrollOffsetY;

    if (event.getButton() == MouseButton.SECONDARY) {
        handleContextMenu(event, mx, my);
        return;
    }

    // Find which visible row was clicked
    int rowIndex = (int) (my / ROW_HEIGHT);
    if (rowIndex < 0 || rowIndex >= visibleRows.size()) return;

    FlatRow clickedRow = visibleRows.get(rowIndex);

    // Check if click is on an expand bar
    FlatRow expandBarRow = findExpandBarAt(mx, my);
    if (expandBarRow != null) {
        FlatRow.toggleExpand(expandBarRow, allRows);
        recalculateVisibleRows();
        layoutDirty = true;
        ensureLayout();
        updateScrollBars();
        render();
        return;
    }

    // Double-click: start editing
    if (event.getClickCount() == 2) {
        if (mx >= nameColumnWidth) {
            // Click on value column
            startEditingValue(clickedRow);
        } else {
            // Click on name column — only for elements
            if (clickedRow.getType() == FlatRow.RowType.ELEMENT) {
                startEditingElementName(clickedRow);
            }
        }
        return;
    }

    // Single click: select row
    selectRow(clickedRow);
}

private FlatRow findExpandBarAt(double mx, double my) {
    int rowIndex = (int) (my / ROW_HEIGHT);
    if (rowIndex < 0 || rowIndex >= visibleRows.size()) return null;

    // Check each visible expandable element to see if mx is in its bar region
    for (int i = 0; i <= Math.min(rowIndex, visibleRows.size() - 1); i++) {
        FlatRow row = visibleRows.get(i);
        if (row.getType() != FlatRow.RowType.ELEMENT || !row.isExpandable()) continue;

        double barX = row.getDepth() * INDENT;
        double barStartY = i * ROW_HEIGHT;

        if (!row.isExpanded()) {
            // Collapsed: only the arrow row is clickable
            if (rowIndex == i && mx >= barX && mx <= barX + EXPAND_BAR_WIDTH) {
                return row;
            }
        } else {
            // Expanded: entire bar height is clickable
            int lastDescIdx = findLastVisibleDescendantIndex(i);
            double barEndY = (lastDescIdx + 1) * ROW_HEIGHT;
            if (mx >= barX && mx <= barX + EXPAND_BAR_WIDTH && my >= barStartY && my < barEndY) {
                return row;
            }
        }
    }
    return null;
}

private void selectRow(FlatRow row) {
    if (selectedRow != null) selectedRow.setSelected(false);
    selectedRow = row;
    if (selectedRow != null) {
        selectedRow.setSelected(true);
        // Update context selection model
        context.getSelectionModel().setSelectedNode(row.getModelNode());
    }
    render();
}
```

- [ ] **Step 2: Rewrite handleMouseMove for flat rows**

Replace `handleMouseMove()` (line 1097-1218) with:

```java
private void handleMouseMove(MouseEvent event) {
    double mx = event.getX() + scrollOffsetX;
    double my = event.getY() + scrollOffsetY;

    int rowIndex = (int) (my / ROW_HEIGHT);
    FlatRow newHoveredRow = (rowIndex >= 0 && rowIndex < visibleRows.size())
            ? visibleRows.get(rowIndex) : null;
    FlatRow newHoveredBar = findExpandBarAt(mx, my);

    boolean changed = false;

    if (newHoveredRow != hoveredRow) {
        if (hoveredRow != null) hoveredRow.setHovered(false);
        hoveredRow = newHoveredRow;
        if (hoveredRow != null) hoveredRow.setHovered(true);
        changed = true;
    }

    if (newHoveredBar != hoveredExpandBar) {
        hoveredExpandBar = newHoveredBar;
        changed = true;
    }

    // Update cursor
    if (newHoveredBar != null) {
        setCursor(javafx.scene.Cursor.HAND);
    } else {
        setCursor(javafx.scene.Cursor.DEFAULT);
    }

    if (changed) render();
}
```

- [ ] **Step 3: Rewrite keyboard navigation for flat rows**

Replace `handleKeyNavigation()` (line 540-614) and the navigation helper methods with:

```java
private void handleKeyNavigation(KeyEvent event) {
    if (selectedRow == null && !visibleRows.isEmpty()) {
        selectRow(visibleRows.get(0));
        event.consume();
        return;
    }

    int currentIndex = visibleRows.indexOf(selectedRow);
    if (currentIndex < 0) return;

    switch (event.getCode()) {
        case UP -> {
            if (currentIndex > 0) {
                selectRow(visibleRows.get(currentIndex - 1));
                ensureRowVisible(currentIndex - 1);
            }
            event.consume();
        }
        case DOWN -> {
            if (currentIndex < visibleRows.size() - 1) {
                selectRow(visibleRows.get(currentIndex + 1));
                ensureRowVisible(currentIndex + 1);
            }
            event.consume();
        }
        case RIGHT -> {
            if (selectedRow.isExpandable() && !selectedRow.isExpanded()) {
                FlatRow.toggleExpand(selectedRow, allRows);
                recalculateVisibleRows();
                layoutDirty = true;
                ensureLayout();
                updateScrollBars();
                render();
            }
            event.consume();
        }
        case LEFT -> {
            if (selectedRow.isExpandable() && selectedRow.isExpanded()) {
                FlatRow.toggleExpand(selectedRow, allRows);
                recalculateVisibleRows();
                layoutDirty = true;
                ensureLayout();
                updateScrollBars();
                render();
            } else if (selectedRow.getParentRow() != null) {
                // Jump to parent
                int parentIndex = visibleRows.indexOf(selectedRow.getParentRow());
                if (parentIndex >= 0) {
                    selectRow(visibleRows.get(parentIndex));
                    ensureRowVisible(parentIndex);
                }
            }
            event.consume();
        }
        case ENTER -> {
            if (selectedRow.isExpandable()) {
                FlatRow.toggleExpand(selectedRow, allRows);
                recalculateVisibleRows();
                layoutDirty = true;
                ensureLayout();
                updateScrollBars();
                render();
            }
            event.consume();
        }
        case HOME -> {
            if (!visibleRows.isEmpty()) {
                selectRow(visibleRows.get(0));
                ensureRowVisible(0);
            }
            event.consume();
        }
        case END -> {
            if (!visibleRows.isEmpty()) {
                selectRow(visibleRows.get(visibleRows.size() - 1));
                ensureRowVisible(visibleRows.size() - 1);
            }
            event.consume();
        }
        default -> { /* no-op */ }
    }
}

private void ensureRowVisible(int visibleIndex) {
    double rowY = visibleIndex * ROW_HEIGHT;
    double viewportHeight = canvas.getHeight();

    if (rowY < scrollOffsetY) {
        scrollOffsetY = rowY;
    } else if (rowY + ROW_HEIGHT > scrollOffsetY + viewportHeight) {
        scrollOffsetY = rowY + ROW_HEIGHT - viewportHeight;
    }
    updateScrollBars();
    render();
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "feat: rewrite mouse click, hover, and keyboard navigation for flat rows"
```

---

### Task 7: Rewrite Inline Editing for Flat Rows

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Implement startEditingValue and startEditingElementName**

Replace existing editing start methods with:

```java
private void startEditingValue(FlatRow row) {
    if (row.getValue() == null && row.getType() != FlatRow.RowType.ATTRIBUTE) return;
    cancelEditing();

    editingRow = row;
    editingValue = true;
    editingElementName = false;

    int visibleIndex = visibleRows.indexOf(row);
    if (visibleIndex < 0) return;

    double x = nameColumnWidth - scrollOffsetX;
    double y = visibleIndex * ROW_HEIGHT - scrollOffsetY;
    double width = canvas.getWidth() - nameColumnWidth;

    String currentValue = row.getValue() != null ? row.getValue() : "";

    // Build XPath for schema lookup
    String xpath = buildXPathForRow(row);
    String attrName = (row.getType() == FlatRow.RowType.ATTRIBUTE) ? row.getLabel() : null;

    createEditField(currentValue, x, y, width, xpath, attrName);
}

private void startEditingElementName(FlatRow row) {
    if (row.getType() != FlatRow.RowType.ELEMENT) return;
    cancelEditing();

    editingRow = row;
    editingValue = false;
    editingElementName = true;

    int visibleIndex = visibleRows.indexOf(row);
    if (visibleIndex < 0) return;

    double contentX = getContentStartX(row) + 18 - scrollOffsetX; // after icon
    double y = visibleIndex * ROW_HEIGHT - scrollOffsetY;
    double width = nameColumnWidth - contentX - scrollOffsetX;

    createEditField(row.getLabel(), contentX, y, Math.max(width, 100));
}

private String buildXPathForRow(FlatRow row) {
    StringBuilder path = new StringBuilder();
    FlatRow current = row;

    // For attributes, start from parent element
    if (current.getType() == FlatRow.RowType.ATTRIBUTE) {
        current = current.getParentRow();
    }

    while (current != null && current.getType() == FlatRow.RowType.ELEMENT) {
        path.insert(0, "/" + current.getLabel());
        current = current.getParentRow();
    }

    return path.toString();
}
```

- [ ] **Step 2: Rewrite commitEditing for flat rows**

Replace `commitEditing()` (line 2418-2480) with:

```java
private void commitEditing() {
    if (isCommitting) return;
    isCommitting = true;

    try {
        String newValue;
        if (activeEditWidget != null) {
            newValue = activeEditWidget.getValue();
        } else if (editField != null) {
            newValue = editField.getText();
        } else {
            return;
        }

        // Table cell editing
        if (editingTable != null) {
            commitTableCellEditing(newValue);
            removeEditField();
            rebuildTree();
            notifyDocumentModified();
            return;
        }

        if (editingRow == null) {
            removeEditField();
            return;
        }

        XmlNode modelNode = editingRow.getModelNode();

        if (editingElementName) {
            // Rename element
            if (modelNode instanceof XmlElement element && !newValue.equals(element.getName())) {
                context.getCommandManager().executeCommand(
                        new RenameNodeCommand(element, newValue));
            }
        } else if (editingValue) {
            if (editingRow.getType() == FlatRow.RowType.ATTRIBUTE) {
                // Set attribute value
                if (modelNode instanceof XmlElement element) {
                    String attrName = editingRow.getLabel();
                    String oldValue = element.getAttribute(attrName);
                    if (!newValue.equals(oldValue)) {
                        context.getCommandManager().executeCommand(
                                new SetAttributeCommand(element, attrName, newValue));
                    }
                }
            } else if (editingRow.getType() == FlatRow.RowType.ELEMENT) {
                // Set leaf element text
                if (modelNode instanceof XmlElement element) {
                    context.getCommandManager().executeCommand(
                            new SetElementTextCommand(element, newValue));
                }
            } else if (editingRow.getType() == FlatRow.RowType.TEXT) {
                // Set text node content
                if (modelNode instanceof XmlText text) {
                    context.getCommandManager().executeCommand(
                            new SetTextCommand(text, newValue));
                }
            }
        }

        removeEditField();
        rebuildTree();
        notifyDocumentModified();
    } finally {
        isCommitting = false;
    }
}

private void removeEditField() {
    if (editField != null) {
        getChildren().remove(editField);
        editField = null;
    }
    if (activeWidgetNode != null) {
        getChildren().remove(activeWidgetNode);
        activeWidgetNode = null;
        activeEditWidget = null;
    }
    editingRow = null;
    editingValue = false;
    editingElementName = false;
    editingTable = null;
    editingTableRowIndex = -1;
    editingTableColumnName = null;
}
```

- [ ] **Step 3: Rewrite cancelEditing**

Replace `cancelEditing()` (line 2518-2540) with:

```java
private void cancelEditing() {
    removeEditField();
    render();
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "feat: rewrite inline editing for flat row-based layout"
```

---

### Task 8: Integrate Repeating Tables into Row Flow

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java`

- [ ] **Step 1: Add repeating element detection to FlatRow.flatten()**

In `FlatRow.java`, modify `flattenElement()` to detect and group repeating elements:

```java
private static void flattenElement(XmlElement element, int depth, FlatRow parentRow,
                                    List<FlatRow> rows, boolean expandByDefault) {
    // Count direct child elements
    int childElementCount = 0;
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement) childElementCount++;
    }

    // Determine if this is a leaf with text
    String textValue = null;
    if (childElementCount == 0) {
        textValue = extractTextContent(element);
    }

    int totalChildren = element.getAttributes().size() + childElementCount;

    FlatRow elementRow = new FlatRow(RowType.ELEMENT, depth, element, parentRow,
            element.getName(), textValue, totalChildren);
    if (expandByDefault) {
        elementRow.setExpanded(true);
    }
    rows.add(elementRow);

    // Attributes
    int attrIdx = 0;
    for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
        FlatRow attrRow = new FlatRow(RowType.ATTRIBUTE, depth + 1, element, elementRow,
                attr.getKey(), attr.getValue(), 0);
        attrRow.setAttributeIndex(attrIdx++);
        rows.add(attrRow);
    }

    // Detect repeating elements
    Map<String, List<XmlElement>> elementsByName = new java.util.LinkedHashMap<>();
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement ce) {
            elementsByName.computeIfAbsent(ce.getName(), k -> new ArrayList<>()).add(ce);
        }
    }
    java.util.Set<String> repeatingNames = new java.util.HashSet<>();
    for (var entry : elementsByName.entrySet()) {
        if (entry.getValue().size() >= 2) {
            repeatingNames.add(entry.getKey());
        }
    }

    // Child elements
    java.util.Set<String> processedRepeating = new java.util.HashSet<>();
    for (XmlNode child : element.getChildren()) {
        if (child instanceof XmlElement childElement) {
            if (repeatingNames.contains(childElement.getName())) {
                // Only create one FlatRow for the repeating group (first occurrence)
                if (!processedRepeating.contains(childElement.getName())) {
                    processedRepeating.add(childElement.getName());
                    List<XmlElement> group = elementsByName.get(childElement.getName());
                    FlatRow tableRow = new FlatRow(RowType.ELEMENT, depth + 1, childElement,
                            elementRow, childElement.getName(), null, group.size());
                    tableRow.setExpanded(expandByDefault);
                    // RepeatingElementsTable will be set by XmlCanvasView during rebuild
                    rows.add(tableRow);
                }
                // Skip individual elements — they're in the table
            } else {
                flattenElement(childElement, depth + 1, elementRow, rows, false);
            }
        } else if (child instanceof XmlText text) {
            if (childElementCount > 0 && text.getText() != null && !text.getText().isBlank()) {
                rows.add(new FlatRow(RowType.TEXT, depth + 1, text, elementRow,
                        "#text", text.getText(), 0));
            }
        } else if (child instanceof XmlComment comment) {
            rows.add(new FlatRow(RowType.COMMENT, depth + 1, comment, elementRow,
                    "<!-- -->", comment.getText(), 0));
        } else if (child instanceof XmlCData cdata) {
            rows.add(new FlatRow(RowType.CDATA, depth + 1, cdata, elementRow,
                    "<![CDATA[]]>", cdata.getText(), 0));
        }
    }
}
```

- [ ] **Step 2: Create repeating tables during rebuildTree**

Update `rebuildTree()` in `XmlCanvasView.java`:

```java
private void rebuildTree() {
    cancelEditing();

    XmlDocument document = context.getDocument();
    if (document == null) {
        allRows.clear();
        visibleRows.clear();
        layoutDirty = true;
        render();
        return;
    }

    allRows = FlatRow.flatten(document);

    // Create RepeatingElementsTable instances for grouped elements
    attachRepeatingTables();

    recalculateVisibleRows();
    layoutDirty = true;
    ensureLayout();
    updateScrollBars();
    render();
}

private void attachRepeatingTables() {
    for (FlatRow row : allRows) {
        if (row.getType() != FlatRow.RowType.ELEMENT) continue;
        if (row.getParentRow() == null) continue;

        // Check if the parent element has repeating children with this name
        XmlNode parentModel = row.getParentRow().getModelNode();
        if (!(parentModel instanceof XmlElement parentElement)) continue;

        java.util.List<XmlElement> sameNameSiblings = new java.util.ArrayList<>();
        for (XmlNode sibling : parentElement.getChildren()) {
            if (sibling instanceof XmlElement se && se.getName().equals(row.getLabel())) {
                sameNameSiblings.add(se);
            }
        }

        if (sameNameSiblings.size() >= 2) {
            RepeatingElementsTable table = new RepeatingElementsTable(
                    row.getLabel(), sameNameSiblings, null,
                    row.getDepth(), this::markLayoutDirty);
            row.setRepeatingTable(table);
        }
    }
}

private void markLayoutDirty() {
    layoutDirty = true;
}
```

- [ ] **Step 3: Add row number column to RepeatingElementsTable rendering**

In `XmlCanvasView.java`, update the table rendering methods. Add a new method to draw tables inline in the row flow:

```java
private double renderInlineTable(FlatRow row, int visibleIndex) {
    RepeatingElementsTable table = row.getRepeatingTable();
    if (table == null || !row.isExpanded()) return 0;

    double tableX = getContentStartX(row);
    double tableY = (visibleIndex + 1) * ROW_HEIGHT; // Below the element row
    double tableW = Math.max(totalWidth - tableX, 400);

    // Position the table
    table.setX(tableX);
    table.setY(tableY);

    // Draw table header row with column names and icons
    drawInlineTableColumnHeaders(table, tableX, tableY, tableW);

    // Draw data rows with row numbers
    double rowY = tableY + ROW_HEIGHT;
    for (int i = 0; i < table.getRows().size(); i++) {
        drawInlineTableDataRow(table, i, tableX, rowY, tableW);
        rowY += ROW_HEIGHT;
    }

    return table.getRows().size() * ROW_HEIGHT + ROW_HEIGHT; // total height of table block
}

private void drawInlineTableColumnHeaders(RepeatingElementsTable table, double x, double y, double w) {
    // Background
    gc.setFill(TABLE_HEADER_BG);
    gc.fillRect(x, y, w, ROW_HEIGHT);

    // Bottom border
    gc.setStroke(TABLE_BORDER);
    gc.setLineWidth(1);
    gc.strokeLine(x, y + ROW_HEIGHT, x + w, y + ROW_HEIGHT);

    // Row number column header (empty)
    double colX = x;
    double rowNumWidth = 36;
    gc.setStroke(ROW_SEPARATOR);
    gc.strokeLine(colX + rowNumWidth, y, colX + rowNumWidth, y + ROW_HEIGHT);
    colX += rowNumWidth;

    // Column headers with icons
    gc.setFont(ROW_FONT);
    gc.setTextAlign(TextAlignment.LEFT);
    gc.setTextBaseline(VPos.CENTER);

    for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
        // Draw column type icon
        if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            drawRowIcon(FlatRow.RowType.ATTRIBUTE, colX + 4, y);
        } else {
            drawRowIcon(FlatRow.RowType.ELEMENT, colX + 4, y);
        }

        // Column name
        gc.setFill(TABLE_HEADER_TEXT);
        gc.setFont(ROW_FONT);
        gc.fillText(col.getDisplayName(), colX + 22, y + ROW_HEIGHT / 2);

        colX += col.getWidth();
        gc.setStroke(ROW_SEPARATOR);
        gc.strokeLine(colX, y, colX, y + ROW_HEIGHT);
    }
}

private void drawInlineTableDataRow(RepeatingElementsTable table, int rowIndex, double x, double y, double w) {
    // Row background
    Color rowBg = (rowIndex % 2 == 0) ? TABLE_ROW_EVEN : TABLE_ROW_ODD;
    if (table == selectedTable && table.getSelectedRowIndex() == rowIndex) {
        rowBg = TABLE_ROW_SELECTED;
    } else if (table == hoveredTable && hoveredTableRowIndex == rowIndex) {
        rowBg = TABLE_ROW_HOVER;
    }
    gc.setFill(rowBg);
    gc.fillRect(x, y, w, ROW_HEIGHT);

    // Bottom border
    gc.setStroke(ROW_SEPARATOR);
    gc.setLineWidth(0.5);
    gc.strokeLine(x, y + ROW_HEIGHT, x + w, y + ROW_HEIGHT);

    // Row number
    double rowNumWidth = 36;
    gc.setFont(SMALL_FONT);
    gc.setFill(ROW_NUMBER_COLOR);
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setTextBaseline(VPos.CENTER);
    gc.fillText(String.valueOf(rowIndex + 1), x + rowNumWidth / 2, y + ROW_HEIGHT / 2);
    gc.setStroke(ROW_SEPARATOR);
    gc.strokeLine(x + rowNumWidth, y, x + rowNumWidth, y + ROW_HEIGHT);

    // Cell values
    double colX = x + rowNumWidth;
    RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);

    gc.setTextAlign(TextAlignment.LEFT);

    for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
        String value = row.getValue(col.getName());
        if (value != null) {
            gc.setFill(col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE
                    ? TEXT_ATTRIBUTE_VALUE : TEXT_CONTENT);
            gc.setFont(ROW_FONT);
            double availableWidth = col.getWidth() - 8;
            gc.fillText(truncateText(value, availableWidth), colX + 4, y + ROW_HEIGHT / 2);
        }

        colX += col.getWidth();
        gc.setStroke(ROW_SEPARATOR);
        gc.strokeLine(colX, y, colX, y + ROW_HEIGHT);
    }
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRow.java src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTable.java
git commit -m "feat: integrate repeating element tables into flat row flow with row numbers and column icons"
```

---

### Task 9: Wire Up Context Menu and Selection Model

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Rewrite handleContextMenu for flat rows**

Replace `handleContextMenu()` (line 833-891) with:

```java
private void handleContextMenu(MouseEvent event, double mx, double my) {
    int rowIndex = (int) (my / ROW_HEIGHT);
    if (rowIndex < 0 || rowIndex >= visibleRows.size()) return;

    FlatRow clickedRow = visibleRows.get(rowIndex);
    selectRow(clickedRow);

    XmlNode modelNode = clickedRow.getModelNode();
    if (modelNode == null) return;

    contextMenu.show(this, event.getScreenX(), event.getScreenY(), modelNode);
}
```

- [ ] **Step 2: Wire up handleKeyPress for flat rows**

Replace `handleKeyPress()` (line 520-535) with:

```java
private void handleKeyPress(KeyEvent event) {
    // Navigation keys
    if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN ||
        event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT ||
        event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.HOME ||
        event.getCode() == KeyCode.END) {
        handleKeyNavigation(event);
        return;
    }

    // F2 - start editing
    if (event.getCode() == KeyCode.F2 && selectedRow != null) {
        if (selectedRow.getValue() != null) {
            startEditingValue(selectedRow);
        } else if (selectedRow.getType() == FlatRow.RowType.ELEMENT) {
            startEditingElementName(selectedRow);
        }
        event.consume();
        return;
    }

    // Delegate to context menu for other shortcuts (Delete, Ctrl+C/X/V/D)
    if (selectedRow != null) {
        contextMenu.handleKeyPress(event, selectedRow.getModelNode());
    }
}
```

- [ ] **Step 3: Rewrite getSelectedNode and setSelectedNode**

Replace public selection methods with:

```java
public XmlNode getSelectedNode() {
    return selectedRow != null ? selectedRow.getModelNode() : null;
}

public void setSelectedNode(XmlNode node) {
    if (node == null) {
        selectRow(null);
        return;
    }
    for (FlatRow row : visibleRows) {
        if (row.getModelNode() == node) {
            selectRow(row);
            int idx = visibleRows.indexOf(row);
            if (idx >= 0) ensureRowVisible(idx);
            return;
        }
    }
}
```

- [ ] **Step 4: Rewrite expandAll and collapseAll**

Replace `expandAll()` and `collapseAll()` (line 2703-2721) with:

```java
public void expandAll() {
    for (FlatRow row : allRows) {
        if (row.isExpandable()) {
            row.setExpanded(true);
            row.setVisible(true);
        }
    }
    // Make all rows visible
    for (FlatRow row : allRows) {
        row.setVisible(true);
    }
    recalculateVisibleRows();
    layoutDirty = true;
    ensureLayout();
    updateScrollBars();
    render();
}

public void collapseAll() {
    for (FlatRow row : allRows) {
        if (row.isExpandable() && row.getParentRow() != null) {
            row.setExpanded(false);
        }
    }
    // Recalculate visibility — only root and its direct content visible
    for (FlatRow row : allRows) {
        if (row.getDepth() == 0) {
            row.setVisible(true);
        } else if (row.getParentRow() != null && row.getParentRow().getDepth() == 0
                   && row.getParentRow().isExpanded()) {
            row.setVisible(true);
        } else {
            row.setVisible(false);
        }
    }
    recalculateVisibleRows();
    layoutDirty = true;
    ensureLayout();
    updateScrollBars();
    render();
}
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "feat: wire up context menu, selection model, and expand/collapse for flat rows"
```

---

### Task 10: Clean Up Dead Code and Integration Test

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java`

- [ ] **Step 1: Remove dead methods from XmlCanvasView**

Remove the following methods that are no longer used (they were part of the nested grid rendering):
- `renderGridRecursively()`
- `renderGrid()`
- `drawGridHeader()`
- `drawAttributeRow()` (the old grid-based version)
- `drawTextContentRow()`
- `drawElementIcon()` (replaced by `drawRowIcon()`)
- `drawChildrenHeader()` (if it exists)
- `calculateSizes()` (the old nested version)
- `positionNodes()` (the old nested version)
- `findNodeAt()` (replaced by row index calculation)
- `findNodeAtPoint()`
- `findTableAt()`
- `selectNode()` (replaced by `selectRow()`)
- `selectTable()` (if no longer used)
- `selectPreviousSibling()`, `selectNextSibling()`, `selectParent()`, `selectFirstChild()`, `selectFirstNode()`, `selectLastVisibleNode()` (replaced by keyboard navigation on flat rows)
- `ensureNodeVisible()` (replaced by `ensureRowVisible()`)
- `flashHighlight()` and `flashHighlightByModel()` — rewrite to use FlatRow

Also remove the `NestedGridNode rootNode` field if no longer referenced.

- [ ] **Step 2: Rewrite flashHighlightByModel for flat rows**

```java
private FlatRow highlightedRow = null;

public void flashHighlightByModel(XmlNode modelNode) {
    for (FlatRow row : allRows) {
        if (row.getModelNode() == modelNode) {
            highlightedRow = row;
            render();

            PauseTransition pause = new PauseTransition(Duration.millis(1500));
            pause.setOnFinished(e -> {
                highlightedRow = null;
                render();
            });
            pause.play();
            return;
        }
    }
}
```

Update `drawRow()` to use `highlightedRow` instead of `highlightedNode`:

In the `drawRow()` method, the line `if (row == highlightedNode)` should be `if (row == highlightedRow)`.

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run the application and visually verify**

Run: `./gradlew run`

Open an XML file in the Unified Editor, switch to Graphic view. Verify:
1. Elements, attributes, and text appear as flat rows with correct icons
2. Expand/collapse bars work (click to toggle)
3. Tree connection lines are visible
4. Repeating elements show as inline tables with row numbers and column icons
5. Inline editing works (double-click on values)
6. Keyboard navigation works (arrow keys, Home, End)
7. Context menu appears on right-click
8. Undo/Redo still works after edits

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlCanvasView.java
git commit -m "refactor: remove dead nested-grid code, finalize flat row rendering"
```

---

### Task 11: Remove NestedGridNode

**Files:**
- Delete: `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/NestedGridNode.java`
- Modify: Any remaining references

- [ ] **Step 1: Search for remaining NestedGridNode references**

Run: `grep -r "NestedGridNode" src/main/java/ --include="*.java" -l`

For each file that still references `NestedGridNode`, update to use `FlatRow` instead or remove the reference if the code is no longer needed.

- [ ] **Step 2: Remove NestedGridNode.java**

Only do this after all references are removed and the build compiles.

- [ ] **Step 3: Verify build and tests pass**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove NestedGridNode, replaced by FlatRow"
```

---

### Task 12: Update and Run Tests

**Files:**
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java`
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/RepeatingElementsTableTest.java` (if affected)

- [ ] **Step 1: Add tests for repeating element detection in FlatRow**

Add to `FlatRowTest.java`:

```java
@Test
@DisplayName("Flatten detects repeating elements")
void testFlattenRepeatingElements() {
    var doc = new XmlDocument();
    var root = new XmlElement("Root");

    // Add 3 Person elements — should be grouped
    for (int i = 0; i < 3; i++) {
        var person = new XmlElement("Person");
        person.setAttribute("name", "Person" + i);
        root.addChild(person);
    }
    doc.addChild(root);

    List<FlatRow> rows = FlatRow.flatten(doc);

    // Should have: Root, then a single Person row (representing the group)
    long personRows = rows.stream()
        .filter(r -> r.getType() == FlatRow.RowType.ELEMENT && "Person".equals(r.getLabel()))
        .count();
    assertEquals(1, personRows, "Repeating elements should produce a single grouped row");

    FlatRow personRow = rows.stream()
        .filter(r -> "Person".equals(r.getLabel()))
        .findFirst().orElseThrow();
    assertEquals(3, personRow.getChildCount());
}

@Test
@DisplayName("Flatten handles mixed content")
void testFlattenMixedContent() {
    var doc = new XmlDocument();
    var root = new XmlElement("Root");
    root.addChild(new XmlText("some text"));
    root.addChild(new XmlElement("Child"));
    doc.addChild(root);

    List<FlatRow> rows = FlatRow.flatten(doc);

    boolean hasTextRow = rows.stream()
        .anyMatch(r -> r.getType() == FlatRow.RowType.TEXT);
    assertTrue(hasTextRow, "Mixed content should produce a separate text row");
}
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/FlatRowTest.java
git commit -m "test: add tests for repeating element detection and mixed content in FlatRow"
```
