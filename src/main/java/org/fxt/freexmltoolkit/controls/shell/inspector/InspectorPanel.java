package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.shell.schema.XmlNodeIcons;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels;
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
    /** Bumped on every refresh so a late "Used in" result for a previous node is dropped. */
    private int usedInRequest;
    private XsdNode currentXsdNode;
    private XmlNode currentXmlNode;
    private org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode currentJsonNode;

    // Top bar + node header
    @FXML private javafx.scene.control.Button favoriteButton;
    @FXML private IconifyIcon favoriteIcon;
    @FXML private javafx.scene.control.Tooltip favoriteTooltip;
    @FXML private javafx.scene.control.MenuButton moreButton;
    @FXML private javafx.scene.control.MenuItem copyXPathMenuItem;
    @FXML private javafx.scene.control.MenuItem copyNodeMenuItem;
    @FXML private IconifyIcon nodeIcon;
    @FXML private Label nodeHeaderName;
    @FXML private Label emptyHint;
    @FXML private HBox subtitleBox;
    @FXML private Label nodeHeaderKind;
    @FXML private Label subtitleDot;
    @FXML private Label nodeHeaderType;
    @FXML private Label validationBadge;

    // Read-only values
    @FXML private Label kindValue;
    @FXML private Label xpathValue;
    @FXML private HBox namespaceRow;
    @FXML private Label namespaceValue;
    @FXML private HBox positionRow;
    @FXML private Label positionValue;
    @FXML private HBox schemaTypeRow;
    @FXML private HBox baseTypeRow;
    @FXML private Label baseTypeValue;
    @FXML private HBox derivationRow;
    @FXML private Label derivationValue;
    @FXML private HBox orderRow;
    @FXML private Label orderValue;
    // Read-only cardinality of a schema-bound XML instance element
    @FXML private HBox xmlMinRow;
    @FXML private HBox xmlMaxRow;
    @FXML private HBox xmlUseRow;
    @FXML private Label xmlMinValue;
    @FXML private Label xmlMaxValue;
    @FXML private Label xmlUseValue;
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
    @FXML private InspectorKeyValueList<XsdFacet> facetList;
    @FXML private VBox facetBox;
    @FXML private HBox facetAddRow;
    /** Facets currently shown that are inherited via a named-type reference (read-only). */
    private final java.util.Set<XsdFacet> inheritedFacets = new java.util.HashSet<>();
    @FXML private ComboBox<org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType> facetTypeCombo;
    @FXML private TextField facetValueField;
    // XML-instance value/attributes
    @FXML private TextArea xmlTextArea;
    @FXML private InspectorKeyValueList<String> xmlAttrList;
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
    @FXML private HBox valueCheckBox;
    @FXML private IconifyIcon valueCheckIcon;
    @FXML private Label valueCheckText;
    @FXML private VBox examplesBox;
    @FXML private javafx.scene.layout.FlowPane exampleValuesPane;
    @FXML private VBox validChildrenBox;
    @FXML private javafx.scene.layout.FlowPane validChildrenPane;
    @FXML private VBox usedInBox;
    @FXML private VBox usedInList;
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
    // Nodes that only need wiring: copy button, table columns, the bound Delete button.
    @FXML private javafx.scene.control.Button copyXPathButton;
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

        initHeader();
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
            updateFavoriteStar();
            debounce.playFromStart();
        });
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeSelectedNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        // Remote imports downloaded in the background add types to the type list.
        editorHost.schemaImportsRevisionProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        // A bound XSD loads off-thread: show its facts once it is there, without a caret move.
        editorHost.xmlSchemaProviderRevisionProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeXmlNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        editorHost.activeJsonNodeProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        refresh();
    }

    // ----- wiring of the FXML-defined sections ------------------------------

    /**
     * The top bar's favourite star follows the active file (and favourites changed elsewhere).
     */
    private void initHeader() {
        org.fxt.freexmltoolkit.service.FavoritesService.getInstance().addChangeListener(() -> {
            if (javafx.application.Platform.isFxApplicationThread()) {
                updateFavoriteStar();
            } else {
                javafx.application.Platform.runLater(this::updateFavoriteStar);
            }
        });
        updateFavoriteStar();
        // Long namespace URIs keep their distinctive end visible; the tooltip has the full URI.
        namespaceValue.setTextOverrun(javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS);
        javafx.scene.control.Tooltip nsTip = new javafx.scene.control.Tooltip();
        nsTip.textProperty().bind(namespaceValue.textProperty());
        namespaceValue.setTooltip(nsTip);
    }

    /** @return the active document's file, or {@code null} for none / an unsaved buffer */
    private java.nio.file.Path activeFile() {
        return editorHost.getActiveDocument().map(d -> d.getPath()).orElse(null);
    }

    private void updateFavoriteStar() {
        java.nio.file.Path file = activeFile();
        boolean active = file != null && org.fxt.freexmltoolkit.service.FavoritesService.getInstance()
                .isFavorite(file.toFile().getAbsolutePath());
        favoriteButton.setDisable(file == null);
        favoriteIcon.setIconLiteral(active ? "bi-star-fill" : "bi-star");
        favoriteButton.getStyleClass().remove("fxt-inspector-star-active");
        if (active) {
            favoriteButton.getStyleClass().add("fxt-inspector-star-active");
        }
        favoriteTooltip.setText(file == null ? "Save the file to add it to Favorites"
                : active ? "Remove this file from Favorites" : "Add this file to Favorites");
    }

    /** The star: adds the active file to Favorites, or removes it when it already is one. */
    @FXML
    public void onToggleFavorite() {
        java.nio.file.Path file = activeFile();
        if (file == null) {
            return;
        }
        var favorites = org.fxt.freexmltoolkit.service.FavoritesService.getInstance();
        String path = file.toFile().getAbsolutePath();
        if (favorites.isFavorite(path)) {
            favorites.removeFavoriteByPath(path);
        } else {
            favorites.addFavorite(file.toFile());
        }
        updateFavoriteStar();
    }

    /** ⋮ menu: expands every visible section. */
    @FXML
    public void onExpandAllSections() {
        setSectionsExpanded(true);
    }

    /** ⋮ menu: collapses every visible section. */
    @FXML
    public void onCollapseAllSections() {
        setSectionsExpanded(false);
    }

    private void setSectionsExpanded(boolean expanded) {
        for (Node n : lookupAll(".fxt-inspector-section")) {
            if (n instanceof TitledPane pane && !pane.getStyleClass().contains("fxt-inspector-subsection")) {
                pane.setExpanded(expanded);
            }
        }
    }

    /**
     * The "XPath" block: the copy button, the ⋮ menu's "Copy XPath" / "Copy Node (XML)" and the
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
        copyXPathMenuItem.disableProperty().bind(disabled);
        copyNodeMenuItem.disableProperty().bind(disabled);
        xpathValue.cursorProperty().bind(javafx.beans.binding.Bindings.when(disabled)
                .then(javafx.scene.Cursor.DEFAULT).otherwise(javafx.scene.Cursor.HAND));
    }

    private void initTypeFacets() {
        // Editable facet values (inline facets); inherited facets are read-only rows.
        facetList.setPlaceholderText("No facets");
        facetList.setOnValueCommit((entry, value) -> {
            if (!updating && !inheritedFacets.contains(entry.item())) {
                editorHost.editActiveFacet(entry.item(), value);
            }
        });
        facetList.setOnDelete(entry -> {
            if (!inheritedFacets.contains(entry.item())) {
                editorHost.deleteActiveFacet(entry.item());
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

    private void initValueAttr() {
        xmlTextArea.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit(this::commitValueText);
            }
        });

        xmlAttrList.setPlaceholderText("No attributes");
        xmlAttrList.setOnNameCommit((entry, name) -> {
            if (!updating && !name.isBlank()) {
                editorHost.renameActiveXmlAttribute(entry.item(), name.trim());
            }
        });
        xmlAttrList.setOnValueCommit((entry, value) -> {
            if (!updating) {
                editorHost.setActiveXmlAttribute(entry.item(), value);
            }
        });
        xmlAttrList.setOnDelete(entry -> editorHost.removeActiveXmlAttribute(entry.item()));
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
            resetFigmaRows();
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
            updateNameDisplay();
            updatePosition();
        } finally {
            updating = false;
        }
    }

    /** Hides the optional read-only rows and boxes; each populate path re-shows what applies. */
    private void resetFigmaRows() {
        usedInRequest++;
        for (HBox row : new HBox[]{namespaceRow, schemaTypeRow, baseTypeRow, derivationRow, orderRow,
                xmlMinRow, xmlMaxRow, xmlUseRow}) {
            showRow(row, false);
        }
        show(valueCheckBox, false);
        show(examplesBox, false);
        show(validChildrenBox, false);
        show(usedInBox, false);
        show(xmlSchemaDocValue, false);
        show(facetAddRow, true);
        usedInList.getChildren().clear();
        exampleValuesPane.getChildren().clear();
        validChildrenPane.getChildren().clear();
    }

    /** The header shows the editable name field for nameable nodes, otherwise a plain title. */
    private void updateNameDisplay() {
        boolean editable = nameField.isEditable() && nameField.getText() != null && !nameField.getText().isBlank();
        show(nameField, editable);
        show(nodeHeaderName, !editable);
    }

    /** "Ln x · Col y" of the caret — only meaningful (and shown) in the Text view. */
    private void updatePosition() {
        boolean text = editorHost.getActiveDocument().isPresent()
                && editorHost.activeViewModeProperty().get() == org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.TEXT;
        if (text) {
            int[] lc = editorHost.getActiveCaretLineColumn();
            positionValue.setText("Ln " + lc[0] + " · Col " + lc[1]);
        }
        showRow(positionRow, text);
    }

    /** Shows a read-only value row, or hides it for a blank value. */
    private void showValue(HBox row, Label label, String value) {
        boolean shown = value != null && !value.isBlank() && !PLACEHOLDER.equals(value);
        label.setText(shown ? value : PLACEHOLDER);
        showRow(row, shown);
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
        nodeIcon.setIconLiteral(XmlNodeIcons.icon(node));
    }

    private void populateContent(String kind, String label, String text) {
        setHeader(kind, kind);
        kindValue.setText(kind);
        contentLabel.setText(label.toUpperCase(java.util.Locale.ROOT));
        contentArea.setText(text == null ? "" : text);
        show(contentSection, true);
    }

    /** An XSD comment node: only its editable content is shown (reusing the CONTENT section). */
    private void populateXsdComment(org.fxt.freexmltoolkit.controls.v2.model.XsdComment comment) {
        SelectedNodeInfo info = SelectedNodeInfo.of(comment);
        xpathValue.setText(info.xpath());
        nameField.setEditable(false);
        nameField.setText("");
        contentArea.setEditable(true);
        populateContent("Comment", "Comment", comment.getContent());
        nodeIcon.setIconLiteral("bi-chat-left-text");
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
        setHeader(blankToPlaceholder(info.name()), info.kind(), info.type(),
                XsdNodeLabels.icon(node.getNodeType()));
        kindValue.setText(info.kind());
        xpathValue.setText(info.xpath());
        showValue(namespaceRow, namespaceValue, editorHost.getActiveSchema()
                .map(XsdSchema::getTargetNamespace).orElse(null));

        boolean isElement = node instanceof XsdElement;
        boolean isAttribute = node instanceof XsdAttribute;
        XsdNode schemaRoot = editorHost.getActiveSchemaRoot().orElse(null);
        var derivation = SchemaDerivation.of(node, schemaRoot);
        showValue(baseTypeRow, baseTypeValue, derivation.map(SchemaDerivation.Derivation::base).orElse(null));
        showValue(derivationRow, derivationValue, derivation.map(SchemaDerivation.Derivation::method).orElse(null));
        showValue(orderRow, orderValue, SchemaDerivation.order(node).orElse(null));
        loadUsedIn(node);

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
        boolean facets = facetsApplicable(node, schemaRoot);
        show(facetBox, facets);
        show(typeFacetsSection, isElement || isAttribute || facets || derivation.isPresent());
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
     * Fills USED IN for an XSD node off the FX thread (building the reference graph walks the whole
     * schema); a result that arrives after the selection moved on is dropped.
     */
    private void loadUsedIn(XsdNode node) {
        XsdSchema schema = editorHost.getActiveSchema().orElse(null);
        if (schema == null) {
            return;
        }
        int request = usedInRequest;
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            List<EditorHost.SchemaUsage> usages;
            try {
                usages = editorHost.resolveSchemaUsages(schema, node);
            } catch (RuntimeException e) {
                usages = List.of();
            }
            List<EditorHost.SchemaUsage> result = usages;
            javafx.application.Platform.runLater(() -> {
                if (request == usedInRequest && node == currentXsdNode) {
                    showUsedIn(result.stream().map(u -> new UsedInEntry(u.name(), u.detail(),
                            XsdNodeLabels.icon(u.node().getNodeType()), () -> editorHost.selectNodeInActiveTree(u.node())))
                            .toList());
                }
            });
        });
    }

    /** One USED IN row: icon, name (a link when it can navigate) and a muted detail. */
    private record UsedInEntry(String name, String detail, String icon, Runnable onOpen) {
    }

    private void showUsedIn(List<UsedInEntry> entries) {
        usedInList.getChildren().clear();
        for (UsedInEntry entry : entries) {
            IconifyIcon icon = new IconifyIcon(entry.icon());
            icon.setIconSize(13);
            icon.getStyleClass().add("fxt-inspector-usage-icon");
            Label name = new Label(entry.name());
            name.getStyleClass().add("fxt-inspector-usage-name");
            name.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            Label detail = new Label(entry.detail() == null || entry.detail().isBlank() ? "" : "· " + entry.detail());
            detail.getStyleClass().add("fxt-inspector-usage-detail");
            detail.setMinWidth(0);
            HBox row = new HBox(8, icon, name, detail);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("fxt-inspector-usage-row");
            if (entry.onOpen() != null) {
                row.getStyleClass().add("fxt-inspector-usage-link");
                row.setOnMouseClicked(e -> entry.onOpen().run());
            }
            usedInList.getChildren().add(row);
        }
        show(usedInBox, !entries.isEmpty());
        show(docSection, docSection.isVisible() || !entries.isEmpty());
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

    /**
     * XML-instance element: editable name, namespace, text and attributes. With a bound schema the
     * read-only schema facts follow (type, base/derivation, facets, cardinality, order,
     * documentation, a value check, example values, valid children and the containing element).
     */
    private void populateXmlNode(XmlElement el) {
        String xpath = xmlXPath(el);
        var info = editorHost.resolveActiveXmlElementInfo(xpath);
        setHeader(el.getName(), "Element", info.map(i -> i.typeName()).orElse(null), XmlNodeIcons.icon(el));
        kindValue.setText("Element");
        nameField.setEditable(true);
        nameField.setText(el.getName());
        xpathValue.setText(xpath);

        // Namespace: a summary row, plus the editable NAMESPACE section (prefix + URI).
        String prefix = el.getNamespacePrefix();
        String uri = el.getNamespaceURI();
        showValue(namespaceRow, namespaceValue, uri == null || uri.isBlank() ? null
                : prefix == null || prefix.isBlank() ? uri + " (default)" : prefix + " → " + uri);
        nsPrefixField.setText(prefix == null ? "" : prefix);
        nsUriField.setText(uri == null ? "" : uri);
        show(namespaceSection, true);

        // Text content is only editable for leaf elements (no child elements → no mixed content).
        boolean leaf = !el.hasElementChildren();
        xmlTextLabel.setText("TEXT");
        xmlTextArea.setEditable(true);
        String text = leaf && el.getTextContent() != null ? el.getTextContent() : "";
        xmlTextArea.setText(text);
        show(xmlTextBox, leaf);

        List<InspectorKeyValueList.Entry<String>> entries = new ArrayList<>();
        el.getAttributes().forEach((k, v) -> entries.add(new InspectorKeyValueList.Entry<>(k, k, v)));
        xmlAttrList.setItems(entries);
        show(xmlAttrBox, true);

        showXsdSections(false);
        show(valueAttrSection, true);
        if (info.isPresent()) {
            populateXmlSchemaFacts(el, xpath, info.get(), leaf, text);
        }
    }

    /** The read-only schema facts of a schema-bound XML element (see {@link #populateXmlNode}). */
    private void populateXmlSchemaFacts(XmlElement el, String xpath,
            org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ElementTypeInfo info,
            boolean leaf, String text) {
        // TYPE & FACETS
        showRow(typeRow, false);
        xmlSchemaTypeValue.setText(blankToPlaceholder(info.typeName()));
        showRow(schemaTypeRow, true);
        var derivation = editorHost.resolveActiveXmlElementDerivation(xpath);
        showValue(baseTypeRow, baseTypeValue, derivation.map(SchemaDerivation.Derivation::base).orElse(null));
        showValue(derivationRow, derivationValue, derivation.map(SchemaDerivation.Derivation::method).orElse(null));
        List<InspectorKeyValueList.Entry<XsdFacet>> facets = new ArrayList<>();
        if (info.facets() != null) {
            info.facets().forEach((name, value) ->
                    facets.add(new InspectorKeyValueList.Entry<>(null, name, value, true, "Defined by the schema")));
        }
        facetList.setItems(facets);
        show(facetAddRow, false);
        show(facetBox, !facets.isEmpty());
        show(typeFacetsSection, true);

        // CARDINALITY & USE (read-only) + position in the parent compositor
        for (HBox r : new HBox[]{minRow, maxRow, useRow, formRow, nillableRow, abstractRow, fixedRow, substRow}) {
            showRow(r, false);
        }
        showValue(xmlMinRow, xmlMinValue, info.minOccurs());
        showValue(xmlMaxRow, xmlMaxValue, info.maxOccurs());
        showValue(xmlUseRow, xmlUseValue, info.isMandatory() ? "required" : "optional");
        showValue(orderRow, orderValue, editorHost.resolveActiveXmlElementOrder(xpath).orElse(null));
        show(cardUseSection, true);

        // DOCUMENTATION & REFS
        String doc = formatSchemaDoc(editorHost.resolveActiveXmlElementDocs(xpath), info.documentation());
        xmlSchemaDocValue.setText(doc.isBlank() ? PLACEHOLDER : doc);
        show(xmlSchemaDocValue, !doc.isBlank());
        show(docBox, false);
        boolean constrained = !facets.isEmpty() || (info.enumerationValues() != null && !info.enumerationValues().isEmpty());
        if (leaf && constrained) {
            editorHost.validateActiveXmlElementValue(xpath, text).ifPresent(result -> showValueCheck(
                    result.errorMessage() == null, result.errorMessage()));
        }
        fillChips(exampleValuesPane, examplesBox, editorHost.resolveExampleValues(xpath),
                "Set as the element's text", editorHost::setActiveXmlElementText);
        fillChips(validChildrenPane, validChildrenBox, editorHost.resolveValidChildren(xpath),
                "Add as a child element", editorHost::addActiveXmlChildElement);
        if (el.getParent() instanceof XmlElement parent) {
            String parentType = editorHost.resolveActiveXmlElementInfo(xmlXPath(parent))
                    .map(i -> i.typeName()).filter(t -> t != null && !t.isBlank()).orElse("element");
            showUsedIn(List.of(new UsedInEntry(parent.getName(), parentType, XmlNodeIcons.icon(parent), null)));
        }
        show(docSection, true);
    }

    /** The value-check box: green "matches" or an amber warning with the validator's message. */
    private void showValueCheck(boolean ok, String message) {
        valueCheckBox.getStyleClass().removeAll("fxt-inspector-check-ok", "fxt-inspector-check-warn");
        valueCheckBox.getStyleClass().add(ok ? "fxt-inspector-check-ok" : "fxt-inspector-check-warn");
        valueCheckIcon.setIconLiteral(ok ? "bi-check-circle-fill" : "bi-exclamation-triangle-fill");
        valueCheckText.setText(ok ? "Value matches the schema facets" : message);
        show(valueCheckBox, true);
    }

    /** Fills a chip row; a click on a chip runs {@code action} with its text. */
    private void fillChips(javafx.scene.layout.FlowPane pane, VBox box, List<String> values, String tooltip,
            java.util.function.Consumer<String> action) {
        pane.getChildren().clear();
        for (String value : values) {
            javafx.scene.control.Button chip = new javafx.scene.control.Button(value);
            chip.getStyleClass().add("fxt-inspector-chip");
            chip.setTooltip(new javafx.scene.control.Tooltip(tooltip));
            chip.setOnAction(e -> action.accept(value));
            pane.getChildren().add(chip);
        }
        show(box, !values.isEmpty());
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
        setHeader(blankToPlaceholder(key != null ? key : jsonLabel(node)), kind, null, "bi-braces");
        kindValue.setText(kind);
        nameField.setEditable(keyEditable);
        nameField.setText(key == null ? "" : key);
        xpathValue.setText(node.getPath());

        xmlTextLabel.setText("VALUE");
        xmlTextArea.setEditable(valueEditable);
        xmlTextArea.setText(jsonValue(node));
        show(xmlTextBox, true);
        show(xmlAttrBox, false);

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
        } else if (docOpt.get().getFileType() == EditorFileType.JSON) {
            showEmptyHint("Select a node in the Tree or Graphic view to see and edit its properties.");
            setHeader(PLACEHOLDER, "JSON", null, "bi-braces");
            kindValue.setText("JSON");
            nameField.setText("");
            xpathValue.setText(PLACEHOLDER);
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
        setHeader(name, kind, null, null);
    }

    /**
     * Node header: the title (the kind stands in for a node without a name), the subtitle
     * "Kind · type" and the kind icon in the tile ({@code null} keeps the element icon).
     */
    private void setHeader(String name, String kind, String type, String icon) {
        boolean hasKind = kind != null && !PLACEHOLDER.equals(kind) && !kind.isBlank();
        boolean hasType = type != null && !PLACEHOLDER.equals(type) && !type.isBlank();
        boolean noName = name == null || name.isBlank() || PLACEHOLDER.equals(name);
        nodeHeaderName.setText(noName && hasKind ? kind : noName ? PLACEHOLDER : name);
        nodeHeaderKind.setText(hasKind ? kind : "");
        nodeHeaderType.setText(hasType ? type : "");
        show(nodeHeaderKind, hasKind);
        show(nodeHeaderType, hasType);
        show(subtitleDot, hasKind && hasType);
        show(subtitleBox, hasKind || hasType);
        nodeIcon.setIconLiteral(icon != null ? icon : "bi-code-slash");
    }

    private void updateFacets(XsdNode node) {
        inheritedFacets.clear();
        XsdNode schemaRoot = editorHost.getActiveSchemaRoot().orElse(null);
        if (node == null || !facetsApplicable(node, schemaRoot)) {
            facetList.setItems(List.of());
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
        List<InspectorKeyValueList.Entry<XsdFacet>> rows = new ArrayList<>();
        for (XsdFacet facet : facets) {
            boolean readOnly = inheritedFacets.contains(facet);
            rows.add(new InspectorKeyValueList.Entry<>(facet, facet.getFacetType().getXmlName(), facet.getValue(),
                    readOnly, readOnly ? "Inherited from the referenced named type" : null));
        }
        facetList.setItems(rows);
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
        return validChildrenPane.getChildren().size();
    }

    /** @return the number of example values listed (for tests/observers). */
    public int getExampleValueCount() {
        return exampleValuesPane.getChildren().size();
    }

    /** @return the number of facets currently shown (for tests/observers). */
    public int getFacetCount() {
        return facetList.size();
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
        return xmlAttrList.size();
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

    /** @return the text of a value row, or "" while the row is hidden */
    private static String rowText(HBox row, Label value) {
        return row.isVisible() && value.getText() != null ? value.getText() : "";
    }

    /** @return the caret position row ("Ln x · Col y"), "" when hidden (for tests/observers). */
    public String getPositionText() {
        return rowText(positionRow, positionValue);
    }

    /** @return the Namespace row, "" when hidden (for tests/observers). */
    public String getNamespaceText() {
        return rowText(namespaceRow, namespaceValue);
    }

    /** @return the Base type row, "" when hidden (for tests/observers). */
    public String getBaseTypeText() {
        return rowText(baseTypeRow, baseTypeValue);
    }

    /** @return the Derivation row, "" when hidden (for tests/observers). */
    public String getDerivationText() {
        return rowText(derivationRow, derivationValue);
    }

    /** @return the Order row ("2 of 6 · sequence"), "" when hidden (for tests/observers). */
    public String getOrderText() {
        return rowText(orderRow, orderValue);
    }

    /** @return the value-check message, "" when no check is shown (for tests/observers). */
    public String getValueCheckText() {
        return valueCheckBox.isVisible() && valueCheckText.getText() != null ? valueCheckText.getText() : "";
    }

    /** @return whether the shown value check passed (for tests/observers). */
    public boolean isValueCheckOk() {
        return valueCheckBox.isVisible() && valueCheckBox.getStyleClass().contains("fxt-inspector-check-ok");
    }

    /** @return the number of USED IN rows (for tests/observers). */
    public int getUsedInCount() {
        return usedInBox.isVisible() ? usedInList.getChildren().size() : 0;
    }

    /** @return the header subtitle, e.g. "Element · xs:string" (for tests/observers). */
    public String getHeaderSubtitleText() {
        String kind = nodeHeaderKind.isVisible() ? nodeHeaderKind.getText() : "";
        String type = nodeHeaderType.isVisible() ? nodeHeaderType.getText() : "";
        return kind.isEmpty() ? type : type.isEmpty() ? kind : kind + " · " + type;
    }

    /** @return the kind icon shown in the header tile (for tests/observers). */
    public String getHeaderIconLiteral() {
        return nodeIcon.getIconLiteral();
    }

    /** @return whether the top bar's star marks the active file as a favourite (for tests/observers). */
    public boolean isFavoriteStarActive() {
        return favoriteButton.getStyleClass().contains("fxt-inspector-star-active");
    }

    /** @return whether the XML DECLARATION section is visible (for tests/observers). */
    public boolean isDeclarationSectionVisible() {
        return declarationSection.isVisible();
    }
}
