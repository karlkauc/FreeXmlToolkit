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

    private VBox detailPane; // Der Container für die Detail-Informationen

    // Stile
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
            return new Label("Keine Element-Informationen gefunden.");
        }

        // Haupt-Layout ist ein SplitPane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.6); // 60% für den Baum, 40% für Details

        // Linke Seite: Der Diagramm-Baum in einem ScrollPane
        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));
        Node rootNodeView = createNodeView(rootNode, 0);
        diagramContainer.getChildren().add(rootNodeView);
        ScrollPane treeScrollPane = new ScrollPane(diagramContainer);
        treeScrollPane.setFitToWidth(true);

        // Rechte Seite: Der Detail-Bereich
        detailPane = new VBox(10);
        detailPane.setStyle(DETAIL_PANE_STYLE);
        Label placeholder = new Label("Klicken Sie auf einen Knoten, um Details anzuzeigen.");
        detailPane.getChildren().add(placeholder);
        ScrollPane detailScrollPane = new ScrollPane(detailPane);
        detailScrollPane.setFitToWidth(true);

        splitPane.getItems().addAll(treeScrollPane, detailScrollPane);
        return splitPane;
    }

    private Node createNodeView(XsdNodeInfo node, int depth) {
        VBox nodeContainer = new VBox(5);
        nodeContainer.setPadding(new Insets(0, 0, 0, depth * 20));

        HBox nameAndToggleRow = new HBox(5);
        nameAndToggleRow.setAlignment(Pos.CENTER_LEFT);

        VBox childrenContainer = new VBox(5);
        childrenContainer.setVisible(false);
        childrenContainer.setManaged(false);

        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(NODE_LABEL_STYLE);
        // Klick-Ereignis hinzufügen, um Details zu laden
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

        nodeContainer.getChildren().add(nameAndToggleRow);

        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docLabel = new Label(node.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            nodeContainer.getChildren().add(docLabel);
        }

        for (XsdNodeInfo childNode : node.children()) {
            childrenContainer.getChildren().add(createNodeView(childNode, depth + 1));
        }
        nodeContainer.getChildren().add(childrenContainer);
        return nodeContainer;
    }

    /**
     * Füllt den Detailbereich mit Informationen aus dem ausgewählten Element.
     * Diese Methode ist nun robust gegen fehlende Daten.
     */
    private void updateDetailPane(ExtendedXsdElement element) {
        detailPane.getChildren().clear();
        if (element == null) {
            detailPane.getChildren().add(new Label("Bitte einen Knoten auswählen."));
            return;
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        int rowIndex = 0;

        // getDisplayName() verwenden, um '@' bei Attributen zu sehen
        addDetailRow(grid, rowIndex++, "Name:", element.getElementName());
        addDetailRow(grid, rowIndex++, "XPath:", element.getCurrentXpath());
        addDetailRow(grid, rowIndex++, "Datentyp:", element.getElementType());

        if (element.getGenericAppInfos() != null && !element.getGenericAppInfos().isEmpty()) {
            addDetailRow(grid, rowIndex++, "Kardinalität:", element.getGenericAppInfos().toString());
        }

        if (element.getXsdDocumentation() != null && !element.getXsdDocumentation().isEmpty()) {
            String docText = element.getXsdDocumentation().getFirst().getContent();
            addDetailRow(grid, rowIndex++, "Dokumentation:", docText);
        }

        // KORREKTUR: Die Methode sicher aufrufen, um Abstürze zu vermeiden.
        String restrictions = element.getXsdRestrictionString();
        if (restrictions != null && !restrictions.isEmpty()) {
            addDetailRow(grid, rowIndex++, "Einschränkungen:", restrictions);
        }

        if (element.getSampleData() != null) {
            addDetailRow(grid, rowIndex++, "Beispieldaten:", element.getSampleData());
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
     * Fügt eine Zeile zum Detail-Grid hinzu.
     * Diese Methode ist nun null-sicher und hat ein verbessertes Layout.
     */
    private void addDetailRow(GridPane grid, int rowIndex, String labelText, String valueText) {
        // Null-Werte abfangen, um Abstürze zu verhindern
        if (valueText == null) {
            valueText = ""; // Leeren String verwenden statt null
        }

        Label label = new Label(labelText);
        label.setStyle(DETAIL_LABEL_STYLE);
        // KORREKTUR: VPos.TOP ist besser für die Ausrichtung bei mehrzeiligen Werten
        GridPane.setValignment(label, VPos.TOP);

        Text value = new Text(valueText);
        value.setWrappingWidth(300); // Passt die Breite an

        grid.add(label, 0, rowIndex);
        grid.add(value, 1, rowIndex);
    }
}