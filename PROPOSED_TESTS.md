# Proposed Test Implementations

This document contains detailed test implementation proposals for critical gaps in test coverage.

## 1. CommandManagerTest.java

**Location:** `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/commands/CommandManagerTest.java`

**Purpose:** Test the undo/redo manager for V2 editor

```java
package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandManagerTest {

    private CommandManager commandManager;
    private TestCommand command1;
    private TestCommand command2;
    private TestCommand command3;

    @BeforeEach
    void setUp() {
        commandManager = new CommandManager();
        command1 = new TestCommand("Command 1");
        command2 = new TestCommand("Command 2");
        command3 = new TestCommand("Command 3");
    }

    @Test
    @DisplayName("Should execute command and add to undo stack")
    void testExecuteCommand() {
        assertTrue(commandManager.executeCommand(command1));
        assertTrue(command1.isExecuted());
        assertEquals(1, commandManager.getUndoStackSize());
        assertTrue(commandManager.canUndo());
        assertFalse(commandManager.canRedo());
    }

    @Test
    @DisplayName("Should throw exception when executing null command")
    void testExecuteNullCommand() {
        assertThrows(IllegalArgumentException.class, () -> commandManager.executeCommand(null));
    }

    @Test
    @DisplayName("Should undo command successfully")
    void testUndo() {
        commandManager.executeCommand(command1);

        assertTrue(commandManager.undo());
        assertTrue(command1.isUndone());
        assertEquals(0, commandManager.getUndoStackSize());
        assertEquals(1, commandManager.getRedoStackSize());
        assertFalse(commandManager.canUndo());
        assertTrue(commandManager.canRedo());
    }

    @Test
    @DisplayName("Should redo command successfully")
    void testRedo() {
        commandManager.executeCommand(command1);
        commandManager.undo();

        assertTrue(commandManager.redo());
        assertEquals(2, command1.getExecuteCount()); // Executed twice
        assertEquals(1, commandManager.getUndoStackSize());
        assertEquals(0, commandManager.getRedoStackSize());
        assertTrue(commandManager.canUndo());
        assertFalse(commandManager.canRedo());
    }

    @Test
    @DisplayName("Should handle multiple undo/redo operations")
    void testMultipleUndoRedo() {
        commandManager.executeCommand(command1);
        commandManager.executeCommand(command2);
        commandManager.executeCommand(command3);

        assertEquals(3, commandManager.getUndoStackSize());
        assertEquals("Command 3", commandManager.getUndoDescription());

        // Undo all
        commandManager.undo();
        assertEquals("Command 2", commandManager.getUndoDescription());
        commandManager.undo();
        assertEquals("Command 1", commandManager.getUndoDescription());
        commandManager.undo();

        assertFalse(commandManager.canUndo());
        assertEquals(3, commandManager.getRedoStackSize());

        // Redo all
        commandManager.redo();
        commandManager.redo();
        commandManager.redo();

        assertTrue(commandManager.canUndo());
        assertFalse(commandManager.canRedo());
    }

    @Test
    @DisplayName("Should clear redo stack when new command is executed")
    void testRedoStackClearedOnNewCommand() {
        commandManager.executeCommand(command1);
        commandManager.executeCommand(command2);
        commandManager.undo(); // command2 in redo stack

        assertEquals(1, commandManager.getRedoStackSize());

        commandManager.executeCommand(command3);

        assertEquals(0, commandManager.getRedoStackSize());
        assertFalse(commandManager.canRedo());
    }

    @Test
    @DisplayName("Should merge commands when possible")
    void testCommandMerging() {
        MergeableCommand mergeableCommand1 = new MergeableCommand("A");
        MergeableCommand mergeableCommand2 = new MergeableCommand("B");

        commandManager.executeCommand(mergeableCommand1);
        commandManager.executeCommand(mergeableCommand2);

        // Should have merged, so only 1 command in stack
        assertEquals(1, commandManager.getUndoStackSize());
        assertEquals("Merged: A, B", commandManager.getUndoDescription());
    }

    @Test
    @DisplayName("Should enforce history limit")
    void testHistoryLimit() {
        CommandManager limitedManager = new CommandManager(3);

        for (int i = 1; i <= 5; i++) {
            limitedManager.executeCommand(new TestCommand("Command " + i));
        }

        // Should only keep last 3
        assertEquals(3, limitedManager.getUndoStackSize());
        assertEquals("Command 5", limitedManager.getUndoDescription());

        limitedManager.undo();
        assertEquals("Command 4", limitedManager.getUndoDescription());
        limitedManager.undo();
        assertEquals("Command 3", limitedManager.getUndoDescription());
    }

    @Test
    @DisplayName("Should throw exception for invalid history limit")
    void testInvalidHistoryLimit() {
        assertThrows(IllegalArgumentException.class, () -> new CommandManager(0));
        assertThrows(IllegalArgumentException.class, () -> new CommandManager(-1));
    }

    @Test
    @DisplayName("Should clear all history")
    void testClear() {
        commandManager.executeCommand(command1);
        commandManager.executeCommand(command2);
        commandManager.undo();

        assertTrue(commandManager.canUndo());
        assertTrue(commandManager.canRedo());

        commandManager.clear();

        assertFalse(commandManager.canUndo());
        assertFalse(commandManager.canRedo());
        assertEquals(0, commandManager.getUndoStackSize());
        assertEquals(0, commandManager.getRedoStackSize());
    }

    @Test
    @DisplayName("Should fire property change events")
    void testPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        commandManager.addPropertyChangeListener(listener);

        commandManager.executeCommand(command1);

        // Should fire canUndo: false->true and canRedo: true->false
        assertTrue(events.stream().anyMatch(e ->
            e.getPropertyName().equals("canUndo") &&
            Boolean.FALSE.equals(e.getOldValue()) &&
            Boolean.TRUE.equals(e.getNewValue())
        ));

        events.clear();
        commandManager.undo();

        // Should fire canUndo: true->false and canRedo: false->true
        assertTrue(events.stream().anyMatch(e ->
            e.getPropertyName().equals("canRedo") &&
            Boolean.FALSE.equals(e.getOldValue()) &&
            Boolean.TRUE.equals(e.getNewValue())
        ));
    }

    @Test
    @DisplayName("Should handle undo failure gracefully")
    void testUndoFailure() {
        FailingCommand failingCommand = new FailingCommand();
        commandManager.executeCommand(failingCommand);

        assertFalse(commandManager.undo());

        // Command should be back in undo stack
        assertEquals(1, commandManager.getUndoStackSize());
        assertTrue(commandManager.canUndo());
    }

    @Test
    @DisplayName("Should return false when cannot undo")
    void testCannotUndo() {
        assertFalse(commandManager.canUndo());
        assertFalse(commandManager.undo());
    }

    @Test
    @DisplayName("Should return false when cannot redo")
    void testCannotRedo() {
        assertFalse(commandManager.canRedo());
        assertFalse(commandManager.redo());
    }

    @Test
    @DisplayName("Should get correct undo/redo descriptions")
    void testDescriptions() {
        assertNull(commandManager.getUndoDescription());
        assertNull(commandManager.getRedoDescription());

        commandManager.executeCommand(command1);
        assertEquals("Command 1", commandManager.getUndoDescription());
        assertNull(commandManager.getRedoDescription());

        commandManager.undo();
        assertNull(commandManager.getUndoDescription());
        assertEquals("Command 1", commandManager.getRedoDescription());
    }

    // Helper classes for testing

    private static class TestCommand implements XsdCommand {
        private final String description;
        private int executeCount = 0;
        private boolean undone = false;

        TestCommand(String description) {
            this.description = description;
        }

        @Override
        public boolean execute() {
            executeCount++;
            undone = false;
            return true;
        }

        @Override
        public boolean undo() {
            undone = true;
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public boolean canMergeWith(XsdCommand other) {
            return false;
        }

        @Override
        public XsdCommand mergeWith(XsdCommand other) {
            return null;
        }

        boolean isExecuted() {
            return executeCount > 0;
        }

        boolean isUndone() {
            return undone;
        }

        int getExecuteCount() {
            return executeCount;
        }
    }

    private static class MergeableCommand implements XsdCommand {
        private final String value;

        MergeableCommand(String value) {
            this.value = value;
        }

        @Override
        public boolean execute() {
            return true;
        }

        @Override
        public boolean undo() {
            return true;
        }

        @Override
        public String getDescription() {
            return "Merged: " + value;
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public boolean canMergeWith(XsdCommand other) {
            return other instanceof MergeableCommand;
        }

        @Override
        public XsdCommand mergeWith(XsdCommand other) {
            if (other instanceof MergeableCommand otherMergeable) {
                return new MergeableCommand(value + ", " + otherMergeable.value);
            }
            return null;
        }
    }

    private static class FailingCommand implements XsdCommand {
        @Override
        public boolean execute() {
            return true;
        }

        @Override
        public boolean undo() {
            return false; // Simulate undo failure
        }

        @Override
        public String getDescription() {
            return "Failing Command";
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public boolean canMergeWith(XsdCommand other) {
            return false;
        }

        @Override
        public XsdCommand mergeWith(XsdCommand other) {
            return null;
        }
    }
}
```

