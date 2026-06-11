# Quick Reference

## Build Commands
```bash
./gradlew run                    # Run app
./gradlew test                   # Run all tests
./gradlew test --tests "Class.method"  # Single test
./gradlew clean build            # Clean build
./gradlew createAllExecutables   # Native packages
```

---

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

## Icons (IconifyIcon — bundled Iconify SVG)

Icons use `org.fxt.freexmltoolkit.controls.icons.IconifyIcon` (not Ikonli). Bundled set:
`src/main/resources/icons/iconify/bi.json` (Bootstrap Icons). Same `iconLiteral`/`iconSize`/
`iconColor` API as the old `FontIcon`.

### Valid Patterns
```java
new IconifyIcon("bi-save")          // Save icon
new IconifyIcon("bi-trash")         // Delete icon
new IconifyIcon("bi-plus-circle")   // Add icon
new IconifyIcon("bi-pencil")        // Edit icon
```

### Unknown icons
- Do **not** crash — they render a placeholder and log a warning.
- `IconifyIconCoverageTest` fails the build for any unresolved `bi-*` literal in FXML/Java.
- Browse names: https://icons.getbootstrap.com/

### Standard Sizes
- Menu items: `iconSize="16"`
- Toolbar: `iconSize="20"`
- Empty states: `iconSize="48"` or `iconSize="64"`

### Semantic Colors
```java
"#28a745"  // Success (green)
"#dc3545"  // Danger (red)
"#17a2b8"  // Info (cyan)
"#ffc107"  // Warning (yellow)
"#007bff"  // Primary (blue)
```

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
| Shell CSS / tokens | `css/unified-shell.css`, `css/design-tokens.css` |
| Style Guide | `STYLE_GUIDE.jsonc` |
| Doc screenshots | `xvfb-run ./gradlew docScreenshots` → `docs/img/` |
