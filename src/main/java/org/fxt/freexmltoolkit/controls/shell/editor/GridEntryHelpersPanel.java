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

/**
 * The entry helpers beside the XML grid (XMLSpy: Siblings / Children / Values): the
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

    private XmlEditorContext context;
    private XmlCanvasView canvas;
    private Focus focus = Focus.of(null, null);

    public GridEntryHelpersPanel() {
        FxmlViews.load(this, "/pages/views/GridEntryHelpersPanel.fxml");
        for (ListView<Entry> list : List.of(siblingsList, childrenList, valuesList)) {
            list.setCellFactory(l -> new EntryCell());
            list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateButtons());
        }
        siblingsList.setPlaceholder(placeholder("Select a node in the grid"));
        childrenList.setPlaceholder(placeholder("Select an element"));
        valuesList.setPlaceholder(placeholder("No enumeration"));
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
                apply(EntryHelperModel.setValue(focus, valuesList.getSelectionModel().getSelectedItem()));
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
        if (context != this.context && context != null) {
            // The schema is bound off-thread after the document opens
            context.addPropertyChangeListener("schemaProvider", evt -> javafx.application.Platform.runLater(() -> {
                if (this.context == context) {
                    refresh();
                }
            }));
        }
        if (canvas != this.canvas && canvas != null) {
            canvas.addSelectionListener(() -> {
                if (this.canvas == canvas) {
                    refresh();
                }
            });
        }
        this.context = context;
        this.canvas = canvas;
        refresh();
    }

    /** Re-reads the lists for the current selection. */
    public void refresh() {
        XmlSchemaProvider schema = context != null ? context.getSchemaProvider() : null;
        boolean hasSchema = schema != null && schema.hasSchema();
        hintLabel.setText(hasSchema ? "" : "Bind an XSD schema to the document to get suggestions here.");
        hintLabel.setManaged(!hasSchema);
        hintLabel.setVisible(!hasSchema);

        focus = currentFocus();
        siblingsList.getItems().setAll(EntryHelperModel.siblings(schema, focus));
        childrenList.getItems().setAll(EntryHelperModel.children(schema, focus));
        valuesList.getItems().setAll(EntryHelperModel.values(schema, focus));
        updateButtons();
    }

    /** The focus of the canvas selection: an attribute row passes its element and name. */
    private Focus currentFocus() {
        if (canvas == null) {
            return Focus.of(null, null);
        }
        FlatRow row = canvas.getSelectedRow();
        if (row != null && row.getType() == FlatRow.RowType.ATTRIBUTE && row.getModelNode() instanceof XmlNode node) {
            return Focus.of(node, row.getLabel());
        }
        return Focus.of(canvas.getSelectedNode(), null);
    }

    private void updateButtons() {
        Entry sibling = siblingsList.getSelectionModel().getSelectedItem();
        boolean canInsert = sibling != null && sibling.available() && focus.siblingParent() != null;
        boolean positional = canInsert && sibling.kind() == EntryHelperModel.Kind.ELEMENT && focus.reference() != null;
        insertBeforeButton.setDisable(!positional);
        insertAfterButton.setDisable(!canInsert);
        appendButton.setDisable(!canInsert);
        Entry child = childrenList.getSelectionModel().getSelectedItem();
        addChildButton.setDisable(child == null || !child.available() || focus.owner() == null);
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
        apply(EntryHelperModel.addChild(schema(), focus, childrenList.getSelectionModel().getSelectedItem()));
    }

    private void insertSibling(Placement placement) {
        apply(EntryHelperModel.insertSibling(schema(), focus, siblingsList.getSelectionModel().getSelectedItem(),
                placement));
    }

    private XmlSchemaProvider schema() {
        return context != null ? context.getSchemaProvider() : null;
    }

    /** Runs the command through the context and lets the grid rebuild and write back the text. */
    private void apply(XmlCommand command) {
        if (command == null || context == null || !context.executeCommand(command)) {
            return;
        }
        if (canvas != null) {
            canvas.refresh();
        }
        refresh();
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
            });
            name.setText(entry.name());
            mandatory.setVisible(entry.mandatory());
            if (entry.kind() != EntryHelperModel.Kind.ELEMENT) {
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
