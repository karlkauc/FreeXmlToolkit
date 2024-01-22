/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import java.util.Objects;

public class SimpleNodeElement extends VBox {
    Node node;

    final Image imagePlus = new Image(Objects.requireNonNull(getClass().getResource("/img/plus.png")).toString());
    final ImageView imageViewPlus = new ImageView(imagePlus);

    final Image imageMinus = new Image(Objects.requireNonNull(getClass().getResource("/img/minus.png")).toString());
    final ImageView imageViewMinus = new ImageView(imageMinus);


    public SimpleNodeElement() {

    }

    public SimpleNodeElement(Node node) {
        this.node = node;

        imageViewPlus.setFitHeight(15);
        imageViewPlus.setFitWidth(15);
        imageViewPlus.setPreserveRatio(true);

        imageViewMinus.setFitHeight(15);
        imageViewMinus.setFitWidth(15);
        imageViewMinus.setPreserveRatio(true);

        createByNode(node);
    }

    private final static Logger logger = LogManager.getLogger(SimpleNodeElement.class);

    public void createByNode(Node node) {
        // this.getChildren().add(new Label(node.getNodeName() + " {" + calculateCount(node) + "}"));

        int row = 0;
        int col = 0;

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("treeGrid");

        if (node.hasChildNodes()) {
            // Plus

            if (node.getAttributes() != null) {
                logger.debug("Attributes: {}", node.getAttributes().getLength());

                for (int i = 0; i < node.getAttributes().getLength(); i++) {
                    var attributes = node.getAttributes().item(i);
                    logger.debug(attributes.getNodeName() + ":" + attributes.getNodeValue());
                    gridPane.add(new Label(attributes.getNodeName()), 0, row);
                    gridPane.add(new Label(attributes.getNodeValue()), 1, row);
                    row++;
                }
            }

            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                var subNode = node.getChildNodes().item(i);
                logger.debug("Node Type: {}", subNode.getNodeType());

                int finalRow = row;
                switch (subNode.getNodeType()) {
                    case Node.COMMENT_NODE -> {
                        final Label l = new Label("COMMENT: " + subNode.getNodeValue());
                        l.getStyleClass().add("xmlTreeComment");
                        // this.getChildren().add(l);
                        // DEBUG - nachher wieder reingeben
                    }
                    case Node.ELEMENT_NODE -> {
                        if (subNode.getChildNodes().getLength() == 1 &&
                                subNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
                            var n = subNode.getChildNodes().item(0);

                            logger.debug("adding element text node: {}", subNode.getNodeName() + ":" + n.getNodeValue());
                            logger.debug("ROW: {}", row);

                            Label nodeName = new Label(subNode.getNodeName());
                            gridPane.add(nodeName, 0, row);

                            Label nodeValue = new Label(n.getNodeValue());
                            nodeValue.setOnMouseClicked(event -> {
                                logger.debug("Node Value: {}", nodeValue.getText());
                                TextField textField = new TextField(nodeValue.getText());
                                gridPane.getChildren().remove(nodeValue);
                                gridPane.add(textField, 1, finalRow);
                                textField.setOnKeyPressed(keyEvent -> {
                                    if (keyEvent.getCode() == KeyCode.ENTER) {
                                        logger.debug("NEW VALUE: {}", textField.getText());
                                        gridPane.getChildren().remove(textField);
                                        nodeValue.setText(textField.getText());
                                        gridPane.add(nodeValue, 1, finalRow);
                                        n.setNodeValue(textField.getText());
                                    }
                                });
                            });
                            gridPane.add(nodeValue, 1, row);
                            row++;
                        } else {
                            HBox box = new HBox();

                            Label label = new Label(subNode.getNodeName() + " - {" + calculateCount(subNode) + "}");
                            logger.debug("Element: {}", label.getText());

                            box.setOnMouseClicked(event -> {
                                logger.debug("Click Event: {}", event.getSource().toString());
                                logger.debug("Final Row: {}", finalRow);

                                gridPane.getChildren().remove(box);

                                HBox b = new HBox();
                                Label label2 = new Label("OPEN - " + subNode.getNodeName() + " - {" + calculateCount(subNode) + "}");
                                SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode);

                                b.getChildren().addAll(imageViewMinus, label2, simpleNodeElement);

                                b.setOnMouseClicked(event1 -> {
                                    b.getChildren().remove(b);
                                    gridPane.add(box, 1, finalRow);
                                });

                                gridPane.add(b, 1, finalRow);
                            });

                            box.getChildren().addAll(imageViewPlus, label);

                            gridPane.add(box, 1, row);
                            row++;
                        }
                    }
                    case Node.TEXT_NODE -> {
                        //this.getChildren().add(new Label("TEXT2: " + subNode.getNodeName() + ":" + subNode.getNodeValue()));
                    }

                    default -> this.getChildren().add(new Label("DEFAULT: " + subNode.getNodeName()));
                }
                this.getStyleClass().add("normalElement");
            }

            gridPane.getStyleClass().add("normalElement");
            this.getChildren().add(gridPane);
        }
    }


    private int calculateCount(Node n) {
        int ret = 0;
        for (int i = 0; i < n.getChildNodes().getLength(); i++) {
            if (n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE) {
                ret++;
            }
        }
        return ret;
    }
}
