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

    /**
     * Resolves the facets a node inherits via a {@code type="..."} reference to a named simple type
     * defined in the schema — the "one level of SimpleType resolution" that inline {@link #collect}
     * does not cover. Built-in types ({@code xs:*}) and nodes without a type reference yield none.
     *
     * @param node       the element/attribute whose referenced type is resolved
     * @param schemaRoot the schema root holding the global named types
     * @return the referenced named simple type's facets, or an empty list
     */
    public static List<XsdFacet> resolveReferencedTypeFacets(XsdNode node, XsdNode schemaRoot) {
        if (node == null || schemaRoot == null) {
            return new ArrayList<>();
        }
        String typeName = referencedTypeName(node);
        if (typeName == null) {
            return new ArrayList<>();
        }
        for (XsdNode child : schemaRoot.getChildren()) {
            if (child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.SIMPLE_TYPE
                    && typeName.equals(child.getName())) {
                return collect(child);
            }
        }
        return new ArrayList<>();
    }

    /** @return the local name of the node's {@code type} reference, or {@code null} for none/builtins. */
    private static String referencedTypeName(XsdNode node) {
        String type = null;
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            type = element.getType();
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
            type = attribute.getType();
        }
        if (type == null || type.isBlank()) {
            return null;
        }
        int colon = type.indexOf(':');
        String prefix = colon >= 0 ? type.substring(0, colon) : "";
        String local = colon >= 0 ? type.substring(colon + 1) : type;
        // XSD built-in types carry no schema-defined facets to inherit.
        if (prefix.equals("xs") || prefix.equals("xsd")) {
            return null;
        }
        return local;
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
