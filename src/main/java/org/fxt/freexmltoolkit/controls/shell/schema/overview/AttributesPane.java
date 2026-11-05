package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.ShellMenuItem;
import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoice;
import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoices;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * "Attributes" tab of the {@link SchemaOverviewView}: the attributes, attribute group
 * references and attribute wildcard of the selected element, complex type or attribute group,
 * edited in place. Default and Fixed offer the enumeration values of the attribute's type.
 *
 * @since 2.6
 */
final class AttributesPane extends VBox {

    private static final List<String> USES = List.of("optional", "required", "prohibited");

    @FXML private Button addButton;
    @FXML private IconifyIcon addIcon;
    @FXML private MenuButton groupRefButton;
    @FXML private IconifyIcon groupRefIcon;
    @FXML private Button deleteButton;
    @FXML private IconifyIcon deleteIcon;
    @FXML private Label hintLabel;
    @FXML private TableView<XsdNode> attributeTable;
    @FXML private TableColumn<XsdNode, String> nameColumn;
    @FXML private TableColumn<XsdNode, String> typeColumn;
    @FXML private TableColumn<XsdNode, String> useColumn;
    @FXML private TableColumn<XsdNode, String> defaultColumn;
    @FXML private TableColumn<XsdNode, String> fixedColumn;

    private final SchemaOverviewView overview;
    private XsdNode owner;
    private boolean editable;

