package org.fxt.freexmltoolkit.controls;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.*;
import java.util.stream.IntStream;

/**
 * A custom VBox implementation for displaying XML nodes in a tree structure.
 * Styles are managed via an external CSS file.
 */
public class SimpleNodeElement extends VBox {

    private static final Logger logger = LogManager.getLogger(SimpleNodeElement.class);
    // Resourcen werden nur einmal geladen
    private static final Image imagePlus = new Image(Objects.requireNonNull(SimpleNodeElement.class.getResource("/img/plus_15.png")).toString());
    private static final Image imageMinus = new Image(Objects.requireNonNull(SimpleNodeElement.class.getResource("/img/minus_15.png")).toString());

    private final Node node;
    private final XmlEditor xmlEditor;

    public SimpleNodeElement(Node node, XmlEditor caller) {
        this.node = node;
        this.xmlEditor = caller;
        // NEU: Dem Wurzelelement wird eine CSS-Klasse für gezieltes Styling zugewiesen.
        this.getStyleClass().add("simple-node-element");
        createByNode(node);
    }

    public void createByNode(Node node) {
        this.getStyleClass().add("tree-grid");

        if (node.hasChildNodes()) {
            addAttributes(node);
            addChildNodes(node);
        }
    }

    private void addAttributes(Node node) {
        if (node.getAttributes() != null && node.getAttributes().getLength() != 0) {
            GridPane gridPane = new GridPane();
            gridPane.getStyleClass().add("tree-grid");

            for (int i = 0; i < node.getAttributes().getLength(); i++) {
                var attributes = node.getAttributes().item(i);
                gridPane.add(new Label(attributes.getNodeName()), 0, i);
                gridPane.add(new Label(attributes.getNodeValue()), 1, i);
            }
            this.getChildren().add(gridPane);
        }
    }

