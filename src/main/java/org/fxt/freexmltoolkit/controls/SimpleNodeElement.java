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
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.*;
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

                            ColumnConstraints column1 = new ColumnConstraints();
                            ColumnConstraints column2 = new ColumnConstraints();
                            column1.setPercentWidth(50);
                            column2.setPercentWidth(50);
                            column1.setHgrow(Priority.ALWAYS);
                            column2.setHgrow(Priority.ALWAYS);
                            gridPane.getColumnConstraints().addAll(column1, column2);

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

                            var nodeNameBox = new HBox(nodeName);
                            nodeNameBox.setStyle("-fx-padding: 2px;");
                            HBox.setHgrow(nodeNameBox, Priority.ALWAYS);

                            var nodeValueBox = new HBox(nodeValue);
                            nodeValueBox.setStyle("-fx-padding: 2px;");
                            nodeValueBox.setAlignment(Pos.CENTER_LEFT);
                            nodeValueBox.setStyle("-fx-background-color: #d2f4e5");
                            HBox.setHgrow(nodeValueBox, Priority.ALWAYS);

                            gridPane.add(nodeNameBox, 0, row);
                            gridPane.add(nodeValueBox, 1, row);

                            this.getChildren().add(gridPane);
                        } else {
                            var elementBox = new HBox();
                            elementBox.setSpacing(3);
                            HBox.setMargin(elementBox, new Insets(3, 5, 3, 5));

                            final Label label = new Label(subNode.getNodeName() + " - {" + calculateNodeCount(subNode) + "}");
                            logger.debug("Element: {}", label.getText());

                            var btnPlus = new Button("", new ImageView(imagePlus));
                            btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));
                            elementBox.getChildren().addAll(btnPlus, label);
                            this.getChildren().add(elementBox);
                        }
                    }
                    case Node.TEXT_NODE -> {
                        // this.getChildren().add(new Label("TEXT2: " + subNode.getNodeName() + ":" + subNode.getTextContent()));
                    }

                    default -> this.getChildren().add(new Label("DEFAULT: " + subNode.getNodeName()));
                }
                this.getStyleClass().add("normalElement");
            }
        }
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValue, Node node) {
        return event -> {
            logger.debug("Node Value: {}", nodeValue.getText());

            try {
                final String originalValue = nodeValue.getText();
                HBox parent = null;
                if (nodeValue.getParent() instanceof HBox hBox) {
                    parent = hBox;
                } else {
                    logger.debug("not HBOX: {}", nodeValue.getParent());
                }

                TextField textField = new TextField(nodeValue.getText());
                textField.setStyle("-fx-border-radius: 2px; -fx-background-color: #f1c4c4;");
                textField.setOnKeyPressed(keyEvent -> {

                    HBox parentNew;
                    if (keyEvent.getCode() == KeyCode.ENTER) {
                        logger.debug("NEW VALUE: {}", textField.getText());
                        nodeValue.setText(textField.getText());
                        node.setNodeValue(textField.getText());

                        parentNew = (HBox) textField.getParent();
                        parentNew.getChildren().remove(textField);
                        parentNew.getChildren().add(new Label(textField.getText()));
                        parentNew.requestLayout();

                        this.xmlEditor.refreshTextView();
                    }
                    if (keyEvent.getCode() == KeyCode.ESCAPE) {
                        logger.debug("ESC Pressed");

                        parentNew = (HBox) textField.getParent();
                        parentNew.getChildren().remove(textField);
                        parentNew.getChildren().add(new Label(originalValue));
                    }

                    this.setOnMouseClicked(editNodeValueHandler(nodeValue, node));

                });
                if (parent != null) {
                    parent.getChildren().remove(nodeValue);
                    parent.getChildren().add(textField);
                }

            } catch (ClassCastException e) {
                logger.error("Node Value: {}", nodeValue.getText());
                logger.error("Error: {}", e.getMessage());
            }

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

                boolean shouldBeTable = shouldBeTable(subNode);
                logger.debug("shouldBeTable: {}", shouldBeTable);

                if (shouldBeTable) {
                    GridPane gridPane = createTable(elementBox, subNode, btnMinus);

                    hbox.getChildren().addAll(gridPane);
                    elementBox.getChildren().add(hbox);

                } else {
                    var t = elementBox.getChildren().get(1);
                    elementBox.getChildren().clear();
                    elementBox.getChildren().addAll(btnMinus, t);

                    hbox.getChildren().addAll(simpleNodeElement);
                    elementBox.getChildren().add(hbox);
                }
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

    private GridPane createTable(HBox elementBox, Node subNode, Button btnMinus) {
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("treeGrid");
        int row = 1;
        Map<String, Integer> columns = new HashMap<>();

        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);

            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                var textContent = new Label(oneRow.getNodeName() + "# [" + row + "]");
                Pane rowCount = getCenterPaneWithLabel(textContent);
                rowCount.setStyle("-fx-background-color: #f1d239;");

                int finalRow = row;
                rowCount.setOnMouseEntered(event -> {
                    for (int c = 0; c < gridPane.getColumnCount() - 1; c++) {
                        var node = getNodeFromGridPane(gridPane, c + 1, finalRow);
                        if (node != null) {
                            node.setStyle("-fx-background-color: #0a53be");
                        }
                    }
                });
                rowCount.setOnMouseExited(event -> {
                    for (int c = 0; c < gridPane.getColumnCount() - 1; c++) {
                        var node = getNodeFromGridPane(gridPane, c + 1, finalRow);
                        if (node != null) {
                            node.setStyle("-fx-background-color: white");
                        }
                    }
                });

                gridPane.add(rowCount, 0, row);


                for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
                    Node oneNode = oneRow.getChildNodes().item(x);

                    if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                        var nodeName = oneNode.getNodeName();
                        int colPos = 1;
                        if (columns.containsKey(nodeName)) {
                            colPos = columns.get(nodeName);
                        } else {
                            colPos = columns.size() + 1;
                            columns.put(nodeName, colPos);
                            var nn = new Label(nodeName);
                            nn.setOnMouseClicked(editNodeValueHandler(nn, oneNode));
                            Pane textPane = getCenterPaneWithLabel(nn);

                            textPane.setStyle("-fx-background-color: #fae88d; -fx-font-weight: bold;");
                            gridPane.add(textPane, colPos, 0);

                            int finalColPos = colPos;
                            textPane.setOnMouseEntered(event -> {
                                for (int r = 0; r < gridPane.getRowCount() - 1; r++) {
                                    var node = getNodeFromGridPane(gridPane, finalColPos, r + 1);
                                    if (node != null) {
                                        node.setStyle("-fx-background-color: #0a53be");
                                    }
                                }
                            });
                            textPane.setOnMouseExited(event -> {
                                for (int r = 0; r < gridPane.getRowCount() - 1; r++) {
                                    var node = getNodeFromGridPane(gridPane, finalColPos, r + 1);
                                    if (node != null) {
                                        node.setStyle("-fx-background-color: white");
                                    }
                                }
                            });
                        }
                        if (oneNode.getChildNodes().getLength() == 1 &&
                                oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
                            var n = new Label(oneNode.getTextContent());
                            var t = getCenterPaneWithLabel(n);
                            t.setOnMouseClicked(editNodeValueHandler(n, oneNode));
                            gridPane.add(t, colPos, row);
                        } else {
                            gridPane.add(new SimpleNodeElement(oneNode, xmlEditor), colPos, row);
                        }
                    }
                }
                row++;
            }
        }
        var t = elementBox.getChildren().get(1);
        elementBox.getChildren().clear();
        elementBox.getChildren().addAll(btnMinus, t);

        return gridPane;
    }

    private javafx.scene.Node getNodeFromGridPane(GridPane gridPane, int col, int row) {
        final var children = gridPane.getChildren();
        for (final var node : children) {
            Integer columnIndex = GridPane.getColumnIndex(node);
            Integer rowIndex = GridPane.getRowIndex(node);

            if (columnIndex == null)
                columnIndex = 0;
            if (rowIndex == null)
                rowIndex = 0;

            if (columnIndex == col && rowIndex == row) {
                logger.debug("columnIndex: {}, rowIndex: {}", columnIndex, rowIndex);
                return node;
            }
        }
        return null;
    }


    public Pane getCenterPaneWithLabel(Label label) {
        Pane pane = new Pane(label);
        label.layoutXProperty().bind(pane.widthProperty().subtract(label.widthProperty()).divide(2));
        label.layoutYProperty().bind(pane.heightProperty().subtract(label.heightProperty()).divide(2));

        return pane;
    }

    private static int calculateNodeCount(Node n) {
        return (int) IntStream
                .range(0, n.getChildNodes().getLength())
                .filter(i -> n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
                .count();
    }

    private static boolean shouldBeTable(Node n) {
        Set<String> nodeNames = new HashSet<>();
        int count = 0;
        for (int i = 0; i < n.getChildNodes().getLength(); i++) {
            if (n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE) {
                nodeNames.add(n.getChildNodes().item(i).getNodeName());
                if (nodeNames.size() > 1) {
                    return false;
                }
                count++;
            }
        }
        logger.debug("Node Names: {}", nodeNames);
        if (count == 1) {
            return false;
        }
        return nodeNames.size() == 1;
    }
}
