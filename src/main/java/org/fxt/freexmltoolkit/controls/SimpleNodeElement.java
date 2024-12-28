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
 */
public class SimpleNodeElement extends VBox {

    private static final Logger logger = LogManager.getLogger(SimpleNodeElement.class);
    private static final Image imagePlus = new Image(Objects.requireNonNull(SimpleNodeElement.class.getResource("/img/plus_15.png")).toString());
    private static final Image imageMinus = new Image(Objects.requireNonNull(SimpleNodeElement.class.getResource("/img/minus_15.png")).toString());

    // CSS style constants
    private static final String TREE_GRID_STYLE = "treeGrid";
    private static final String NORMAL_ELEMENT_STYLE = "normalElement";
    private static final String XML_TREE_COMMENT_STYLE = "xmlTreeComment";
    private static final String XML_TREE_TEXT_STYLE = "xmlTreeText";
    private static final String PADDING_STYLE = "-fx-padding: 2px;";
    private static final String BACKGROUND_COLOR_GREEN_STYLE = "-fx-padding: 2px; -fx-background-color: #d2f4e5";
    private static final String ATTRIBUTE_BOX_STYLE = "-fx-background-color: #c9c9ec; -fx-text-fill: #eeeef8; -fx-padding: 2px;";
    private static final String ATTRIBUTE_VALUE_BOX_STYLE = "-fx-background-color: #f6f691";
    private static final String ELEMENT_BOX_STYLE = "-fx-background-color: #f1d239;";
    private static final String TEXT_FIELD_STYLE = "-fx-border-radius: 2px; -fx-background-color: #f1c4c4;";
    private static final String TABLE_HEADER_STYLE = "-fx-background-color: #fae88d; -fx-font-weight: bold;";
    private static final String HOVER_STYLE = "-fx-background-color: #0a53be";
    private static final String DEFAULT_STYLE = "-fx-background-color: white";

    private final Node node;
    private final XmlEditor xmlEditor;

    /**
     * Constructs a SimpleNodeElement for the given XML node.
     *
     * @param node   the XML node represented by this element
     * @param caller the XmlEditor instance that created this element
     */
    public SimpleNodeElement(Node node, XmlEditor caller) {
        this.node = node;
        this.xmlEditor = caller;
        createByNode(node);
    }

    /**
     * Creates the visual representation of the XML node.
     *
     * @param node the XML node to be represented
     */
    public void createByNode(Node node) {
        this.getStyleClass().add(TREE_GRID_STYLE);

        if (node.hasChildNodes()) {
            addAttributes(node);
            addChildNodes(node);
        }
    }

    /**
     * Adds the attributes of the XML node to the visual representation.
     *
     * @param node the XML node whose attributes are to be added
     */
    private void addAttributes(Node node) {
        if (node.getAttributes() != null && node.getAttributes().getLength() != 0) {
            logger.debug("Attributes: {}", node.getAttributes().getLength());

            GridPane gridPane = new GridPane();
            gridPane.getStyleClass().add(TREE_GRID_STYLE);

            for (int i = 0; i < node.getAttributes().getLength(); i++) {
                var attributes = node.getAttributes().item(i);
                logger.debug("{}:{}", attributes.getNodeName(), attributes.getNodeValue());

                gridPane.add(new Label(attributes.getNodeName()), 0, i);
                gridPane.add(new Label(attributes.getNodeValue()), 1, i);
            }
            this.getChildren().add(gridPane);
        }
    }

