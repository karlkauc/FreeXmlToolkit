package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;

/**
 * UI-free collection of the inline facets of a node (element / simple type with
 * an inline restriction) for the inspector's Type &amp; Facets table.
 * <p>
 * Resolves only inline facets; facets inherited via a named type reference are a
 * follow-up (matches the V2 "one level of SimpleType resolution" limitation).
 */
public final class SchemaFacets {

    private SchemaFacets() {
    }

    /** @return the facets declared inline under the node's simple-type restriction. */
    public static List<XsdFacet> collect(XsdNode node) {
        List<XsdFacet> facets = new ArrayList<>();
        if (node != null) {
            gather(node, facets);
        }
        return facets;
    }

    /** @return the inline {@link XsdRestriction} of the node's simple type, or {@code null}. */
    public static XsdRestriction findRestriction(XsdNode node) {
        return node == null ? null : findRestriction0(node);
    }

    private static XsdRestriction findRestriction0(XsdNode node) {
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                return restriction;
            }
            switch (child.getNodeType()) {
                case SIMPLE_TYPE, SIMPLE_CONTENT, COMPLEX_TYPE, COMPLEX_CONTENT, EXTENSION, LIST, UNION -> {
                    XsdRestriction found = findRestriction0(child);
                    if (found != null) {
                        return found;
                    }
                }
                default -> {
                    // do not descend into nested elements/attributes
                }
            }
        }
        return null;
    }

    private static void gather(XsdNode node, List<XsdFacet> out) {
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdFacet facet) {
                out.add(facet);
                continue;
            }
            switch (child.getNodeType()) {
                case SIMPLE_TYPE, RESTRICTION, SIMPLE_CONTENT, COMPLEX_TYPE, COMPLEX_CONTENT,
                     EXTENSION, LIST, UNION -> gather(child, out);
                default -> {
                    // do not descend into nested elements/attributes
                }
            }
        }
    }
}
