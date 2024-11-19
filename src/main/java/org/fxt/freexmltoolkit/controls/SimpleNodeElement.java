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

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

    private final static Logger logger = LogManager.getLogger(SimpleNodeElement.class);

    Node node;

    final static Image imagePlus = new Image(Objects.requireNonNull(SimpleNodeElement.class.getResource("/img/plus_15.png")).toString());
    final static Image imageMinus = new Image(Objects.requireNonNull(SimpleNodeElement.class.getResource("/img/minus_15.png")).toString());

    XmlEditor xmlEditor;

    public SimpleNodeElement(Node node, XmlEditor caller) {
        this.node = node;
        this.xmlEditor = caller;
        createByNode(node);
    }

    public void createByNode(Node node) {

        this.getStyleClass().add("treeGrid");

        if (node.hasChildNodes()) {
            if (node.getAttributes() != null && node.getAttributes().getLength() != 0) {
                logger.debug("Attributes: {}", node.getAttributes().getLength());

                GridPane gridPane = new GridPane();
                gridPane.getStyleClass().add("treeGrid");
                int row = 0;

                for (int i = 0; i < node.getAttributes().getLength(); i++) {
                    var attributes = node.getAttributes().item(i);
                    logger.debug("{}:{}", attributes.getNodeName(), attributes.getNodeValue());

                    gridPane.add(new Label(attributes.getNodeName()), 0, row);
                    gridPane.add(new Label(attributes.getNodeValue()), 1, row);

                    row++;
                }
                this.getChildren().add(gridPane);
            }

            for (int childNodeIndex = 0; childNodeIndex < node.getChildNodes().getLength(); childNodeIndex++) {
                var subNode = node.getChildNodes().item(childNodeIndex);
                // logger.debug("Node Type: {}", subNode.getNodeType());

                switch (subNode.getNodeType()) {
                    case Node.COMMENT_NODE -> {
                        final Label l = new Label("COMMENT: " + subNode.getNodeValue());
                        l.getStyleClass().add("xmlTreeComment");
                        this.getChildren().add(l);
                        // DEBUG - nachher wieder hereingeben
                    }
                    case Node.ELEMENT_NODE -> {
                        if (subNode.getChildNodes().getLength() == 1 &&
                                subNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
                            var n = subNode.getChildNodes().item(0);

                            logger.debug("adding element text node: {}", subNode.getNodeName() + ":" + n.getNodeValue());

                            var nodeName = new Label(subNode.getNodeName());
                            var nodeValue = new Label(n.getNodeValue());
                            nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, n));

                            GridPane gridPane = new GridPane();
                            gridPane.getStyleClass().add("xmlTreeText");
                            int row = 0;

                            if (subNode.hasAttributes()) {
                                for (int attributeIndex = 0; attributeIndex < subNode.getAttributes().getLength(); attributeIndex++) {
                                    var attributes = subNode.getAttributes().item(attributeIndex);

                                    VBox nodeNameBox = new VBox();
                                    var attributeBox = new HBox();
                                    HBox.setMargin(attributeBox, new Insets(5, 5, 5, 5));
                                    attributeBox.setStyle("-fx-background-color: #7272e3; -fx-text-fill: #eeeef8; -fx-padding: 2px;");
                                    attributeBox.getChildren().add(new Label("@"));
                                    attributeBox.getChildren().add(new Label(attributes.getNodeName()));
                                    nodeNameBox.getChildren().add(attributeBox);

                                    VBox nodeValueBox = new VBox();
                                    nodeValueBox.getChildren().add(new Label(attributes.getNodeValue()));
                                    nodeValueBox.setAlignment(Pos.CENTER_RIGHT);
                                    nodeValueBox.setStyle("-fx-background-color: yellow");

                                    gridPane.add(nodeNameBox, 0, row);
                                    gridPane.add(nodeValueBox, 1, row);
                                    row++;
                                }
                            }

                            var nodeNameBox = new VBox(nodeName);
                            nodeNameBox.setStyle("-fx-padding: 2px;");
                            var nodeValueBox = new VBox(nodeValue);
                            nodeValueBox.setStyle("-fx-padding: 2px;");
                            nodeValueBox.setAlignment(Pos.CENTER_RIGHT);

                            gridPane.add(nodeNameBox, 0, row);
                            gridPane.add(nodeValueBox, 1, row);

                            this.getChildren().add(gridPane);
                        } else {
                            HBox elementBox = new HBox();
                            elementBox.setSpacing(3);
                            HBox.setMargin(elementBox, new Insets(3, 5, 3, 5));

                            final Label label = new Label(subNode.getNodeName() + " - {" + calculateNodeCount(subNode) + "}");
                            logger.debug("Element: {}", label.getText());

                            var btnPlus = new Button("", new ImageView(imagePlus));
                            // btnPlus.setStyle("-fx-background-color: transparent; -fx-border-color: none; -fx-padding: 0;");
                            btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));
                            elementBox.getChildren().addAll(btnPlus, label);
                            this.getChildren().add(elementBox);
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
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValue, Node n) {
        return event -> {
            logger.debug("Node Value: {}", nodeValue.getText());


            TextField textField = new TextField(nodeValue.getText());
            textField.setStyle("-fx-border-radius: 2px; -fx-background-color: #f1c4c4;");
            textField.setOnKeyPressed(keyEvent -> {
                if (keyEvent.getCode() == KeyCode.ENTER) {
                    logger.debug("NEW VALUE: {}", textField.getText());
                    nodeValue.setText(textField.getText());
                    n.setNodeValue(textField.getText());
                    this.xmlEditor.refreshTextView();
                }
                if (keyEvent.getCode() == KeyCode.ESCAPE) {
                    logger.debug("ESC Pressed");
                }
            });
        };
    }

    @NotNull
    private EventHandler<ActionEvent> mouseOpenHandler(HBox elementBox, Node subNode, Boolean isOpen) {
        return event -> {
            logger.debug("Open Clicked: {} - isOpen: {}", subNode.getNodeName(), isOpen);

            if (isOpen) {
                logger.debug("OPEN Pressed");

                SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode, xmlEditor);
                HBox hbox = new HBox();

                Button btnMinus = new Button("", new ImageView(imageMinus));
                btnMinus.setOnAction(mouseOpenHandler(elementBox, subNode, false));

                var t = elementBox.getChildren().get(1);
                elementBox.getChildren().clear();
                elementBox.getChildren().addAll(btnMinus, t);

                hbox.getChildren().addAll(simpleNodeElement);
                elementBox.getChildren().add(hbox);
            } else {
                logger.debug("CLOSE Pressed");

                Button btnPlus = new Button("", new ImageView(imagePlus));
                btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, false));

                var t = elementBox.getChildren().get(1);
                elementBox.getChildren().clear();
                elementBox.getChildren().addAll(btnPlus, t);

                btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));
            }
        };
    }

    private static int calculateNodeCount(Node n) {
        return (int) IntStream
                .range(0, n.getChildNodes().getLength())
                .filter(i -> n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
                .count();
    }
}