    /**
     * Adds the child nodes of the XML node to the visual representation.
     *
     * @param node the XML node whose child nodes are to be added
     */
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
        this.getStyleClass().add(NORMAL_ELEMENT_STYLE);
    }

    /**
     * Adds a comment node to the visual representation.
     *
     * @param subNode the comment node to be added
     */
    private void addCommentNode(Node subNode) {
        Label label = new Label("COMMENT: " + subNode.getNodeValue());
        label.getStyleClass().add(XML_TREE_COMMENT_STYLE);
        this.getChildren().add(label);
    }

    /**
     * Adds an element node to the visual representation.
     *
     * @param subNode the element node to be added
     */
    private void addElementNode(Node subNode) {
        if (subNode.getChildNodes().getLength() == 1 && subNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            addTextNode(subNode);
        } else {
            addComplexNode(subNode);
        }
    }

    /**
     * Adds a text node to the visual representation.
     *
     * @param subNode the text node to be added
     */
    private void addTextNode(Node subNode) {
        var firstItem = subNode.getChildNodes().item(0);
        logger.debug("adding element text node: {}", subNode.getNodeName() + ":" + firstItem.getNodeValue());

        var nodeName = new Label(subNode.getNodeName());
        var nodeValue = new Label(firstItem.getNodeValue());
        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, firstItem));

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add(XML_TREE_TEXT_STYLE);

        ColumnConstraints column1 = new ColumnConstraints();
        ColumnConstraints column2 = new ColumnConstraints();
        column1.setPercentWidth(50);
        column2.setPercentWidth(50);
        column1.setHgrow(Priority.ALWAYS);
        column2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(column1, column2);

        addAttributesToGridPane(subNode, gridPane);

        var nodeNameBox = new HBox(nodeName);
        nodeNameBox.setStyle(PADDING_STYLE);
        HBox.setHgrow(nodeNameBox, Priority.ALWAYS);

        var nodeValueBox = new HBox(nodeValue);
        nodeValueBox.setStyle(BACKGROUND_COLOR_GREEN_STYLE);
        nodeValueBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nodeValueBox, Priority.ALWAYS);

        final int row = gridPane.getRowCount();
        gridPane.add(nodeNameBox, 0, row);
        gridPane.add(nodeValueBox, 1, row);

        this.getChildren().add(gridPane);
    }

    /**
     * Adds the attributes of a sub-node to the specified GridPane.
     *
     * @param subNode  the sub-node whose attributes are to be added
     * @param gridPane the GridPane to which the attributes are to be added
     */
    private void addAttributesToGridPane(Node subNode, GridPane gridPane) {
        if (subNode.hasAttributes()) {
            for (int i = 0; i < subNode.getAttributes().getLength(); i++) {
                var attributes = subNode.getAttributes().item(i);

                VBox nodeNameBox = new VBox();
                var attributeBox = new HBox();
                HBox.setMargin(attributeBox, new Insets(5, 5, 5, 5));
                attributeBox.setStyle(ATTRIBUTE_BOX_STYLE);
                attributeBox.getChildren().add(new Label("@"));
                attributeBox.getChildren().add(new Label(attributes.getNodeName()));
                nodeNameBox.getChildren().add(attributeBox);

                VBox nodeValueBox = new VBox();
                nodeValueBox.getChildren().add(new Label(attributes.getNodeValue()));
                nodeValueBox.setAlignment(Pos.CENTER_RIGHT);
                nodeValueBox.setStyle(ATTRIBUTE_VALUE_BOX_STYLE);

                gridPane.add(nodeNameBox, 0, i);
                gridPane.add(nodeValueBox, 1, i);
            }
        }
    }

    /**
     * Adds a complex node to the visual representation.
     *
     * @param subNode the complex node to be added
     */
    private void addComplexNode(Node subNode) {
        var elementBox = new HBox(3);
        HBox.setMargin(elementBox, new Insets(3, 5, 3, 5));

        final Label label = new Label(subNode.getNodeName() + " - {" + calculateNodeCount(subNode) + "}");
        logger.debug("Element: {}", label.getText());

        var btnPlus = new Button("", new ImageView(imagePlus));
        btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));
        elementBox.getChildren().addAll(btnPlus, label);
        this.getChildren().add(elementBox);
    }

    /**
     * Creates an event handler for editing the value of a node.
     *
     * @param nodeValue the label displaying the node value
     * @param node the node to be edited
     * @return the event handler for editing the node value
     */
    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValue, Node node) {
        return event -> {
            logger.debug("Node Value: {}", nodeValue.getText());

            try {
                final String originalValue = nodeValue.getText();
                HBox parent = (HBox) nodeValue.getParent();

                TextField textField = new TextField(nodeValue.getText());
                textField.setStyle(TEXT_FIELD_STYLE);
                textField.setOnKeyPressed(keyEvent -> handleKeyPress(keyEvent, textField, nodeValue, node, originalValue));

                parent.getChildren().remove(nodeValue);
                parent.getChildren().add(textField);

            } catch (ClassCastException e) {
                logger.error("Node Value: {}", nodeValue.getText());
                logger.error("Error: {}", e.getMessage());
            }
        };
    }

    /**
     * Handles key press events for editing the value of a node.
     *
     * @param keyEvent      the key event
     * @param textField     the text field for editing the node value
     * @param nodeValue     the label displaying the node value
     * @param node          the node to be edited
     * @param originalValue the original value of the node
     */
    private void handleKeyPress(KeyEvent keyEvent, TextField textField, Label nodeValue, Node node, String originalValue) {
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
    }

    /**
     * Creates an event handler for opening or closing a node.
     *
     * @param elementBox the HBox containing the node
     * @param subNode the node to be opened or closed
     * @param isOpen whether the node is currently open
     * @return the event handler for opening or closing the node
     */
    @NotNull
    private EventHandler<ActionEvent> mouseOpenHandler(HBox elementBox, Node subNode, Boolean isOpen) {
        return event -> {
            logger.debug("Open Clicked: {} - isOpen: {}", subNode.getNodeName(), isOpen);

            if (isOpen) {
                openNode(elementBox, subNode);
            } else {
                closeNode(elementBox, subNode);
            }
        };
    }

    /**
     * Opens a node, displaying its children.
     *
     * @param elementBox the HBox containing the node
     * @param subNode    the node to be opened
     */
    private void openNode(HBox elementBox, Node subNode) {
        logger.debug("OPEN Pressed");

        SimpleNodeElement simpleNodeElement = new SimpleNodeElement(subNode, xmlEditor);
        HBox hbox = new HBox();

        Button btnMinus = new Button("", new ImageView(imageMinus));
        btnMinus.setOnAction(mouseOpenHandler(elementBox, subNode, false));

        if (shouldBeTable(subNode)) {
            GridPane gridPane = createTable(elementBox, subNode, btnMinus);
            hbox.getChildren().addAll(gridPane);
        } else {
            var t = elementBox.getChildren().get(1);
            elementBox.getChildren().clear();
            elementBox.getChildren().addAll(btnMinus, t);

            hbox.getChildren().addAll(simpleNodeElement);
        }
        elementBox.getChildren().add(hbox);
    }

    /**
     * Closes a node, hiding its children.
     *
     * @param elementBox the HBox containing the node
     * @param subNode    the node to be closed
     */
    private void closeNode(HBox elementBox, Node subNode) {
        logger.debug("CLOSE Pressed");

        Button btnPlus = new Button("", new ImageView(imagePlus));
        btnPlus.setOnAction(mouseOpenHandler(elementBox, subNode, true));

        var t = elementBox.getChildren().get(1);
        elementBox.getChildren().clear();
        elementBox.getChildren().addAll(btnPlus, t);
    }

    /**
     * Creates a table representation of the node's children.
     *
     * @param elementBox the HBox containing the node
     * @param subNode the node whose children are to be displayed in a table
     * @param btnMinus the button for collapsing the node
     * @return the GridPane representing the table
     */
    private GridPane createTable(HBox elementBox, Node subNode, Button btnMinus) {
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add(TREE_GRID_STYLE);
        int row = 1;
        Map<String, Integer> columns = new HashMap<>();

        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);

            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                addTableRow(gridPane, oneRow, row, columns);
                row++;
            }
        }
        var t = elementBox.getChildren().get(1);
        elementBox.getChildren().clear();
        elementBox.getChildren().addAll(btnMinus, t);

        return gridPane;
    }

    /**
     * Adds a row to the table representation of the node's children.
     *
     * @param gridPane the GridPane representing the table
     * @param oneRow   the node representing the row
     * @param row      the row index
     * @param columns  the map of column names to column indices
     */
    private void addTableRow(GridPane gridPane, Node oneRow, int row, Map<String, Integer> columns) {
        var textContent = new Label(oneRow.getNodeName() + "# [" + row + "]");
        Pane rowCount = getCenterPaneWithLabel(textContent);
        rowCount.setStyle(ELEMENT_BOX_STYLE);
        setRowHoverEffect(gridPane, rowCount, row);

        gridPane.add(rowCount, 0, row);

        for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
            Node oneNode = oneRow.getChildNodes().item(x);

            if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                addTableCell(gridPane, oneNode, row, columns);
            }
        }
    }

    /**
     * Sets the hover effect for a row in the table.
     *
     * @param gridPane the GridPane representing the table
     * @param rowCount the Pane representing the row count
     * @param row      the row index
     */
    private void setRowHoverEffect(GridPane gridPane, Pane rowCount, int row) {
        rowCount.setOnMouseEntered(event -> setHoverEffect(gridPane, row, true));
        rowCount.setOnMouseExited(event -> setHoverEffect(gridPane, row, false));
    }

    /**
     * Sets the hover effect for a cell in the table.
     *
     * @param gridPane  the GridPane representing the table
     * @param row       the row index
     * @param isHovered whether the cell is hovered
     */
    private void setHoverEffect(GridPane gridPane, int row, boolean isHovered) {
        for (int c = 0; c < gridPane.getColumnCount() - 1; c++) {
            var node = getNodeFromGridPane(gridPane, c + 1, row);
            if (node != null) {
                node.setStyle(isHovered ? HOVER_STYLE : DEFAULT_STYLE);
            }
        }
    }

    /**
     * Adds a cell to the table representation of the node's children.
     *
     * @param gridPane the GridPane representing the table
     * @param oneNode  the node representing the cell
     * @param row      the row index
     * @param columns  the map of column names to column indices
     */
    private void addTableCell(GridPane gridPane, Node oneNode, int row, Map<String, Integer> columns) {
        var nodeName = oneNode.getNodeName();
        int colPos = columns.computeIfAbsent(nodeName, k -> columns.size() + 1);

        if (columns.get(nodeName) == colPos) {
            var nn = new Label(nodeName);
            nn.setOnMouseClicked(editNodeValueHandler(nn, oneNode));
            Pane textPane = getCenterPaneWithLabel(nn);
            textPane.setStyle(TABLE_HEADER_STYLE);
            gridPane.add(textPane, colPos, 0);
            setColumnHoverEffect(gridPane, textPane, colPos);
        }

        if (oneNode.getChildNodes().getLength() == 1 && oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            var n = new Label(oneNode.getTextContent());
            var t = getCenterPaneWithLabel(n);
            t.setOnMouseClicked(editNodeValueHandler(n, oneNode));
            gridPane.add(t, colPos, row);
        } else {
            gridPane.add(new SimpleNodeElement(oneNode, xmlEditor), colPos, row);
        }
    }

    private void setColumnHoverEffect(GridPane gridPane, Pane textPane, int colPos) {
        textPane.setOnMouseEntered(event -> setHoverEffect(gridPane, colPos, true));
        textPane.setOnMouseExited(event -> setHoverEffect(gridPane, colPos, false));
    }

    private javafx.scene.Node getNodeFromGridPane(GridPane gridPane, int col, int row) {
        for (var node : gridPane.getChildren()) {
            Integer columnIndex = GridPane.getColumnIndex(node);
            Integer rowIndex = GridPane.getRowIndex(node);

            if (Objects.equals(columnIndex, col) && Objects.equals(rowIndex, row)) {
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
        return (int) IntStream.range(0, n.getChildNodes().getLength())
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
        return count > 1 && nodeNames.size() == 1;
    }
}