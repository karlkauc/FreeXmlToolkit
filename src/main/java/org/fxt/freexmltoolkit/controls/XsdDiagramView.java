package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.controller.XsdController;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;

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

    // Styles
    private static final String NODE_LABEL_STYLE =
            "-fx-background-color: #eef4ff; -fx-border-color: #adc8ff; -fx-border-width: 1px; " + "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 5px 10px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d47a1; -fx-cursor: hand;";

    private static final String SEQUENCE_NODE_STYLE =
            "-fx-background-color: #f5f5f5; -fx-border-color: #bdbdbd; -fx-border-width: 1px; -fx-border-style: solid; " +
                    "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 5px 10px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #424242;";

    private static final String CHOICE_NODE_STYLE =
            "-fx-background-color: #fff8e1; -fx-border-color: #ffc107; -fx-border-width: 1px; -fx-border-style: dashed; " +
                    "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 5px 10px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #856404;";

    private static final String CARDINALITY_LABEL_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #555; -fx-font-family: 'Consolas', 'Monaco', monospace;";

    private static final String TOGGLE_BUTTON_STYLE =
            "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0d47a1; -fx-padding: 0 5px; -fx-cursor: hand;";

    private static final String DOC_LABEL_STYLE =
            "-fx-font-size: 11px; -fx-text-fill: #6c757d; -fx-font-style: italic; -fx-padding: 2px 0 0 5px;";

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
        splitPane.setDividerPositions(0.8);

        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));
        diagramContainer.setAlignment(Pos.CENTER_LEFT);
        Node rootNodeView = createNodeView(rootNode, 0);
        diagramContainer.getChildren().add(rootNodeView);
        ScrollPane treeScrollPane = new ScrollPane(diagramContainer);
        treeScrollPane.setFitToWidth(true);
        treeScrollPane.setFitToHeight(true);

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

    private Node createNodeView(XsdNodeInfo node, int depth) {
        return switch (node.nodeType()) {
            case ELEMENT, ATTRIBUTE, ANY -> createElementNodeView(node);
            case SEQUENCE -> createStructuralNodeView(node, "SEQUENCE", SEQUENCE_NODE_STYLE);
            case CHOICE -> createStructuralNodeView(node, "CHOICE", CHOICE_NODE_STYLE);
            default -> new Label("Unknown Node Type: " + node.name());
        };
    }

    private Node createElementNodeView(XsdNodeInfo node) {
        HBox nodeContainer = new HBox(15);
        nodeContainer.setAlignment(Pos.CENTER_LEFT);
        VBox parentInfoContainer = new VBox(5);
        HBox nameAndToggleRow = new HBox(5);
        nameAndToggleRow.setAlignment(Pos.CENTER_LEFT);
        VBox childrenContainer = new VBox(5);
        childrenContainer.setVisible(false);
        childrenContainer.setManaged(false);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(NODE_LABEL_STYLE);
        nameLabel.setOnMouseClicked(event -> updateDetailPane(node));

        Label cardinalityLabel = new Label(formatCardinality(node.minOccurs(), node.maxOccurs()));
        cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);

        nameAndToggleRow.getChildren().addAll(nameLabel, cardinalityLabel);

        if (!node.children().isEmpty()) {
            // GEÄNDERT: Die Logik zum Lazy Loading wird hier übergeben
            addToggleButton(nameAndToggleRow, childrenContainer, node);
        }

        parentInfoContainer.getChildren().add(nameAndToggleRow);

        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docLabel = new Label(node.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            parentInfoContainer.getChildren().add(docLabel);
        }

        // ENTFERNT: Die Schleife zum Erstellen der Kinder wird nicht mehr hier ausgeführt.
        // Sie wird jetzt bei Bedarf im Toggle-Button-Handler ausgeführt.

        nodeContainer.getChildren().addAll(parentInfoContainer, childrenContainer);
        return nodeContainer;
    }

    private Node createStructuralNodeView(XsdNodeInfo node, String title, String style) {
        VBox structuralContainer = new VBox(5);

        HBox titleRow = new HBox(5);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setStyle(style);

        Label titleLabel = new Label(title);
        Label cardinalityLabel = new Label(formatCardinality(node.minOccurs(), node.maxOccurs()));
        cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);

        titleRow.getChildren().addAll(titleLabel, cardinalityLabel);

        VBox childrenContainer = new VBox(5);
        childrenContainer.setPadding(new Insets(5, 0, 5, 20));
        childrenContainer.setVisible(false);
        childrenContainer.setManaged(false);

        if (!node.children().isEmpty()) {
            // GEÄNDERT: Die Logik zum Lazy Loading wird hier übergeben
            addToggleButton(titleRow, childrenContainer, node);
        }

        // ENTFERNT: Die Schleife zum Erstellen der Kinder wird nicht mehr hier ausgeführt.

        structuralContainer.getChildren().addAll(titleRow, childrenContainer);
        return structuralContainer;
    }

    /**
     * GEÄNDERT: Diese Methode enthält jetzt die Kernlogik für das Lazy Loading.
     * Sie fügt einen Toggle-Button hinzu, der die Kind-Knoten erst dann erstellt,
     * wenn er zum ersten Mal geklickt wird.
     *
     * @param parentRow         Die HBox, zu der der Button hinzugefügt wird.
     * @param childrenContainer Der VBox-Container, der die Kinder aufnehmen wird.
     * @param node              Das Datenmodell des Eltern-Knotens, dessen Kinder geladen werden sollen.
     */
    private void addToggleButton(HBox parentRow, VBox childrenContainer, XsdNodeInfo node) {
        Label toggleButton = new Label("+");
        toggleButton.setStyle(TOGGLE_BUTTON_STYLE);

        final boolean[] isExpanded = {false};
        final boolean[] childrenLoaded = {false}; // Flag, um zu prüfen, ob die Kinder schon geladen wurden

        toggleButton.setOnMouseClicked(event -> {
            isExpanded[0] = !isExpanded[0];

            // Nur beim ersten Expandieren die Kinder-UI-Elemente erstellen
            if (isExpanded[0] && !childrenLoaded[0]) {
                for (XsdNodeInfo childNode : node.children()) {
                    // Hier findet das "Lazy Loading" statt!
                    childrenContainer.getChildren().add(createNodeView(childNode, 0));
                }
                childrenLoaded[0] = true; // Markieren, dass die Kinder geladen sind
            }

            // Sichtbarkeit umschalten
            childrenContainer.setVisible(isExpanded[0]);
            childrenContainer.setManaged(isExpanded[0]);
            toggleButton.setText(isExpanded[0] ? "−" : "+");
        });
        parentRow.getChildren().add(toggleButton);
    }

    private String formatCardinality(String minOccurs, String maxOccurs) {
        String min = (minOccurs == null) ? "1" : minOccurs;
        String max = (maxOccurs == null) ? "1" : maxOccurs;
        if ("unbounded".equalsIgnoreCase(max)) {
            max = "*";
        }
        return String.format("[%s..%s]", min, max);
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
}