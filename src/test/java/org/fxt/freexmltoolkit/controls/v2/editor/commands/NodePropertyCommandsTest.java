package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAny;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the new Inspector property commands: default/ref/block/final/mixed, wildcard and
 * schema-level edits, including execute/undo round-trips and merge behaviour of
 * {@link AbstractNodePropertyCommand}.
 */
class NodePropertyCommandsTest {

    private XsdEditorContext context;
    private XsdSchema schema;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        context = new XsdEditorContext(schema);
    }

    @Test
    void changeDefaultValue_executeAndUndo() {
        XsdElement el = new XsdElement("e");
        schema.addChild(el);
        ChangeDefaultValueCommand cmd = new ChangeDefaultValueCommand(context, el, "  42  ");
        assertTrue(cmd.execute());
        assertEquals("42", el.getDefaultValue());
        assertTrue(cmd.undo());
        assertNull(el.getDefaultValue());
    }

    @Test
    void changeDefaultValue_onAttribute() {
        XsdAttribute at = new XsdAttribute("a");
        ChangeDefaultValueCommand cmd = new ChangeDefaultValueCommand(context, at, "x");
        assertTrue(cmd.execute());
        assertEquals("x", at.getDefaultValue());
    }

    @Test
    void changeDefaultValue_rejectsUnsupportedNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeDefaultValueCommand(context, new XsdComplexType("ct"), "x"));
    }

    @Test
    void changeBlock_onElementAndComplexType() {
        XsdElement el = new XsdElement("e");
        ChangeBlockCommand c1 = new ChangeBlockCommand(context, el, "extension");
        assertTrue(c1.execute());
        assertEquals("extension", el.getBlock());

        XsdComplexType ct = new XsdComplexType("ct");
        ChangeBlockCommand c2 = new ChangeBlockCommand(context, ct, "#all");
        assertTrue(c2.execute());
        assertEquals("#all", ct.getBlock());
        assertTrue(c2.undo());
        assertNull(ct.getBlock());
    }

    @Test
    void changeFinal_onComplexType() {
        XsdComplexType ct = new XsdComplexType("ct");
        ChangeFinalCommand cmd = new ChangeFinalCommand(context, ct, "restriction");
        assertTrue(cmd.execute());
        assertEquals("restriction", ct.getFinal());
        assertTrue(cmd.undo());
        assertNull(ct.getFinal());
    }

    @Test
    void changeMixed_togglesAndUndoes() {
        XsdComplexType ct = new XsdComplexType("ct");
        assertFalse(ct.isMixed());
        ChangeMixedCommand cmd = new ChangeMixedCommand(context, ct, true);
        assertTrue(cmd.execute());
        assertTrue(ct.isMixed());
        assertTrue(cmd.undo());
        assertFalse(ct.isMixed());
    }

    @Test
    void changeRef_onElement() {
        XsdElement el = new XsdElement("e");
        ChangeRefCommand cmd = new ChangeRefCommand(context, el, "ns:Global");
        assertTrue(cmd.execute());
        assertEquals("ns:Global", el.getRef());
        assertTrue(cmd.undo());
        assertNull(el.getRef());
    }

    @Test
    void changeWildcard_executeAndUndo() {
        XsdAny any = new XsdAny();
        any.setNamespace("##any");
        any.setProcessContents(XsdAny.ProcessContents.STRICT);
        ChangeWildcardCommand cmd = new ChangeWildcardCommand(context, any, "##other", XsdAny.ProcessContents.LAX);
        assertTrue(cmd.execute());
        assertEquals("##other", any.getNamespace());
        assertEquals(XsdAny.ProcessContents.LAX, any.getProcessContents());
        assertTrue(cmd.undo());
        assertEquals("##any", any.getNamespace());
        assertEquals(XsdAny.ProcessContents.STRICT, any.getProcessContents());
    }

    @Test
    void changeSchemaProperty_targetNamespaceAndVersion() {
        ChangeSchemaPropertyCommand ns = new ChangeSchemaPropertyCommand(
                context, schema, ChangeSchemaPropertyCommand.Property.TARGET_NAMESPACE, "http://x");
        assertTrue(ns.execute());
        assertEquals("http://x", schema.getTargetNamespace());

        ChangeSchemaPropertyCommand ver = new ChangeSchemaPropertyCommand(
                context, schema, ChangeSchemaPropertyCommand.Property.VERSION, "1.2");
        assertTrue(ver.execute());
        assertEquals("1.2", schema.getVersion());
        assertTrue(ver.undo());
        assertNull(schema.getVersion());
    }

    @Test
    void schemaProperty_doesNotMergeAcrossDifferentProperties() {
        ChangeSchemaPropertyCommand ns = new ChangeSchemaPropertyCommand(
                context, schema, ChangeSchemaPropertyCommand.Property.TARGET_NAMESPACE, "http://x");
        ChangeSchemaPropertyCommand ver = new ChangeSchemaPropertyCommand(
                context, schema, ChangeSchemaPropertyCommand.Property.VERSION, "1.0");
        assertFalse(ns.canMergeWith(ver), "Different schema properties must not merge");
    }

    @Test
    void propertyCommand_mergeKeepsOriginalUndoTarget() {
        XsdElement el = new XsdElement("e");
        // "old(null) -> A"
        ChangeDefaultValueCommand first = new ChangeDefaultValueCommand(context, el, "A");
        assertTrue(first.execute());
        // "A -> B"
        ChangeDefaultValueCommand second = new ChangeDefaultValueCommand(context, el, "B");
        assertTrue(second.execute());
        assertTrue(first.canMergeWith(second));
        XsdCommand merged = first.mergeWith(second);
        // Undoing the merged command must restore the ORIGINAL value (null), not "A".
        assertTrue(merged.undo());
        assertNull(el.getDefaultValue());
    }
}
