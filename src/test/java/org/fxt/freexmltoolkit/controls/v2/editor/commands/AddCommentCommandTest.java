package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AddCommentCommand.
 *
 * @since 2.0
 */
class AddCommentCommandTest {

    private XsdElement parentElement;

    @BeforeEach
    void setUp() {
        parentElement = new XsdElement("parent");
    }

    @Test
    @DisplayName("execute() should add comment as child of parent")
    void testExecuteAddsComment() {
        AddCommentCommand command = new AddCommentCommand(parentElement, " This is a comment ");

        boolean result = command.execute();

        assertTrue(result);
        assertEquals(1, parentElement.getChildren().size());
        assertInstanceOf(XsdComment.class, parentElement.getChildren().get(0));
        assertEquals(" This is a comment ", ((XsdComment) parentElement.getChildren().get(0)).getContent());
    }

    @Test
    @DisplayName("undo() should remove the added comment")
    void testUndoRemovesComment() {
        AddCommentCommand command = new AddCommentCommand(parentElement, "test comment");
        command.execute();
        assertEquals(1, parentElement.getChildren().size());

        boolean result = command.undo();

        assertTrue(result);
        assertEquals(0, parentElement.getChildren().size());
    }

    @Test
    @DisplayName("execute then undo then execute should work correctly")
    void testExecuteUndoExecuteCycle() {
        AddCommentCommand command = new AddCommentCommand(parentElement, "cycle test");

        command.execute();
        assertEquals(1, parentElement.getChildren().size());

        command.undo();
        assertEquals(0, parentElement.getChildren().size());

        command.execute();
        assertEquals(1, parentElement.getChildren().size());
    }

    @Test
    @DisplayName("should work with sequence as parent")
    void testAddCommentToSequence() {
        XsdSequence sequence = new XsdSequence();
        AddCommentCommand command = new AddCommentCommand(sequence, "comment in sequence");

        boolean result = command.execute();

        assertTrue(result);
        assertEquals(1, sequence.getChildren().size());
        assertInstanceOf(XsdComment.class, sequence.getChildren().get(0));
    }

    @Test
    @DisplayName("getDescription() should include parent name")
    void testGetDescription() {
        AddCommentCommand command = new AddCommentCommand(parentElement, "test");
        assertTrue(command.getDescription().contains("parent"));
    }

    @Test
    @DisplayName("getAddedComment() should return null before execute")
    void testGetAddedCommentBeforeExecute() {
        AddCommentCommand command = new AddCommentCommand(parentElement, "test");
        assertNull(command.getAddedComment());
    }

    @Test
    @DisplayName("getAddedComment() should return comment after execute")
    void testGetAddedCommentAfterExecute() {
        AddCommentCommand command = new AddCommentCommand(parentElement, "test");
        command.execute();
        assertNotNull(command.getAddedComment());
        assertEquals("test", command.getAddedComment().getContent());
    }

    @Test
    @DisplayName("constructor should reject null parent")
    void testNullParentThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddCommentCommand(null, "content"));
    }

    @Test
    @DisplayName("constructor should reject null content")
    void testNullContentThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddCommentCommand(parentElement, null));
    }
}
