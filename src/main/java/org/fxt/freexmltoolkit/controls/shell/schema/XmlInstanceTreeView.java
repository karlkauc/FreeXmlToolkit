package org.fxt.freexmltoolkit.controls.shell.schema;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A read-only tree view of an <em>XML instance</em> document (the element/text
 * structure), as opposed to {@link XsdTreeView} which renders an XSD schema.
 * Whitespace-only text nodes are omitted; element attributes are shown inline.
 * Parsing is XXE-hardened.
 */
public class XmlInstanceTreeView extends TreeView<Node> {

    public XmlInstanceTreeView() {
        getStyleClass().add("fxt-xml-tree");
        setCellFactory(tv -> new XmlNodeCell());
    }

    /**
     * Parses {@code xml} and renders its DOM tree.
     *
     * @return {@code true} if parsing succeeded; on failure the tree is cleared
     */
    public boolean setXml(String xml) {
        try {
            Document doc = parse(xml);
            setRoot(buildItem(doc.getDocumentElement()));
            return true;
        } catch (Exception e) {
            setRoot(null);
            return false;
        }
    }

    private TreeItem<Node> buildItem(Node node) {
        TreeItem<Node> item = new TreeItem<>(node);
        item.setExpanded(true);
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                item.getChildren().add(buildItem(child));
            } else if (child.getNodeType() == Node.TEXT_NODE
                    && child.getTextContent() != null && !child.getTextContent().isBlank()) {
                item.getChildren().add(new TreeItem<>(child));
            }
        }
        return item;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Renders an element (name + inline attributes) or a text node (its value). */
    private static final class XmlNodeCell extends TreeCell<Node> {
        @Override
        protected void updateItem(Node item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.getNodeType() == Node.TEXT_NODE) {
                setText("\"" + item.getTextContent().strip() + "\"");
                setGraphic(null);
                return;
            }
            StringBuilder label = new StringBuilder(item.getNodeName());
            NamedNodeMap attrs = item.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    Node attr = attrs.item(i);
                    label.append(' ').append(attr.getNodeName()).append("=\"")
                            .append(attr.getNodeValue()).append('"');
                }
            }
            setText(label.toString());
            IconifyIcon icon = new IconifyIcon("bi-code-slash");
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
