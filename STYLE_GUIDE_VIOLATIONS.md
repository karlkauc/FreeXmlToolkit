# Style Guide Violations Report

Generated: 2024-12-26
Updated: 2024-12-26 (after fixes)

This document lists all violations of the `STYLE_GUIDE.jsonc` found in the codebase.

## Summary

| Category | Original | Fixed | Remaining | Severity |
|----------|----------|-------|-----------|----------|
| Wrong Icon Library (FontAwesome) | 4 | 4 | 0 | High |
| Non-Bootstrap Icon Packs | 7 | 0 | 7 | Medium |
| Menu Items Without Icons (FXML) | 15+ | 15 | 0 | High |
| Menu Items Without Icons (Java) | 40+ | 0 | 40+ | Medium |
| Non-Standard Icon Sizes | 50+ | 0 | 50+ | Low |
| Hardcoded Colors (acceptable) | Many | N/A | Many | Info |

---

## FIXES APPLIED (2024-12-26)

### 1. FontAwesome Icons Fixed in FileExplorer.java
- `fa-folder-o` -> `bi-folder`
- `fa-file-o` -> `bi-file-earmark`
- `fa-folder-open-o` -> `bi-folder2-open`
- `fa-home` -> `bi-house`

### 2. Icons Added to TypeLibraryView.java Export Menu
- CSV, Excel, HTML, JSON, XML, Markdown menu items now have icons

### 3. Icons Added to FXML Files
- **tab_xslt_developer.fxml**: XQuery example menu items (4 items)
- **tab_xml_ultimate.fxml**: XPath and XQuery example menu items (6 items)
- **FavoritesPanel.fxml**: Filter menu items (7 items)

### 4. Already Compliant (No Changes Needed)
- **XmlContextMenuManager.java**: Already had icons
- **XmlGridContextMenu.java**: Already had icons with proper Bootstrap icons

---

## 1. ~~Wrong Icon Library (FontAwesome instead of Bootstrap)~~ FIXED

**Severity: HIGH** - FontAwesome icons are not part of Ikonli Bootstrap Icons!

**File:** `src/main/java/org/fxt/freexmltoolkit/controls/FileExplorer.java`

| Line | Current Icon | Suggested Fix |
|------|--------------|---------------|
| 130 | `fa-folder-o` | `bi-folder` |
| 133 | `fa-file-o` | `bi-file-earmark` |
| 149 | `fa-folder-open-o` | `bi-folder2-open` |
| 205 | `fa-home` | `bi-house` |

**Fix Required:**
```java
// Before
icon = new FontIcon("fa-folder-o");
icon = new FontIcon("fa-file-o");
((FontIcon) getGraphic()).setIconLiteral("fa-folder-open-o");
FontIcon homeIcon = new FontIcon("fa-home");

// After
icon = new FontIcon("bi-folder");
icon = new FontIcon("bi-file-earmark");
((FontIcon) getGraphic()).setIconLiteral("bi-folder2-open");
FontIcon homeIcon = new FontIcon("bi-house");
```

---

## 2. Non-Bootstrap Icon Packs Used

**Severity: MEDIUM** - These are valid but not from Bootstrap Icons.

These icons are documented as acceptable in the style guide under `icons.other`, but should be reviewed for consistency:

| File | Icon | Purpose |
|------|------|---------|
| `tab_fop.fxml:146` | `fth-git-pull-request` | XSLT icon |
| `tab_fop.fxml:159` | `win10-pdf` | PDF icon |
| `tab_xsd.fxml:426` | `win10-notebook` | Documentation icon |
| `main.fxml:160` | `fth-git-pull-request` | XSLT tab |
| `main.fxml:185` | `win10-pdf` | FOP tab |
| `main.fxml:192` | `win10-key` | Signature tab |
| `XsdController.java:5370` | `win10-notebook` | Documentation feature |

**Recommendation:** Consider replacing with Bootstrap equivalents:
- `fth-git-pull-request` -> `bi-arrow-repeat` (already used elsewhere for XSLT)
- `win10-pdf` -> `bi-file-pdf`
- `win10-key` -> `bi-key`
- `win10-notebook` -> `bi-journal-text` or `bi-book`

---

## 3. Menu Items Without Icons

**Severity: HIGH** - Style guide requires: "Menus and context menus should always have icons and text"

### FXML Files

**File:** `src/main/resources/pages/tab_xslt_developer.fxml`
- Line 309: `<MenuItem text="Simple Query" .../>` - Missing icon
- Line 310: `<MenuItem text="FLWOR Expression" .../>` - Missing icon
- Line 311: `<MenuItem text="HTML Report" .../>` - Missing icon
- Line 313: `<MenuItem text="Data Quality Check" .../>` - Missing icon

**File:** `src/main/resources/pages/tab_xml_ultimate.fxml`
- Lines 293-296: XPath example menu items - Missing icons
- Lines 311-312: XQuery example menu items - Missing icons

**File:** `src/main/resources/pages/controls/FavoritesPanel.fxml`
- Lines 58-65: Filter menu items (All Files, XML Documents, etc.) - Missing icons

### Java Files (55+ instances)

**File:** `src/main/java/org/fxt/freexmltoolkit/controls/editor/XmlContextMenuManager.java`

| Line | Menu Item | Suggested Icon |
|------|-----------|----------------|
| 79 | "Comment Lines" | `bi-chat-square-text` |
| 90 | "Cut" | `bi-scissors` |
| 99 | "Copy" | `bi-files` |
| 108 | "Paste" | `bi-clipboard` |
| 119 | "Copy XPath" | `bi-signpost-2` |
| 128 | "Go to Definition" | `bi-box-arrow-up-right` |
| 139 | "Select All" | `bi-check2-square` |
| 148 | "Find & Replace" | `bi-search` |
| 159 | "Format XML" | `bi-code-square` |
| 168 | "Validate XML" | `bi-check-circle` |
| 179 | "Expand All" | `bi-arrows-expand` |
| 188 | "Collapse All" | `bi-arrows-collapse` |