    AttributesPane(SchemaOverviewView overview) {
        this.overview = overview;
        FxmlViews.load(this, "/pages/views/AttributesPane.fxml");
        SemanticIcon.bind(addIcon, ActionColor.CREATE);
        SemanticIcon.bind(groupRefIcon, ActionColor.CREATE);
        SemanticIcon.bind(deleteIcon, ActionColor.DELETE);

        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(nameOf(c.getValue())));
        nameColumn.setCellFactory(col -> OverviewCells.text(this::isPlainAttribute));
        nameColumn.setOnEditCommit(e -> rename(e.getRowValue(), e.getNewValue()));
        typeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue() instanceof XsdAttribute a ? nullToEmpty(a.getType()) : ""));
        typeColumn.setCellFactory(col -> OverviewCells.combo(this::isPlainAttribute, row -> typeSuggestions(), true));
        typeColumn.setOnEditCommit(e -> changeType(e.getRowValue(), e.getNewValue()));
        useColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue() instanceof XsdAttribute a ? (a.getUse() == null ? "optional" : a.getUse()) : ""));
        useColumn.setCellFactory(col -> OverviewCells.combo(this::isEditableAttribute, row -> USES, false));
        useColumn.setOnEditCommit(e -> changeUse(e.getRowValue(), e.getNewValue()));
        defaultColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue() instanceof XsdAttribute a ? nullToEmpty(a.getDefaultValue()) : ""));
        defaultColumn.setCellFactory(col -> OverviewCells.combo(this::isEditableAttribute, this::enumerationValues, true));
        defaultColumn.setOnEditCommit(e -> changeValue(e.getRowValue(), e.getNewValue(), false));
        fixedColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue() instanceof XsdAttribute a ? nullToEmpty(a.getFixed()) : ""));
        fixedColumn.setCellFactory(col -> OverviewCells.combo(this::isEditableAttribute, this::enumerationValues, true));
        fixedColumn.setOnEditCommit(e -> changeValue(e.getRowValue(), e.getNewValue(), true));
        attributeTable.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && attributeTable.getEditingCell() == null) {
                onDelete();
                e.consume();
            }
        });
        attributeTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateButtons());
    }

    /**
     * Shows the attributes of an element, complex type or attribute group.
     *
     * @return true when the node can have attributes
     */
    boolean show(XsdNode node) {
        owner = applies(node) ? node : null;
        if (owner == null) {
            attributeTable.getItems().clear();
            hintLabel.setText("");
            return false;
        }
        XsdNode container = XsdEditRules.attributeContainer(owner);
        editable = !owner.isFromInclude() && (container == null || !container.isFromInclude());
        List<XsdNode> rows = new ArrayList<>();
        if (container != null) {
            for (XsdNode child : container.getChildren()) {
                if (child instanceof XsdAttribute || child instanceof XsdAttributeGroup || child instanceof XsdAnyAttribute) {
                    rows.add(child);
                }
            }
        }
        XsdNode selected = attributeTable.getSelectionModel().getSelectedItem();
        attributeTable.getSelectionModel().clearSelection();
        attributeTable.getItems().setAll(rows);
        if (selected != null && rows.contains(selected)) {
            attributeTable.getSelectionModel().select(selected);
        }
        attributeTable.setEditable(editable);
        hintLabel.setText(hint(container));
        buildGroupMenu();
        updateButtons();
        return true;
    }

    private static boolean applies(XsdNode node) {
        return (node instanceof XsdElement element && (element.getRef() == null || element.getRef().isBlank()))
                || node instanceof XsdComplexType
                || (node instanceof XsdAttributeGroup group && !group.isReference());
    }

    private String hint(XsdNode container) {
        if (!editable) {
            return "Read-only: declared in an included file.";
        }
        if (owner instanceof XsdElement element && container instanceof XsdComplexType type
                && type.getParent() != element) {
            return "Attributes of type '" + type.getName() + "': changes apply to every use of the type.";
        }
        if (owner instanceof XsdElement && container == null) {
            EditCheck check = XsdEditRules.addAttribute(owner);
            return check.isAllowed() ? "Adding an attribute gives the element a complex type." : check.reason();
        }
        return "";
    }

    private void updateButtons() {
        addButton.setDisable(!editable || owner == null || !XsdEditRules.addAttribute(owner).isAllowed());
        // isDisable(), not isDisabled(): the tab may still be disabled while this pane is filled
        groupRefButton.setDisable(addButton.isDisable() || groupRefButton.getItems().isEmpty());
        deleteButton.setDisable(!editable || attributeTable.getSelectionModel().getSelectedItem() == null);
    }

    private void buildGroupMenu() {
        groupRefButton.getItems().clear();
        XsdSchema schema = overview.schema();
        if (schema == null) {
            return;
        }
        String prefix = prefixFor(schema);
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdAttributeGroup group && !group.isReference() && group != owner) {
                String ref = prefix + group.getName();
                ShellMenuItem item = new ShellMenuItem(ref, "bi-collection", ActionColor.CREATE);
                item.setOnAction(e -> addGroupRef(ref));
                groupRefButton.getItems().add(item);
            }
        }
    }

    /** @return the prefix (with colon) that refers to the schema's target namespace, or "" */
    static String prefixFor(XsdSchema schema) {
        String target = schema.getTargetNamespace();
        if (target == null || target.isBlank()) {
            return "";
        }
        for (var entry : schema.getNamespaces().entrySet()) {
            if (target.equals(entry.getValue())) {
                return entry.getKey().isEmpty() ? "" : entry.getKey() + ":";
            }
        }
        return "";
    }

    private boolean isPlainAttribute(XsdNode row) {
        return editable && row instanceof XsdAttribute a && (a.getRef() == null || a.getRef().isBlank());
    }

    private boolean isEditableAttribute(XsdNode row) {
        return editable && row instanceof XsdAttribute;
    }

    private static String nameOf(XsdNode row) {
        return switch (row) {
            case XsdAttribute a when a.getRef() != null && !a.getRef().isBlank() -> "ref " + a.getRef();
            case XsdAttributeGroup g -> "group " + nullToEmpty(g.getRef());
            case XsdAnyAttribute any -> "anyAttribute" + (any.getNamespace() == null ? "" : " " + any.getNamespace());
            default -> nullToEmpty(row.getName());
        };
    }

    private List<String> typeSuggestions() {
        List<String> names = new ArrayList<>();
        for (TypeChoice choice : TypeChoices.of(overview.schema())) {
            if (choice.kind() != TypeChoice.Kind.COMPLEX) {
                names.add(choice.qualifiedName());
            }
        }
        return names;
    }

    /** @return "" plus the enumeration values of the attribute's type (inline, referenced, inherited) */
    List<String> enumerationValues(XsdNode row) {
        Set<String> values = new LinkedHashSet<>();
        values.add("");
        XsdSchema schema = overview.schema();
        List<XsdFacet> facets = new ArrayList<>(SchemaFacets.collect(row));
        if (schema != null) {
            facets.addAll(SchemaFacets.resolveReferencedTypeFacets(row, schema));
            facets.addAll(SchemaFacets.resolveBaseChainFacets(row, schema));
        }
        for (XsdFacet facet : facets) {
            if (facet.getFacetType() == XsdFacetType.ENUMERATION && facet.getValue() != null) {
                values.add(facet.getValue());
            }
        }
        return new ArrayList<>(values);
    }

    private XsdEditorContext context() {
        return overview.getContext();
    }

    private void rename(XsdNode row, String name) {
        if (row == null || name == null || name.trim().equals(row.getName()) || name.isBlank()) {
            attributeTable.refresh();
            return;
        }
        String n = name.trim();
        EditCheck check = XsdEditRules.isNcName(n) ? EditCheck.allowed()
                : EditCheck.blocked("'" + n + "' is not a valid attribute name.");
        if (check.isAllowed()) {
            for (XsdNode sibling : attributeTable.getItems()) {
                if (sibling != row && sibling instanceof XsdAttribute && n.equals(sibling.getName())) {
                    check = EditCheck.blocked("There already is an attribute named '" + n + "'.");
                }
            }
        }
        overview.run("Rename Attribute", check, new RenameNodeCommand(row, n));
    }

    private void changeType(XsdNode row, String type) {
        if (!(row instanceof XsdAttribute attribute) || context() == null
                || nullToEmpty(type).trim().equals(nullToEmpty(attribute.getType()))) {
            attributeTable.refresh();
            return;
        }
        String value = type.trim();
        Optional<Boolean> resolution = EditRuleDialogs.resolve(overview.window(), "Change Type",
                XsdEditRules.changeType(attribute, value));
        if (resolution.isEmpty()) {
            attributeTable.refresh();
            return;
        }
        overview.run("Change Type", null, resolution.get()
                ? ChangeTypeCommand.replacingInlineType(context(), attribute, value)
                : new ChangeTypeCommand(context(), attribute, value));
    }

    private void changeUse(XsdNode row, String use) {
        if (row instanceof XsdAttribute attribute && context() != null && use != null
                && !use.equals(attribute.getUse() == null ? "optional" : attribute.getUse())) {
            EditCheck check = "required".equals(use) && attribute.getDefaultValue() != null
                    ? EditCheck.blocked("A required attribute cannot have a default value.")
                    : EditCheck.allowed();
            overview.run("Change Use", check, new ChangeUseCommand(context(), attribute, use));
        } else {
            attributeTable.refresh();
        }
    }

    void changeValue(XsdNode row, String value, boolean fixed) {
        if (!(row instanceof XsdAttribute attribute) || context() == null) {
            return;
        }
        String v = value == null || value.isEmpty() ? null : value;
        String current = fixed ? attribute.getFixed() : attribute.getDefaultValue();
        if (java.util.Objects.equals(v, current)) {
            attributeTable.refresh();
            return;
        }
        EditCheck check = EditCheck.allowed();
        if (v != null && (fixed ? attribute.getDefaultValue() : attribute.getFixed()) != null) {
            check = EditCheck.blocked("An attribute cannot have both a default and a fixed value.");
        } else if (v != null && !fixed && "required".equals(attribute.getUse())) {
            check = EditCheck.blocked("A required attribute cannot have a default value.");
        }
        overview.run(fixed ? "Fixed Value" : "Default Value", check, fixed
                ? new ChangeFixedValueCommand(context(), attribute, v)
                : new ChangeDefaultValueCommand(context(), attribute, v));
    }

    @FXML
    public void onAdd() {
        if (owner == null) {
            return;
        }
        Set<String> taken = new LinkedHashSet<>();
        attributeTable.getItems().forEach(r -> taken.add(r.getName()));
        String name = "attribute1";
        for (int i = 2; taken.contains(name); i++) {
            name = "attribute" + i;
        }
        XsdSchema schema = overview.schema();
        String type = XsdEditRules.xsdPrefix(schema != null ? schema : owner) + ":string";
        NewAttributeSpec spec = new NewAttributeSpec(name, type, null, null, null, null, null);
        if (overview.run("Add Attribute", XsdEditRules.newAttribute(owner, spec), AddAttributeCommand.of(owner, spec))) {
            String added = name;
            attributeTable.getItems().stream().filter(r -> added.equals(r.getName())).findFirst().ifPresent(r -> {
                attributeTable.getSelectionModel().select(r);
                attributeTable.edit(attributeTable.getItems().indexOf(r), nameColumn);
            });
        }
    }

    private void addGroupRef(String ref) {
        if (owner == null) {
            return;
        }
        AddAttributeGroupRefCommand command = new AddAttributeGroupRefCommand(owner, ref);
        overview.run("Add Attribute Group Reference", command.check(), command);
    }

    @FXML
    public void onDelete() {
        XsdNode row = attributeTable.getSelectionModel().getSelectedItem();
        if (row != null && editable) {
            overview.run("Delete Attribute", null, new DeleteNodeCommand(row));
        }
    }

    /** @return the table (tests) */
    TableView<XsdNode> table() {
        return attributeTable;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
