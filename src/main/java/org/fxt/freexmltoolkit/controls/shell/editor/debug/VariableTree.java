package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.saxon.s9api.XdmArray;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmMap;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.QName;

import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;

/**
 * The structure behind the Variables tree: a row ({@link Node}) and how it unfolds — a sequence
 * into its items, an element into its attributes and child nodes, a document into its children,
 * a map into its entries, an array into its members. Every row knows the XPath that reaches it
 * from its variable ({@code $order/item[2]/@id}), so it can be opened or watched.
 */
final class VariableTree {

    /** Children shown per row before a "… more" row. */
    static final int MAX_CHILDREN = 500;
    private static final int MAX_TEXT = 200;

    /**
     * One row.
     *
     * @param name    what the row shows in the Name column
     * @param value   rendered value
     * @param type    type label
     * @param scope   scope label (empty for nested rows)
     * @param xdm     the value, or {@code null} when only text is known (no children then)
     * @param binding the variable row this is, or {@code null} for a nested row
     * @param expr    XPath reaching this value from its variable, or {@code null} when not expressible
     */
    record Node(String name, String value, String type, String scope, XdmValue xdm,
                VariableBinding binding, String expr) {

        /** A top-level row for a variable binding with its (optional) full value. */
        static Node of(VariableBinding b, XdmValue xdm) {
            String expr = VariablesView.isWatchable(b) ? b.name() : null;
            return new Node(b.name(), b.value(), b.type(), b.scope() == null ? "" : b.scope().name(), xdm, b, expr);
        }

        /** @return whether the row unfolds into children. */
        boolean hasChildren() {
            if (xdm == null || xdm.size() == 0) {
                return false;
            }
            if (xdm.size() > 1) {
                return true;
            }
            XdmItem item = xdm.itemAt(0);
            if (item instanceof XdmMap map) {
                return map.mapSize() > 0;
            }
            if (item instanceof XdmArray array) {
                return array.arrayLength() > 0;
            }
            if (item instanceof XdmNode node) {
                return node.getNodeKind() == XdmNodeKind.DOCUMENT
                        || node.getNodeKind() == XdmNodeKind.ELEMENT && (node.children().iterator().hasNext()
                        || node.axisIterator(net.sf.saxon.s9api.Axis.ATTRIBUTE).hasNext());
            }
            return false;
        }
    }

    private VariableTree() {
    }

    /** @return the rows {@code parent} unfolds into (empty for a leaf). */
    static List<Node> children(Node parent) {
        List<Node> rows = new ArrayList<>();
        XdmValue v = parent.xdm();
        if (v == null || v.size() == 0) {
            return rows;
        }
        if (v.size() > 1) {
            for (int i = 0; i < v.size() && i < MAX_CHILDREN; i++) {
                rows.add(row("[" + (i + 1) + "]", v.itemAt(i), item(parent.expr(), i + 1)));
            }
            more(rows, v.size());
            return rows;
        }
        XdmItem item = v.itemAt(0);
        if (item instanceof XdmMap map) {
            for (Map.Entry<XdmAtomicValue, XdmValue> e : map.entrySet()) {
                if (rows.size() >= MAX_CHILDREN) {
                    break;
                }
                XdmAtomicValue key = e.getKey();
                rows.add(row(key.getStringValue(), e.getValue(),
                        parent.expr() == null ? null : parent.expr() + "?(" + literal(key) + ")"));
            }
            more(rows, map.mapSize());
        } else if (item instanceof XdmArray array) {
            for (int i = 0; i < array.arrayLength() && i < MAX_CHILDREN; i++) {
                rows.add(row("[" + (i + 1) + "]", array.get(i), parent.expr() == null ? null : parent.expr() + "?" + (i + 1)));
            }
            more(rows, array.arrayLength());
        } else if (item instanceof XdmNode node) {
            nodeChildren(node, parent.expr(), rows);
        }
        return rows;
    }

    private static void nodeChildren(XdmNode node, String expr, List<Node> rows) {
        if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            var attrs = node.axisIterator(net.sf.saxon.s9api.Axis.ATTRIBUTE);
            while (attrs.hasNext() && rows.size() < MAX_CHILDREN) {
                XdmNode a = attrs.next();
                rows.add(new Node("@" + a.getNodeName(), a.getStringValue(), "attribute()", "", a, null,
                        expr == null ? null : expr + "/@" + step(a.getNodeName())));
            }
        }
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        for (XdmNode child : node.children()) {
            totals.merge(stepKey(child), 1, Integer::sum);
        }
        int count = 0;
        for (XdmNode child : node.children()) {
            String key = stepKey(child);
            int index = seen.merge(key, 1, Integer::sum);
            if (child.getNodeKind() == XdmNodeKind.TEXT && child.getStringValue().isBlank()) {
                continue; // indentation
            }
            count++;
            if (rows.size() >= MAX_CHILDREN) {
                continue;
            }
            String step = key + (totals.get(key) > 1 ? "[" + index + "]" : "");
            String name = switch (child.getNodeKind()) {
                case ELEMENT -> "<" + child.getNodeName() + ">";
                case TEXT -> "text()";
                case COMMENT -> "comment()";
                case PROCESSING_INSTRUCTION -> "processing-instruction(" + child.getNodeName() + ")";
                default -> child.getNodeKind().name().toLowerCase(java.util.Locale.ROOT);
            };
            rows.add(row(name, child, expr == null ? null : expr + "/" + step));
        }
        if (count > MAX_CHILDREN) {
            more(rows, count);
        }
    }

    /** The XPath step of a child node without its position. */
    private static String stepKey(XdmNode child) {
        return switch (child.getNodeKind()) {
            case ELEMENT -> step(child.getNodeName());
            case TEXT -> "text()";
            case COMMENT -> "comment()";
            case PROCESSING_INSTRUCTION -> "processing-instruction(" + child.getNodeName().getLocalName() + ")";
            default -> "node()";
        };
    }

    /** {@code local} for a name without namespace, else {@code Q{uri}local} (needs no prefix). */
    private static String step(QName name) {
        return name.getNamespace().isEmpty() ? name.getLocalName()
                : "Q{" + name.getNamespace() + "}" + name.getLocalName();
    }

    /** The XPath of item {@code i} of the sequence {@code expr} reaches. */
    private static String item(String expr, int i) {
        if (expr == null) {
            return null;
        }
        boolean plainVariable = expr.matches("\\$[\\w.:-]+");
        return (plainVariable ? expr : "(" + expr + ")") + "[" + i + "]";
    }

    /** A map key as an XPath literal: numbers as they are, everything else as a quoted string. */
    private static String literal(XdmAtomicValue key) {
        String type = key.getPrimitiveTypeName().getLocalName();
        boolean numeric = "decimal".equals(type) || "double".equals(type) || "float".equals(type)
                || "integer".equals(type);
        return numeric ? key.getStringValue() : "'" + key.getStringValue().replace("'", "''") + "'";
    }

    private static Node row(String name, XdmValue value, String expr) {
        return new Node(name, WatchEvaluator.render(value, MAX_TEXT), WatchEvaluator.describeType(value), "",
                value, null, expr);
    }

    private static void more(List<Node> rows, int total) {
        if (total > MAX_CHILDREN) {
            rows.add(new Node("…", (total - MAX_CHILDREN) + " more", "", "", null, null, null));
        }
    }
}
