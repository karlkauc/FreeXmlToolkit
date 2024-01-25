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

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.Objects;
import java.util.stream.IntStream;

public class SimpleNodeElement extends VBox {
    Node node;

    final Image imagePlus = new Image(Objects.requireNonNull(getClass().getResource("/img/plus_15.png")).toString());
    final Image imageMinus = new Image(Objects.requireNonNull(getClass().getResource("/img/minus_15.png")).toString());

    public SimpleNodeElement() {

    }

    public SimpleNodeElement(Node node) {
        this.node = node;
        createByNode(node);
    }

    private final static Logger logger = LogManager.getLogger(SimpleNodeElement.class);

    public void createByNode(Node node) {
        int row = 0;
        int col = 0;

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("treeGrid");

        if (node.hasChildNodes()) {

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
                // logger.debug("Node Type: {}", subNode.getNodeType());

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
                            nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, gridPane, finalRow, n));
                            gridPane.add(nodeValue, 1, row);
                            row++;
                        } else {
                            HBox elementBox = new HBox();
                            Label label = new Label(subNode.getNodeName() + " - {" + calculateCount(subNode) + "}");
                            logger.debug("Element: {}", label.getText());
                            final ImageView imageViewPlus = new ImageView(imagePlus);

                            elementBox.getChildren().addAll(imageViewPlus, label);
                            elementBox.setOnMouseClicked(mouseOpenHandler(finalRow, gridPane, elementBox, subNode));

                            gridPane.add(elementBox, 1, row);
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

    @NotNull
    private static EventHandler<MouseEvent> editNodeValueHandler(Label nodeValue, GridPane gridPane, int finalRow, Node n) {
        return event -> {
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
        };
    }

    @NotNull
    private EventHandler<MouseEvent> mouseOpenHandler(int finalRow, GridPane gridPane, HBox box, Node subNode) {
        return event -> {
            // logger.debug("Click Event: {}", event.getSource().toString());
            logger.debug("Final Row: {}", finalRow);
            gridPane.getChildren().remove(box);

            HBox wrapperOpen = new HBox();
            HBox openBox = new HBox();
            Label label2 = new Label("OPEN - " + subNode.getNodeName() + " - {" + SimpleNodeElement.this.calculateCount(subNode) + "}");
            SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode);

            final ImageView imageViewMinus = new ImageView(imageMinus);
            openBox.getChildren().addAll(imageViewMinus, label2);

            openBox.setOnMouseClicked(event1 -> {
                // logger.debug("Click Event - open Box");
                // wieder orginal aufklappen einhängen
                wrapperOpen.getChildren().removeAll(simpleNodeElement);
                openBox.setOnMouseClicked(mouseOpenHandler(finalRow, gridPane, box, subNode));
            });
            wrapperOpen.getChildren().addAll(openBox, simpleNodeElement);

            gridPane.add(wrapperOpen, 1, finalRow);
        };
    }


    private int calculateCount(Node n) {
        return (int) IntStream
                .range(0, n.getChildNodes().getLength())
                .filter(i -> n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
                .count();
    }
}
