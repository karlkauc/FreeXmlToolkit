package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;

import java.util.Map;

public class XsdDiagramView {

    private final XsdNodeInfo rootNode;
    private final Map<String, ExtendedXsdElement> elementMap;

    private VBox detailPane; // The container for the detail information

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

    public XsdDiagramView(XsdNodeInfo rootNode, XsdDocumentationData documentationData) {
        this.rootNode = rootNode;
        this.elementMap = documentationData.getExtendedXsdElementMap();
    }

    public Node build() {
        if (rootNode == null) {
            return new Label("No element information found.");
        }

        // Main layout is a SplitPane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.6); // 60% for the tree, 40% for details

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

        // Right side: The detail area
        detailPane = new VBox(10);
        detailPane.setStyle(DETAIL_PANE_STYLE);
        Label placeholder = new Label("Click on a node to view details.");
        detailPane.getChildren().add(placeholder);
        ScrollPane detailScrollPane = new ScrollPane(detailPane);
        detailScrollPane.setFitToWidth(true);

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
            ExtendedXsdElement selectedElement = elementMap.get(node.xpath());
            updateDetailPane(selectedElement);
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
     * This method is now robust against missing data.
     */
    private void updateDetailPane(ExtendedXsdElement element) {
        detailPane.getChildren().clear();
        if (element == null) {
            detailPane.getChildren().add(new Label("Please select a node."));
            return;
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        int rowIndex = 0;

        // Use getDisplayName() to see '@' for attributes
        addDetailRow(grid, rowIndex++, "Name:", element.getElementName());
        addDetailRow(grid, rowIndex++, "XPath:", element.getCurrentXpath());
        addDetailRow(grid, rowIndex++, "Data Type:", element.getElementType());

        if (element.getGenericAppInfos() != null && !element.getGenericAppInfos().isEmpty()) {
            addDetailRow(grid, rowIndex++, "Cardinality:", element.getGenericAppInfos().toString());
        }

        if (element.getXsdDocumentation() != null && !element.getXsdDocumentation().isEmpty()) {
            String docText = element.getXsdDocumentation().getFirst().getContent();
            addDetailRow(grid, rowIndex++, "Documentation:", docText);
        }

        // Call the method safely to avoid crashes.
        String restrictions = element.getXsdRestrictionString();
        if (restrictions != null && !restrictions.isEmpty()) {
            addDetailRow(grid, rowIndex++, "Restrictions:", restrictions);
        }

        if (element.getSampleData() != null) {
            addDetailRow(grid, rowIndex++, "Sample Data:", element.getSampleData());
        }

        if (element.getJavadocInfo() != null && element.getJavadocInfo().hasData()) {
            if (element.getJavadocInfo().getSince() != null) {
                addDetailRow(grid, rowIndex++, "@since:", element.getJavadocInfo().getSince());
            }
            if (element.getJavadocInfo().getDeprecated() != null) {
                addDetailRow(grid, rowIndex++, "@deprecated:", element.getJavadocInfo().getDeprecated());
            }
        }

        detailPane.getChildren().add(grid);
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
        // VPos.CENTER is better for alignment with multi-line values
        GridPane.setValignment(label, VPos.CENTER);

        Text value = new Text(valueText);
        value.setWrappingWidth(300); // Adjusts the width

        grid.add(label, 0, rowIndex);
        grid.add(value, 1, rowIndex);
    }
}