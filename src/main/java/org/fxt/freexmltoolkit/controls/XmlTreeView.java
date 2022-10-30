package org.fxt.freexmltoolkit.controls;

import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import java.net.URL;
import java.util.ResourceBundle;

public class XmlTreeView extends TreeView<org.w3c.dom.Node> implements Initializable {

    private final static Logger logger = LogManager.getLogger(XmlTreeView.class);
    Document xmlDocument;

    public XmlTreeView() {
        if (xmlDocument != null) {
            this.setRoot(new XmlTreeItem(xmlDocument));
            this.setShowRoot(true);
        }
    }

    public void setXmlDocument(Document document) {
        if (document != null) {
            logger.debug("Set Document: {}", document.toString());
            this.xmlDocument = document;

            var xmlTreeItem = new XmlTreeItem(document.getDocumentElement());
            this.setCellFactory(param -> new XmlTreeCell());
            this.setRoot(xmlTreeItem);

            printChildren(xmlTreeItem);
        }
    }

    int maxWidth = 0;

    private void printChildren(TreeItem<org.w3c.dom.Node> root) {
        System.out.println("Current Parent :" + root.getValue());

        for (TreeItem<org.w3c.dom.Node> child : root.getChildren()) {
            if (child.getChildren().isEmpty()) {
                System.out.println(child.getValue());
            } else {
                printChildren(child);
            }
        }
    }

    @Override
    public Node getStyleableNode() {
        return super.getStyleableNode();
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("INIT!");
    }
}
