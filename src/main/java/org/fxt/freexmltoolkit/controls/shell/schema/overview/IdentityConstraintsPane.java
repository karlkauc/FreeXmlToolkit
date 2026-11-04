package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddIdentityConstraintCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditIdentityConstraintCommand;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * "Identity Constraints" tab of the {@link SchemaOverviewView}: the xs:key, xs:keyref and
 * xs:unique of the selected element, edited in place.
 *
 * @since 2.6
 */
final class IdentityConstraintsPane extends VBox {

    @FXML private MenuButton addButton;
    @FXML private IconifyIcon addIcon;
    @FXML private MenuItem addKey;
    @FXML private MenuItem addUnique;
    @FXML private MenuItem addKeyRef;
    @FXML private Button deleteButton;
    @FXML private IconifyIcon deleteIcon;
    @FXML private TableView<XsdIdentityConstraint> constraintTable;
    @FXML private TableColumn<XsdIdentityConstraint, String> kindColumn;
    @FXML private TableColumn<XsdIdentityConstraint, String> nameColumn;
    @FXML private TableColumn<XsdIdentityConstraint, String> selectorColumn;
    @FXML private TableColumn<XsdIdentityConstraint, String> fieldsColumn;
    @FXML private TableColumn<XsdIdentityConstraint, String> referColumn;

    private final SchemaOverviewView overview;
    private XsdElement element;
    private boolean editable;

