package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetProcessingInstructionCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;

/**
 * Grid filters saved in the document: a processing instruction
 * {@code <?fxt-filter expression="…"?>} as child of the filtered element. The grid shows no row
 * for it (its funnel line stands for it); a saved filter is loaded <em>inactive</em>, so a file
 * never opens filtered without the user switching the filter on.
 */
public final class GridFilters {

    /** The processing-instruction target of a saved filter. */
    public static final String TARGET = "fxt-filter";

    private GridFilters() {
    }

    /** @return whether the node is a saved filter instruction */
    public static boolean isFilter(XmlNode node) {
        return node instanceof XmlProcessingInstruction pi && TARGET.equals(pi.getTarget());
    }

    /** @return the expression stored in a filter instruction's data (empty when missing) */
    public static String parse(String data) {
        if (data != null) {
            Matcher m = GridFormulas.FIELD.matcher(data);
            while (m.find()) {
                if ("expression".equals(m.group(1))) {
                    return GridFormulas.unescape(m.group(2));
                }
            }
        }
        return "";
    }

    /** @return the instruction data for an expression */
    static String toData(String expression) {
        return "expression=\"" + GridFormulas.escape(expression) + "\"";
    }

    /** @return a new filter instruction */
    public static XmlProcessingInstruction create(String expression) {
        return new XmlProcessingInstruction(TARGET, toData(expression));
    }

    /** @return the command that replaces the expression of a filter instruction */
    public static XmlCommand update(XmlProcessingInstruction pi, String expression) {
        return new SetProcessingInstructionCommand(pi, TARGET, toData(expression));
    }

    /** @return the element's saved filter instruction (the first one), or {@code null} */
    public static XmlProcessingInstruction of(XmlElement element) {
        for (XmlNode child : element.getChildren()) {
            if (isFilter(child)) {
                return (XmlProcessingInstruction) child;
            }
        }
        return null;
    }

    /**
     * @return every saved filter of the document by the positional XPath of its element (the key
     *         the grid gives that element's row), in document order
     */
    public static Map<String, XmlProcessingInstruction> savedFilters(XmlDocument document) {
        return savedFilters(document, null);
    }

    /** Elements with this many children remember which of their child subtrees hold no filter. */
    static final int MEMO_CHILDREN = 1_000;

    /**
     * Remembers, for the children of large elements, the subtree version at which a child's subtree
     * held no filter, so the next scan skips it while unchanged: after a cell edit in a table of
     * 680,000 records the scan of 4 million nodes (0.6 s on every grid rebuild) visits one record.
     * Children are matched by position (arrays, no hashing); a reordered parent is scanned again.
     */
    public static final class Memo {
        private Map<XmlElement, Children> byParent = new java.util.IdentityHashMap<>();
        private Map<XmlElement, Children> next;
    }

    /** Per child index: the child, its subtree version and whether that subtree held no filter. */
    private record Children(XmlNode[] nodes, int[] versions, boolean[] filterFree) {
    }

    /**
     * {@link #savedFilters(XmlDocument)} that skips the subtrees {@code memo} knows to be unchanged
     * and filter-free, and updates the memo.
     *
     * @param document the document
     * @param memo     the memo of the previous scan of the same document, or {@code null}
     * @return the saved filters by element key, in document order
     */
    public static Map<String, XmlProcessingInstruction> savedFilters(XmlDocument document, Memo memo) {
        Map<String, XmlProcessingInstruction> result = new LinkedHashMap<>();
        if (document != null) {
            if (memo != null) {
                memo.next = new java.util.IdentityHashMap<>();
            }
            collect(document.getRootElement(), result, memo);
            if (memo != null) {
                memo.byParent = memo.next; // only what this scan saw (removed elements drop out)
                memo.next = null;
            }
        }
        return result;
    }

    /**
     * At least this many children of a large element to scan: their subtrees are checked in
     * parallel first (the first scan of a 4-million-node document took 0.6 s on the FX thread).
     */
    static final int PARALLEL_CHILDREN = 10_000;

    /** @return whether the subtree of {@code element} holds a filter */
    private static boolean collect(XmlElement element, Map<String, XmlProcessingInstruction> out, Memo memo) {
        if (element == null) {
            return false;
        }
        List<XmlNode> children = element.getChildren();
        Children before = null;
        Children now = null;
        boolean[] unknown = null; // per child: to scan (not known filter-free)
        boolean[] holds = null;   // per child to scan: its subtree holds a filter (parallel pre-check)
        if (memo != null && children.size() >= MEMO_CHILDREN) {
            before = memo.byParent.get(element);
            int n = children.size();
            now = new Children(new XmlNode[n], new int[n], new boolean[n]);
            memo.next.put(element, now);
            unknown = new boolean[n];
            int toScan = 0;
            for (int i = 0; i < n; i++) {
                if (children.get(i) instanceof XmlElement el
                        && !(before != null && i < before.nodes().length && before.nodes()[i] == el
                        && before.versions()[i] == el.getSubtreeVersion() && before.filterFree()[i])) {
                    unknown[i] = true;
                    toScan++;
                }
            }
            if (toScan >= PARALLEL_CHILDREN) {
                boolean[] result = new boolean[n];
                boolean[] scan = unknown;
                java.util.stream.IntStream.range(0, n).parallel()
                        .filter(i -> scan[i])
                        .forEach(i -> result[i] = holdsFilter((XmlElement) children.get(i)));
                holds = result;
            }
        }
        boolean found = false;
        String key = null;
        for (int i = 0; i < children.size(); i++) {
            XmlNode child = children.get(i);
            if (isFilter(child)) {
                found = true;
                if (key == null) {
                    key = XmlNodeXPath.positional(element);
                    out.putIfAbsent(key, (XmlProcessingInstruction) child);
                }
            } else if (child instanceof XmlElement el) {
                if (now == null) {
                    found |= collect(el, out, memo);
                    continue;
                }
                boolean free = !unknown[i];
                if (!free) {
                    // A subtree the parallel pre-check found filter-free is not walked again
                    free = holds != null && !holds[i] || !collect(el, out, memo);
                    found |= !free;
                }
                now.nodes()[i] = el;
                now.versions()[i] = el.getSubtreeVersion();
                now.filterFree()[i] = free;
            }
        }
        return found;
    }

    /** @return whether the subtree of {@code element} holds a filter (read-only, any thread) */
    private static boolean holdsFilter(XmlElement element) {
        for (XmlNode child : element.getChildren()) {
            if (isFilter(child) || child instanceof XmlElement el && holdsFilter(el)) {
                return true;
            }
        }
        return false;
    }
}
