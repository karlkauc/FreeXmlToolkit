package org.fxt.freexmltoolkit.controls;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A custom VBox implementation for displaying XML nodes in a tree structure.
 * Styles are managed via an external CSS file.
 * This version is refactored for a more modern look and better performance.
 */
public class SimpleNodeElement extends VBox {

    private static final Logger logger = LogManager.getLogger(SimpleNodeElement.class);

    private final XmlEditor xmlEditor;

    public SimpleNodeElement(Node node, XmlEditor caller) {
        this.xmlEditor = caller;
        // Dem Wurzelelement wird eine CSS-Klasse für gezieltes Styling zugewiesen.
        this.getStyleClass().add("simple-node-element");

        if (node.hasChildNodes()) {
            // Attribute werden jetzt direkt innerhalb der jeweiligen Node-Typen behandelt
            addChildNodes(node);
        }
    }

    private void addChildNodes(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            var subNode = node.getChildNodes().item(i);

            switch (subNode.getNodeType()) {
                case Node.COMMENT_NODE -> addCommentNode(subNode);
                case Node.ELEMENT_NODE -> addElementNode(subNode);
                case Node.TEXT_NODE -> {
                    // Leere Textknoten (oft nur Zeilenumbrüche) werden ignoriert
                    if (subNode.getNodeValue() != null && !subNode.getNodeValue().trim().isEmpty()) {
                        this.getChildren().add(new Label("TEXT: " + subNode.getNodeValue()));
                    }
                }
                default -> this.getChildren().add(new Label("DEFAULT: " + subNode.getNodeName()));
            }
        }
    }

    private void addCommentNode(Node subNode) {
        Label label = new Label("<!-- " + subNode.getNodeValue().trim() + " -->");
        label.getStyleClass().add("xml-tree-comment");
        this.getChildren().add(label);
    }

    private void addElementNode(Node subNode) {
        // Prüft, ob der Knoten nur ein einziges Text-Kind hat (z.B. <tag>wert</tag>)
        boolean isTextNode = subNode.getChildNodes().getLength() == 1 && subNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE;

        if (isTextNode) {
            addTextNode(subNode);
        } else {
            addComplexNode(subNode);
        }
    }

    private void addTextNode(Node subNode) {
        var firstItem = subNode.getChildNodes().item(0);
        var nodeName = new Label(subNode.getNodeName());
        var nodeValue = new Label(firstItem.getNodeValue());

        // Zeilenumbruch für den Knotennamen deaktivieren.
        nodeName.setWrapText(false);
        nodeValue.setWrapText(true); // Der Wert darf weiterhin umbrechen.

        nodeName.setTooltip(new Tooltip(subNode.getNodeName()));
        nodeValue.setTooltip(new Tooltip(firstItem.getNodeValue()));

        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, firstItem));

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("xml-tree-text");

        // Flexibles Spaltenlayout anstelle von festen Prozentwerten.
        // Die Namensspalte nimmt sich so viel Platz, wie sie braucht.
        ColumnConstraints nameColumn = new ColumnConstraints();
        nameColumn.setHgrow(Priority.NEVER);

        // Die Wertespalte füllt den gesamten restlichen Platz.
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);

        gridPane.getColumnConstraints().addAll(nameColumn, valueColumn);

        addAttributesToGridPane(subNode, gridPane);

        var nodeNameBox = new HBox(nodeName);
        nodeNameBox.getStyleClass().add("node-name-box");

        var nodeValueBox = new HBox(nodeValue);
        nodeValueBox.getStyleClass().add("node-value-box");

        final int row = gridPane.getRowCount();
        gridPane.add(nodeNameBox, 0, row);
        gridPane.add(nodeValueBox, 1, row);

        this.getChildren().add(gridPane);
    }

    private void addAttributesToGridPane(Node subNode, GridPane gridPane) {
        if (subNode.hasAttributes()) {
            for (int i = 0; i < subNode.getAttributes().getLength(); i++) {
                var attribute = subNode.getAttributes().item(i);

                var attributeNameLabel = new Label(attribute.getNodeName());
                var attributeBox = new HBox(attributeNameLabel);
                attributeBox.getStyleClass().add("attribute-box");

                var attributeValueLabel = new Label(attribute.getNodeValue());
                attributeValueLabel.setOnMouseClicked(editNodeValueHandler(attributeValueLabel, attribute));
                var nodeValueBox = new HBox(attributeValueLabel);
                nodeValueBox.getStyleClass().add("attribute-value-box");

                gridPane.add(attributeBox, 0, i);
                gridPane.add(nodeValueBox, 1, i);
            }
        }
    }

    /**
     * Refactored method to handle complex nodes with a more efficient expand/collapse logic.
     */
    private void addComplexNode(Node subNode) {
        // 1. Der Header für den aufklappbaren Bereich
        HBox headerBox = new HBox(5);
        headerBox.getStyleClass().add("element-box");
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Region icon = new Region();
        icon.getStyleClass().add("icon");

        Button toggleButton = new Button();
        toggleButton.setGraphic(icon);
        toggleButton.getStyleClass().add("tree-toggle-button");
        icon.getStyleClass().add("toggle-expand");

        Label label = new Label(subNode.getNodeName());
        label.getStyleClass().add("node-label-complex");

        Label countLabel = new Label("(" + calculateNodeCount(subNode) + ")");
        countLabel.getStyleClass().add("node-count-label");

        headerBox.getChildren().addAll(toggleButton, label, countLabel);

        // 2. Der Container für die Kind-Elemente
        VBox childrenContainer = new VBox();
        childrenContainer.getStyleClass().add("children-container");

        // --- NEU: Die seitliche Klick-Leiste zum Einklappen ---
        Region collapseBar = new Region();
        collapseBar.getStyleClass().add("collapse-bar");
        // Ein Klick auf die Leiste löst die Aktion des Toggle-Buttons aus
        collapseBar.setOnMouseClicked(event -> toggleButton.fire());

        // --- NEU: Ein HBox-Wrapper für Leiste und Inhalt ---
        HBox contentWrapper = new HBox(collapseBar, childrenContainer);
        // Der childrenContainer soll den gesamten verfügbaren horizontalen Platz einnehmen
        HBox.setHgrow(childrenContainer, Priority.ALWAYS);

        // Initialer Zustand: Alles unsichtbar
        contentWrapper.setVisible(false);
        contentWrapper.setManaged(false);

        // 3. Die Aktion zum Umschalten der Sichtbarkeit
        toggleButton.setOnAction(event -> {
            boolean isExpanded = contentWrapper.isVisible();
            if (isExpanded) {
                // Zuklappen
                contentWrapper.setVisible(false);
                contentWrapper.setManaged(false);
                icon.getStyleClass().remove("toggle-collapse");
                icon.getStyleClass().add("toggle-expand");
            } else {
                // Aufklappen
                if (childrenContainer.getChildren().isEmpty()) {
                    if (shouldBeTable(subNode)) {
                        childrenContainer.getChildren().add(createTable(subNode));
                    } else {
                        childrenContainer.getChildren().add(new SimpleNodeElement(subNode, xmlEditor));
                    }
                }
                contentWrapper.setVisible(true);
                contentWrapper.setManaged(true);
                icon.getStyleClass().remove("toggle-expand");
                icon.getStyleClass().add("toggle-collapse");
            }
        });

        // 4. Header und den neuen contentWrapper zum Haupt-VBox hinzufügen
        this.getChildren().addAll(headerBox, contentWrapper);
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValueLabel, Node domNode) {
        return event -> {
            if (event.getClickCount() != 2) return; // Nur bei Doppelklick bearbeiten

            try {
                final String originalValue = nodeValueLabel.getText();
                HBox parent = (HBox) nodeValueLabel.getParent();

                TextField textField = new TextField(originalValue);
                textField.setOnAction(e -> handleEditCommit(textField, nodeValueLabel, domNode, parent));
                textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal) { // Wenn der Fokus verloren geht
                        handleEditCancel(textField, nodeValueLabel, originalValue, parent);
                    }
                });

                parent.getChildren().setAll(textField);
                textField.requestFocus();
                textField.selectAll();

            } catch (ClassCastException e) {
                logger.error("Error while creating edit textfield for node value: {}", nodeValueLabel.getText(), e);
            }
        };
    }

    private void handleEditCommit(TextField textField, Label label, Node node, HBox parent) {
        label.setText(textField.getText());
        node.setNodeValue(textField.getText());
        parent.getChildren().setAll(label);
        this.xmlEditor.refreshTextView();
    }

    private void handleEditCancel(TextField textField, Label label, String originalValue, HBox parent) {
        label.setText(originalValue);
        parent.getChildren().setAll(label);
    }

    private GridPane createTable(Node subNode) {
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("table-grid");

        // Map, um Spaltennamen und ihre Indizes zu speichern.
        // LinkedHashMap behält die Einfügereihenfolge bei, was für eine konsistente Spaltenreihenfolge sorgt.
        Map<String, Integer> columns = new LinkedHashMap<>();

        // --- SCHRITT 1: Alle Spaltenköpfe im Voraus ermitteln ---
        // Wir durchlaufen alle Zeilen, nur um die Spaltennamen zu sammeln.
        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);
            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
                    Node oneNode = oneRow.getChildNodes().item(x);
                    if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                        // Fügt den Spaltennamen hinzu, falls er noch nicht existiert,
                        // und weist ihm den nächsten verfügbaren Index zu.
                        columns.computeIfAbsent(oneNode.getNodeName(), k -> columns.size());
                    }
                }
            }
        }

        // --- SCHRITT 2: Header-Zeile basierend auf den gesammelten Spalten erstellen ---
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            String columnName = entry.getKey();
            int columnIndex = entry.getValue();

            var headerLabel = new Label(columnName);
            var headerPane = new StackPane(headerLabel);
            headerPane.getStyleClass().add("table-header");
            gridPane.add(headerPane, columnIndex, 0); // Header immer in Zeile 0
        }

        // --- SCHRITT 3: Datenzeilen füllen ---
        int row = 1; // Daten beginnen in Zeile 1
        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);
            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                // Die Hilfsmethoden verwenden jetzt die vorab gefüllte 'columns'-Map.
                addTableRow(gridPane, oneRow, row, columns);
                row++;
            }
        }
        return gridPane;
    }

    private void addTableRow(GridPane gridPane, Node oneRow, int row, Map<String, Integer> columns) {
        for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
            Node oneNode = oneRow.getChildNodes().item(x);
            if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                addTableCell(gridPane, oneNode, row, columns);
            }
        }
    }

    private void addTableCell(GridPane gridPane, Node oneNode, int row, Map<String, Integer> columns) {
        var nodeName = oneNode.getNodeName();
        int colPos = columns.computeIfAbsent(nodeName, k -> columns.size());

        // Header nur einmal hinzufügen
        if (row == 1) {
            var headerLabel = new Label(nodeName);
            var headerPane = new StackPane(headerLabel);
            headerPane.getStyleClass().add("table-header");
            gridPane.add(headerPane, colPos, 0);
        }

        StackPane cellPane;
        if (oneNode.getChildNodes().getLength() == 1 && oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            var contentLabel = new Label(oneNode.getTextContent());
            contentLabel.setOnMouseClicked(editNodeValueHandler(contentLabel, oneNode.getChildNodes().item(0)));
            cellPane = new StackPane(contentLabel);
        } else {
            // Verschachtelte komplexe Knoten in einer Tabelle
            cellPane = new StackPane(new SimpleNodeElement(oneNode, xmlEditor));
        }
        cellPane.getStyleClass().add("table-cell");
        gridPane.add(cellPane, colPos, row);
    }

    private static int calculateNodeCount(Node n) {
        return (int) IntStream.range(0, n.getChildNodes().getLength())
                .filter(i -> n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
                .count();
    }

    private static boolean shouldBeTable(Node n) {
        if (n.getChildNodes().getLength() < 2) return false;

        String firstChildName = null;
        int elementNodeCount = 0;

        for (int i = 0; i < n.getChildNodes().getLength(); i++) {
            Node child = n.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementNodeCount++;
                if (firstChildName == null) {
                    firstChildName = child.getNodeName();
                } else if (!firstChildName.equals(child.getNodeName())) {
                    return false; // Unterschiedliche Namen, also keine Tabelle
                }
            }
        }
        return elementNodeCount > 1;
    }
}