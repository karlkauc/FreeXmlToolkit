package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.ShellMenuItem;
import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

/**
 * The XSD "Overview" view mode, a schema overview: every top-level component of
 * the schema in one table (imports, includes, annotations, elements, types, groups, attribute
 * groups, attributes, notations) that can be reordered, sorted, renamed, annotated, extended
 * and deleted; below it tabs for the selected component (details, attributes, identity
 * constraints, assertions) and the schema's namespace declarations.
 * <p>
 * All edits run as commands on the document's shared {@link XsdEditorContext}
 * ({@link SchemaOverviewHost#execute}), so undo history is shared with Text, Tree and Graphic.
 *
 * @since 2.6
 */
public final class SchemaOverviewView extends VBox {

    private static final DataFormat ROWS = new DataFormat("application/x-fxt-overview-rows");
    private static final String DIVIDER_KEY = "xsd.overview.divider";
    private static final String TAB_KEY = "xsd.overview.tab";

    /** Test hook: answers "Also update N references?" instead of a dialog (empty = cancel). */
    private static BiFunction<XsdNode, Integer, Optional<Boolean>> referenceUpdateResponder;

    @FXML private TextField targetNamespaceField;
    @FXML private ComboBox<String> elementFormCombo;
    @FXML private ComboBox<String> attributeFormCombo;
    @FXML private SplitPane splitPane;
    @FXML private MenuButton insertButton;
    @FXML private MenuButton appendButton;
    @FXML private MenuButton directiveButton;
    @FXML private IconifyIcon insertIcon;
    @FXML private IconifyIcon appendIcon;
    @FXML private IconifyIcon directiveIcon;
    @FXML private IconifyIcon moveUpIcon;
    @FXML private IconifyIcon moveDownIcon;
    @FXML private IconifyIcon sortIcon;
    @FXML private IconifyIcon deleteIcon;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    @FXML private MenuButton sortButton;
    @FXML private MenuItem sortTypeThenName;
    @FXML private MenuItem sortNameThenType;
    @FXML private Button deleteButton;
    @FXML private ToggleButton showIncludedToggle;
    @FXML private TextField filterField;
    @FXML private TableView<XsdNode> componentTable;
    @FXML private TableColumn<XsdNode, String> kindColumn;
    @FXML private TableColumn<XsdNode, String> nameColumn;
    @FXML private TableColumn<XsdNode, String> detailsColumn;
    @FXML private TableColumn<XsdNode, String> annotationColumn;
    @FXML private TabPane detailTabs;
    @FXML private Tab detailsTab;
    @FXML private Tab attributesTab;
    @FXML private Tab constraintsTab;
    @FXML private Tab assertionsTab;
    @FXML private Tab namespacesTab;

    private final SchemaOverviewHost host;
    private final ObservableList<XsdNode> rows = FXCollections.observableArrayList();
    private final ComponentDetailsPane detailsPane;
    private final AttributesPane attributesPane;
    private final IdentityConstraintsPane constraintsPane;
    private final AssertionsPane assertionsPane;
    private final NamespacesPane namespacesPane;

    private XsdEditorContext context;
    private boolean updating;
    /** The tab the user chose last; kept while it does not apply to the selection. */
    private Tab preferredTab;

