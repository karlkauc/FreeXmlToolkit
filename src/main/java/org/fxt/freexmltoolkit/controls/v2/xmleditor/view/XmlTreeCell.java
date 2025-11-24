package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.scene.control.TreeCell;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

/**
 * Custom TreeCell for displaying XmlNode objects in a tree view.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Different visual styles for different node types</li>
 *   <li>Icons for each node type</li>
 *   <li>Attribute display for elements</li>
 *   <li>Truncated text for long content</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlTreeCell extends TreeCell<XmlNode> {

    /**
     * Maximum length for displayed text content.
     */
    private static final int MAX_TEXT_LENGTH = 50;

    /**
     * Constructs a new XmlTreeCell.
     */
    public XmlTreeCell() {
        super();
    }

    @Override
    protected void updateItem(XmlNode node, boolean empty) {
        super.updateItem(node, empty);

        if (empty || node == null) {
            setText(null);
            setGraphic(null);
            setStyle("");
        } else {
            setText(getDisplayText(node));
            setGraphic(null); // We use text-based icons
            setStyle(getNodeStyle(node));
        }
    }

    /**
     * Gets the display text for a node.
     *
     * @param node the node
     * @return display text
     */
    private String getDisplayText(XmlNode node) {
        String icon = getIconForNode(node);
        String text = getTextForNode(node);
        return icon + " " + text;
    }

    /**
     * Gets the icon string for a node type.
     *
     * @param node the node
     * @return icon string
     */
    private String getIconForNode(XmlNode node) {
        return switch (node.getNodeType()) {
            case DOCUMENT -> "📄";
            case ELEMENT -> "📦";
            case TEXT -> "📝";
            case COMMENT -> "💬";
            case CDATA -> "📋";
            case PROCESSING_INSTRUCTION -> "⚙️";
            case ATTRIBUTE -> "🏷️";
        };
    }

    /**
     * Gets the text content for a node.
     *
     * @param node the node
     * @return text content
     */
    private String getTextForNode(XmlNode node) {
        return switch (node.getNodeType()) {
            case DOCUMENT -> getDocumentText((XmlDocument) node);
            case ELEMENT -> getElementText((XmlElement) node);
            case TEXT -> getTextNodeText((XmlText) node);
            case COMMENT -> getCommentText((XmlComment) node);
            case CDATA -> getCDataText((XmlCData) node);
            case PROCESSING_INSTRUCTION -> getPIText((XmlProcessingInstruction) node);
            case ATTRIBUTE -> getAttributeText((XmlAttribute) node);
        };
    }

    /**
     * Gets display text for a document node.
     */
    private String getDocumentText(XmlDocument doc) {
        if (doc.getRootElement() != null) {
            return "Document <" + doc.getRootElement().getName() + ">";
        }
        return "Document (empty)";
    }

    /**
     * Gets display text for an element node.
     */
    private String getElementText(XmlElement element) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(element.getName()).append(">");

        // Add attributes if present
        if (!element.getAttributes().isEmpty()) {
            sb.append(" [");
            boolean first = true;
            for (var entry : element.getAttributes().entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append("=\"");
                String value = entry.getValue();
                if (value.length() > 20) {
                    value = value.substring(0, 20) + "...";
                }
                sb.append(value).append("\"");
                first = false;
            }
            sb.append("]");
        }

        return sb.toString();
    }

    /**
     * Gets display text for a text node.
     */
    private String getTextNodeText(XmlText textNode) {
        String text = textNode.getText();
        if (text == null || text.isBlank()) {
            return "(empty text)";
        }

        // Remove extra whitespace
        text = text.trim().replaceAll("\\s+", " ");

        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "...";
        }

        return "\"" + text + "\"";
    }

    /**
     * Gets display text for a comment node.
     */
    private String getCommentText(XmlComment comment) {
        String text = comment.getText();
        if (text == null || text.isBlank()) {
            return "<!-- (empty) -->";
        }

        text = text.trim().replaceAll("\\s+", " ");

        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "...";
        }

        return "<!-- " + text + " -->";
    }

    /**
     * Gets display text for a CDATA node.
     */
    private String getCDataText(XmlCData cdata) {
        String text = cdata.getText();
        if (text == null || text.isBlank()) {
            return "<![CDATA[ (empty) ]]>";
        }

        text = text.trim().replaceAll("\\s+", " ");

        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "...";
        }

        return "<![CDATA[ " + text + " ]]>";
    }

    /**
     * Gets display text for a processing instruction node.
     */
    private String getPIText(XmlProcessingInstruction pi) {
        String target = pi.getTarget();
        String data = pi.getData();

        if (data == null || data.isBlank()) {
            return "<?" + target + " ?>";
        }

        if (data.length() > 30) {
            data = data.substring(0, 30) + "...";
        }

        return "<?" + target + " " + data + " ?>";
    }

    /**
     * Gets display text for an attribute node.
     */
    private String getAttributeText(XmlAttribute attr) {
        String value = attr.getValue();
        if (value.length() > 30) {
            value = value.substring(0, 30) + "...";
        }
        return attr.getName() + "=\"" + value + "\"";
    }

    /**
     * Gets the CSS style for a node.
     *
     * @param node the node
     * @return CSS style string
     */
    private String getNodeStyle(XmlNode node) {
        return switch (node.getNodeType()) {
            case DOCUMENT -> "-fx-font-weight: bold;";
            case ELEMENT -> "-fx-text-fill: #000080; -fx-font-weight: bold;";
            case TEXT -> "-fx-text-fill: #000000;";
            case COMMENT -> "-fx-text-fill: #808080; -fx-font-style: italic;";
            case CDATA -> "-fx-text-fill: #660066;";
            case PROCESSING_INSTRUCTION -> "-fx-text-fill: #880088;";
            case ATTRIBUTE -> "-fx-text-fill: #FF0000;";
        };
    }
}
