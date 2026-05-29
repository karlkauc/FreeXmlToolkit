package org.fxt.freexmltoolkit.controls.shell.inspector;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathCalculator;

/**
 * The Unified shell inspector (UI rebuild Phase 3). Renders the four required
 * sections and keeps them identical across views. In Text mode it fills the
 * "Node &amp; XPath" section from the active editor's caret position (debounced);
 * the type/facets, cardinality and documentation sections become live once the
 * schema-aware views land in later phases.
 */
public class InspectorPanel extends VBox {

    private static final String PLACEHOLDER = "—";

    private final EditorHost editorHost;
    private final XPathCalculator xpathCalculator = new XPathCalculator();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(180));

    private final Label kindValue = value();
    private final Label nameValue = value();
    private final Label xpathValue = value();
    private final Label depthValue = value();

    public InspectorPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-inspector");
        setPrefWidth(384);
        setMinWidth(384);

        Label header = new Label("PROPERTIES");
        header.getStyleClass().add("fxt-inspector-header");
        getChildren().add(header);

        getChildren().add(section("Node & XPath", buildNodeXPathBody()));
        getChildren().add(section("Type & Facets", placeholderBody()));
        getChildren().add(section("Cardinality & Use", placeholderBody()));
        getChildren().add(section("Documentation & Refs", placeholderBody()));

        debounce.setOnFinished(e -> refresh());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        refresh();
    }

    private GridPane buildNodeXPathBody() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("fxt-inspector-grid");
        grid.setHgap(8);
        grid.setVgap(4);
        ColumnConstraints keyCol = new ColumnConstraints();
        keyCol.setMinWidth(70);
        ColumnConstraints valCol = new ColumnConstraints();
        valCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyCol, valCol);

        addRow(grid, 0, "Kind", kindValue);
        addRow(grid, 1, "Name", nameValue);
        addRow(grid, 2, "XPath", xpathValue);
        addRow(grid, 3, "Depth", depthValue);
        return grid;
    }

    private void addRow(GridPane grid, int row, String key, Label valueLabel) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("fxt-inspector-key");
        grid.add(keyLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private void refresh() {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            setNodeXPath(PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER);
            return;
        }
        if (docOpt.get().getFileType() == EditorFileType.JSON) {
            // JSON uses JSONPath, not XPath; handled when the JSON view lands.
            setNodeXPath("JSON", PLACEHOLDER, PLACEHOLDER, PLACEHOLDER);
            return;
        }
        String text = editorHost.getActiveText().orElse("");
        int caret = editorHost.activeCaretProperty().get();
        NodeXPathInfo info = NodeXPathInfo.fromCaret(xpathCalculator, text, caret);
        setNodeXPath(info.kind(),
                info.name().isEmpty() ? PLACEHOLDER : info.name(),
                info.xpath(),
                Integer.toString(info.depth()));
    }

    private void setNodeXPath(String kind, String name, String xpath, String depth) {
        kindValue.setText(kind);
        nameValue.setText(name);
        xpathValue.setText(xpath);
        depthValue.setText(depth);
    }

    private TitledPane section(String title, javafx.scene.Node body) {
        TitledPane pane = new TitledPane(title, body);
        pane.setExpanded(true);
        pane.getStyleClass().add("fxt-inspector-section");
        return pane;
    }

    private Label placeholderBody() {
        Label label = new Label(PLACEHOLDER);
        label.getStyleClass().add("fxt-placeholder-text");
        return label;
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
}
