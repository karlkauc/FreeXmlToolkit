package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CommandManager class.
 *
 * @author Claude Code
 * @since 2.0
 */
class CommandManagerTest {

    private CommandManager manager;
    private List<PropertyChangeEvent> events;

    @BeforeEach
    void setUp() {
        manager = new CommandManager();
        events = new ArrayList<>();
        manager.addPropertyChangeListener(evt -> events.add(evt));
    }

    @Test
    void testInitialState() {
        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());
        assertFalse(manager.isDirty());
        assertNull(manager.getUndoDescription());
        assertNull(manager.getRedoDescription());
        assertEquals(0, manager.getUndoStackSize());
        assertEquals(0, manager.getRedoStackSize());
    }

    @Test
    void testExecuteCommand() {
        XmlElement element = new XmlElement("test");
        RenameNodeCommand cmd = new RenameNodeCommand(element, "newName");

        boolean success = manager.executeCommand(cmd);

        assertTrue(success);
        assertEquals("newName", element.getName());
        assertTrue(manager.canUndo());
        assertFalse(manager.canRedo());
        assertTrue(manager.isDirty());
        assertEquals(1, manager.getUndoStackSize());
        assertEquals(0, manager.getRedoStackSize());
    }

    @Test
    void testUndo() {
        XmlElement element = new XmlElement("test");
        RenameNodeCommand cmd = new RenameNodeCommand(element, "newName");

        manager.executeCommand(cmd);
        boolean success = manager.undo();

        assertTrue(success);
        assertEquals("test", element.getName());
        assertFalse(manager.canUndo());
        assertTrue(manager.canRedo());
        assertEquals(0, manager.getUndoStackSize());
        assertEquals(1, manager.getRedoStackSize());
    }

    @Test
    void testRedo() {
        XmlElement element = new XmlElement("test");
        RenameNodeCommand cmd = new RenameNodeCommand(element, "newName");

        manager.executeCommand(cmd);
        manager.undo();
        boolean success = manager.redo();

        assertTrue(success);
        assertEquals("newName", element.getName());
        assertTrue(manager.canUndo());
        assertFalse(manager.canRedo());
        assertEquals(1, manager.getUndoStackSize());
        assertEquals(0, manager.getRedoStackSize());
    }

    @Test
    void testMultipleUndoRedo() {
        // Use different elements to prevent command merging (canMergeWith returns true for same element)
        XmlElement element1 = new XmlElement("test1");
        XmlElement element2 = new XmlElement("test2");
        XmlElement element3 = new XmlElement("test3");

        manager.executeCommand(new RenameNodeCommand(element1, "name1"));
        manager.executeCommand(new RenameNodeCommand(element2, "name2"));
        manager.executeCommand(new RenameNodeCommand(element3, "name3"));

        assertEquals("name1", element1.getName());
        assertEquals("name2", element2.getName());
        assertEquals("name3", element3.getName());
        assertEquals(3, manager.getUndoStackSize());

        manager.undo();
        assertEquals("test3", element3.getName()); // Reverted to original

        manager.undo();
        assertEquals("test2", element2.getName()); // Reverted to original

        manager.redo();
        assertEquals("name2", element2.getName()); // Re-applied

        manager.redo();
        assertEquals("name3", element3.getName()); // Re-applied
    }

    @Test
    void testRedoStackClearedOnNewCommand() {
        // Use different elements to prevent command merging (canMergeWith returns true for same element)
        XmlElement element1 = new XmlElement("test1");
        XmlElement element2 = new XmlElement("test2");
        XmlElement element3 = new XmlElement("test3");

        manager.executeCommand(new RenameNodeCommand(element1, "name1"));
        manager.executeCommand(new RenameNodeCommand(element2, "name2"));
        manager.undo();
        manager.undo();

        // Redo stack should have 2 commands
        assertEquals(2, manager.getRedoStackSize());

        // Execute new command - redo stack should be cleared
        manager.executeCommand(new RenameNodeCommand(element3, "name3"));

        assertEquals(0, manager.getRedoStackSize());
        assertFalse(manager.canRedo());
    }

    @Test
    void testHistoryLimit() {
        CommandManager limitedManager = new CommandManager(5);

        // Use different elements for each command to prevent merging
        // (canMergeWith returns true for same element)
        for (int i = 0; i < 10; i++) {
            XmlElement element = new XmlElement("test" + i);
            limitedManager.executeCommand(new RenameNodeCommand(element, "name" + i));
        }

        // Should only keep last 5
        assertEquals(5, limitedManager.getUndoStackSize());
    }

    @Test
    void testClear() {
        XmlElement element = new XmlElement("test");
        manager.executeCommand(new RenameNodeCommand(element, "newName"));

        manager.clear();

        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());
        assertEquals(0, manager.getUndoStackSize());
        assertEquals(0, manager.getRedoStackSize());
    }

    @Test
    void testMarkAsSaved() {
        XmlElement element = new XmlElement("test");
        manager.executeCommand(new RenameNodeCommand(element, "newName"));

        assertTrue(manager.isDirty());

        manager.markAsSaved();

        assertFalse(manager.isDirty());
    }

    @Test
    void testGetUndoDescription() {
        XmlElement element = new XmlElement("test");
        manager.executeCommand(new RenameNodeCommand(element, "newName"));

        String desc = manager.getUndoDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("Rename"));
    }

    @Test
    void testGetRedoDescription() {
        XmlElement element = new XmlElement("test");
        manager.executeCommand(new RenameNodeCommand(element, "newName"));
        manager.undo();

        String desc = manager.getRedoDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("Rename"));
    }

    @Test
    void testPropertyChangeEvents() {
        XmlElement element = new XmlElement("test");

        events.clear();
        manager.executeCommand(new RenameNodeCommand(element, "newName"));

        // Should fire: canUndo, canRedo, undoDescription, redoDescription, dirty
        assertTrue(events.size() >= 4);

        boolean foundCanUndo = events.stream().anyMatch(e -> e.getPropertyName().equals("canUndo"));
        boolean foundDirty = events.stream().anyMatch(e -> e.getPropertyName().equals("dirty"));

        assertTrue(foundCanUndo);
        assertTrue(foundDirty);
    }

    @Test
    void testUndoWithoutCommands() {
        boolean success = manager.undo();
        assertFalse(success);
    }

    @Test
    void testRedoWithoutCommands() {
        boolean success = manager.redo();
        assertFalse(success);
    }

    @Test
    void testSetHistoryLimit() {
        manager.setHistoryLimit(10);
        assertEquals(10, manager.getHistoryLimit());
    }

    @Test
    void testSetHistoryLimitInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.setHistoryLimit(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            manager.setHistoryLimit(-1);
        });
    }

    @Test
    void testExecuteNullCommand() {
        boolean success = manager.executeCommand(null);
        assertFalse(success);
    }

    @Test
    void testToString() {
        String str = manager.toString();

        assertTrue(str.contains("CommandManager"));
        assertTrue(str.contains("undoStack"));
        assertTrue(str.contains("redoStack"));
        assertTrue(str.contains("dirty"));
    }
}