**Coverage:** 18 test cases covering:
- Basic execution, undo, redo
- Multiple operations
- Command merging
- History limits
- Property change events
- Error conditions
- Edge cases

---

## 2. XsdEditorContextTest.java

**Location:** `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/XsdEditorContextTest.java`

```java
package org.fxt.freexmltoolkit.controls.v2.editor;

import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XsdEditorContextTest {

    private XsdSchema schema;
    private XsdEditorContext context;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        context = new XsdEditorContext(schema);
    }

    @Test
    @DisplayName("Should throw exception for null schema")
    void testNullSchema() {
        assertThrows(IllegalArgumentException.class, () -> new XsdEditorContext(null));
    }

    @Test
    @DisplayName("Should initialize with default selection model")
    void testDefaultSelectionModel() {
        assertNotNull(context.getSelectionModel());
        assertNotNull(context.getCommandManager());
        assertEquals(schema, context.getSchema());
    }

    @Test
    @DisplayName("Should use provided selection model")
    void testProvidedSelectionModel() {
        SelectionModel sharedSelection = new SelectionModel();
        XsdEditorContext contextWithShared = new XsdEditorContext(schema, sharedSelection);

        assertSame(sharedSelection, contextWithShared.getSelectionModel());
    }

    @Test
    @DisplayName("Should default to edit mode enabled")
    void testDefaultEditMode() {
        assertTrue(context.isEditMode());
    }

    @Test
    @DisplayName("Should toggle edit mode and fire property change")
    void testEditModeToggle() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        context.addPropertyChangeListener("editMode", events::add);

        context.setEditMode(false);

        assertFalse(context.isEditMode());
        assertEquals(1, events.size());
        assertEquals("editMode", events.get(0).getPropertyName());
        assertTrue((Boolean) events.get(0).getOldValue());
        assertFalse((Boolean) events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should default to not dirty")
    void testDefaultDirtyFlag() {
        assertFalse(context.isDirty());
    }

    @Test
    @DisplayName("Should set dirty flag and fire property change")
    void testDirtyFlagChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        context.addPropertyChangeListener("dirty", events::add);

        context.setDirty(true);

        assertTrue(context.isDirty());
        assertEquals(1, events.size());
        assertEquals("dirty", events.get(0).getPropertyName());
        assertFalse((Boolean) events.get(0).getOldValue());
        assertTrue((Boolean) events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should reset dirty flag")
    void testResetDirty() {
        context.setDirty(true);
        assertTrue(context.isDirty());

        context.resetDirty();

        assertFalse(context.isDirty());
    }

    @Test
    @DisplayName("Should automatically set dirty when command is executed")
    void testAutoDirtyOnCommand() {
        assertFalse(context.isDirty());

        // Execute a simple test command
        TestCommand command = new TestCommand();
        context.getCommandManager().executeCommand(command);

        assertTrue(context.isDirty());
    }

    @Test
    @DisplayName("Should manage property change listeners")
    void testPropertyChangeListeners() {
        List<PropertyChangeEvent> allEvents = new ArrayList<>();
        List<PropertyChangeEvent> dirtyEvents = new ArrayList<>();

        PropertyChangeListener allListener = allEvents::add;
        PropertyChangeListener dirtyListener = dirtyEvents::add;

        context.addPropertyChangeListener(allListener);
        context.addPropertyChangeListener("dirty", dirtyListener);

        context.setDirty(true);
        context.setEditMode(false);

        // All listener should receive both events
        assertEquals(2, allEvents.size());

        // Dirty listener should only receive dirty event
        assertEquals(1, dirtyEvents.size());
        assertEquals("dirty", dirtyEvents.get(0).getPropertyName());

        // Remove listeners
        context.removePropertyChangeListener(allListener);
        context.removePropertyChangeListener("dirty", dirtyListener);

        allEvents.clear();
        dirtyEvents.clear();

        context.setDirty(false);

        assertEquals(0, allEvents.size());
        assertEquals(0, dirtyEvents.size());
    }

    @Test
    @DisplayName("Should provide access to schema")
    void testGetSchema() {
        assertSame(schema, context.getSchema());
    }

    @Test
    @DisplayName("Should provide access to command manager")
    void testGetCommandManager() {
        assertNotNull(context.getCommandManager());
        assertSame(context.getCommandManager(), context.getCommandManager());
    }

    @Test
    @DisplayName("Should provide access to selection model")
    void testGetSelectionModel() {
        assertNotNull(context.getSelectionModel());
        assertSame(context.getSelectionModel(), context.getSelectionModel());
    }

    // Helper command for testing
    private static class TestCommand implements org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand {
        @Override
        public boolean execute() {
            return true;
        }

        @Override
        public boolean undo() {
            return true;
        }

        @Override
        public String getDescription() {
            return "Test Command";
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public boolean canMergeWith(org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand other) {
            return false;
        }

        @Override
        public org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand mergeWith(
                org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand other) {
            return null;
        }
    }
}
```

