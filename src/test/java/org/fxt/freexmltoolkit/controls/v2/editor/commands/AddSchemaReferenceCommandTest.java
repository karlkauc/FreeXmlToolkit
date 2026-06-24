package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdImport;
import org.fxt.freexmltoolkit.controls.v2.model.XsdInclude;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AddSchemaReferenceCommand} (xs:import / xs:include add + undo + ordering).
 */
class AddSchemaReferenceCommandTest {

    private XsdEditorContext context;
    private XsdSchema schema;
    private XsdElement typeDef;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        context = new XsdEditorContext(schema);
        // A pre-existing top-level component: imports/includes must be inserted before it.
        typeDef = new XsdElement("Root");
        schema.addChild(typeDef);
    }

    @Test
    void addImport_insertedBeforeTypeDefs_andUndoes() {
        AddSchemaReferenceCommand cmd = new AddSchemaReferenceCommand(context, schema,
                AddSchemaReferenceCommand.Kind.IMPORT, "http://ns/x", "x.xsd");
        assertTrue(cmd.execute());

        XsdImport imp = assertInstanceOf(XsdImport.class, schema.getChildren().get(0));
        assertEquals("http://ns/x", imp.getNamespace());
        assertEquals("x.xsd", imp.getSchemaLocation());
        assertTrue(schema.getChildren().indexOf(imp) < schema.getChildren().indexOf(typeDef));

        assertTrue(cmd.undo());
        assertFalse(schema.getChildren().contains(imp));
    }

    @Test
    void addInclude_setsSchemaLocation() {
        AddSchemaReferenceCommand cmd = new AddSchemaReferenceCommand(context, schema,
                AddSchemaReferenceCommand.Kind.INCLUDE, null, "common.xsd");
        assertTrue(cmd.execute());
        XsdInclude inc = assertInstanceOf(XsdInclude.class, schema.getChildren().get(0));
        assertEquals("common.xsd", inc.getSchemaLocation());
    }

    @Test
    void secondReference_insertedAfterFirstHeader() {
        new AddSchemaReferenceCommand(context, schema,
                AddSchemaReferenceCommand.Kind.IMPORT, "ns1", "a.xsd").execute();
        new AddSchemaReferenceCommand(context, schema,
                AddSchemaReferenceCommand.Kind.INCLUDE, null, "b.xsd").execute();

        assertInstanceOf(XsdImport.class, schema.getChildren().get(0));
        assertInstanceOf(XsdInclude.class, schema.getChildren().get(1));
        // both still before the type definition
        assertTrue(schema.getChildren().indexOf(typeDef) >= 2);
    }

    @Test
    void rejectsEmptySchemaLocationAndNonSchema() {
        assertThrows(IllegalArgumentException.class, () -> new AddSchemaReferenceCommand(context, schema,
                AddSchemaReferenceCommand.Kind.INCLUDE, null, "  "));
        assertThrows(IllegalArgumentException.class, () -> new AddSchemaReferenceCommand(context, typeDef,
                AddSchemaReferenceCommand.Kind.IMPORT, "ns", "x.xsd"));
    }
}
