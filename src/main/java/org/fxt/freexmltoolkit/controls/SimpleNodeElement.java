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
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

public class SimpleNodeElement extends VBox {

    Node node;

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
        this.setStyle("-fx-start-margin: 10; -fx-border-color: black; -fx-border-style: solid; -fx-border-width: 2px; -fx-font-size: 1.1em;");

        int row = 0;
        int col = 0;

        GridPane gridPane = new GridPane();
        gridPane.setGridLinesVisible(true);

        if (node.getAttributes() != null) {
            logger.debug("Attributes: {}", node.getAttributes().getLength());

            for (int i = 0; i < node.getAttributes().getLength(); i++) {
                var attributes = node.getAttributes().item(i);
                logger.debug(attributes.getNodeName() + ":" + attributes.getNodeValue());
                gridPane.add(new Label(attributes.getNodeName()), 0, row);
                gridPane.add(new Label(attributes.getNodeValue()), 1, row);
                row++;
            }
            gridPane.getStyleClass().add("normalElement");
            this.getChildren().add(gridPane);
        }


        this.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                if (event.getClickCount() == 1) {
                    for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                        var subNode = node.getChildNodes().item(i);
                        logger.debug("Node Type: {}", subNode.getNodeType());

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
                                    this.getChildren().add(new Label(subNode.getNodeName() + ":" + n.getNodeValue()));
                                } else {
                                    SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode);
                                    this.getChildren().add(simpleNodeElement);
                                }
                            }
                            case Node.TEXT_NODE -> {
                                //this.getChildren().add(new Label("TEXT2: " + subNode.getNodeName() + ":" + subNode.getNodeValue()));
                            }

                            default -> this.getChildren().add(new Label("DEFAULT: " + subNode.getNodeName()));
                        }
                        this.getStyleClass().add("normalElement");
                    }
                }

                if (event.getClickCount() == 2) {
                    logger.debug("Double clicked: {}", this.node.getNodeName());
                }
            }
        });
        this.getStyleClass().add("rootElement");
        this.applyCss();
    }
}
