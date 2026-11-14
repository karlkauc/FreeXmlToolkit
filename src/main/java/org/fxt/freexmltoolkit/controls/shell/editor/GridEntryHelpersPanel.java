package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Entry;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Focus;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * The entry helpers beside the XML grid (Siblings / Children / Values): the
 * attributes and elements the bound schema allows next to the selected node and in the
 * selected element, and the enumeration values of the selected element or attribute. Entries
 * that cannot be added there any more are greyed out, required ones carry a "!". A
 * double-click inserts (Siblings: after the selected node, Children: into it, Values: sets
 * the value); the buttons insert before / after / at the end. Every insertion is one undo
 * step through the editor context. The lists come from {@link EntryHelperModel}.
 */
public final class GridEntryHelpersPanel extends VBox {

    @FXML private Label hintLabel;
    @FXML private ListView<Entry> siblingsList;
    @FXML private ListView<Entry> childrenList;
    @FXML private ListView<Entry> valuesList;
    @FXML private Button insertBeforeButton;
    @FXML private Button insertAfterButton;
    @FXML private Button appendButton;
    @FXML private Button addChildButton;

    /** Where the lists come from and what the buttons do: XML (XSD) or JSON (JSON Schema). */
    private Backend backend;

    /** The source of the lists and the actions of one grid. */
    interface Backend {
        /** @return the hint shown while no schema is bound, or {@code null} when one is */
        String missingSchemaHint();

        /** Reads the focus from the grid's selection. */
        void updateFocus();

        List<Entry> siblings();

        List<Entry> children();

        List<Entry> values();

        /** @return whether the selected sibling can be inserted ({@code positional}: before/after the selection) */
        boolean canInsertSibling(Entry entry, boolean positional);

        boolean canAddChild(Entry entry);

        /** @return whether the model changed */
        boolean insertSibling(Entry entry, Placement placement);

        boolean addChild(Entry entry);

        boolean setValue(Entry entry);
    }

