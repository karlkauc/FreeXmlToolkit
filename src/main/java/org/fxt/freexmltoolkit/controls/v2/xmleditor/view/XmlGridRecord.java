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
    private final List<String> columnKeys = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Object> complexChildren = new LinkedHashMap<>();
    private final Map<String, String> attributeSuffixes = new LinkedHashMap<>();

    public XmlGridRecord(XmlElement element) {
        this.element = element;
        analyze();
    }

    private void analyze() {
        // Attributes (in document order)
        for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
            columnKeys.add("@" + attr.getKey());
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
                if (!values.containsKey(childName)) {
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
        return Collections.unmodifiableList(columnKeys);
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
        Object child = complexChildren.get(columnName);
        return child instanceof XmlElement el ? FlatRow.flattenElement(el) : List.of();
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
                // Only element children - show as expandable
                return summary.toString();
            }
        }

        return text.toString();
    }
}
