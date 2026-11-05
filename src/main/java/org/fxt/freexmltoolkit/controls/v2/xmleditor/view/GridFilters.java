package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.LinkedHashMap;
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
        Map<String, XmlProcessingInstruction> result = new LinkedHashMap<>();
        if (document != null) {
            collect(document.getRootElement(), result);
        }
        return result;
    }

    private static void collect(XmlElement element, Map<String, XmlProcessingInstruction> out) {
        if (element == null) {
            return;
        }
        String key = null;
        for (XmlNode child : element.getChildren()) {
            if (isFilter(child)) {
                if (key == null) {
                    key = XmlNodeXPath.positional(element);
                    out.putIfAbsent(key, (XmlProcessingInstruction) child);
                }
            } else if (child instanceof XmlElement el) {
                collect(el, out);
            }
        }
    }
}
