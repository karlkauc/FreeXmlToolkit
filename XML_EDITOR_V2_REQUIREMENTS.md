# XML EDITOR V2 - COMPLETE REQUIREMENTS SPECIFICATION

**Document Version:** 1.0
**Date:** 2025-11-22
**Project:** FreeXmlToolkit - XML Editor V2 Reimplementation
**Architecture Pattern:** Based on XSD Editor V2 (controls/v2/)
**Author:** Requirements Analysis from Current Implementation + XMLSpy Grid View Analysis

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Current State Analysis](#2-current-state-analysis)
3. [Functional Requirements](#3-functional-requirements)
4. [Grid View Specification](#4-grid-view-specification)
5. [Performance Requirements](#5-performance-requirements)
6. [XSD Integration](#6-xsd-integration)
7. [Technical Architecture](#7-technical-architecture)
8. [Migration Strategy](#8-migration-strategy)
9. [Implementation Roadmap](#9-implementation-roadmap)
10. [Success Criteria](#10-success-criteria)

---

## 1. EXECUTIVE SUMMARY

### 1.1 Project Overview

**Objective:** Reimplementation of the XML Editor with modern architecture based on proven patterns from XSD Editor V2.

**Current State:** V1 XML Editor with 90+ features but architectural limitations (no undo/redo, direct DOM manipulation, performance constraints).

**Target State:** V2 XML Editor with 165+ features, clean Model-View-Command architecture, XMLSpy-style Grid View, and enterprise-grade performance.

### 1.2 Key Improvements

| Area | V1 Current | V2 Target |
|------|------------|-----------|
| **Undo/Redo** | ❌ None | ✅ 100-step command history |
| **Grid View** | ❌ None | ✅ XMLSpy-style table editing |
| **Performance** | ⚠️ 20MB limit | ✅ 1GB+ files supported |
| **Architecture** | ⚠️ Tight coupling | ✅ Model-View-Command separation |
| **XSD Integration** | ⚠️ Basic | ✅ Advanced (15+ type-aware editors) |
| **Sync** | ⚠️ Manual refresh | ✅ Automatic (< 50ms) |
| **Image Support** | ❌ None | ✅ Base64 thumbnails in grid |
| **Test Coverage** | ⚠️ ~40% | ✅ 80%+ target |

### 1.3 Success Metrics

**Performance Targets:**
- 100MB file: < 2s load time
- Edit operations: < 50ms latency
- Scroll performance: 60 FPS

**Feature Completeness:**
- All 90+ V1 features preserved
- 75+ new V2 features added
- **Total: 165+ features**

**Quality Targets:**
- 80%+ code coverage
- Zero critical bugs at release
- All automated tests passing

### 1.4 Timeline & Effort

**Duration:** 16 weeks (4 months)
**Estimated LOC:** 15,000-20,000 lines
**Risk Level:** Medium (following proven XSD Editor V2 architecture)

---

## 2. CURRENT STATE ANALYSIS

### 2.1 V1 Architecture Overview

**Code Statistics:**
```
XmlGraphicEditor:         3,533 lines  (Tree visualization)
XmlCodeEditor:            2,768 lines  (Text editing)
XmlEditor:                2,705 lines  (Coordinator)
XmlUltimateController:    2,261 lines  (Tab management)
XmlEditorSidebarController: ~800 lines  (XSD/Validation sidebar)
───────────────────────────────────────────────────────
Total Core:              ~12,000 lines
```

**Architecture Pattern:** MVC with Manager Pattern (separated concerns)

**Key Components:**
```
XmlUltimateController (Main Tab Controller)
├── XmlEditor (Tab Coordinator - per file)
│   ├── XML Tab → XmlCodeEditor (Text Mode)
│   │   ├── SyntaxHighlightManager
│   │   ├── XmlValidationManager
│   │   ├── FileOperationsManager
│   │   ├── XmlContextMenuManager
│   │   ├── StatusLineController
│   │   └── XmlCodeFoldingManager
│   └── Graphic Tab → XmlGraphicEditor (Visual Tree Mode)
│       ├── DOM-based tree rendering
│       ├── Context menus
│       └── Drag & drop
└── Sidebar (XmlEditorSidebarController)
    ├── XSD schema integration
    ├── Validation results
    ├── Element documentation
    └── Example values
```

### 2.2 V1 Complete Feature Inventory (90+ Features)

#### File Management (12 Features)
1. New file creation with templates
2. Open file with FileChooser
3. Drag & drop file opening (multiple XML files)
4. Tab deduplication (prevents opening same file twice)
5. Save / Save As with proper file tracking
6. Recent files tracking (via MainController)
7. Last directory memory (PropertiesService)
8. Empty state UI (shows when no tabs open)
9. Multi-tab editing (TabPane with XmlEditor tabs)
10. File modification tracking (dirty flag with asterisk)
11. Auto file reload detection (FileOperationsManager)
12. Per-file XSD/Schematron association

#### Text Editing (25+ Features)
13. RichTextFX CodeArea (high-performance text editing)
14. Line numbers with folding indicators
15. Syntax highlighting (async with debouncing)
16. Auto-indentation
17. Font size adjustment (default 11px, adjustable)
18. Virtualized scrolling (handles large files)
19. Context-sensitive element completion (based on XSD schema)
20. XPath-based context detection (knows current element)
21. Parent-child relationship mapping from XSD
22. Attribute completion (with enumeration values from XSD)
23. Enumeration value suggestions for simpleType restrictions
24. Auto-closing tags (`<element>` → `<element></element>`)
25. Self-closing tag detection (br, hr, img, input, etc.)
26. Enhanced completion popup (3-column layout: Name, Type, Documentation)
27. Fuzzy search in completion list
28. Quick actions integration
29. Schematron mode auto-completion (`<sch:rule>`, `<sch:assert>`, etc.)
30. XSLT mode auto-completion (`<xsl:template>`, `<xsl:apply-templates>`, etc.)
31. XSL-FO mode auto-completion (`<fo:block>`, `<fo:table>`, etc.)
32. Hierarchical element folding
33. Fold/unfold indicators (▶ ▼ icons)
34. Collapse all / Expand all commands
35. Folding state preservation during editing
36. Minimap integration (shows folded regions)
37. Find/Replace dialog (Ctrl+F, Ctrl+R)
38. Regex support in search
39. Case sensitive/insensitive search
40. Whole word matching
41. Result highlighting
42. Navigate between matches (Next/Previous)
43. Replace all

#### Graphical Tree Editor (30+ Features)
44. DOM-based tree rendering
45. Hierarchical element display
46. XMLSpy-inspired styling (colors, borders, shadows)
47. Element nodes (collapsible containers)
48. Text nodes (GridPane with name + value)
49. Attribute display (inline with elements)
50. Comment nodes (styled differently)
51. Expand/collapse buttons (▶ ▼)
52. Visual distinction (Element names: Blue, Attributes: Brown, Text: Black, Comments: Gray)
53. Add Child Element (to current node)
54. Add Sibling Before (same parent)
55. Add Sibling After (same parent)
56. Delete Node
57. Move Up (swap with previous sibling)
58. Move Down (swap with next sibling)
59. Add Attribute
60. Edit Attributes (dialog)
61. Copy Node (to clipboard as XML)
62. Copy Value (text content only)
63. Go to XSD Definition (navigates to schema)
64. Double-click text editing (text nodes and attribute values)
65. TextField for editing
66. Enter to confirm, Escape to cancel
67. Immediate DOM update on confirmation
68. Drag element nodes to reorder
69. Drop position indicators (BEFORE/AFTER/INSIDE)
70. Visual feedback during drag (cursor changes)
71. Drop zone highlighting
72. Constraint validation (can't drop on text nodes)
73. Ctrl+F search bar (integrated)
74. Search in element names, attribute names, attribute values, text content, comments
75. Auto-expand collapsed nodes to show results
76. Visual highlighting of matches
77. Navigate results (Enter/Shift+Enter)
78. Result count display (e.g., "5 of 12")
79. Escape to close search
80. Single-click selection (shows in sidebar)
81. Visual selection highlight (blue background)
82. Hover effects (light blue on hover)
83. Sidebar sync (selected element info displayed)

#### Validation (12 Features)
84. Automatic XSD detection from XML (`xsi:schemaLocation`)
85. Manual XSD file selection
86. Continuous validation (as you type)
87. On-demand validation (button)
88. Xerces validator (XSD 1.1 support)
89. Saxon validator (fallback)
90. Error display in sidebar (list with line numbers)
91. Click error to navigate to line
92. Well-formedness check (separate from schema)
93. Schematron file selection
94. Pure Schematron support (.sch)
95. XSLT-based Schematron (.xsl, .xslt)

#### XSD Integration (10 Features)
96. Element name and type at cursor
97. XPath of current position
98. Element documentation (from XSD `<annotation>`)
99. Example values from XSD
100. Allowed child elements list
101. Attribute list with types
102. SimpleType vs ComplexType indication
103. Built-in type detection (xs:string, xs:int, etc.)
104. Ctrl+Click to jump to XSD
105. Opens XSD tab in schema viewer

#### XPath/XQuery (8 Features)
106. Dual tabs (XPath and XQuery)
107. Saxon engine for execution
108. Execute button (or Ctrl+Enter)
109. Results replace XML in editor (with Reset button)
110. Comment removal before execution
111. Example queries (dropdown)
112. Syntax validation (before execution)
113. Error reporting (user-friendly messages)

#### XSLT Development (8 Features)
114. Split pane layout (XML Source + XSLT + Output)
115. Load/Save buttons for each editor
116. Transform button
117. Output format selector (XML, HTML, Text, JSON)
118. Live preview checkbox
119. WebView preview (for HTML output)
120. Performance metrics (transformation time, output size)
121. XsltTransformationEngine integration

#### Advanced Features (15+ Features)
122. Favorites system (Add to Favorites button)
123. Category organization (custom folders)
124. Favorites sidebar panel (toggleable)
125. Double-click to open from favorites
126. Cross-editor favorites (XML, XSD, Schematron)
127. XML ↔ Excel converter
128. XML ↔ CSV converter
129. Template library browser
130. Smart template system
131. Parameter-based generation
132. Template preview
133. Generate XSD from XML (SchemaGenerationEngine)
134. Intelligent type inference
135. Pretty Print / Format XML button (Ctrl+Alt+F)
136. Configurable indentation (default 2 spaces)
137. Preserves comments and CDATA
138. Whitespace normalization
139. Activity log with timestamps
140. Operation feedback in console
141. Error messages in console
142. Console clear button

**Total V1 Features:** 142 features

### 2.3 V1 Critical Limitations

**Architecture Issues:**
1. ❌ **No Undo/Redo** - Direct DOM manipulation (org.w3c.dom.Node), destructive operations
2. ❌ **No Command Pattern** - Changes cannot be reverted
3. ❌ **Tight Coupling** - Graphic view directly depends on DOM structure
4. ⚠️ **No Model-View Separation** - UI logic mixed with data logic

**Performance Issues:**
5. ⚠️ **File Size Limit** - 20MB max for formatting operations
6. ⚠️ **Deep Nesting** - May slow down graphic view
7. ⚠️ **Full Re-render** - No incremental updates

**Feature Gaps:**
8. ❌ **No Grid View** - Only tree visualization available
9. ⚠️ **Basic XSD Integration** - Only 1-level type resolution
10. ⚠️ **Limited Schema Support** - No DTD, no RelaxNG
11. ❌ **No Image Display** - Cannot show base64 images

**Testability:**
12. ⚠️ **UI-Dependent Tests** - Hard to test without JavaFX runtime
13. ⚠️ **Low Coverage** - ~40% code coverage

### 2.4 V1 Strengths (Must Preserve)

**Excellent Existing Features:**
- ✅ Manager Pattern (SyntaxHighlight, Validation, FileOps) - **Keep**
- ✅ IntelliSense Engine (context-aware completion) - **Keep & Enhance**
- ✅ Service Abstraction (XmlService, ValidationService) - **Keep**
- ✅ Feature Flag System (ready for V1/V2 toggle) - **Use**
- ✅ Async Operations (debouncing, threading) - **Keep & Improve**
- ✅ Multi-tab Editing - **Keep**
- ✅ Favorites System - **Keep**
- ✅ XPath/XQuery/XSLT Integration - **Keep**

---

## 3. FUNCTIONAL REQUIREMENTS

### FR-01: Dual-View Architecture with Permanent Sync

**Priority:** 🔴 CRITICAL
**Complexity:** High

**Description:**
Text-View and Graphic-View must be permanently synchronized. Changes in one view appear immediately (< 50ms) in the other view without user intervention.

**Architecture:**
```
XmlDocument (Model - Observable)
    ↓ PropertyChangeEvents
    ├→ XmlTextView (RichTextFX CodeArea)
    │  └─ PropertyChangeListener: onModelChanged()
    │     └─ Updates text, preserves cursor position
    │
    └→ XmlGraphicView (TreeView / GridView)
       └─ PropertyChangeListener: onModelChanged()
          └─ Updates tree/grid, preserves selection
```

**User Scenarios:**

**Scenario 1: Text → Graphic Sync**
```
GIVEN: User is in Text View
WHEN: User types "<name>John</name>"
THEN: Tree View updates to show new <name> element within 50ms
AND: Cursor position in Text View is preserved
AND: No flickering or visual glitches
```

**Scenario 2: Graphic → Text Sync**
```
GIVEN: User is in Graphic/Grid View
WHEN: User changes cell value from "John" to "Jane"
THEN: Text View updates XML to show "Jane" within 50ms
AND: Selection in Grid remains on same cell
AND: Dirty flag set to true
```

**Technical Requirements:**
- Model uses `PropertyChangeSupport` to fire events
- Views register as `PropertyChangeListener`
- Event propagation: Model → View (unidirectional)
- User edits: View → Command → Model
- Cursor/selection preservation on both sides

**Acceptance Criteria:**
- ✅ AC-01.1: Text-Änderung erscheint in < 50ms in Graphic-View
- ✅ AC-01.2: Graphic-Änderung erscheint in < 50ms in Text-View
- ✅ AC-01.3: Cursor-Position bleibt erhalten nach Sync
- ✅ AC-01.4: Selection bleibt erhalten nach Sync
- ✅ AC-01.5: Kein Flackern/Flash beim Sync
- ✅ AC-01.6: Undo/Redo synchronisiert beide Views

---

### FR-02: Grid View for Repeating Elements

**Priority:** 🔴 CRITICAL
**Complexity:** High
**Reference:** XMLSpy Grid View - https://www.altova.com/manual/XMLSpy/spyenterprise/xseditingviews_gridview.html

**Description:**
When XML contains repeating child elements (≥ 2 with same name), provide XMLSpy-style Grid View for tabular editing.

**Trigger Condition:**
```java
if (parentElement.getChildren()
    .stream()
    .collect(Collectors.groupingBy(XmlElement::getName))
    .values()
    .stream()
    .anyMatch(list -> list.size() >= 2)) {
    // Grid View available
}
```

**Example:**
```xml
<persons>
  <person id="1">
    <name>John</name>
    <age>30</age>
  </person>
  <person id="2">
    <name>Jane</name>
    <age>25</age>
  </person>
  <person id="3">
    <name>Bob</name>
    <age>35</age>
  </person>
</persons>
```

**Grid Display:**
```
┌────────────────────────────────────────────────────────┐
│ Element: <person> (3 rows)  [Tree View] [Grid View●]  │
├────┬───────┬────────────────┬─────────────────────────┤
│ #  │ @id   │ name           │ age                     │
├────┼───────┼────────────────┼─────────────────────────┤
│  1 │ 1     │ John           │ 30                      │
│  2 │ 2     │ Jane           │ 25                      │
│  3 │ 3     │ Bob            │ 35                      │
└────┴───────┴────────────────┴─────────────────────────┘
```

**Core Features:**
1. Automatic repeating element detection
2. Toggle button: Tree View ↔ Grid View
3. All attributes as columns (prefix: @)
4. Text-only child elements as columns
5. Complex child elements as expandable columns
6. Row # column (read-only, auto-renumbering)
7. Add Row / Delete Row / Duplicate Row buttons
8. Inline cell editing (double-click)
9. Sort by column (click header)
10. Filter rows (search field)
11. Column management (resize, reorder, hide/show)
12. Context menus (row and cell operations)
13. Export to CSV/Excel

**Acceptance Criteria:**
- ✅ AC-02.1: Automatische Erkennung wiederholender Elemente
- ✅ AC-02.2: Toggle-Button: Tree ↔ Grid
- ✅ AC-02.3: Alle Attribute als Spalten
- ✅ AC-02.4: Text-Child-Elemente als Spalten
- ✅ AC-02.5: Inline-Editing in Grid-Zellen
- ✅ AC-02.6: Add/Delete/Duplicate Row Buttons funktionieren
- ✅ AC-02.7: Sort by column funktioniert
- ✅ AC-02.8: Filter funktioniert
- ✅ AC-02.9: Export to CSV/Excel funktioniert

**See Section 4 for detailed Grid View specification**

---

### FR-03: Undo/Redo System

**Priority:** 🔴 CRITICAL
**Complexity:** Medium

**Description:**
All editing operations must be reversible using Command Pattern with 100-step history.

**Command Pattern:**
```java
public interface XmlCommand {
    boolean execute();           // Perform operation
    boolean undo();              // Reverse operation
    String getDescription();     // "Add element 'person'"
    boolean canUndo();           // Usually true
    boolean canMergeWith(XmlCommand other);  // For consecutive edits
    XmlCommand mergeWith(XmlCommand other);  // Merge logic
}
```

**CommandManager:**
```java
public class CommandManager {
    private final Deque<XmlCommand> undoStack = new ArrayDeque<>(100);
    private final Deque<XmlCommand> redoStack = new ArrayDeque<>(100);

    public void executeCommand(XmlCommand command);
    public void undo();  // Pop from undoStack, execute undo, push to redoStack
    public void redo();  // Pop from redoStack, execute again, push to undoStack
    public boolean canUndo();
    public boolean canRedo();
    public void clear();
}
```

**Commands (25+):**
1. AddElementCommand
2. DeleteNodeCommand
3. MoveNodeCommand (up/down)
4. RenameNodeCommand
5. EditTextCommand
6. AddAttributeCommand
7. EditAttributeCommand
8. DeleteAttributeCommand
9. DuplicateNodeCommand
10. PasteCommand (XML from clipboard)
11. CutCommand
12. FormatDocumentCommand
13. IndentCommand
14. InsertXmlCommand (fragment insertion)
15. WrapInElementCommand
16. UnwrapElementCommand
17. CommentNodeCommand
18. UncommentNodeCommand
19. AddCDataCommand
20. AddProcessingInstructionCommand
21. ChangeNamespaceCommand
22. SortChildrenCommand
23. MergeElementsCommand
24. SplitElementCommand
25. CompositeCommand (multiple operations as one)

**UI Integration:**
- Toolbar: [Undo ↶] [Redo ↷] buttons
- Shortcuts: Ctrl+Z (Undo), Ctrl+Y or Ctrl+Shift+Z (Redo)
- Menu: Edit → Undo / Redo with command description
- Optional: Command History Panel (list of executed commands)

**Acceptance Criteria:**
- ✅ AC-03.1: Alle Operationen durch Commands
- ✅ AC-03.2: Undo/Redo funktioniert zuverlässig
- ✅ AC-03.3: Command-Descriptions in UI anzeigen
- ✅ AC-03.4: Dirty-Flag korrekt bei Undo/Redo
- ✅ AC-03.5: 100 Schritte History
- ✅ AC-03.6: Command-Merging für consecutive text edits

---

### FR-04: XSD Schema Integration

**Priority:** 🔴 CRITICAL
**Complexity:** High

**Description:**
When XSD schema is linked, editor must provide schema-aware editing, validation, and intelligent suggestions.

**4.1 Schema Auto-Detection:**
```xml
<!-- Method 1: xsi:schemaLocation -->
<root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://example.com/schema schema.xsd">
</root>

<!-- Method 2: xsi:noNamespaceSchemaLocation -->
<root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:noNamespaceSchemaLocation="schema.xsd">
</root>

<!-- Method 3: Convention (<file>.xml → <file>.xsd) -->
data.xml → looks for data.xsd in same directory
```

**4.2 Smart Element Insertion:**

**Without Schema:**
```
Dialog: "Element name: _________"
        [OK] [Cancel]
```

**With Schema:**
```
┌─────────────────────────────────────────┐
│ Add Child Element to <person>          │
├─────────────────────────────────────────┤
│ Required Elements (missing):            │
│ ☐ firstName (xs:string)      ★ Required│
│ ☐ lastName  (xs:string)      ★ Required│
│                                         │
│ Optional Elements:                      │
│ ☐ birthDate (xs:date)        Optional  │
│ ☐ email     (xs:string)      Optional  │
│ ☐ address   (AddressType)    Optional  │
│                                         │
│ [✓] Insert all required elements       │
│                                         │
│ [ Insert ] [ Cancel ]                  │
└─────────────────────────────────────────┘
```

**4.3 Type-Aware Validation:**
```
<age>thirty</age>  ⚠️ "Invalid value 'thirty' for type 'xs:int'"
<age>30</age>      ✅ Valid

<email>john.doe</email>        ⚠️ "Invalid email format"
<email>john.doe@example.com</email>  ✅ Valid
```

**4.4 Schema Templates:**
```
User: Right-click → "Insert from Schema: Person"

Inserted:
<person id="">
  <firstName></firstName>
  <lastName></lastName>
</person>

Cursor: positioned in "id" attribute (first required field)
```

**Acceptance Criteria:**
- ✅ AC-04.1: Schema auto-detection funktioniert
- ✅ AC-04.2: Element-Vorschläge basierend auf Schema
- ✅ AC-04.3: Attribute-Vorschläge basierend auf Schema
- ✅ AC-04.4: Datentyp-Validation in Echtzeit
- ✅ AC-04.5: Enumeration als Dropdown
- ✅ AC-04.6: Pattern-Validation mit Fehlermeldung
- ✅ AC-04.7: Required vs Optional visuell unterscheiden
- ✅ AC-04.8: Schema-Templates für schnelles Einfügen

**See Section 6 for detailed XSD Integration specification**

---

### FR-05: Performance for Large Files

**Priority:** 🔴 CRITICAL
**Complexity:** High

**Description:**
Editor must handle very large XML files (100MB+) with acceptable performance.

**Performance Targets:**

| File Size | Load Time | Edit Latency | Scroll FPS | Memory |
|-----------|-----------|--------------|------------|--------|
| 1 MB      | < 0.5s    | < 20ms       | 60 FPS     | < 50 MB |
| 10 MB     | < 1s      | < 30ms       | 60 FPS     | < 200 MB |
| 100 MB    | < 2s      | < 50ms       | 60 FPS     | < 1 GB |
| 500 MB    | < 10s     | < 100ms      | 30 FPS     | < 2 GB |
| 1 GB      | < 20s     | < 200ms      | 30 FPS     | < 4 GB |

**Optimization Strategies:**

**1. Virtualized Rendering:**
- Text View: RichTextFX already virtualized (built-in)
- Tree View: JavaFX TreeView with `fixedCellSize` (virtualizes cells)
- Grid View: JavaFX TableView with `fixedCellSize` (renders only visible rows)

**2. Lazy Loading:**
```java
class XmlElement {
    private List<XmlNode> children;
    private boolean childrenLoaded = false;

    public List<XmlNode> getChildren() {
        if (!childrenLoaded) {
            loadChildrenLazy();  // Load from disk/cache
            childrenLoaded = true;
        }
        return children;
    }
}
```

**3. Incremental Parsing:**
```java
CompletableFuture<XmlDocument> parseAsync(Path file) {
    return CompletableFuture.supplyAsync(() -> {
        SAXParser parser = ...;
        // Parse in background, update UI every 1000 elements
        parser.parse(file, new ProgressiveHandler());
    }, executor);
}
```

**4. Debouncing:**
- Syntax Highlighting: 300ms debounce
- Validation: 500ms debounce
- Auto-save: 2s debounce

**5. Schema Caching:**
```java
Map<Path, XsdSchema> schemaCache;
// Compiled schemas cached, reloaded only when file changes
```

**6. Incremental Updates:**
- Only changed nodes re-render
- Preserve scroll position and selection
- Differential text updates (replace only changed ranges)

**Acceptance Criteria:**
- ✅ AC-05.1: 100MB Datei in < 2s laden
- ✅ AC-05.2: Edit-Operation in < 50ms
- ✅ AC-05.3: 60 FPS bei Scrolling (< 100MB files)
- ✅ AC-05.4: Keine Dateigröße-Limits (remove 20MB constraint)
- ✅ AC-05.5: Memory Usage < 4GB für 1GB Datei
- ✅ AC-05.6: Progress indication für große Dateien
- ✅ AC-05.7: Cancelable loading operations

**See Section 5 for detailed Performance Architecture**

---

### FR-06: Feature Flag (V1 ↔ V2 Toggle)

**Priority:** 🟡 MEDIUM
**Complexity:** Low

**Description:**
Users must be able to switch between V1 and V2 editors.

**Property:**
```properties
# FreeXmlToolkit.properties
xml.editor.use.v2=false    # Default: V1 (stable)
xml.editor.use.v2=true     # V2 (new features)
```

**Preferences Dialog:**
```
┌─────────────────────────────────────────┐
│ XML Editor Settings                     │
├─────────────────────────────────────────┤
│ Editor Version:                         │
│                                         │
│ ○ Version 1 (Classic)                   │
│   Stable, production-ready              │
│   Features: Text, Tree editing          │
│                                         │
│ ● Version 2 (Modern)                    │
│   New features: Undo/Redo, Grid View,   │
│   Image Display, improved performance   │
│                                         │
│ ⚠️ Note: New tabs will use selected    │
│         version. Open tabs unchanged.   │
│                                         │
│ [Apply] [OK] [Cancel]                   │
└─────────────────────────────────────────┘
```

**Behavior:**
- Existing open tabs keep their version (V1 or V2)
- Newly opened tabs use the property setting
- Services shared (XmlService, ValidationService, etc.)
- V1 code remains untouched (no breaking changes)

**Acceptance Criteria:**
- ✅ AC-06.1: Umschaltung über Preferences Dialog
- ✅ AC-06.2: Neue Tabs verwenden neue Einstellung
- ✅ AC-06.3: Offene Tabs funktionieren weiter
- ✅ AC-06.4: V1 bleibt unverändert (zero breaking changes)
- ✅ AC-06.5: Setting persistiert über Neustarts

---

### FR-07: Add All Node Types

**Priority:** 🟡 MEDIUM
**Complexity:** Medium

**Description:**
Users must be able to add all XML node types.

**7.1 Element with Text Content:**
```xml
<name>John Doe</name>
```

**Dialog:**
```
┌─────────────────────────────────────────┐
│ Add Element                             │
├─────────────────────────────────────────┤
│ Element Name: [name____________]        │
│                                         │
│ Content Type:                           │
│ ● Text Content                          │
│ ○ Child Elements                        │
│ ○ Empty                                 │
│                                         │
│ Text Value: [John Doe___________]       │
│                                         │
│ [Add] [Cancel]                          │
└─────────────────────────────────────────┘
```

**7.2 Element with Children:**
```xml
<person>
  <name>John</name>
  <age>30</age>
</person>
```

**Workflow:**
1. Add Element "person" (Content Type: Child Elements)
2. Auto-switch to new element in Tree View
3. Add Child "name", Add Child "age"

**7.3 Attributes:**
```xml
<person id="p001" status="active">
```

**Property Panel:**
```
┌─────────────────────────────────────────┐
│ Attributes for <person>                 │
├─────────────────────────────────────────┤
│ id      [p001____________]    [Delete]  │
│ status  [active__________]    [Delete]  │
│                                         │
│ [+] Add Attribute ▼                     │
│     Suggestions from schema:            │
│     - class                             │
│     - style                             │
│     - lang                              │
└─────────────────────────────────────────┘
```

**7.4 Other Node Types:**
- Comments: `<!-- comment -->`
- CDATA: `<![CDATA[data]]>`
- Processing Instructions: `<?target data?>`

**Acceptance Criteria:**
- ✅ AC-07.1: Add Element mit Text-Content
- ✅ AC-07.2: Add Element mit Child-Elements
- ✅ AC-07.3: Add Empty Element
- ✅ AC-07.4: Add Attribute (inline + dialog)
- ✅ AC-07.5: Delete Attribute
- ✅ AC-07.6: Edit Attribute inline
- ✅ AC-07.7: Add Comment
- ✅ AC-07.8: Add CDATA
- ✅ AC-07.9: Add Processing Instruction

---

### FR-08: All V1 Features Preserved

**Priority:** 🔴 CRITICAL
**Complexity:** High

**Description:**
All 142 V1 features must be available in V2.

**Feature Categories:**
- File Management (12 features) - **100% preserved**
- Text Editing (25+ features) - **100% preserved + enhanced**
- Graphic Tree Editing (30+ features) - **100% preserved + enhanced**
- Validation (12 features) - **100% preserved + enhanced**
- XSD Integration (10 features) - **Enhanced to 15+ features**
- XPath/XQuery (8 features) - **100% preserved**
- XSLT Development (8 features) - **100% preserved**
- Advanced Features (15+ features) - **100% preserved**

**Migration Strategy:**
- Reuse existing services (XmlService, XsdDocumentationService, etc.)
- Reimplement UI with new architecture
- Keep all existing functionality
- Add new features on top

**Acceptance Criteria:**
- ✅ AC-08.1: Alle 142 V1 Features funktionieren in V2
- ✅ AC-08.2: Feature-Parität erreicht vor V2 Release
- ✅ AC-08.3: Keine Regression-Bugs
- ✅ AC-08.4: Automated regression test suite

---

### FR-09: Keyboard Shortcuts

**Priority:** 🟡 MEDIUM
**Complexity:** Low

**Description:**
All operations must be accessible via keyboard.

**Global Shortcuts:**

| Shortcut | Action |
|----------|--------|
| Ctrl+N | New File |
| Ctrl+O | Open File |
| Ctrl+S | Save |
| Ctrl+Shift+S | Save As |
| Ctrl+W | Close Tab |
| Ctrl+Z | Undo |
| Ctrl+Y / Ctrl+Shift+Z | Redo |
| Ctrl+F | Find |
| Ctrl+R | Replace |
| Ctrl+G | Go to Line |
| Ctrl+D | Duplicate Node |
| Ctrl+X | Cut Node |
| Ctrl+C | Copy Node |
| Ctrl+V | Paste Node |
| Del | Delete Node |
| F2 | Rename |
| Ctrl+Space | IntelliSense |
| Ctrl+Alt+F | Format Document |
| Ctrl+Click | Go to Definition |
| Alt+Up/Down | Move Node Up/Down |
| Ctrl+Alt+Up/Down | Expand/Collapse |
| Ctrl+Shift+G | Toggle Grid View |
| F5 | Validate |
| Ctrl+Shift+V | Paste as XML |
| Ctrl+/ | Comment/Uncomment |
| Ctrl+Shift+F | Format Selection |

**Grid View Shortcuts:**

| Shortcut | Action |
|----------|--------|
| Tab | Next Cell |
| Shift+Tab | Previous Cell |
| Enter | Confirm Edit + Next Row |
| Escape | Cancel Edit |
| Ctrl+Insert | Add Row |
| Ctrl+Delete | Delete Row |
| Ctrl+D | Duplicate Row |

**Acceptance Criteria:**
- ✅ AC-09.1: Alle Shortcuts dokumentiert
- ✅ AC-09.2: Shortcuts konsistent mit V1
- ✅ AC-09.3: Keine Konflikte zwischen Shortcuts
- ✅ AC-09.4: Tooltips zeigen Shortcuts

---

### FR-10: Accessibility

**Priority:** 🟢 LOW
**Complexity:** Medium

**Description:**
Editor must be accessible to users with disabilities.

**Features:**
- Screen Reader Support (ARIA labels on all controls)
- High Contrast Mode (respects OS settings)
- Keyboard Navigation (no mouse required)
- Tooltips on all buttons/icons
- Status Updates (announced to screen readers)
- Adjustable Font Sizes
- Color-blind friendly colors

**Acceptance Criteria:**
- ✅ AC-10.1: WCAG 2.1 Level AA compliance
- ✅ AC-10.2: Screen reader tested (NVDA, JAWS)
- ✅ AC-10.3: Full keyboard navigation
- ✅ AC-10.4: High contrast mode supported

---

### FR-11: Enhanced Entry Helpers (NEW)

**Priority:** 🔴 CRITICAL
**Complexity:** High
**Source:** XMLSpy Grid View Analysis

**Description:**
Provide intelligent, type-aware editors for all XSD built-in types and patterns.

**Extended Type Mappings:**

| XSD Type | UI Component | Additional Helper | Validation |
|----------|--------------|-------------------|------------|
| xs:string | TextField | - | - |
| xs:int | TextField | Number Spinner | Integer only |
| xs:decimal | TextField | Number Spinner | Decimal format |
| xs:boolean | CheckBox | - | true/false |
| xs:date | DatePicker | Calendar popup | YYYY-MM-DD |
| xs:time | TextField | Time picker | HH:MM:SS |
| xs:dateTime | DateTimePicker | Date+Time picker | ISO 8601 |
| xs:duration | TextField | Format helper | P1Y2M3DT4H5M6S |
| xs:gYear | Spinner | Year selector | 0001-9999 |
| xs:gMonth | Spinner | Month selector | 01-12 |
| xs:gDay | Spinner | Day selector | 01-31 |
| **xs:anyURI** | TextField | **"Browse File" Button** | URI validation |
| **xs:IDREF** | ComboBox | **Auto-populate with all IDs** | Must reference existing ID |
| **xs:IDREFS** | Multi-Select ListBox | **All IDs, multiple selection** | Space-separated IDs |
| **xs:QName** | TextField | **Namespace prefix dropdown** | Prefix:LocalName |
| **xs:base64Binary** | TextArea | **"Import File" Button** | Base64 encode file |
| **xs:hexBinary** | TextField | Hex validator | Hex digits only |
| Enumeration | ComboBox | Values from schema | One of enum values |
| Pattern (Email) | TextField | **Format hint below field** | Live validation |
| Pattern (Phone) | TextField | **Format mask** | Pattern match |
| Pattern (URL) | TextField | **"Open" Button** | URL validation |
| Pattern (Postal Code) | TextField | **Format hint (e.g., 12345)** | Pattern match |
| Pattern (Credit Card) | TextField | **Format mask (XXXX-XXXX-XXXX-XXXX)** | Luhn algorithm |

**Example: xs:IDREF Editor**
```xml
<!-- Document has these elements with IDs -->
<person id="p001">...</person>
<person id="p002">...</person>
<person id="p003">...</person>

<!-- User edits this element -->
<reference personRef="">
  ↓ personRef has type xs:IDREF
  ↓ Editor shows ComboBox with:

  ┌──────────────┐
  │ p001         │
  │ p002         │
  │ p003         │  ← Auto-populated from document
  └──────────────┘
```

**Example: xs:base64Binary with File Import**
```
Grid Cell / Property Panel:
┌────────────────────────────────────┐
│ image (xs:base64Binary)            │
│ ┌────────────────────────────────┐ │
│ │ iVBORw0KGgoAAAANSUhEUgAAAAUA...││
│ │ ...                            ││
│ └────────────────────────────────┘ │
│ [Import File...] [Clear]           │
└────────────────────────────────────┘

Click "Import File":
→ Opens FileChooser
→ User selects image.png
→ File content encoded to Base64
→ Inserted into field
```

**Example: Pattern with Format Hints**
```
Pattern: [A-Z]{2}\d{4}  (e.g., "AB1234")

TextField:
┌─────────────────────────────┐
│ AB1234_                     │ ← User input
└─────────────────────────────┘
ℹ️ Format: 2 uppercase letters + 4 digits (e.g., AB1234)

Invalid input → Red border + error tooltip
```

**Acceptance Criteria:**
- ✅ AC-11.1: Alle XSD Built-in Types haben passende Editoren
- ✅ AC-11.2: Pattern-basierte Validierung mit Format-Hints
- ✅ AC-11.3: IDREF zeigt nur gültige Referenzen aus Dokument
- ✅ AC-11.4: Base64-Felder mit File-Import
- ✅ AC-11.5: QName mit Namespace-Präfix Dropdown
- ✅ AC-11.6: anyURI mit "Browse File" Button
- ✅ AC-11.7: Duration mit Format-Helper (ISO 8601)
- ✅ AC-11.8: Email/Phone/URL Patterns mit Live-Validation

---

### FR-12: Image Display in Grid (NEW)

**Priority:** 🟡 MEDIUM
**Complexity:** Medium
**Source:** XMLSpy Grid View Analysis

**Description:**
Display Base64-encoded images as thumbnails in Grid View.

**Trigger Conditions:**
1. Element contains Base64-encoded data
2. Schema defines `xs:base64Binary` type
3. Auto-detection: Data starts with image signature:
   - PNG: `iVBORw0KG...`
   - JPEG: `/9j/4AAQ...`
   - GIF: `R0lGOD...`
   - BMP: `Qk0...`

**Grid Display:**
```
Column: "image"
┌──────────────────────┐
│ [🖼️ 64x64 Thumbnail] │
│                      │
│ (Click to enlarge)   │
└──────────────────────┘
```

**Features:**

**1. Thumbnail Rendering:**
- Size: 64x64 or 128x128 (user configurable)
- Aspect ratio preserved
- Fallback icon for invalid/corrupt images

**2. Full-Size Preview:**
```
Click thumbnail → Opens dialog:

┌──────────────────────────────────────┐
│ Image Preview                        │
├──────────────────────────────────────┤
│                                      │
│      [Full-Size Image]               │
│                                      │
│ Original Size: 1024x768              │
│ Format: PNG                          │
│ Base64 Length: 123,456 bytes         │
│                                      │
│ [Save As...] [Copy] [Close]          │
└──────────────────────────────────────┘
```

**3. Context Menu:**
```
Right-click thumbnail:
┌────────────────────────┐
│ 🔍 View Full Size      │
│ 💾 Save Image As...    │
│ 📋 Copy Image          │
│ 🔄 Replace Image...    │
│ 🗑️ Clear Image         │
└────────────────────────┘
```

**4. Import Image:**
```
Property Panel / Grid Cell:
┌────────────────────────────────────┐
│ image (xs:base64Binary)            │
│ ┌────────────────────────────────┐ │
│ │ [🖼️ 128x128 Preview]          ││
│ └────────────────────────────────┘ │
│ [Import...] [Clear]                │
└────────────────────────────────────┘

Click "Import":
→ FileChooser (PNG, JPEG, GIF, BMP, SVG)
→ Load file bytes
→ Encode to Base64
→ Set in model
→ Thumbnail appears
```

**5. Supported Formats:**
- PNG (Portable Network Graphics)
- JPEG (Joint Photographic Experts Group)
- GIF (Graphics Interchange Format)
- BMP (Bitmap)
- SVG (Scalable Vector Graphics) - rendered via JavaFX

**6. Performance:**
- Lazy loading (load thumbnail only when visible)
- Caching (decoded image cached)
- Background decoding (async)

**Example XML:**
```xml
<product>
  <name>Widget</name>
  <image>iVBORw0KGgoAAAANSUhEUgAAAAUA...</image>
</product>

Grid View:
┌────┬─────────┬──────────────────┐
│ #  │ name    │ image            │
├────┼─────────┼──────────────────┤
│  1 │ Widget  │ [🖼️ Thumbnail]   │
└────┴─────────┴──────────────────┘
```

**Acceptance Criteria:**
- ✅ AC-12.1: Base64-Bilder als Thumbnails in Grid
- ✅ AC-12.2: Click öffnet Full-Size Preview Dialog
- ✅ AC-12.3: "Import Image" Button konvertiert zu Base64
- ✅ AC-12.4: Context Menu: Save, Copy, Replace, Clear
- ✅ AC-12.5: Unterstützt PNG, JPEG, GIF, BMP, SVG
- ✅ AC-12.6: Fallback für ungültige/korrupte Bilder
- ✅ AC-12.7: Lazy Loading + Caching für Performance
- ✅ AC-12.8: Konfigurierbare Thumbnail-Größe (Settings)

---

### FR-13: Grid View Settings Dialog (NEW)

**Priority:** 🟡 MEDIUM
**Complexity:** Low
**Source:** XMLSpy Grid View Analysis

**Description:**
Provide user-configurable settings for Grid View appearance and behavior.

**Settings Dialog:**
```
Menu: View → Grid View Settings

┌─────────────────────────────────────────┐
│ Grid View Settings                      │
├─────────────────────────────────────────┤
│ Display:                                │
│ ☑ Show row numbers                      │
│ ☑ Show element names in column headers  │
│ ☑ Auto-size columns to fit content      │
│ ☑ Highlight current cell                │
│ ☑ Zebra striping (alternating colors)   │
│ ☑ Show grid lines                       │
│ ☑ Freeze first column (Row #)           │
│ ☐ Show tooltips on truncated values     │
│                                         │
│ Appearance:                             │
│ Font Family: [Consolas        ▼]        │
│ Font Size:   [12              ▼] px     │
│ Row Height:  [25              ] px      │
│ Image Size:  [64  ○ 128  ○ 256] px     │
│                                         │
│ Formatting:                             │
│ Date format:    [yyyy-MM-dd   ▼]        │
│                  (ISO 8601, US, EU, ...)│
│ Number format:  [#,##0.00     ▼]        │
│                  (1,234.56 or 1.234,56) │
│ Boolean format: [true/false   ▼]        │
│                  (1/0, yes/no, on/off)  │
│ Null display:   [(empty)      ▼]        │
│                  ((empty), NULL, -)     │
│                                         │
│ Behavior:                               │
│ ☑ Enter key moves to next row           │
│ ☑ Tab key moves to next column          │
│ ☑ Auto-save after cell edit             │
│ ☐ Confirm before deleting rows          │
│                                         │
│ [ OK ] [ Apply ] [ Cancel ] [ Reset ]   │
└─────────────────────────────────────────┘
```

**Settings Persistence:**
```properties
# FreeXmlToolkit.properties
xml.editor.v2.grid.show.row.numbers=true
xml.editor.v2.grid.show.element.names=true
xml.editor.v2.grid.auto.size.columns=true
xml.editor.v2.grid.highlight.current.cell=true
xml.editor.v2.grid.zebra.striping=true
xml.editor.v2.grid.show.grid.lines=true
xml.editor.v2.grid.freeze.first.column=true
xml.editor.v2.grid.font.family=Consolas
xml.editor.v2.grid.font.size=12
xml.editor.v2.grid.row.height=25
xml.editor.v2.grid.image.size=64
xml.editor.v2.grid.date.format=yyyy-MM-dd
xml.editor.v2.grid.number.format=#,##0.00
xml.editor.v2.grid.boolean.format=true/false
xml.editor.v2.grid.enter.moves.to.next.row=true
xml.editor.v2.grid.tab.moves.to.next.column=true
```

**Features:**

**1. Immediate Application:**
- "Apply" button: applies settings without closing dialog
- Changes immediately visible in all Grid View instances
- No restart required

**2. Reset to Defaults:**
- "Reset" button restores all settings to factory defaults
- Confirmation dialog: "Reset all Grid View settings to defaults?"

**3. Per-User Settings:**
- Stored in user-specific properties file
- Not shared across users
- Portable (copy properties file to new installation)

**Acceptance Criteria:**
- ✅ AC-13.1: Settings Dialog verfügbar über Menu
- ✅ AC-13.2: Alle Einstellungen persistiert
- ✅ AC-13.3: "Apply"-Button wendet Änderungen sofort an
- ✅ AC-13.4: Alle Grid-Instanzen übernehmen Settings
- ✅ AC-13.5: "Reset"-Button stellt Defaults wieder her
- ✅ AC-13.6: Font Family/Size anpassbar
- ✅ AC-13.7: Date/Number/Boolean Formats anpassbar
- ✅ AC-13.8: Zebra Striping toggle funktioniert
- ✅ AC-13.9: Image Size konfigurierbar (64/128/256)

---

### FR-14: Tree + Grid Split View (NEW - UPDATED from Tabs)

**Priority:** 🔴 CRITICAL
**Complexity:** Medium
**Source:** XMLSpy Grid View Analysis

**Description:**
Display Tree View and Grid View side-by-side instead of as tabs, with bidirectional synchronization.

**Layout Change:**

**BEFORE (Original Requirements - Tabs):**
```
┌────────────────────────────────┐
│ [Tree View] [Grid View]  ← Tabs
├────────────────────────────────┤
│                                │
│    (nur eine View sichtbar)    │
│                                │
└────────────────────────────────┘
```

**AFTER (XMLSpy-Style - Split):**
```
┌────────────┬───────────────────┐
│ Tree View  │ Grid View         │
│            │                   │
│ ├─ root    │ ┌──┬────┬────┐   │
│ │  ├─ item◄┼─┤1 │name│val │   │
│ │  ├─ item │ │2 │B   │20  │   │
│ │  └─ item │ └──┴────┴────┘   │
│            │                   │
│ [◀ Hide]   │ [⚙ Settings]     │
└────────────┴───────────────────┘
       ↕ Resizable Divider
```

**Features:**

**1. Horizontal SplitPane:**
- Left: Tree View (default 30% width)
- Right: Grid View (default 70% width)
- Resizable divider (drag to adjust)
- Divider position persisted

**2. Tree View Panel:**
- Hierarchical element display
- Expand/collapse controls
- Selection highlighting
- "Hide" button (collapses tree to left edge)
- Tooltip on collapsed tree: "Show Tree (Ctrl+T)"

**3. Grid View Panel:**
- Table display of repeating elements
- Toolbar: Add Row, Delete Row, Settings
- Filter/Search field
- Column headers with sort indicators

**4. Bidirectional Sync:**

**Tree → Grid:**
```
GIVEN: User clicks element in Tree View
WHEN: Element has repeating children
THEN: Grid View scrolls to corresponding row
AND: Row is highlighted
```

**Grid → Tree:**
```
GIVEN: User clicks row in Grid View
WHEN: Row represents element
THEN: Tree View expands to show element
AND: Element is selected/highlighted in tree
```

**5. Collapse/Expand Tree:**
```
Tree Collapsed:
┌──┬──────────────────────────┐
│▶ │ Grid View                │
│  │                          │
│T │ ┌──┬────┬────┐           │
│r │ │1 │name│val │           │
│e │ │2 │B   │20  │           │
│e │ └──┴────┴────┘           │
│  │                          │
│  │ [⚙ Settings]             │
└──┴──────────────────────────┘
   ← Click arrow to expand
```

**6. Keyboard Navigation:**
- Ctrl+T: Toggle Tree Visibility
- Ctrl+1: Focus Tree View
- Ctrl+2: Focus Grid View
- Tab: Switch between Tree and Grid

**7. Layout Modes:**

**Mode 1: Tree + Grid (Default)**
- Both visible, synchronized
- Best for understanding structure while editing data

**Mode 2: Grid Only**
- Tree hidden (collapsed to left edge)
- Maximum space for grid editing
- Quick toggle back with Ctrl+T or click ▶

**Mode 3: Tree Only**
- Grid can be minimized (drag divider to right)
- Traditional tree editing mode

**Acceptance Criteria:**
- ✅ AC-14.1: Tree und Grid gleichzeitig sichtbar
- ✅ AC-14.2: Bidirektionale Sync funktioniert
- ✅ AC-14.3: Resizable Divider, Position persistiert
- ✅ AC-14.4: Tree collapsible mit "Hide" Button
- ✅ AC-14.5: Keyboard Shortcuts (Ctrl+T, Ctrl+1, Ctrl+2)
- ✅ AC-14.6: Tree → Grid: Scroll to row, Highlight
- ✅ AC-14.7: Grid → Tree: Expand + Select element
- ✅ AC-14.8: Default Split: 30/70 (Tree/Grid)

---

## 4. GRID VIEW SPECIFICATION

**Complete Grid View specification moved to separate document:**
**See:** `XML_EDITOR_V2_GRID_VIEW.md`

**Summary:**
- XMLSpy-style table editing
- Auto-column detection (attributes, text children, complex children)
- Inline editing with type-aware editors
- Row operations (Add, Delete, Duplicate, Move, Sort)
- Column management (Resize, Reorder, Hide/Show, Freeze)
- Image display (Base64 thumbnails)
- Grid Settings Dialog
- XSD Integration (validation, enumerations, types)
- Performance optimization (virtualization, lazy loading)
- Export to CSV/Excel

---

## 5. PERFORMANCE REQUIREMENTS

**Complete performance architecture specification moved to separate document:**
**See:** `XML_EDITOR_V2_ARCHITECTURE.md` (Section: Performance Architecture)

**Summary:**
- Virtualized rendering (Text, Tree, Grid)
- Lazy loading (children on-demand)
- Incremental parsing (large files in background)
- Debouncing (syntax highlight, validation)
- Schema caching
- Incremental updates (only changed nodes)
- Memory management (string interning, flyweight, weak references)
- Threading model (IO, CPU, Scheduler pools)

---

## 6. XSD INTEGRATION

**Complete XSD integration specification moved to separate document:**
**See:** `XML_EDITOR_V2_XSD_INTEGRATION.md`

**Summary:**
- Schema auto-detection
- Schema caching
- Smart element insertion
- Enhanced type-aware editors (15+ mappings)
- Real-time validation
- Quick fixes
- Schema templates
- Attribute management
- IntelliSense integration

---

## 7. TECHNICAL ARCHITECTURE

**Complete technical architecture specification moved to separate document:**
**See:** `XML_EDITOR_V2_ARCHITECTURE.md`

**Summary:**
- Model Layer (XmlNode hierarchy, 10+ classes)
- Command Pattern (25+ commands)
- View Layer (Text, Tree, Grid)
- Editor Context & Managers
- Serialization Layer
- Validation Layer
- IntelliSense Layer
- Schema Integration Layer
- Performance Layer

---

## 8. MIGRATION STRATEGY

**Complete migration strategy moved to separate document:**
**See:** `XML_EDITOR_V2_REQUIREMENTS.md` (This document, Section 8)

### 8.1 Feature Flag System

**Property:**
```properties
# FreeXmlToolkit.properties
xml.editor.use.v2=false    # Default: V1 (stable)
```

**Editor Factory:**
```java
public class XmlEditorFactory {
    public XmlEditor createEditor(Path xmlFile) {
        if (propertiesService.useXmlEditorV2()) {
            return new XmlEditorV2(xmlFile);  // New architecture
        } else {
            return new XmlEditor(xmlFile);    // V1 (unchanged)
        }
    }
}
```

### 8.2 Parallel Development

**Package Structure:**
```
controls/                   # V1 (existing, untouched)
├── XmlEditor.java
├── XmlCodeEditor.java
├── XmlGraphicEditor.java
└── ...

controls/v2/xmleditor/      # V2 (new)
├── model/
├── commands/
├── view/
├── editor/
├── serialization/
├── validation/
├── intellisense/
├── schema/
└── performance/
```

**Shared Services:**
- XmlService
- XmlValidationService
- XsdDocumentationService
- SchematronService
- ThreadPoolManager
- PropertiesService

### 8.3 Rollback Strategy

**Immediate Rollback:**
```properties
# Emergency: revert to V1
xml.editor.use.v2=false
```

**User Rollback:**
- Preferences → XML Editor → Version 1

**No Data Migration:**
- Both V1 and V2 use standard XML
- Files interchangeable
- No proprietary format

---

## 9. IMPLEMENTATION ROADMAP

**Complete implementation roadmap moved to separate document:**
**See:** `XML_EDITOR_V2_ROADMAP.md`

**Summary - 16 Weeks:**
- Phase 1-2: Model Layer + Command Pattern (Week 1-4)
- Phase 3-4: Serialization + Editor Context (Week 5-6)
- Phase 5-7: View Layer (Text, Tree, Grid) (Week 7-9)
- Phase 8-9: XSD Integration + Validation (Week 10-11)
- Phase 10: Enhanced Features (Entry Helpers, Images, Settings, Split) (Week 12)
- Phase 11: Performance Optimization (Week 13)
- Phase 12-13: Testing & Bug Fixes (Week 14-15)
- Phase 14: Documentation & Release (Week 16)

---

## 10. SUCCESS CRITERIA

### 10.1 Functional Success Criteria

**Critical Features:**
- ✅ FC-01: Dual-view sync (Text ↔ Graphic/Grid) in < 50ms
- ✅ FC-02: Undo/Redo for all operations (100 steps)
- ✅ FC-03: Grid view for repeating elements (XMLSpy-style)
- ✅ FC-04: XSD-aware editing (15+ type-aware editors)
- ✅ FC-05: All 142 V1 features preserved
- ✅ FC-06: Feature flag (V1 ↔ V2 switch)
- ✅ FC-07: Add all node types (elements, text, attributes, comments, CDATA, PI)
- ✅ FC-08: Enhanced Entry Helpers (IDREF, base64Binary, anyURI, QName, etc.)
- ✅ FC-09: Image display (Base64 thumbnails in grid)
- ✅ FC-10: Grid Settings Dialog
- ✅ FC-11: Tree + Grid Split View

**Performance:**
- ✅ FC-12: 100MB file loads in < 2s
- ✅ FC-13: Edit operations in < 50ms
- ✅ FC-14: 60 FPS scrolling (< 100MB files)

**Quality:**
- ✅ FC-15: 80%+ code coverage
- ✅ FC-16: Zero critical bugs at release
- ✅ FC-17: All automated tests passing

### 10.2 Non-Functional Success Criteria

**Usability:**
- ✅ NFC-01: All features accessible via keyboard
- ✅ NFC-02: Consistent shortcuts across views
- ✅ NFC-03: Tooltips on all controls
- ✅ NFC-04: User-friendly error messages
- ✅ NFC-05: Undo/Redo descriptions visible

**Maintainability:**
- ✅ NFC-06: Model-View separation (no UI in model)
- ✅ NFC-07: Command pattern for all modifications
- ✅ NFC-08: Observable properties (PropertyChangeSupport)
- ✅ NFC-09: Comprehensive JavaDoc (all public APIs)
- ✅ NFC-10: Clean package structure

**Compatibility:**
- ✅ NFC-11: V1 ↔ V2 seamless switch
- ✅ NFC-12: Standard XML format (no proprietary extensions)
- ✅ NFC-13: Backward compatible settings
- ✅ NFC-14: No breaking changes to V1

**Performance:**
- ✅ NFC-15: Memory usage < 4GB for 1GB file
- ✅ NFC-16: No file size limits
- ✅ NFC-17: Startup time < 3s

### 10.3 Acceptance Tests

**AT-01: Dual-View Sync**
```
GIVEN: XML document open in V2 editor
WHEN: User edits text "<name>John</name>" → "<name>Jane</name>"
THEN: Tree View updates to show "Jane" within 50ms
AND: Grid View (if applicable) updates to show "Jane" within 50ms
AND: Cursor position preserved in Text View
```

**AT-02: Undo/Redo**
```
GIVEN: User performed 10 edit operations
WHEN: User presses Ctrl+Z 10 times
THEN: All operations undone in reverse order
AND: Document returns to initial state
WHEN: User presses Ctrl+Y 10 times
THEN: All operations redone in original order
AND: Document returns to final state
```

**AT-03: Grid View**
```
GIVEN: XML with 100 repeating <person> elements
WHEN: User opens Grid View
THEN: Table displays with 100 rows
AND: Columns auto-detected (@id, name, age, email)
WHEN: User edits cell in row 50
THEN: Model updates
AND: Text View reflects change
AND: Tree View reflects change
```

**AT-04: XSD Integration**
```
GIVEN: XML with linked XSD schema
WHEN: User adds child element to <person>
THEN: Only schema-valid elements suggested (firstName, lastName, birthDate, email)
WHEN: User enters invalid value "abc" in <age> field (xs:int)
THEN: Error highlighted in red
AND: Tooltip shows "Invalid value 'abc' for type 'xs:int'"
AND: Quick Fix suggests removing invalid value
```

**AT-05: Performance**
```
GIVEN: 100MB XML file
WHEN: User opens file
THEN: File loads in < 2s
AND: Progress bar shows loading status
WHEN: User scrolls in Tree View
THEN: Scrolling at 60 FPS (smooth)
WHEN: User edits text
THEN: Response in < 50ms (no lag)
```

**AT-06: Image Display**
```
GIVEN: XML with Base64-encoded PNG image
WHEN: User opens Grid View
THEN: Image column displays 64x64 thumbnail
WHEN: User clicks thumbnail
THEN: Full-size image preview dialog opens
AND: Shows original dimensions (1024x768)
```

**AT-07: Entry Helpers**
```
GIVEN: Element with xs:IDREF attribute
WHEN: User edits attribute in Grid
THEN: ComboBox appears with all IDs from document
WHEN: User selects "p002"
THEN: Value set to "p002"
AND: Validation passes
```

**AT-08: Tree + Grid Split**
```
GIVEN: XML with repeating elements
WHEN: User selects element in Tree View
THEN: Grid View scrolls to corresponding row
AND: Row highlighted
WHEN: User clicks row in Grid View
THEN: Tree View expands to show element
AND: Element selected in tree
```

---

## 11. DELIVERABLES

### 11.1 Code Deliverables

**Package:** `org.fxt.freexmltoolkit.controls.v2.xmleditor`

**Modules:**
1. **model/** - XmlNode hierarchy (10+ classes)
2. **commands/** - XmlCommand implementations (25+ commands)
3. **view/** - XmlTextView, XmlTreeView, XmlGridView
4. **editor/** - XmlEditorContext, SelectionModel, Panels, Menus
5. **serialization/** - XmlSerializer, XmlParser, IncrementalParser
6. **validation/** - XmlValidationManager, XsdValidator, SchematronValidator, QuickFix
7. **intellisense/** - XmlIntelliSenseManager, CompletionEngine, XsdIntegration
8. **schema/** - SchemaCache, SchemaAnalyzer, ElementSuggester, TypeMapper, TemplateGenerator
9. **performance/** - VirtualizationManager, LazyLoader, PerformanceMonitor, CacheManager

**Estimated LOC:** 15,000-20,000 lines

### 11.2 Test Deliverables

**Test Coverage:** 80%+ target

**Test Categories:**
- Model Tests (pure Java, no UI)
- Command Tests (execute/undo verification)
- Serialization Tests (round-trip XML → Model → XML)
- View Tests (TestFX for JavaFX UI)
- Integration Tests (end-to-end scenarios)
- Performance Tests (large files, benchmarks)

### 11.3 Documentation Deliverables

1. **XML_EDITOR_V2_REQUIREMENTS.md** (this document) - Complete requirements specification
2. **XML_EDITOR_V2_FEATURES.md** - Detailed feature list (165+ features)
3. **XML_EDITOR_V2_ARCHITECTURE.md** - Technical architecture details
4. **XML_EDITOR_V2_GRID_VIEW.md** - Grid View complete specification
5. **XML_EDITOR_V2_XSD_INTEGRATION.md** - XSD integration details
6. **XML_EDITOR_V2_ROADMAP.md** - Implementation timeline
7. **CLAUDE.md** (updated) - V2 structure and references

---

## APPENDICES

### Appendix A: Glossary

**Command Pattern:** Design pattern where operations are encapsulated as objects, enabling undo/redo.

**PropertyChangeSupport:** Java mechanism for observable properties, fires events when values change.

**Virtualization:** Rendering technique where only visible items are created, improving performance for large datasets.

**Lazy Loading:** Deferred loading of data until actually needed, reduces initial load time.

**Incremental Parsing:** Parsing large files in chunks, updating UI progressively.

**Debouncing:** Delaying execution of an operation until a quiet period, reduces redundant processing.

**XSD (XML Schema Definition):** W3C standard for defining the structure of XML documents.

**Schematron:** Rule-based validation language for XML, complements XSD.

**Base64:** Encoding scheme for binary data in text format (used for images in XML).

### Appendix B: References

**XSD Editor V2:**
- `docs/XSD_EDITOR_V2_README.md`
- `docs/XSD_EDITOR_V2_PLAN.md`
- `docs/XSD_EDITOR_V2_ROADMAP.md`

**Facets:**
- `FACETS_IMPLEMENTATION_SUMMARY.md`
- `INHERITED_FACETS_FEATURE.md`
- `XSD-1.1-DATATYPES-FACETS.md`

**Threading:**
- `docs/ThreadPoolArchitecture.md`

**IntelliSense:**
- `docs/context-sensitive-intellisense.md`

**XMLSpy Grid View:**
- https://www.altova.com/manual/XMLSpy/spyenterprise/xseditingviews_gridview.html
- https://www.altova.com/manual/XMLSpy/spyenterprise/xseditingviews_gridview_tabledisplayxml.html

---

## DOCUMENT HISTORY

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-22 | Requirements Analysis | Initial version with all 14 functional requirements, XMLSpy Grid View analysis integrated, 165+ features documented |

---

**END OF REQUIREMENTS SPECIFICATION**

**Total Requirements:** 14 Functional Requirements (FR-01 to FR-14)
**Total Features:** 165+ (142 V1 + 23 new V2)
**Timeline:** 16 weeks
**Estimated Effort:** 15,000-20,000 LOC
**Risk:** Medium (proven architecture pattern from XSD Editor V2)

**Next Steps:**
1. Review and approval of requirements
2. Begin implementation (see XML_EDITOR_V2_ROADMAP.md)
3. Weekly progress reviews
4. Feature-by-feature acceptance testing
