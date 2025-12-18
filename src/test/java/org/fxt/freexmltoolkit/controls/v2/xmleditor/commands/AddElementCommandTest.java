package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AddElementCommand class.
 *
 * @author Claude Code
 * @since 2.0
 */
class AddElementCommandTest {

    private XmlElement parent;
    private XmlElement child;

    @BeforeEach
    void setUp() {
        parent = new XmlElement("parent");
        child = new XmlElement("child");
    }

    @Test
    void testExecute() {
        AddElementCommand cmd = new AddElementCommand(parent, child);

        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(1, parent.getChildCount());
        assertEquals(child, parent.getChildren().get(0));
        assertEquals(parent, child.getParent());
    }

    @Test
    void testExecuteAtIndex() {
        parent.addChild(new XmlElement("existing"));

        AddElementCommand cmd = new AddElementCommand(parent, child, 0);
        cmd.execute();

        assertEquals(2, parent.getChildCount());
        assertEquals(child, parent.getChildren().get(0));
    }

    @Test
    void testUndo() {
        AddElementCommand cmd = new AddElementCommand(parent, child);

        cmd.execute();
        boolean success = cmd.undo();

        assertTrue(success);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
    }

    @Test
    void testUndoWithoutExecute() {
        AddElementCommand cmd = new AddElementCommand(parent, child);

        boolean success = cmd.undo();

        assertFalse(success);
    }

    @Test
    void testExecuteUndo() {
        AddElementCommand cmd = new AddElementCommand(parent, child);

        cmd.execute();
        cmd.undo();
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(1, parent.getChildCount());
        assertEquals(child, parent.getChildren().get(0));
    }

    @Test
    void testAddToDocument() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");

        AddElementCommand cmd = new AddElementCommand(doc, root);
        cmd.execute();

        assertEquals(1, doc.getChildCount());
        assertEquals(root, doc.getChildren().get(0));
    }

    @Test
    void testUndoFromDocument() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");

        AddElementCommand cmd = new AddElementCommand(doc, root);
        cmd.execute();
        cmd.undo();

        assertEquals(0, doc.getChildCount());
    }

    @Test
    void testGetDescription() {
        AddElementCommand cmd = new AddElementCommand(parent, child);

        String desc = cmd.getDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("Add"));
        assertTrue(desc.contains("child"));
    }

    @Test
    void testToString() {
        AddElementCommand cmd = new AddElementCommand(parent, child);

        String str = cmd.toString();

        assertNotNull(str);
        assertTrue(str.contains("Add"));
    }

    // ==================== Mixed Content Prevention Tests ====================

    @Test
    void testExecute_ParentHasTextContent_ReturnsFalse() {
        // Setup: parent has non-whitespace text content
        parent.addChild(new XmlText("some text"));

        AddElementCommand cmd = new AddElementCommand(parent, child);
        boolean success = cmd.execute();

        // Should fail - cannot add element to parent with text content
        assertFalse(success);
        assertEquals(1, parent.getChildCount()); // Only the text node
        assertInstanceOf(XmlText.class, parent.getChildren().get(0));
    }

    @Test
    void testExecute_ParentHasNoTextContent_ReturnsTrue() {
        // Setup: parent has no text content
        AddElementCommand cmd = new AddElementCommand(parent, child);
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(1, parent.getChildCount());
        assertEquals(child, parent.getChildren().get(0));
    }

    @Test
    void testExecute_ParentHasExistingChildElements_ReturnsTrue() {
        // Setup: parent already has child elements (no text)
        parent.addChild(new XmlElement("existing"));

        AddElementCommand cmd = new AddElementCommand(parent, child);
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(2, parent.getChildCount());
    }

    @Test
    void testExecuteAtIndex_ParentHasTextContent_ReturnsFalse() {
        // Setup: parent has non-whitespace text content
        parent.addChild(new XmlText("some text"));

        AddElementCommand cmd = new AddElementCommand(parent, child, 0);
        boolean success = cmd.execute();

        assertFalse(success);
    }

    @Test
    void testExecute_ParentHasWhitespaceOnlyText_ReturnsTrue() {
        // Setup: parent has whitespace-only text content (should be allowed)
        parent.addChild(new XmlText("   \n\t  "));

        AddElementCommand cmd = new AddElementCommand(parent, child);
        boolean success = cmd.execute();

        // Should succeed - whitespace-only text is not considered mixed content
        assertTrue(success);
        assertEquals(2, parent.getChildCount());
    }
}
