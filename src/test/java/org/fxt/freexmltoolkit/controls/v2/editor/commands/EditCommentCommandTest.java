package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EditCommentCommand.
 *
 * @since 2.0
 */
class EditCommentCommandTest {

    private XsdComment comment;

    @BeforeEach
    void setUp() {
        comment = new XsdComment("original content");
    }

    @Test
    @DisplayName("execute() should change comment content")
    void testExecuteChangesContent() {
        EditCommentCommand command = new EditCommentCommand(comment, "new content");

        boolean result = command.execute();

        assertTrue(result);
        assertEquals("new content", comment.getContent());
    }

    @Test
    @DisplayName("undo() should restore original content")
    void testUndoRestoresContent() {
        EditCommentCommand command = new EditCommentCommand(comment, "new content");
        command.execute();

        boolean result = command.undo();

        assertTrue(result);
        assertEquals("original content", comment.getContent());
    }

    @Test
    @DisplayName("canMergeWith() should return true for same comment")
    void testCanMergeWithSameComment() {
        EditCommentCommand cmd1 = new EditCommentCommand(comment, "first edit");
        EditCommentCommand cmd2 = new EditCommentCommand(comment, "second edit");

        assertTrue(cmd1.canMergeWith(cmd2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different comments")
    void testCanMergeWithDifferentComment() {
        XsdComment otherComment = new XsdComment("other");
        EditCommentCommand cmd1 = new EditCommentCommand(comment, "edit1");
        EditCommentCommand cmd2 = new EditCommentCommand(otherComment, "edit2");

        assertFalse(cmd1.canMergeWith(cmd2));
    }

    @Test
    @DisplayName("mergeWith() should produce command with latest content")
    void testMergeWith() {
        EditCommentCommand cmd1 = new EditCommentCommand(comment, "first edit");
        EditCommentCommand cmd2 = new EditCommentCommand(comment, "second edit");

        XsdCommand merged = cmd1.mergeWith(cmd2);
        assertInstanceOf(EditCommentCommand.class, merged);

        merged.execute();
        assertEquals("second edit", comment.getContent());
    }

    @Test
    @DisplayName("getDescription() should return meaningful text")
    void testGetDescription() {
        EditCommentCommand command = new EditCommentCommand(comment, "new");
        assertNotNull(command.getDescription());
        assertFalse(command.getDescription().isEmpty());
    }

    @Test
    @DisplayName("canUndo() should always return true")
    void testCanUndo() {
        EditCommentCommand command = new EditCommentCommand(comment, "new");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("constructor should reject null comment")
    void testNullCommentThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new EditCommentCommand(null, "content"));
    }

    @Test
    @DisplayName("constructor should reject null content")
    void testNullContentThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new EditCommentCommand(comment, null));
    }

    @Test
    @DisplayName("getters should return correct values")
    void testGetters() {
        EditCommentCommand command = new EditCommentCommand(comment, "new content");
        assertEquals(comment, command.getComment());
        assertEquals("original content", command.getOldContent());
        assertEquals("new content", command.getNewContent());
    }
}
