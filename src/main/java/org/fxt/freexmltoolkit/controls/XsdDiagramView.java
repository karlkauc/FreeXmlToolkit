package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.controller.XsdController;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class XsdDiagramView {

    private final XsdNodeInfo rootNode;
    private final XsdController controller;
    private final String initialDoc;
    private final String initialJavadoc;

    private VBox detailPane;
    private XsdNodeInfo selectedNode;

    // Editor components
    private TextArea documentationTextArea;
    private TextArea javadocTextArea;
    private Button saveDocumentationButton;
    private ListView<String> exampleListView;
    private VBox exampleEditorPane;
    private boolean isEditingSchemaDoc = true;

    private ScrollPane treeScrollPane;

    // Enhanced Styles inspired by Altova XMLSpy
    private static final String NODE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f0f8ff); " +
                    "-fx-border-color: #4a90e2; -fx-border-width: 2px; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 8px 12px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c5aa0; " +
                    "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);";

    private static final String ATTRIBUTE_LABEL_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fffef7, #f9f5e7); " +
                    "-fx-border-color: #d4a147; -fx-border-width: 1.5px; " +
                    "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                    "-fx-padding: 4px 8px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: normal; -fx-text-fill: #8b6914; " +
                    "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 1, 0, 0.5, 0.5);";

    private static final String SEQUENCE_NODE_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                    "-fx-border-color: #6c757d; -fx-border-width: 2px; -fx-border-style: solid; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 10px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #495057; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);";

    private static final String CHOICE_NODE_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #fffbf0, #fff3cd); " +
                    "-fx-border-color: #ff8c00; -fx-border-width: 2px; -fx-border-style: dashed; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 10px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #b45309; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);";

    private static final String ANY_NODE_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #dee2e6); " +
                    "-fx-border-color: #adb5bd; -fx-border-width: 1.5px; -fx-border-style: dotted; " +
                    "-fx-border-radius: 4px; -fx-background-radius: 4px; " +
                    "-fx-padding: 6px 10px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #6c757d; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 1, 0, 0.5, 0.5);";

    private static final String CARDINALITY_LABEL_STYLE =
            "-fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-font-family: 'Consolas', 'Monaco', monospace; " +
                    "-fx-background-color: #f8f9fa; -fx-padding: 2px 4px; -fx-background-radius: 3px; " +
                    "-fx-border-color: #dee2e6; -fx-border-width: 1px; -fx-border-radius: 3px;";

    private static final String TOGGLE_BUTTON_STYLE =
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4a90e2; " +
                    "-fx-background-color: #ffffff; -fx-border-color: #4a90e2; -fx-border-width: 1px; " +
                    "-fx-border-radius: 50%; -fx-background-radius: 50%; -fx-padding: 2px 6px; " +
                    "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 1, 0, 0.5, 0.5);";

    private static final String DOC_LABEL_STYLE =
            "-fx-font-size: 11px; -fx-text-fill: #6c757d; -fx-font-style: italic; " +
                    "-fx-padding: 4px 8px 4px 12px; -fx-background-color: rgba(108, 117, 125, 0.05); " +
                    "-fx-background-radius: 3px; -fx-border-color: rgba(108, 117, 125, 0.15); " +
                    "-fx-border-width: 0 0 0 3px; -fx-border-radius: 0 3px 3px 0;";

    private static final String DETAIL_LABEL_STYLE = "-fx-font-weight: bold; -fx-text-fill: #333;";
    private static final String DETAIL_PANE_STYLE = "-fx-padding: 15px; -fx-background-color: #ffffff; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1px;";


    public XsdDiagramView(XsdNodeInfo rootNode, XsdController controller, String initialDoc, String initialJavadoc) {
        this.rootNode = rootNode;
        this.controller = controller;
        this.initialDoc = initialDoc;
        this.initialJavadoc = initialJavadoc;
    }

    public Node build() {
        if (rootNode == null) {
            return new Label("No element information found.");
        }

        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.75);

        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));
        diagramContainer.setAlignment(Pos.TOP_CENTER); // Root-Node zentrieren
        Node rootNodeView = createNodeView(rootNode);
        diagramContainer.getChildren().add(rootNodeView);

        this.treeScrollPane = new ScrollPane(diagramContainer);
        treeScrollPane.setFitToWidth(false);
        treeScrollPane.setFitToHeight(false);

        BorderPane rightPaneLayout = new BorderPane();
        rightPaneLayout.setStyle(DETAIL_PANE_STYLE);

        detailPane = new VBox(10);
        Label placeholder = new Label("Click on a node to view details.");
        detailPane.getChildren().add(placeholder);

        ScrollPane detailScrollPane = new ScrollPane(detailPane);
        detailScrollPane.setFitToWidth(true);
        detailScrollPane.setFitToHeight(true);
        detailScrollPane.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        rightPaneLayout.setCenter(detailScrollPane);

        Node editorPane = createEditorPane();
        BorderPane.setMargin(editorPane, new Insets(10, 0, 0, 0));
        rightPaneLayout.setBottom(editorPane);

        splitPane.getItems().addAll(treeScrollPane, rightPaneLayout);
        return splitPane;
    }

    /**
     * Haupt-Dispatch-Methode, die entscheidet, welche Art von Ansicht für einen Knoten erstellt wird.
     */
    private Node createNodeView(XsdNodeInfo node) {
        return switch (node.nodeType()) {
            case ELEMENT -> createElementNodeView(node);
            case ATTRIBUTE -> createAttributeNodeView(node);
            case ANY -> createAnyNodeView(node);
            case SEQUENCE -> createStructuralNodeView(node, "SEQUENCE", SEQUENCE_NODE_STYLE);
            case CHOICE -> createStructuralNodeView(node, "CHOICE", CHOICE_NODE_STYLE);
            default -> new Label("Unknown Node Type: " + node.name());
        };
    }

    /**
     * Erstellt die Ansicht für ein <xs:element>.
     * Dies ist die Kernmethode für das neue Kaskaden-Layout.
     */
    private Node createElementNodeView(XsdNodeInfo node) {
        // Hauptcontainer für die gesamte Elementansicht (Info links, Kinder rechts)
        HBox mainContainer = new HBox(10);
        mainContainer.setAlignment(Pos.TOP_LEFT);

        // Linker Teil: Infos über das Element selbst und seine Attribute
        VBox elementInfoContainer = new VBox(5);
        elementInfoContainer.setPadding(new Insets(5));
        elementInfoContainer.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 1px 0 0; -fx-border-style: dotted; -fx-padding: 5;");

        // Kinder in Attribute und Strukturelemente aufteilen
        List<XsdNodeInfo> attributes = node.children().stream()
                .filter(c -> c.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE)
                .toList();
        List<XsdNodeInfo> structuralChildren = node.children().stream()
                .filter(c -> c.nodeType() != XsdNodeInfo.NodeType.ATTRIBUTE)
                .collect(Collectors.toList());

        // Wenn strukturelle Kinder vorhanden sind, das Element vertikal mittig ausrichten
        if (!structuralChildren.isEmpty()) {
            elementInfoContainer.setAlignment(Pos.CENTER_LEFT);
        }

        // Rechter Teil: Container für strukturelle Kinder (sequence, choice) mit Verbindungslinien
        VBox structuralChildrenContainer = new VBox(5);
        structuralChildrenContainer.setPadding(new Insets(0, 0, 0, 20));
        structuralChildrenContainer.setVisible(false);
        structuralChildrenContainer.setManaged(false);
        structuralChildrenContainer.setAlignment(Pos.TOP_LEFT);

        // Add visual connector line
        structuralChildrenContainer.setStyle("-fx-border-color: #4a90e2; -fx-border-width: 0 0 0 2px; -fx-border-style: solid;");

        // --- Linken Teil befüllen (elementInfoContainer) ---
        HBox nameAndToggleRow = new HBox(5);
        nameAndToggleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(NODE_LABEL_STYLE);

        // Add type-specific icon
        FontIcon elementIcon = createTypeSpecificIcon(node.type());
        nameLabel.setGraphic(elementIcon);
        
        nameLabel.setOnMouseClicked(event -> updateDetailPane(node));

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            nameAndToggleRow.getChildren().addAll(nameLabel, cardinalityLabel);
        } else {
            nameAndToggleRow.getChildren().add(nameLabel);
        }
        elementInfoContainer.getChildren().add(nameAndToggleRow);

        // Dokumentation für das Element hinzufügen
        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docLabel = new Label(node.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            elementInfoContainer.getChildren().add(docLabel);
        }

        // Attribute direkt zum linken Container hinzufügen
        if (!attributes.isEmpty()) {
            VBox attributeBox = new VBox(3);
            attributeBox.setPadding(new Insets(8, 0, 0, 15));
            for (XsdNodeInfo attr : attributes) {
                attributeBox.getChildren().add(createNodeView(attr));
            }
            elementInfoContainer.getChildren().add(attributeBox);
        }

        // --- Rechten Teil einrichten (structuralChildrenContainer) ---
        if (!structuralChildren.isEmpty()) {
            // Der Toggle-Button steuert jetzt den rechten Container
            addToggleButton(nameAndToggleRow, structuralChildrenContainer, structuralChildren);

            // Add horizontal connector line between element and children
            HBox connectorContainer = new HBox();
            Line horizontalLine = new Line();
            horizontalLine.setStartX(0);
            horizontalLine.setStartY(0);
            horizontalLine.setEndX(15);
            horizontalLine.setEndY(0);
            horizontalLine.setStroke(Color.web("#4a90e2"));
            horizontalLine.setStrokeWidth(2);
            connectorContainer.getChildren().add(horizontalLine);
            connectorContainer.setAlignment(Pos.CENTER_LEFT);
            mainContainer.getChildren().addAll(elementInfoContainer, connectorContainer, structuralChildrenContainer);
        } else {
            mainContainer.getChildren().add(elementInfoContainer);
        }

        return mainContainer;
    }

    /**
     * Erstellt die Ansicht für ein Attribut.
     */
    private Node createAttributeNodeView(XsdNodeInfo node) {
        HBox attributeContainer = new HBox(5);
        attributeContainer.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(ATTRIBUTE_LABEL_STYLE);

        // Add attribute icon
        FontIcon attributeIcon = new FontIcon("bi-at");
        attributeIcon.setIconColor(javafx.scene.paint.Color.web("#d4a147"));
        attributeIcon.setIconSize(12);
        nameLabel.setGraphic(attributeIcon);
        
        nameLabel.setOnMouseClicked(event -> updateDetailPane(node));

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            attributeContainer.getChildren().addAll(nameLabel, cardinalityLabel);
        } else {
            attributeContainer.getChildren().add(nameLabel);
        }
        return attributeContainer;
    }

    /**
     * Erstellt die Ansicht für ein Strukturelement (<xs:sequence> oder <xs:choice>).
     */
    private Node createStructuralNodeView(XsdNodeInfo node, String title, String style) {
        HBox mainContainer = new HBox(10);
        mainContainer.setAlignment(Pos.TOP_LEFT);

        // Linker Teil: Das "SEQUENCE" oder "CHOICE" Label - vertikal mittig
        VBox titleContainer = new VBox();
        titleContainer.setAlignment(Pos.CENTER_LEFT); // Vertikal mittig, horizontal linksbündig
        HBox titleRow = new HBox(5);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setStyle(style);

        Label titleLabel = new Label(title);

        // Add appropriate icon for structural elements
        FontIcon structuralIcon;
        if ("SEQUENCE".equals(title)) {
            structuralIcon = new FontIcon("bi-list-ol");
            structuralIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        } else if ("CHOICE".equals(title)) {
            structuralIcon = new FontIcon("bi-option");
            structuralIcon.setIconColor(javafx.scene.paint.Color.web("#ff8c00"));
        } else {
            structuralIcon = new FontIcon("bi-diagram-3");
            structuralIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        }
        structuralIcon.setIconSize(12);
        titleLabel.setGraphic(structuralIcon);

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            titleRow.getChildren().addAll(titleLabel, cardinalityLabel);
        } else {
            titleRow.getChildren().add(titleLabel);
        }
        titleContainer.getChildren().add(titleRow);

        // Rechter Teil: Die eigentlichen Kinder der Sequenz/Choice mit Verbindungslinien
        VBox childrenVBox = new VBox(5);
        childrenVBox.setPadding(new Insets(0, 0, 5, 20));
        childrenVBox.setVisible(false);
        childrenVBox.setManaged(false);
        childrenVBox.setAlignment(Pos.TOP_LEFT);

        // Enhanced connector line style based on structural type
        if ("SEQUENCE".equals(title)) {
            childrenVBox.setStyle("-fx-border-color: #6c757d; -fx-border-width: 0 0 0 2px; -fx-border-style: solid;");
        } else if ("CHOICE".equals(title)) {
            childrenVBox.setStyle("-fx-border-color: #ff8c00; -fx-border-width: 0 0 0 2px; -fx-border-style: dashed;");
        } else {
            childrenVBox.setStyle("-fx-border-color: #adb5bd; -fx-border-width: 0 0 0 1px; -fx-border-style: dotted;");
        }

        if (!node.children().isEmpty()) {
            addToggleButton(titleRow, childrenVBox, node.children());

            // Add horizontal connector for structural nodes
            HBox connectorContainer = new HBox();
            Line horizontalLine = new Line();
            horizontalLine.setStartX(0);
            horizontalLine.setStartY(0);
            horizontalLine.setEndX(15);
            horizontalLine.setEndY(0);
            if ("SEQUENCE".equals(title)) {
                horizontalLine.setStroke(Color.web("#6c757d"));
            } else if ("CHOICE".equals(title)) {
                horizontalLine.setStroke(Color.web("#ff8c00"));
                horizontalLine.getStrokeDashArray().addAll(5.0, 5.0);
            } else {
                horizontalLine.setStroke(Color.web("#adb5bd"));
            }
            horizontalLine.setStrokeWidth(2);
            connectorContainer.getChildren().add(horizontalLine);
            connectorContainer.setAlignment(Pos.CENTER_LEFT);
            mainContainer.getChildren().addAll(titleContainer, connectorContainer, childrenVBox);
        } else {
            mainContainer.getChildren().add(titleContainer);
        }
        return mainContainer;
    }

    /**
     * Erstellt die Ansicht für ein <xs:any> Element.
     */
    private Node createAnyNodeView(XsdNodeInfo node) {
        HBox nodeContainer = new HBox(5);
        nodeContainer.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(ANY_NODE_STYLE);

        // Add any element icon
        FontIcon anyIcon = new FontIcon("bi-asterisk");
        anyIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        anyIcon.setIconSize(12);
        nameLabel.setGraphic(anyIcon);
        
        nameLabel.setOnMouseClicked(event -> updateDetailPane(node));

        String cardinality = formatCardinality(node.minOccurs(), node.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardinalityLabel = new Label(cardinality);
            cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);
            nodeContainer.getChildren().addAll(nameLabel, cardinalityLabel);
        } else {
            nodeContainer.getChildren().add(nameLabel);
        }
        return nodeContainer;
    }

    /**
     * Fügt einen Toggle-Button hinzu, der die Kind-Knoten erst dann erstellt (lazy loading),
     * wenn er zum ersten Mal geklickt wird. Scrollt den neu sichtbaren Bereich automatisch
     * in die Ansicht.
     *
     * @param parentRow         Die HBox, zu der der Button hinzugefügt wird.
     * @param childrenContainer Der VBox-Container, der die Kinder aufnehmen wird.
     * @param childrenToRender  Die Liste der Kind-Datenmodelle, die gerendert werden sollen.
     */
    private void addToggleButton(HBox parentRow, VBox childrenContainer, List<XsdNodeInfo> childrenToRender) {
        Label toggleButton = new Label("+");
        toggleButton.setStyle(TOGGLE_BUTTON_STYLE);

        final boolean[] isExpanded = {false};
        final boolean[] childrenLoaded = {false};

        toggleButton.setOnMouseClicked(event -> {
            isExpanded[0] = !isExpanded[0];

            if (isExpanded[0] && !childrenLoaded[0]) {
                for (XsdNodeInfo childNode : childrenToRender) {
                    childrenContainer.getChildren().add(createNodeView(childNode));
                }
                childrenLoaded[0] = true;
            }

            childrenContainer.setVisible(isExpanded[0]);
            childrenContainer.setManaged(isExpanded[0]);
            toggleButton.setText(isExpanded[0] ? "−" : "+");

            // Scrollt den neu sichtbaren Bereich in die Ansicht
            if (isExpanded[0]) {
                // Platform.runLater stellt sicher, dass das Layout aktualisiert wurde,
                // bevor wir versuchen, die Positionen zu berechnen.
                Platform.runLater(() -> {
                    if (treeScrollPane == null || treeScrollPane.getContent() == null) {
                        return;
                    }
                    Node content = treeScrollPane.getContent();

                    // Berechne die Position des Kind-Containers relativ zum gesamten Inhalt
                    Bounds childBounds = childrenContainer.localToScene(childrenContainer.getBoundsInLocal());
                    Bounds contentBounds = content.localToScene(content.getBoundsInLocal());

                    if (childBounds == null || contentBounds == null) return;

                    double layoutX = childBounds.getMinX() - contentBounds.getMinX();

                    // Berechne die Dimensionen des sichtbaren Bereichs und des Gesamtinhalts
                    double contentWidth = content.getBoundsInLocal().getWidth();
                    double viewportWidth = treeScrollPane.getViewportBounds().getWidth();

                    // Scrolle horizontal, wenn nötig
                    if (contentWidth > viewportWidth) {
                        // Ziel ist es, den Anfang des Kind-Containers sichtbar zu machen.
                        // Wir berechnen den hvalue, der erforderlich ist, um den Container an den Anfang
                        // des sichtbaren Bereichs zu bringen.
                        double targetHValue = layoutX / (contentWidth - viewportWidth);

                        // Scrolle nur, wenn der neue Bereich rechts außerhalb der aktuellen Ansicht liegt.
                        // Ein Zurückscrollen nach links wird vermieden, um die Ansicht ruhig zu halten.
                        if (targetHValue > treeScrollPane.getHvalue()) {
                            treeScrollPane.setHvalue(Math.min(1.0, targetHValue));
                        }
                    }
                });
            }
        });
        parentRow.getChildren().add(toggleButton);
    }

    private String formatCardinality(String minOccurs, String maxOccurs) {
        String min = (minOccurs == null) ? "1" : minOccurs;
        String max = (maxOccurs == null) ? "1" : maxOccurs;
        if ("unbounded".equalsIgnoreCase(max)) {
            max = "∞";
        }

        // Special formatting for common cases
        if ("1".equals(min) && "1".equals(max)) {
            return ""; // Don't show [1..1] as it's the default
        } else if ("0".equals(min) && "1".equals(max)) {
            return "0..1";
        } else if ("1".equals(min) && "∞".equals(max)) {
            return "1..∞";
        } else if ("0".equals(min) && "∞".equals(max)) {
            return "0..∞";
        } else {
            return String.format("%s..%s", min, max);
        }
    }

    private void updateDetailPane(XsdNodeInfo node) {
        detailPane.getChildren().clear();
        if (node == null) {
            detailPane.getChildren().add(new Label("Please select a node."));
            return;
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        int rowIndex = 0;

        addDetailRow(grid, rowIndex++, "Name:", node.name());
        addDetailRow(grid, rowIndex++, "XPath:", node.xpath());
        addDetailRow(grid, rowIndex++, "Data Type:", node.type());

        detailPane.getChildren().add(grid);

        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docHeader = new Label("Documentation:");
            docHeader.setStyle(DETAIL_LABEL_STYLE);
            detailPane.getChildren().add(docHeader);

            WebView docView = new WebView();
            docView.getEngine().loadContent("<html><body style='font-family: sans-serif; font-size: 13px;'>" + node.documentation() + "</body></html>");
            docView.setPrefHeight(200);
            detailPane.getChildren().add(docView);
        }

        this.selectedNode = node;
        isEditingSchemaDoc = false;
        this.documentationTextArea.setText(node.documentation() != null ? node.documentation() : "");
        this.javadocTextArea.setText("");
        this.documentationTextArea.setEditable(false);
        this.javadocTextArea.setEditable(false);
        this.saveDocumentationButton.setDisable(true);

        this.exampleListView.getItems().setAll(node.exampleValues());
        this.exampleEditorPane.setDisable(false);
    }

    private Node createEditorPane() {
        TitledPane titledPane = new TitledPane("Edit Documentation & Javadoc", null);
        titledPane.setAnimated(true);
        titledPane.setExpanded(false);

        VBox editorContent = new VBox(15);
        editorContent.setPadding(new Insets(10, 5, 5, 5));

        Label docLabel = new Label("Documentation");
        docLabel.getStyleClass().add("h3");
        this.documentationTextArea = new TextArea(initialDoc);
        documentationTextArea.setPrefHeight(100.0);
        documentationTextArea.setWrapText(true);
        documentationTextArea.setPromptText("General documentation for the schema...");
        VBox docSection = new VBox(5, docLabel, documentationTextArea);
        docSection.setStyle("-fx-background-color: #f0f4f8; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #dfe6ee; -fx-border-radius: 8;");

        Label javadocLabel = new Label("Javadoc");
        javadocLabel.getStyleClass().add("h3");
        this.javadocTextArea = new TextArea(initialJavadoc);
        javadocTextArea.setPrefHeight(100.0);
        javadocTextArea.setWrapText(true);
        javadocTextArea.setPromptText("Enter Javadoc tags here, e.g.:\n@version 1.2.3\n@see http://example.com/docs\n@deprecated This schema is outdated.");

        VBox javadocSection = new VBox(5, javadocLabel, javadocTextArea);
        javadocSection.setStyle("-fx-background-color: #f0f8f0; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #dff0df; -fx-border-radius: 8;");

        this.saveDocumentationButton = new Button("Save Documentation");
        saveDocumentationButton.setGraphic(new FontIcon("bi-save"));
        saveDocumentationButton.setDisable(true);
        saveDocumentationButton.setOnAction(event -> controller.saveDocumentation(documentationTextArea.getText(), javadocTextArea.getText()));

        this.exampleEditorPane = new VBox(10);
        exampleEditorPane.setDisable(true);
        exampleEditorPane.setStyle("-fx-background-color: #fffaf0; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #faebd7; -fx-border-radius: 8;");

        Label exampleValuesLabel = new Label("Example Values");
        exampleValuesLabel.getStyleClass().add("h3");

        this.exampleListView = new ListView<>();
        exampleListView.setPrefHeight(80);
        exampleListView.setPlaceholder(new Label("No example values defined for this element."));

        HBox addExampleBox = new HBox(5);
        TextField newExampleField = new TextField();
        newExampleField.setPromptText("Enter a new example value and press Add");
        HBox.setHgrow(newExampleField, javafx.scene.layout.Priority.ALWAYS);
        Button addExampleButton = new Button("Add");
        addExampleButton.setOnAction(e -> {
            String newValue = newExampleField.getText();
            if (newValue != null && !newValue.isBlank()) {
                exampleListView.getItems().add(newValue);
                newExampleField.clear();
            }
        });
        addExampleBox.getChildren().addAll(newExampleField, addExampleButton);

        Button removeExampleButton = new Button("Remove Selected");
        removeExampleButton.setOnAction(e -> {
            String selected = exampleListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                exampleListView.getItems().remove(selected);
            }
        });

        exampleEditorPane.getChildren().addAll(exampleValuesLabel, exampleListView, addExampleBox, removeExampleButton);

        Runnable updateSaveButtonState = () -> {
            if (isEditingSchemaDoc) {
                boolean docChanged = !java.util.Objects.equals(documentationTextArea.getText(), initialDoc);
                boolean javadocChanged = !java.util.Objects.equals(javadocTextArea.getText(), initialJavadoc);
                boolean isDirty = docChanged || javadocChanged;
                saveDocumentationButton.setDisable(!isDirty || controller == null);
            } else {
                saveDocumentationButton.setDisable(true);
            }
        };

        documentationTextArea.textProperty().addListener((obs, ov, nv) -> updateSaveButtonState.run());
        javadocTextArea.textProperty().addListener((obs, ov, nv) -> updateSaveButtonState.run());

        Button editSchemaDocButton = getButton(updateSaveButtonState);

        Button saveExamplesButton = new Button("Save Examples");
        saveExamplesButton.setGraphic(new FontIcon("bi-save"));
        saveExamplesButton.setOnAction(e -> {
            if (selectedNode != null && controller != null) {
                controller.saveExampleValues(selectedNode.xpath(), new ArrayList<>(exampleListView.getItems()));
            }
        });

        HBox buttonBar = new HBox(10, saveDocumentationButton, saveExamplesButton, editSchemaDocButton);
        buttonBar.setStyle("-fx-margin-top: 10;");

        editorContent.getChildren().addAll(docSection, javadocSection, exampleEditorPane, buttonBar);

        titledPane.setContent(editorContent);
        return titledPane;
    }

    private @NotNull Button getButton(Runnable updateSaveButtonState) {
        Button editSchemaDocButton = new Button("Edit Schema Doc");
        editSchemaDocButton.setTooltip(new Tooltip("Switches to editing the main schema documentation, keeping the current text."));
        editSchemaDocButton.setOnAction(e -> {
            this.selectedNode = null;
            isEditingSchemaDoc = true;
            documentationTextArea.setEditable(true);
            javadocTextArea.setEditable(true);
            exampleEditorPane.setDisable(true);
            exampleListView.getItems().clear();
            updateSaveButtonState.run();
        });
        return editSchemaDocButton;
    }

    private void addDetailRow(GridPane grid, int rowIndex, String labelText, String valueText) {
        if (valueText == null) {
            valueText = "";
        }

        Label label = new Label(labelText);
        label.setStyle(DETAIL_LABEL_STYLE);
        GridPane.setValignment(label, VPos.TOP);

        Label value = new Label(valueText);
        value.setWrapText(true);
        value.setMaxWidth(300);

        grid.add(label, 0, rowIndex);
        grid.add(value, 1, rowIndex);
    }

    /**
     * Creates a type-specific icon based on XSD data type
     *
     * @param type The XSD type (e.g., "xs:string", "xs:date", etc.)
     * @return FontIcon with appropriate icon and color
     */
    private FontIcon createTypeSpecificIcon(String type) {
        String iconLiteral;
        String iconColor;

        if (type == null || type.isEmpty()) {
            // Default icon for unknown/empty types
            iconLiteral = "bi-box";
            iconColor = "#4a90e2";
        } else {
            // Remove namespace prefix if present (xs:string -> string)
            String cleanType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;

            switch (cleanType.toLowerCase()) {
                // String types
                case "string", "normalizedstring", "token", "nmtoken", "name", "ncname", "id", "idref", "idrefs",
                     "entity", "entities" -> {
                    iconLiteral = "bi-chat-quote";
                    iconColor = "#28a745"; // Green
                }

                // Numeric types
                case "int", "integer", "long", "short", "byte", "positiveinteger", "negativeinteger",
                     "nonpositiveinteger", "nonnegativeinteger", "unsignedlong", "unsignedint",
                     "unsignedshort", "unsignedbyte" -> {
                    iconLiteral = "bi-plus-circle";
                    iconColor = "#007bff"; // Blue
                }

                // Decimal/Float types
                case "decimal", "float", "double" -> {
                    iconLiteral = "bi-calculator";
                    iconColor = "#007bff"; // Blue
                }

                // Date/Time types
                case "date", "datetime", "time", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday", "duration" -> {
                    iconLiteral = "bi-calendar-date";
                    iconColor = "#fd7e14"; // Orange
                }

                // Boolean type
                case "boolean" -> {
                    iconLiteral = "bi-check-square";
                    iconColor = "#6f42c1"; // Purple
                }

                // Binary types
                case "base64binary", "hexbinary" -> {
                    iconLiteral = "bi-file-binary";
                    iconColor = "#6c757d"; // Gray
                }

                // URI type
                case "anyuri" -> {
                    iconLiteral = "bi-link-45deg";
                    iconColor = "#17a2b8"; // Cyan
                }

                // QName type
                case "qname" -> {
                    iconLiteral = "bi-tag";
                    iconColor = "#e83e8c"; // Pink
                }

                // Language type
                case "language" -> {
                    iconLiteral = "bi-globe";
                    iconColor = "#20c997"; // Teal
                }

                // Complex or custom types
                default -> {
                    if (cleanType.endsWith("type") || cleanType.contains("complex")) {
                        iconLiteral = "bi-diagram-3";
                        iconColor = "#dc3545"; // Red
                    } else {
                        // Default icon for unrecognized types
                        iconLiteral = "bi-box";
                        iconColor = "#4a90e2"; // Default blue
                    }
                }
            }
        }

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(iconColor));
        icon.setIconSize(14);
        return icon;
    }
}