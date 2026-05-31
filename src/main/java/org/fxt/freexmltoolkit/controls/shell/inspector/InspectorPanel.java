package org.fxt.freexmltoolkit.controls.shell.inspector;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathCalculator;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * The Unified shell inspector. Renders the four required sections, kept identical
 * across views. Two data sources feed it, debounced:
 * <ul>
 *   <li><b>Structured selection</b> (Tree/Graphic): the selected {@link XsdNode}
 *       fills all sections via {@link SelectedNodeInfo} (the real model).</li>
 *   <li><b>Text caret</b> (Text mode, no selection): the Node &amp; XPath section
 *       is derived from the caret via {@link NodeXPathInfo}.</li>
 * </ul>
 */
public class InspectorPanel extends VBox {

    private static final String PLACEHOLDER = "—";

    private final EditorHost editorHost;
    private final XPathCalculator xpathCalculator = new XPathCalculator();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(150));

    private final Label kindValue = value();
    private final Label nameValue = value();
    private final Label xpathValue = value();
    private final Label depthValue = value();
    private final Label typeValue = value();
    private final Label cardinalityValue = value();
    private final Label useValue = value();
    private final Label docValue = value();
    private final TableView<XsdFacet> facetTable = new TableView<>();

    public InspectorPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-inspector");
        setPrefWidth(384);
        setMinWidth(384);

        Label header = new Label("PROPERTIES");
        header.getStyleClass().add("fxt-inspector-header");
        getChildren().add(header);

        getChildren().add(section("Node & XPath", grid(
                "Kind", kindValue, "Name", nameValue, "XPath", xpathValue, "Depth", depthValue)));
        getChildren().add(section("Type & Facets", buildTypeFacetsBody()));
        getChildren().add(section("Cardinality & Use", grid(
                "Cardinality", cardinalityValue, "Use", useValue)));
        getChildren().add(section("Documentation & Refs", grid("Docs", docValue)));

        debounce.setOnFinished(e -> refresh());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeSelectedNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        refresh();
    }

    @SuppressWarnings("unchecked")
    private javafx.scene.Node buildTypeFacetsBody() {
        TableColumn<XsdFacet, String> nameCol = new TableColumn<>("Facet");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getFacetType().getXmlName()));
        nameCol.setPrefWidth(140);
        TableColumn<XsdFacet, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getValue()));
        valueCol.setPrefWidth(180);
        facetTable.getColumns().setAll(nameCol, valueCol);
        facetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        facetTable.setPrefHeight(120);
        facetTable.getStyleClass().add("fxt-facet-table");
        facetTable.setPlaceholder(new Label("No facets"));

        VBox body = new VBox(4, grid("Type", typeValue), facetTable);
        return body;
    }

    private void updateFacets(XsdNode node) {
        facetTable.getItems().setAll(node != null ? SchemaFacets.collect(node) : FXCollections.emptyObservableList());
    }

    private void refresh() {
        XsdNode selected = editorHost.activeSelectedNodeProperty().get();
        updateFacets(selected);
        if (selected != null) {
            SelectedNodeInfo info = SelectedNodeInfo.of(selected);
            set(info.kind(), blankToPlaceholder(info.name()), info.xpath(), Integer.toString(info.depth()),
                    info.type(), info.cardinality(), info.use(), info.documentation());
            return;
        }
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            clear();
            return;
        }
        if (docOpt.get().getFileType() == EditorFileType.JSON) {
            set("JSON", PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER);
            return;
        }
        // Text mode: caret-derived Node & XPath; other sections need the model.
        String text = editorHost.getActiveText().orElse("");
        int caret = editorHost.activeCaretProperty().get();
        NodeXPathInfo info = NodeXPathInfo.fromCaret(xpathCalculator, text, caret);
        set(info.kind(), blankToPlaceholder(info.name()), info.xpath(), Integer.toString(info.depth()),
                PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER);
    }

    private void clear() {
        set(PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER);
    }

    private void set(String kind, String name, String xpath, String depth,
                     String type, String cardinality, String use, String doc) {
        kindValue.setText(kind);
        nameValue.setText(name);
        xpathValue.setText(xpath);
        depthValue.setText(depth);
        typeValue.setText(type);
        cardinalityValue.setText(cardinality);
        useValue.setText(use);
        docValue.setText(doc);
    }

    private String blankToPlaceholder(String s) {
        return (s == null || s.isBlank()) ? PLACEHOLDER : s;
    }

    private TitledPane section(String title, javafx.scene.Node body) {
        TitledPane pane = new TitledPane(title, body);
        pane.setExpanded(true);
        pane.getStyleClass().add("fxt-inspector-section");
        return pane;
    }

    /** Builds a two-column key/value grid from alternating key, Label arguments. */
    private GridPane grid(Object... keyValuePairs) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("fxt-inspector-grid");
        grid.setHgap(8);
        grid.setVgap(4);
        ColumnConstraints keyCol = new ColumnConstraints();
        keyCol.setMinWidth(80);
        ColumnConstraints valCol = new ColumnConstraints();
        valCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyCol, valCol);

        for (int i = 0, row = 0; i < keyValuePairs.length; i += 2, row++) {
            Label key = new Label((String) keyValuePairs[i]);
            key.getStyleClass().add("fxt-inspector-key");
            grid.add(key, 0, row);
            grid.add((Label) keyValuePairs[i + 1], 1, row);
        }
        return grid;
    }

    private Label value() {
        Label label = new Label(PLACEHOLDER);
        label.getStyleClass().add("fxt-inspector-value");
        label.setWrapText(true);
        return label;
    }

    /** @return the current "Node &amp; XPath" XPath value (for tests/observers). */
    public String getXPathText() {
        return xpathValue.getText();
    }

    /** @return the current node name value (for tests/observers). */
    public String getNodeNameText() {
        return nameValue.getText();
    }

    /** @return the current type value (for tests/observers). */
    public String getTypeText() {
        return typeValue.getText();
    }

    /** @return the current cardinality value (for tests/observers). */
    public String getCardinalityText() {
        return cardinalityValue.getText();
    }

    /** @return the number of facets currently shown (for tests/observers). */
    public int getFacetCount() {
        return facetTable.getItems().size();
    }
}
