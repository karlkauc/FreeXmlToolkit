package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeNamespaceDeclarationCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * "Namespaces" tab of the {@link SchemaOverviewView}: the schema's namespace declarations,
 * edited in place (prefix and URI).
 *
 * @since 2.6
 */
final class NamespacesPane extends VBox {

    /** One declaration; the empty prefix is the default namespace. */
    record Declaration(String prefix, String uri) {
    }

    @FXML private Button addButton;
    @FXML private IconifyIcon addIcon;
    @FXML private Button deleteButton;
    @FXML private IconifyIcon deleteIcon;
    @FXML private TableView<Declaration> namespaceTable;
    @FXML private TableColumn<Declaration, String> prefixColumn;
    @FXML private TableColumn<Declaration, String> uriColumn;

    private final SchemaOverviewView overview;

    NamespacesPane(SchemaOverviewView overview) {
        this.overview = overview;
        FxmlViews.load(this, "/pages/views/NamespacesPane.fxml");
        SemanticIcon.bind(addIcon, ActionColor.CREATE);
        SemanticIcon.bind(deleteIcon, ActionColor.DELETE);
        prefixColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().prefix()));
        prefixColumn.setCellFactory(col -> OverviewCells.text(r -> true));
        prefixColumn.setOnEditCommit(e -> change(e.getRowValue(), e.getNewValue().trim(), e.getRowValue().uri()));
        uriColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().uri()));
        uriColumn.setCellFactory(col -> OverviewCells.text(r -> true));
        uriColumn.setOnEditCommit(e -> change(e.getRowValue(), e.getRowValue().prefix(), e.getNewValue().trim()));
        namespaceTable.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && namespaceTable.getEditingCell() == null) {
                onDelete();
                e.consume();
            }
        });
        namespaceTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
                deleteButton.setDisable(n == null));
        deleteButton.setDisable(true);
    }

    /** Shows the declarations of the schema. */
    void show(XsdSchema schema) {
        Declaration selected = namespaceTable.getSelectionModel().getSelectedItem();
        List<Declaration> rows = new ArrayList<>();
        if (schema != null) {
            for (Map.Entry<String, String> entry : schema.getNamespaces().entrySet()) {
                rows.add(new Declaration(entry.getKey(), entry.getValue()));
            }
        }
        if (!rows.equals(namespaceTable.getItems())) {
            namespaceTable.getSelectionModel().clearSelection();
            namespaceTable.getItems().setAll(rows);
            if (selected != null) {
                rows.stream().filter(r -> r.prefix().equals(selected.prefix())).findFirst()
                        .ifPresent(r -> namespaceTable.getSelectionModel().select(r));
            }
        }
        addButton.setDisable(schema == null);
    }

    private void change(Declaration row, String prefix, String uri) {
        XsdSchema schema = overview.schema();
        if (schema == null || row == null || (row.prefix().equals(prefix) && row.uri().equals(uri))) {
            namespaceTable.refresh();
            return;
        }
        ChangeNamespaceDeclarationCommand command = new ChangeNamespaceDeclarationCommand(schema, row.prefix(), prefix, uri);
        if (!overview.run("Edit Namespace", command.check(), command)) {
            namespaceTable.refresh();
        }
    }

    @FXML
    public void onAdd() {
        XsdSchema schema = overview.schema();
        if (schema == null) {
            return;
        }
        String prefix = "ns1";
        for (int i = 2; schema.getNamespaces().containsKey(prefix); i++) {
            prefix = "ns" + i;
        }
        ChangeNamespaceDeclarationCommand command = new ChangeNamespaceDeclarationCommand(schema, null, prefix,
                "urn:example:" + prefix);
        if (overview.run("Add Namespace", command.check(), command)) {
            String added = prefix;
            namespaceTable.getItems().stream().filter(r -> r.prefix().equals(added)).findFirst().ifPresent(r -> {
                namespaceTable.getSelectionModel().select(r);
                namespaceTable.edit(namespaceTable.getItems().indexOf(r), uriColumn);
            });
        }
    }

    @FXML
    public void onDelete() {
        XsdSchema schema = overview.schema();
        Declaration row = namespaceTable.getSelectionModel().getSelectedItem();
        if (schema != null && row != null) {
            ChangeNamespaceDeclarationCommand command = new ChangeNamespaceDeclarationCommand(schema, row.prefix(), null, null);
            overview.run("Remove Namespace", command.check(), command);
        }
    }

    /** @return the table (tests) */
    TableView<Declaration> table() {
        return namespaceTable;
    }
}
