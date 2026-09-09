package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonSyntaxException;

/**
 * Tests for {@link JsonEditorContext}.
 */
class JsonEditorContextTest {

    private static final String SAMPLE = "{\"name\": \"Alice\", \"age\": 30}";

    private JsonEditorContext context;
    private List<PropertyChangeEvent> events;

    @BeforeEach
    void setUp() {
        PropertiesService properties = mock(PropertiesService.class);
        when(properties.getJsonIndentSpaces()).thenReturn(4);
        ServiceRegistry.register(PropertiesService.class, properties);

        context = new JsonEditorContext();
        events = new ArrayList<>();
        context.addPropertyChangeListener(events::add);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
    }

    private List<PropertyChangeEvent> eventsNamed(String name) {
        return events.stream().filter(e -> name.equals(e.getPropertyName())).toList();
    }

    private JsonPrimitive nameProperty() {
        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        return (JsonPrimitive) root.getProperty("name");
    }

    // ==================== Loading ====================

    @Test
    void newContextHasEmptyDocument() {
        assertNotNull(context.getDocument());
        assertNull(context.getDocument().getRootValue());
        assertFalse(context.isDirty());
        assertTrue(context.isEditMode());
        assertFalse(context.canUndo());
        assertFalse(context.canRedo());
        assertFalse(context.isLossyFormat());
    }

    @Test
    void loadFiresDocumentEventAndClearsHistory() {
        context.loadDocumentFromString(SAMPLE);
        context.executeCommand(new SetPrimitiveValueCommand(nameProperty(), "Bob"));
        assertTrue(context.canUndo());
        context.getSelectionModel().setSelectedNode(nameProperty());
        events.clear();

        context.loadDocumentFromString("{\"other\": 1}");

        assertEquals(1, eventsNamed("document").size());
        assertFalse(context.canUndo());
        assertFalse(context.canRedo());
        assertFalse(context.isDirty());
        assertFalse(context.getSelectionModel().hasSelection());
        assertNotNull(((JsonObject) context.getDocument().getRootValue()).getProperty("other"));
    }

    @Test
    void loadPropagatesSyntaxErrors() {
        assertThrows(JsonSyntaxException.class, () -> context.loadDocumentFromString("{\"a\": }"));
    }

    @Test
    void loadRecordsFormat() {
        context.loadDocumentFromString("// comment\n{\"a\": 1}");
        assertEquals("jsonc", context.getFormat());
    }

    @Test
    void setDocumentFiresDocumentEvent() {
        JsonDocument doc = new JsonDocument(new JsonObject());
        events.clear();

        context.setDocument(doc);

        assertSame(doc, context.getDocument());
        assertEquals(1, eventsNamed("document").size());
        assertSame(doc, eventsNamed("document").get(0).getNewValue());
    }

    // ==================== Serialization ====================

    @Test
    void serializeHonoursConfiguredIndent() {
        context.loadDocumentFromString(SAMPLE);

        String out = context.serializeToString();

        assertEquals("{\n    \"name\": \"Alice\",\n    \"age\": 30\n}", out);
    }

    @Test
    void serializeFallsBackToTwoSpacesWithoutPropertiesService() {
        ServiceRegistry.reset();
        ServiceRegistry.registerFactory(PropertiesService.class, () -> {
            throw new IllegalStateException("unavailable");
        });
        context.loadDocumentFromString(SAMPLE);

        String out = context.serializeToString();

        assertEquals("{\n  \"name\": \"Alice\",\n  \"age\": 30\n}", out);
    }

    @Test
    void serializeKeepsTrailingNewlineIffPresentInSource() {
        context.loadDocumentFromString(SAMPLE + "\n");
        assertTrue(context.serializeToString().endsWith("}\n"));

        context.loadDocumentFromString(SAMPLE);
        assertTrue(context.serializeToString().endsWith("}"));
    }

    @Test
    void serializeRoundTripsNumbersUnchanged() {
        String text = "{\"i\": 42, \"big\": 12345678901234, \"d\": 1.5, \"neg\": -3}\n";
        context.loadDocumentFromString(text);

        assertEquals("{\n    \"i\": 42,\n    \"big\": 12345678901234,\n    \"d\": 1.5,\n    \"neg\": -3\n}\n",
                context.serializeToString());
    }

    // ==================== Commands ====================

    @Test
    void executeCommandFiresModelChangedAndCanUndo() {
        context.loadDocumentFromString(SAMPLE);
        events.clear();

        boolean ok = context.executeCommand(new SetPrimitiveValueCommand(nameProperty(), "Bob"));

        assertTrue(ok);
        assertEquals("Bob", nameProperty().getValue());
        assertEquals(1, eventsNamed("modelChanged").size());
        assertEquals(1L, eventsNamed("modelChanged").get(0).getNewValue());
        assertTrue(eventsNamed("canUndo").stream().anyMatch(e -> Boolean.TRUE.equals(e.getNewValue())));
        assertTrue(eventsNamed("dirty").stream().anyMatch(e -> Boolean.TRUE.equals(e.getNewValue())));
        assertTrue(context.isDirty());
        assertTrue(context.canUndo());
        assertFalse(context.canRedo());
    }

    @Test
    void modelChangedRevisionIncreases() {
        context.loadDocumentFromString(SAMPLE);
        context.executeCommand(new SetPrimitiveValueCommand(nameProperty(), "Bob"));
        context.executeCommand(new SetPrimitiveValueCommand(nameProperty(), "Carol"));

        List<PropertyChangeEvent> changed = eventsNamed("modelChanged");
        assertEquals(2, changed.size());
        assertEquals(1L, changed.get(0).getNewValue());
        assertEquals(2L, changed.get(1).getNewValue());
    }

