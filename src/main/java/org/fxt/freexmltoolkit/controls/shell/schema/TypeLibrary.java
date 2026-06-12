package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * UI-free collection of a schema's top-level named simple/complex types for the
 * Type Library panel.
 */
public final class TypeLibrary {

    private TypeLibrary() {
    }

    /**
     * @return the top-level named simple and complex types of the schema, including those brought
     * in via {@code xs:include} (inlined as children) and {@code xs:import} (imported schemas).
     */
    public static List<XsdNode> collectNamedTypes(XsdNode schema) {
        return collect(schema, XsdNodeType.SIMPLE_TYPE, XsdNodeType.COMPLEX_TYPE);
    }

    /**
     * @return the schema's top-level (global) element declarations, including those from
     * {@code xs:include} (inlined as children) and {@code xs:import} (imported schemas).
     */
    public static List<XsdNode> collectGlobalElements(XsdNode schema) {
        return collect(schema, XsdNodeType.ELEMENT);
    }

    private static List<XsdNode> collect(XsdNode schema, XsdNodeType... nodeTypes) {
        List<XsdNode> result = new ArrayList<>();
        collectInto(schema, result, nodeTypes);
        if (schema instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema xsdSchema) {
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdSchema imported
                    : xsdSchema.getImportedSchemas().values()) {
                collectInto(imported, result, nodeTypes);
            }
        }
        return result;
    }

    private static void collectInto(XsdNode schema, List<XsdNode> out, XsdNodeType... nodeTypes) {
        if (schema == null) {
            return;
        }
        for (XsdNode child : schema.getChildren()) {
            for (XsdNodeType nodeType : nodeTypes) {
                if (child.getNodeType() == nodeType
                        && child.getName() != null && !child.getName().isBlank()) {
                    out.add(child);
                    break;
                }
            }
        }
    }
}
