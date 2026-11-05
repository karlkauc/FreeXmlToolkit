package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AssertCommands;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAssert;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * "Assertions" tab of the {@link SchemaOverviewView}: the XSD 1.1 {@code xs:assert} of the
 * selected complex type (or of an element's anonymous complex type), edited in place.
 *
 * @since 2.6
 */
final class AssertionsPane extends VBox {

    @FXML private Button addButton;
    @FXML private IconifyIcon addIcon;
    @FXML private Button deleteButton;
    @FXML private IconifyIcon deleteIcon;
    @FXML private Label hintLabel;
    @FXML private TableView<XsdAssert> assertTable;
    @FXML private TableColumn<XsdAssert, String> testColumn;
    @FXML private TableColumn<XsdAssert, String> namespaceColumn;

    private final SchemaOverviewView overview;
    private XsdNode owner;
    private boolean editable;

    AssertionsPane(SchemaOverviewView overview) {
        this.overview = overview;
        FxmlViews.load(this, "/pages/views/AssertionsPane.fxml");
        SemanticIcon.bind(addIcon, ActionColor.CREATE);
        SemanticIcon.bind(deleteIcon, ActionColor.DELETE);
        testColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(nullToEmpty(c.getValue().getTest())));
        testColumn.setCellFactory(col -> OverviewCells.text(r -> editable));
        testColumn.setOnEditCommit(e -> edit(e.getRowValue(), e.getNewValue(), e.getRowValue().getXpathDefaultNamespace()));
        namespaceColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(nullToEmpty(c.getValue().getXpathDefaultNamespace())));
        namespaceColumn.setCellFactory(col -> OverviewCells.combo(r -> editable,
                r -> java.util.List.of("", "##targetNamespace", "##defaultNamespace", "##local"), true));
        namespaceColumn.setOnEditCommit(e -> edit(e.getRowValue(), e.getRowValue().getTest(), e.getNewValue()));
        assertTable.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && assertTable.getEditingCell() == null) {
                onDelete();
                e.consume();
            }
        });
        assertTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
                deleteButton.setDisable(!editable || n == null));
    }

    /**
     * Shows the assertions of a complex type or of an element's anonymous complex type.
     *
     * @return true when the node can have assertions
     */
    boolean show(XsdNode node) {
        owner = node != null && AssertCommands.assertContainer(node) != null ? node : null;
        if (owner == null) {
            assertTable.getItems().clear();
            return false;
        }
        editable = !owner.isFromInclude();
        XsdAssert selected = assertTable.getSelectionModel().getSelectedItem();
        var rows = AssertCommands.assertsOf(owner);
        assertTable.getSelectionModel().clearSelection();
        assertTable.getItems().setAll(rows);
        if (selected != null && rows.contains(selected)) {
            assertTable.getSelectionModel().select(selected);
        }
        assertTable.setEditable(editable);
        addButton.setDisable(!editable);
        deleteButton.setDisable(!editable || assertTable.getSelectionModel().getSelectedItem() == null);
        hintLabel.setText("Assertions are XSD 1.1: validate with an XSD 1.1 processor.");
        return true;
    }

    private void edit(XsdAssert target, String test, String namespace) {
        if (target == null) {
            return;
        }
        if (java.util.Objects.equals(nullToEmpty(test).trim(), nullToEmpty(target.getTest()))
                && java.util.Objects.equals(nullToEmpty(namespace).trim(), nullToEmpty(target.getXpathDefaultNamespace()))) {
            assertTable.refresh();
            return;
        }
        overview.run("Edit Assertion", AssertCommands.checkTest(test), new AssertCommands.Edit(target, test, namespace));
    }

    @FXML
    public void onAdd() {
        if (owner == null) {
            return;
        }
        AssertCommands.Add command = new AssertCommands.Add(owner, "true()", null);
        if (overview.run("Add Assertion", null, command)) {
            assertTable.getSelectionModel().select(command.getCreated());
            assertTable.edit(assertTable.getItems().indexOf(command.getCreated()), testColumn);
        }
    }

    @FXML
    public void onDelete() {
        XsdAssert selected = assertTable.getSelectionModel().getSelectedItem();
        if (selected != null && editable) {
            overview.run("Delete Assertion", null, new DeleteNodeCommand(selected));
        }
    }

    /** @return the table (tests) */
    TableView<XsdAssert> table() {
        return assertTable;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
