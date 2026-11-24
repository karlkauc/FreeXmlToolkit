package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

        // Stops once the position is known and a second same-name sibling was seen: the first of
        // 680,000 siblings no longer counts all of them
        int position = 0;
        int sameNameCount = 0;
        for (XmlNode sibling : siblings) {
            if (sibling instanceof XmlElement siblingElement && siblingElement.getName().equals(name)) {
                sameNameCount++;
                if (sibling == element) {
                    position = sameNameCount;
                }
                if (position > 0 && sameNameCount > 1) {
                    break;
                }
            }
        }
        if (position == 0) {
            position = 1;
        }

        return sameNameCount > 1 ? "/" + name + "[" + position + "]" : "/" + name;
    }

    /**
     * {@link #positional} for many nodes of a model that does not change meanwhile: the steps of
     * all children of a parent come from one pass over them, and ancestor paths are reused. Without
     * it, the paths of n same-name siblings cost n²/2 sibling visits (a grid of 200,000 records
     * spent minutes on its row keys). Gives exactly what {@link #positional} gives; drop it after a
     * change to the model.
     */
    public static final class Cache {
        private final Map<XmlNode, String> prefixes = new IdentityHashMap<>();
        private final Map<XmlNode, Map<XmlElement, String>> stepsByParent = new IdentityHashMap<>();

        /** @see XmlNodeXPath#positional(XmlNode) */
        public String positional(XmlNode node) {
            String prefix = prefix(node);
            return prefix.isEmpty() ? "/" : prefix;
        }

        /** The element steps from the root down to {@code node}, "" for none (iterative: no deep recursion). */
        private String prefix(XmlNode node) {
            List<XmlNode> chain = new ArrayList<>();
            String known = "";
            for (XmlNode current = node; current != null && !(current instanceof XmlDocument);
                 current = current.getParent()) {
                String cached = prefixes.get(current);
                if (cached != null) {
                    known = cached;
                    break;
                }
                chain.add(current);
            }
            for (int i = chain.size() - 1; i >= 0; i--) {
                XmlNode current = chain.get(i);
                if (current instanceof XmlElement element) {
                    known = known + stepOf(element);
                }
                prefixes.put(current, known);
            }
            return known;
        }

        private String stepOf(XmlElement element) {
            XmlNode parent = element.getParent();
            List<XmlNode> siblings = parent instanceof XmlElement parentElement ? parentElement.getChildren()
                    : parent instanceof XmlDocument parentDocument ? parentDocument.getChildren() : null;
            if (siblings == null) {
                return "/" + element.getName();
            }
            String step = stepsByParent.computeIfAbsent(parent, p -> steps(siblings)).get(element);
            return step != null ? step : step(element); // not among its parent's children (being removed)
        }

        private static Map<XmlElement, String> steps(List<XmlNode> siblings) {
            Map<String, Integer> totals = new HashMap<>();
            for (XmlNode sibling : siblings) {
                if (sibling instanceof XmlElement element) {
                    totals.merge(element.getName(), 1, Integer::sum);
                }
            }
            Map<String, Integer> seen = new HashMap<>();
            Map<XmlElement, String> steps = new IdentityHashMap<>();
            for (XmlNode sibling : siblings) {
                if (sibling instanceof XmlElement element) {
                    String name = element.getName();
                    int position = seen.merge(name, 1, Integer::sum);
                    steps.put(element, totals.get(name) > 1 ? "/" + name + "[" + position + "]" : "/" + name);
                }
            }
            return steps;
        }
    }
}
