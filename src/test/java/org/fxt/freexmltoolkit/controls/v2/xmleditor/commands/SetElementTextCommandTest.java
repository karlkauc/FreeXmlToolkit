package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SetElementTextCommand class.
 *
 * @author Claude Code
 * @since 2.0
 */
class SetElementTextCommandTest {

    private XmlElement element;

    @BeforeEach
    void setUp() {
        element = new XmlElement("test");
    }

    @Test
    void testExecute_SetText_Success() {
        SetElementTextCommand cmd = new SetElementTextCommand(element, "Hello World");
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(1, element.getChildCount());
        assertInstanceOf(XmlText.class, element.getChildren().get(0));
        assertEquals("Hello World", element.getTextContent());
    }

    @Test
    void testExecute_ReplaceText_Success() {
        element.addChild(new XmlText("old text"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "new text");
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(1, element.getChildCount());
        assertEquals("new text", element.getTextContent());
    }

    @Test
    void testExecute_ClearText_Success() {
        element.addChild(new XmlText("some text"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "");
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(0, element.getChildCount());
    }

    @Test
    void testExecute_NullText_Success() {
        element.addChild(new XmlText("some text"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, null);
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(0, element.getChildCount());
    }

    @Test
    void testUndo_RestoresOldText() {
        element.addChild(new XmlText("original"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "new text");
        cmd.execute();
        boolean success = cmd.undo();

        assertTrue(success);
        assertEquals(1, element.getChildCount());
        assertEquals("original", element.getTextContent());
    }

    @Test
    void testUndo_WithoutExecute_ReturnsFalse() {
        SetElementTextCommand cmd = new SetElementTextCommand(element, "text");
        boolean success = cmd.undo();

        assertFalse(success);
    }

    @Test
    void testUndo_AfterClearingText() {
        element.addChild(new XmlText("original"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "");
        cmd.execute();
        cmd.undo();

        assertEquals(1, element.getChildCount());
        assertEquals("original", element.getTextContent());
    }

    @Test
    void testGetDescription() {
        SetElementTextCommand cmd = new SetElementTextCommand(element, "text");
        String desc = cmd.getDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("Edit") || desc.contains("Text"));
    }

    @Test
    void testCanMergeWith_SameElement_ReturnsTrue() {
        SetElementTextCommand cmd1 = new SetElementTextCommand(element, "text1");
        SetElementTextCommand cmd2 = new SetElementTextCommand(element, "text2");

        assertTrue(cmd1.canMergeWith(cmd2));
    }

    @Test
    void testCanMergeWith_DifferentElement_ReturnsFalse() {
        XmlElement otherElement = new XmlElement("other");
        SetElementTextCommand cmd1 = new SetElementTextCommand(element, "text1");
        SetElementTextCommand cmd2 = new SetElementTextCommand(otherElement, "text2");

        assertFalse(cmd1.canMergeWith(cmd2));
    }

    @Test
    void testCanMergeWith_DifferentCommandType_ReturnsFalse() {
        SetElementTextCommand cmd1 = new SetElementTextCommand(element, "text1");
        AddElementCommand cmd2 = new AddElementCommand(element, new XmlElement("child"));

        assertFalse(cmd1.canMergeWith(cmd2));
    }

    // ==================== Mixed Content Prevention Tests ====================

    @Test
    void testExecute_ElementHasChildElements_ReturnsFalse() {
        // Setup: element has child elements
        element.addChild(new XmlElement("child"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "some text");
        boolean success = cmd.execute();

        // Should fail - cannot add text to element with child elements
        assertFalse(success);
        assertEquals(1, element.getChildCount());
        assertInstanceOf(XmlElement.class, element.getChildren().get(0));
    }

    @Test
    void testExecute_ElementHasNoChildElements_ReturnsTrue() {
        SetElementTextCommand cmd = new SetElementTextCommand(element, "some text");
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals(1, element.getChildCount());
        assertEquals("some text", element.getTextContent());
    }

    @Test
    void testExecute_ElementHasExistingText_ReturnsTrue() {
        // Setup: element already has text (no child elements)
        element.addChild(new XmlText("old text"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "new text");
        boolean success = cmd.execute();

        assertTrue(success);
        assertEquals("new text", element.getTextContent());
    }

    @Test
    void testExecute_ClearTextWhenHasChildElements_Success() {
        // Setup: element has child elements
        element.addChild(new XmlElement("child"));

        // Clearing text (empty string) should succeed even with child elements
        SetElementTextCommand cmd = new SetElementTextCommand(element, "");
        boolean success = cmd.execute();

        assertTrue(success);
    }

    @Test
    void testExecute_NullTextWhenHasChildElements_Success() {
        // Setup: element has child elements
        element.addChild(new XmlElement("child"));

        // Null text should succeed even with child elements
        SetElementTextCommand cmd = new SetElementTextCommand(element, null);
        boolean success = cmd.execute();

        assertTrue(success);
    }

    @Test
    void testExecute_MultipleChildElements_ReturnsFalse() {
        // Setup: element has multiple child elements
        element.addChild(new XmlElement("child1"));
        element.addChild(new XmlElement("child2"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "text");
        boolean success = cmd.execute();

        assertFalse(success);
        assertEquals(2, element.getChildCount());
    }

    @Test
    void testExecute_WhitespaceOnlyText_WithChildElements_ReturnsFalse() {
        // Setup: element has child elements - even whitespace-only text should fail
        element.addChild(new XmlElement("child"));

        SetElementTextCommand cmd = new SetElementTextCommand(element, "   \n\t  ");
        boolean success = cmd.execute();

        // Whitespace-only text should still fail because element has children
        assertFalse(success);
    }
}
