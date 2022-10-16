package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeCell;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

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
            System.out.println("nodeToString(n) = " + nodeToString(n));
        });
    }

    private static String nodeToString(Node node) {
        logger.debug("Node Type: {}", node.getNodeType());

        StringWriter sw = new StringWriter();
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerException te) {
            System.out.println("nodeToString Transformer Exception");
        }
        return sw.toString();
    }

    @Override
    protected void updateItem(Node node, boolean empty) {
        super.updateItem(node, empty);

        if (node != null) {
            // logger.debug("UPDATE: {}", node.getNodeName());
            if (node.hasChildNodes()) {
                setGraphic(new Text(node.getNodeName()));
            } else {
                setGraphic(new Text(node.getNodeName() + ":" + node.getNodeValue()));
            }

        } else {
            setGraphic(null);
        }
    }

}
