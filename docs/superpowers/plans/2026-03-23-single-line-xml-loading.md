# Single-Line XML Loading Performance Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent editor freezing when loading XML files with extremely long lines (no line breaks).

**Architecture:** Three-layer defense: (1) Detection at load time offers auto-format, (2) FoldingManagerV2 guards against O(n^2) regex on long lines, (3) SyntaxHighlightManagerV2 disables highlighting for long lines. All guards use a simple char-by-char loop to find max line length — no regex, no Stream allocation.

**Tech Stack:** Java 25, JavaFX 24.0.1, RichTextFX, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-23-single-line-xml-loading-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/org/fxt/freexmltoolkit/service/XmlService.java` | Modify (lines 134-147) | Replace `isSingleLineXml()` with `hasProblematicLineLength()` |
| `src/main/java/org/fxt/freexmltoolkit/controller/XmlUltimateController.java` | Modify (line 642) | Update method call |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2.java` | Modify (lines 90-94) | Add long-line guard |
| `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2.java` | Modify (lines 84-96) | Add long-line guard |
| `src/test/java/org/fxt/freexmltoolkit/XmlServiceTest.java` | Modify (lines 201-261) | Replace old tests with new ones |
| `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2LongLineGuardTest.java` | Create | Test folding guard |
| `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2LongLineGuardTest.java` | Create | Test highlighting guard |

---

### Task 1: Replace `isSingleLineXml()` with `hasProblematicLineLength()` in XmlService

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XmlService.java:134-147`
- Modify: `src/test/java/org/fxt/freexmltoolkit/XmlServiceTest.java:201-261`

- [ ] **Step 1: Write tests for `hasProblematicLineLength()`**

Replace the existing `isSingleLineXml` test block (lines 201-261 in `XmlServiceTest.java`) with:

```java
// ==================== Problematic Line Length Detection Tests ====================

@Test
@DisplayName("Should return false for null input")
void testHasProblematicLineLength_null() {
    assertFalse(XmlService.hasProblematicLineLength(null));
}

@Test
@DisplayName("Should return false for empty input")
void testHasProblematicLineLength_empty() {
    assertFalse(XmlService.hasProblematicLineLength(""));
}

