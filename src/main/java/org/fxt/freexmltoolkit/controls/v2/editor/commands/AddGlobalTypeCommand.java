package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Declares a new top-level type: a complex type with an empty sequence (ready for child
 * elements) or a simple type restricting {@code xs:string}. It goes after the last top-level type
 * of the same kind, else at the end of the schema. Undo removes it again.
 */
public class AddGlobalTypeCommand implements XsdCommand {

    /** The kind of type to declare. */
    public enum Kind { COMPLEX, SIMPLE }

    private final XsdSchema schema;
    private final Kind kind;
    private final String name;
    private XsdNode addedType;

    public AddGlobalTypeCommand(XsdSchema schema, Kind kind, String name) {
        if (schema == null || kind == null) {
            throw new IllegalArgumentException("Schema and kind are required");
        }
        this.schema = schema;
        this.kind = kind;
        this.name = name == null ? "" : name.trim();
    }

    @Override
    public boolean execute() {
        if (!XsdEditRules.addGlobalType(schema, name).isAllowed()) {
            return false;
        }
        if (kind == Kind.COMPLEX) {
            XsdComplexType complexType = new XsdComplexType(name);
            complexType.addChild(new XsdSequence());
            addedType = complexType;
        } else {
            XsdSimpleType simpleType = new XsdSimpleType(name);
            simpleType.addChild(new XsdRestriction(XsdEditRules.xsdPrefix(schema) + ":string"));
            addedType = simpleType;
        }
        schema.addChild(insertIndex(), addedType);
        return true;
    }

    private int insertIndex() {
        Class<?> sameKind = kind == Kind.COMPLEX ? XsdComplexType.class : XsdSimpleType.class;
        List<XsdNode> children = schema.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (sameKind.isInstance(children.get(i))) {
                return i + 1;
            }
        }
        return children.size();
    }

    @Override
    public boolean undo() {
        return addedType != null && schema.removeChild(addedType);
    }

    @Override
    public String getDescription() {
        return "Add " + (kind == Kind.COMPLEX ? "complex" : "simple") + " type '" + name + "'";
    }

    @Override
    public boolean canUndo() {
        return addedType != null;
    }

    /** @return the type created by the last {@link #execute()}, or {@code null} */
    public XsdNode getAddedType() {
        return addedType;
    }
}
