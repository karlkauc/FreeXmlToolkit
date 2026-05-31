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

    /** @return the top-level named simple and complex types of the schema. */
    public static List<XsdNode> collectNamedTypes(XsdNode schema) {
        List<XsdNode> types = new ArrayList<>();
        if (schema != null) {
            for (XsdNode child : schema.getChildren()) {
                boolean isType = child.getNodeType() == XsdNodeType.SIMPLE_TYPE
                        || child.getNodeType() == XsdNodeType.COMPLEX_TYPE;
                if (isType && child.getName() != null && !child.getName().isBlank()) {
                    types.add(child);
                }
            }
        }
        return types;
    }
}
