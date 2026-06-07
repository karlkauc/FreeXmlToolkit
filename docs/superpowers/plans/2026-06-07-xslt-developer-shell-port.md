# XSLT Developer → Unified Shell Port — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the legacy XSLT Developer's four advanced features (interactive debugger, multi-file batch processing, profiler, debug trace) into the Unified Shell's Transform activity so `xsltDeveloper` becomes redundant and can be retired afterwards.

**Architecture:** Reuse the existing `debugger/` domain, `BreakpointGutterFactory`, and `XsltTransformationEngine` (which already has `transformWithDebugSession`, profiling, and batch methods). Add shell-native panels/views + UI-free runners following the established `TransformRunner`/`SchematronCheckResultView`/`openToolTab` patterns. Add one thin gutter seam on `EditorView` so the shell editor can host breakpoint markers.

**Tech Stack:** Java 25, JavaFX 24, RichTextFX `CodeArea`, Saxon HE 12.9, JUnit 5 + TestFX (Monocle headless).

**Spec:** `docs/superpowers/specs/2026-06-07-xslt-developer-shell-port-design.md`

---

## Conventions used in this plan

- All new shell classes live in `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/` (new package) except the `TransformRunner`/`TransformPanel`/`EditorHost`/`EditorView`/`XmlEditorView` edits, which stay in `controls/shell/editor/`.
- Tests live under `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/...` mirroring the source.
- Run a single test class with: `./gradlew test --tests "FullyQualifiedClassName"`.
- TestFX UI tests extend `ApplicationExtension` like the existing `TransformPanelTest`; pure-logic tests need no JavaFX.
- Commit after each task with the message shown. Push at the end of each task (the repo convention is commit+push after a working increment).

---

## Task 1: Editor gutter seam

Expose a way to attach a breakpoint gutter to the active shell editor. `XmlCodeEditorV2` already has `setExtraGutterFactory(IntFunction<Node>)` + `refreshGutter()`; we surface them through `EditorView` → `EditorHost`. JSON returns `false` (unsupported).

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorView.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/XmlEditorView.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostGutterTest.java`

- [ ] **Step 1: Write the failing test**

Create `EditorHostGutterTest.java` (uses the temp-file + `WaitForAsyncUtils` pattern of the existing `EditorHostXmlEditTest`; `EditorHost` has a no-arg constructor and only `openFile(Path)`/`openFile(File)`):

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class EditorHostGutterTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
    }

    @Test
    void attachesGutterToXsltEditor(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("sheet.xslt");
        Files.writeString(xsl,
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitForFxEvents();
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveEditorGutterFactory(line -> (Node) new Region()));
        assertTrue(applied, "XSLT editor should accept a gutter factory");
    }

    @Test
    void jsonEditorRejectsGutter(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitForFxEvents();
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveEditorGutterFactory(line -> (Node) new Region()));
        assertFalse(applied, "JSON editor has no gutter support");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostGutterTest"`
Expected: compile error — `setActiveEditorGutterFactory` does not exist.

- [ ] **Step 3: Add the gutter methods to `EditorView`**

In `EditorView.java`, after `invalidateIntelliSenseCache()` (before the closing brace), add:

```java
    /**
     * Installs (or clears, when {@code factory} is null) an extra gutter component
     * (breakpoint dots / execution arrow) on the editor's line strip.
     *
     * @return {@code true} if this editor supports a gutter (XML family); {@code false} otherwise.
     */
    default boolean setExtraGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        return false;
    }

    /** Re-renders the gutter (e.g. after breakpoint/execution-line changes). No-op if unsupported. */
    default void refreshGutter() {
    }
```

- [ ] **Step 4: Override them in `XmlEditorView`**

In `XmlEditorView.java`, after `invalidateIntelliSenseCache()`, add:

```java
    @Override
    public boolean setExtraGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        editor.setExtraGutterFactory(factory);
        return true;
    }

    @Override
    public void refreshGutter() {
        editor.refreshGutter();
    }
```

- [ ] **Step 5: Add the host wrappers to `EditorHost`**

In `EditorHost.java`, right after the `moveActiveCaretTo(int position)` method (around line 1240), add:

```java
    /**
     * Attaches an extra gutter factory (breakpoint markers / execution arrow) to the
     * currently active editor tab.
     *
     * @return {@code true} if the active tab is an XML-family editor that accepted it.
     */
    public boolean setActiveEditorGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et && et.view.setExtraGutterFactory(factory);
    }

    /** Re-renders the gutter on a specific open document's editor tab (if open). */
    public void refreshGutterFor(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                et.view.refreshGutter();
                return;
            }
        }
    }

    /** Attaches a gutter factory to a specific open document's editor tab (if open). */
    public boolean setGutterFactoryFor(OpenDocument document,
            java.util.function.IntFunction<javafx.scene.Node> factory) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                return et.view.setExtraGutterFactory(factory);
            }
        }
        return false;
    }
```

> `tabPane`, `EditorTab`, and `et.document`/`et.view` are all already used elsewhere in this file (e.g. `getActiveCodeArea` at line 1190, `selectDocument` iterating `et.document`).

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostGutterTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/XmlEditorView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostGutterTest.java
git commit -m "feat(shell): expose editor gutter factory for the XSLT debugger

EditorView.setExtraGutterFactory/refreshGutter (default no-op) delegated by
XmlEditorView to XmlCodeEditorV2; EditorHost gains active/by-document wrappers.
Foundation for the shell XSLT debugger.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Interactive debugger

