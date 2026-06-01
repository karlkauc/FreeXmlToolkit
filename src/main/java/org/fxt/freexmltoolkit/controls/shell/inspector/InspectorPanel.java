package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathCalculator;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * The Unified shell inspector — the editable properties pane. It keeps the Figma flat
 * sections (Node&amp;XPath / Type&amp;Facets / Cardinality&amp;Use / Documentation&amp;Refs) but the
 * fields are editable in place for the selected XSD node and commit through the
 * {@link EditorHost} command stack (undo/redo + round-trip to text). An {@code updating}
 * guard prevents edits from re-firing while the panel repopulates from the model.
 */
public class InspectorPanel extends VBox {

    private static final String PLACEHOLDER = "—";
    private static final List<String> BUILTIN_TYPES = List.of(
            "xs:string", "xs:boolean", "xs:int", "xs:integer", "xs:long", "xs:decimal",
            "xs:double", "xs:float", "xs:date", "xs:dateTime", "xs:time", "xs:anyURI", "xs:QName");

    private final EditorHost editorHost;
    private final XPathCalculator xpathCalculator = new XPathCalculator();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(150));

    /** True while repopulating controls from the model — suppresses edit commits. */
    private boolean updating;
    private XsdNode currentXsdNode;

    private final Label nodeHeaderName = new Label(PLACEHOLDER);
    private final Label nodeHeaderKind = new Label();
    private final Label validationBadge = new Label();

    // Read-only values
    private final Label kindValue = roLabel();
    private final Label xpathValue = roLabel();
    private final Label depthValue = roLabel();
    // Editable controls
    private final TextField nameField = editField("inspector-name");
    private final ComboBox<String> typeCombo = editCombo("inspector-type");
    private final Spinner<Integer> minSpinner = intSpinner("inspector-min");
    private final Spinner<Integer> maxSpinner = intSpinner("inspector-max");
    private final CheckBox unboundedCheck = new CheckBox("unbounded");
    private final ComboBox<String> useCombo = editCombo("inspector-use");
    private final ComboBox<String> formCombo = editCombo("inspector-form");
    private final CheckBox nillableCheck = new CheckBox();
    private final CheckBox abstractCheck = new CheckBox();
    private final TextField fixedField = editField("inspector-fixed");
    private final TextField substField = editField("inspector-subst");
    private final TextArea docArea = new TextArea();
    private final TableView<XsdFacet> facetTable = new TableView<>();

    // Rows toggled by node type
    private HBox typeRow;
    private HBox minRow;
    private HBox maxRow;
    private HBox useRow;
    private HBox formRow;
    private HBox nillableRow;
    private HBox abstractRow;
    private HBox fixedRow;
    private HBox substRow;
    private VBox docBox;

    public InspectorPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-inspector");
        setPrefWidth(384);
        setMinWidth(384);

        Label header = new Label("PROPERTIES");
        header.getStyleClass().add("fxt-inspector-header");
        getChildren().add(header);

        nodeHeaderName.getStyleClass().add("fxt-inspector-node-name");
        nodeHeaderKind.getStyleClass().add("fxt-inspector-kind-chip");
        validationBadge.setId("inspector-validation-badge");
        validationBadge.setVisible(false);
        validationBadge.setManaged(false);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox nodeHeader = new HBox(8, nodeHeaderName, nodeHeaderKind, headerSpacer, validationBadge);
        nodeHeader.setAlignment(Pos.CENTER_LEFT);
        nodeHeader.getStyleClass().add("fxt-inspector-node-header");
        getChildren().add(nodeHeader);

        editorHost.validationStatusProperty().addListener((obs, oldV, newV) -> updateValidationBadge(newV));
        updateValidationBadge(editorHost.validationStatusProperty().get());

        getChildren().add(section("NODE & XPATH", new VBox(4,
                row("Kind", kindValue), row("Name", nameField), row("XPath", xpathValue), row("Depth", depthValue))));
        getChildren().add(section("TYPE & FACETS", buildTypeFacetsBody()));
        getChildren().add(section("CARDINALITY & USE", buildCardinalityBody()));
        getChildren().add(section("DOCUMENTATION & REFS", buildDocBody()));

        wireEditing();

        debounce.setOnFinished(e -> refresh());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeSelectedNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        refresh();
    }

    // ----- section bodies --------------------------------------------------

    @SuppressWarnings("unchecked")
    private Node buildTypeFacetsBody() {
        TableColumn<XsdFacet, String> nameCol = new TableColumn<>("Facet");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getFacetType().getXmlName()));
        nameCol.setPrefWidth(140);
        TableColumn<XsdFacet, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getValue()));
        valueCol.setPrefWidth(180);
        facetTable.getColumns().setAll(nameCol, valueCol);
        facetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        facetTable.setPrefHeight(120);
        facetTable.getStyleClass().add("fxt-facet-table");
        facetTable.setPlaceholder(new Label("No facets"));

        typeRow = row("Type", typeCombo);
        return new VBox(4, typeRow, facetTable);
    }

    private Node buildCardinalityBody() {
        HBox maxBox = new HBox(8, maxSpinner, unboundedCheck);
        maxBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(maxSpinner, Priority.ALWAYS);
        minRow = row("minOccurs", minSpinner);
        maxRow = row("maxOccurs", maxBox);
        useRow = row("Use", useCombo);
        formRow = row("Form", formCombo);
        nillableRow = row("Nillable", nillableCheck);
        abstractRow = row("Abstract", abstractCheck);
        fixedRow = row("Fixed", fixedField);
        substRow = row("Subst. Group", substField);
        return new VBox(4, minRow, maxRow, useRow, formRow, nillableRow, abstractRow, fixedRow, substRow);
    }

    private Node buildDocBody() {
        docArea.setId("inspector-doc");
        docArea.getStyleClass().add("fxt-inspector-edit");
        docArea.setWrapText(true);
        docArea.setPrefRowCount(3);
        docBox = new VBox(docArea);
        return docBox;
    }

    private void wireEditing() {
        useCombo.getItems().setAll("optional", "required", "prohibited");
        formCombo.getItems().setAll("", "qualified", "unqualified");
        minSpinner.setEditable(true);
        maxSpinner.setEditable(true);

        commitOnEnterAndBlur(nameField, () -> editorHost.renameActiveNode(nameField.getText().trim()));
        typeCombo.setOnAction(e -> commit(() -> editorHost.changeActiveType(comboText(typeCombo))));
        minSpinner.valueProperty().addListener((o, ov, nv) -> commit(this::commitCardinality));
        maxSpinner.valueProperty().addListener((o, ov, nv) -> commit(this::commitCardinality));
        unboundedCheck.selectedProperty().addListener((o, ov, nv) -> {
            maxSpinner.setDisable(nv);
            commit(this::commitCardinality);
        });
        useCombo.valueProperty().addListener((o, ov, nv) -> commit(() -> editorHost.changeActiveUse(nv)));
        formCombo.valueProperty().addListener((o, ov, nv) ->
                commit(() -> editorHost.changeActiveForm(nv == null ? "" : nv)));
        nillableCheck.selectedProperty().addListener((o, ov, nv) -> commit(this::commitConstraints));
        abstractCheck.selectedProperty().addListener((o, ov, nv) -> commit(this::commitConstraints));
        commitOnEnterAndBlur(fixedField, this::commitConstraints);
        commitOnEnterAndBlur(substField, () -> editorHost.changeActiveSubstitutionGroup(substField.getText().trim()));
        docArea.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit(() -> editorHost.changeActiveDocumentation(docArea.getText()));
            }
        });
    }

    private void commitCardinality() {
        int min = minSpinner.getValue();
        int max = unboundedCheck.isSelected() ? XsdNode.UNBOUNDED : maxSpinner.getValue();
        editorHost.changeActiveCardinality(min, max);
    }

    private void commitConstraints() {
        String fixed = fixedField.getText() == null || fixedField.getText().isBlank() ? null : fixedField.getText();
        editorHost.changeActiveConstraints(nillableCheck.isSelected(), abstractCheck.isSelected(), fixed);
    }

    /** Runs an edit only if it is a genuine user change (not a programmatic repopulate) on a node. */
    private void commit(Runnable edit) {
        if (!updating && currentXsdNode != null) {
            edit.run();
        }
    }

    // ----- refresh / populate ---------------------------------------------

    private void refresh() {
        XsdNode selected = editorHost.activeSelectedNodeProperty().get();
        currentXsdNode = selected;
        updateFacets(selected);
        updating = true;
        try {
            if (selected != null) {
                populateXsdNode(selected);
            } else {
                populateCaret();
            }
        } finally {
            updating = false;
        }
    }

    private void populateXsdNode(XsdNode node) {
        SelectedNodeInfo info = SelectedNodeInfo.of(node);
        setHeader(blankToPlaceholder(info.name()), info.kind());
        kindValue.setText(info.kind());
        xpathValue.setText(info.xpath());
        depthValue.setText(Integer.toString(info.depth()));

        boolean isElement = node instanceof XsdElement;
        boolean isAttribute = node instanceof XsdAttribute;

        nameField.setEditable(true);
        nameField.setText(blankToPlaceholder(info.name()).equals(PLACEHOLDER) ? "" : info.name());

        // Type
        List<String> types = new ArrayList<>(BUILTIN_TYPES);
        for (XsdNode t : editorHost.getActiveNamedTypes()) {
            if (t.getName() != null && !types.contains(t.getName())) {
                types.add(t.getName());
            }
        }
        typeCombo.getItems().setAll(types);
        typeCombo.setValue(PLACEHOLDER.equals(info.type()) ? "" : info.type());
        typeCombo.setDisable(false);
        showRow(typeRow, isElement || isAttribute);

        // Cardinality (elements only)
        int min = node.getMinOccurs();
        int max = node.getMaxOccurs();
        minSpinner.getValueFactory().setValue(Math.max(0, min));
        boolean unbounded = max == XsdNode.UNBOUNDED || max >= Integer.MAX_VALUE;
        unboundedCheck.setSelected(unbounded);
        maxSpinner.setDisable(unbounded);
        maxSpinner.getValueFactory().setValue(unbounded ? Math.max(1, min) : Math.max(0, max));
        showRow(minRow, isElement);
        showRow(maxRow, isElement);

        // Use (attributes only)
        if (isAttribute) {
            String use = ((XsdAttribute) node).getUse();
            useCombo.setValue(use == null || use.isBlank() ? "optional" : use);
        }
        showRow(useRow, isAttribute);

        // Form (elements + attributes)
        String form = isElement ? ((XsdElement) node).getForm() : isAttribute ? ((XsdAttribute) node).getForm() : null;
        formCombo.setValue(form == null ? "" : form);
        showRow(formRow, isElement || isAttribute);

        // Element-only constraints
        if (isElement) {
            XsdElement el = (XsdElement) node;
            nillableCheck.setSelected(el.isNillable());
            abstractCheck.setSelected(el.isAbstract());
            fixedField.setText(el.getFixed() == null ? "" : el.getFixed());
            substField.setText(el.getSubstitutionGroup() == null ? "" : el.getSubstitutionGroup());
        } else if (isAttribute) {
            fixedField.setText(((XsdAttribute) node).getFixed() == null ? "" : ((XsdAttribute) node).getFixed());
        }
        showRow(nillableRow, isElement);
        showRow(abstractRow, isElement);
        showRow(fixedRow, isElement || isAttribute);
        showRow(substRow, isElement);

        // Documentation (all nodes)
        docArea.setEditable(true);
        docArea.setText(node.getDocumentation() == null ? "" : node.getDocumentation());
        show(docBox, true);
    }

    /** Text/caret mode (no XSD node): show read-only Node & XPath, hide the editable rows. */
    private void populateCaret() {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            setHeader(PLACEHOLDER, null);
            kindValue.setText(PLACEHOLDER);
            nameField.setText("");
            xpathValue.setText(PLACEHOLDER);
            depthValue.setText(PLACEHOLDER);
        } else if (docOpt.get().getFileType() == EditorFileType.JSON) {
            setHeader(PLACEHOLDER, "JSON");
            kindValue.setText("JSON");
            nameField.setText("");
            xpathValue.setText(PLACEHOLDER);
            depthValue.setText(PLACEHOLDER);
        } else {
            String text = editorHost.getActiveText().orElse("");
            int caret = editorHost.activeCaretProperty().get();
            NodeXPathInfo info = NodeXPathInfo.fromCaret(xpathCalculator, text, caret);
            setHeader(blankToPlaceholder(info.name()), info.kind());
            kindValue.setText(info.kind());
            nameField.setText(PLACEHOLDER.equals(blankToPlaceholder(info.name())) ? "" : info.name());
            xpathValue.setText(info.xpath());
            depthValue.setText(Integer.toString(info.depth()));
        }
        nameField.setEditable(false);
        for (HBox r : new HBox[]{typeRow, minRow, maxRow, useRow, formRow, nillableRow, abstractRow, fixedRow, substRow}) {
            showRow(r, false);
        }
        show(docBox, false);
    }

    private void setHeader(String name, String kind) {
        nodeHeaderName.setText(name);
        boolean hasKind = kind != null && !PLACEHOLDER.equals(kind) && !kind.isBlank();
        nodeHeaderKind.setText(hasKind ? kind : "");
        nodeHeaderKind.setVisible(hasKind);
        nodeHeaderKind.setManaged(hasKind);
    }

    private void updateFacets(XsdNode node) {
        facetTable.getItems().setAll(node != null ? SchemaFacets.collect(node) : FXCollections.emptyObservableList());
    }

    private void updateValidationBadge(EditorHost.ValidationStatus status) {
        if (status == null || status.state() == EditorHost.ValidationState.NOT_VALIDATED) {
            validationBadge.setVisible(false);
            validationBadge.setManaged(false);
            return;
        }
        boolean valid = status.state() == EditorHost.ValidationState.VALID;
        IconifyIcon icon = new IconifyIcon(valid ? "bi-check-circle-fill" : "bi-x-circle-fill");
        icon.setIconSize(12);
        validationBadge.setGraphic(icon);
        validationBadge.setText(status.summary());
        validationBadge.getStyleClass().removeAll("fxt-inspector-valid-badge", "fxt-inspector-invalid-badge");
        validationBadge.getStyleClass().add(valid ? "fxt-inspector-valid-badge" : "fxt-inspector-invalid-badge");
        validationBadge.setVisible(true);
        validationBadge.setManaged(true);
    }

    // ----- small builders --------------------------------------------------

    private TitledPane section(String title, Node body) {
        TitledPane pane = new TitledPane(title, body);
        pane.setExpanded(true);
        pane.getStyleClass().add("fxt-inspector-section");
        return pane;
    }

    private HBox row(String key, Node control) {
        Label k = new Label(key);
        k.getStyleClass().add("fxt-inspector-key");
        k.setMinWidth(92);
        if (control instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(control, Priority.ALWAYS);
        }
        HBox row = new HBox(8, k, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("fxt-inspector-row");
        return row;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Toggles a key/value row and all its children (so the child control's own visibility reflects it). */
    private static void showRow(HBox row, boolean visible) {
        show(row, visible);
        for (Node child : row.getChildren()) {
            show(child, visible);
        }
    }

    private Label roLabel() {
        Label label = new Label(PLACEHOLDER);
        label.getStyleClass().add("fxt-inspector-value");
        label.setWrapText(true);
        return label;
    }

    private TextField editField(String id) {
        TextField f = new TextField();
        f.setId(id);
        f.getStyleClass().add("fxt-inspector-edit");
        return f;
    }

    private ComboBox<String> editCombo(String id) {
        ComboBox<String> c = new ComboBox<>();
        c.setId(id);
        c.setEditable(true);
        c.getStyleClass().add("fxt-inspector-edit");
        return c;
    }

    private Spinner<Integer> intSpinner(String id) {
        Spinner<Integer> s = new Spinner<>();
        s.setId(id);
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 1));
        s.getStyleClass().add("fxt-inspector-edit");
        return s;
    }

    private void commitOnEnterAndBlur(TextField field, Runnable edit) {
        field.setOnAction(e -> commit(edit));
        field.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit(edit);
            }
        });
    }

    private String comboText(ComboBox<String> combo) {
        String v = combo.getEditor().getText();
        if (v == null || v.isBlank()) {
            v = combo.getValue();
        }
        return v == null ? "" : v.trim();
    }

    private String blankToPlaceholder(String s) {
        return (s == null || s.isBlank()) ? PLACEHOLDER : s;
    }

    // ----- test/observer accessors ----------------------------------------

    /** @return the current "Node &amp; XPath" XPath value (for tests/observers). */
    public String getXPathText() {
        return xpathValue.getText();
    }

    /** @return the current node name (for tests/observers). */
    public String getNodeNameText() {
        String t = nameField.getText();
        return t == null || t.isBlank() ? PLACEHOLDER : t;
    }

    /** @return the current type value (for tests/observers). */
    public String getTypeText() {
        return comboText(typeCombo);
    }

    /** @return the number of facets currently shown (for tests/observers). */
    public int getFacetCount() {
        return facetTable.getItems().size();
    }
}
