# Single-Line XML Loading Performance Fix

**Date:** 2026-03-23
**Status:** Approved

## Problem

Loading XML files without line breaks (single-line XML) causes the editor to freeze. Root causes:

1. **FoldingManagerV2**: `OPEN_TAG_PATTERN` uses `(?!.*/>)` negative lookahead — O(n^2) on long lines
2. **SyntaxHighlightManagerV2**: Nested regex matchers in `XmlSyntaxHighlighter` can be slow on very long lines
3. **Detection**: `isSingleLineXml()` only checks line count <= 3, misses cases like 2 lines where one is 500KB

## Approach

Combined approach (Approach C): Auto-format as the default path, plus defensive loading for "Load Anyway".

## Design

### 1. Improved Detection (`XmlService.java`)

Replace `isSingleLineXml()` with `hasProblematicLineLength()`:

- Use a single manual loop to find max line length (no IntStream/Stream allocation for large strings)
- If max line length exceeds 100KB (`MAX_SAFE_LINE_LENGTH = 100 * 1024`), return true
- No arbitrary line-count short-circuit — always checks actual line lengths

```java
private static final int MAX_SAFE_LINE_LENGTH = 100 * 1024; // 100KB

static boolean hasProblematicLineLength(String content) {
    if (content == null || content.isEmpty()) return false;
    int maxLineLength = 0;
    int currentLineLength = 0;
    for (int i = 0; i < content.length(); i++) {
        char c = content.charAt(i);
        if (c == '\n' || c == '\r') {
            maxLineLength = Math.max(maxLineLength, currentLineLength);
            currentLineLength = 0;
            // Skip \n after \r for \r\n
            if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                i++;
            }
        } else {
            currentLineLength++;
        }
    }
    maxLineLength = Math.max(maxLineLength, currentLineLength);
    return maxLineLength > MAX_SAFE_LINE_LENGTH;
}
```

### 2. Path A: "Format Now" (no changes needed)

Existing `XmlService.prettyFormat()` formats the content before loading. After formatting, line breaks exist and all editor features (folding, highlighting) work normally.

### 3. Path B: "Load Anyway" — Defensive Loading

#### 3a. FoldingManagerV2 — Defense in Depth

Add guard in `updateFoldingRegions()`: check if any line exceeds a safe length threshold. This protects against direct calls that bypass `foldingEnabled`, and also catches cases like a 2-line file where one line is 500KB.

The threshold (100KB) matches `MAX_SAFE_LINE_LENGTH` from `XmlService`. Rationale: the folding regex `(?!.*/>)` is O(n^2) per line, so any line > 100KB is dangerous.

```java
private static final int MAX_SAFE_LINE_LENGTH_FOR_FOLDING = 100 * 1024; // 100KB

public void updateFoldingRegions(String text) {
    if (text == null || text.isEmpty()) {
        foldingRegions.clear();
        return;
    }
    // Guard: skip folding if any line is extremely long (prevents O(n^2) regex)
    if (hasExtremelyLongLine(text, MAX_SAFE_LINE_LENGTH_FOR_FOLDING)) {
        logger.info("Skipping folding - text contains line exceeding {}KB", MAX_SAFE_LINE_LENGTH_FOR_FOLDING / 1024);
        foldingRegions.clear();
        return;
    }
    // ... existing logic
}

private static boolean hasExtremelyLongLine(String text, int threshold) {
    int lineLength = 0;
    for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
            if (lineLength > threshold) return true;
            lineLength = 0;
        } else {
            lineLength++;
        }
    }
    return lineLength > threshold;
}
```

#### 3b. SyntaxHighlightManagerV2 — Long Line Guard

Add guard in `applySyntaxHighlighting()`: before scheduling highlighting, check if any line exceeds 200KB. If so, disable highlighting (same behavior as the existing 2MB total-size guard).

The threshold (200KB) is higher than folding (100KB) because highlighting runs on a background thread with debouncing and is less pathological than the folding regex.

```java
private static final int MAX_HIGHLIGHTABLE_LINE_LENGTH = 200 * 1024; // 200KB

// In applySyntaxHighlighting(), after the very-large-file guard:
if (hasExtremelyLongLine(text)) {
    if (!highlightingDisabled) {
        highlightingDisabled = true;
        logger.info("Syntax highlighting disabled - line exceeds {}KB", MAX_HIGHLIGHTABLE_LINE_LENGTH / 1024);
    }
    return;
}
```

Private helper (same approach as FoldingManagerV2):
```java
private boolean hasExtremelyLongLine(String text) {
    int lineLength = 0;
    for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
            if (lineLength > MAX_HIGHLIGHTABLE_LINE_LENGTH) return true;
            lineLength = 0;
        } else {
            lineLength++;
        }
    }
    return lineLength > MAX_HIGHLIGHTABLE_LINE_LENGTH;
}
```

### 4. Controller Update (`XmlUltimateController.java`)

Change call from `isSingleLineXml(content)` to `hasProblematicLineLength(content)`. No other changes to dialog or logic.

## Files Changed

| File | Change |
|------|--------|
| `XmlService.java` | Replace `isSingleLineXml()` with `hasProblematicLineLength()`, add `MAX_SAFE_LINE_LENGTH` constant |
| `XmlUltimateController.java` | Update method call `isSingleLineXml` -> `hasProblematicLineLength` |
| `FoldingManagerV2.java` | Add long-line guard in `updateFoldingRegions()` with `hasExtremelyLongLine()` helper |
| `SyntaxHighlightManagerV2.java` | Add long-line guard in `applySyntaxHighlighting()` with `hasExtremelyLongLine()` helper |

## Files NOT Changed

- `XmlSyntaxHighlighter.java` — Regex patterns work fine for normal XML; guards in callers handle edge cases
- `XmlCodeEditorV2.java` — Existing `foldingEnabled` logic works correctly
- `XmlCodeFoldingManager.java` — Legacy class, not instantiated anywhere (dead code)
- Dialog text — already clear and appropriate

## Tests

### `XmlServiceTest` — `hasProblematicLineLength()`
- Normal multi-line XML (should return false)
- Short single-line XML under threshold (should return false)
- Long single-line XML over 100KB (should return true)
- 2-line XML where one line exceeds 100KB (should return true)
- Exactly at the 100KB boundary (should return false)
- Just over 100KB boundary (should return true)
- `\r\n` line endings with short lines (should return false)
- `\r` line endings (classic Mac) with short lines (should return false)
- null and empty input (should return false)

### `FoldingManagerV2Test` — long-line guard
- `updateFoldingRegions()` with single-line XML: foldingRegions must be empty
- `updateFoldingRegions()` with 2-line XML where one line is 200KB: foldingRegions must be empty
- `updateFoldingRegions()` with normal multi-line XML: foldingRegions must be populated

### `SyntaxHighlightManagerV2Test` — long-line guard
- `applySyntaxHighlighting()` with line > 200KB: highlighting must be disabled (verify no `processHighlighting` call or expose disabled state)
- `applySyntaxHighlighting()` with normal text: highlighting proceeds normally
