package org.fxt.freexmltoolkit.controls.v2.editor;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand;
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

/**
 * Unit tests for XsdEditorContext - the central coordination point for V2 Editor.
 * Tests context initialization, dirty flag management, edit mode, and property change events.
 */
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

        assertTrue(context.isDirty(), "Dirty flag should be set automatically after command execution");
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
        assertEquals(2, allEvents.size(), "General listener should receive all events");

        // Dirty listener should only receive dirty event
        assertEquals(1, dirtyEvents.size(), "Specific listener should only receive its events");
        assertEquals("dirty", dirtyEvents.get(0).getPropertyName());

        // Remove listeners
        context.removePropertyChangeListener(allListener);
        context.removePropertyChangeListener("dirty", dirtyListener);

        allEvents.clear();
        dirtyEvents.clear();

        context.setDirty(false);

        assertEquals(0, allEvents.size(), "No events after listener removal");
        assertEquals(0, dirtyEvents.size(), "No events after listener removal");
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

    @Test
    @DisplayName("Should not fire property change when value doesn't change")
    void testNoPropertyChangeWhenValueUnchanged() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        context.addPropertyChangeListener("editMode", events::add);

        context.setEditMode(true); // Already true by default

        assertEquals(0, events.size(), "Should not fire event when value doesn't change");
    }

    @Test
    @DisplayName("Should maintain command manager across multiple operations")
    void testCommandManagerPersistence() {
        TestCommand command1 = new TestCommand();
        TestCommand command2 = new TestCommand();

        context.getCommandManager().executeCommand(command1);
        context.getCommandManager().executeCommand(command2);

        assertEquals(2, context.getCommandManager().getUndoStackSize());
        assertTrue(context.isDirty());
    }

    // ========== HELPER TEST CLASS ==========

    /**
     * Simple test command for testing context integration.
     */
    private static class TestCommand implements XsdCommand {
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
    }
}
