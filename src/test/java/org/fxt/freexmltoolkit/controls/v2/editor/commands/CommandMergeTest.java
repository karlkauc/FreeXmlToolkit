package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that consecutive, mergeable commands collapse into one undo step through the
 * {@link org.fxt.freexmltoolkit.controls.v2.editor.commands.CommandManager} without throwing
 * "Merge not supported", and that undoing the merged command restores the ORIGINAL state
 * (not the intermediate one).
 */
class CommandMergeTest {

    private XsdEditorContext context;
    private XsdSchema schema;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        context = new XsdEditorContext(schema);
        element = new XsdElement("e");
        schema.addChild(element);
    }

    private boolean run(XsdCommand cmd) {
        return context.getCommandManager().executeCommand(cmd);
    }

    @Test
    void appinfoEdits_mergeAndUndoToOriginal() {
        XsdAppInfo a1 = new XsdAppInfo();
        a1.setSince("4.0.0");
        XsdAppInfo a2 = new XsdAppInfo();
        a2.setSince("4.0.0");
        a2.setDeprecated("gone");

        assertDoesNotThrow(() -> {
            run(new ChangeAppinfoCommand(context, element, a1));
            run(new ChangeAppinfoCommand(context, element, a2)); // merges with the first
        });
        assertTrue(element.getAppinfo().isDeprecated());

        assertTrue(context.getCommandManager().undo());
        assertNull(element.getAppinfo(), "one undo must restore the original (no appinfo)");
    }

    @Test
    void formEdits_mergeAndUndoToOriginal() {
        assertDoesNotThrow(() -> {
            run(new ChangeFormCommand(context, element, "qualified"));
            run(new ChangeFormCommand(context, element, "unqualified")); // merges
        });
        assertEquals("unqualified", element.getForm());

        assertTrue(context.getCommandManager().undo());
        assertEquals("", normalize(element.getForm()), "one undo must restore the original form");

        assertTrue(context.getCommandManager().redo());
        assertEquals("unqualified", element.getForm());
    }

    private static String normalize(String s) {
        return s == null ? "" : s;
    }
}
