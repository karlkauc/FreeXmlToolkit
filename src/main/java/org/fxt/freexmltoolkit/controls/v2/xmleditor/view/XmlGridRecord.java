package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * {@link GridRecord} over one {@link XmlElement} of a repeating-element group.
 *
 * <p>Columns come from the element's attributes (keys {@code "@name"}), its first-level
 * child elements (keys = tag names, first occurrence wins) and its direct text content
 * (key {@code "#text"}), in document order — exactly the structure the
 * {@link RepeatingElementsTable} historically analysed itself.</p>
 */
public final class XmlGridRecord implements GridRecord {

    /** Column key of the direct text content of a repeating element. */
    public static final String TEXT_KEY = "#text";

    private final XmlElement element;
    /** The element's {@link XmlElement#getSubtreeVersion() subtree version} when analysed. */
    private final int version;
    /** Built by {@link #analyze()}, then immutable (and possibly shared, see {@link #shareColumnKeys}). */
    private List<String> columnKeys = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    // complexChildren / attributeSuffixes / cellMembers: built by analyze(), then an empty one is
    // replaced by Map.of() and cellMembers dropped when every column holds one element — the
    // records of a table of 680,000 kept three maps and five lists each, and copying them in the
    // young collections took most of the grid's first build
    private Map<String, Object> complexChildren = new LinkedHashMap<>();
    /** Every child element per column name, in document order ({@code null}: at most one each). */
    private Map<String, List<XmlElement>> cellMembers = new LinkedHashMap<>();
    private Map<String, String> attributeSuffixes = new LinkedHashMap<>();

    public XmlGridRecord(XmlElement element) {
        this.element = element;
        this.version = element.getSubtreeVersion();
        analyze();
        columnKeys = sharedKeys(columnKeys);
        if (complexChildren.isEmpty()) {
            complexChildren = Map.of();
        }
        if (attributeSuffixes.isEmpty()) {
            attributeSuffixes = Map.of();
        }
        if (cellMembers.values().stream().allMatch(members -> members.size() < 2)) {
            cellMembers = null;
        }
    }

    /**
     * Column orders seen so far, one list instance each: the records of a large table, analysed
     * in parallel, take over the same list, and the table's structure pass then compares them by
     * identity (680,000 list comparisons on the FX thread before). Bounded like the attribute keys.
     */
    private static final java.util.concurrent.ConcurrentHashMap<List<String>, List<String>> KEY_ORDERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_KEY_ORDERS = 1_000;
    private static final int MAX_SHARED_ORDER_LENGTH = 64;

    private static List<String> sharedKeys(List<String> keys) {
        List<String> copy = List.copyOf(keys);
        if (copy.size() > MAX_SHARED_ORDER_LENGTH) {
            return copy;
        }
        List<String> known = KEY_ORDERS.get(copy);
        if (known != null) {
            return known;
        }
        if (KEY_ORDERS.size() < MAX_KEY_ORDERS) {
            known = KEY_ORDERS.putIfAbsent(copy, copy);
            if (known != null) {
                return known;
            }
        }
        return copy;
    }

    /**
     * @return whether the element is unchanged since this record analysed it (nothing in its
     *         subtree fired a change), so a rebuilt table can keep the record
     */
    public boolean isCurrent() {
        return element.getSubtreeVersion() == version;
    }

    /**
     * Column keys of attributes ({@code @name}), one instance per name: a table of 680,000 records
     * built a new string per record and attribute, and comparing and hashing the records' key
     * lists then read every character. Bounded, so a document with endless attribute names cannot
     * grow it without limit.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> ATTRIBUTE_KEYS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_ATTRIBUTE_KEYS = 10_000;
    /** Cell summaries of complex children ({@code <name>, <city>}), shared like the attribute keys. */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> SUMMARIES =
            new java.util.concurrent.ConcurrentHashMap<>();

    static String attributeKey(String name) {
        String key = ATTRIBUTE_KEYS.get(name);
        if (key == null) {
            key = "@" + name;
            if (ATTRIBUTE_KEYS.size() < MAX_ATTRIBUTE_KEYS) {
                String known = ATTRIBUTE_KEYS.putIfAbsent(name, key);
                if (known != null) {
                    key = known;
                }
            }
        }
        return key;
    }

