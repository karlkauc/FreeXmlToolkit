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
        List<XsdNode> types = new ArrayList<>();
        collectInto(schema, types);
        if (schema instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema xsdSchema) {
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdSchema imported
                    : xsdSchema.getImportedSchemas().values()) {
                collectInto(imported, types);
            }
        }
        return types;
    }

    private static void collectInto(XsdNode schema, List<XsdNode> out) {
        if (schema == null) {
            return;
        }
        for (XsdNode child : schema.getChildren()) {
            boolean isType = child.getNodeType() == XsdNodeType.SIMPLE_TYPE
                    || child.getNodeType() == XsdNodeType.COMPLEX_TYPE;
            if (isType && child.getName() != null && !child.getName().isBlank()) {
                out.add(child);
            }
        }
    }
}
