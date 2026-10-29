package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser;

/**
 * The content of an element as raw markup and back (XMLSpy "Edit as Raw Text"): {@link #of}
 * serializes the children (markup and entities unresolved), {@link #parse} turns edited markup
 * into new child nodes or reports why it is not well-formed.
 */
public final class RawTextContent {

    private static final String WRAPPER = "fxt-raw-content";

    private RawTextContent() {
    }

    /** @return the element's children serialized as markup (no indentation added) */
    public static String of(XmlElement element) {
        StringBuilder sb = new StringBuilder();
        for (XmlNode child : element.getChildren()) {
            sb.append(child.serialize(0));
        }
        return sb.toString();
    }

    /**
     * @param markup edited content (elements, text, comments, CDATA, PIs; may be empty)
     * @return the parsed nodes, ready to become children (no parent)
     * @throws IllegalArgumentException with the parser's message when the markup is not well-formed
     */
    public static List<XmlNode> parse(String markup) {
        String text = markup == null ? "" : markup;
        XmlDocument doc;
        try {
            doc = new XmlParser().parse("<" + WRAPPER + ">" + text + "</" + WRAPPER + ">");
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage() != null ? e.getMessage() : "Not well-formed XML", e);
        }
        List<XmlNode> nodes = new ArrayList<>();
        for (XmlNode child : List.copyOf(doc.getRootElement().getChildren())) {
            nodes.add(child.deepCopy(""));
        }
        return nodes;
    }
}
