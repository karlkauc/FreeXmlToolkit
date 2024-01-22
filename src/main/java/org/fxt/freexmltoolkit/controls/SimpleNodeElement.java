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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import java.util.Objects;

public class SimpleNodeElement extends VBox {
    Node node;

    final Image image = new Image(Objects.requireNonNull(getClass().getResource("/img/plus.png")).toString());
    final ImageView imageView = new ImageView(image);

    public SimpleNodeElement() {

    }

    public SimpleNodeElement(Node node) {
        this.node = node;
        createByNode(node);
    }

    private final static Logger logger = LogManager.getLogger(SimpleNodeElement.class);

    public void createByNode(Node node) {
        this.node = node;

        this.getChildren().add(new Label(node.getNodeName() + " {" + node.getChildNodes().getLength() + "}"));

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

                            gridPane.add(new Label(subNode.getNodeName()), 0, row);
                            gridPane.add(new Label(n.getNodeValue()), 1, row);
                            row++;
                        } else {
                            HBox box = new HBox();

                            imageView.setFitHeight(15);
                            imageView.setFitWidth(15);
                            imageView.setPreserveRatio(true);

                            Label label = new Label(subNode.getNodeName() + " - {" + calculateCount(subNode) + "}");

                            box.setOnMouseClicked(event -> {
                                // ((ImageView) this.getChildren().get(1)).setImage(new Image(Objects.requireNonNull(getClass().getResource("/img/minus.png")).toString()));
                                SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode);
                                gridPane.add(simpleNodeElement, 1, finalRow);
                            });

                            box.getChildren().addAll(imageView, label);

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