    private void analyze() {
        // Attributes (in document order)
        for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
            columnKeys.add(attributeKey(attr.getKey()));
            if (attr.getValue() != null) {
                values.put(attr.getKey(), attr.getValue());
            }
        }
        // First-level child elements and direct text (in document order)
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement childEl) {
                String childName = childEl.getName();
                if (!columnKeys.contains(childName)) {
                    columnKeys.add(childName);
                }
                List<XmlElement> members = cellMembers.computeIfAbsent(childName, k -> new ArrayList<>());
                members.add(childEl);
                if (members.size() == 2) {
                    // Several children of one name (four Positions of one Waehrung): the cell holds
                    // the whole group and opens like any repeating group.
                    values.put(childName, groupSummary(childName, 2));
                    attributeSuffixes.remove(childName);
                    complexChildren.put(childName, members.getFirst());
                } else if (members.size() > 2) {
                    values.put(childName, groupSummary(childName, members.size()));
                }
                if (members.size() == 1) {
                    values.put(childName, extractElementText(childEl));
                    // The cell element's own attributes (e.g. ccy="EUR" on an
                    // Amount) are shown as a display-only suffix next to the value.
                    if (!childEl.getAttributes().isEmpty()) {
                        attributeSuffixes.put(childName, formatAttributes(childEl));
                    }
                    // Store complex children for later expansion
                    if (childEl.hasElementChildren()) {
                        complexChildren.put(childName, childEl);
                    }
                }
            } else if (child instanceof XmlText textNode) {
                String text = textNode.getText().trim();
                if (!text.isEmpty()) {
                    if (!columnKeys.contains(TEXT_KEY)) {
                        columnKeys.add(TEXT_KEY);
                    }
                    values.put(TEXT_KEY, text);
                }
            }
        }
    }

    /**
     * Replaces the column keys by an equal list shared with other records, so the records of a
     * large table hold one list instead of 680,000 and the table compares them by identity.
     *
     * @param keys a list equal to {@link #columnKeys()}
     */
    void shareColumnKeys(List<String> keys) {
        if (keys != columnKeys && keys.equals(columnKeys)) {
            columnKeys = keys;
        }
    }

    /** @return the wrapped element */
    public XmlElement getElement() {
        return element;
    }

    @Override
    public Object node() {
        return element;
    }

    @Override
    public List<String> columnKeys() {
        return columnKeys;
    }

    @Override
    public String columnName(String key) {
        return key.startsWith("@") ? key.substring(1) : key;
    }

    @Override
    public RepeatingElementsTable.ColumnType columnType(String key) {
        if (key.startsWith("@")) {
            return RepeatingElementsTable.ColumnType.ATTRIBUTE;
        }
        if (TEXT_KEY.equals(key)) {
            return RepeatingElementsTable.ColumnType.TEXT_CONTENT;
        }
        return RepeatingElementsTable.ColumnType.CHILD_ELEMENT;
    }

    @Override
    public Map<String, String> values() {
        return values;
    }

    @Override
    public Map<String, Object> complexChildren() {
        return complexChildren;
    }

    @Override
    public Map<String, String> attributeSuffixes() {
        return attributeSuffixes;
    }

    @Override
    public List<FlatRow> flattenComplexChild(String columnName) {
        return complexChildren.containsKey(columnName)
                ? FlatRow.flattenCellContent(element, cellMembers(columnName), FlatRow.CELL_GROUPS,
                        cellFormulas(columnName))
                : List.of();
    }

    @Override
    public List<FlatRow> flattenCell(String columnName, java.util.function.Predicate<String> groupAsTable) {
        return complexChildren.containsKey(columnName)
                ? FlatRow.flattenCellContent(element, cellMembers(columnName), groupAsTable,
                        cellFormulas(columnName))
                : List.of();
    }

    @Override
    public int countCellMatches(String columnName, String needle) {
        if (!complexChildren.containsKey(columnName)) {
            return 0;
        }
        int count = 0;
        for (XmlElement member : cellMembers(columnName)) {
            // The member's own name is the column header; its attributes and content are the cell's
            count += countAttributeMatches(member, needle);
            for (XmlNode child : member.getChildren()) {
                count += countNodeMatches(child, needle);
            }
        }
        return count;
    }

    private static int countNodeMatches(XmlNode node, String needle) {
        if (node instanceof XmlElement element) {
            int count = contains(element.getName(), needle) ? 1 : 0;
            count += countAttributeMatches(element, needle);
            for (XmlNode child : element.getChildren()) {
                count += countNodeMatches(child, needle);
            }
            return count;
        }
        if (node instanceof XmlText text) {
            return !text.isWhitespace() && contains(text.getText(), needle) ? 1 : 0;
        }
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData cdata) {
            return contains(cdata.getText(), needle) ? 1 : 0;
        }
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment comment) {
            return contains(comment.getText(), needle) ? 1 : 0;
        }
        return 0;
    }

    private static int countAttributeMatches(XmlElement element, String needle) {
        int count = 0;
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            if (contains(attribute.getKey(), needle) || contains(attribute.getValue(), needle)) {
                count++;
            }
        }
        return count;
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase().contains(needle);
    }

    /**
     * The formulas of this record shown in the opened cell of a column. A record has no row of its
     * own, so each of its formulas goes to the first complex column whose name is a step of the
     * formula's expression ({@code sum(Datenpunkt/Wert)} → Datenpunkt); a formula naming none of
     * them goes to the first complex column.
     *
     * @param columnName the column
     * @return the record's formula instructions for this cell, in document order
     */
    public List<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction> cellFormulas(
            String columnName) {
        if (!complexChildren.containsKey(columnName)) {
            return List.of();
        }
        List<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction> result = new ArrayList<>();
        for (XmlNode child : element.getChildren()) {
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction pi
                    && GridFormulas.isFormula(pi) && columnName.equals(formulaColumn(pi))) {
                result.add(pi);
            }
        }
        return result;
    }

    /** @return the complex column a formula of this record belongs to, or {@code null} without any */
    private String formulaColumn(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction pi) {
        if (complexChildren.isEmpty()) {
            return null;
        }
        String expression = GridFormulas.parse(pi.getData()).expression();
        if (expression != null) {
            for (String column : complexChildren.keySet()) {
                if (java.util.regex.Pattern.compile("(?<![\\w.:-])" + java.util.regex.Pattern.quote(column)
                        + "(?![\\w.:-])").matcher(expression).find()) {
                    return column;
                }
            }
        }
        return complexChildren.keySet().iterator().next();
    }

    /** @return every child element behind the column, in document order (empty for attributes and text) */
    public List<XmlElement> cellMembers(String columnName) {
        if (cellMembers == null) {
            // At most one child per name when analysed
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlElement el && el.getName().equals(columnName)) {
                    return List.of(el);
                }
            }
            return List.of();
        }
        List<XmlElement> members = cellMembers.get(columnName);
        return members == null ? List.of() : Collections.unmodifiableList(members);
    }

    /** @return the summary of a cell holding {@code count} children named {@code name} */
    static String groupSummary(String name, int count) {
        return count + " \u00d7 <" + name + ">";
    }

    /**
     * Formats an element's attributes as a compact display summary, e.g.
     * {@code ccy=EUR} or {@code ccy=EUR lang=de}.
     */
    static String formatAttributes(XmlElement element) {
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(attribute.getKey()).append('=').append(attribute.getValue());
        }
        return summary.toString();
    }

    /**
     * Extracts text content from an element.
     * For leaf elements (only text), returns the text.
     * For complex elements (with child elements), returns a summary showing child element names.
     */
    static String extractElementText(XmlElement element) {
        List<XmlNode> children = element.getChildren();
        if (children.size() == 1 && children.getFirst() instanceof XmlText only) {
            return only.getText().trim(); // the common leaf cell, without the general pass below
        }
        StringBuilder text = new StringBuilder();
        List<String> childElementNames = new ArrayList<>();

        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement childElement) {
                childElementNames.add(childElement.getName());
            } else if (child instanceof XmlText) {
                String t = ((XmlText) child).getText().trim();
                if (!t.isEmpty()) {
                    if (text.length() > 0) {
                        text.append(" ");
                    }
                    text.append(t);
                }
            }
        }

        // If it has element children, show element names
        if (!childElementNames.isEmpty()) {
            // Build summary showing element names (max 3, then "...")
            StringBuilder summary = new StringBuilder();
            int shown = 0;
            for (String name : childElementNames) {
                if (shown > 0) {
                    summary.append(", ");
                }
                if (shown >= 3) {
                    summary.append("...");
                    break;
                }
                summary.append("<").append(name).append(">");
                shown++;
            }

            if (text.length() > 0) {
                // Mixed content: show text + child summary
                return text.toString() + " [" + summary + "]";
            } else {
                // Only element children - show as expandable (one instance per summary text: the
                // records of a large table repeat the same few)
                String built = summary.toString();
                return built.length() > 64 ? built : SUMMARIES.computeIfAbsent(built,
                        k -> SUMMARIES.size() < MAX_ATTRIBUTE_KEYS ? k : null) instanceof String known ? known : built;
            }
        }

        return text.toString();
    }
}