    IdentityConstraintsPane(SchemaOverviewView overview) {
        this.overview = overview;
        FxmlViews.load(this, "/pages/views/IdentityConstraintsPane.fxml");
        SemanticIcon.bind(addIcon, ActionColor.CREATE);
        SemanticIcon.bind(deleteIcon, ActionColor.DELETE);
        addKey.setGraphic(SchemaOverviewView.menuIcon("bi-key", ActionColor.CREATE));
        addUnique.setGraphic(SchemaOverviewView.menuIcon("bi-key", ActionColor.CREATE));
        addKeyRef.setGraphic(SchemaOverviewView.menuIcon("bi-link-45deg", ActionColor.CREATE));

        kindColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(kindOf(c.getValue())));
        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        nameColumn.setCellFactory(col -> OverviewCells.text(r -> editable));
        nameColumn.setOnEditCommit(e -> edit(e.getRowValue(), e.getNewValue(), null, null, null));
        selectorColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getSelector() != null ? c.getValue().getSelector().getXpath() : ""));
        selectorColumn.setCellFactory(col -> OverviewCells.text(r -> editable));
        selectorColumn.setOnEditCommit(e -> edit(e.getRowValue(), null, e.getNewValue(), null, null));
        fieldsColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(fieldsOf(c.getValue())));
        fieldsColumn.setCellFactory(col -> OverviewCells.text(r -> editable));
        fieldsColumn.setOnEditCommit(e -> edit(e.getRowValue(), null, null, e.getNewValue(), null));
        referColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue() instanceof XsdKeyRef keyRef && keyRef.getRefer() != null ? keyRef.getRefer() : ""));
        referColumn.setCellFactory(col -> OverviewCells.combo(r -> editable && r instanceof XsdKeyRef,
                r -> referable(), true));
        referColumn.setOnEditCommit(e -> edit(e.getRowValue(), null, null, null, e.getNewValue()));
        constraintTable.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && constraintTable.getEditingCell() == null) {
                onDelete();
                e.consume();
            }
        });
        constraintTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
                deleteButton.setDisable(!editable || n == null));
    }

    /**
     * Shows the identity constraints of an element.
     *
     * @return true for (non-reference) elements
     */
    boolean show(XsdNode node) {
        element = node instanceof XsdElement e && (e.getRef() == null || e.getRef().isBlank()) ? e : null;
        if (element == null) {
            constraintTable.getItems().clear();
            return false;
        }
        editable = !element.isFromInclude();
        XsdIdentityConstraint selected = constraintTable.getSelectionModel().getSelectedItem();
        List<XsdIdentityConstraint> rows = element.getChildren().stream()
                .filter(XsdIdentityConstraint.class::isInstance).map(XsdIdentityConstraint.class::cast).toList();
        constraintTable.getSelectionModel().clearSelection();
        constraintTable.getItems().setAll(rows);
        if (selected != null && rows.contains(selected)) {
            constraintTable.getSelectionModel().select(selected);
        }
        constraintTable.setEditable(editable);
        addButton.setDisable(!editable);
        deleteButton.setDisable(!editable || constraintTable.getSelectionModel().getSelectedItem() == null);
        return true;
    }

    private static String kindOf(XsdIdentityConstraint constraint) {
        return switch (constraint) {
            case XsdKey ignored -> "key";
            case XsdKeyRef ignored -> "keyref";
            default -> "unique";
        };
    }

    private static String fieldsOf(XsdIdentityConstraint constraint) {
        return String.join(", ", constraint.getFields().stream().map(XsdField::getXpath).toList());
    }

    /** @return all identity constraints of the schema (their names share one symbol space) */
    private List<XsdIdentityConstraint> allConstraints() {
        List<XsdIdentityConstraint> out = new ArrayList<>();
        XsdSchema schema = overview.schema();
        if (schema == null) {
            return out;
        }
        Deque<XsdNode> stack = new ArrayDeque<>(List.of(schema));
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            XsdNode node = stack.pop();
            if (!visited.add(node.getId())) {
                continue;
            }
            if (node instanceof XsdIdentityConstraint constraint) {
                out.add(constraint);
            }
            node.getChildren().forEach(stack::push);
        }
        return out;
    }

    /** @return the qualified names of the keys and unique constraints a keyref can refer to */
    private List<String> referable() {
        XsdSchema schema = overview.schema();
        String prefix = schema != null ? AttributesPane.prefixFor(schema) : "";
        return allConstraints().stream().filter(c -> !(c instanceof XsdKeyRef))
                .map(c -> prefix + c.getName()).toList();
    }

    private String uniqueName(String base) {
        Set<String> names = new HashSet<>();
        allConstraints().forEach(c -> names.add(c.getName()));
        String name = base;
        for (int i = 2; names.contains(name); i++) {
            name = base + i;
        }
        return name;
    }

    private void edit(XsdIdentityConstraint constraint, String name, String selector, String fields, String refer) {
        if (constraint == null) {
            return;
        }
        String newName = name != null ? name.trim() : constraint.getName();
        if (name != null && !newName.equals(constraint.getName())) {
            for (XsdIdentityConstraint other : allConstraints()) {
                if (other != constraint && newName.equals(other.getName())) {
                    overview.run("Rename Constraint", EditCheck.blocked(
                            "The schema already has an identity constraint named '" + newName + "'."), null);
                    constraintTable.refresh();
                    return;
                }
            }
        }
        List<String> fieldList = fields != null ? Arrays.stream(fields.split(",")).map(String::trim).toList()
                : constraint.getFields().stream().map(XsdField::getXpath).toList();
        EditIdentityConstraintCommand command = new EditIdentityConstraintCommand(constraint, newName,
                selector != null ? selector : constraint.getSelector() != null ? constraint.getSelector().getXpath() : null,
                fieldList,
                refer != null ? refer : constraint instanceof XsdKeyRef keyRef ? keyRef.getRefer() : null);
        overview.run("Edit Identity Constraint", command.check(), command);
    }

    @FXML
    public void onAddKey() {
        add(AddIdentityConstraintCommand.Kind.KEY);
    }

    @FXML
    public void onAddUnique() {
        add(AddIdentityConstraintCommand.Kind.UNIQUE);
    }

    @FXML
    public void onAddKeyRef() {
        add(AddIdentityConstraintCommand.Kind.KEYREF);
    }

    private void add(AddIdentityConstraintCommand.Kind kind) {
        if (element == null || overview.getContext() == null) {
            return;
        }
        String refer = null;
        if (kind == AddIdentityConstraintCommand.Kind.KEYREF) {
            List<String> targets = referable();
            if (targets.isEmpty()) {
                overview.run("Add Keyref", EditCheck.blocked(
                        "A keyref refers to a key or unique constraint. Add one of those first."), null);
                return;
            }
            refer = targets.getFirst();
        }
        String name = uniqueName(element.getName() + switch (kind) {
            case KEY -> "Key";
            case UNIQUE -> "Unique";
            case KEYREF -> "Ref";
        });
        AddIdentityConstraintCommand command = new AddIdentityConstraintCommand(overview.getContext(), element,
                kind, name, ".//*", List.of("@id"), refer);
        if (overview.run("Add Identity Constraint", null, command) && command.getCreated() != null) {
            constraintTable.getSelectionModel().select(command.getCreated());
            constraintTable.edit(constraintTable.getItems().indexOf(command.getCreated()), selectorColumn);
        }
    }

    @FXML
    public void onDelete() {
        XsdIdentityConstraint selected = constraintTable.getSelectionModel().getSelectedItem();
        if (selected != null && editable) {
            overview.run("Delete Identity Constraint", null, new DeleteNodeCommand(selected));
        }
    }

    /** @return the table (tests) */
    TableView<XsdIdentityConstraint> table() {
        return constraintTable;
    }
}
