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

    private VBox detailPane; // The container for the detail information
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
            "-fx-background-color: #eef4ff; -fx-border-color: #adc8ff; -fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 5px 10px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d47a1; -fx-cursor: hand;";

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

        // Main content area is a SplitPane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.8); // 80% for the tree, 20% for details

        // Left side: The diagram tree in a ScrollPane
        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));
        diagramContainer.setAlignment(Pos.CENTER_LEFT);
        Node rootNodeView = createNodeView(rootNode, 0);
        diagramContainer.getChildren().add(rootNodeView);
        ScrollPane treeScrollPane = new ScrollPane(diagramContainer);
        treeScrollPane.setFitToWidth(true);
        treeScrollPane.setFitToHeight(true);

        // Right side: Use a BorderPane to pin the editor to the bottom.
        BorderPane rightPaneLayout = new BorderPane();
        rightPaneLayout.setStyle(DETAIL_PANE_STYLE);

        // Center: Scrollable detail view.
        detailPane = new VBox(10); // Container for node-specific details
        Label placeholder = new Label("Click on a node to view details.");
        detailPane.getChildren().add(placeholder);

        ScrollPane detailScrollPane = new ScrollPane(detailPane);
        detailScrollPane.setFitToWidth(true);
        detailScrollPane.setFitToHeight(true);
        // Make the scrollpane transparent, as the BorderPane provides the background and border
        detailScrollPane.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        rightPaneLayout.setCenter(detailScrollPane);

        // Bottom: The editor pane.
        Node editorPane = createEditorPane(); // The TitledPane
        BorderPane.setMargin(editorPane, new Insets(10, 0, 0, 0)); // Add some space above the editor
        rightPaneLayout.setBottom(editorPane);

        splitPane.getItems().addAll(treeScrollPane, rightPaneLayout);
        return splitPane;
    }

    private Node createNodeView(XsdNodeInfo node, int depth) {
        // The spacing between the parent block and the children block is set to 15.
        HBox nodeContainer = new HBox(15);
        // Vertically centers the child elements next to the parent element.
        nodeContainer.setAlignment(Pos.CENTER_LEFT);

        // A dedicated VBox for the parent element's information (name, doc).
        // This allows us to treat them as a single unit.
        VBox parentInfoContainer = new VBox(5);

        HBox nameAndToggleRow = new HBox(5);
        nameAndToggleRow.setAlignment(Pos.CENTER_LEFT);

        VBox childrenContainer = new VBox(5);
        childrenContainer.setVisible(false);
        childrenContainer.setManaged(false);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(NODE_LABEL_STYLE);
        nameLabel.setOnMouseClicked(event -> {
            updateDetailPane(node);
        });
        // Label für Kardinalität erstellen und hinzufügen
        Label cardinalityLabel = new Label(formatCardinality(node.minOccurs(), node.maxOccurs()));
        cardinalityLabel.setStyle(CARDINALITY_LABEL_STYLE);

        // Das Label für den Namen und die Kardinalität in die Zeile einfügen
        nameAndToggleRow.getChildren().addAll(nameLabel, cardinalityLabel);

        if (!node.children().isEmpty()) {
            Label toggleButton = new Label("+");
            toggleButton.setStyle(TOGGLE_BUTTON_STYLE);
            final boolean[] isExpanded = {false};
            toggleButton.setOnMouseClicked(event -> {
                isExpanded[0] = !isExpanded[0];
                childrenContainer.setVisible(isExpanded[0]);
                childrenContainer.setManaged(isExpanded[0]);
                toggleButton.setText(isExpanded[0] ? "−" : "+");
            });
            nameAndToggleRow.getChildren().add(toggleButton);
        }

        // Add the row with name and toggle to the parent container
        parentInfoContainer.getChildren().add(nameAndToggleRow);

        // Also add the documentation (if available) to the parent container
        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docLabel = new Label(node.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            parentInfoContainer.getChildren().add(docLabel);
        }

        // Fill the container for the child elements
        for (XsdNodeInfo childNode : node.children()) {
            // The indentation (depth) is less relevant for the new structure,
            // but we keep it for the recursion.
            childrenContainer.getChildren().add(createNodeView(childNode, depth + 1));
        }

        // Add the parent block and the children block to the main HBox container
        nodeContainer.getChildren().addAll(parentInfoContainer, childrenContainer);

        return nodeContainer;
    }

    /**
     * HINZUGEFÜGT: Eine Hilfsmethode, um die Kardinalität schön zu formatieren.
     *
     * @param minOccurs Die minOccurs-Zeichenkette.
     * @param maxOccurs Die maxOccurs-Zeichenkette.
     * @return Ein formatierter String wie "[1..*]" oder "[1..1]".
     */
    private String formatCardinality(String minOccurs, String maxOccurs) {
        // Standardwerte setzen, falls null
        String min = (minOccurs == null) ? "1" : minOccurs;
        String max = (maxOccurs == null) ? "1" : maxOccurs;

        // "unbounded" durch "*" ersetzen für eine kompaktere Darstellung
        if ("unbounded".equalsIgnoreCase(max)) {
            max = "*";
        }

        return String.format("[%s..%s]", min, max);
    }

    /**
     * Fills the detail area with information from the selected element.
     * This version uses only the lightweight XsdNodeInfo.
     */
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

            // Use a WebView to render potentially HTML-formatted documentation
            WebView docView = new WebView();
            docView.getEngine().loadContent("<html><body style='font-family: sans-serif; font-size: 13px;'>" + node.documentation() + "</body></html>");
            docView.setPrefHeight(200); // Give it some initial size
            detailPane.getChildren().add(docView);
        }

        // Update the editor pane with the documentation of the selected node
        this.selectedNode = node;
        isEditingSchemaDoc = false; // Switch to "viewing" mode
        this.documentationTextArea.setText(node.documentation() != null ? node.documentation() : "");
        this.javadocTextArea.setText(""); // Sub-nodes don't have separate Javadoc
        // When viewing a node's doc, make the editor read-only and disable saving.
        this.documentationTextArea.setEditable(false);
        this.javadocTextArea.setEditable(false);
        this.saveDocumentationButton.setDisable(true);

        // Populate and enable the example editor
        this.exampleListView.getItems().setAll(node.exampleValues());
        this.exampleEditorPane.setDisable(false);
    }

    private Node createEditorPane() {
        TitledPane titledPane = new TitledPane("Edit Documentation & Javadoc", null);
        titledPane.setAnimated(true);
        titledPane.setExpanded(false);

        // The main container for all editor sections, with increased spacing
        VBox editorContent = new VBox(15);
        editorContent.setPadding(new Insets(10, 5, 5, 5));

        // --- Documentation Section ---
        Label docLabel = new Label("Documentation");
        docLabel.getStyleClass().add("h3");
        this.documentationTextArea = new TextArea(initialDoc);
        documentationTextArea.setPrefHeight(100.0);
        documentationTextArea.setWrapText(true);
        documentationTextArea.setPromptText("General documentation for the schema...");
        VBox docSection = new VBox(5, docLabel, documentationTextArea);
        docSection.setStyle("-fx-background-color: #f0f4f8; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #dfe6ee; -fx-border-radius: 8;");

        // --- Javadoc Section ---
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
        saveDocumentationButton.setDisable(true); // Start disabled, as content is not "dirty" yet
        saveDocumentationButton.setOnAction(event -> controller.saveDocumentation(documentationTextArea.getText(), javadocTextArea.getText()));

        // --- Example Values Section ---
        this.exampleEditorPane = new VBox(10);
        exampleEditorPane.setDisable(true); // Disabled by default
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

        // Add listeners to enable the save button on change, but only in schema-editing mode.
        Runnable updateSaveButtonState = () -> {
            if (isEditingSchemaDoc) {
                // KORREKTUR: Verwende Objects.equals für einen null-sicheren Vergleich.
                boolean docChanged = !java.util.Objects.equals(documentationTextArea.getText(), initialDoc);
                boolean javadocChanged = !java.util.Objects.equals(javadocTextArea.getText(), initialJavadoc);
                boolean isDirty = docChanged || javadocChanged;
                saveDocumentationButton.setDisable(!isDirty || controller == null); // Nur aktivieren, wenn "dirty" und Controller vorhanden
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
            isEditingSchemaDoc = true; // Switch back to editing mode
            // Re-enable editing
            documentationTextArea.setEditable(true);
            javadocTextArea.setEditable(true);
            // Disable and clear example editor
            exampleEditorPane.setDisable(true);
            exampleListView.getItems().clear();
            // Check immediately if the current content is different from the original schema doc
            // to enable the save button if necessary.
            updateSaveButtonState.run();
        });
        return editSchemaDocButton;
    }

    /**
     * Adds a row to the detail grid.
     * This method is now null-safe and has an improved layout.
     */
    private void addDetailRow(GridPane grid, int rowIndex, String labelText, String valueText) {
        // Handle null values to prevent crashes
        if (valueText == null) {
            valueText = ""; // Use an empty string instead of null
        }

        Label label = new Label(labelText);
        label.setStyle(DETAIL_LABEL_STYLE);
        GridPane.setValignment(label, VPos.TOP);

        Label value = new Label(valueText);
        value.setWrapText(true);
        value.setMaxWidth(300); // Adjusts the width

        grid.add(label, 0, rowIndex);
        grid.add(value, 1, rowIndex);
    }
}