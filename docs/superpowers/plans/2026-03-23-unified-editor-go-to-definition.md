# Unified Editor: Go to Definition Opens XSD in Same Editor

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When "Go to Definition" is triggered from an XML file in the Unified Editor, open the linked XSD as a new tab within the same Unified Editor (not the dedicated XSD tab) and navigate to the target element.

**Architecture:** Currently, `XmlUnifiedTab.setMainController()` wires the Go to Definition handler to `mainController.switchToXsdViewAndNavigate()`, which leaves the Unified Editor entirely. We change this so that `UnifiedEditorTabManager` provides its own navigation handler that opens/reuses an `XsdUnifiedTab` and navigates within it. `XsdUnifiedTab` gains a `navigateToElement()` method with deferred-navigation support (same pattern as `XsdController.pendingNavigationElement`).

**Tech Stack:** Java 25, JavaFX 24.0.1, JUnit 5, TestFX

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `controls/unified/XsdUnifiedTab.java` | Add `navigateToElement(String)` with deferred navigation support |
| Modify | `controls/unified/UnifiedEditorTabManager.java` | Add `openXsdFileAndNavigate(File, String)` method |
| Modify | `controls/unified/XmlUnifiedTab.java` | Add `setGoToDefinitionHandler(Consumer<NavigationRequest>)`, decouple from MainController |
| Modify | `controls/unified/UnifiedEditorTabManager.java` | Change wiring to use in-editor navigation instead of `mainController.switchToXsdViewAndNavigate()` |
| Create | `test: controls/unified/XsdUnifiedTabNavigationTest.java` | Test deferred navigation state management |
| Create | `test: controls/unified/UnifiedEditorTabManagerNavigationTest.java` | Test tab manager navigation method |
| Create | `test: controls/unified/UnifiedEditorGoToDefinitionIntegrationTest.java` | End-to-end integration test |

All paths relative to `src/main/java/org/fxt/freexmltoolkit/` (or `src/test/java/...` for tests).

---

## Task 1: Add `navigateToElement` to `XsdUnifiedTab`

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/unified/XsdUnifiedTab.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/unified/XsdUnifiedTabNavigationTest.java`

- [ ] **Step 1: Write the failing test**

Create test file `src/test/java/org/fxt/freexmltoolkit/controls/unified/XsdUnifiedTabNavigationTest.java`.

Note: `XsdUnifiedTab` creates JavaFX components, so all tests must run on the JavaFX Application Thread using TestFX. To test the *deferred* navigation path, create the tab with `null` source file (so `graphView` stays null during construction), then call `navigateToElement()`.

```java
package org.fxt.freexmltoolkit.controls.unified;

import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdUnifiedTab.navigateToElement() and deferred navigation.
 * Uses TestFX because XsdUnifiedTab creates JavaFX components.
 */
@ExtendWith(ApplicationExtension.class)
class XsdUnifiedTabNavigationTest {

    private XsdUnifiedTab tab;

    @Start
    void start(Stage stage) {
        // Create tab without a file — graphView will NOT be built,
        // so navigateToElement takes the deferred path.
        tab = new XsdUnifiedTab(null);
        stage.show();
    }

    @Test
    void navigateToElement_storesPendingWhenNoGraphView() {
        // graphView is null (no file loaded) — should store as pending
        tab.navigateToElement("Root");
        assertEquals("Root", tab.getPendingNavigationElement());
    }

    @Test
    void navigateToElement_nullElementDoesNothing() {
        tab.navigateToElement(null);
        assertNull(tab.getPendingNavigationElement());
    }

    @Test
    void navigateToElement_emptyElementDoesNothing() {
        tab.navigateToElement("");
        assertNull(tab.getPendingNavigationElement());
    }

