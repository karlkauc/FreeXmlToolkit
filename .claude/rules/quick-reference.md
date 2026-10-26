# Quick Reference

Build commands: see `CLAUDE.md` (root).

## Creating a New Command

1. Location: `controls/v2/editor/commands/`
2. Implement `XsdCommand` interface
3. Required methods:
   - `execute()` - Apply change, return success
   - `undo()` - Revert change
   - `getDescription()` - Human-readable description
   - `canUndo()` - Usually `return true`

```java
public class MyCommand implements XsdCommand {
    private final XsdNode node;
    private final String oldValue, newValue;

    public MyCommand(XsdNode node, String newValue) {
        this.node = node;
        this.oldValue = node.getValue();  // Store for undo
        this.newValue = newValue;
    }

    @Override public boolean execute() {
        node.setValue(newValue);
        return true;
    }

    @Override public boolean undo() {
        node.setValue(oldValue);
        return true;
    }

    @Override public String getDescription() {
        return "Change value to " + newValue;
    }

    @Override public boolean canUndo() { return true; }
}
```

---

## Icons

See "Icon Usage (Critical)" in `CLAUDE.md` (root) — `IconifyIcon`, `bi-*` names, sizes, semantic colors.

---

## FXML Controller Rules

```java
// WRONG - will fail in jpackage release
@FXML
private void handleSave() { ... }

// CORRECT - must be public
@FXML
public void handleSave() { ... }
```

**Reason:** Module system prevents access to non-public methods in native builds.

---

## FXML Views

Layout goes into `src/main/resources/pages/{panels,views,dialogs,inspector}/<ClassName>.fxml`,
behaviour stays in the class.

```java
public final class FopPanel extends VBox {
    @FXML private Button generateButton;    // fx:id="generateButton" id="fop-generate"

    public FopPanel(EditorHost editorHost) {
        this.editorHost = editorHost;       // 1. constructor-arg fields first
        FxmlViews.load(this, "/pages/panels/FopPanel.fxml");   // 2. fx:root: this = root + controller
        generateButton.disableProperty().bind(…); // 3. dynamic wiring (bindings, cell factories, menus)
    }

    @FXML
    public void onGenerate() { … }
}
```

- Dialogs / non-`fx:root` content: `Node content = FxmlViews.loadContent(this, "/pages/dialogs/X.fxml")`.
- Keep the test `id="…"` next to `fx:id`; tests look nodes up by `#id`.
- Building blocks as tags: `SidePanelHeader`, `SidePanelSection`, `PanelActionList` +
  `PanelActionButton` (`iconLiteral`, `color` = `ActionColor`, `primary`), `SourceRow`,
  `PanelProgress`, `SettingsCard`, `WelcomeQuickCard` / `WelcomeToolCard` (task rows) / `WelcomeStatCard`;
  table columns in FXML, value binding via `FxmlColumns.text(column, fn)`.
- No `stylesheets=` in panel FXMLs (inherited from the shell root).
- `FxmlContractTest` fails for an `@FXML` field without `fx:id`, a missing or non-public
  handler, or a duplicate `id`.

| FXML pitfall | Solution |
|--------------|----------|
| Nested class tag "is not a valid type" | Fully qualified tag: `<javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory min="1" max="9" initialValue="2"/>` |
| Text starting with `@` is read as a resource path | Escape it: `text="\@since"` |
| `USE_PREF_SIZE` / `Double.MAX_VALUE` | `-Infinity` / `Infinity` |
| Initial `setValue` on a combo fires `onAction` | Guard the handler while filling (see `FundsXmlPanel`) |

---

## UI Threading

```java
// Background task with UI update
executorService.submit(() -> {
    var result = heavyComputation();
    Platform.runLater(() -> {
        label.setText(result);  // UI update
    });
});
```

---

## Model Modification Rules