@Test
@DisplayName("Should return false for normal multi-line XML")
void testHasProblematicLineLength_normalXml() {
    String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <child>text</child>
            </root>
            """;
    assertFalse(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should return false for short single-line XML")
void testHasProblematicLineLength_shortSingleLine() {
    String xml = "<root><child>text</child></root>";
    assertFalse(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should return true for single line exceeding 100KB")
void testHasProblematicLineLength_longSingleLine() {
    // Build a single line > 100KB
    String xml = "<root>" + "x".repeat(100 * 1024 + 1) + "</root>";
    assertTrue(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should return false for line exactly at 100KB threshold")
void testHasProblematicLineLength_exactlyAtThreshold() {
    // Build a single line of exactly 100*1024 chars
    String xml = "x".repeat(100 * 1024);
    assertFalse(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should return true for line just over 100KB threshold")
void testHasProblematicLineLength_justOverThreshold() {
    String xml = "x".repeat(100 * 1024 + 1);
    assertTrue(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should return true when one of two lines exceeds threshold")
void testHasProblematicLineLength_twoLinesOneLong() {
    String xml = "x".repeat(100 * 1024 + 1) + "\n</root>";
    assertTrue(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should handle CRLF line endings correctly")
void testHasProblematicLineLength_crlfShortLines() {
    String xml = "<root>\r\n<child>text</child>\r\n</root>";
    assertFalse(XmlService.hasProblematicLineLength(xml));
}

@Test
@DisplayName("Should handle CR-only line endings correctly")
void testHasProblematicLineLength_crOnlyShortLines() {
    String xml = "<root>\r<child>text</child>\r</root>";
    assertFalse(XmlService.hasProblematicLineLength(xml));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.XmlServiceTest.testHasProblematicLineLength*" 2>&1 | tail -20`
Expected: Compilation error — `hasProblematicLineLength` does not exist yet.

- [ ] **Step 3: Implement `hasProblematicLineLength()` in XmlService**

Replace lines 134-147 in `XmlService.java`:

```java
    /**
     * Maximum safe line length before performance issues arise (100KB).
     * Lines longer than this threshold can cause O(n^2) regex behavior in folding/highlighting.
     */
    int MAX_SAFE_LINE_LENGTH = 100 * 1024;

    /**
     * Checks if the XML content has any line exceeding the safe length threshold.
     * Files with extremely long lines cause performance issues with folding and syntax highlighting
     * due to regex patterns that exhibit O(n^2) behavior on long lines.
     *
     * @param content the XML content to check
     * @return true if any line exceeds {@link #MAX_SAFE_LINE_LENGTH}
     */
    static boolean hasProblematicLineLength(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        int currentLineLength = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r') {
                if (currentLineLength > MAX_SAFE_LINE_LENGTH) {
                    return true;
                }
                currentLineLength = 0;
                // Skip \n after \r for \r\n
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                currentLineLength++;
            }
        }
        return currentLineLength > MAX_SAFE_LINE_LENGTH;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.XmlServiceTest.testHasProblematicLineLength*" 2>&1 | tail -20`
Expected: All 10 tests PASS.

- [ ] **Step 5: Update controller call**

In `XmlUltimateController.java` line 642, change:
```java
// Old:
if (XmlService.isSingleLineXml(content)) {
// New:
if (XmlService.hasProblematicLineLength(content)) {
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/XmlService.java \
        src/main/java/org/fxt/freexmltoolkit/controller/XmlUltimateController.java \
        src/test/java/org/fxt/freexmltoolkit/XmlServiceTest.java
git commit -m "feat: replace isSingleLineXml with hasProblematicLineLength

Detects XML files with lines exceeding 100KB instead of just counting
lines. Uses char-by-char loop for efficient scanning without Stream
allocation. Handles LF, CRLF, and CR line endings."
```

---

### Task 2: Add long-line guard in FoldingManagerV2

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2.java:45-47,90-94`
- Create: `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2LongLineGuardTest.java`

- [ ] **Step 1: Write test for long-line guard**

Create `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2LongLineGuardTest.java`:

```java
package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.*;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import javafx.stage.Stage;

@ExtendWith(ApplicationExtension.class)
class FoldingManagerV2LongLineGuardTest {

    private FoldingManagerV2 foldingManager;

    @Start
    void start(Stage stage) {
        CodeArea codeArea = new CodeArea();
        foldingManager = new FoldingManagerV2(codeArea);
    }

    @Test
    @DisplayName("Should skip folding for single-line XML exceeding threshold")
    void testSkipsFoldingForLongSingleLine() {
        // Single line > 100KB — folding must be skipped
        String longLine = "<root>" + "x".repeat(100 * 1024 + 1) + "</root>";
        foldingManager.updateFoldingRegions(longLine);
        assertTrue(foldingManager.getFoldableRegions().isEmpty(),
                "Folding regions must be empty for single-line XML exceeding threshold");
    }

    @Test
    @DisplayName("Should skip folding when one of two lines exceeds threshold")
    void testSkipsFoldingForTwoLinesWithOneLong() {
        String xml = "<root>" + "x".repeat(100 * 1024 + 1) + "\n</root>";
        foldingManager.updateFoldingRegions(xml);
        assertTrue(foldingManager.getFoldableRegions().isEmpty(),
                "Folding regions must be empty when any line exceeds threshold");
    }

    @Test
    @DisplayName("Should compute folding for normal multi-line XML")
    void testComputesFoldingForNormalXml() {
        String xml = "<root>\n  <child>text</child>\n  <child2>text2</child2>\n</root>";
        foldingManager.updateFoldingRegions(xml);
        assertFalse(foldingManager.getFoldableRegions().isEmpty(),
                "Folding regions should be computed for normal multi-line XML");
    }

    @Test
    @DisplayName("Should clear existing regions when long-line text is set")
    void testClearsExistingRegionsOnLongLine() {
        // First set normal XML to populate regions
        String normalXml = "<root>\n  <child>text</child>\n  <child2>text2</child2>\n</root>";
        foldingManager.updateFoldingRegions(normalXml);
        assertFalse(foldingManager.getFoldableRegions().isEmpty());

        // Now set long-line XML — must clear
        String longLine = "<root>" + "x".repeat(100 * 1024 + 1) + "</root>";
        foldingManager.updateFoldingRegions(longLine);
        assertTrue(foldingManager.getFoldableRegions().isEmpty(),
                "Existing folding regions must be cleared when long-line text is set");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2LongLineGuardTest" 2>&1 | tail -20`
Expected: `testSkipsFoldingForLongSingleLine` FAILS (folding regions are not empty — the guard doesn't exist yet).

- [ ] **Step 3: Add long-line guard to FoldingManagerV2**

Add constant after line 47 (`private final CodeArea codeArea;`):

```java
    // Maximum line length before folding is skipped (prevents O(n^2) regex behavior)
    private static final int MAX_SAFE_LINE_LENGTH_FOR_FOLDING = 100 * 1024; // 100KB
```

Insert guard in `updateFoldingRegions()` after the null/empty check (after line 94, before the `isFoldingInProgress` check):

```java
        // Guard: skip folding if any line is extremely long (prevents O(n^2) regex)
        if (hasExtremelyLongLine(text)) {
            logger.info("Skipping folding - text contains line exceeding {}KB",
                    MAX_SAFE_LINE_LENGTH_FOR_FOLDING / 1024);
            foldingRegions.clear();
            return;
        }
```

Add private helper method at the end of the class (before the closing `}`):

```java
    /**
     * Checks if any line in the text exceeds the safe length threshold.
     * Uses a simple char-by-char loop for O(n) performance without Stream allocation.
     *
     * @param text the text to check
     * @return true if any line exceeds {@link #MAX_SAFE_LINE_LENGTH_FOR_FOLDING}
     */
    private static boolean hasExtremelyLongLine(String text) {
        int lineLength = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (lineLength > MAX_SAFE_LINE_LENGTH_FOR_FOLDING) {
                    return true;
                }
                lineLength = 0;
            } else {
                lineLength++;
            }
        }
        return lineLength > MAX_SAFE_LINE_LENGTH_FOR_FOLDING;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingManagerV2LongLineGuardTest" 2>&1 | tail -20`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2.java \
        src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/FoldingManagerV2LongLineGuardTest.java
git commit -m "fix: add long-line guard to FoldingManagerV2

Skip folding region calculation when any line exceeds 100KB.
Prevents O(n^2) regex behavior from OPEN_TAG_PATTERN's negative
lookahead on extremely long lines. Defense-in-depth alongside
the foldingEnabled flag in XmlCodeEditorV2."
```

---

### Task 3: Add long-line guard in SyntaxHighlightManagerV2

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2.java:34-36,84-96`
- Create: `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2LongLineGuardTest.java`

- [ ] **Step 1: Write test for long-line guard**

Create `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2LongLineGuardTest.java`:

```java
package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.*;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import javafx.stage.Stage;

@ExtendWith(ApplicationExtension.class)
class SyntaxHighlightManagerV2LongLineGuardTest {

    private SyntaxHighlightManagerV2 syntaxManager;

    @Start
    void start(Stage stage) {
        CodeArea codeArea = new CodeArea();
        syntaxManager = new SyntaxHighlightManagerV2(codeArea);
    }

    @Test
    @DisplayName("Should disable highlighting for text with line exceeding 200KB")
    void testDisablesHighlightingForLongLine() {
        String longLine = "<root>" + "x".repeat(200 * 1024 + 1) + "</root>";
        syntaxManager.applySyntaxHighlighting(longLine);
        assertTrue(syntaxManager.isHighlightingDisabled(),
                "Highlighting must be disabled for text with extremely long line");
    }

    @Test
    @DisplayName("Should not disable highlighting for normal text")
    void testDoesNotDisableForNormalText() {
        String normalXml = "<root>\n  <child>text</child>\n</root>";
        syntaxManager.applySyntaxHighlighting(normalXml);
        assertFalse(syntaxManager.isHighlightingDisabled(),
                "Highlighting must not be disabled for normal XML");
    }

    @Test
    @DisplayName("Should not disable highlighting for short single-line XML")
    void testDoesNotDisableForShortSingleLine() {
        String shortLine = "<root><child>text</child></root>";
        syntaxManager.applySyntaxHighlighting(shortLine);
        assertFalse(syntaxManager.isHighlightingDisabled(),
                "Highlighting must not be disabled for short single-line XML");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.editor.managers.SyntaxHighlightManagerV2LongLineGuardTest" 2>&1 | tail -20`
Expected: Compilation error — `isHighlightingDisabled()` does not exist yet.

- [ ] **Step 3: Add long-line guard to SyntaxHighlightManagerV2**

Add constant after line 36 (`private static final long LARGE_FILE_DEBOUNCE_MS = 800;`):

```java
    private static final int MAX_HIGHLIGHTABLE_LINE_LENGTH = 200 * 1024; // 200KB
```

Add package-private accessor after the `highlightingDisabled` field (after line 47):

```java
    /**
     * Returns whether syntax highlighting is currently disabled.
     * Package-private for testing.
     */
    boolean isHighlightingDisabled() {
        return highlightingDisabled;
    }
```

Insert long-line guard in `applySyntaxHighlighting()` after the very-large-file guard (after line 96, before the re-enable block):

```java
        // Long line guard: disable highlighting for extremely long lines
        if (hasExtremelyLongLine(text)) {
            if (!highlightingDisabled) {
                highlightingDisabled = true;
                logger.info("Syntax highlighting disabled - line exceeds {}KB",
                        MAX_HIGHLIGHTABLE_LINE_LENGTH / 1024);
            }
            return;
        }
```

Add private helper method at the end of the class (before the closing `}`):

```java
    /**
     * Checks if any line in the text exceeds the highlightable line length threshold.
     * Uses a simple char-by-char loop for O(n) performance without Stream allocation.
     *
     * @param text the text to check
     * @return true if any line exceeds {@link #MAX_HIGHLIGHTABLE_LINE_LENGTH}
     */
    private static boolean hasExtremelyLongLine(String text) {
        int lineLength = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (lineLength > MAX_HIGHLIGHTABLE_LINE_LENGTH) {
                    return true;
                }
                lineLength = 0;
            } else {
                lineLength++;
            }
        }
        return lineLength > MAX_HIGHLIGHTABLE_LINE_LENGTH;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.editor.managers.SyntaxHighlightManagerV2LongLineGuardTest" 2>&1 | tail -20`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2.java \
        src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/SyntaxHighlightManagerV2LongLineGuardTest.java
git commit -m "fix: add long-line guard to SyntaxHighlightManagerV2

Disable syntax highlighting when any line exceeds 200KB.
Prevents slow nested regex matching in XmlSyntaxHighlighter
on extremely long lines. Threshold is higher than folding (100KB)
because highlighting runs on a background thread with debouncing."
```

---

### Task 4: Run full test suite and verify

- [ ] **Step 1: Run all tests**

Run: `./gradlew test 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Verify no compilation warnings**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit (if any fixups needed)**

Only if tests revealed issues in previous tasks.