    /**
     * @param host the editor tab showing this view
     */
    public SchemaOverviewView(SchemaOverviewHost host) {
        this.host = host;
        FxmlViews.load(this, "/pages/views/SchemaOverviewView.fxml");
        setId("schema-overview");

        detailsPane = new ComponentDetailsPane(this);
        attributesPane = new AttributesPane(this);
        constraintsPane = new IdentityConstraintsPane(this);
        assertionsPane = new AssertionsPane(this);
        namespacesPane = new NamespacesPane(this);
        detailsTab.setContent(detailsPane);
        attributesTab.setContent(attributesPane);
        constraintsTab.setContent(constraintsPane);
        assertionsTab.setContent(assertionsPane);
        namespacesTab.setContent(namespacesPane);

        SemanticIcon.bind(insertIcon, ActionColor.CREATE);
        SemanticIcon.bind(appendIcon, ActionColor.CREATE);
        SemanticIcon.bind(directiveIcon, ActionColor.STRUCTURE);
        SemanticIcon.bind(moveUpIcon, ActionColor.MODIFY);
        SemanticIcon.bind(moveDownIcon, ActionColor.MODIFY);
        SemanticIcon.bind(sortIcon, ActionColor.MODIFY);
        SemanticIcon.bind(deleteIcon, ActionColor.DELETE);
        sortTypeThenName.setGraphic(menuIcon("bi-sort-alpha-down", ActionColor.MODIFY));
        sortNameThenType.setGraphic(menuIcon("bi-sort-down-alt", ActionColor.MODIFY));

        elementFormCombo.getItems().setAll("qualified", "unqualified");
        attributeFormCombo.getItems().setAll("qualified", "unqualified");
        targetNamespaceField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                onTargetNamespaceCommit();
            }
        });

        buildComponentMenus();
        setUpTable();
        // The editor host deletes its selected node on Delete in structured views; inside the
        // Overview the tables handle Delete themselves, so the key never reaches the host
        // (it would delete the whole component while a row of a lower tab is focused).
        addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DELETE) {
                e.consume();
            }
        });
        filterField.textProperty().addListener((obs, o, n) -> refresh());

        restoreLayout();
        detailTabs.getSelectionModel().selectedItemProperty().addListener((obs, o, tab) -> {
            if (!updating && tab != null) {
                preferredTab = tab;
                saveSetting(TAB_KEY, tab.getId());
            }
        });
    }

    // ------------------------------------------------------------------ public API

    /** Binds the view to a (new) model; null shows nothing. */
    public void setContext(XsdEditorContext context) {
        this.context = context;
        refresh();
    }

    /** @return the model the view shows, or null */
    public XsdEditorContext getContext() {
        return context;
    }

    /** @return the selected row (the lead of a multi-selection), or null */
    public XsdNode getSelectedNode() {
        return componentTable.getSelectionModel().getSelectedItem();
    }

    /**
     * Selects a node: a top-level component directly, a nested node through the component
     * that contains it.
     */
    public void select(XsdNode node) {
        XsdNode row = node;
        while (row != null && !(row.getParent() instanceof XsdSchema)) {
            row = row.getParent();
        }
        if (row != null && !rows.contains(row) && row.isFromInclude() && !showIncludedToggle.isSelected()) {
            showIncludedToggle.setSelected(true);
            refresh();
        }
        if (row != null && rows.contains(row)) {
            componentTable.getSelectionModel().clearSelection();
            componentTable.getSelectionModel().select(row);
            componentTable.scrollTo(row);
        }
    }

    /** Re-reads the model: rows, header and the panes of the selection (keeps selection and scroll). */
    public void refresh() {
        updating = true;
        try {
            XsdSchema schema = schema();
            List<XsdNode> selected = new ArrayList<>(componentTable.getSelectionModel().getSelectedItems());
            List<XsdNode> visible = new ArrayList<>();
            if (schema != null) {
                String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
                for (XsdNode child : schema.getChildren()) {
                    if (child.isFromInclude() && !showIncludedToggle.isSelected()) {
                        continue;
                    }
                    if (!filter.isEmpty() && !OverviewRows.kind(child).toLowerCase(Locale.ROOT).contains(filter)
                            && !OverviewRows.name(child).toLowerCase(Locale.ROOT).contains(filter)) {
                        continue;
                    }
                    visible.add(child);
                }
            }
            if (!visible.equals(rows)) {
                componentTable.getSelectionModel().clearSelection();
                rows.setAll(visible);
                for (XsdNode node : selected) {
                    if (rows.contains(node)) {
                        componentTable.getSelectionModel().select(node);
                    }
                }
            }
            componentTable.refresh();
            refreshHeader(schema);
        } finally {
            updating = false;
        }
        showSelection();
    }

    /** @param responder answers the reference-update question in tests ({@code null} = dialog) */
    public static void setReferenceUpdateResponder(BiFunction<XsdNode, Integer, Optional<Boolean>> responder) {
        referenceUpdateResponder = responder;
    }

    // ------------------------------------------------------------------ package API for the panes

    XsdSchema schema() {
        return context != null ? context.getSchema() : null;
    }

    Window window() {
        Window window = host.window();
        return window != null ? window : getScene() != null ? getScene().getWindow() : null;
    }

    SchemaOverviewHost host() {
        return host;
    }

    /**
     * Runs a command after its rule check; a blocked check is explained in a dialog.
     *
     * @return true when the command ran
     */
    boolean run(String title, EditCheck check, XsdCommand command) {
        if (check != null && check.isBlocked()) {
            EditRuleDialogs.showBlocked(window(), title, check);
            return false;
        }
        XsdNode keep = getSelectedNode();
        boolean ok = host.execute(command);
        refresh();
        if (keep != null && keep.getParent() != null) {
            select(keep);
        }
        host.selectionChanged(getSelectedNode());
        return ok;
    }

    /** Selects the row of a top-level node after it was added, then lets the user type its name. */
    void selectAndEditName(XsdNode node) {
        select(node);
        host.selectionChanged(node);
        int index = componentTable.getItems().indexOf(node);
        if (index >= 0) {
            TableColumn<XsdNode, String> column = OverviewRows.hasEditableName(node) ? nameColumn : annotationColumn;
            Platform.runLater(() -> {
                componentTable.requestFocus();
                componentTable.edit(index, column);
            });
        }
    }

    static IconifyIcon menuIcon(String literal, ActionColor color) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return SemanticIcon.bind(icon, color);
    }

    // ------------------------------------------------------------------ header

    private void refreshHeader(XsdSchema schema) {
        boolean has = schema != null;
        targetNamespaceField.setDisable(!has);
        elementFormCombo.setDisable(!has);
        attributeFormCombo.setDisable(!has);
        insertButton.setDisable(!has);
        appendButton.setDisable(!has);
        directiveButton.setDisable(!has);
        sortButton.setDisable(!has);
        if (!has) {
            return;
        }
        if (!targetNamespaceField.isFocused()) {
            targetNamespaceField.setText(schema.getTargetNamespace() == null ? "" : schema.getTargetNamespace());
        }
        elementFormCombo.setValue(schema.getElementFormDefault() == null ? "unqualified" : schema.getElementFormDefault());
        attributeFormCombo.setValue(schema.getAttributeFormDefault() == null ? "unqualified" : schema.getAttributeFormDefault());
    }

    @FXML
    public void onTargetNamespaceCommit() {
        XsdSchema schema = schema();
        if (schema == null || updating) {
            return;
        }
        String value = targetNamespaceField.getText() == null ? "" : targetNamespaceField.getText().trim();
        String current = schema.getTargetNamespace() == null ? "" : schema.getTargetNamespace();
        if (!value.equals(current)) {
            run("Target Namespace", null, new ChangeSchemaPropertyCommand(context, schema,
                    ChangeSchemaPropertyCommand.Property.TARGET_NAMESPACE, value));
        }
    }

    @FXML
    public void onElementFormChanged() {
        changeForm(elementFormCombo, ChangeSchemaPropertyCommand.Property.ELEMENT_FORM_DEFAULT);
    }

    @FXML
    public void onAttributeFormChanged() {
        changeForm(attributeFormCombo, ChangeSchemaPropertyCommand.Property.ATTRIBUTE_FORM_DEFAULT);
    }

    private void changeForm(ComboBox<String> combo, ChangeSchemaPropertyCommand.Property property) {
        XsdSchema schema = schema();
        if (schema == null || updating || combo.getValue() == null) {
            return;
        }
        String current = property == ChangeSchemaPropertyCommand.Property.ELEMENT_FORM_DEFAULT
                ? schema.getElementFormDefault() : schema.getAttributeFormDefault();
        if (!combo.getValue().equals(current == null ? "unqualified" : current)) {
            run("Form Default", null, new ChangeSchemaPropertyCommand(context, schema, property, combo.getValue()));
        }
    }

    // ------------------------------------------------------------------ toolbar

    private void buildComponentMenus() {
        for (GlobalComponentKind kind : GlobalComponentKind.values()) {
            insertButton.getItems().add(componentItem(kind, true));
            appendButton.getItems().add(componentItem(kind, false));
        }
        for (AddSchemaReferenceCommand.Kind kind : AddSchemaReferenceCommand.Kind.values()) {
            String label = "xs:" + kind.name().toLowerCase(Locale.ROOT) + "…";
            ShellMenuItem item = new ShellMenuItem(label, iconOf(kind), ActionColor.STRUCTURE);
            item.setId("overview-add-" + kind.name().toLowerCase(Locale.ROOT));
            item.setOnAction(e -> addDirective(kind));
            directiveButton.getItems().add(item);
        }
    }

    private static String iconOf(AddSchemaReferenceCommand.Kind kind) {
        return switch (kind) {
            case IMPORT -> "bi-box-arrow-in-down-right";
            case INCLUDE -> "bi-file-earmark-plus";
            case REDEFINE -> "bi-pencil-square";
            case OVERRIDE -> "bi-arrow-repeat";
        };
    }

    private MenuItem componentItem(GlobalComponentKind kind, boolean insert) {
        ShellMenuItem item = new ShellMenuItem(kind.label(), iconFor(kind), ActionColor.CREATE);
        item.setId("overview-" + (insert ? "insert-" : "append-") + kind.name().toLowerCase(Locale.ROOT));
        item.setOnAction(e -> addComponent(kind, insert));
        return item;
    }

    static String iconFor(GlobalComponentKind kind) {
        return switch (kind) {
            case ELEMENT -> "bi-box";
            case COMPLEX_TYPE -> "bi-diagram-3";
            case SIMPLE_TYPE -> "bi-type";
            case GROUP, ATTRIBUTE_GROUP -> "bi-collection";
            case ATTRIBUTE -> "bi-at";
            case NOTATION -> "bi-tag";
            case ANNOTATION -> "bi-card-text";
        };
    }

    /**
     * Adds a component in front of the selected row ({@code insert}) or at the end, named with
     * the first free default name, and starts editing its name.
     *
     * @return the new node, or null
     */
    public XsdNode addComponent(GlobalComponentKind kind, boolean insert) {
        XsdSchema schema = schema();
        if (schema == null) {
            return null;
        }
        int index = schema.getChildren().size();
        XsdNode anchor = getSelectedNode();
        if (insert && anchor != null && anchor.getParent() == schema) {
            index = schema.getChildren().indexOf(anchor);
        }
        String name = kind.isNamed() ? SchemaLayoutRules.uniqueName(schema, kind, kind.defaultName()) : null;
        AddGlobalComponentCommand command = new AddGlobalComponentCommand(schema, kind, name, index);
        if (run("Add " + kind.label(), SchemaLayoutRules.addComponent(schema, kind, name), command)
                && command.getAdded() != null) {
            selectAndEditName(command.getAdded());
            return command.getAdded();
        }
        return null;
    }

    private void addDirective(AddSchemaReferenceCommand.Kind kind) {
        XsdSchema schema = schema();
        if (schema == null) {
            return;
        }
        SchemaReferenceDialog.ask(window(), kind, host.documentPath()).ifPresent(answer -> {
            AddSchemaReferenceCommand command = new AddSchemaReferenceCommand(context, schema, kind,
                    answer.namespace(), answer.location());
            if (run("Add xs:" + kind.name().toLowerCase(Locale.ROOT), null, command) && command.getCreated() != null) {
                select(command.getCreated());
                host.selectionChanged(command.getCreated());
            }
        });
    }

    @FXML
    public void onMoveUp() {
        move(-1);
    }

    @FXML
    public void onMoveDown() {
        move(1);
    }

    private void move(int delta) {
        XsdSchema schema = schema();
        List<XsdNode> nodes = ownSelection();
        if (schema == null || nodes.isEmpty()) {
            return;
        }
        ReorderSchemaChildrenCommand command = ReorderSchemaChildrenCommand.moved(schema, nodes, delta, rows::contains);
        if (command.changesOrder()) {
            reorder(command, nodes);
        }
    }

    private void reorder(ReorderSchemaChildrenCommand command, List<XsdNode> keepSelected) {
        if (run(command.getDescription(), command.check(), command)) {
            componentTable.getSelectionModel().clearSelection();
            for (XsdNode node : keepSelected) {
                componentTable.getSelectionModel().select(node);
            }
        }
    }

    @FXML
    public void onSortTypeThenName() {
        sort(ReorderSchemaChildrenCommand.SortMode.TYPE_THEN_NAME);
    }

    @FXML
    public void onSortNameThenType() {
        sort(ReorderSchemaChildrenCommand.SortMode.NAME_THEN_TYPE);
    }

    private void sort(ReorderSchemaChildrenCommand.SortMode mode) {
        XsdSchema schema = schema();
        if (schema == null) {
            return;
        }
        ReorderSchemaChildrenCommand command = ReorderSchemaChildrenCommand.sorted(schema, mode);
        if (command.changesOrder()) {
            run(command.getDescription(), command.check(), command);
        }
    }

    @FXML
    public void onDelete() {
        List<XsdNode> nodes = ownSelection();
        if (nodes.isEmpty()) {
            return;
        }
        int usages = 0;
        for (XsdNode node : nodes) {
            if (GlobalComponentKind.of(node) != null && GlobalComponentKind.of(node).isNamed()) {
                usages += RenameGlobalComponentCommand.countReferences(node);
            }
        }
        if (usages > 0 && !EditRuleDialogs.confirm(window(), "Delete",
                "The selection is still referenced",
                usages + " reference" + (usages == 1 ? "" : "s") + " in this schema will point to nothing. "
                        + "Delete anyway?", "Delete")) {
            return;
        }
        String description = nodes.size() == 1 ? "Delete " + OverviewRows.kind(nodes.getFirst()) + " '"
                + OverviewRows.name(nodes.getFirst()) + "'" : "Delete " + nodes.size() + " components";
        int index = componentTable.getSelectionModel().getSelectedIndex();
        if (run(description, null, new DeleteNodesCommand(nodes, description)) && !rows.isEmpty()) {
            componentTable.getSelectionModel().select(Math.min(Math.max(index, 0), rows.size() - 1));
        }
    }

    @FXML
    public void onShowIncludedChanged() {
        refresh();
    }

    /** @return the selected rows that belong to this file (included rows are read-only) */
    private List<XsdNode> ownSelection() {
        return componentTable.getSelectionModel().getSelectedItems().stream()
                .filter(n -> n != null && !n.isFromInclude()).toList();
    }

    // ------------------------------------------------------------------ table

    private void setUpTable() {
        componentTable.setItems(rows);
        componentTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        kindColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(OverviewRows.kind(c.getValue())));
        kindColumn.setCellFactory(col -> new KindCell());
        nameColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(OverviewRows.name(c.getValue())));
        nameColumn.setCellFactory(col -> new EditCell(OverviewRows::hasEditableName));
        nameColumn.setOnEditCommit(e -> rename(e.getRowValue(), e.getNewValue()));
        detailsColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(
                detailsText(c.getValue())));
        annotationColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(
                OverviewRows.annotation(c.getValue())));
        annotationColumn.setCellFactory(col -> new EditCell(OverviewRows::hasEditableAnnotation));
        annotationColumn.setOnEditCommit(e -> changeAnnotation(e.getRowValue(), e.getNewValue()));

        componentTable.setRowFactory(table -> createRow());
        componentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (!updating) {
                showSelection();
                host.selectionChanged(n);
            }
        });
        componentTable.setOnKeyPressed(e -> {
            if (componentTable.getEditingCell() != null) {
                return;
            }
            if (e.getCode() == KeyCode.DELETE) {
                onDelete();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.UP) {
                onMoveUp();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.DOWN) {
                onMoveDown();
                e.consume();
            } else if (e.getCode() == KeyCode.F2 && getSelectedNode() != null
                    && OverviewRows.hasEditableName(getSelectedNode())) {
                componentTable.edit(componentTable.getSelectionModel().getSelectedIndex(), nameColumn);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && getSelectedNode() != null) {
                open(getSelectedNode());
                e.consume();
            }
        });
    }

    private static String detailsText(XsdNode node) {
        String origin = OverviewRows.origin(node);
        String details = OverviewRows.details(node);
        return origin == null ? details : details.isEmpty() ? "from " + origin : details + " · from " + origin;
    }

    private TableRow<XsdNode> createRow() {
        TableRow<XsdNode> row = new TableRow<>() {
            @Override
            protected void updateItem(XsdNode item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("fxt-overview-row-included", "fxt-overview-row-comment");
                if (!empty && item != null) {
                    if (item.isFromInclude()) {
                        getStyleClass().add("fxt-overview-row-included");
                    }
                    if (item instanceof XsdComment) {
                        getStyleClass().add("fxt-overview-row-comment");
                    }
                }
            }
        };
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()
                    && componentTable.getEditingCell() == null) {
                open(row.getItem());
            }
        });
        row.setOnContextMenuRequested(e -> {
            if (!row.isEmpty()) {
                if (!componentTable.getSelectionModel().getSelectedItems().contains(row.getItem())) {
                    componentTable.getSelectionModel().clearSelection();
                    componentTable.getSelectionModel().select(row.getItem());
                }
                contextMenu(row.getItem()).show(row, e.getScreenX(), e.getScreenY());
                e.consume();
            }
        });
        row.setOnDragDetected(e -> {
            if (!row.isEmpty() && !ownSelection().isEmpty()) {
                var board = row.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.put(ROWS, "rows");
                board.setContent(content);
                e.consume();
            }
        });
        row.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(ROWS)) {
                e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            }
        });
        row.setOnDragDropped(e -> {
            XsdSchema schema = schema();
            if (schema == null || !e.getDragboard().hasContent(ROWS)) {
                return;
            }
            List<XsdNode> nodes = new ArrayList<>(ownSelection());
            int target = schema.getChildren().size();
            if (!row.isEmpty()) {
                target = schema.getChildren().indexOf(row.getItem())
                        + (e.getY() > row.getHeight() / 2 ? 1 : 0);
            }
            ReorderSchemaChildrenCommand command = ReorderSchemaChildrenCommand.movedTo(schema, nodes, target);
            if (command.changesOrder()) {
                reorder(command, nodes);
            }
            e.setDropCompleted(true);
            e.consume();
        });
        return row;
    }

    private ContextMenu contextMenu(XsdNode node) {
        ContextMenu menu = new ContextMenu();
        boolean own = !node.isFromInclude();
        GlobalComponentKind kind = GlobalComponentKind.of(node);
        if (node instanceof XsdComplexType || node instanceof XsdSimpleType) {
            menu.getItems().add(item(I18n.t("SchemaOverviewView.open-in-type-editor"), "bi-box-arrow-up-right", ActionColor.NAVIGATE,
                    () -> host.openTypeEditor(node.getName())));
        }
        if (node instanceof XsdElement) {
            menu.getItems().add(item(I18n.t("SchemaOverviewView.show-in-graphic-view"), "bi-diagram-2", ActionColor.NAVIGATE,
                    () -> host.showInGraphic(node)));
        }
        if (node instanceof XsdSchemaReference reference) {
            menu.getItems().add(item(I18n.t("SchemaOverviewView.open-referenced-file"), "bi-folder2-open", ActionColor.NAVIGATE,
                    () -> host.openReferencedFile(reference)));
        }
        if (!menu.getItems().isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }
        if (own && OverviewRows.hasEditableName(node)) {
            menu.getItems().add(item(I18n.t("SchemaOverviewView.rename"), "bi-pencil", ActionColor.MODIFY, () -> componentTable.edit(
                    componentTable.getItems().indexOf(node), nameColumn)));
        }
        if (own && kind != null && OverviewRows.hasEditableAnnotation(node)) {
            menu.getItems().add(item(I18n.t("SchemaOverviewView.edit-annotation"), "bi-card-text", ActionColor.MODIFY, () -> componentTable.edit(
                    componentTable.getItems().indexOf(node), annotationColumn)));
        }
        Menu insert = new Menu(I18n.t("SchemaOverviewView.insert-before"));
        insert.setGraphic(menuIcon("bi-plus-square", ActionColor.CREATE));
        for (GlobalComponentKind each : GlobalComponentKind.values()) {
            insert.getItems().add(item(each.label(), iconFor(each), ActionColor.CREATE, () -> addComponent(each, true)));
        }
        menu.getItems().add(insert);
        if (own) {
            menu.getItems().addAll(
                    item(I18n.t("SchemaOverviewView.move-up"), "bi-arrow-up", ActionColor.MODIFY, this::onMoveUp),
                    item(I18n.t("SchemaOverviewView.move-down"), "bi-arrow-down", ActionColor.MODIFY, this::onMoveDown),
                    new SeparatorMenuItem(),
                    item(I18n.t("SchemaOverviewView.delete"), "bi-trash", ActionColor.DELETE, this::onDelete));
        }
        return menu;
    }

    private static MenuItem item(String text, String icon, ActionColor color, Runnable action) {
        ShellMenuItem item = new ShellMenuItem(text, icon, color);
        item.setOnAction(e -> action.run());
        return item;
    }

    private void open(XsdNode node) {
        switch (node) {
            case XsdComplexType type -> host.openTypeEditor(type.getName());
            case XsdSimpleType type -> host.openTypeEditor(type.getName());
            case XsdElement element -> host.showInGraphic(element);
            case XsdSchemaReference reference -> host.openReferencedFile(reference);
            default -> {
                if (OverviewRows.hasEditableName(node)) {
                    componentTable.edit(componentTable.getItems().indexOf(node), nameColumn);
                } else if (OverviewRows.hasEditableAnnotation(node)) {
                    componentTable.edit(componentTable.getItems().indexOf(node), annotationColumn);
                }
            }
        }
    }

    /** Renames a component; asks whether references are updated too. */
    void rename(XsdNode node, String newName) {
        if (node == null || newName == null || newName.trim().equals(node.getName())) {
            componentTable.refresh();
            return;
        }
        int references = RenameGlobalComponentCommand.countReferences(node);
        boolean update = false;
        if (references > 0) {
            Optional<Boolean> answer = askUpdateReferences(node, references);
            if (answer.isEmpty()) {
                componentTable.refresh();
                return;
            }
            update = answer.get();
        }
        RenameGlobalComponentCommand command = new RenameGlobalComponentCommand(node, newName, update);
        run("Rename", command.check(), command);
    }

    private Optional<Boolean> askUpdateReferences(XsdNode node, int references) {
        if (referenceUpdateResponder != null) {
            return referenceUpdateResponder.apply(node, references);
        }
        ButtonType update = new ButtonType("Rename and Update", ButtonBar.ButtonData.YES);
        ButtonType only = new ButtonType("Rename Only", ButtonBar.ButtonData.NO);
        Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(update, only, ButtonType.CANCEL);
        alert.setTitle(I18n.t("SchemaOverviewView.rename-2"));
        alert.setHeaderText(I18n.t(references == 1 ? "SchemaOverviewView.referencedOnce" : "SchemaOverviewView.referencedTimes", node.getName(), references));
        alert.setContentText(I18n.t("SchemaOverviewView.update-the-references-to-the"));
        if (window() != null) {
            alert.initOwner(window());
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return Optional.empty();
        }
        return Optional.of(result.get() == update);
    }

    /** Sets the first documentation of a component (keeps its language and source). */
    void changeAnnotation(XsdNode node, String text) {
        if (node == null || context == null || text == null || text.strip().equals(OverviewRows.annotation(node))) {
            componentTable.refresh();
            return;
        }
        List<XsdDocumentation> docs = node.getDocumentations();
        List<XsdDocumentation> updated = new ArrayList<>();
        if (!text.isBlank()) {
            XsdDocumentation first = docs.isEmpty() ? null : docs.getFirst();
            updated.add(new XsdDocumentation(text.strip(), first != null ? first.getLang() : null,
                    first != null ? first.getSource() : null));
        }
        run("Annotation", null, new ChangeDocumentationsCommand(context, node, updated));
    }

    // ------------------------------------------------------------------ detail tabs

    private void showSelection() {
        XsdNode node = getSelectedNode();
        boolean own = node != null && !node.isFromInclude();
        moveUpButton.setDisable(!own);
        moveDownButton.setDisable(!own);
        deleteButton.setDisable(!own);

        updating = true;
        try {
            detailsTab.setDisable(!detailsPane.show(node));
            attributesTab.setDisable(!attributesPane.show(node));
            constraintsTab.setDisable(!constraintsPane.show(node));
            assertionsTab.setDisable(!assertionsPane.show(node));
            namespacesPane.show(schema());
            Tab wanted = preferredTab != null && !preferredTab.isDisable() ? preferredTab : null;
            Tab current = detailTabs.getSelectionModel().getSelectedItem();
            if (wanted != null) {
                detailTabs.getSelectionModel().select(wanted);
            } else if (current == null || current.isDisable()) {
                detailTabs.getTabs().stream().filter(t -> !t.isDisable()).findFirst()
                        .ifPresent(t -> detailTabs.getSelectionModel().select(t));
            }
        } finally {
            updating = false;
        }
    }

    /** @return the tab pane below the table (tests) */
    TabPane detailTabs() {
        return detailTabs;
    }

    // ------------------------------------------------------------------ layout persistence

    private void restoreLayout() {
        try {
            var props = PropertiesServiceImpl.getInstance();
            String divider = props.get(DIVIDER_KEY);
            if (divider != null) {
                splitPane.setDividerPositions(Double.parseDouble(divider));
            }
            String tabId = props.get(TAB_KEY);
            detailTabs.getTabs().stream().filter(t -> t.getId().equals(tabId)).findFirst()
                    .ifPresent(t -> preferredTab = t);
        } catch (RuntimeException ignored) {
            // settings are a convenience; the defaults apply
        }
        splitPane.getDividers().getFirst().positionProperty().addListener((obs, o, n) ->
                saveSetting(DIVIDER_KEY, String.format(Locale.ROOT, "%.3f", n.doubleValue())));
    }

    private static void saveSetting(String key, String value) {
        try {
            PropertiesServiceImpl.getInstance().set(key, value);
        } catch (RuntimeException ignored) {
            // not persisted; harmless
        }
    }

    // ------------------------------------------------------------------ cells

    /** Kind column: icon + XSD name. */
    private static final class KindCell extends TableCell<XsdNode, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            XsdNode node = getTableRow() != null ? getTableRow().getItem() : null;
            if (empty || node == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item);
            IconifyIcon icon = new IconifyIcon(OverviewRows.icon(node));
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }

    /** A text cell that only edits rows the predicate allows. */
    private static final class EditCell extends TableCell<XsdNode, String> {
        private final java.util.function.Predicate<XsdNode> editable;
        private TextField field;

        EditCell(java.util.function.Predicate<XsdNode> editable) {
            this.editable = editable;
        }

        private XsdNode node() {
            return getTableRow() != null ? getTableRow().getItem() : null;
        }

        @Override
        public void startEdit() {
            XsdNode node = node();
            if (node == null || !editable.test(node)) {
                return;
            }
            super.startEdit();
            if (!isEditing()) {
                return;
            }
            field = new TextField(getItem());
            field.setOnAction(e -> commitEdit(field.getText()));
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });
            field.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused && isEditing()) {
                    commitEdit(field.getText());
                }
            });
            setText(null);
            setGraphic(field);
            field.requestFocus();
            field.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing() && field != null) {
                setText(null);
                setGraphic(field);
            } else {
                setText(item);
                setGraphic(null);
            }
        }
    }
}