    @Test
    void failedCommandFiresNoModelChanged() {
        context.loadDocumentFromString(SAMPLE);
        events.clear();

        boolean ok = context.executeCommand(
                new org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand(nameProperty(), "age"));

        assertFalse(ok);
        assertTrue(eventsNamed("modelChanged").isEmpty());
        assertFalse(context.canUndo());
    }

    @Test
    void undoAndRedoRestoreValuesAndFireEvents() {
        context.loadDocumentFromString(SAMPLE);
        context.executeCommand(new SetPrimitiveValueCommand(nameProperty(), "Bob"));
        events.clear();

        assertTrue(context.undo());
        assertEquals("Alice", nameProperty().getValue());
        assertEquals(1, eventsNamed("modelChanged").size());
        assertFalse(context.canUndo());
        assertTrue(context.canRedo());

        assertTrue(context.redo());
        assertEquals("Bob", nameProperty().getValue());
        assertEquals(2, eventsNamed("modelChanged").size());
        assertTrue(context.canUndo());
        assertFalse(context.canRedo());
    }

    @Test
    void undoWithEmptyHistoryReturnsFalse() {
        assertFalse(context.undo());
        assertFalse(context.redo());
        assertTrue(eventsNamed("modelChanged").isEmpty());
    }

    @Test
    void clearHistoryDropsUndoRedo() {
        context.loadDocumentFromString(SAMPLE);
        context.executeCommand(new SetPrimitiveValueCommand(nameProperty(), "Bob"));

        context.clearHistory();

        assertFalse(context.canUndo());
        assertFalse(context.canRedo());
    }

    @Test
    void executeCommandThrowsInReadOnlyMode() {
        context.loadDocumentFromString(SAMPLE);
        context.setEditMode(false);
        assertFalse(context.isEditMode());
        assertEquals(1, eventsNamed("editMode").size());

        SetPrimitiveValueCommand cmd = new SetPrimitiveValueCommand(nameProperty(), "Bob");
        assertThrows(IllegalStateException.class, () -> context.executeCommand(cmd));
        assertEquals("Alice", nameProperty().getValue());
    }

    @Test
    void setDirtyFiresEvent() {
        context.setDirty(true);
        assertTrue(context.isDirty());
        assertEquals(1, eventsNamed("dirty").size());
    }

    // ==================== Lossy format detection ====================

    @Test
    void hasLossyConstructsDetectsLineComments() {
        assertTrue(JsonEditorContext.hasLossyConstructs("{\"a\": 1} // c"));
    }

    @Test
    void hasLossyConstructsDetectsBlockComments() {
        assertTrue(JsonEditorContext.hasLossyConstructs("/* c */ {\"a\": 1}"));
    }

    @Test
    void hasLossyConstructsIgnoresSlashesInsideStrings() {
        assertFalse(JsonEditorContext.hasLossyConstructs("{\"url\":\"http://x\"}"));
        assertFalse(JsonEditorContext.hasLossyConstructs("{\"note\":\"/* not a comment */\"}"));
    }

    @Test
    void hasLossyConstructsDetectsJson5UnquotedKeys() {
        assertTrue(JsonEditorContext.hasLossyConstructs("{a: 1}"));
    }

    @Test
    void hasLossyConstructsDetectsJson5TrailingCommas() {
        assertTrue(JsonEditorContext.hasLossyConstructs("{\"a\": 1,}"));
    }

    @Test
    void hasLossyConstructsIsFalseForPlainJson() {
        assertFalse(JsonEditorContext.hasLossyConstructs(SAMPLE));
        assertFalse(JsonEditorContext.hasLossyConstructs("{\"quote\": \"it's\"}"));
        assertFalse(JsonEditorContext.hasLossyConstructs(""));
        assertFalse(JsonEditorContext.hasLossyConstructs(null));
    }

    @Test
    void lossyFormatDetectedFiredOnlyWhenLossy() {
        context.loadDocumentFromString(SAMPLE);
        assertTrue(eventsNamed("lossyFormatDetected").isEmpty());
        assertFalse(context.isLossyFormat());

        context.loadDocumentFromString("// header\n{\"a\": 1}");
        List<PropertyChangeEvent> lossy = eventsNamed("lossyFormatDetected");
        assertEquals(1, lossy.size());
        assertEquals(false, lossy.get(0).getOldValue());
        assertEquals(true, lossy.get(0).getNewValue());
        assertTrue(context.isLossyFormat());

        context.loadDocumentFromString(SAMPLE);
        assertFalse(context.isLossyFormat());
        assertEquals(1, eventsNamed("lossyFormatDetected").size());
    }

    // ==================== Listener management ====================

    @Test
    void namedListenerReceivesOnlyItsProperty() {
        List<PropertyChangeEvent> named = new ArrayList<>();
        context.addPropertyChangeListener("editMode", named::add);

        context.setEditMode(false);
        context.setDirty(true);

        assertEquals(1, named.size());
        assertEquals("editMode", named.get(0).getPropertyName());

        context.removePropertyChangeListener("editMode", named::add);
    }

    @Test
    void removedListenerReceivesNothing() {
        context.removePropertyChangeListener(events::add);
        List<PropertyChangeEvent> local = new ArrayList<>();
        java.beans.PropertyChangeListener listener = local::add;
        context.addPropertyChangeListener(listener);
        context.removePropertyChangeListener(listener);

        context.setEditMode(false);

        assertTrue(local.isEmpty());
    }
}
