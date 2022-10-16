package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeCell;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

public class XmlTreeCell extends TreeCell<Node> {
    private final static Logger logger = LogManager.getLogger(XmlTreeCell.class);

    public XmlTreeCell() {
        super();
        setOnMouseClicked(event -> {
            var ti = getTreeItem();
            if (ti == null || event.getClickCount() < 2) {
                return;
            }
            Node n = ti.getValue();
            // ToDo: irgendwas damit anstellen!
        });
    }

    @Override
    protected void updateItem(Node node, boolean empty) {
        super.updateItem(node, empty);

        if (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.hasChildNodes()
                    && node.getFirstChild().getNodeType() == Node.TEXT_NODE
                    && node.getFirstChild().getTextContent().trim().length() > 1) {
                setGraphic(new Text(node.getNodeName() + ": " + node.getFirstChild().getTextContent()));
            } else {
                setGraphic(new Text(node.getNodeName() + " { " + countRealNodes(node) + " }"));
            }

        } else {
            setGraphic(null);
        }
    }

    private int countRealNodes(Node node) {
        int i = 0;
        for (int x = 0; x < node.getChildNodes().getLength(); x++) {
            var temp = node.getChildNodes().item(x);
            if (temp.getNodeType() == Node.ELEMENT_NODE) {
                i++;
            }
        }
        return i;
    }

}
