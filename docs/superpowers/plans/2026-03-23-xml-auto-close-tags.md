# XML Auto-Close Tag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user types `</` in the XML text editor, automatically complete the closing tag with the innermost unclosed element name (e.g., `<a>text</` becomes `<a>text</a>`).

**Architecture:** The existing `ContextAnalyzer.buildXPathContext()` already maintains an element stack that tracks unclosed elements. We add a `'/'` char trigger to the `IntelliSenseEngine` that checks if the text before the caret ends with `</`, retrieves the innermost unclosed element from the XPath context, and inserts `elementName>` at the caret position. No new classes needed -- just a new method + trigger registration in `IntelliSenseEngine` and tests in existing test files.

**Tech Stack:** Java 25, RichTextFX CodeArea, existing IntelliSense trigger system

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `src/test/java/.../intellisense/context/ContextAnalyzerTest.java` | Add tests verifying element stack resolution for `</` scenarios |
| Modify | `src/main/java/.../intellisense/IntelliSenseEngine.java` | Add `handleAutoCloseTag()` + register `'/'` trigger |

Base package: `org.fxt.freexmltoolkit.controls.v2.editor`

---

### Task 1: Add failing tests for closing tag element resolution

These tests verify that `ContextAnalyzer.analyze()` correctly identifies the innermost unclosed element when text ends with `</`. This is the core logic that the auto-close feature depends on.

**Files:**
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/intellisense/context/ContextAnalyzerTest.java`

- [ ] **Step 1: Write failing tests for closing tag element resolution**

Add these tests to `ContextAnalyzerTest.java`:

```java
@Test
void closingTag_simpleElement() {
    // <a>text</ → should identify "a" as the element to close
    String xml = "<a>text</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertNotNull(context.getXPathContext());
    assertEquals("a", context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_nestedElements() {
    // <a><b>text</ → should identify "b" (innermost unclosed)
    String xml = "<a><b>text</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertEquals("b", context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_afterClosedSibling() {
    // <a><b>text</b></ → "b" is closed, should identify "a"
    String xml = "<a><b>text</b></";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertEquals("a", context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_afterSelfClosing() {
    // <a><br/>text</ → <br/> is self-closing, should identify "a"
    String xml = "<a><br/>text</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertEquals("a", context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_withNamespacePrefix() {
    // <ns:element>text</ → should identify "ns:element"
    String xml = "<ns:element>text</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertEquals("ns:element", context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_atRootLevel() {
    // </ with no opening tags → no element to close
    String xml = "</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertNotNull(context.getXPathContext());
    assertNull(context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_deeplyNested() {
    // <a><b><c>text</ → should identify "c"
    String xml = "<a><b><c>text</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertEquals("c", context.getXPathContext().getCurrentElement());
}

@Test
void closingTag_withAttributes() {
    // <div class="main"><span id="x">text</ → should identify "span"
    String xml = "<div class=\"main\"><span id=\"x\">text</";
    XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
    assertEquals("span", context.getXPathContext().getCurrentElement());
}
```

- [ ] **Step 2: Run tests to verify they fail (or pass -- they should pass since buildXPathContext already handles this)**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzerTest" -i`

Expected: All new tests should PASS because `buildXPathContext()` already correctly maintains the element stack. The XPathContext is always built regardless of ContextType. These tests document the behavior we depend on.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/intellisense/context/ContextAnalyzerTest.java
git commit -m "test: add closing tag element resolution tests for auto-close feature"
```

---

### Task 2: Implement auto-close tag handler in IntelliSenseEngine

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/intellisense/IntelliSenseEngine.java`

- [ ] **Step 1: Register '/' char trigger in setupTriggers()**

In `IntelliSenseEngine.java`, add the trigger registration in the `setupTriggers()` method (line ~106):

```java
private void setupTriggers() {
    // Character triggers
    triggerSystem.addCharTrigger('<', this::showCompletions);
    triggerSystem.addCharTrigger(' ', this::showCompletionsIfInAttributeContext);
    triggerSystem.addCharTrigger('/', this::handleAutoCloseTag);

    // Key triggers
    triggerSystem.addKeyTrigger(KeyCode.SPACE, true, this::showCompletions); // Ctrl+Space

    logger.debug("Setup IntelliSense triggers");
}
```

- [ ] **Step 2: Add handleAutoCloseTag() method**

Add this method to `IntelliSenseEngine.java` (after `showCompletionsIfInAttributeContext` at line ~202):

```java
/**
 * Handles auto-closing of XML tags.
 * When the user types '/' after '&lt;', automatically completes the closing tag
 * with the innermost unclosed element name.
 * <p>Example: typing '/' in {@code <a>text</} produces {@code <a>text</a>}</p>
 */
private void handleAutoCloseTag() {
    String text = editorContext.getText();
    int caretPos = editorContext.getCaretPosition();

    // Check if text before caret ends with "</"
    if (caretPos < 2) {
        return;
    }
    String textBeforeCaret = text.substring(0, caretPos);
    if (!textBeforeCaret.endsWith("</")) {
        return;
    }

    // Hide any IntelliSense popup that might be showing (e.g., from '<' trigger)
    hideCompletions();

    // Find the innermost unclosed element using existing context analysis
    XmlContext context = analyzeContext(text, caretPos);

    // Don't auto-close inside attribute values, comments, or CDATA
    if (context.getType() == org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType.ATTRIBUTE_VALUE) {
        return;
    }

    XPathContext xpathContext = context.getXPathContext();
    if (xpathContext == null || xpathContext.getCurrentElement() == null) {
        return;
    }

    String elementName = xpathContext.getCurrentElement();
    logger.debug("Auto-closing tag: </{}>", elementName);

    // Insert the element name and closing '>'
    CodeArea codeArea = editorContext.getCodeArea();
    codeArea.insertText(caretPos, elementName + ">");

    // Position cursor after the '>'
    codeArea.moveTo(caretPos + elementName.length() + 1);
}
```

- [ ] **Step 3: Add missing import for XPathContext**

Add import at the top of `IntelliSenseEngine.java`:

```java
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathContext;
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run existing IntelliSense tests to verify no regression**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.editor.intellisense.*" -i`
Expected: All existing tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/intellisense/IntelliSenseEngine.java
git commit -m "feat: auto-close XML tags when typing </

When the user types '</' in the XML editor, the editor now automatically
completes the closing tag with the innermost unclosed element name.
For example, typing '<a>text</' produces '<a>text</a>'.

Uses the existing ContextAnalyzer XPath element stack to determine
which element to close. Registered as a '/' char trigger in the
IntelliSense trigger system."
```

---

### Task 3: Manual verification

- [ ] **Step 1: Run the application and test auto-close**

Run: `./gradlew run`

Test these scenarios in the XML editor tab:
1. Type `<root>` then `</` → should auto-complete to `</root>`
2. Type `<a><b>text` then `</` → should auto-complete to `</b>`
3. Type `<a><b>text</b>` then `</` → should auto-complete to `</a>`
4. Type `<ns:element>text` then `</` → should auto-complete to `</ns:element>`
5. Type `/` inside text content (not after `<`) → should NOT trigger auto-close
6. Type `<br/` (self-closing) → should NOT trigger auto-close
7. Verify IntelliSense popup still works: type `<` and verify element suggestions appear