**File:** `src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlGridContextMenu.java`

| Line | Menu Item | Suggested Icon |
|------|-----------|----------------|
| 83 | "Child Element" | `bi-plus-circle` |
| 87 | "Attribute" | `bi-at` |
| 91 | "Text Content" | `bi-text-left` |
| 95 | "Sibling Before" | `bi-arrow-up` |
| 99 | "Sibling After" | `bi-arrow-down` |
| 110 | "Rename" | `bi-pencil` |
| 115 | "Duplicate" | `bi-files` |
| 121 | "Copy" | `bi-files` |
| 126 | "Cut" | `bi-scissors` |
| 131 | "Paste as Sibling" | `bi-clipboard` |
| 136 | "Paste as Child" | `bi-clipboard-plus` |
| 141 | "Copy Cell Content" | `bi-clipboard` |
| 146 | "Copy XPath" | `bi-signpost-2` |
| 152 | "Move Up" | `bi-arrow-up` |
| 157 | "Move Down" | `bi-arrow-down` |
| 163 | "Expand All" | `bi-arrows-expand` |
| 167 | "Collapse All" | `bi-arrows-collapse` |
| 175 | "Sort Ascending" | `bi-sort-alpha-down` |
| 179 | "Sort Descending" | `bi-sort-alpha-up` |
| 186 | "Delete" | `bi-trash` |

**File:** `src/main/java/org/fxt/freexmltoolkit/controls/v2/view/TypeLibraryView.java`

| Line | Menu Item | Suggested Icon |
|------|-----------|----------------|
| 711 | "CSV" | `bi-file-earmark-spreadsheet` |
| 715 | "Excel" | `bi-file-earmark-excel` |
| 719 | "HTML" | `bi-file-earmark-code` |
| 723 | "JSON" | `bi-braces` |
| 727 | "XML" | `bi-file-earmark-code` |
| 731 | "Markdown" | `bi-markdown` |

**Other files with menu items missing icons:**
- `XsdController.java` (lines 3881, 3919, 5423)
- `XmlUltimateController.java` (lines 906, 913, 930)
- `SnippetContextMenu.java` (line 93)
- `WelcomeController.java` (lines 330, 334)
- `TypeUsageDialog.java` (lines 207, 217)

---

## 4. Non-Standard Icon Sizes

**Severity: LOW** - Style guide recommends: 16px (menu), 20px (toolbar), 48/64px (empty states)

Non-standard sizes found (12px, 14px, 18px):

### 12px Icons (should be 16px for menus)
- `popup_templates.fxml:31` - bi-arrow-clockwise
- `popup_templates.fxml:73` - bi-plus-circle
- `popup_templates.fxml:98` - bi-eye
- `tab_xml_ultimate.fxml:399` - bi-plus-circle
- `tab_xml_ultimate.fxml:446` - bi-trash
- `tab_xsd.fxml:750` - bi-download
- `welcome.fxml:240` - bi-check

### 14px Icons (should be 16px)
- `tab_xslt_developer.fxml` - 23 instances
- `tab_xsd.fxml:613, 759`
- `popup_templates.fxml:114, 119, 125, 130`
- `welcome.fxml:71, 86, 101, 116, 134, 149, 164, 179`

### 18px Icons (should be 16px or 20px)
- `tab_xsd.fxml:593, 607, 741`
- `tab_xml_ultimate.fxml:263`
- `welcome.fxml:197`

**Recommendation:** Standardize to 16px for small/menu icons or 20px for toolbar icons.

---

## 5. Hardcoded Colors (Informational)

While many hardcoded colors exist, most follow the semantic color palette defined in the style guide:

**Correctly using semantic colors:**
- `#28a745` (success green) - Used correctly
- `#dc3545` (danger red) - Used correctly
- `#007bff` (primary blue) - Used correctly
- `#ffc107` (warning yellow) - Used correctly
- `#17a2b8` (info cyan) - Used correctly
- `#6c757d` (secondary gray) - Used correctly

**Non-standard colors found (should be reviewed):**
| Color | Location | Possible Issue |
|-------|----------|----------------|
| `#2563eb` | welcome.fxml | Non-standard blue (should be #007bff) |
| `#155724` | tab_xsd.fxml:613 | Dark green (should use success color) |
| `#6b7280` | tab_xsd.fxml, welcome.fxml | Non-standard gray |
| `#16a34a` | welcome.fxml:240 | Non-standard green (should be #28a745) |

---

## 6. Missing `bi-database` Icon (Correctly Absent)

**STATUS: PASSED** - No instances of the forbidden `bi-database` icon were found.

---

## Recommended Priority

1. **HIGH - Fix FontAwesome Icons** (FileExplorer.java)
   - These will fail if FontAwesome is not loaded

2. **HIGH - Add Icons to Menu Items**
   - XmlContextMenuManager.java
   - XmlGridContextMenu.java
   - TypeLibraryView.java

3. **MEDIUM - Consider Bootstrap Replacements**
   - Replace win10-* and fth-* icons with Bootstrap equivalents

4. **LOW - Standardize Icon Sizes**
   - Convert 12px/14px to 16px
   - Convert 18px to 16px or 20px

---

## Quick Fix Script

For the FontAwesome icons in FileExplorer.java:

```java
// Replace these patterns:
// fa-folder-o -> bi-folder
// fa-file-o -> bi-file-earmark
// fa-folder-open-o -> bi-folder2-open
// fa-home -> bi-house
```
