package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;

/**
 * Where a new child element belongs among its parent's children, so that adding a missing element
 * (a value typed into an empty grid cell, a pasted block, "Add Child Element") keeps the order the
 * schema prescribes instead of appending it after everything else.
 */
public final class ChildOrder {

    private ChildOrder() {
    }

    /**
     * The index in {@code parent.getChildren()} at which a new child element {@code name} belongs.
     *
     * <ul>
     *   <li>children of the same name exist → right after the last of them;</li>
     *   <li>otherwise before the first element child that comes later in {@code order};
     *       children whose name is not in {@code order} are not considered;</li>
     *   <li>{@code name} not in {@code order}, or nothing later present → {@code -1} (append).</li>
     * </ul>
     *
     * @param parent the element receiving the child
     * @param name   the new child's name (a prefix is ignored)
     * @param order  child names in the expected order (local names); may be empty
     * @return the insertion index, or {@code -1} to append
     */
    public static int insertionIndex(XmlElement parent, String name, List<String> order) {
        if (parent == null || name == null) {
            return -1;
        }
        String local = localName(name);
        List<XmlNode> children = parent.getChildren();
        int lastSame = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof XmlElement child && localName(child.getName()).equals(local)) {
                lastSame = i;
            }
        }
        if (lastSame >= 0) {
            return lastSame + 1;
        }
        int rank = order == null ? -1 : order.indexOf(local);
        if (rank < 0) {
            return -1;
        }
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof XmlElement child) {
                int childRank = order.indexOf(localName(child.getName()));
                if (childRank > rank) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The expected child order of an element: the schema's declaration order when a schema is
     * bound, followed by the {@code fallback} names it does not declare (for example a table's
     * columns, which follow the document order of all rows).
     *
     * @param schema     the schema provider (may be {@code null})
     * @param parentPath the parent's schema-lookup XPath ({@code /root/child}, no positions)
     * @param fallback   names used when the schema has none or does not know them (may be {@code null})
     * @return local names in the expected order (never {@code null})
     */
    public static List<String> orderFor(XmlSchemaProvider schema, String parentPath, List<String> fallback) {
        List<String> declared = List.of();
        if (schema != null && schema.hasSchema() && parentPath != null) {
            try {
                List<String> fromSchema = schema.getValidChildElements(parentPath);
                if (fromSchema != null) {
                    declared = fromSchema;
                }
            } catch (RuntimeException e) {
                // no usable schema order: the fallback decides
            }
        }
        return merge(declared, fallback);
    }

    /**
     * {@code primary} (local names, own order kept) completed with the {@code fallback} names it lacks,
     * each placed before the next fallback name already present (else at the end).
     *
     * @param primary  the leading order (may be {@code null})
     * @param fallback names to fill in (may be {@code null})
     * @return a new mutable list
     */
    public static List<String> merge(List<String> primary, List<String> fallback) {
        List<String> result = new ArrayList<>();
        if (primary != null) {
            primary.forEach(n -> {
                String local = localName(n);
                if (!result.contains(local)) {
                    result.add(local);
                }
            });
        }
        if (fallback == null) {
            return result;
        }
        for (int i = 0; i < fallback.size(); i++) {
            String name = localName(fallback.get(i));
            if (result.contains(name)) {
                continue;
            }
            int at = result.size();
            for (int j = i + 1; j < fallback.size(); j++) {
                int later = result.indexOf(localName(fallback.get(j)));
                if (later >= 0) {
                    at = later;
                    break;
                }
            }
            result.add(at, name);
        }
        return result;
    }

    private static String localName(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
