package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddIdentityConstraintCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSchemaReferenceCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeSchemaPropertyCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathCalculator;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAny;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdImport;
import org.fxt.freexmltoolkit.controls.v2.model.XsdInclude;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath;
import org.fxt.freexmltoolkit.util.MarkdownSupport;

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
    private XmlNode currentXmlNode;
    private org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode currentJsonNode;

    @FXML private Label nodeHeaderName;
    @FXML private Label emptyHint;
    @FXML private Label nodeHeaderKind;
    @FXML private Label validationBadge;

    // Read-only values
    @FXML private Label kindValue;
    @FXML private Label xpathValue;
    @FXML private Label depthValue;
    // Editable controls
    @FXML private TextField nameField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private Spinner<Integer> minSpinner;
    @FXML private Spinner<Integer> maxSpinner;
    @FXML private CheckBox unboundedCheck;
    @FXML private ComboBox<String> useCombo;
    @FXML private ComboBox<String> formCombo;
    @FXML private CheckBox nillableCheck;
    @FXML private CheckBox abstractCheck;
    @FXML private TextField fixedField;
    @FXML private TextField substField;
    // Advanced XSD properties (default/ref/block/final/mixed + wildcard) — section toggled by node type.
    @FXML private TextField defaultField;
    @FXML private TextField refField;
    @FXML private TextField blockField;
    @FXML private TextField finalField;
    @FXML private CheckBox mixedCheck;
    @FXML private TextField wildcardNsField;
    @FXML private ComboBox<String> wildcardProcessCombo;
    // Schema-level (xs:schema root) attributes.
    @FXML private TextField schemaTargetNsField;
    @FXML private TextField schemaVersionField;
    @FXML private ComboBox<String> schemaElementFormCombo;
    @FXML private ComboBox<String> schemaAttributeFormCombo;
    // Schema imports/includes management.
    @FXML private javafx.scene.control.ListView<XsdNode> schemaRefList;
    @FXML private ComboBox<String> schemaRefKindCombo;
    @FXML private TextField schemaRefNsField;
    @FXML private TextField schemaRefLocationField;
    @FXML private DocLanguagesEditor docEditor;
    private AppInfoFieldsEditor appInfoFieldsEditor;
    @FXML private TextArea appinfoArea;
    @FXML private TableView<XsdFacet> facetTable;
    @FXML private VBox facetBox;
    /** Facets currently shown that are inherited via a named-type reference (read-only). */
    private final java.util.Set<XsdFacet> inheritedFacets = new java.util.HashSet<>();
    @FXML private ComboBox<org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType> facetTypeCombo;
    @FXML private TextField facetValueField;
    // XML-instance value/attributes
    @FXML private TextArea xmlTextArea;
    @FXML private TableView<AttrEntry> xmlAttrTable;
    @FXML private Label xmlTextLabel;
    @FXML private VBox xmlTextBox;
    @FXML private VBox xmlAttrBox;
    @FXML private TextField attrNameField;
    @FXML private TextField attrValueField;
    // XML element namespace (prefix + URI)
    @FXML private TextField nsPrefixField;
    @FXML private TextField nsUriField;
    @FXML private TitledPane namespaceSection;
    // Non-element XML nodes: comment / CDATA / text content, and processing instructions
    @FXML private TextArea contentArea;
    @FXML private Label contentLabel;
    @FXML private TitledPane contentSection;
    @FXML private TextField piTargetField;
    @FXML private TextField piDataField;
    @FXML private TitledPane piSection;
    // XML declaration (document node)
    @FXML private TextField declVersionField;
    @FXML private TextField declEncodingField;
    @FXML private ComboBox<String> declStandaloneCombo;
    @FXML private TitledPane declarationSection;
    // XSD-derived, read-only info shown for an XML element when a schema is bound
    @FXML private Label xmlSchemaTypeValue;
    @FXML private Label xmlSchemaDocValue;
    @FXML private javafx.scene.control.ListView<String> validChildrenList;
    @FXML private javafx.scene.control.ListView<String> exampleValuesList;
    @FXML private VBox xmlSchemaBox;
    @FXML private TitledPane typeFacetsSection;
    @FXML private TitledPane cardUseSection;
    @FXML private TitledPane docSection;
    @FXML private TitledPane valueAttrSection;
    @FXML private TitledPane constraintsSection;
    // Read-only identity constraints / assertions of the selected node.
    @FXML private TableView<SchemaConstraints.ConstraintInfo> constraintsTable;
    // Add identity constraint (key/keyref/unique) — element-only.
    @FXML private ComboBox<String> constraintKindCombo;
    @FXML private TextField constraintNameField;
    @FXML private TextField constraintSelectorField;
    @FXML private TextField constraintFieldField;
    @FXML private TextField constraintReferField;
    @FXML private VBox constraintAddBox;

    // Rows toggled by node type
    @FXML private HBox typeRow;
    @FXML private HBox minRow;
    @FXML private HBox maxRow;
    @FXML private HBox useRow;
    @FXML private HBox formRow;
    @FXML private HBox nillableRow;
    @FXML private HBox abstractRow;
    @FXML private HBox fixedRow;
    @FXML private HBox substRow;
    @FXML private HBox defaultRow;
    @FXML private HBox refRow;
    @FXML private HBox blockRow;
    @FXML private HBox finalRow;
    @FXML private HBox mixedRow;
    @FXML private HBox wildcardNsRow;
    @FXML private HBox wildcardProcessRow;
    @FXML private TitledPane advancedSection;
    @FXML private TitledPane schemaSection;
    @FXML private VBox docBox;
    // Nodes that only need wiring: copy buttons, table columns, the bound Delete button.
    @FXML private javafx.scene.control.Button copyXPathButton;
    @FXML private javafx.scene.control.Button copyNodeButton;
    @FXML private TableColumn<XsdFacet, String> facetNameCol;
    @FXML private TableColumn<XsdFacet, String> facetValueCol;
    @FXML private TableColumn<AttrEntry, String> attrNameCol;
    @FXML private TableColumn<AttrEntry, String> attrValueCol;
    @FXML private TableColumn<SchemaConstraints.ConstraintInfo, String> constraintKindCol;
    @FXML private TableColumn<SchemaConstraints.ConstraintInfo, String> constraintNameCol;
    @FXML private TableColumn<SchemaConstraints.ConstraintInfo, String> constraintDetailCol;
    @FXML private javafx.scene.control.Button deleteConstraintButton;

    /** Loads the layout from {@code /pages/inspector/InspectorPanel.fxml} and wires editing and refresh. */
    public InspectorPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        // Width is owned by the shell's horizontal work SplitPane (user-draggable divider,
        // persisted per user; shell default 288 px). No fixed width here — the inspector
        // tables use CONSTRAINED_RESIZE_POLICY, so they follow whatever width the pane gets.
        FxmlViews.load(this, "/pages/inspector/InspectorPanel.fxml");

        editorHost.validationStatusProperty().addListener((obs, oldV, newV) -> updateValidationBadge(newV));
        updateValidationBadge(editorHost.validationStatusProperty().get());

        initXPathRow();
        initTypeFacets();
        initValueAttr();
        initXmlNodeSections();
        initSchemaSections();
        initConstraints();
        initDoc();
        wireEditing();

        debounce.setOnFinished(e -> refresh());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> {
            // The schema (and its cached XPath index for {@link} autocomplete) changes with the tab.
            appInfoFieldsEditor.invalidateSchema();
            debounce.playFromStart();
        });
        editorHost.activeSelectedNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        // Remote imports downloaded in the background add types to the type list.
        editorHost.schemaImportsRevisionProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeXmlNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeJsonNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        refresh();
    }

    // ----- wiring of the FXML-defined sections ------------------------------

    /**
     * The "XPath" block: the two copy buttons ("Copy XPath" positional, "Copy Node (XML)") and the
     * value itself (one click copies the XPath) act on the selected model node when one is present
     * (grid/graphic mode), otherwise on the element enclosing the editor caret (text mode), and are
     * inert while no node/XPath is shown.
     */
    private void initXPathRow() {
        var disabled = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> {
                    String t = xpathValue.getText();
                    return t == null || t.isBlank() || PLACEHOLDER.equals(t);
                },
                xpathValue.textProperty());
        copyXPathButton.disableProperty().bind(disabled);
        copyNodeButton.disableProperty().bind(disabled);
        xpathValue.cursorProperty().bind(javafx.beans.binding.Bindings.when(disabled)
                .then(javafx.scene.Cursor.DEFAULT).otherwise(javafx.scene.Cursor.HAND));
    }

    private void initTypeFacets() {
        facetNameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getFacetType().getXmlName()));
        facetValueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getValue()));
        // Editable facet value (inline facets); inherited facets are read-only.
        facetValueCol.setCellFactory(col -> new javafx.scene.control.cell.TextFieldTableCell<XsdFacet, String>(
                new javafx.util.converter.DefaultStringConverter()) {
            @Override
            public void startEdit() {
                XsdFacet f = getTableRow() == null ? null : getTableRow().getItem();
                if (f != null && inheritedFacets.contains(f)) {
                    return; // inherited from a referenced named type — not editable here
                }
                super.startEdit();
            }
        });
        facetValueCol.setOnEditCommit(e -> {
            if (!updating && e.getRowValue() != null && !inheritedFacets.contains(e.getRowValue())) {
                editorHost.editActiveFacet(e.getRowValue(), e.getNewValue());
            }
        });
        // Render inherited facet rows in muted italics, with a hint tooltip.
        facetTable.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(XsdFacet item, boolean empty) {
                super.updateItem(item, empty);
                boolean inherited = !empty && item != null && inheritedFacets.contains(item);
                setStyle(inherited ? "-fx-font-style: italic; -fx-opacity: 0.8;" : "");
                setTooltip(inherited
                        ? new javafx.scene.control.Tooltip("Inherited from the referenced named type")
                        : null);
            }
        });

        facetTypeCombo.getItems().setAll(
                org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType.values());
        facetTypeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType t) {
                return t == null ? "" : t.getXmlName();
            }

            @Override
            public org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType fromString(String s) {
                return null;
            }
        });
    }

    /** Adds a facet (type + value) to the selected node. */
    @FXML
    public void onAddFacet() {
        var type = facetTypeCombo.getValue();
        String value = facetValueField.getText();
        if (type != null && value != null && !value.isBlank()) {
            editorHost.addActiveFacet(type, value.trim());
            facetValueField.clear();
        }
    }

    /** Deletes the selected facet unless it is inherited from a referenced named type. */
    @FXML
    public void onDeleteFacet() {
        XsdFacet sel = facetTable.getSelectionModel().getSelectedItem();
        if (sel != null && !inheritedFacets.contains(sel)) {
            editorHost.deleteActiveFacet(sel);
        }
    }

    private void initValueAttr() {
        xmlTextArea.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit(this::commitValueText);
            }
        });

        attrNameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        attrNameCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        attrNameCol.setOnEditCommit(e -> {
            if (!updating && e.getRowValue() != null && !e.getNewValue().isBlank()) {
                editorHost.renameActiveXmlAttribute(e.getRowValue().getName(), e.getNewValue().trim());
            }
        });
        attrValueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getValue()));
        attrValueCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        attrValueCol.setOnEditCommit(e -> {
            if (!updating && e.getRowValue() != null) {
                editorHost.setActiveXmlAttribute(e.getRowValue().getName(), e.getNewValue());
            }
        });
    }

    /** Adds (or overwrites) the attribute named in the add row on the selected XML element. */
    @FXML
    public void onAddXmlAttribute() {
        String name = attrNameField.getText();
        if (name != null && !name.isBlank()) {
            editorHost.setActiveXmlAttribute(name.trim(),
                    attrValueField.getText() == null ? "" : attrValueField.getText());
            attrNameField.clear();
            attrValueField.clear();
        }
    }

    /** Removes the attribute selected in the XML attributes table. */
    @FXML
    public void onDeleteXmlAttribute() {
        AttrEntry sel = xmlAttrTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
            editorHost.removeActiveXmlAttribute(sel.getName());
        }
    }

    /** A double-click on a valid child adds it to the selected XML element. */
    @FXML
    public void onValidChildClicked(MouseEvent e) {
        String sel = validChildrenList.getSelectionModel().getSelectedItem();
        if (e.getClickCount() == 2 && sel != null) {
            editorHost.addActiveXmlChildElement(sel);
        }
    }

    /** A double-click on an example value sets it as the selected XML element's text. */
    @FXML
    public void onExampleValueClicked(MouseEvent e) {
        String sel = exampleValuesList.getSelectionModel().getSelectedItem();
        if (e.getClickCount() == 2 && sel != null) {
            editorHost.setActiveXmlElementText(sel);
        }
    }

    /** Namespace, comment/CDATA/text content, processing instruction and XML declaration. */
    private void initXmlNodeSections() {
        commitOnEnterAndBlur(nsPrefixField, this::commitNamespace);
        commitOnEnterAndBlur(nsUriField, this::commitNamespace);

        contentArea.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commitContent();
            }
        });

        commitOnEnterAndBlur(piTargetField, this::commitPi);
        commitOnEnterAndBlur(piDataField, this::commitPi);

        declStandaloneCombo.getItems().setAll("(omit)", "yes", "no");
        commitOnEnterAndBlur(declVersionField, this::commitDeclaration);
        commitOnEnterAndBlur(declEncodingField, this::commitDeclaration);
        declStandaloneCombo.valueProperty().addListener((o, was, isNow) -> commitDeclaration());
    }

    /** Choices of the ADVANCED and SCHEMA sections and the xs:import/xs:include list. */
    private void initSchemaSections() {
        wildcardProcessCombo.getItems().setAll("strict", "lax", "skip");
        schemaElementFormCombo.getItems().setAll("", "qualified", "unqualified");
        schemaAttributeFormCombo.getItems().setAll("", "qualified", "unqualified");

        schemaRefList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(XsdNode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : describeSchemaRef(item));
            }
        });
        schemaRefKindCombo.getItems().setAll("import", "include");
        schemaRefKindCombo.setValue("import");
        // namespace applies to import only.
        schemaRefKindCombo.valueProperty().addListener((o, ov, nv) ->
                schemaRefNsField.setDisable(!"import".equals(nv)));
    }

    @FXML
    public void onAddSchemaRef() {
        commitAddSchemaRef();
    }

    /** Removes the xs:import/xs:include selected in the list. */
    @FXML
    public void onDeleteSchemaRef() {
        XsdNode sel = schemaRefList.getSelectionModel().getSelectedItem();
        if (sel != null) {
            commit(() -> editorHost.removeActiveSchemaReference(sel));
        }
    }

    private void initConstraints() {
        constraintKindCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().kind()));
        constraintNameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        constraintDetailCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().detail()));
        deleteConstraintButton.disableProperty().bind(
                constraintsTable.getSelectionModel().selectedItemProperty().isNull());

        constraintKindCombo.getItems().setAll("key", "unique", "keyref");
        constraintKindCombo.setValue("key");
        // refer applies to keyref only.
        constraintKindCombo.valueProperty().addListener((o, ov, nv) ->
                constraintReferField.setDisable(!"keyref".equals(nv)));
    }

    /** Identity constraints (key/keyref/unique) and assertions are deleted via the command stack. */
    @FXML
    public void onDeleteConstraint() {
        var sel = constraintsTable.getSelectionModel().getSelectedItem();
        if (sel != null && sel.node() != null) {
            commit(() -> editorHost.deleteConstraintNode(sel.node()));
        }
    }

    @FXML
    public void onAddConstraint() {
        commitAddConstraint();
    }

    private void initDoc() {
        docEditor.setOnChange(docs -> commit(() -> editorHost.changeActiveDocumentations(docs)));

        // Structured @since/@version/@author/@see/@deprecated editor with {@link} autocomplete.
        // It commits an XsdAppInfo object (preserving complex/raw appinfo entries) through the
        // command stack; the raw display string stays available below for power users.
        appInfoFieldsEditor = new AppInfoFieldsEditor(
                () -> editorHost.getActiveSchema().orElse(null),
                appinfo -> commit(() -> editorHost.changeActiveAppinfo(appinfo)));
        // Between the "App info (@tags)" label and the "Raw appinfo" sub-pane.
        docBox.getChildren().add(2, appInfoFieldsEditor);
    }

    private static String describeSchemaRef(XsdNode node) {
        if (node instanceof XsdImport imp) {
            String ns = imp.getNamespace();
            return "import  " + nz(imp.getSchemaLocation()) + (ns == null || ns.isBlank() ? "" : "  (" + ns + ")");
        }
        if (node instanceof XsdInclude inc) {
            return "include  " + nz(inc.getSchemaLocation());
        }
        return String.valueOf(node);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void commitAddSchemaRef() {
        AddSchemaReferenceCommand.Kind kind = "include".equals(schemaRefKindCombo.getValue())
                ? AddSchemaReferenceCommand.Kind.INCLUDE
                : AddSchemaReferenceCommand.Kind.IMPORT;
        boolean added = editorHost.addActiveSchemaReference(kind,
                schemaRefNsField.getText(), schemaRefLocationField.getText());
        if (added) {
            schemaRefNsField.clear();
            schemaRefLocationField.clear();
        }
    }

    private void commitContent() {
        if (updating) {
            return;
        }
        String text = contentArea.getText();
        if (currentXsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComment) {
            editorHost.changeActiveComment(text);
            return;
        }
        if (currentXmlNode instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment) {
            editorHost.setActiveCommentText(text);
        } else if (currentXmlNode instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData) {
            editorHost.setActiveCDataText(text);
        } else if (currentXmlNode instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText) {
            editorHost.setActiveTextContent(text);
        }
    }

    private void commitPi() {
        commit(() -> editorHost.setActiveProcessingInstruction(piTargetField.getText(), piDataField.getText()));
    }

    private void commitDeclaration() {
        if (updating || currentXmlNode == null) {
            return;
        }
        Boolean standalone = switch (declStandaloneCombo.getValue() == null ? "" : declStandaloneCombo.getValue()) {
            case "yes" -> Boolean.TRUE;
            case "no" -> Boolean.FALSE;
            default -> null;
        };
        editorHost.setActiveXmlDeclaration(declVersionField.getText(), declEncodingField.getText(), standalone);
    }

    private void commitNamespace() {
        commit(() -> editorHost.setActiveElementNamespace(nsPrefixField.getText(), nsUriField.getText()));
    }

    private void wireEditing() {
        useCombo.getItems().setAll("optional", "required", "prohibited");
        formCombo.getItems().setAll("", "qualified", "unqualified");
        minSpinner.setEditable(true);
        maxSpinner.setEditable(true);

        commitOnEnterAndBlur(nameField, this::commitName);
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
        commitOnEnterAndBlur(defaultField, () -> editorHost.changeActiveDefaultValue(defaultField.getText()));
        commitOnEnterAndBlur(refField, () -> editorHost.changeActiveRef(refField.getText()));
        commitOnEnterAndBlur(blockField, () -> editorHost.changeActiveBlock(blockField.getText()));
        commitOnEnterAndBlur(finalField, () -> editorHost.changeActiveFinal(finalField.getText()));
        mixedCheck.selectedProperty().addListener((o, ov, nv) -> commit(() -> editorHost.changeActiveMixed(nv)));
        commitOnEnterAndBlur(wildcardNsField, this::commitWildcard);
        wildcardProcessCombo.valueProperty().addListener((o, ov, nv) -> commit(this::commitWildcard));
        commitOnEnterAndBlur(schemaTargetNsField, () -> editorHost.changeActiveSchemaProperty(
                ChangeSchemaPropertyCommand.Property.TARGET_NAMESPACE, schemaTargetNsField.getText()));
        commitOnEnterAndBlur(schemaVersionField, () -> editorHost.changeActiveSchemaProperty(
                ChangeSchemaPropertyCommand.Property.VERSION, schemaVersionField.getText()));
        schemaElementFormCombo.valueProperty().addListener((o, ov, nv) -> commit(() ->
                editorHost.changeActiveSchemaProperty(
                        ChangeSchemaPropertyCommand.Property.ELEMENT_FORM_DEFAULT, nv == null ? "" : nv)));
        schemaAttributeFormCombo.valueProperty().addListener((o, ov, nv) -> commit(() ->
                editorHost.changeActiveSchemaProperty(
                        ChangeSchemaPropertyCommand.Property.ATTRIBUTE_FORM_DEFAULT, nv == null ? "" : nv)));
        appinfoArea.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit(() -> editorHost.changeActiveAppinfo(appinfoArea.getText()));
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

    private void commitWildcard() {
        editorHost.changeActiveWildcard(wildcardNsField.getText(),
                XsdAny.ProcessContents.fromString(wildcardProcessCombo.getValue()));
    }

    /** Runs an edit only if it is a genuine user change (not a programmatic repopulate) on a node. */
    private void commit(Runnable edit) {
        if (!updating && (currentXsdNode != null || currentXmlNode != null || currentJsonNode != null)) {
            edit.run();
        }
    }

    private void commitName() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (currentXsdNode != null) {
            if (nameEditable(currentXsdNode)) {
                editorHost.renameActiveNode(name);
            }
        } else if (currentXmlNode != null) {
            editorHost.renameActiveXmlNode(name);
        } else if (currentJsonNode != null && nameField.isEditable()) {
            editorHost.renameActiveJsonKey(name);
        }
    }

    /** Commits the Value text area: the XML element text, or the JSON primitive's value (type kept). */
    private void commitValueText() {
        if (currentJsonNode != null) {
            if (xmlTextArea.isEditable()) {
                editorHost.setActiveJsonValue(xmlTextArea.getText());
            }
        } else {
            editorHost.setActiveXmlElementText(xmlTextArea.getText());
        }
    }

    /** Test seam: types into the Value text area. */
    void setJsonValueTextForTest(String text) {
        xmlTextArea.setText(text);
    }

    /** Test seam: types into the Name field. */
    void setNodeNameTextForTest(String text) {
        nameField.setText(text);
    }

    /** Test seam: commits the Value text area as if it lost focus. */
    void commitValueTextForTest() {
        commit(this::commitValueText);
    }

    /** Test seam: commits the Name field as if Enter was pressed. */
    void commitNameForTest() {
        commit(this::commitName);
    }

    /**
     * Name-bearing nodes whose name may be edited: elements, attributes, and named types/groups.
     * The schema root and structural compositors (sequence/choice/restriction/content models …)
     * have no name attribute — a rename there is meaningless and could corrupt the document.
     */
    private static boolean nameEditable(XsdNode node) {
        return switch (node.getNodeType()) {
            case ELEMENT, ATTRIBUTE -> true;
            case COMPLEX_TYPE, SIMPLE_TYPE, GROUP, ATTRIBUTE_GROUP ->
                    node.getName() != null && !node.getName().isBlank();
            default -> false;
        };
    }

    // ----- refresh / populate ---------------------------------------------

    private void refresh() {
        showEmptyHint(null);
        XsdNode selected = editorHost.activeSelectedNodeProperty().get();
        XmlNode xmlSelected = editorHost.activeXmlNodeProperty().get();
        var jsonSelected = editorHost.activeJsonNodeProperty().get();
        currentXsdNode = selected;
        currentXmlNode = selected == null ? xmlSelected : null;
        currentJsonNode = (selected == null && xmlSelected == null) ? jsonSelected : null;
        updateFacets(selected);
        updating = true;
        try {
            resetXmlNodeSections();
            if (selected != null) {
                populateXsdNode(selected);
            } else if (currentXmlNode instanceof XmlElement el) {
                populateXmlNode(el);
            } else if (currentXmlNode != null) {
                populateXmlOtherNode(currentXmlNode);
            } else if (currentJsonNode != null) {
                populateJsonNode(currentJsonNode);
            } else {
                populateCaret();
            }
        } finally {
            updating = false;
        }
    }

    /** Hides all XML-node-specific sections; each populate path re-shows what it needs. */
    private void resetXmlNodeSections() {
        show(namespaceSection, false);
        show(valueAttrSection, false);
        show(contentSection, false);
        show(piSection, false);
        show(declarationSection, false);
    }

    /** Non-element XML nodes: comment / CDATA / text content, or a processing instruction. */
    private void populateXmlOtherNode(XmlNode node) {
        nameField.setEditable(false);
        nameField.setText("");
        xpathValue.setText(PLACEHOLDER);
        depthValue.setText(PLACEHOLDER);
        showXsdSections(false);

        if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment comment) {
            populateContent("Comment", "Comment", comment.getText());
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData cdata) {
            populateContent("CDATA", "CDATA", cdata.getText());
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText text) {
            populateContent("Text", "Text", text.getText());
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction pi) {
            setHeader(pi.getTarget(), "Processing Instruction");
            kindValue.setText("Processing Instruction");
            piTargetField.setText(pi.getTarget() == null ? "" : pi.getTarget());
            piDataField.setText(pi.getData() == null ? "" : pi.getData());
            show(piSection, true);
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc) {
            setHeader("XML Document", "Document");
            kindValue.setText("Document");
            declVersionField.setText(doc.getVersion() == null ? "" : doc.getVersion());
            declEncodingField.setText(doc.getEncoding() == null ? "" : doc.getEncoding());
            declStandaloneCombo.setValue(doc.getStandalone() == null ? "(omit)"
                    : (doc.getStandalone() ? "yes" : "no"));
            show(declarationSection, true);
        } else {
            setHeader(PLACEHOLDER, "Node");
            kindValue.setText("Node");
        }
    }

    private void populateContent(String kind, String label, String text) {
        setHeader(kind, kind);
        kindValue.setText(kind);
        contentLabel.setText(label);
        contentArea.setText(text == null ? "" : text);
        show(contentSection, true);
    }

    /** An XSD comment node: only its editable content is shown (reusing the CONTENT section). */
    private void populateXsdComment(org.fxt.freexmltoolkit.controls.v2.model.XsdComment comment) {
        SelectedNodeInfo info = SelectedNodeInfo.of(comment);
        xpathValue.setText(info.xpath());
        depthValue.setText(Integer.toString(info.depth()));
        nameField.setEditable(false);
        nameField.setText("");
        contentArea.setEditable(true);
        populateContent("Comment", "Comment", comment.getContent());
        show(typeFacetsSection, false);
        show(cardUseSection, false);
        show(advancedSection, false);
        show(schemaSection, false);
        show(docSection, false);
        show(constraintsSection, false);
        show(namespaceSection, false);
        show(valueAttrSection, false);
    }

    private void populateXsdNode(XsdNode node) {
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComment comment) {
            populateXsdComment(comment);
            return;
        }
        SelectedNodeInfo info = SelectedNodeInfo.of(node);
        setHeader(blankToPlaceholder(info.name()), info.kind());
        kindValue.setText(info.kind());
        xpathValue.setText(info.xpath());
        depthValue.setText(Integer.toString(info.depth()));

        boolean isElement = node instanceof XsdElement;
        boolean isAttribute = node instanceof XsdAttribute;

        nameField.setEditable(nameEditable(node));
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

        // Advanced attributes (default/ref/block/final/mixed + wildcard), toggled per node type.
        populateAdvanced(node, isElement, isAttribute);

        // Schema-level attributes (only for the xs:schema root).
        boolean isSchema = node instanceof XsdSchema;
        if (isSchema) {
            XsdSchema sc = (XsdSchema) node;
            schemaTargetNsField.setText(sc.getTargetNamespace() == null ? "" : sc.getTargetNamespace());
            schemaVersionField.setText(sc.getVersion() == null ? "" : sc.getVersion());
            schemaElementFormCombo.setValue(sc.getElementFormDefault() == null ? "" : sc.getElementFormDefault());
            schemaAttributeFormCombo.setValue(sc.getAttributeFormDefault() == null ? "" : sc.getAttributeFormDefault());
            java.util.List<XsdNode> refs = new ArrayList<>();
            for (XsdNode child : sc.getChildren()) {
                if (child instanceof XsdImport || child instanceof XsdInclude) {
                    refs.add(child);
                }
            }
            schemaRefList.getItems().setAll(refs);
        }
        show(schemaSection, isSchema);

        // Documentation (all nodes). The parser stores annotation text in the multi-language
        // `documentations` list (the legacy single `documentation` String is only set after an
        // inline edit), so read the list first and fall back to the legacy field.
        docEditor.setEntries(resolveXsdDocEntries(node));
        appInfoFieldsEditor.setAppinfo(node.getAppinfo());
        appinfoArea.setEditable(true);
        appinfoArea.setText(node.getAppinfoAsString() == null ? "" : node.getAppinfoAsString());
        show(docBox, true);

        // Section visibility: TYPE & FACETS only when a type field or facets apply;
        // CARDINALITY & USE only for elements/attributes; DOCUMENTATION for all nodes.
        boolean facets = facetsApplicable(node, editorHost.getActiveSchemaRoot().orElse(null));
        show(facetBox, facets);
        show(typeFacetsSection, isElement || isAttribute || facets);
        show(cardUseSection, isElement || isAttribute);
        // Identity constraints / assertions (read-only) — shown only when the node has any.
        java.util.List<SchemaConstraints.ConstraintInfo> constraints = SchemaConstraints.collect(node);
        constraintsTable.getItems().setAll(constraints);
        // Identity constraints can only be added to elements; the read-only table shows any node's.
        show(constraintAddBox, isElement);
        show(constraintsSection, isElement || !constraints.isEmpty());
        show(docSection, true);
        show(namespaceSection, false);
        show(valueAttrSection, false);
    }

    /**
     * Populates the ADVANCED section (default/ref/block/final/mixed/wildcard) for the selected
     * node and toggles each row + the section visibility by node type.
     */
    private void populateAdvanced(XsdNode node, boolean isElement, boolean isAttribute) {
        boolean isComplexType = node instanceof XsdComplexType;
        boolean isWildcard = node instanceof XsdAny || node instanceof XsdAnyAttribute;

        // default + ref (element / attribute)
        if (isElement) {
            XsdElement el = (XsdElement) node;
            defaultField.setText(el.getDefaultValue() == null ? "" : el.getDefaultValue());
            refField.setText(el.getRef() == null ? "" : el.getRef());
        } else if (isAttribute) {
            XsdAttribute at = (XsdAttribute) node;
            defaultField.setText(at.getDefaultValue() == null ? "" : at.getDefaultValue());
            refField.setText(at.getRef() == null ? "" : at.getRef());
        }
        showRow(defaultRow, isElement || isAttribute);
        showRow(refRow, isElement || isAttribute);

        // block (element / complex type)
        if (isElement) {
            blockField.setText(((XsdElement) node).getBlock() == null ? "" : ((XsdElement) node).getBlock());
        } else if (isComplexType) {
            blockField.setText(((XsdComplexType) node).getBlock() == null ? "" : ((XsdComplexType) node).getBlock());
        }
        showRow(blockRow, isElement || isComplexType);

        // final + mixed (complex type)
        if (isComplexType) {
            XsdComplexType ct = (XsdComplexType) node;
            finalField.setText(ct.getFinal() == null ? "" : ct.getFinal());
            mixedCheck.setSelected(ct.isMixed());
        }
        showRow(finalRow, isComplexType);
        showRow(mixedRow, isComplexType);

        // wildcard namespace + processContents (xs:any / xs:anyAttribute)
        if (isWildcard) {
            String ns;
            XsdAny.ProcessContents pc;
            if (node instanceof XsdAny any) {
                ns = any.getNamespace();
                pc = any.getProcessContents();
            } else {
                XsdAnyAttribute anyAttr = (XsdAnyAttribute) node;
                ns = anyAttr.getNamespace();
                pc = anyAttr.getProcessContents();
            }
            wildcardNsField.setText(ns == null ? "" : ns);
            wildcardProcessCombo.setValue(pc == null ? "strict" : pc.getValue());
        }
        showRow(wildcardNsRow, isWildcard);
        showRow(wildcardProcessRow, isWildcard);

        show(advancedSection, isElement || isAttribute || isComplexType || isWildcard);
    }

    /** XML-instance node: editable name + text + attributes; hide the XSD-only sections. */
    private void populateXmlNode(XmlElement el) {
        setHeader(el.getName(), "Element");
        kindValue.setText("Element");
        nameField.setEditable(true);
        nameField.setText(el.getName());
        xpathValue.setText(xmlXPath(el));
        depthValue.setText(Integer.toString(xmlDepth(el)));

        // Namespace (prefix + URI).
        nsPrefixField.setText(el.getNamespacePrefix() == null ? "" : el.getNamespacePrefix());
        nsUriField.setText(el.getNamespaceURI() == null ? "" : el.getNamespaceURI());
        show(namespaceSection, true);

        // Text content is only editable for leaf elements (no child elements → no mixed content).
        boolean leaf = !el.hasElementChildren();
        xmlTextLabel.setText("Text");
        xmlTextArea.setEditable(true);
        xmlTextArea.setText(leaf && el.getTextContent() != null ? el.getTextContent() : "");
        show(xmlTextBox, leaf);

        List<AttrEntry> entries = new ArrayList<>();
        el.getAttributes().forEach((k, v) -> entries.add(new AttrEntry(k, v)));
        xmlAttrTable.getItems().setAll(entries);
        show(xmlAttrBox, true);

        // XSD-derived read-only info, when a schema is bound to the document.
        String xpath = xmlXPath(el);
        var info = editorHost.resolveActiveXmlElementInfo(xpath);
        if (info.isPresent()) {
            xmlSchemaTypeValue.setText(blankToPlaceholder(info.get().typeName()));
            String doc = formatSchemaDoc(editorHost.resolveActiveXmlElementDocs(xpath),
                    info.get().documentation());
            xmlSchemaDocValue.setText(doc.isBlank() ? PLACEHOLDER : doc);
            validChildrenList.getItems().setAll(editorHost.resolveValidChildren(xpath));
            exampleValuesList.getItems().setAll(editorHost.resolveExampleValues(xpath));
            show(xmlSchemaBox, true);
        } else {
            show(xmlSchemaBox, false);
        }

        showXsdSections(false);
        show(valueAttrSection, true);
    }

    /**
     * @return the documentation text to show/edit for an XSD node: the multi-language
     * {@code documentations} list joined by blank lines (the parser populates this), falling back to
     * the legacy single {@code documentation} String (set after an inline edit).
     */
    private static java.util.List<org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation>
            resolveXsdDocEntries(XsdNode node) {
        var docs = node.getDocumentations();
        if (docs != null && !docs.isEmpty()) {
            return docs;
        }
        String legacy = node.getDocumentation();
        if (legacy != null && !legacy.isBlank()) {
            return java.util.List.of(new org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation(legacy));
        }
        return java.util.List.of();
    }

    /**
     * Formats the schema documentation for the Properties pane. With more than one entry
     * (multi-language annotations), every entry is prefixed with its language tag
     * ({@code [EN] …}, {@code [DE] …}; entries without {@code xml:lang} stay untagged) so the
     * user can tell the languages apart. A single entry renders as plain text without a tag;
     * when no per-language entries are available, {@code joinedFallback} (the pre-joined
     * documentation string) is used.
     */
    private String formatSchemaDoc(
            java.util.List<org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo> docs,
            String joinedFallback) {
        if (docs == null || docs.size() < 2) {
            return htmlToPlainText(joinedFallback);
        }
        StringBuilder out = new StringBuilder();
        for (var entry : docs) {
            String text = htmlToPlainText(entry.content()).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            String lang = entry.lang();
            if (lang != null && !lang.isBlank() && !"default".equals(lang)) {
                out.append('[').append(lang.toUpperCase(java.util.Locale.ROOT)).append("] ");
            }
            out.append(text);
        }
        return out.toString();
    }

    /**
     * Renders (possibly HTML) schema documentation as plain text: block boundaries
     * ({@code <br>}, {@code </p>}, {@code </div>}, list items, headings) become line breaks so
     * multiple documentation entries (e.g. English + German) stay separated; remaining tags are
     * dropped, common entities decoded, and intra-line whitespace collapsed.
     */
    static String htmlToPlainText(String html) {
        return MarkdownSupport.toPlainText(html);
    }

    /**
     * JSON node info: key (editable for object properties), kind (node type), JSONPath, depth
     * and the scalar value (editable for primitives; the type is kept when committing).
     */
    private void populateJsonNode(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode node) {
        String key = node.getKey();
        String kind = node.getNodeType() == null ? "JSON" : node.getNodeType().name();
        boolean keyEditable = node.getParent() instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
        boolean valueEditable = node instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
        setHeader(blankToPlaceholder(key != null ? key : jsonLabel(node)), kind);
        kindValue.setText(kind);
        nameField.setEditable(keyEditable);
        nameField.setText(key == null ? "" : key);
        xpathValue.setText(node.getPath());
        depthValue.setText(Integer.toString(jsonDepth(node)));

        xmlTextLabel.setText("Value");
        xmlTextArea.setEditable(valueEditable);
        xmlTextArea.setText(jsonValue(node));
        show(xmlTextBox, true);
        show(xmlAttrBox, false);
        show(xmlSchemaBox, false);

        showXsdSections(false);
        show(namespaceSection, false);
        show(valueAttrSection, true);
    }

    /** The scalar value, unquoted, for primitives; a type summary for objects/arrays. */
    private String jsonValue(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode node) {
        if (node instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive p) {
            Object v = p.getValue();
            return v == null ? "null" : v.toString();
        }
        String s = node.getValueAsString();
        return s == null ? "" : s;
    }

    /** {@code [i]} for array items, {@code $} for the root value (keys are shown directly). */
    private String jsonLabel(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode node) {
        return org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonGridAdapter.labelOf(node);
    }

    private int jsonDepth(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode node) {
        int depth = 0;
        for (var n = node.getParent(); n != null; n = n.getParent()) {
            depth++;
        }
        return depth;
    }

    private String xmlXPath(XmlNode node) {
        return XmlNodeXPath.positional(node);
    }

    private int xmlDepth(XmlNode node) {
        int depth = 0;
        for (XmlNode n = node.getParent(); n instanceof XmlElement; n = n.getParent()) {
            depth++;
        }
        return depth;
    }

    private void commitAddConstraint() {
        AddIdentityConstraintCommand.Kind kind = switch (constraintKindCombo.getValue()) {
            case "keyref" -> AddIdentityConstraintCommand.Kind.KEYREF;
            case "unique" -> AddIdentityConstraintCommand.Kind.UNIQUE;
            default -> AddIdentityConstraintCommand.Kind.KEY;
        };
        boolean added = editorHost.addActiveIdentityConstraint(kind,
                constraintNameField.getText(), constraintSelectorField.getText(),
                List.of(constraintFieldField.getText() == null ? "" : constraintFieldField.getText()),
                constraintReferField.getText());
        if (added) {
            constraintNameField.clear();
            constraintSelectorField.clear();
            constraintFieldField.clear();
            constraintReferField.clear();
        }
    }

    private void showXsdSections(boolean visible) {
        show(typeFacetsSection, visible);
        show(cardUseSection, visible);
        show(advancedSection, visible);
        show(schemaSection, visible);
        show(constraintsSection, visible);
        show(docSection, visible);
    }

    /** Shows the empty-state hint below the header, or hides it for {@code null}. */
    private void showEmptyHint(String text) {
        boolean shown = text != null;
        emptyHint.setText(shown ? text : "");
        emptyHint.setVisible(shown);
        emptyHint.setManaged(shown);
    }

    /** @return the empty-state hint currently shown, or {@code null} (for tests) */
    String emptyHintText() {
        return emptyHint.isVisible() ? emptyHint.getText() : null;
    }

    /** Text/caret mode (no XSD node): show read-only Node & XPath, hide the editable rows. */
    private void populateCaret() {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            showEmptyHint("Open a document to see the properties of its nodes here.");
            setHeader(PLACEHOLDER, null);
            kindValue.setText(PLACEHOLDER);
            nameField.setText("");
            xpathValue.setText(PLACEHOLDER);
            depthValue.setText(PLACEHOLDER);
        } else if (docOpt.get().getFileType() == EditorFileType.JSON) {
            showEmptyHint("Select a node in the Tree or Graphic view to see and edit its properties.");
            setHeader(PLACEHOLDER, "JSON");
            kindValue.setText("JSON");
            nameField.setText("");
            xpathValue.setText(PLACEHOLDER);
            depthValue.setText(PLACEHOLDER);
        } else {
            String text = editorHost.getActiveText().orElse("");
            int caret = editorHost.activeCaretProperty().get();
            NodeXPathInfo info = NodeXPathInfo.fromCaret(xpathCalculator, text, caret);
            if (info.depth() == 0) {
                showEmptyHint("Place the caret on an element, or select a node in the Tree or Graphic view, "
                        + "to see and edit its properties.");
            }
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
        showXsdSections(false);
        show(namespaceSection, false);
        show(valueAttrSection, false);
    }

    private void setHeader(String name, String kind) {
        nodeHeaderName.setText(name);
        boolean hasKind = kind != null && !PLACEHOLDER.equals(kind) && !kind.isBlank();
        nodeHeaderKind.setText(hasKind ? kind : "");
        nodeHeaderKind.setVisible(hasKind);
        nodeHeaderKind.setManaged(hasKind);
    }

    private void updateFacets(XsdNode node) {
        inheritedFacets.clear();
        XsdNode schemaRoot = editorHost.getActiveSchemaRoot().orElse(null);
        if (node == null || !facetsApplicable(node, schemaRoot)) {
            facetTable.getItems().setAll(FXCollections.emptyObservableList());
            return;
        }
        java.util.List<XsdFacet> inline = SchemaFacets.collect(node);
        java.util.List<XsdFacet> facets = new java.util.ArrayList<>(inline);
        java.util.List<XsdFacet> inherited = new java.util.ArrayList<>();
        // An element/attribute referencing a named type (no inline facets): the referenced type's
        // full effective facets are inherited (own + restriction-base chain + list/union).
        if (inline.isEmpty() && (node instanceof XsdElement || node instanceof XsdAttribute)) {
            inherited.addAll(SchemaFacets.resolveReferencedTypeFacets(node, schemaRoot));
        }
        // A type node (simple type / element with an inline restriction): its restriction-base
        // chain and any xs:list item / xs:union member facets are inherited (read-only), shown
        // alongside its own inline facets.
        inherited.addAll(SchemaFacets.resolveBaseChainFacets(node, schemaRoot));
        inherited.addAll(SchemaFacets.resolveListUnionFacets(node, schemaRoot));
        inheritedFacets.addAll(inherited);
        facets.addAll(inherited);
        facetTable.getItems().setAll(facets);
    }

    /**
     * Facets are meaningful for a node that bears a simple-type restriction at one level: an
     * attribute, a simple type / restriction / list / union, or an element with an inline
     * restriction or a {@code type} reference to a named simple type (inherited facets). For the
     * schema root and complex-type containers they must NOT be shown — otherwise
     * {@link SchemaFacets#collect} aggregates every facet of every global type.
     */
    private static boolean facetsApplicable(XsdNode node, XsdNode schemaRoot) {
        if (node == null) {
            return false;
        }
        if (node instanceof XsdAttribute) {
            return true;
        }
        boolean simpleTypeNode = switch (node.getNodeType()) {
            case SIMPLE_TYPE, RESTRICTION, LIST, UNION -> true;
            default -> false;
        };
        return simpleTypeNode
                || (node instanceof XsdElement
                && (SchemaFacets.findRestriction(node) != null
                || !SchemaFacets.resolveReferencedTypeFacets(node, schemaRoot).isEmpty()));
    }

    private void updateValidationBadge(EditorHost.ValidationStatus status) {
        if (status == null || status.state() == EditorHost.ValidationState.NOT_VALIDATED) {
            validationBadge.setVisible(false);
            validationBadge.setManaged(false);
            return;
        }
        boolean valid = status.state() == EditorHost.ValidationState.VALID;
        // Well-formed without a schema is no "valid": neutral, like the Validation panel.
        boolean noSchema = valid && org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.WELL_FORMED_NO_SCHEMA
                .equals(status.summary());
        IconifyIcon icon = new IconifyIcon(noSchema ? "bi-info-circle-fill"
                : valid ? "bi-check-circle-fill" : "bi-x-circle-fill");
        icon.setIconSize(12);
        validationBadge.setGraphic(icon);
        validationBadge.setText(status.summary());
        validationBadge.getStyleClass().removeAll("fxt-inspector-valid-badge", "fxt-inspector-invalid-badge",
                "fxt-inspector-neutral-badge");
        validationBadge.getStyleClass().add(noSchema ? "fxt-inspector-neutral-badge"
                : valid ? "fxt-inspector-valid-badge" : "fxt-inspector-invalid-badge");
        validationBadge.setVisible(true);
        validationBadge.setManaged(true);
    }

    // ----- small helpers ---------------------------------------------------

    /** Copies the positional XPath of the selected node, or of the element at the caret. */
    @FXML
    public void copyXPathToClipboard() {
        String xpath = currentXmlNode != null
                ? XmlNodeXPath.positional(currentXmlNode)
                : caretElement().map(XmlElementAtCaret.Result::xpath).orElse(xpathValue.getText());
        if (xpath != null && !xpath.isBlank() && !xpath.equals("/") && !PLACEHOLDER.equals(xpath)) {
            copyToClipboard(xpath);
        }
    }

    /** Copies the XML of the selected node, or of the element at the caret. */
    @FXML
    public void copyNodeToClipboard() {
        String xml = currentXmlNode != null
                ? currentXmlNode.serialize(0)
                : caretElement().map(XmlElementAtCaret.Result::xml).orElse(null);
        if (xml != null && !xml.isEmpty()) {
            copyToClipboard(xml);
        }
    }

    /** @return the element enclosing the editor caret (text mode), if any. */
    private java.util.Optional<XmlElementAtCaret.Result> caretElement() {
        return XmlElementAtCaret.at(editorHost.getActiveText().orElse(""), editorHost.activeCaretProperty().get());
    }

    private static void copyToClipboard(String text) {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
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

    /** @return whether the Name field is currently editable (for tests/observers). */
    public boolean isNameEditable() {
        return nameField.isEditable();
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

    /** @return the current Kind value (for tests/observers). */
    public String getKindText() {
        String t = kindValue.getText();
        return t == null ? "" : t;
    }

    /** @return the current App-info text shown in the editable XSD-node appinfo area (for tests). */
    public String getAppinfoText() {
        String t = appinfoArea.getText();
        return t == null ? "" : t;
    }

    /** @return the current Documentation text shown in the editable XSD-node doc area (for tests). */
    public String getDocumentationText() {
        return docEditor.getCombinedText();
    }

    /** @return the value shown for a selected JSON node (for tests/observers). */
    public String getJsonValueText() {
        return xmlTextArea.getText() == null ? "" : xmlTextArea.getText();
    }

    /** @return the XSD-derived type shown for a selected XML element (for tests/observers). */
    public String getSchemaTypeText() {
        return xmlSchemaTypeValue.getText() == null ? "" : xmlSchemaTypeValue.getText();
    }

    /** @return the XSD-derived documentation shown for a selected XML element (for tests/observers). */
    public String getSchemaDocText() {
        return xmlSchemaDocValue.getText() == null ? "" : xmlSchemaDocValue.getText();
    }

    /** @return the number of valid child elements listed (for tests/observers). */
    public int getValidChildCount() {
        return validChildrenList.getItems().size();
    }

    /** @return the number of example values listed (for tests/observers). */
    public int getExampleValueCount() {
        return exampleValuesList.getItems().size();
    }

    /** @return the number of facets currently shown (for tests/observers). */
    public int getFacetCount() {
        return facetTable.getItems().size();
    }

    /** @return how many of the shown facets are read-only inherited (for tests/observers). */
    public int getInheritedFacetCount() {
        return inheritedFacets.size();
    }

    /** @return the number of identity constraints / assertions shown (for tests/observers). */
    public int getConstraintCount() {
        return constraintsTable.getItems().size();
    }

    /** @return the number of XML attributes currently shown (for tests/observers). */
    public int getXmlAttributeCount() {
        return xmlAttrTable.getItems().size();
    }

    /** @return the element namespace prefix shown (for tests/observers). */
    public String getNamespacePrefixText() {
        return nsPrefixField.getText() == null ? "" : nsPrefixField.getText();
    }

    /** @return the element namespace URI shown (for tests/observers). */
    public String getNamespaceUriText() {
        return nsUriField.getText() == null ? "" : nsUriField.getText();
    }

    /** @return whether the NAMESPACE section is visible (for tests/observers). */
    public boolean isNamespaceSectionVisible() {
        return namespaceSection.isVisible();
    }

    /** @return whether the editable Text box is visible (hidden for container elements). */
    public boolean isXmlTextVisible() {
        return xmlTextBox.isVisible();
    }

    /** @return the comment/CDATA/text CONTENT editor value (for tests/observers). */
    public String getContentText() {
        return contentArea.getText() == null ? "" : contentArea.getText();
    }

    /** @return whether the CONTENT section is visible (for tests/observers). */
    public boolean isContentSectionVisible() {
        return contentSection.isVisible();
    }

    /** @return the processing-instruction target shown (for tests/observers). */
    public String getPiTargetText() {
        return piTargetField.getText() == null ? "" : piTargetField.getText();
    }

    /** @return whether the PROCESSING INSTRUCTION section is visible (for tests/observers). */
    public boolean isPiSectionVisible() {
        return piSection.isVisible();
    }

    /** @return the XML declaration version shown (for tests/observers). */
    public String getDeclarationVersionText() {
        return declVersionField.getText() == null ? "" : declVersionField.getText();
    }

    /** @return whether the XML DECLARATION section is visible (for tests/observers). */
    public boolean isDeclarationSectionVisible() {
        return declarationSection.isVisible();
    }

    /** A name/value row in the XML attributes table. */
    public static final class AttrEntry {
        private final String name;
        private final String value;

        AttrEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