    @Test
    void navigateToElement_overwritesPreviousPending() {
        tab.navigateToElement("First");
        tab.navigateToElement("Second");
        assertEquals("Second", tab.getPendingNavigationElement());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.unified.XsdUnifiedTabNavigationTest"`
Expected: FAIL — `navigateToElement` and `getPendingNavigationElement` do not exist yet (compilation error).

- [ ] **Step 3: Implement `navigateToElement` with deferred navigation**

In `XsdUnifiedTab.java`, add a `pendingNavigationElement` field and the `navigateToElement()` method. The key design: always set `pendingNavigationElement` first, then let `parseAndBuildGraphView` consume it if a rebuild is triggered. If no rebuild is triggered and `graphView` already exists, consume immediately as a fallback.

Add field near line 85 (after `private boolean syncingViews = false;`):

```java
private String pendingNavigationElement;
```

Add methods after the `discardChanges()` method (around line 465):

```java
/**
 * Navigates to a specific element in the XSD graphic view.
 * If the graphic view is not yet built, stores the element name for deferred navigation
 * (consumed when parseAndBuildGraphView succeeds).
 *
 * @param elementName the element to navigate to
 */
public void navigateToElement(String elementName) {
    if (elementName == null || elementName.isEmpty()) {
        return;
    }

    // Store pending navigation — parseAndBuildGraphView will consume it
    // if the graphic tab switch triggers a rebuild
    pendingNavigationElement = elementName;

    // Switch to graphic tab to show the navigation result
    if (currentViewMode == ViewMode.TABS) {
        viewTabPane.getSelectionModel().select(graphicTab);
    }

    // If graphView already exists and no rebuild was triggered
    // (parseAndBuildGraphView would have consumed pendingNavigationElement),
    // navigate immediately
    if (pendingNavigationElement != null && graphView != null) {
        String name = pendingNavigationElement;
        pendingNavigationElement = null;
        Platform.runLater(() -> graphView.navigateToElement(name));
    }
}

/**
 * Returns the pending navigation element name, or null if none.
 * Package-private for testing.
 */
String getPendingNavigationElement() {
    return pendingNavigationElement;
}
```

Then, in `parseAndBuildGraphView()`, after the graph view is successfully built (after the line `setGraphicViewContent(graphView);`, around line 294), consume any pending navigation:

```java
// Handle deferred navigation
if (pendingNavigationElement != null) {
    String elementName = pendingNavigationElement;
    pendingNavigationElement = null;
    Platform.runLater(() -> graphView.navigateToElement(elementName));
}
```

This block goes right before the existing `onEditorContextChangedCallback` block.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.unified.XsdUnifiedTabNavigationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/unified/XsdUnifiedTab.java \
        src/test/java/org/fxt/freexmltoolkit/controls/unified/XsdUnifiedTabNavigationTest.java
git commit -m "feat: add navigateToElement to XsdUnifiedTab with deferred navigation"
```

---

## Task 2: Add `openXsdFileAndNavigate` to `UnifiedEditorTabManager`

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManager.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManagerNavigationTest.java`

- [ ] **Step 1: Write the failing test**

Create test file `src/test/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManagerNavigationTest.java`:

```java
package org.fxt.freexmltoolkit.controls.unified;

import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UnifiedEditorTabManager.openXsdFileAndNavigate().
 * Requires JavaFX Application Thread (TestFX).
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedEditorTabManagerNavigationTest {

    private TabPane tabPane;
    private UnifiedEditorTabManager tabManager;

    private static final String SIMPLE_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified">
                <xs:element name="Root" type="RootType"/>
                <xs:complexType name="RootType">
                    <xs:sequence>
                        <xs:element name="Child" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:schema>
            """;

    @Start
    void start(Stage stage) {
        tabPane = new TabPane();
        tabManager = new UnifiedEditorTabManager(tabPane);
        stage.show();
    }

    @Test
    void openXsdFileAndNavigate_opensNewTab(@TempDir Path tempDir) throws IOException {
        File xsdFile = tempDir.resolve("test.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        XsdUnifiedTab result = tabManager.openXsdFileAndNavigate(xsdFile, "Root");

        assertNotNull(result);
        assertEquals(1, tabManager.getTabCount());
        assertInstanceOf(XsdUnifiedTab.class, tabManager.getCurrentTab());
    }

    @Test
    void openXsdFileAndNavigate_reusesExistingTab(@TempDir Path tempDir) throws IOException {
        File xsdFile = tempDir.resolve("test.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        // Open the same file twice with different element targets
        XsdUnifiedTab first = tabManager.openXsdFileAndNavigate(xsdFile, "Root");
        XsdUnifiedTab second = tabManager.openXsdFileAndNavigate(xsdFile, "Child");

        // Should still be 1 tab (reused), same instance
        assertEquals(1, tabManager.getTabCount());
        assertSame(first, second);
    }

    @Test
    void openXsdFileAndNavigate_nullFileReturnsNull() {
        assertNull(tabManager.openXsdFileAndNavigate(null, "Root"));
        assertEquals(0, tabManager.getTabCount());
    }

    @Test
    void openXsdFileAndNavigate_nonExistentFileReturnsNull(@TempDir Path tempDir) {
        File nonExistent = tempDir.resolve("missing.xsd").toFile();
        assertNull(tabManager.openXsdFileAndNavigate(nonExistent, "Root"));
        assertEquals(0, tabManager.getTabCount());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.unified.UnifiedEditorTabManagerNavigationTest"`
Expected: FAIL — `openXsdFileAndNavigate` does not exist yet (compilation error).

- [ ] **Step 3: Implement `openXsdFileAndNavigate`**

In `UnifiedEditorTabManager.java`, add the following method after the `openFile()` method (around line 98):

```java
/**
 * Opens an XSD file and navigates to a specific element in its graphic view.
 * If the file is already open, switches to the existing tab and navigates.
 * Used by Go to Definition from XML tabs within the Unified Editor.
 *
 * @param xsdFile     the XSD file to open
 * @param elementName the element name to navigate to in the graphic view
 * @return the XSD tab, or null if opening failed
 */
public XsdUnifiedTab openXsdFileAndNavigate(File xsdFile, String elementName) {
    if (xsdFile == null || !xsdFile.exists()) {
        logger.warn("Cannot navigate: XSD file is null or does not exist");
        return null;
    }

    // Open the file (or switch to existing tab)
    AbstractUnifiedEditorTab tab = openFile(xsdFile);

    if (tab instanceof XsdUnifiedTab xsdTab) {
        xsdTab.navigateToElement(elementName);
        return xsdTab;
    }

    logger.warn("Opened file is not an XSD tab: {}", xsdFile.getName());
    return null;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.unified.UnifiedEditorTabManagerNavigationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManager.java \
        src/test/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManagerNavigationTest.java
git commit -m "feat: add openXsdFileAndNavigate to UnifiedEditorTabManager"
```

---

## Task 3: Rewire Go to Definition handler for Unified Editor

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/unified/XmlUnifiedTab.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManager.java`

- [ ] **Step 1: Add `setGoToDefinitionHandler` to `XmlUnifiedTab`**

In `XmlUnifiedTab.java`, add a new method near `setMainController()` (around line 712):

```java
/**
 * Sets a custom Go to Definition handler.
 * This decouples the navigation behavior from MainController,
 * allowing the Unified Editor to handle navigation internally.
 *
 * @param handler the handler to invoke on Go to Definition
 */
public void setGoToDefinitionHandler(
        java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.editor.core.NavigationRequest> handler) {
    if (handler != null && textEditor != null) {
        textEditor.getEditorContext().setGoToDefinitionHandler(handler);
        logger.debug("Custom Go to Definition handler set for XmlUnifiedTab");
    }
}
```

- [ ] **Step 2: Change wiring in `UnifiedEditorTabManager`**

In `UnifiedEditorTabManager.java`, modify the `createTab()` method. Replace the existing MainController wiring block (lines 133-136):

```java
// Wire MainController for cross-tab navigation
if (mainController != null && tab instanceof XmlUnifiedTab xmlTab) {
    xmlTab.setMainController(mainController);
}
```

With:

```java
// Wire Go to Definition for in-editor navigation
if (tab instanceof XmlUnifiedTab xmlTab) {
    xmlTab.setGoToDefinitionHandler(request ->
            openXsdFileAndNavigate(request.xsdFile(), request.elementName())
    );
}
```

Note: The `mainController != null` guard is removed because the handler no longer depends on MainController.

Similarly, modify `setMainController()` (lines 55-63). Replace the loop body:

```java
for (AbstractUnifiedEditorTab tab : openTabs.values()) {
    if (tab instanceof XmlUnifiedTab xmlTab) {
        xmlTab.setMainController(mainController);
    }
}
```

With:

```java
for (AbstractUnifiedEditorTab tab : openTabs.values()) {
    if (tab instanceof XmlUnifiedTab xmlTab) {
        xmlTab.setGoToDefinitionHandler(request ->
                openXsdFileAndNavigate(request.xsdFile(), request.elementName())
        );
    }
}
```

- [ ] **Step 3: Run all existing tests to verify nothing is broken**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/unified/XmlUnifiedTab.java \
        src/main/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorTabManager.java
git commit -m "feat: rewire Go to Definition in Unified Editor to open XSD in same editor"
```

---

## Task 4: Integration test

**Files:**
- Create: `src/test/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorGoToDefinitionIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

Create `src/test/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorGoToDefinitionIntegrationTest.java`:

```java
package org.fxt.freexmltoolkit.controls.unified;

import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: XML tab in Unified Editor triggers Go to Definition,
 * which opens the XSD in a new XsdUnifiedTab within the same editor.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedEditorGoToDefinitionIntegrationTest {

    private TabPane tabPane;
    private UnifiedEditorTabManager tabManager;

    private static final String SIMPLE_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified">
                <xs:element name="Root" type="xs:string"/>
            </xs:schema>
            """;

    @Start
    void start(Stage stage) {
        tabPane = new TabPane();
        tabManager = new UnifiedEditorTabManager(tabPane);
        stage.show();
    }

    @Test
    void goToDefinition_opensXsdInSameEditor(@TempDir Path tempDir) throws IOException {
        // Setup: create XSD file on disk
        File xsdFile = tempDir.resolve("schema.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        // Setup: open an XML file first
        File xmlFile = tempDir.resolve("test.xml").toFile();
        Files.writeString(xmlFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Root>Hello</Root>
                """);
        tabManager.openFile(xmlFile);
        assertEquals(1, tabManager.getTabCount());

        // Act: simulate Go to Definition by calling openXsdFileAndNavigate
        // (this is what the handler wired in createTab now does)
        XsdUnifiedTab xsdTab = tabManager.openXsdFileAndNavigate(xsdFile, "Root");

        // Assert: XSD opened as a new tab in the same editor
        assertNotNull(xsdTab);
        assertEquals(2, tabManager.getTabCount());

        // Assert: the XSD tab is now selected
        assertEquals(xsdTab, tabManager.getCurrentTab());
    }

