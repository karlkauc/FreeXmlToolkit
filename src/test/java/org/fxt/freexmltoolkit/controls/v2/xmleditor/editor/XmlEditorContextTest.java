package org.fxt.freexmltoolkit.controls.v2.xmleditor.editor;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.AddElementCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlEditorContext class.
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlEditorContextTest {

    @TempDir
    Path tempDir;

    private XmlEditorContext context;
    private XmlDocument document;
    private XmlElement root;

    @BeforeEach
    void setUp() {
        document = new XmlDocument();
        root = new XmlElement("root");
        document.setRootElement(root);
        context = new XmlEditorContext(document);
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructorWithDocument() {
        assertNotNull(context.getDocument());
        assertEquals(document, context.getDocument());
        assertNotNull(context.getCommandManager());
        assertNotNull(context.getSelectionModel());
    }

    @Test
    void testConstructorThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            new XmlEditorContext(null);
        });
    }

    @Test
    void testDefaultConstructor() {
        XmlEditorContext ctx = new XmlEditorContext();

        assertNotNull(ctx.getDocument());
        assertNull(ctx.getDocument().getRootElement());
    }

    // ==================== Document Management Tests ====================

    @Test
    void testNewDocument() {
        context.setEditMode(true);
        XmlElement child = new XmlElement("child");
        root.addChild(child);
        context.markAsDirty();

        context.newDocument();

        assertNotNull(context.getDocument());
        assertNotEquals(document, context.getDocument());
        assertNull(context.getDocument().getRootElement());
        assertNull(context.getFilePath());
        assertFalse(context.isDirty());
    }

    @Test
    void testLoadDocumentFromString() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<test><child/></test>";

        context.loadDocumentFromString(xml);

        assertNotNull(context.getDocument().getRootElement());
        assertEquals("test", context.getDocument().getRootElement().getName());
        assertEquals(1, context.getDocument().getRootElement().getChildCount());
    }

    @Test
    void testLoadDocumentFromFile() throws IOException {
        Path testFile = tempDir.resolve("test.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<test/>";
        Files.writeString(testFile, xml);

        context.loadDocument(testFile.toString());

        assertEquals("test", context.getDocument().getRootElement().getName());
        assertEquals(testFile.toString(), context.getFilePath());
        assertEquals("test.xml", context.getFileName());
    }

    @Test
    void testGetFileNameReturnsUntitledForNewDocument() {
        context.newDocument();

        assertEquals("Untitled", context.getFileName());
    }

    @Test
    void testSerializeToString() {
        String xml = context.serializeToString();

        assertNotNull(xml);
        assertTrue(xml.contains("<?xml"));
        assertTrue(xml.contains("<root"));
    }

    // ==================== Save Tests ====================

    @Test
    void testSaveThrowsWithoutFilePath() {
        assertThrows(IllegalStateException.class, () -> {
            context.save();
        });
    }

    @Test
    void testSaveAs() throws IOException {
        Path testFile = tempDir.resolve("output.xml");

        context.saveAs(testFile.toString(), false);

        assertTrue(Files.exists(testFile));
        assertEquals(testFile.toString(), context.getFilePath());
        assertFalse(context.isDirty());
    }

    @Test
    void testSaveWithFilePath() throws IOException {
        Path testFile = tempDir.resolve("output.xml");
        context.saveAs(testFile.toString(), false);

        // Modify document
        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);
        assertTrue(context.isDirty());

        // Save
        context.save();

        assertFalse(context.isDirty());
        assertTrue(Files.exists(testFile));
    }

    @Test
    void testSaveAsCreatesBackup() throws IOException {
        Path testFile = tempDir.resolve("output.xml");

        // Create original file
        Files.writeString(testFile, "<?xml version=\"1.0\"?>\n<old/>");

        // Save new content with backup
        context.saveAs(testFile.toString(), true);

        // Check that backup was created
        File[] backups = tempDir.toFile().listFiles((dir, name) ->
                name.startsWith("output.xml.backup_"));
        assertNotNull(backups);
        assertTrue(backups.length > 0);
    }

    // ==================== Command Execution Tests ====================

    @Test
    void testExecuteCommand() {
        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);

        boolean success = context.executeCommand(cmd);

        assertTrue(success);
        assertEquals(1, root.getChildCount());
        assertTrue(context.canUndo());
        assertTrue(context.isDirty());
    }

    @Test
    void testExecuteCommandInReadOnlyMode() {
        context.setEditMode(false);

        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);

        assertThrows(IllegalStateException.class, () -> {
            context.executeCommand(cmd);
        });
    }

    @Test
    void testUndo() {
        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);

        boolean success = context.undo();

        assertTrue(success);
        assertEquals(0, root.getChildCount());
        assertTrue(context.canRedo());
    }

    @Test
    void testRedo() {
        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);
        context.undo();

        boolean success = context.redo();

        assertTrue(success);
        assertEquals(1, root.getChildCount());
        assertTrue(context.canUndo());
    }

    @Test
    void testCanUndoCanRedo() {
        assertFalse(context.canUndo());
        assertFalse(context.canRedo());

        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);

        assertTrue(context.canUndo());
        assertFalse(context.canRedo());

        context.undo();

        assertFalse(context.canUndo());
        assertTrue(context.canRedo());
    }

    @Test
    void testClearHistory() {
        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);

        context.clearHistory();

        assertFalse(context.canUndo());
        assertFalse(context.canRedo());
    }

    // ==================== Edit Mode Tests ====================

    @Test
    void testSetEditMode() {
        assertTrue(context.isEditMode()); // Default is true

        context.setEditMode(false);

        assertFalse(context.isEditMode());
    }

    @Test
    void testEditModePropertyChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("editMode", listener);
        context.setEditMode(false);

        assertEquals(1, events.size());
        assertEquals("editMode", events.get(0).getPropertyName());
        assertEquals(true, events.get(0).getOldValue());
        assertEquals(false, events.get(0).getNewValue());
    }

    // ==================== Dirty Flag Tests ====================

    @Test
    void testMarkAsDirty() {
        assertFalse(context.isDirty());

        context.markAsDirty();

        assertTrue(context.isDirty());
    }

    @Test
    void testDirtyFlagSetByCommand() {
        assertFalse(context.isDirty());

        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);

        assertTrue(context.isDirty());
    }

    @Test
    void testDirtyFlagClearedBySave() throws IOException {
        Path testFile = tempDir.resolve("test.xml");
        context.saveAs(testFile.toString(), false);

        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);
        assertTrue(context.isDirty());

        context.save();

        assertFalse(context.isDirty());
    }

    @Test
    void testDirtyPropertyChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("dirty", listener);
        context.markAsDirty();

        assertEquals(1, events.size());
        assertEquals("dirty", events.get(0).getPropertyName());
        assertEquals(false, events.get(0).getOldValue());
        assertEquals(true, events.get(0).getNewValue());
    }

    // ==================== PropertyChangeSupport Tests ====================

    @Test
    void testDocumentPropertyChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("document", listener);
        context.newDocument();

        assertEquals(1, events.size());
        assertEquals("document", events.get(0).getPropertyName());
        assertEquals(document, events.get(0).getOldValue());
        assertNotEquals(document, events.get(0).getNewValue());
    }

    @Test
    void testFilePathPropertyChange() throws IOException {
        Path testFile = tempDir.resolve("test.xml");

        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("filePath", listener);
        context.saveAs(testFile.toString(), false);

        assertEquals(1, events.size());
        assertEquals("filePath", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals(testFile.toString(), events.get(0).getNewValue());
    }

    @Test
    void testCanUndoPropertyChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("canUndo", listener);

        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);

        assertEquals(1, events.size());
        assertEquals("canUndo", events.get(0).getPropertyName());
    }

    @Test
    void testCanRedoPropertyChange() {
        XmlElement child = new XmlElement("child");
        AddElementCommand cmd = new AddElementCommand(root, child);
        context.executeCommand(cmd);

        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("canRedo", listener);
        context.undo();

        assertEquals(1, events.size());
        assertEquals("canRedo", events.get(0).getPropertyName());
    }

    @Test
    void testRemovePropertyChangeListener() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        context.addPropertyChangeListener("dirty", listener);
        context.markAsDirty();
        assertEquals(1, events.size());

        context.removePropertyChangeListener("dirty", listener);
        context.setEditMode(false);
        context.setEditMode(true);
        // No new dirty event
        assertEquals(1, events.size());
    }

    // ==================== Integration Tests ====================

    @Test
    void testCompleteWorkflow() throws IOException {
        // Create new document
        context.newDocument();
        XmlElement newRoot = new XmlElement("book");
        context.getDocument().setRootElement(newRoot);

        // Add elements via commands
        XmlElement title = new XmlElement("title");
        AddElementCommand addTitleCmd = new AddElementCommand(newRoot, title);
        context.executeCommand(addTitleCmd);

        XmlElement author = new XmlElement("author");
        AddElementCommand addAuthorCmd = new AddElementCommand(newRoot, author);
        context.executeCommand(addAuthorCmd);

        // Rename title
        RenameNodeCommand renameCmd = new RenameNodeCommand(title, "bookTitle");
        context.executeCommand(renameCmd);

        assertEquals(2, newRoot.getChildCount());
        assertEquals("bookTitle", title.getName());
        assertTrue(context.isDirty());

        // Undo rename
        context.undo();
        assertEquals("title", title.getName());

        // Redo rename
        context.redo();
        assertEquals("bookTitle", title.getName());

        // Save
        Path testFile = tempDir.resolve("book.xml");
        context.saveAs(testFile.toString());

        assertFalse(context.isDirty());
        assertTrue(Files.exists(testFile));

        // Load saved file
        XmlEditorContext newContext = new XmlEditorContext();
        newContext.loadDocument(testFile.toString());

        assertEquals("book", newContext.getDocument().getRootElement().getName());
        assertEquals(2, newContext.getDocument().getRootElement().getChildCount());
    }

    @Test
    void testGetters() {
        assertNotNull(context.getDocument());
        assertNotNull(context.getCommandManager());
        assertNotNull(context.getSelectionModel());
        assertNotNull(context.getParser());
        assertNotNull(context.getSerializer());
    }

    @Test
    void testToString() {
        String str = context.toString();

        assertNotNull(str);
        assertTrue(str.contains("XmlEditorContext"));
        assertTrue(str.contains("Untitled"));
    }
}
