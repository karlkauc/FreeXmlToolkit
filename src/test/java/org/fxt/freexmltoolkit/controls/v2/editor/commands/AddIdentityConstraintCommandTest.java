package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdIdentityConstraint;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKeyRef;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AddIdentityConstraintCommand} (key/keyref/unique creation).
 */
class AddIdentityConstraintCommandTest {

    private XsdEditorContext context;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        context = new XsdEditorContext(schema);
        element = new XsdElement("root");
        schema.addChild(element);
    }

    @Test
    void addKey_buildsSelectorAndFieldAndUndoes() {
        AddIdentityConstraintCommand cmd = new AddIdentityConstraintCommand(context, element,
                AddIdentityConstraintCommand.Kind.KEY, "idKey", ".//item", List.of("@id"), null);
        assertTrue(cmd.execute());

        XsdIdentityConstraint c = cmd.getCreated();
        assertEquals("idKey", c.getName());
        assertEquals(".//item", c.getSelector().getXpath());
        assertEquals(1, c.getFields().size());
        assertEquals("@id", c.getFields().get(0).getXpath());
        assertTrue(element.getChildren().contains(c));

        assertTrue(cmd.undo());
        assertFalse(element.getChildren().contains(c));
    }

    @Test
    void addKeyRef_setsReferAttribute() {
        AddIdentityConstraintCommand cmd = new AddIdentityConstraintCommand(context, element,
                AddIdentityConstraintCommand.Kind.KEYREF, "ref1", ".//ref", List.of("@idref"), "idKey");
        assertTrue(cmd.execute());
        XsdIdentityConstraint c = cmd.getCreated();
        XsdKeyRef keyRef = assertInstanceOf(XsdKeyRef.class, c);
        assertEquals("idKey", keyRef.getRefer());
    }

    @Test
    void rejectsMissingNameOrSelectorOrField() {
        assertThrows(IllegalArgumentException.class, () -> new AddIdentityConstraintCommand(context, element,
                AddIdentityConstraintCommand.Kind.KEY, "  ", ".//x", List.of("@a"), null));
        assertThrows(IllegalArgumentException.class, () -> new AddIdentityConstraintCommand(context, element,
                AddIdentityConstraintCommand.Kind.KEY, "n", "", List.of("@a"), null));
        assertThrows(IllegalArgumentException.class, () -> new AddIdentityConstraintCommand(context, element,
                AddIdentityConstraintCommand.Kind.KEY, "n", ".//x", List.of("  "), null));
    }
}