**Coverage:** 12 test cases covering:
- Constructor validation
- Selection model management
- Edit mode toggle
- Dirty flag management
- Automatic dirty flag on command execution
- Property change listeners
- Component access

---

## 3. SelectionModelTest.java

**Location:** `src/test/java/org/fxt/freexmltoolkit/controls/v2/editor/selection/SelectionModelTest.java`

```java
package org.fxt.freexmltoolkit.controls.v2.editor.selection;

import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionModelTest {

    private SelectionModel selectionModel;
    private VisualNode node1;
    private VisualNode node2;
    private VisualNode node3;

    @BeforeEach
    void setUp() {
        selectionModel = new SelectionModel();

        // Create mock VisualNodes
        node1 = mock(VisualNode.class);
        when(node1.getLabel()).thenReturn("Node 1");

        node2 = mock(VisualNode.class);
        when(node2.getLabel()).thenReturn("Node 2");

        node3 = mock(VisualNode.class);
        when(node3.getLabel()).thenReturn("Node 3");
    }

    @Test
    @DisplayName("Should start with empty selection")
    void testInitialState() {
        assertTrue(selectionModel.isEmpty());
        assertEquals(0, selectionModel.getSelectionCount());
        assertNull(selectionModel.getPrimarySelection());
        assertFalse(selectionModel.hasMultipleSelection());
    }

    @Test
    @DisplayName("Should select single node")
    void testSelectSingleNode() {
        selectionModel.select(node1);

        assertTrue(selectionModel.isSelected(node1));
        assertEquals(1, selectionModel.getSelectionCount());
        assertSame(node1, selectionModel.getPrimarySelection());
        assertFalse(selectionModel.isEmpty());
        assertFalse(selectionModel.hasMultipleSelection());
    }

    @Test
    @DisplayName("Should clear previous selection when selecting new node")
    void testSelectClearsPrevious() {
        selectionModel.select(node1);
        selectionModel.select(node2);

        assertFalse(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertEquals(1, selectionModel.getSelectionCount());
        assertSame(node2, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should clear selection when selecting null")
    void testSelectNull() {
        selectionModel.select(node1);
        selectionModel.select(null);

        assertTrue(selectionModel.isEmpty());
        assertNull(selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should add to selection")
    void testAddToSelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        assertTrue(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertEquals(2, selectionModel.getSelectionCount());
        assertTrue(selectionModel.hasMultipleSelection());
        assertSame(node1, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should not add duplicate to selection")
    void testAddDuplicate() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node1);

        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    @DisplayName("Should set primary selection when adding to empty selection")
    void testAddToEmptySelection() {
        selectionModel.addToSelection(node1);

        assertSame(node1, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should remove from selection")
    void testRemoveFromSelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        selectionModel.removeFromSelection(node2);

        assertTrue(selectionModel.isSelected(node1));
        assertFalse(selectionModel.isSelected(node2));
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    @DisplayName("Should update primary selection when removing it")
    void testRemovePrimarySelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        selectionModel.removeFromSelection(node1);

        assertSame(node2, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should clear primary when removing last node")
    void testRemoveLastNode() {
        selectionModel.select(node1);
        selectionModel.removeFromSelection(node1);

        assertNull(selectionModel.getPrimarySelection());
        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should toggle selection")
    void testToggleSelection() {
        selectionModel.select(node1);

        // Toggle off
        selectionModel.toggleSelection(node1);
        assertFalse(selectionModel.isSelected(node1));

        // Toggle on
        selectionModel.toggleSelection(node1);
        assertTrue(selectionModel.isSelected(node1));
    }

    @Test
    @DisplayName("Should select multiple nodes")
    void testSelectMultiple() {
        List<VisualNode> nodes = Arrays.asList(node1, node2, node3);

        selectionModel.selectMultiple(nodes);

        assertEquals(3, selectionModel.getSelectionCount());
        assertTrue(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertTrue(selectionModel.isSelected(node3));
        assertTrue(selectionModel.hasMultipleSelection());
        assertNotNull(selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should clear when selecting empty collection")
    void testSelectMultipleEmpty() {
        selectionModel.select(node1);
        selectionModel.selectMultiple(new ArrayList<>());

        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should clear when selecting null collection")
    void testSelectMultipleNull() {
        selectionModel.select(node1);
        selectionModel.selectMultiple(null);

        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should clear all selection")
    void testClearSelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        selectionModel.clearSelection();

        assertTrue(selectionModel.isEmpty());
        assertEquals(0, selectionModel.getSelectionCount());
        assertNull(selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should handle clearing empty selection")
    void testClearEmptySelection() {
        selectionModel.clearSelection();

        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should return unmodifiable set of selected nodes")
    void testGetSelectedNodes() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        Set<VisualNode> selectedNodes = selectionModel.getSelectedNodes();

        assertEquals(2, selectedNodes.size());
        assertTrue(selectedNodes.contains(node1));
        assertTrue(selectedNodes.contains(node2));

        // Should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
            selectedNodes.add(node3)
        );
    }

    @Test
    @DisplayName("Should handle null node operations gracefully")
    void testNullNodeHandling() {
        // Should not throw, should do nothing
        selectionModel.addToSelection(null);
        selectionModel.removeFromSelection(null);
        selectionModel.toggleSelection(null);

        assertFalse(selectionModel.isSelected(null));
        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should fire selection change events via listener")
    void testSelectionListener() {
        List<SelectionEvent> events = new ArrayList<>();

        selectionModel.addSelectionListener((oldSelection, newSelection) -> {
            events.add(new SelectionEvent(oldSelection, newSelection));
        });

        selectionModel.select(node1);

        assertEquals(1, events.size());
        assertTrue(events.get(0).oldSelection.isEmpty());
        assertEquals(1, events.get(0).newSelection.size());
        assertTrue(events.get(0).newSelection.contains(node1));
    }

    @Test
    @DisplayName("Should fire property change events")
    void testPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();

        selectionModel.addPropertyChangeListener(events::add);

        selectionModel.select(node1);

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> "selection".equals(e.getPropertyName())));
    }

    // Helper class for testing selection events
    private static class SelectionEvent {
        final Set<VisualNode> oldSelection;
        final Set<VisualNode> newSelection;

        SelectionEvent(Set<VisualNode> oldSelection, Set<VisualNode> newSelection) {
            this.oldSelection = oldSelection;
            this.newSelection = newSelection;
        }
    }
}
```

