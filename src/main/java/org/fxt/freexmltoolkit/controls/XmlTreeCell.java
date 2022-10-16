package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeCell;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

public class XmlTreeCell extends TreeCell<Node> {
    private final static Logger logger = LogManager.getLogger(XmlTreeCell.class);

    @Override
    protected void updateItem(Node node, boolean empty) {
        super.updateItem(node, empty);

        if (node != null) {
            logger.debug("UPDATE: {}", node.getNodeName());
            setGraphic(new Text(node.getNodeName()));
        }
    }

}