| Action | Wrong | Correct |
|--------|-------|---------|
| Rename | `node.setName("x")` | `cmd = new RenameNodeCommand(...)` |
| Delete | `parent.remove(node)` | `cmd = new DeleteNodeCommand(...)` |
| Add | `parent.add(node)` | `cmd = new AddElementCommand(...)` |

**Always:** `commandManager.executeCommand(cmd)`

---

## Testing

### Unit Tests (no JavaFX)
```java
@Test
void testNodeProperties() {
    var node = new XsdElement("test");
    assertEquals("test", node.getName());
}
```

### UI Tests (TestFX)
```java
@ExtendWith(ApplicationExtension.class)
class MyUITest {
    @Start
    void start(Stage stage) { ... }

    @Test
    void testButton(FxRobot robot) {
        robot.clickOn("#saveButton");
    }
}
```

---

## Common Gotchas

| Issue | Cause | Solution |
|-------|-------|----------|
| UI freezes | Heavy work on UI thread | Use `executorService` |
| Changes not saved | Direct model modification | Use Command pattern |
| Infinite loop | Circular XSD references | Check visited nodes |
| Icon missing | Invalid icon name | `IconifyIconCoverageTest` catches it |
| Per-item icon color ignored | CSS `-fx-icon-color` overrides `setIconColor()` | `bind()` the property instead |
| Native build fails | Non-public FXML method | Make all @FXML public |
| ListView IndexOutOfBounds | `items.setAll` while a row is selected | `clearSelection()` first; open files via `Platform.runLater` |
| Flaky TestFX assertion | Asserting a sibling effect of the same `runLater` pulse | `waitFor`/poll the combined condition |
| Wrong code in search results | Stale `.claude/worktrees/*` copies | Ignore worktree paths |
| macOS notarization rejects "binary is not signed" in a jar | A dependency ships a native library inside its jar; `jpackage --mac-sign` does not sign jar contents | `scripts/sign-macos-jar-natives.sh` signs `*.dylib`/`*.jnilib` before jpackage (see `scripts/README.md`) |
| macOS app-image build breaks | jpackage app root is `FreeXmlToolkit.app` (not `FreeXmlToolkit/`); the bundle must not be modified after codesign | Ship extras next to the bundle (see build.gradle.kts doLast) |

---

## Key File Locations

| Purpose | Path |
|---------|------|
| Main class | `FxtGui.java` (loads `pages/tab_unified_shell.fxml`) |
| Shell view | `controls/shell/UnifiedShellView.java` |
| Editor center (tabs, view modes, schema binding) | `controls/shell/editor/EditorHost.java` |
| Activity side panels | `controls/shell/editor/*Panel.java` |
| Inspector | `controls/shell/inspector/InspectorPanel.java` |
| Schema activity views | `controls/shell/schema/` |
| Formatting Utilities | `util/FormattingUtils.java` |
| XSD Model | `controls/v2/model/XsdNode.java` |
| Commands | `controls/v2/editor/commands/` (XSD), `controls/v2/xmleditor/commands/` (XML) |
| Serializer | `controls/v2/editor/serialization/XsdSerializer.java` |
| Factory | `controls/v2/model/XsdNodeFactory.java` |
| Facets Panel | `controls/v2/editor/panels/FacetsPanel.java` |
| Type picker (type="…" of elements/attributes) | `controls/shell/schema/TypePickerField.java` (+ `TypePickerPopup`, `TypePickerDialog`, `TypeChoices`), built-in catalog `domain/XsdBuiltinTypeCatalog.java` |
| Shell CSS / tokens | `css/unified-shell.css`, `css/design-tokens.css` |
| Style Guide | `STYLE_GUIDE.jsonc` |
| Doc screenshots | `xvfb-run ./gradlew docScreenshots` → `docs/img/` (every `*DocScreenshotGenerator` in `src/test/.../screenshots/`; the XSLT debugger images come from `XsltDebuggerDocScreenshotGenerator`) |
