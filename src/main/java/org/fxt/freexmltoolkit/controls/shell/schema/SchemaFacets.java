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
        List<XsdFacet> out = new ArrayList<>();
        addNamedTypeFacets(referencedTypeName(node), schemaRoot, out, new java.util.HashSet<>());
        return out;
    }

    /**
     * Resolves the facets contributed by a node's {@code xs:list} item type or {@code xs:union}
     * member types when those reference named simple types (the V2 list/union gap). Inline item/
     * member types are already covered by {@link #collect}.
     */
    public static List<XsdFacet> resolveListUnionFacets(XsdNode node, XsdNode schemaRoot) {
        if (node == null || schemaRoot == null) {
            return new ArrayList<>();
        }
        List<XsdFacet> out = new ArrayList<>();
        collectListUnion(node, schemaRoot, out, new java.util.HashSet<>());
        return out;
    }

    /** Adds the effective facets of the named simple type (its inline facets, else its list/union). */
    private static void addNamedTypeFacets(String typeName, XsdNode schemaRoot,
            List<XsdFacet> out, java.util.Set<String> visited) {
        if (typeName == null || !visited.add(typeName)) {
            return; // unknown/builtin, or already visited (circular reference guard)
        }
        XsdNode named = null;
        for (XsdNode child : schemaRoot.getChildren()) {
            if (child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.SIMPLE_TYPE
                    && typeName.equals(child.getName())) {
                named = child;
                break;
            }
        }
        if (named == null) {
            return;
        }
        List<XsdFacet> inline = collect(named);
        if (!inline.isEmpty()) {
            out.addAll(inline);
        } else {
            collectListUnion(named, schemaRoot, out, visited);
        }
    }

    /** Descends simple-type constructs and resolves named list item / union member type facets. */
    private static void collectListUnion(XsdNode node, XsdNode schemaRoot,
            List<XsdFacet> out, java.util.Set<String> visited) {
        for (XsdNode child : node.getChildren()) {
            switch (child.getNodeType()) {
                case SIMPLE_TYPE, RESTRICTION -> collectListUnion(child, schemaRoot, out, visited);
                case LIST -> {
                    if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdList list) {
                        addNamedTypeFacets(localTypeName(list.getItemType()), schemaRoot, out, visited);
                    }
                }
                case UNION -> {
                    if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdUnion union) {
                        for (String member : union.getMemberTypes()) {
                            addNamedTypeFacets(localTypeName(member), schemaRoot, out, visited);
                        }
                    }
                }
                default -> {
                    // not a simple-type construct
                }
            }
        }
    }

    /** @return the local name of the node's {@code type} reference, or {@code null} for none/builtins. */
    private static String referencedTypeName(XsdNode node) {
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            return localTypeName(element.getType());
        }
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
            return localTypeName(attribute.getType());
        }
        return null;
    }

    /** @return the local part of a (possibly prefixed) type name, or {@code null} for blank/builtins. */
    private static String localTypeName(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        int colon = type.indexOf(':');
        String prefix = colon >= 0 ? type.substring(0, colon) : "";
        // XSD built-in types carry no schema-defined facets to inherit.
        if (prefix.equals("xs") || prefix.equals("xsd")) {
            return null;
        }
        return colon >= 0 ? type.substring(colon + 1) : type;
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
