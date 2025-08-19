package org.fxt.freexmltoolkit.controls;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A custom VBox implementation for displaying XML nodes in a tree structure.
 * Styles are managed via an external CSS file.
 * This version is refactored for a more modern look and better performance.
 */
public class XmlGraphicEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlGraphicEditor.class);

    private final XmlEditor xmlEditor;
    private final Node currentDomNode;

    public XmlGraphicEditor(Node node, XmlEditor caller) {
        this.xmlEditor = caller;
        this.currentDomNode = node;
        // Dem Wurzelelement wird eine CSS-Klasse für gezieltes Styling zugewiesen.
        this.getStyleClass().add("simple-node-element");

        if (node.hasChildNodes()) {
            addChildNodes(node);
        }

        // KEIN Kontextmenü für die Haupt-VBox, um doppelte Menüs zu vermeiden
        // Kontextmenüs werden nur für die einzelnen Kinder-Elemente erstellt
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

        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, subNode));

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

        // Kontextmenü für Text-Knoten hinzufügen - nur an das GridPane, um doppelte Menüs zu vermeiden
        logger.debug("Setting up context menu for text node: {} (Type: {})", subNode.getNodeName(), subNode.getNodeType());
        setupContextMenu(gridPane, subNode);

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
        // Container für das gesamte Element (Parent + Children)
        VBox elementContainer = new VBox();
        elementContainer.getStyleClass().add("element-container");
        elementContainer.setAlignment(Pos.TOP_CENTER);
        
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
                        childrenContainer.getChildren().add(new XmlGraphicEditor(subNode, xmlEditor));
                    }
                }
                contentWrapper.setVisible(true);
                contentWrapper.setManaged(true);
                icon.getStyleClass().remove("toggle-expand");
                icon.getStyleClass().add("toggle-collapse");
            }
        });

        // 4. Header und den neuen contentWrapper zum Haupt-VBox hinzufügen
        elementContainer.getChildren().addAll(headerBox, contentWrapper);

        // 5. Kontextmenü für das Element hinzufügen
        logger.debug("Setting up context menu for complex node: {} (Type: {})", subNode.getNodeName(), subNode.getNodeType());
        setupContextMenu(elementContainer, subNode);

        // 6. Drag & Drop für das Element einrichten
        setupDragAndDrop(elementContainer, subNode);

        this.getChildren().add(elementContainer);
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValueLabel, Node domNode) {
        return event -> {
            if (event.getClickCount() != 2) return; // Nur bei Doppelklick bearbeiten

            if (!(nodeValueLabel.getParent() instanceof Pane parent)) {
                logger.warn("Cannot edit node value, label's parent is not a Pane.");
                return;
            }

            final String originalValue = nodeValueLabel.getText();
            TextField textField = new TextField(originalValue);

            // Ein Flag, um zu verfolgen, ob die Bearbeitung erfolgreich committet wurde.
            // Wir verwenden ein Array, damit die Variable im Lambda-Ausdruck effektiv final ist.
            final boolean[] committed = {false};

            // 1. Die Commit-Aktion (ENTER) setzt die Flag auf true.
            textField.setOnAction(e -> {
                handleEditCommit(textField, nodeValueLabel, domNode, parent);
                committed[0] = true;
            });

            // 2. Die Cancel-Aktion (Fokusverlust) wird NUR ausgeführt, wenn NICHT committet wurde.
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal && !committed[0]) { // Prüfe den Flag!
                    handleEditCancel(textField, nodeValueLabel, originalValue, parent);
                }
            });

            parent.getChildren().setAll(textField);
            textField.requestFocus();
            textField.selectAll();
        };
    }

    /**
     * Übernimmt den neuen Wert aus dem Textfeld, aktualisiert das UI-Label und den zugrundeliegenden XML-DOM-Knoten.
     *
     * @param textField       Das Textfeld mit dem neuen Wert.
     * @param label           Das UI-Label, das wieder angezeigt werden soll.
     * @param domNodeToUpdate Der XML-Knoten (Text oder Attribut), dessen Wert aktualisiert wird.
     * @param parent          Der UI-Container, in dem das Label/Textfeld liegt.
     */
    private void handleEditCommit(TextField textField, Label label, Node domNodeToUpdate, Pane parent) {
        // 1. Neuen Wert aus dem Textfeld holen.
        final String newValue = textField.getText() != null ? textField.getText() : "";

        // 2. Den Text des UI-Labels aktualisieren.
        label.setText(newValue);

        // Protokolliere alte und neue Werte
        String oldValue = domNodeToUpdate.getNodeType() == Node.ELEMENT_NODE
                ? domNodeToUpdate.getTextContent()
                : domNodeToUpdate.getNodeValue();
        logger.info("Wertänderung - Alt: '{}', Neu: '{}'", oldValue, newValue);

        // 3. Den Wert im XML-DOM aktualisieren.
        if (domNodeToUpdate.getNodeType() == Node.ELEMENT_NODE) {
            domNodeToUpdate.setTextContent(newValue);
        } else {
            domNodeToUpdate.setNodeValue(newValue);
        }

        // 4. Das Textfeld wieder durch das Label ersetzen.
        parent.getChildren().setAll(label);

        // 5. Die Textansicht des Editors aktualisieren.
        // KORREKTUR: Ruft die neue Methode auf, die aus dem DOM liest, nicht aus der Datei.
        this.xmlEditor.refreshTextViewFromDom();
    }

    private void handleEditCancel(TextField textField, Label label, String originalValue, Pane parent) {
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

        // Die Spaltenposition wird jetzt zuverlässig aus der vorab gefüllten Map geholt.
        Integer colPos = columns.get(nodeName);
        if (colPos == null) {
            // Dies sollte mit der neuen createTable-Logik nicht passieren, ist aber eine gute Absicherung.
            logger.warn("Column '{}' not found in pre-calculated header map. Skipping cell.", nodeName);
            return;
        }

        StackPane cellPane;
        if (oneNode.getChildNodes().getLength() == 1 && oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            var contentLabel = new Label(oneNode.getTextContent());
            // Wir übergeben den ELEMENT-Knoten (oneNode), nicht mehr seinen Text-Kind-Knoten.
            contentLabel.setOnMouseClicked(editNodeValueHandler(contentLabel, oneNode));
            cellPane = new StackPane(contentLabel);
        } else {
            // Verschachtelte komplexe Knoten in einer Tabelle
            cellPane = new StackPane(new XmlGraphicEditor(oneNode, xmlEditor));
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

    private void setupContextMenu(javafx.scene.Node uiContainer, Node domNode) {
        logger.debug("Creating context menu for DOM node: {} (Type: {}, Parent: {})",
                domNode.getNodeName(),
                domNode.getNodeType(),
                domNode.getParentNode() != null ? domNode.getParentNode().getNodeName() : "null");

        ContextMenu contextMenu = new ContextMenu();

        MenuItem addChildMenuItem = new MenuItem("Add Child to: " + domNode.getNodeName());
        addChildMenuItem.setGraphic(createIcon("ADD_CHILD"));
        addChildMenuItem.setOnAction(e -> addChildNodeToSpecificParent(domNode));

        MenuItem addSiblingAfterMenuItem = new MenuItem("Add Sibling After");
        addSiblingAfterMenuItem.setGraphic(createIcon("ADD_AFTER"));
        addSiblingAfterMenuItem.setOnAction(e -> addSiblingNode(domNode, true));

        MenuItem addSiblingBeforeMenuItem = new MenuItem("Add Sibling Before");
        addSiblingBeforeMenuItem.setGraphic(createIcon("ADD_BEFORE"));
        addSiblingBeforeMenuItem.setOnAction(e -> addSiblingNode(domNode, false));

        MenuItem deleteMenuItem = new MenuItem("Delete: " + domNode.getNodeName());
        deleteMenuItem.setGraphic(createIcon("DELETE"));
        deleteMenuItem.setOnAction(e -> deleteNode(domNode));

        contextMenu.getItems().addAll(
                addChildMenuItem,
                addSiblingAfterMenuItem,
                addSiblingBeforeMenuItem,
                new SeparatorMenuItem(),
                deleteMenuItem
        );

        uiContainer.setOnContextMenuRequested(e -> {
            logger.debug("Context menu requested for UI container. DOM node: {}", domNode.getNodeName());

            // Alle anderen Kontextmenüs schließen
            contextMenu.hide();

            // Unser Menü anzeigen
            contextMenu.show(uiContainer, e.getScreenX(), e.getScreenY());

            // Wichtig: Event konsumieren, damit es nicht weiter nach oben bubbelt
            e.consume();
        });
    }

    private void setupDragAndDrop(VBox elementContainer, Node domNode) {
        // Drag-Quelle einrichten - nur für VBox Container (komplexe Knoten)
        elementContainer.setOnDragDetected(event -> {
            Dragboard db = elementContainer.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(domNode.getNodeName() + "||" + System.identityHashCode(domNode));
            db.setContent(content);
            event.consume();
        });

        // Drop-Ziel einrichten
        elementContainer.setOnDragOver(event -> {
            if (event.getGestureSource() != elementContainer && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        elementContainer.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String[] data = db.getString().split("\\|\\|");
                if (data.length == 2) {
                    String sourceName = data[0];
                    try {
                        moveNodeToNewParent(domNode, sourceName);
                        success = true;
                    } catch (Exception e) {
                        logger.error("Fehler beim Verschieben des Knotens", e);
                        showErrorDialog("Verschieben fehlgeschlagen", e.getMessage());
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void addChildNodeToSpecificParent(Node parentNode) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Node");
        dialog.setHeaderText("Create new child element for '" + parentNode.getNodeName() + "'");
        dialog.setContentText("Element Name:");

        dialog.showAndWait().ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                try {
                    Document doc = parentNode.getOwnerDocument();
                    Element newElement = doc.createElement(elementName.trim());
                    parentNode.appendChild(newElement);

                    // UI aktualisieren - wir müssen die gesamte Ansicht neu laden
                    refreshWholeView();

                    logger.info("New child element '{}' added to '{}'", elementName, parentNode.getNodeName());
                } catch (Exception e) {
                    showErrorDialog("Error adding element", e.getMessage());
                }
            }
        });
    }

    private void addSiblingNode(Node siblingNode, boolean after) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Node");
        dialog.setHeaderText(after ? "Create element after current" : "Create element before current");
        dialog.setContentText("Element Name:");

        dialog.showAndWait().ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                try {
                    Node parentNode = siblingNode.getParentNode();
                    if (parentNode != null) {
                        Document doc = siblingNode.getOwnerDocument();
                        Element newElement = doc.createElement(elementName.trim());

                        if (after) {
                            Node nextSibling = siblingNode.getNextSibling();
                            if (nextSibling != null) {
                                parentNode.insertBefore(newElement, nextSibling);
                            } else {
                                parentNode.appendChild(newElement);
                            }
                        } else {
                            parentNode.insertBefore(newElement, siblingNode);
                        }

                        // UI aktualisieren
                        refreshWholeView();

                        logger.info("New sibling element '{}' added {} '{}'",
                                elementName, after ? "after" : "before", siblingNode.getNodeName());
                    }
                } catch (Exception e) {
                    showErrorDialog("Error adding element", e.getMessage());
                }
            }
        });
    }

    private void deleteNode(Node nodeToDelete) {
        // Debug info about the node to delete
        logger.info("Deleting node: Name='{}', Type={}, HasParent={}",
                nodeToDelete.getNodeName(),
                nodeToDelete.getNodeType(),
                nodeToDelete.getParentNode() != null);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Node");
        alert.setHeaderText("Delete node and all child nodes?");
        alert.setContentText("Element '" + nodeToDelete.getNodeName() + "' will be permanently deleted.\n" +
                "Node type: " + getNodeTypeString(nodeToDelete.getNodeType()) + "\n" +
                "Parent: " + (nodeToDelete.getParentNode() != null ? nodeToDelete.getParentNode().getNodeName() : "null"));

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    Node parentNode = nodeToDelete.getParentNode();
                    if (parentNode != null) {
                        parentNode.removeChild(nodeToDelete);

                        // UI aktualisieren
                        refreshWholeView();

                        logger.info("Element '{}' successfully deleted", nodeToDelete.getNodeName());
                    }
                } catch (Exception e) {
                    showErrorDialog("Error deleting element", e.getMessage());
                }
            }
        });
    }

    private void refreshWholeView() {
        // Die gesamte Ansicht neu aufbauen - wir müssen vom Root aus neu laden
        // da sich die DOM-Struktur geändert hat
        this.xmlEditor.refreshTextViewFromDom();

        // Den grafischen Editor neu initialisieren
        // Dazu suchen wir die parent XmlGraphicEditor Instanz
        findRootEditorAndRefresh();
    }

    private void findRootEditorAndRefresh() {
        // Diese Methode würde in einer echten Implementierung
        // den Root-Editor finden und neu laden
        // Für jetzt loggen wir nur, dass eine Aktualisierung nötig ist
        logger.info("DOM structure changed - full UI refresh required");

        // Einfache Lösung: Die aktuelle Instanz neu laden
        this.getChildren().clear();
        if (currentDomNode.hasChildNodes()) {
            addChildNodes(currentDomNode);
        }
    }

    private void moveNodeToNewParent(Node newParentNode, String sourceNodeName) {
        // Vereinfachte Implementierung - in einer echten Anwendung würde man
        // den zu verschiebenden Knoten anhand der ID finden und verschieben
        logger.info("Node '{}' would be moved to '{}'", sourceNodeName, newParentNode.getNodeName());

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Move Node");
        info.setHeaderText(null);
        info.setContentText("Node '" + sourceNodeName + "' would be moved to '" + newParentNode.getNodeName() + "'.\n" +
                "(Full implementation requires node reference tracking)");
        info.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getNodeTypeString(short nodeType) {
        return switch (nodeType) {
            case Node.ELEMENT_NODE -> "ELEMENT";
            case Node.TEXT_NODE -> "TEXT";
            case Node.COMMENT_NODE -> "COMMENT";
            case Node.ATTRIBUTE_NODE -> "ATTRIBUTE";
            case Node.DOCUMENT_NODE -> "DOCUMENT";
            default -> "OTHER(" + nodeType + ")";
        };
    }

    private javafx.scene.Node createIcon(String iconType) {
        return switch (iconType) {
            case "ADD_CHILD" -> createAddChildIcon();
            case "ADD_AFTER" -> createAddAfterIcon();
            case "ADD_BEFORE" -> createAddBeforeIcon();
            case "DELETE" -> createDeleteIcon();
            default -> createDefaultIcon();
        };
    }

    private javafx.scene.Node createAddChildIcon() {
        // Plus icon with downward arrow
        Group group = new Group();

        // Plus sign
        Rectangle hLine = new Rectangle(10, 2);
        hLine.setFill(Color.DARKGREEN);
        hLine.setX(3);
        hLine.setY(7);

        Rectangle vLine = new Rectangle(2, 10);
        vLine.setFill(Color.DARKGREEN);
        vLine.setX(7);
        vLine.setY(3);

        // Small arrow pointing down
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(8.0, 14.0,  // top point
                6.0, 16.0,  // left point
                10.0, 16.0  // right point
        );
        arrow.setFill(Color.DARKGREEN);

        group.getChildren().addAll(hLine, vLine, arrow);
        return group;
    }

    private javafx.scene.Node createAddAfterIcon() {
        // Plus with right arrow
        Group group = new Group();

        Rectangle hLine = new Rectangle(8, 2);
        hLine.setFill(Color.DARKBLUE);
        hLine.setX(2);
        hLine.setY(7);

        Rectangle vLine = new Rectangle(2, 8);
        vLine.setFill(Color.DARKBLUE);
        vLine.setX(5);
        vLine.setY(4);

        // Arrow pointing right
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(11.0, 8.0,  // left point
                14.0, 6.0,  // top point
                14.0, 10.0  // bottom point
        );
        arrow.setFill(Color.DARKBLUE);

        group.getChildren().addAll(hLine, vLine, arrow);
        return group;
    }

    private javafx.scene.Node createAddBeforeIcon() {
        // Plus with left arrow
        Group group = new Group();

        Rectangle hLine = new Rectangle(8, 2);
        hLine.setFill(Color.DARKBLUE);
        hLine.setX(6);
        hLine.setY(7);

        Rectangle vLine = new Rectangle(2, 8);
        vLine.setFill(Color.DARKBLUE);
        vLine.setX(9);
        vLine.setY(4);

        // Arrow pointing left
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(5.0, 8.0,   // right point
                2.0, 6.0,   // top point
                2.0, 10.0   // bottom point
        );
        arrow.setFill(Color.DARKBLUE);

        group.getChildren().addAll(hLine, vLine, arrow);
        return group;
    }

    private javafx.scene.Node createDeleteIcon() {
        // X icon
        Group group = new Group();

        // First diagonal line (top-left to bottom-right)
        Line line1 = new Line(3, 3, 13, 13);
        line1.setStroke(Color.DARKRED);
        line1.setStrokeWidth(2);

        // Second diagonal line (top-right to bottom-left)
        Line line2 = new Line(13, 3, 3, 13);
        line2.setStroke(Color.DARKRED);
        line2.setStrokeWidth(2);

        group.getChildren().addAll(line1, line2);
        return group;
    }

    private javafx.scene.Node createDefaultIcon() {
        // Simple circle
        Circle circle = new Circle(8, 8, 6);
        circle.setFill(Color.LIGHTGRAY);
        circle.setStroke(Color.GRAY);
        return circle;
    }
}