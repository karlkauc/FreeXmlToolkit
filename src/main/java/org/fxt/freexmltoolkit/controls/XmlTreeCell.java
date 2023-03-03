package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontSmoothingType;
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
            logger.debug("XPath: {}", getXPath(n));

            HBox content = new HBox();
            Text tNodeName = new Text(n.getNodeName());
            VBox boxNodeName = new VBox();
            boxNodeName.getChildren().add(tNodeName);
            boxNodeName.setPadding(new Insets(0, 10, 0, 0));

            TextField tfNodeValue = new TextField();
            tfNodeValue.setText(n.getFirstChild().getTextContent());

            tfNodeValue.setOnKeyPressed(ae -> {
                System.out.println("KEY PRESSED: " + ae.getText());
                System.out.println("TEXTFIELD: " + tfNodeValue.getText());

            });

            tfNodeValue.setOnAction(ae -> {
                n.setNodeValue(tfNodeValue.getText());
                // System.out.println(XmlServiceImpl.getInstance().prettyFormat(Paths.get()));
            });


            content.getChildren().addAll(boxNodeName, tfNodeValue);

            this.setGraphic(content);
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

                HBox content = new HBox();
                VBox boxNodeName = new VBox();
                VBox boxNodeContent = new VBox();

                Text tNodeName = new Text(node.getNodeName());
                tNodeName.setFontSmoothingType(FontSmoothingType.LCD);
                tNodeName.setStyle("-fx-font-weight: bold;");

                boxNodeName.getChildren().add(tNodeName);
                boxNodeName.setPadding(new Insets(0, 10, 0, 0));

                Text tNodeContent = new Text(node.getFirstChild().getTextContent());
                boxNodeContent.getChildren().add(tNodeContent);

                content.getChildren().addAll(boxNodeName, boxNodeContent);
                content.setStyle("-fx-border-width: 1px 1px 1px 1px; -fx-border-color: black; -fx-border-style: solid;");

                logger.debug("Node: {} - MAX SIZE: {}", getXPath(node.getParentNode()), TreeHelper.widthHelper.get(getXPath(node.getParentNode())));
                boxNodeName.prefWidthProperty().bind(TreeHelper.widthHelper.get(getXPath(node.getParentNode())));

                setGraphic(content);
            } else {
                var maxWidth = calculateMaxWidth(node);
                logger.debug("{} - Max width: {}", node.getNodeName(), maxWidth);

                TreeHelper.widthHelper.put(getXPath(node), new SimpleDoubleProperty(maxWidth + 10));

                HBox content = new HBox();
                Text nodeName = new Text(node.getNodeName());
                nodeName.setFill(Color.DARKGRAY);

                Text nodeCount = new Text(" {" + countRealNodes(node) + "}");
                nodeCount.setFill(Color.FUCHSIA);

                content.getChildren().addAll(nodeName, nodeCount);
                setGraphic(content);
            }
        } else {
            setGraphic(null);
        }
    }

    private static String getXPath(Node node) {
        Node parent = node.getParentNode();
        if (parent == null) {
            return node.getNodeName();
        }
        return getXPath(parent) + "/" + node.getNodeName();
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

    private double calculateMaxWidth(Node node) {
        double maxWidth = 0;

        for (int x = 0; x < node.getChildNodes().getLength(); x++) {
            var temp = node.getChildNodes().item(x);

            if (temp.getNodeType() == Node.ELEMENT_NODE) {
                var t = new Text(temp.getNodeName());
                if (t.getLayoutBounds().getWidth() > maxWidth) {
                    maxWidth = t.getLayoutBounds().getWidth();
                }
            }
        }
        return maxWidth;
    }

}