Reuses the `debugger/` domain + `BreakpointGutterFactory` + `XsltTransformationEngine.transformWithDebugSession`. Adds a UI-free `XsltDebugController`, four small shell-native panels, a composing `XsltDebugView` tool tab, and the entry wiring (`EditorHost.startXsltDebug` + a "Debug" button in the Transform panel's new Advanced section).

**Important UX note:** the XSLT document tab and the Debug tool tab are siblings in the same center `TabPane`, so only one is visible at a time. On pause we update the gutter arrow + populate the Debug panels and show "Paused at line N"; a "Show in editor" button jumps to the XSLT tab on demand. We do NOT steal focus to the editor on every pause (that would hide the step controls).

### 2a. `XsltDebugController` (UI-free orchestration)

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/XsltDebugController.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/XsltDebugControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.Executors;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class XsltDebugControllerTest {

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <out><xsl:value-of select="count(//item)"/></out>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    @Timeout(20)
    void pausesAtBreakpointThenRunsToCompletion() throws Exception {
        XsltDebugController controller =
                new XsltDebugController(Executors.newSingleThreadExecutor());
        DebugSession session = controller.getSession();
        // Breakpoint on the <xsl:value-of> line (line 4 in the block above, 1-based).
        session.addBreakpoint(new Breakpoint("", 4, true));

        controller.start(XML, XSLT, Map.of(), OutputFormat.XML);

        // Wait until the Saxon thread blocks at the breakpoint.
        waitForState(session, DebugSession.State.PAUSED);
        assertNotNull(session.getPausedSnapshot(), "snapshot captured at pause");

        controller.continueRun();

        // After continue, the transform completes and the session closes (IDLE).
        waitForState(session, DebugSession.State.IDLE);
        assertNotNull(controller.getLastResult(), "result produced");
        assertTrue(controller.getLastResult().isSuccess(), "transform succeeded");
    }

    private static void waitForState(DebugSession session, DebugSession.State target)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (session.getState() != target && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        if (session.getState() != target) {
            throw new AssertionError("Timed out waiting for state " + target
                    + " (was " + session.getState() + ")");
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugControllerTest"`
Expected: compile error — `XsltDebugController` does not exist.

- [ ] **Step 3: Implement `XsltDebugController`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.Map;
import java.util.concurrent.Executor;

import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * UI-free orchestration of an interactive XSLT debug run: owns a {@link DebugSession},
 * launches {@link XsltTransformationEngine#transformWithDebugSession} on a background
 * executor, and forwards step requests. The UI subscribes to the session's
 * {@code "state"} property to react to pauses.
 */
public final class XsltDebugController {

    private final DebugSession session = new DebugSession();
    private final Executor executor;
    private volatile XsltTransformationResult lastResult;

    /** Uses the shared application executor. */
    public XsltDebugController() {
        this(org.fxt.freexmltoolkit.FxtGui.executorService);
    }

    /** Test/seam constructor with an injectable executor. */
    public XsltDebugController(Executor executor) {
        this.executor = executor;
    }

    public DebugSession getSession() {
        return session;
    }

    public XsltTransformationResult getLastResult() {
        return lastResult;
    }

    /** Starts the debug transform on the background executor (breakpoints already set on the session). */
    public void start(String xml, String xsltContent, Map<String, Object> parameters, OutputFormat format) {
        session.startSession();
        executor.execute(() ->
                lastResult = XsltTransformationEngine.getInstance()
                        .transformWithDebugSession(xml, xsltContent, parameters, format, session));
    }

    public void continueRun() { session.requestContinue(); }
    public void stepInto()    { session.requestStepInto(); }
    public void stepOver()    { session.requestStepOver(); }
    public void stepOut()     { session.requestStepOut(); }
    public void stop()        { session.requestStop(); }

    /** Aborts any in-flight run and resets the session. */
    public void close()       { session.close(); }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugControllerTest"`
Expected: PASS. (If the breakpoint line is off by one for this Saxon build, adjust the test's line number to the actual `<xsl:value-of>` line; the controller is correct.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/XsltDebugController.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/XsltDebugControllerTest.java
git commit -m "feat(shell): UI-free XsltDebugController orchestrating DebugSession + engine

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### 2b. The four debugger panels

Small read-only/edit panels populated from `PausedSnapshot` data. All extend `VBox` and use `fxt-side-panel-content` styling for consistency.

**Files:**
- Create: `.../debug/BreakpointsView.java`
- Create: `.../debug/VariablesView.java`
- Create: `.../debug/CallStackView.java`
- Create: `.../debug/WatchView.java`
- Test: `.../debug/DebugPanelsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class DebugPanelsTest {

    @Start
    void start(Stage stage) {
    }

    @Test
    void variablesViewShowsBindings() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            VariablesView view = new VariablesView();
            view.setVariables(List.of(
                    new VariableBinding("count", "2", "xs:integer", VariableScope.LOCAL)));
            return view.getRowCount();
        });
        assertEquals(1, rows);
    }

    @Test
    void callStackViewShowsFrames() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            CallStackView view = new CallStackView();
            view.setFrames(List.of(new DebugStackFrame("template match=/", "sheet.xslt", 4, List.of())));
            return view.getRowCount();
        });
        assertEquals(1, rows);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugPanelsTest"`
Expected: compile error — views do not exist.

- [ ] **Step 3: Implement `VariablesView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.VariableBinding;

/** Read-only table of the variables in scope at the current pause point. */
public class VariablesView extends VBox {

    private final TableView<VariableBinding> table = new TableView<>();

    public VariablesView() {
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("VARIABLES");
        title.getStyleClass().add("fxt-side-panel-title");

        table.getColumns().add(col("Name", b -> b.name(), 120));
        table.getColumns().add(col("Value", b -> b.value(), -1));
        table.getColumns().add(col("Type", b -> b.type(), 110));
        table.getColumns().add(col("Scope", b -> b.scope().name(), 80));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Not paused."));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(title, table);
    }

    public void setVariables(List<VariableBinding> variables) {
        table.getItems().setAll(variables == null ? List.of() : variables);
    }

    public void clear() {
        table.getItems().clear();
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    private static TableColumn<VariableBinding, String> col(String title,
            java.util.function.Function<VariableBinding, String> value, double prefWidth) {
        TableColumn<VariableBinding, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
```

- [ ] **Step 4: Implement `CallStackView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.DebugStackFrame;

/** Read-only call stack list; double-clicking a frame jumps the editor to its line. */
public class CallStackView extends VBox {

    private final ListView<DebugStackFrame> list = new ListView<>();
    private IntConsumer onJumpToLine = line -> { };

    public CallStackView() {
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("CALL STACK");
        title.getStyleClass().add("fxt-side-panel-title");

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(DebugStackFrame frame, boolean empty) {
                super.updateItem(frame, empty);
                setText(empty || frame == null ? null
                        : frame.description() + "  (line " + frame.lineNumber() + ")");
            }
        });
        list.setPlaceholder(new Label("Not paused."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DebugStackFrame f = list.getSelectionModel().getSelectedItem();
                if (f != null) {
                    onJumpToLine.accept(f.lineNumber());
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, list);
    }

    public void setFrames(List<DebugStackFrame> frames) {
        list.getItems().setAll(frames == null ? List.of() : frames);
    }

    public void clear() {
        list.getItems().clear();
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }
}
```

- [ ] **Step 5: Implement `BreakpointsView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;

/** Lists the session's breakpoints with remove/clear; double-click jumps to the line. */
public class BreakpointsView extends VBox {

    private final ListView<Breakpoint> list = new ListView<>();
    private final DebugSession session;
    private IntConsumer onJumpToLine = line -> { };
    private Runnable onChanged = () -> { };

    public BreakpointsView(DebugSession session) {
        this.session = session;
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("BREAKPOINTS");
        title.getStyleClass().add("fxt-side-panel-title");

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Breakpoint bp, boolean empty) {
                super.updateItem(bp, empty);
                setText(empty || bp == null ? null : "line " + bp.lineNumber()
                        + (bp.filePath().isEmpty() ? "" : "  " + shortName(bp.filePath())));
            }
        });
        list.setPlaceholder(new Label("No breakpoints."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Breakpoint bp = list.getSelectionModel().getSelectedItem();
                if (bp != null) {
                    onJumpToLine.accept(bp.lineNumber());
                }
            }
        });

        Button remove = new Button("Remove");
        remove.getStyleClass().add("fxt-tool-button");
        remove.setOnAction(e -> {
            Breakpoint bp = list.getSelectionModel().getSelectedItem();
            if (bp != null) {
                session.removeBreakpoint(bp.filePath(), bp.lineNumber());
                refresh();
                onChanged.run();
            }
        });
        Button clear = new Button("Clear all");
        clear.getStyleClass().add("fxt-tool-button");
        clear.setOnAction(e -> {
            session.clearBreakpoints();
            refresh();
            onChanged.run();
        });

        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, list, new HBox(8, remove, clear));
        refresh();
    }

    /** Re-reads the breakpoints from the session into the list. */
    public final void refresh() {
        List<Breakpoint> sorted = new ArrayList<>(session.getBreakpoints());
        sorted.sort(java.util.Comparator.comparingInt(Breakpoint::lineNumber));
        list.getItems().setAll(sorted);
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }

    /** Invoked after the user removes/clears breakpoints (so the gutter can refresh). */
    public void setOnChanged(Runnable handler) {
        this.onChanged = handler == null ? () -> { } : handler;
    }

    private static String shortName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
```

- [ ] **Step 6: Implement `WatchView`**

Watches are evaluated against the active XML document text (document-root context) via `XmlService.getXmlFromXpath` — the same proven path the Transform panel's XPath field uses. The current pause `contextItem` string is shown as an informational label.

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;

/** Add/remove XPath watch expressions, evaluated against the active XML on each pause. */
public class WatchView extends VBox {

    /** One watch row: the expression and its last evaluated value/error. */
    public static final class WatchRow {
        private final String expression;
        private String value = "<not evaluated>";

        public WatchRow(String expression) {
            this.expression = expression;
        }

        public String getExpression() { return expression; }
        public String getValue() { return value; }
    }

    private final TableView<WatchRow> table = new TableView<>();
    private final TextField input = new TextField();
    private Supplier<String> xmlSupplier = () -> "";

    public WatchView() {
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("WATCH");
        title.getStyleClass().add("fxt-side-panel-title");

        table.getColumns().add(col("Expression", WatchRow::getExpression, 180));
        table.getColumns().add(col("Value", WatchRow::getValue, -1));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No watches."));
        VBox.setVgrow(table, Priority.ALWAYS);

        input.setPromptText("XPath to watch, e.g. count(//item)");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> addWatch());
        Button add = new Button("Add");
        add.getStyleClass().add("fxt-tool-button");
        add.setOnAction(e -> addWatch());
        Button remove = new Button("Remove");
        remove.getStyleClass().add("fxt-tool-button");
        remove.setOnAction(e -> {
            WatchRow r = table.getSelectionModel().getSelectedItem();
            if (r != null) {
                table.getItems().remove(r);
            }
        });

        getChildren().addAll(title, table, new HBox(6, input, add, remove));
    }

    /** Supplies the XML text watches are evaluated against (the active document). */
    public void setXmlSupplier(Supplier<String> supplier) {
        this.xmlSupplier = supplier == null ? () -> "" : supplier;
    }

    private void addWatch() {
        String expr = input.getText();
        if (expr == null || expr.isBlank()) {
            return;
        }
        table.getItems().add(new WatchRow(expr.trim()));
        input.clear();
        evaluateAll();
    }

    /** Re-evaluates every watch against the current XML (call on each pause). */
    public void evaluateAll() {
        String xml = xmlSupplier.get();
        for (WatchRow row : table.getItems()) {
            try {
                String result = ServiceRegistry.get(XmlService.class)
                        .getXmlFromXpath(xml, row.expression);
                row.value = result == null ? "" : result;
            } catch (Throwable t) {
                row.value = "ERROR: " + t.getMessage();
            }
        }
        table.refresh();
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    private static TableColumn<WatchRow, String> col(String title,
            java.util.function.Function<WatchRow, String> value, double prefWidth) {
        TableColumn<WatchRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
```

- [ ] **Step 7: Run to verify the panels test passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugPanelsTest"`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/VariablesView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/CallStackView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/BreakpointsView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/WatchView.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/DebugPanelsTest.java
git commit -m "feat(shell): debugger panels (variables, call stack, breakpoints, watch)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### 2c. `XsltDebugView` (composing tool tab) + `EditorHost.startXsltDebug` + Transform-panel entry

**Files:**
- Create: `.../debug/XsltDebugView.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java`
- Test: `.../debug/XsltDebugViewTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class XsltDebugViewTest {

    private XsltDebugController controller;

    @Start
    void start(Stage stage) {
        controller = new XsltDebugController(Runnable::run);
    }

    @Test
    void buildsWithAllSections() {
        XsltDebugView view = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> new XsltDebugView(controller, "", () -> "", line -> { }, () -> { }));
        assertNotNull(view.getBreakpointsView());
        assertNotNull(view.getVariablesView());
        assertNotNull(view.getCallStackView());
        assertNotNull(view.getWatchView());
        assertTrue(view.getChildren().size() >= 2, "toolbar + content");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugViewTest"`
Expected: compile error — `XsltDebugView` does not exist.

- [ ] **Step 3: Implement `XsltDebugView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;

/**
 * The Debug tool tab: a step-controls toolbar over the four debugger panels
 * (breakpoints / variables / call stack / watch). Subscribes to the session
 * state and refreshes on each pause.
 */
public class XsltDebugView extends VBox {

    private final XsltDebugController controller;
    private final BreakpointsView breakpointsView;
    private final VariablesView variablesView = new VariablesView();
    private final CallStackView callStackView = new CallStackView();
    private final WatchView watchView = new WatchView();
    private final Label status = new Label("Ready.");

    private final IntConsumer onCurrentLine;  // updates the gutter arrow (line, or -1 to clear)
    private final Runnable onShowInEditor;    // jumps the editor to the paused line

    public XsltDebugView(XsltDebugController controller,
            String xsltFilePath,
            Supplier<String> activeXmlSupplier,
            IntConsumer onCurrentLine,
            Runnable onShowInEditor) {
        this.controller = controller;
        this.onCurrentLine = onCurrentLine == null ? line -> { } : onCurrentLine;
        this.onShowInEditor = onShowInEditor == null ? () -> { } : onShowInEditor;
        this.breakpointsView = new BreakpointsView(controller.getSession());

        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().add("fxt-side-panel-content");

        watchView.setXmlSupplier(activeXmlSupplier);
        breakpointsView.setOnJumpToLine(line -> this.onShowInEditor.run());
        breakpointsView.setOnChanged(this.onShowInEditor::run);
        callStackView.setOnJumpToLine(line -> this.onShowInEditor.run());

        HBox toolbar = buildToolbar();
        status.getStyleClass().add("fxt-placeholder-text");

        SplitPane panels = new SplitPane(breakpointsView, variablesView, callStackView, watchView);
        panels.setDividerPositions(0.25, 0.5, 0.75);
        VBox.setVgrow(panels, Priority.ALWAYS);

        getChildren().addAll(toolbar, status, panels);

        controller.getSession().addPropertyChangeListener(evt -> {
            if (DebugSession.PROP_STATE.equals(evt.getPropertyName())) {
                Platform.runLater(() -> onStateChanged((DebugSession.State) evt.getNewValue()));
            }
        });
    }

    private HBox buildToolbar() {
        Button continueBtn = stepButton("Continue", "bi-play-fill", controller::continueRun);
        Button stepInto = stepButton("Step Into", "bi-box-arrow-in-down-right", controller::stepInto);
        Button stepOver = stepButton("Step Over", "bi-arrow-right-short", controller::stepOver);
        Button stepOut = stepButton("Step Out", "bi-box-arrow-up-right", controller::stepOut);
        Button stop = stepButton("Stop", "bi-stop-fill", controller::stop);
        Button showInEditor = stepButton("Show in editor", "bi-eye", onShowInEditor);
        return new HBox(6, continueBtn, stepInto, stepOver, stepOut, stop, showInEditor);
    }

    private Button stepButton(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private void onStateChanged(DebugSession.State state) {
        switch (state) {
            case PAUSED -> {
                PausedSnapshot snap = controller.getSession().getPausedSnapshot();
                if (snap != null) {
                    variablesView.setVariables(snap.variables());
                    callStackView.setFrames(snap.callStack());
                    watchView.evaluateAll();
                    onCurrentLine.accept(snap.lineNumber());
                    String ctx = snap.contextItem().isEmpty() ? "" : "  ·  " + snap.contextItem();
                    status.setText("Paused at line " + snap.lineNumber() + ctx);
                }
            }
            case RUNNING -> {
                onCurrentLine.accept(-1);
                status.setText("Running…");
            }
            case STOPPED -> {
                onCurrentLine.accept(-1);
                variablesView.clear();
                callStackView.clear();
                status.setText("Stopped.");
            }
            case IDLE -> {
                onCurrentLine.accept(-1);
                variablesView.clear();
                callStackView.clear();
                status.setText("Finished.");
            }
        }
    }

    public BreakpointsView getBreakpointsView() { return breakpointsView; }
    public VariablesView getVariablesView() { return variablesView; }
    public CallStackView getCallStackView() { return callStackView; }
    public WatchView getWatchView() { return watchView; }
    public String getStatusText() { return status.getText(); }
}
```

- [ ] **Step 4: Add `startXsltDebug` to `EditorHost`**

In `EditorHost.java`, after `setGutterFactoryFor(...)` (added in Task 1), add:

```java
    /**
     * Launches an interactive XSLT debug session: opens the stylesheet as an editor
     * document, attaches a breakpoint gutter, and opens the Debug tool tab.
     */
    public void startXsltDebug(java.io.File xsltFile, String xml,
            java.util.Map<String, Object> parameters,
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format) {
        // 1. Open (or focus) the stylesheet as an editor document.
        openFile(xsltFile);
        OpenDocument xsltDoc = getActiveDocument().orElse(null);
        if (xsltDoc == null) {
            return;
        }

        // 2. Build the debug controller + gutter and attach the gutter to the XSLT tab.
        var controller = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController();
        var gutter = new org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory(
                controller.getSession(), v -> refreshGutterFor(xsltDoc));
        gutter.setFilePath(xsltFile.getAbsolutePath());
        setGutterFactoryFor(xsltDoc, gutter);

        // 3. Open the Debug tool tab; wire current-line + show-in-editor back to this host.
        var debugView = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugView(
                controller,
                xsltFile.getAbsolutePath(),
                () -> getActiveText().orElse(xml),
                line -> { gutter.setCurrentExecutionLine(line); refreshGutterFor(xsltDoc); },
                () -> {
                    selectDocument(xsltDoc);
                    int line = controller.getSession().getPausedSnapshot() != null
                            ? controller.getSession().getPausedSnapshot().lineNumber() : 1;
                    if (line > 0) {
                        goToLine(line);
                    }
                });
        openToolTab("Debug", "bi-bug", debugView);

        // 4. Start the run (any breakpoints set via the gutter are already on the session).
        String xsltContent;
        try {
            xsltContent = java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            xsltContent = "";
        }
        controller.start(xml, xsltContent, parameters, format);
    }
```

> The user must set breakpoints by clicking the gutter before pressing Continue/Step — but since `start` runs immediately, in practice the user sets breakpoints first, then this method is called. For the MVP, breakpoints can be set on the XSLT gutter before launching debug (the gutter is attached before `start`), and the user can also Step Into from the first instruction. This matches the legacy "Run Debug" behavior.

- [ ] **Step 5: Add the Advanced section + Debug button to `TransformPanel`**

In `TransformPanel.java`, add a field and a method, and append the section to the layout.

After the existing field declarations (near line 69), add:

```java
    private final CheckBox profileCheck = new CheckBox("Profile");
    private final CheckBox traceCheck = new CheckBox("Trace");
```

In the constructor, just before the final `getChildren().addAll(...)` call (line 171), build the Advanced section:

```java
        Label advancedLabel = new Label("ADVANCED");
        advancedLabel.getStyleClass().add("fxt-side-panel-title");
        Button debugButton = button("Debug", "bi-bug", this::startDebug);
        Button batchButton = button("Batch…", "bi-files", this::openBatch);
        HBox advancedButtons = new HBox(6, debugButton, batchButton);
        HBox advancedChecks = new HBox(12, profileCheck, traceCheck);
```

Then add these to the `getChildren().addAll(...)` list (append before `resultHeader`):

```java
                advancedLabel, advancedButtons, advancedChecks,
```

Add the `startDebug` method (the `openBatch` method comes in Task 3 — add a temporary stub now and replace it then):

```java
    /** Launches the interactive XSLT debugger for the active XML + selected stylesheet. */
    public void startDebug() {
        if (xsltFile == null) {
            output.setText("Select an XSLT stylesheet first.");
            return;
        }
        if (editorHost.getActiveDocument().isEmpty()) {
            output.setText("No document open.");
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        editorHost.startXsltDebug(xsltFile, xml, collectParameters(),
                outputFormat.getValue() != null ? outputFormat.getValue() : OutputFormat.XML);
    }

    /** Opens the batch-transform tool tab. (Implemented in Task 3.) */
    public void openBatch() {
        output.setText("Batch processing is not yet available.");
    }
```

- [ ] **Step 6: Run the view test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugViewTest"`
Expected: PASS.

- [ ] **Step 7: Verify the project still compiles + the editor seam test passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostGutterTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.TransformPanelTest"`
Expected: PASS (TransformPanel still builds with the Advanced section).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/XsltDebugView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/XsltDebugViewTest.java
git commit -m "feat(shell): XSLT debugger tool tab wired into the Transform panel

Debug button opens the stylesheet as a document, attaches the breakpoint gutter,
and opens a Debug tool tab with step controls + variables/call-stack/breakpoints/watch.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 3: Multi-file batch processing

UI-free `BatchTransformRunner` looping over files (XSLT via `TransformRunner`, XQuery via the engine's per-file query), plus a `BatchTransformView` tool tab. Replaces the Task-2 `openBatch` stub.

**Files:**
- Create: `.../debug/BatchFileResult.java`
- Create: `.../debug/BatchTransformRunner.java`
- Create: `.../debug/BatchTransformView.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java`
- Test: `.../debug/BatchTransformRunnerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchTransformRunnerTest {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="count(//item)"/></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void runsXsltOverEveryFileAndReportsErrors(@TempDir Path dir) throws Exception {
        File good1 = write(dir, "a.xml", "<root><item/><item/></root>");
        File good2 = write(dir, "b.xml", "<root><item/></root>");
        File bad = write(dir, "c.xml", "<root><item></root>"); // malformed

        List<BatchFileResult> results = BatchTransformRunner.runXsltBatch(
                List.of(good1, good2, bad), XSLT, Map.of(), OutputFormat.TEXT);

        assertEquals(3, results.size());
        assertTrue(results.get(0).ok());
        assertEquals("2", results.get(0).output().strip());
        assertTrue(results.get(1).ok());
        assertFalse(results.get(2).ok(), "malformed XML should fail");
    }

    @Test
    void writeAllWritesOneFilePerResult(@TempDir Path dir) throws Exception {
        File in = write(dir, "a.xml", "<root><item/></root>");
        List<BatchFileResult> results = BatchTransformRunner.runXsltBatch(
                List.of(in), XSLT, Map.of(), OutputFormat.TEXT);
        Path out = Files.createDirectory(dir.resolve("out"));

        int written = BatchTransformRunner.writeAll(results, out, "txt");

        assertEquals(1, written);
        assertTrue(Files.exists(out.resolve("a.txt")));
    }

    private static File write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformRunnerTest"`
Expected: compile error — classes do not exist.

- [ ] **Step 3: Implement `BatchFileResult`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;

/** One file's outcome in a batch transform run. */
public record BatchFileResult(File file, String output, boolean ok, String error, long timeMs) {
}
```

- [ ] **Step 4: Implement `BatchTransformRunner`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * UI-free multi-file transform runner: applies one stylesheet / XQuery to each XML file
 * independently, collecting a {@link BatchFileResult} per file. Run off the UI thread.
 */
public final class BatchTransformRunner {

    private BatchTransformRunner() {
    }

    /** Applies {@code xsltContent} to every file; errors are captured per file, never thrown. */
    public static List<BatchFileResult> runXsltBatch(List<File> files, String xsltContent,
            Map<String, Object> parameters, OutputFormat format) {
        List<BatchFileResult> results = new ArrayList<>();
        for (File file : files) {
            long start = System.nanoTime();
            try {
                String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String out = TransformRunner.xsltTransform(xml, xsltContent, parameters, format);
                long ms = (System.nanoTime() - start) / 1_000_000;
                boolean ok = out != null && !out.startsWith("ERROR");
                results.add(new BatchFileResult(file, ok ? out : null, ok, ok ? null : out, ms));
            } catch (Exception e) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                results.add(new BatchFileResult(file, null, false, "ERROR: " + e.getMessage(), ms));
            }
        }
        return results;
    }

    /** Runs {@code xqueryContent} against every file's context independently. */
    public static List<BatchFileResult> runXQueryBatch(List<File> files, String xqueryContent,
            Map<String, Object> externalVariables, OutputFormat format) {
        List<BatchFileResult> results = new ArrayList<>();
        for (File file : files) {
            long start = System.nanoTime();
            try {
                String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String out = TransformRunner.runXQuery(xml, xqueryContent, externalVariables, format);
                long ms = (System.nanoTime() - start) / 1_000_000;
                boolean ok = out != null && !out.startsWith("ERROR");
                results.add(new BatchFileResult(file, ok ? out : null, ok, ok ? null : out, ms));
            } catch (Exception e) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                results.add(new BatchFileResult(file, null, false, "ERROR: " + e.getMessage(), ms));
            }
        }
        return results;
    }

    /**
     * Writes each successful result to {@code targetDir} as {@code <basename>.<extension>}.
     *
     * @return the number of files written
     */
    public static int writeAll(List<BatchFileResult> results, Path targetDir, String extension) {
        int written = 0;
        for (BatchFileResult r : results) {
            if (!r.ok() || r.output() == null) {
                continue;
            }
            String base = r.file().getName().replaceFirst("\\.[^.]+$", "");
            Path out = targetDir.resolve(base + "." + extension);
            try {
                Files.writeString(out, r.output(), StandardCharsets.UTF_8);
                written++;
            } catch (Exception ignored) {
                // skip unwritable files; caller reports the written count
            }
        }
        return written;
    }
}
```

- [ ] **Step 5: Run to verify the runner test passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformRunnerTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Implement `BatchTransformView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * Batch-transform tool tab: pick XML files, run the active stylesheet/XQuery over each,
 * inspect per-file results, and save them all to a directory.
 */
public class BatchTransformView extends VBox {

    /** Whether the batch runs the XSLT stylesheet or the XQuery script. */
    public enum Kind { XSLT, XQUERY }

    private final Kind kind;
    private final String transformContent;
    private final Map<String, Object> parameters;
    private final OutputFormat format;

    private final TableView<BatchFileResult> table = new TableView<>();
    private final List<File> inputFiles = new ArrayList<>();
    private final TextArea resultArea = new TextArea();
    private final Label summary = new Label("No files selected.");
    private List<BatchFileResult> lastResults = List.of();

    public BatchTransformView(Kind kind, String transformContent,
            Map<String, Object> parameters, OutputFormat format) {
        this.kind = kind;
        this.transformContent = transformContent == null ? "" : transformContent;
        this.parameters = parameters == null ? Map.of() : parameters;
        this.format = format == null ? OutputFormat.XML : format;

        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("BATCH (" + kind + ")");
        title.getStyleClass().add("fxt-side-panel-title");

        Button addFiles = button("Add Files…", "bi-file-earmark-plus", this::addFiles);
        Button addDir = button("Add Directory…", "bi-folder-plus", this::addDirectory);
        Button removeSel = button("Remove", "bi-x-circle", this::removeSelected);
        Button clear = button("Clear", "bi-trash", () -> { inputFiles.clear(); refreshFileTable(); });
        HBox fileButtons = new HBox(6, addFiles, addDir, removeSel, clear);

        table.getColumns().add(col("File", r -> r.file().getName(), 200));
        table.getColumns().add(col("Status", r -> r.ok() ? "OK" : "ERROR", 80));
        table.getColumns().add(col("ms", r -> Long.toString(r.timeMs()), 60));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Add XML files to process."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) ->
                resultArea.setText(sel == null ? "" : (sel.ok() ? sel.output() : sel.error())));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button run = button("Run Batch", "bi-play-fill", this::run);
        Button saveAll = button("Save All…", "bi-save", this::saveAll);
        HBox runButtons = new HBox(8, run, saveAll, summary);
        runButtons.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        resultArea.setEditable(false);
        resultArea.setPrefRowCount(8);
        resultArea.getStyleClass().add("fxt-transform-output");

        getChildren().addAll(title, fileButtons, table, runButtons,
                new Label("RESULT"), resultArea);
        refreshFileTable();
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add XML files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        List<File> chosen = chooser.showOpenMultipleDialog(getScene() != null ? getScene().getWindow() : null);
        if (chosen != null) {
            inputFiles.addAll(chosen);
            refreshFileTable();
        }
    }

    private void addDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add a directory of XML files");
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir != null && dir.isDirectory()) {
            File[] xmls = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
            if (xmls != null) {
                inputFiles.addAll(List.of(xmls));
                refreshFileTable();
            }
        }
    }

    private void removeSelected() {
        BatchFileResult sel = table.getSelectionModel().getSelectedItem();
        if (sel != null) {
            inputFiles.remove(sel.file());
            refreshFileTable();
        }
    }

    /** Shows the current input files (un-run) as placeholder rows. */
    private void refreshFileTable() {
        List<BatchFileResult> rows = new ArrayList<>();
        for (File f : inputFiles) {
            rows.add(new BatchFileResult(f, null, true, null, 0));
        }
        table.getItems().setAll(rows);
        summary.setText(inputFiles.size() + " file(s).");
    }

    /** Runs the batch off the UI thread and shows per-file outcomes. */
    public void run() {
        if (inputFiles.isEmpty()) {
            summary.setText("Add files first.");
            return;
        }
        List<File> files = new ArrayList<>(inputFiles);
        summary.setText("Running…");
        FxtGui.executorService.submit(() -> {
            List<BatchFileResult> results = kind == Kind.XQUERY
                    ? BatchTransformRunner.runXQueryBatch(files, transformContent, parameters, format)
                    : BatchTransformRunner.runXsltBatch(files, transformContent, parameters, format);
            long ok = results.stream().filter(BatchFileResult::ok).count();
            Platform.runLater(() -> {
                lastResults = results;
                table.getItems().setAll(results);
                summary.setText(ok + " ok · " + (results.size() - ok) + " error(s)");
            });
        });
    }

    private void saveAll() {
        if (lastResults.isEmpty()) {
            summary.setText("Run the batch first.");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Save all results to…");
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir == null) {
            return;
        }
        int written = BatchTransformRunner.writeAll(lastResults, Path.of(dir.getAbsolutePath()),
                extensionFor(format));
        summary.setText("Wrote " + written + " file(s) to " + dir.getName());
    }

    private static String extensionFor(OutputFormat format) {
        return switch (format) {
            case HTML, XHTML -> "html";
            case TEXT -> "txt";
            case JSON -> "json";
            default -> "xml";
        };
    }

    public int getFileCount() {
        return inputFiles.size();
    }

    /** Adds input files directly (for tests). */
    public void addInputFiles(List<File> files) {
        inputFiles.addAll(files);
        refreshFileTable();
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static TableColumn<BatchFileResult, String> col(String title,
            java.util.function.Function<BatchFileResult, String> value, double prefWidth) {
        TableColumn<BatchFileResult, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
```

- [ ] **Step 7: Replace the `openBatch` stub in `TransformPanel`**

In `TransformPanel.java`, replace the stub `openBatch()` body added in Task 2 with:

```java
    /** Opens the batch-transform tool tab for the active stylesheet/XQuery. */
    public void openBatch() {
        OutputFormat format = outputFormat.getValue() != null ? outputFormat.getValue() : OutputFormat.XML;
        String xqueryText = xqueryArea.getText();
        BatchTransformView view;
        if (xsltFile != null) {
            String xsltContent;
            try {
                xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                output.setText("Could not read stylesheet: " + e.getMessage());
                return;
            }
            view = new org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView(
                    org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView.Kind.XSLT,
                    xsltContent, collectParameters(), format);
        } else if (xqueryText != null && !xqueryText.isBlank()) {
            view = new org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView(
                    org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView.Kind.XQUERY,
                    xqueryText, collectParameters(), format);
        } else {
            output.setText("Set an XSLT stylesheet or enter an XQuery first.");
            return;
        }
        editorHost.openToolTab("Batch", "bi-files", view);
    }
```

- [ ] **Step 8: Run the runner test + TransformPanel test**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformRunnerTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.TransformPanelTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/BatchFileResult.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/BatchTransformRunner.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/BatchTransformView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/BatchTransformRunnerTest.java
git commit -m "feat(shell): multi-file batch transform (runner + tool tab)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 4: Profiler + Debug trace

Both reports come from one engine call: `transformWithDebugSession` with a fresh, breakpoint-free `DebugSession` runs to completion with line numbering + trace listeners, returning a result carrying the profile, template matches (with per-template times), call stack, and messages. A `TransformRunner.transformForReport` overload returns that result; `ProfileView` and `TraceView` render it; the Transform panel's Profile/Trace checkboxes open the views after a transform.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformRunner.java`
- Create: `.../debug/ProfileView.java`
- Create: `.../debug/TraceView.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java`
- Test: `.../debug/TransformReportTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;

class TransformReportTest {

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/"><out><xsl:value-of select="count(//item)"/></out></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void reportRunReturnsProfileAndTraceData() {
        XsltTransformationResult result =
                TransformRunner.transformForReport(XML, XSLT, Map.of(), OutputFormat.XML);
        assertNotNull(result, "report result");
        assertTrue(result.isSuccess(), "transform succeeded: " + result.getErrorMessage());
        assertNotNull(result.getProfile(), "profile present");
        assertNotNull(result.getTemplateMatches(), "template matches present");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.TransformReportTest"`
Expected: compile error — `transformForReport` does not exist.

- [ ] **Step 3: Add `transformForReport` to `TransformRunner`**

In `TransformRunner.java`, add (after `runXQuery`):

```java
    /**
     * Runs the transform to completion with tracing + profiling enabled and returns the full
     * {@link XsltTransformationResult} (profile, template matches with per-template timings,
     * call stack, messages). Uses a breakpoint-free debug session so it never pauses.
     *
     * @return the result; check {@link XsltTransformationResult#isSuccess()}
     */
    public static XsltTransformationResult transformForReport(String xml, String xsltContent,
            java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat) {
        try {
            org.fxt.freexmltoolkit.debugger.DebugSession session =
                    new org.fxt.freexmltoolkit.debugger.DebugSession();
            return XsltTransformationEngine.getInstance()
                    .transformWithDebugSession(xml, xsltContent, parameters, outputFormat, session);
        } catch (Exception e) {
            return XsltTransformationResult.error(e.getMessage());
        }
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.TransformReportTest"`
Expected: PASS.

- [ ] **Step 5: Implement `ProfileView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.TemplateMatchInfo;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/** Read-only performance report: overall timing/size + per-template execution times. */
public class ProfileView extends VBox {

    private final TableView<TemplateMatchInfo> table = new TableView<>();
    private final Label summary = new Label();

    public ProfileView(XsltTransformationResult result) {
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        summary.getStyleClass().add("fxt-side-panel-title");
        long totalMs = result.getExecutionTime();
        int outputSize = result.getOutputContent() == null ? 0 : result.getOutputContent().length();
        int templateCount = result.getTemplateMatches() == null ? 0 : result.getTemplateMatches().size();
        summary.setText("Total " + totalMs + " ms · output " + outputSize + " chars · "
                + templateCount + " template match(es)");

        table.getColumns().add(col("Template", t -> displayName(t), 240));
        table.getColumns().add(col("Line", t -> Integer.toString(t.lineNumber()), 60));
        table.getColumns().add(col("Time (ms)", t -> Long.toString(t.executionTime()), 90));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No template timing captured."));
        if (result.getTemplateMatches() != null) {
            var sorted = new java.util.ArrayList<>(result.getTemplateMatches());
            sorted.sort(java.util.Comparator.comparingLong(TemplateMatchInfo::executionTime).reversed());
            table.getItems().setAll(sorted);
        }
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(summary, table);
    }

    private static String displayName(TemplateMatchInfo t) {
        if (t.name() != null && !t.name().isEmpty()) {
            return t.name();
        }
        return t.pattern() == null ? "" : t.pattern();
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    public String getSummaryText() {
        return summary.getText();
    }

    private static TableColumn<TemplateMatchInfo, String> col(String title,
            java.util.function.Function<TemplateMatchInfo, String> value, double prefWidth) {
        TableColumn<TemplateMatchInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
```

- [ ] **Step 6: Implement `TraceView`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.TemplateMatchInfo;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/** Read-only execution trace: template matches and {@code xsl:message} output. */
public class TraceView extends VBox {

    private final TableView<TemplateMatchInfo> matches = new TableView<>();
    private final TableView<TransformationMessage> messages = new TableView<>();

    public TraceView(XsltTransformationResult result) {
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        Label matchTitle = new Label("TEMPLATE MATCHES");
        matchTitle.getStyleClass().add("fxt-side-panel-title");
        matches.getColumns().add(matchCol("Pattern", t -> t.pattern(), 200));
        matches.getColumns().add(matchCol("Name", t -> t.name(), 140));
        matches.getColumns().add(matchCol("Line", t -> Integer.toString(t.lineNumber()), 60));
        matches.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        matches.setPlaceholder(new Label("No template matches captured."));
        if (result.getTemplateMatches() != null) {
            matches.getItems().setAll(result.getTemplateMatches());
        }
        VBox.setVgrow(matches, Priority.ALWAYS);

        Label msgTitle = new Label("MESSAGES");
        msgTitle.getStyleClass().add("fxt-side-panel-title");
        messages.getColumns().add(msgCol("Level", TransformationMessage::getLevel, 80));
        messages.getColumns().add(msgCol("Message", TransformationMessage::getMessage, -1));
        messages.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        messages.setPlaceholder(new Label("No xsl:message output."));
        if (result.getMessages() != null) {
            messages.getItems().setAll(result.getMessages());
        }
        VBox.setVgrow(messages, Priority.ALWAYS);

        getChildren().addAll(matchTitle, matches, msgTitle, messages);
    }

    public int getMatchCount() {
        return matches.getItems().size();
    }

    public int getMessageCount() {
        return messages.getItems().size();
    }

    private static TableColumn<TemplateMatchInfo, String> matchCol(String title,
            java.util.function.Function<TemplateMatchInfo, String> value, double prefWidth) {
        TableColumn<TemplateMatchInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }

    private static TableColumn<TransformationMessage, String> msgCol(String title,
            java.util.function.Function<TransformationMessage, String> value, double prefWidth) {
        TableColumn<TransformationMessage, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
```

- [ ] **Step 7: Wire the Profile/Trace checkboxes into `TransformPanel.transform()`**

In `TransformPanel.java`, modify the `transform()` method so that after the normal transform output, if Profile/Trace are checked, it produces the report and opens the view(s). Replace the body of the `FxtGui.executorService.submit(...)` block in `transform()` with this version (it adds the report branch; the rest is unchanged):

```java
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String result;
            String xsltContent = "";
            try {
                xsltContent = Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                result = TransformRunner.xsltTransform(xml, xsltContent, params, format);
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String finalResult = result;
            boolean wantProfile = profileCheck.isSelected();
            boolean wantTrace = traceCheck.isSelected();
            org.fxt.freexmltoolkit.service.XsltTransformationResult report =
                    (wantProfile || wantTrace) && !xsltContent.isBlank()
                            ? TransformRunner.transformForReport(xml, xsltContent, params, format)
                            : null;
            Platform.runLater(() -> {
                output.setText(finalResult);
                statsLabel.setText(statsText(finalResult, elapsedMs));
                textToggle.setSelected(true);
                if (report != null && report.isSuccess()) {
                    if (wantProfile) {
                        editorHost.openToolTab("Profile", "bi-speedometer2",
                                new org.fxt.freexmltoolkit.controls.shell.editor.debug.ProfileView(report));
                    }
                    if (wantTrace) {
                        editorHost.openToolTab("Trace", "bi-list-columns",
                                new org.fxt.freexmltoolkit.controls.shell.editor.debug.TraceView(report));
                    }
                }
            });
        });
```

- [ ] **Step 8: Run the report test + a profile/trace view smoke test**

Add `TransformReportTest` already covers the runner. Verify the views compile and the TransformPanel still builds:

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.debug.TransformReportTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.TransformPanelTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformRunner.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/ProfileView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/TraceView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/debug/TransformReportTest.java
git commit -m "feat(shell): XSLT profiler + debug-trace report views

Profile/Trace checkboxes in the Transform panel open read-only tool tabs from a
breakpoint-free debug-session run (profile + per-template times + messages).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 5: Parity follow-ons + docs + verification

Close the small remaining parity gaps and document the new capabilities.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java` (XQuery examples menu)
- Modify: `docs/unified-shell.md` and/or `docs/xslt-viewer.md` (or the Transform section)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanelExamplesTest.java`

- [ ] **Step 1: Write the failing test for XQuery examples**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class TransformPanelExamplesTest {

    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        panel = new TransformPanel(new EditorHost());
    }

    @Test
    void insertsAnXQueryExample() {
        String text = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.insertXQueryExample("simple");
            return panel.getXQueryText();
        });
        assertFalse(text.isBlank(), "example inserted");
    }
}
```

> Add a `public String getXQueryText()` accessor to `TransformPanel` returning `xqueryArea.getText()` if one does not exist yet.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.TransformPanelExamplesTest"`
Expected: FAIL/compile error — `insertXQueryExample` / `getXQueryText` missing.

- [ ] **Step 3: Add the XQuery examples to `TransformPanel`**

Add the accessor and the example method:

```java
    /** @return the current XQuery text (for tests/observers). */
    public String getXQueryText() {
        return xqueryArea.getText();
    }

    /** Inserts a built-in XQuery example by key (simple, flwor, html, dq). */
    public void insertXQueryExample(String key) {
        String example = switch (key) {
            case "flwor" -> "for $x in //item\norder by $x\nreturn <row>{string($x)}</row>";
            case "html" -> "<html><body><ul>{\n  for $x in //item return <li>{string($x)}</li>\n}</ul></body></html>";
            case "dq" -> "(: data-quality: items missing a value :)\nfor $x in //item[not(normalize-space())]\nreturn <missing>{name($x)}</missing>";
            default -> "for $x in //item return string($x)";
        };
        xqueryArea.setText(example);
    }
```

Add an "Examples" `MenuButton` next to the XQuery Run button. In the constructor, where `runXQuery` is created (around line 169), replace the single button with a row including the menu:

```java
        Button runXQuery = button("Run XQuery", "bi-braces", this::runXQuery);
        MenuButton examplesMenu = new MenuButton("Examples");
        examplesMenu.getStyleClass().add("fxt-tool-button");
        examplesMenu.getItems().addAll(
                exampleItem("Simple", "simple"),
                exampleItem("FLWOR", "flwor"),
                exampleItem("HTML report", "html"),
                exampleItem("Data-quality check", "dq"));
```

And change the layout line that adds the XQuery run button (in `getChildren().addAll`) from `SidePanelLayout.fill(runXQuery),` to:

```java
                new HBox(6, runXQuery, examplesMenu),
```

Add the helper:

```java
    private MenuItem exampleItem(String label, String key) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> insertXQueryExample(key));
        return item;
    }
```

> `MenuButton` and `MenuItem` are already imported in `TransformPanel`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.TransformPanelExamplesTest"`
Expected: PASS.

- [ ] **Step 5: Parity verification against the legacy feature list**

Read `docs/superpowers/specs/2026-06-07-xslt-developer-shell-port-design.md` (the "Background — what already exists" + the four feature sections) and confirm each is now reachable in the shell:

- Debugger: breakpoints (gutter), step into/over/out, continue, stop, variables, call stack, watch — ✅ Task 2.
- Batch: add files/dir, run, per-file results, save all — ✅ Task 3.
- Profiler: timings + per-template times — ✅ Task 4.
- Trace: template matches + messages — ✅ Task 4.
- XQuery examples — ✅ Task 5.

XSLT version selection (1.0/2.0/3.0) is intentionally NOT ported: Saxon HE auto-detects the version from the stylesheet's `version` attribute, so an explicit selector is cosmetic. Note this in the docs.

If any gap is found, add a small follow-on task here before proceeding.

- [ ] **Step 6: Update the docs**

In `docs/unified-shell.md`, under the Transform activity section, add a short subsection documenting the new Advanced capabilities (Debug / Batch / Profile / Trace) and the XQuery examples. Keep the style consistent with the surrounding doc. If a dedicated `docs/xslt-developer-features.md`-style page is warranted, create it and add it to `mkdocs.yml` nav (the repo has an mkdocs-nav-sync agent for that).

Example paragraph to include:

```markdown
### Advanced XSLT tools

The Transform side panel's **Advanced** section adds:

- **Debug** — opens the stylesheet as a document with a breakpoint gutter and a
  Debug tool tab (step into/over/out, continue, stop; variables, call stack,
  breakpoints, and XPath watches).
- **Batch…** — runs the active stylesheet/XQuery over many XML files, with
  per-file results and "Save All".
- **Profile** / **Trace** — when checked, a transform also opens a read-only
  Profile (timings + per-template execution times) or Trace (template matches +
  `xsl:message` output) tool tab.

The XQuery console offers built-in **Examples** (simple, FLWOR, HTML report,
data-quality check).
```

- [ ] **Step 7: Full build + commit**

Run: `./gradlew build`
Expected: GREEN (mind the documented flaky tests — `ConnectionService` network timing, inspector debounce under combined runs; re-run individually if they appear).

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformPanelExamplesTest.java \
        docs/unified-shell.md
git commit -m "feat(shell): XQuery examples menu + docs for the ported XSLT tools

Completes the XSLT Developer feature port into the shell (debugger, batch,
profiler, trace, examples). xsltDeveloper is now redundant and ready to retire
as a separate follow-up.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Out of scope (separate follow-up)

Retiring the `xsltDeveloper` subsystem (delete `XsltDeveloperController` + `tab_xslt_developer.fxml`, remove MainController/menu/sidebar wiring, drop `XsltDeveloperControllerTest`/`XsltDebuggerIntegrationTest` or repoint them, update SkillTracker/UsageTrackingServiceImpl/WelcomeController, remove the screenshot block). The `debugger/` domain + `BreakpointGutterFactory` stay (now used by the shell). Do this only after this plan's parity is verified in the running app.

## Notes on reuse / DRY

- `XsltTransformationEngine.transformWithDebugSession` is reused for the debugger AND the profile/trace reports (breakpoint-free session) — one engine path, no duplication.
- `BreakpointGutterFactory` is reused unchanged.
- `TransformRunner` is the single transform/query entry point shared by interactive transform, batch, and reports.
```