    @Test
    void goToDefinition_reusesExistingXsdTab(@TempDir Path tempDir) throws IOException {
        File xsdFile = tempDir.resolve("schema.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        // Open XSD tab once
        tabManager.openXsdFileAndNavigate(xsdFile, "Root");
        assertEquals(1, tabManager.getTabCount());

        // Go to definition again for same XSD — should reuse tab
        tabManager.openXsdFileAndNavigate(xsdFile, "Root");
        assertEquals(1, tabManager.getTabCount());
    }

    @Test
    void goToDefinition_handlerWiredOnXmlTab(@TempDir Path tempDir) throws IOException {
        // Setup: create files
        File xsdFile = tempDir.resolve("schema.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        File xmlFile = tempDir.resolve("test.xml").toFile();
        Files.writeString(xmlFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Root>Hello</Root>
                """);

        // Open XML tab — Go to Definition handler should be wired automatically
        AbstractUnifiedEditorTab xmlTab = tabManager.openFile(xmlFile);
        assertInstanceOf(XmlUnifiedTab.class, xmlTab);

        // Verify the handler is wired by checking editorContext has a handler set
        XmlUnifiedTab xml = (XmlUnifiedTab) xmlTab;
        assertNotNull(xml.getTextEditor().getEditorContext().getGoToDefinitionHandler());
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.unified.UnifiedEditorGoToDefinitionIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/fxt/freexmltoolkit/controls/unified/UnifiedEditorGoToDefinitionIntegrationTest.java
git commit -m "test: add integration test for Unified Editor Go to Definition"
```

---

## Summary of Changes

| File | Change |
|------|--------|
| `XsdUnifiedTab.java` | Add `pendingNavigationElement` field, `navigateToElement(String)` method, `getPendingNavigationElement()` for testing, consume pending navigation in `parseAndBuildGraphView()` |
| `UnifiedEditorTabManager.java` | Add `openXsdFileAndNavigate(File, String)`, rewire `createTab()` and `setMainController()` to use in-editor navigation |
| `XmlUnifiedTab.java` | Add `setGoToDefinitionHandler(Consumer<NavigationRequest>)` |
| 3 test files | Navigation, tab manager, and integration tests |

**Key design decisions:**
- Deferred navigation via `pendingNavigationElement` (same proven pattern as `XsdController`)
- Always set `pendingNavigationElement` first, then let `parseAndBuildGraphView` consume it if a rebuild is triggered; fallback to immediate navigation if graphView already exists and no rebuild occurred
- `openFile()` reuse means duplicate-tab prevention works automatically
- `XmlUnifiedTab.setMainController()` is kept for backward compatibility but no longer used by the Unified Editor wiring
- No changes to `MainController` or the dedicated XSD tab — the existing "Go to Definition from regular XML editor" behavior is untouched
