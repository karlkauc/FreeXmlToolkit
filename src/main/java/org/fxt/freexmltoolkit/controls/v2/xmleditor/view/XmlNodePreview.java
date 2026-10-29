package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * Builds the one-line inline preview the grid shows for a collapsed element (XMLSpy's
 * "Show inline previews"): the element serialized without indentation, whitespace runs
 * collapsed, cut off with an ellipsis once the soft length limit is reached. The walk
 * stops at the limit, so previewing the collapsed root of a large document stays cheap.
 */
public final class XmlNodePreview {

    /** The ellipsis appended to a cut-off preview. */
    public static final String ELLIPSIS = "…";

    private XmlNodePreview() {
    }

    /**
     * @param node     the node to preview
     * @param maxChars the soft length limit (the result may exceed it by one token)
     * @return the compact one-line preview, never {@code null}
     */
    public static String of(XmlNode node, int maxChars) {
        StringBuilder sb = new StringBuilder(Math.min(maxChars + 16, 4096));
        boolean complete = append(node, sb, maxChars);
        if (!complete) {
            sb.append(ELLIPSIS);
        }
        return sb.toString();
    }

    /** @return {@code false} when the limit was hit and the output is incomplete */
    private static boolean append(XmlNode node, StringBuilder sb, int max) {
        if (sb.length() >= max) {
            return false;
        }
        if (node instanceof XmlElement el) {
            sb.append('<').append(el.getQualifiedName());
            for (Map.Entry<String, String> attr : el.getAttributes().entrySet()) {
                sb.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
                if (sb.length() >= max) {
                    return false;
                }
            }
            if (el.getChildren().isEmpty()) {
                sb.append("/>");
                return true;
            }
            sb.append('>');
            for (XmlNode child : el.getChildren()) {
                if (child instanceof XmlText text && text.isWhitespace()) {
                    continue;
                }
                if (!append(child, sb, max)) {
                    return false;
                }
            }
            sb.append("</").append(el.getQualifiedName()).append('>');
            return true;
        }
        if (node instanceof XmlText text) {
            appendCollapsed(text.getText(), sb);
        } else if (node instanceof XmlCData cdata) {
            sb.append("<![CDATA[");
            appendCollapsed(cdata.getText(), sb);
            sb.append("]]>");
        } else if (node instanceof XmlComment comment) {
            sb.append("<!--");
            appendCollapsed(comment.getText(), sb);
            sb.append("-->");
        } else if (node instanceof XmlProcessingInstruction pi) {
            sb.append("<?").append(pi.getTarget());
            if (pi.getData() != null && !pi.getData().isBlank()) {
                sb.append(' ');
                appendCollapsed(pi.getData(), sb);
            }
            sb.append("?>");
        }
        return sb.length() < max;
    }

    /** Appends {@code text} with every whitespace run collapsed to one space. */
    private static void appendCollapsed(String text, StringBuilder sb) {
        if (text == null) {
            return;
        }
        boolean pendingSpace = false;
        boolean started = false; // leading whitespace of the chunk is dropped
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = started;
            } else {
                if (pendingSpace) {
                    sb.append(' ');
                }
                pendingSpace = false;
                started = true;
                sb.append(c);
            }
        }
    }
}