    public GridEntryHelpersPanel() {
        FxmlViews.load(this, "/pages/views/GridEntryHelpersPanel.fxml");
        for (ListView<Entry> list : List.of(siblingsList, childrenList, valuesList)) {
            list.setCellFactory(l -> new EntryCell());
            list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateButtons());
        }
        siblingsList.setPlaceholder(placeholder(I18n.t("GridEntryHelpersPanel.select-a-node-in-the")));
        childrenList.setPlaceholder(placeholder(I18n.t("GridEntryHelpersPanel.select-an-element")));
        valuesList.setPlaceholder(placeholder(I18n.t("GridEntryHelpersPanel.no-enumeration")));
        siblingsList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                onInsertAfter();
            }
        });
        childrenList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                onAddChild();
            }
        });
        valuesList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                applyValue(valuesList.getSelectionModel().getSelectedItem());
            }
        });
        updateButtons();
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-grid-entry-placeholder");
        return label;
    }

    /**
     * Follows the selection of a grid canvas over the given context (the panel keeps one
     * binding: binding again replaces it).
     */
    public void bind(XmlEditorContext context, XmlCanvasView canvas) {
        XmlBackend xml = new XmlBackend(context, canvas);
        this.backend = xml;
        if (context != null) {
            // The schema is bound off-thread after the document opens
            context.addPropertyChangeListener("schemaProvider", evt -> javafx.application.Platform.runLater(() -> {
                if (this.backend == xml) {
                    refresh();
                }
            }));
        }
        if (canvas != null) {
            canvas.addSelectionListener(() -> {
                if (this.backend == xml) {
                    refresh();
                }
            });
        }
        refresh();
    }

    /**
     * Follows the selection of a JSON grid; the lists come from the document's JSON Schema.
     *
     * @param context the shared JSON context
     * @param canvas  the grid
     * @param schema  the bound schema, or a supplier of {@code null} while none is bound
     */
    public void bind(org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext context,
                     org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView canvas,
                     java.util.function.Supplier<org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver> schema) {
        JsonBackend json = new JsonBackend(context, canvas, schema);
        this.backend = json;
        if (canvas != null) {
            canvas.addSelectionListener(() -> {
                if (this.backend == json) {
                    refresh();
                }
            });
        }
        refresh();
    }

    /** Re-reads the lists for the current selection. */
    public void refresh() {
        String hint = backend != null ? backend.missingSchemaHint() : I18n.t("GridEntryHelpersPanel.bind-xsd");
        hintLabel.setText(hint != null ? hint : "");
        hintLabel.setManaged(hint != null);
        hintLabel.setVisible(hint != null);
        if (backend != null) {
            backend.updateFocus();
        }
        siblingsList.getItems().setAll(backend != null ? backend.siblings() : List.of());
        childrenList.getItems().setAll(backend != null ? backend.children() : List.of());
        valuesList.getItems().setAll(backend != null ? backend.values() : List.of());
        updateButtons();
    }

    private void updateButtons() {
        Entry sibling = siblingsList.getSelectionModel().getSelectedItem();
        boolean canInsert = backend != null && sibling != null && backend.canInsertSibling(sibling, false);
        boolean positional = canInsert && backend.canInsertSibling(sibling, true);
        insertBeforeButton.setDisable(!positional);
        insertAfterButton.setDisable(!canInsert);
        appendButton.setDisable(!canInsert);
        Entry child = childrenList.getSelectionModel().getSelectedItem();
        addChildButton.setDisable(backend == null || child == null || !backend.canAddChild(child));
    }

    @FXML
    public void onInsertBefore() {
        insertSibling(Placement.BEFORE);
    }

    @FXML
    public void onInsertAfter() {
        insertSibling(Placement.AFTER);
    }

    @FXML
    public void onAppend() {
        insertSibling(Placement.APPEND);
    }

    @FXML
    public void onAddChild() {
        Entry entry = childrenList.getSelectionModel().getSelectedItem();
        if (backend != null && entry != null && backend.addChild(entry)) {
            refresh();
        }
    }

    private void insertSibling(Placement placement) {
        Entry entry = siblingsList.getSelectionModel().getSelectedItem();
        if (backend != null && entry != null && backend.insertSibling(entry, placement)) {
            refresh();
        }
    }

    private void applyValue(Entry entry) {
        if (backend != null && entry != null && backend.setValue(entry)) {
            refresh();
        }
    }

    /** The XML grid: lists from the bound XSD ({@link EntryHelperModel}). */
    private static final class XmlBackend implements Backend {
        private final XmlEditorContext context;
        private final XmlCanvasView canvas;
        private Focus focus = Focus.of(null, null);

        XmlBackend(XmlEditorContext context, XmlCanvasView canvas) {
            this.context = context;
            this.canvas = canvas;
        }

        private XmlSchemaProvider schema() {
            return context != null ? context.getSchemaProvider() : null;
        }

        @Override
        public String missingSchemaHint() {
            XmlSchemaProvider schema = schema();
            return schema != null && schema.hasSchema() ? null : I18n.t("GridEntryHelpersPanel.bind-xsd");
        }

        /** The focus of the canvas selection: an attribute row passes its element and name. */
        @Override
        public void updateFocus() {
            if (canvas == null) {
                focus = Focus.of(null, null);
                return;
            }
            FlatRow row = canvas.getSelectedRow();
            if (row != null && row.getType() == FlatRow.RowType.ATTRIBUTE && row.getModelNode() instanceof XmlNode node) {
                focus = Focus.of(node, row.getLabel());
            } else {
                focus = Focus.of(canvas.getSelectedNode(), null);
            }
        }

        @Override
        public List<Entry> siblings() {
            return EntryHelperModel.siblings(schema(), focus);
        }

        @Override
        public List<Entry> children() {
            return EntryHelperModel.children(schema(), focus);
        }

        @Override
        public List<Entry> values() {
            return EntryHelperModel.values(schema(), focus);
        }

        @Override
        public boolean canInsertSibling(Entry entry, boolean positional) {
            boolean can = entry.available() && focus.siblingParent() != null;
            return positional ? can && entry.kind() == EntryHelperModel.Kind.ELEMENT && focus.reference() != null : can;
        }

        @Override
        public boolean canAddChild(Entry entry) {
            return entry.available() && focus.owner() != null;
        }

        @Override
        public boolean insertSibling(Entry entry, Placement placement) {
            return apply(EntryHelperModel.insertSibling(schema(), focus, entry, placement));
        }

        @Override
        public boolean addChild(Entry entry) {
            return apply(EntryHelperModel.addChild(schema(), focus, entry));
        }

        @Override
        public boolean setValue(Entry entry) {
            return apply(EntryHelperModel.setValue(focus, entry));
        }

        /** Runs the command through the context and lets the grid rebuild and write back the text. */
        private boolean apply(XmlCommand command) {
            if (command == null || context == null || !context.executeCommand(command)) {
                return false;
            }
            if (canvas != null) {
                canvas.refresh();
            }
            return true;
        }
    }

    /** The JSON grid: lists from the bound JSON Schema ({@code JsonEntryHelperModel}). */
    private static final class JsonBackend implements Backend {
        private final org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext context;
        private final org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView canvas;
        private final java.util.function.Supplier<org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver> schema;
        private org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.Focus focus =
                org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.Focus.of(null);

        JsonBackend(org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext context,
                    org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView canvas,
                    java.util.function.Supplier<org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver> schema) {
            this.context = context;
            this.canvas = canvas;
            this.schema = schema;
        }

        @Override
        public String missingSchemaHint() {
            return schema.get() != null ? null : I18n.t("GridEntryHelpersPanel.bind-json-schema");
        }

        @Override
        public void updateFocus() {
            Object node = canvas != null ? canvas.getSelectedNode() : null;
            focus = org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.Focus.of(
                    node instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode json ? json : null);
        }

        @Override
        public List<Entry> siblings() {
            return org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.siblings(schema.get(), focus);
        }

        @Override
        public List<Entry> children() {
            return org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.children(schema.get(), focus);
        }

        @Override
        public List<Entry> values() {
            return org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.values(schema.get(), focus);
        }

        @Override
        public boolean canInsertSibling(Entry entry, boolean positional) {
            return entry.available() && focus.siblingParent() != null;
        }

        @Override
        public boolean canAddChild(Entry entry) {
            return entry.available() && focus.owner() != null;
        }

        @Override
        public boolean insertSibling(Entry entry, Placement placement) {
            return apply(org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.insertSibling(
                    schema.get(), focus, entry, placement));
        }

        @Override
        public boolean addChild(Entry entry) {
            return apply(org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.addChild(
                    schema.get(), focus, entry));
        }

        @Override
        public boolean setValue(Entry entry) {
            return apply(org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonEntryHelperModel.setValue(focus, entry));
        }

        private boolean apply(org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand command) {
            if (command == null || context == null || !context.executeCommand(command)) {
                return false;
            }
            if (canvas != null) {
                canvas.refresh();
            }
            return true;
        }
    }

    // ==================== Test hooks ====================

    ListView<Entry> siblingsList() {
        return siblingsList;
    }

    ListView<Entry> childrenList() {
        return childrenList;
    }

    ListView<Entry> valuesList() {
        return valuesList;
    }

    /** One entry: a type glyph, the name and a "!" for required entries; unavailable ones greyed. */
    private static final class EntryCell extends ListCell<Entry> {
        private final Label glyph = new Label();
        private final Label name = new Label();
        private final Label mandatory = new Label("!");
        private final HBox box;

        EntryCell() {
            glyph.getStyleClass().add("fxt-grid-entry-glyph");
            glyph.setMinWidth(Region.USE_PREF_SIZE);
            mandatory.getStyleClass().add("fxt-grid-entry-mandatory");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            box = new HBox(6, glyph, name, spacer, mandatory);
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll("fxt-grid-entry-unavailable", "fxt-grid-entry-attribute", "fxt-grid-entry-value");
            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            glyph.setText(switch (entry.kind()) {
                case ELEMENT -> "<>";
                case ATTRIBUTE -> "=";
                case VALUE -> "Abc";
                case PROPERTY -> "\"\"";
            });
            name.setText(entry.name());
            mandatory.setVisible(entry.mandatory());
            if (entry.kind() == EntryHelperModel.Kind.ATTRIBUTE || entry.kind() == EntryHelperModel.Kind.VALUE) {
                getStyleClass().add(entry.kind() == EntryHelperModel.Kind.ATTRIBUTE
                        ? "fxt-grid-entry-attribute" : "fxt-grid-entry-value");
            }
            if (!entry.available()) {
                getStyleClass().add("fxt-grid-entry-unavailable");
            }
            setText(null);
            setGraphic(box);
        }
    }
}
