package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.util.List;

/**
 * Builds a positional XPath for a node of the XML-instance model.
 *
 * <p>The path uses a sibling position predicate ({@code [n]}) only where it is
 * needed to disambiguate — i.e. when more than one same-name sibling exists at a
 * level — so an unambiguous path stays readable (e.g. {@code /root/items/item[2]/name}).
 *
 * <p>This is the single source of truth for "Copy XPath" across the node context
 * menu, the canvas and the Properties inspector; see callers in
 * {@code XmlGridContextMenu} and {@code InspectorPanel}.
 */
public final class XmlNodeXPath {

    private XmlNodeXPath() {
    }

    /**
     * @param node any model node
     * @return the positional XPath of {@code node}, {@code "/"} for the document
     *         root or {@code null}, walking element ancestors only
     */
    public static String positional(XmlNode node) {
        if (node == null || node instanceof XmlDocument) {
            return "/";
        }

        StringBuilder xpath = new StringBuilder();
        XmlNode current = node;

        while (current != null && !(current instanceof XmlDocument)) {
            if (current instanceof XmlElement element) {
                xpath.insert(0, step(element));
            }
            current = current.getParent();
        }

        return xpath.length() > 0 ? xpath.toString() : "/";
    }

    /** A single {@code /name} or {@code /name[pos]} step for {@code element}. */
    private static String step(XmlElement element) {
        String name = element.getName();
        XmlNode parent = element.getParent();
        if (parent == null) {
            return "/" + name;
        }

        List<XmlNode> siblings;
        if (parent instanceof XmlElement parentElement) {
            siblings = parentElement.getChildren();
        } else if (parent instanceof XmlDocument parentDocument) {
            siblings = parentDocument.getChildren();
        } else {
            return "/" + name;
        }

        int position = 1;
        int sameNameCount = 0;
        for (XmlNode sibling : siblings) {
            if (sibling instanceof XmlElement siblingElement && siblingElement.getName().equals(name)) {
                sameNameCount++;
                if (sibling == element) {
                    position = sameNameCount;
                }
            }
        }

        // Only disambiguate with [position] when there are several same-name siblings.
        return sameNameCount > 1 ? "/" + name + "[" + position + "]" : "/" + name;
    }
}
