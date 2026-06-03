package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * UI-free projection of a selected {@link XsdNode} into the inspector's sections
 * (Node &amp; XPath, Type &amp; Facets, Cardinality &amp; Use, Documentation).
 * Used by the Tree/Graphic views, where the real model — not a caret heuristic —
 * drives the inspector. (Distinct from the domain {@code XsdNodeInfo}.)
 */
public record SelectedNodeInfo(String kind, String name, String xpath, int depth,
                               String type, String cardinality, String use, String documentation) {

    private static final String NONE = "—";

    /** Projects the node; a {@code null} node yields an empty projection. */
    public static SelectedNodeInfo of(XsdNode node) {
        if (node == null) {
            return new SelectedNodeInfo("", "", "/", 0, NONE, NONE, NONE, NONE);
        }
        String[] path = instancePath(node);
        // Show the XSD schema XPath (e.g. /xs:schema/xs:complexType[@name='DataSupplierType']) — it
        // is well-defined for every node type, including named types and compositors that have no
        // instance path (which previously collapsed to "/"). Depth stays the named-ancestor depth.
        String xpath = node.getXPath();
        return new SelectedNodeInfo(
                kind(node.getNodeType()),
                node.getName() != null ? node.getName() : "",
                (xpath == null || xpath.isBlank()) ? "/" : xpath,
                Integer.parseInt(path[1]),
                type(node),
                cardinality(node),
                use(node),
                value(node.getDocumentation()));
    }

    private static String kind(XsdNodeType type) {
        String lower = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String type(XsdNode node) {
        if (node instanceof XsdElement element) {
            return value(element.getType());
        }
        return NONE;
    }

    private static String cardinality(XsdNode node) {
        int min = node.getMinOccurs();
        int max = node.getMaxOccurs();
        String maxText = (max < 0 || max >= Integer.MAX_VALUE) ? "*" : Integer.toString(max);
        return min + ".." + maxText;
    }

    private static String use(XsdNode node) {
        if (node instanceof XsdAttribute attribute) {
            return value(attribute.getUse());
        }
        return NONE;
    }

    /** @return {@code [xpath, depth]} built from the named element/attribute ancestors. */
    private static String[] instancePath(XsdNode node) {
        Deque<String> parts = new ArrayDeque<>();
        for (XsdNode n = node; n != null; n = n.getParent()) {
            String name = n.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (n.getNodeType() == XsdNodeType.ELEMENT) {
                parts.push(name);
            } else if (n.getNodeType() == XsdNodeType.ATTRIBUTE) {
                parts.push("@" + name);
            }
        }
        String xpath = parts.isEmpty() ? "/" : "/" + String.join("/", parts);
        return new String[]{xpath, Integer.toString(parts.size())};
    }

    private static String value(String raw) {
        return (raw == null || raw.isBlank()) ? NONE : raw;
    }
}