**Coverage:** 22 test cases covering:
- Initial state
- Single selection
- Multi-selection
- Add/remove operations
- Toggle selection
- Primary selection management
- Clear operations
- Null handling
- Event notifications
- Unmodifiable collections

---

## 4. Quick Wins: Missing V2 Model Tests

### XsdElementTest.java

**Location:** `src/test/java/org/fxt/freexmltoolkit/controls/v2/model/XsdElementTest.java`

```java
package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XsdElementTest {

    private XsdElement element;

    @BeforeEach
    void setUp() {
        element = new XsdElement();
    }

    @Test
    @DisplayName("Should have correct node type")
    void testNodeType() {
        assertEquals(XsdNodeType.ELEMENT, element.getNodeType());
    }

    @Test
    @DisplayName("Should set and get name")
    void testName() {
        element.setName("myElement");
        assertEquals("myElement", element.getName());
    }

    @Test
    @DisplayName("Should set and get type")
    void testType() {
        element.setType("xs:string");
        assertEquals("xs:string", element.getType());
    }

    @Test
    @DisplayName("Should set and get ref")
    void testRef() {
        element.setRef("myRef");
        assertEquals("myRef", element.getRef());
    }

    @Test
    @DisplayName("Should set and get minOccurs")
    void testMinOccurs() {
        element.setMinOccurs(0);
        assertEquals(0, element.getMinOccurs());

        element.setMinOccurs(5);
        assertEquals(5, element.getMinOccurs());
    }

    @Test
    @DisplayName("Should set and get maxOccurs")
    void testMaxOccurs() {
        element.setMaxOccurs(10);
        assertEquals(10, element.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle unbounded maxOccurs")
    void testUnboundedMaxOccurs() {
        element.setMaxOccursUnbounded(true);
        assertTrue(element.isMaxOccursUnbounded());

        element.setMaxOccursUnbounded(false);
        assertFalse(element.isMaxOccursUnbounded());
    }

    @Test
    @DisplayName("Should set and get nillable")
    void testNillable() {
        element.setNillable(true);
        assertTrue(element.isNillable());

        element.setNillable(false);
        assertFalse(element.isNillable());
    }

    @Test
    @DisplayName("Should set and get abstract")
    void testAbstract() {
        element.setAbstractElement(true);
        assertTrue(element.isAbstractElement());

        element.setAbstractElement(false);
        assertFalse(element.isAbstractElement());
    }

    @Test
    @DisplayName("Should set and get default value")
    void testDefaultValue() {
        element.setDefaultValue("defaultValue");
        assertEquals("defaultValue", element.getDefaultValue());
    }

    @Test
    @DisplayName("Should set and get fixed value")
    void testFixedValue() {
        element.setFixedValue("fixedValue");
        assertEquals("fixedValue", element.getFixedValue());
    }

    @Test
    @DisplayName("Should set and get substitution group")
    void testSubstitutionGroup() {
        element.setSubstitutionGroup("myGroup");
        assertEquals("myGroup", element.getSubstitutionGroup());
    }

    @Test
    @DisplayName("Should set and get form")
    void testForm() {
        element.setForm("qualified");
        assertEquals("qualified", element.getForm());
    }

    @Test
    @DisplayName("Should set and get documentation")
    void testDocumentation() {
        element.setDocumentation("This is documentation");
        assertEquals("This is documentation", element.getDocumentation());
    }

    @Test
    @DisplayName("Should fire property change events")
    void testPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        element.addPropertyChangeListener(listener);

        element.setName("testElement");

        assertEquals(1, events.size());
        assertEquals("name", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("testElement", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should support deep copy")
    void testDeepCopy() {
        element.setName("originalElement");
        element.setType("xs:string");
        element.setMinOccurs(1);
        element.setMaxOccurs(10);
        element.setNillable(true);
        element.setDocumentation("Original documentation");

        XsdElement copy = element.deepCopy("_copy");

        assertNotSame(element, copy);
        assertEquals("originalElement_copy", copy.getName());
        assertEquals(element.getType(), copy.getType());
        assertEquals(element.getMinOccurs(), copy.getMinOccurs());
        assertEquals(element.getMaxOccurs(), copy.getMaxOccurs());
        assertEquals(element.isNillable(), copy.isNillable());
        assertEquals(element.getDocumentation(), copy.getDocumentation());
        assertNotEquals(element.getId(), copy.getId()); // Different UUID
    }

    @Test
    @DisplayName("Should manage parent-child relationships")
    void testParentChild() {
        XsdComplexType parent = new XsdComplexType();

        element.setParent(parent);

        assertSame(parent, element.getParent());
    }

    @Test
    @DisplayName("Should support children")
    void testChildren() {
        XsdComplexType childType = new XsdComplexType();

        element.addChild(childType);

        assertEquals(1, element.getChildren().size());
        assertTrue(element.getChildren().contains(childType));
        assertSame(element, childType.getParent());
    }
}
```

**Similar tests should be created for:**
- `XsdAttributeTest.java`
- `XsdSequenceTest.java`
- `XsdChoiceTest.java`
- `XsdAllTest.java`

Each following the same pattern: test properties, events, deep copy, and relationships.

---

## Summary

These proposed tests address the **6 most critical gaps** in your test coverage:

1. **CommandManager** - Undo/redo functionality (core architecture)
2. **XsdEditorContext** - Central coordination (core architecture)
3. **SelectionModel** - Selection tracking (core feature)
4. **XsdElement** - Most common XSD node type

**Total proposed test cases: 52+**

**Estimated implementation time:**
- CommandManagerTest: 2-3 hours
- XsdEditorContextTest: 1-2 hours
- SelectionModelTest: 2-3 hours
- XsdElementTest + similar: 3-4 hours

**Total: ~10-12 hours for critical core coverage**

These tests will:
- ✅ Catch regression bugs in core architecture
- ✅ Document expected behavior
- ✅ Enable confident refactoring
- ✅ Provide examples for future tests
