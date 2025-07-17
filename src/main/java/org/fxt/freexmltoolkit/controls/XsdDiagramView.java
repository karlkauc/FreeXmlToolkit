package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.controller.XsdController;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.kordamp.ikonli.javafx.FontIcon;

public class XsdDiagramView {

    private final XsdNodeInfo rootNode;
    private final XsdController controller;
    private final String initialDoc;
    private final String initialJavadoc;

    private VBox detailPane; // The container for the detail information
    private TextArea documentationTextArea;
    private TextArea javadocTextArea;
    private Button saveDocumentationButton;
    private boolean isEditingSchemaDoc = true; // NEW: State flag to track if we are editing the main schema doc

    // Styles
    private static final String NODE_LABEL_STYLE =
            "-fx-background-color: #eef4ff; -fx-border-color: #adc8ff; -fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 5px 10px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d47a1; -fx-cursor: hand;";

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

        // Main layout is a SplitPane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.8); // 80% for the tree, 20% for details

        // Left side: The diagram tree in a ScrollPane
        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));
        // Aligns the entire diagram tree to the left-center
        diagramContainer.setAlignment(Pos.CENTER_LEFT);
        Node rootNodeView = createNodeView(rootNode, 0);
        diagramContainer.getChildren().add(rootNodeView);
        ScrollPane treeScrollPane = new ScrollPane(diagramContainer);
        treeScrollPane.setFitToWidth(true);
        // Tells the ScrollPane that the content (our VBox)
        // should fill the entire available height.
        treeScrollPane.setFitToHeight(true);

        // Right side: The detail area and the editor
        VBox rightPaneContainer = new VBox();
        rightPaneContainer.setStyle(DETAIL_PANE_STYLE);

        detailPane = new VBox(10); // Container for node-specific details
        Label placeholder = new Label("Click on a node to view details.");
        detailPane.getChildren().add(placeholder);

        Node editorPane = createEditorPane(); // The new editor TitledPane

        // Add both to the main right-side container
        rightPaneContainer.getChildren().addAll(detailPane, editorPane);

        ScrollPane detailScrollPane = new ScrollPane(rightPaneContainer);
        detailScrollPane.setFitToWidth(true);
        detailScrollPane.setFitToHeight(true);

        splitPane.getItems().addAll(treeScrollPane, detailScrollPane);
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
        nameAndToggleRow.getChildren().add(nameLabel);

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
        isEditingSchemaDoc = false; // Switch to "viewing" mode
        this.documentationTextArea.setText(node.documentation() != null ? node.documentation() : "");
        this.javadocTextArea.setText(""); // Sub-nodes don't have separate Javadoc
        // When viewing a node's doc, make the editor read-only and disable saving.
        this.documentationTextArea.setEditable(false);
        this.javadocTextArea.setEditable(false);
        this.saveDocumentationButton.setDisable(true);
    }

    private Node createEditorPane() {
        TitledPane titledPane = new TitledPane("Edit Documentation & Javadoc", null);
        titledPane.setAnimated(true);
        titledPane.setExpanded(false);
        // Ensures the TitledPane has some vertical padding from the content above it.
        VBox.setMargin(titledPane, new Insets(20, 0, 0, 0));

        VBox editorContent = new VBox(10);
        editorContent.setPadding(new Insets(10, 5, 5, 5));

        Label docLabel = new Label("Documentation");
        docLabel.getStyleClass().add("h3");
        this.documentationTextArea = new TextArea(initialDoc);
        documentationTextArea.setPrefHeight(100.0);
        documentationTextArea.setWrapText(true);
        documentationTextArea.setPromptText("General documentation for the schema...");

        Label javadocLabel = new Label("Javadoc");
        javadocLabel.getStyleClass().add("h3");
        javadocLabel.setStyle("-fx-padding-top: 10;");
        this.javadocTextArea = new TextArea(initialJavadoc);
        javadocTextArea.setPrefHeight(100.0);
        javadocTextArea.setWrapText(true);
        javadocTextArea.setPromptText("Enter Javadoc tags here, e.g.:\n@version 1.2.3\n@see http://example.com/docs\n@deprecated This schema is outdated.");

        this.saveDocumentationButton = new Button("Save Documentation");
        saveDocumentationButton.setGraphic(new FontIcon("bi-save"));
        saveDocumentationButton.setDisable(true); // Start disabled, as content is not "dirty" yet
        saveDocumentationButton.setOnAction(event -> controller.saveDocumentation(documentationTextArea.getText(), javadocTextArea.getText()));

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

        Button editSchemaDocButton = new Button("Edit Schema Doc");
        editSchemaDocButton.setTooltip(new Tooltip("Switches to editing the main schema documentation, keeping the current text."));
        editSchemaDocButton.setOnAction(e -> {
            isEditingSchemaDoc = true; // Switch back to editing mode
            // Re-enable editing
            documentationTextArea.setEditable(true);
            javadocTextArea.setEditable(true);
            // Check immediately if the current content is different from the original schema doc
            // to enable the save button if necessary.
            updateSaveButtonState.run();
        });

        HBox buttonBar = new HBox(10, saveDocumentationButton, editSchemaDocButton);
        buttonBar.setStyle("-fx-margin-top: 10;");

        editorContent.getChildren().addAll(docLabel, documentationTextArea, javadocLabel, javadocTextArea, buttonBar);

        titledPane.setContent(editorContent);
        return titledPane;
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