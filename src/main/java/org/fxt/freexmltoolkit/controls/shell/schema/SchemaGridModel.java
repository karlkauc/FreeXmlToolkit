package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * UI-free logic for the embedded grid in the Graphic view: which nodes repeat
 * (and thus render as a grid instead of N identical cards) and which fields
 * become the grid's columns. Kept separate from the renderer so it is testable.
 */
public final class SchemaGridModel {

    private SchemaGridModel() {
    }

    /** @return {@code true} if the element repeats (maxOccurs &gt; 1 or unbounded). */
    public static boolean isRepeating(XsdNode node) {
        if (node == null || node.getNodeType() != XsdNodeType.ELEMENT) {
            return false;
        }
        int max = node.getMaxOccurs();
        return max < 0 || max > 1;
    }

    /**
     * @return the direct field nodes (child elements/attributes of the content
     * model) that form the grid columns for a repeating element; empty for a
     * simple element.
     */
    public static List<XsdNode> gridColumns(XsdNode element) {
        List<XsdNode> columns = new ArrayList<>();
        if (element != null) {
            collectFields(element, columns);
        }
        return columns;
    }

    private static void collectFields(XsdNode node, List<XsdNode> out) {
        for (XsdNode child : node.getChildren()) {
            switch (child.getNodeType()) {
                case ELEMENT, ATTRIBUTE -> out.add(child); // a field column; do not recurse into it
                case COMPLEX_TYPE, COMPLEX_CONTENT, SIMPLE_CONTENT, EXTENSION,
                     SEQUENCE, CHOICE, ALL, GROUP, ATTRIBUTE_GROUP -> collectFields(child, out);
                default -> {
                    // ignore annotations, facets, etc.
                }
            }
        }
    }
}
