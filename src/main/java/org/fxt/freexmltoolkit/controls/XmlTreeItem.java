package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

public class XmlTreeItem extends TreeItem<Node> {

    private final static Logger logger = LogManager.getLogger(XmlTreeItem.class);

    public XmlTreeItem(Node node) {
        super(node);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            logger.debug("Node Value: {}", node.getNodeValue());
        }

        if (node.hasChildNodes()) {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                Node temp = node.getChildNodes().item(i);
                if (temp.getNodeType() == Node.ELEMENT_NODE) {
                    this.getChildren().add(new XmlTreeItem(temp));
                }
            }
        }

    }


}