    private void addChildNodes(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            var subNode = node.getChildNodes().item(i);

            switch (subNode.getNodeType()) {
                case Node.COMMENT_NODE -> addCommentNode(subNode);
                case Node.ELEMENT_NODE -> addElementNode(subNode);
                case Node.TEXT_NODE -> {
                } // No action needed
                default -> this.getChildren().add(new Label("DEFAULT: " + subNode.getNodeName()));
            }
        }
        this.getStyleClass().add("normal-element");
    }

    private void addCommentNode(Node subNode) {
        Label label = new Label("COMMENT: " + subNode.getNodeValue());
        label.getStyleClass().add("xml-tree-comment");
        this.getChildren().add(label);
    }

    private void addElementNode(Node subNode) {
        if (subNode.getChildNodes().getLength() == 1 && subNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            addTextNode(subNode);
        } else {
            addComplexNode(subNode);
        }
    }

    private void addTextNode(Node subNode) {
        var firstItem = subNode.getChildNodes().item(0);
        var nodeName = new Label(subNode.getNodeName());
        var nodeValue = new Label(firstItem.getNodeValue());

        nodeName.setWrapText(true);
        nodeValue.setWrapText(true);

        nodeName.setTooltip(new Tooltip(subNode.getNodeName()));
        nodeValue.setTooltip(new Tooltip(firstItem.getNodeValue()));

        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, firstItem));

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("xml-tree-text");

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(50);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(50);
        gridPane.getColumnConstraints().addAll(column1, column2);

        addAttributesToGridPane(subNode, gridPane);

        var nodeNameBox = new HBox(nodeName);
        nodeNameBox.getStyleClass().add("node-name-box");
        HBox.setHgrow(nodeNameBox, Priority.ALWAYS);

        var nodeValueBox = new HBox(nodeValue);
        nodeValueBox.getStyleClass().add("node-value-box");
        HBox.setHgrow(nodeValueBox, Priority.ALWAYS);

        final int row = gridPane.getRowCount();
        gridPane.add(nodeNameBox, 0, row);
        gridPane.add(nodeValueBox, 1, row);

        this.getChildren().add(gridPane);
    }

    private void addAttributesToGridPane(Node subNode, GridPane gridPane) {
        if (subNode.hasAttributes()) {
            for (int i = 0; i < subNode.getAttributes().getLength(); i++) {
                var attributes = subNode.getAttributes().item(i);

                var attributeBox = new HBox(new Label("@"), new Label(attributes.getNodeName()));
                HBox.setMargin(attributeBox, new Insets(2, 5, 2, 5));
                attributeBox.getStyleClass().add("attribute-box");

                var nodeValueBox = new HBox(new Label(attributes.getNodeValue()));
                nodeValueBox.getStyleClass().add("attribute-value-box");

                gridPane.add(new HBox(attributeBox), 0, i);
                gridPane.add(nodeValueBox, 1, i);
            }
        }
    }

    private void addComplexNode(Node subNode) {
        var elementBox = new HBox(3);
        elementBox.getStyleClass().add("element-box");

        var btnPlus = new Button("", new ImageView(imagePlus));
        btnPlus.getStyleClass().add("tree-toggle-button");

        final Label label = new Label(subNode.getNodeName() + " - {" + calculateNodeCount(subNode) + "}");

        HBox.setHgrow(label, Priority.ALWAYS);
        label.setAlignment(Pos.CENTER);

        btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));
        elementBox.getChildren().addAll(btnPlus, label);
        this.getChildren().add(elementBox);
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValue, Node node) {
        return event -> {
            try {
                final String originalValue = nodeValue.getText();
                HBox parent = (HBox) nodeValue.getParent();

                TextField textField = new TextField(nodeValue.getText());
                textField.setOnKeyPressed(keyEvent -> handleKeyPress(keyEvent, textField, nodeValue, node, originalValue));

                parent.getChildren().remove(nodeValue);
                parent.getChildren().add(textField);
                textField.requestFocus();

            } catch (ClassCastException e) {
                logger.error("Error while creating edit textfield for node value: {}", nodeValue.getText(), e);
            }
        };
    }

    private void handleKeyPress(KeyEvent keyEvent, TextField textField, Label nodeValue, Node node, String originalValue) {
        HBox parent = (HBox) textField.getParent();
        if (keyEvent.getCode() == KeyCode.ENTER) {
            nodeValue.setText(textField.getText());
            node.setNodeValue(textField.getText());
            parent.getChildren().remove(textField);
            parent.getChildren().add(nodeValue); // Das Original-Label wieder hinzufügen
            this.xmlEditor.refreshTextView();
        }
        if (keyEvent.getCode() == KeyCode.ESCAPE) {
            parent.getChildren().remove(textField);
            nodeValue.setText(originalValue); // Text des Labels zurücksetzen
            parent.getChildren().add(nodeValue); // Das Original-Label wieder hinzufügen
        }
    }

    @NotNull
    private EventHandler<ActionEvent> mouseOpenHandler(HBox elementBox, Node subNode, Boolean isOpen) {
        return event -> {
            if (isOpen) {
                openNode(elementBox, subNode);
            } else {
                closeNode(elementBox, subNode);
            }
        };
    }

    private void openNode(HBox elementBox, Node subNode) {
        SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode, xmlEditor);
        HBox contentHbox = new HBox();

        Button btnMinus = new Button("", new ImageView(imageMinus));
        btnMinus.getStyleClass().add("tree-toggle-button");
        btnMinus.setOnAction(mouseOpenHandler(elementBox, subNode, false));

        if (shouldBeTable(subNode)) {
            GridPane gridPane = createTable(subNode);
            contentHbox.getChildren().add(gridPane);
        } else {
            contentHbox.getChildren().add(simpleNodeElement);
        }

        var label = elementBox.getChildren().get(1);
        elementBox.getChildren().clear();
        elementBox.getChildren().addAll(btnMinus, label, contentHbox);
    }

    private void closeNode(HBox elementBox, Node subNode) {
        var label = elementBox.getChildren().get(1);
        Button btnPlus = new Button("", new ImageView(imagePlus));
        btnPlus.getStyleClass().add("tree-toggle-button");

        btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));
        elementBox.getChildren().clear();
        elementBox.getChildren().addAll(btnPlus, label);
    }

    private GridPane createTable(Node subNode) {
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("tree-grid");
        int row = 1;
        Map<String, Integer> columns = new HashMap<>();

        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);
            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                addTableRow(gridPane, oneRow, row, columns);
                row++;
            }
        }
        return gridPane;
    }

    private void addTableRow(GridPane gridPane, Node oneRow, int row, Map<String, Integer> columns) {
        var textContent = new Label(oneRow.getNodeName() + "# [" + row + "]");
        Pane rowCountPane = getCenterPaneWithLabel(textContent);
        rowCountPane.getStyleClass().add("element-box");
        gridPane.add(rowCountPane, 0, row);

        for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
            Node oneNode = oneRow.getChildNodes().item(x);
            if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                addTableCell(gridPane, oneNode, row, columns);
            }
        }
    }

    // VEREINFACHT: Die gesamte Hover-Logik in Java wurde entfernt.
    // CSS übernimmt das jetzt mit :hover.

    private void addTableCell(GridPane gridPane, Node oneNode, int row, Map<String, Integer> columns) {
        var nodeName = oneNode.getNodeName();
        int colPos = columns.computeIfAbsent(nodeName, k -> columns.size() + 1);

        // Header nur einmal hinzufügen
        if (gridPane.getChildren().stream().noneMatch(node -> GridPane.getColumnIndex(node) == colPos && GridPane.getRowIndex(node) == 0)) {
            var headerLabel = new Label(nodeName);
            Pane headerPane = getCenterPaneWithLabel(headerLabel);
            headerPane.getStyleClass().add("table-header");
            gridPane.add(headerPane, colPos, 0);
        }

        Pane cellPane;
        if (oneNode.getChildNodes().getLength() == 1 && oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            var contentLabel = new Label(oneNode.getTextContent());
            contentLabel.setOnMouseClicked(editNodeValueHandler(contentLabel, oneNode.getChildNodes().item(0)));
            cellPane = getCenterPaneWithLabel(contentLabel);
        } else {
            cellPane = new Pane(new SimpleNodeElement(oneNode, xmlEditor));
        }
        cellPane.getStyleClass().add("table-cell");
        gridPane.add(cellPane, colPos, row);
    }

    public Pane getCenterPaneWithLabel(Label label) {
        Pane pane = new Pane(label);
        label.layoutXProperty().bind(pane.widthProperty().subtract(label.widthProperty()).divide(2));
        label.layoutYProperty().bind(pane.heightProperty().subtract(label.heightProperty()).divide(2));
        return pane;
    }

    private static int calculateNodeCount(Node n) {
        return (int) IntStream.range(0, n.getChildNodes().getLength())
                .filter(i -> n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
                .count();
    }

    private static boolean shouldBeTable(Node n) {
        Set<String> nodeNames = new HashSet<>();
        int elementNodeCount = 0;
        for (int i = 0; i < n.getChildNodes().getLength(); i++) {
            Node child = n.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                nodeNames.add(child.getNodeName());
                elementNodeCount++;
            }
        }
        return elementNodeCount > 1 && nodeNames.size() == 1;
    }
}