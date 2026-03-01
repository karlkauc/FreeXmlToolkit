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

## Icons (Ikonli Bootstrap)

### Valid Patterns
```java
new FontIcon("bi-save")          // Save icon
new FontIcon("bi-trash")         // Delete icon
new FontIcon("bi-plus-circle")   // Add icon
new FontIcon("bi-pencil")        // Edit icon
```

### INVALID Icons (DO NOT USE)
- `bi-database` - Does not exist!
- Always validate: https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html

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
| Icon missing | Invalid icon name | Validate against cheat sheet |
| Native build fails | Non-public FXML method | Make all @FXML public |

---

## Key File Locations

| Purpose | Path |
|---------|------|
| Main class | `FxtGui.java` |
| XSD Controller | `controller/XsdController.java` (tab orchestration) |
| Documentation Sub-tab | `controller/DocumentationTabController.java` |
| Flatten Sub-tab | `controller/FlattenTabController.java` |
| Schema Analysis Sub-tab | `controller/SchemaAnalysisTabController.java` |
| Formatting Utilities | `util/FormattingUtils.java` |
| XSD Model | `controls/v2/model/XsdNode.java` |
| Commands | `controls/v2/editor/commands/` |
| Serializer | `controls/v2/editor/serialization/XsdSerializer.java` |
| Factory | `controls/v2/model/XsdNodeFactory.java` |
| Facets Panel | `controls/v2/editor/panels/FacetsPanel.java` |
| Style Guide | `STYLE_GUIDE.jsonc` |
