package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SortElementsCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.TypeAwareWidgetFactory;

/**
 * {@link GridModelAdapter} for the XML instance grid: flattens the {@link XmlDocument} of an
 * {@link XmlEditorContext} via {@link FlatRow#flatten(XmlDocument)}, turns same-name sibling
 * groups into {@link RepeatingElementsTable}s, offers schema-driven type-aware edit widgets
 * and documentation tooltips, and maps inline edits onto the XML command set.
 */
public class XmlGridAdapter implements GridModelAdapter<XmlNode> {

    private static final Logger logger = LogManager.getLogger(XmlGridAdapter.class);

    private final XmlEditorContext context;

    public XmlGridAdapter(XmlEditorContext context) {
        this.context = context;
    }

    /** @return the wrapped editor context */
    public XmlEditorContext getContext() {
        return context;
    }

    // ==================== Document → rows ====================

    @Override
    public boolean hasDocument() {
        return context.getDocument() != null;
    }

    @Override
    public List<FlatRow> flatten() {
        return FlatRow.flatten(context.getDocument());
    }

    /**
     * Attaches RepeatingElementsTable instances to FlatRows that represent
     * repeating element groups (2+ siblings with the same tag name).
     */
    @Override
    public void attachTables(List<FlatRow> rows, Runnable onLayoutChanged) {
        for (FlatRow row : rows) {
            if (row.getType() != FlatRow.RowType.ELEMENT) {
                continue;
            }
            if (row.getParentRow() == null) {
                continue;
            }

            Object parentModel = row.getParentRow().getModelNode();
            if (!(parentModel instanceof XmlElement parentElement)) {
                continue;
            }

            // Check if this element name appears multiple times under parent
            List<XmlElement> sameNameSiblings = new ArrayList<>();
            for (XmlNode sibling : parentElement.getChildren()) {
                if (sibling instanceof XmlElement se && se.getName().equals(row.getLabel())) {
                    sameNameSiblings.add(se);
                }
            }

            if (sameNameSiblings.size() >= 2) {
                RepeatingElementsTable table = new RepeatingElementsTable(
                        row.getLabel(), sameNameSiblings, row.getDepth(), onLayoutChanged);
                row.setRepeatingTable(table);
            }
        }
    }

    @Override
    public void addModelListener(Runnable onModelChanged) {
        context.addPropertyChangeListener("document", evt -> onModelChanged.run());
    }

    @Override
    public void installViewHooks(GridCanvasView<XmlNode> view) {
        context.addPropertyChangeListener("mixedContentDetected", this::onMixedContentDetected);
    }

    /**
     * Handles mixed content detection event.
     */
    @SuppressWarnings("unchecked")
    private void onMixedContentDetected(PropertyChangeEvent evt) {
        List<XmlElement> mixedElements = (List<XmlElement>) evt.getNewValue();
        if (mixedElements == null || mixedElements.isEmpty()) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Mixed Content Detected");
            alert.setHeaderText("This XML contains " + mixedElements.size()
                    + " element(s) with mixed content");

            StringBuilder details = new StringBuilder();
            details.append("Elements with both text and child elements were found:\n\n");
            int count = 0;
            for (XmlElement elem : mixedElements) {
                if (count++ >= 5) {
                    details.append("... and ").append(mixedElements.size() - 5).append(" more\n");
                    break;
                }
                details.append("- <").append(elem.getName()).append(">\n");
            }
            details.append("\nThis may cause display issues. Consider removing either the text content "
                    + "or child elements to ensure valid XML structure.");

            alert.setContentText(details.toString());
            alert.showAndWait();
        });
    }

    // ==================== Nodes & selection ====================

    @Override
    public XmlNode nodeOf(FlatRow row) {
        return row != null && row.getModelNode() instanceof XmlNode node ? node : null;
    }

    @Override
    public XmlNode nodeOf(RepeatingElementsTable.TableRow row) {
        return row != null ? row.getElement() : null;
    }

    @Override
    public void select(XmlNode node) {
        context.getSelectionModel().setSelectedNode(node);
    }

    // ==================== Rendering ====================

    @Override
    public String decorateLeafValue(FlatRow row) {
        return "\"" + row.getValue() + "\"";
    }

    @Override
    public String emptyStateText() {
        return "No XML document loaded";
    }

    // ==================== Inline editing ====================

    @Override
    public boolean canEditName(FlatRow row) {
        return row.getType() == FlatRow.RowType.ELEMENT && row.getModelNode() instanceof XmlElement;
    }

    @Override
    public boolean canEditValue(FlatRow row) {
        return true;
    }

    @Override
    public EditSpec editSpec(FlatRow row, boolean nameEdit) {
        if (nameEdit) {
            return EditSpec.plain(row.getLabel());
        }
        String currentValue = row.getValue() != null ? row.getValue() : "";
        String elementXPath = pathFor(row.getModelNode());
        String attributeName = (row.getType() == FlatRow.RowType.ATTRIBUTE) ? row.getLabel() : null;
        return schemaAwareSpec(currentValue, elementXPath, attributeName);
    }

    @Override
    public EditSpec cellEditSpec(RepeatingElementsTable table, int rowIndex, String columnName) {
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        RepeatingElementsTable.TableColumn col = table.getColumn(columnName);
        String currentValue = row.getValue(columnName);
        String elementXPath = pathFor(row.getElement());
        String attributeName = (col != null && col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE)
                ? columnName : null;
        return schemaAwareSpec(currentValue, elementXPath, attributeName);
    }

    /**
     * Builds the edit spec for a value: a type-aware widget from the schema (date picker,
     * enumeration combo, …) when a schema is bound and the factory knows the type, otherwise
     * a plain text field carrying the schema documentation as tooltip.
     */
    private EditSpec schemaAwareSpec(String currentValue, String elementXPath, String attributeName) {
        if (!context.hasSchema() || elementXPath == null) {
            return EditSpec.plain(currentValue);
        }
        TypeAwareWidgetFactory factory = context.getWidgetFactory();
        TypeAwareWidgetFactory.EditWidget widget;
        if (attributeName != null) {
            widget = factory.createAttributeWidget(elementXPath, attributeName, currentValue, null);
        } else {
            widget = factory.createElementWidget(elementXPath, currentValue, null);
        }
        if (widget != null) {
            logger.debug("Created type-aware widget for {} (attribute: {})", elementXPath, attributeName);
            return new EditSpec(currentValue, widget, null);
        }
        Optional<String> doc = attributeName != null
                ? context.getSchemaProvider().getAttributeDocumentation(elementXPath, attributeName)
                : context.getSchemaProvider().getElementDocumentation(elementXPath);
        return new EditSpec(currentValue, null, doc.orElse(null));
    }

    /**
     * Builds the schema-lookup XPath ({@code /root/child/leaf}, no positions) of the element
     * behind a model node: the element itself, or the parent element of an attribute-owner,
     * text, comment, CDATA or PI node.
     */
    static String pathFor(Object modelNode) {
        XmlElement element = null;
        if (modelNode instanceof XmlElement el) {
            element = el;
        } else if (modelNode instanceof XmlNode node && node.getParent() instanceof XmlElement parent) {
            element = parent;
        }
        StringBuilder path = new StringBuilder();
        XmlNode current = element;
        while (current instanceof XmlElement el) {
            if (path.length() > 0) {
                path.insert(0, "/");
            }
            path.insert(0, el.getName());
            current = el.getParent();
        }
        return "/" + path;
    }

    @Override
    public boolean commitRowEdit(FlatRow row, boolean nameEdit, String newValue) {
        Object modelNode = row.getModelNode();
        if (nameEdit) {
            // Editing element name
            if (modelNode instanceof XmlElement element && !newValue.trim().isEmpty()) {
                context.executeCommand(new RenameNodeCommand(element, newValue.trim()));
            }
            return true;
        }
        // Editing value
        if (row.getType() == FlatRow.RowType.ATTRIBUTE) {
            if (modelNode instanceof XmlElement element) {
                context.executeCommand(new SetAttributeCommand(element, row.getLabel(), newValue));
            }
        } else if (row.getType() == FlatRow.RowType.ELEMENT) {
            if (modelNode instanceof XmlElement element) {
                context.executeCommand(new SetElementTextCommand(element, newValue));
            }
        } else if (row.getType() == FlatRow.RowType.TEXT && modelNode instanceof XmlText text) {
            context.executeCommand(new SetTextCommand(text, newValue));
        }
        return true;
    }

    @Override
    public boolean commitCellEdit(RepeatingElementsTable table, int rowIndex, String columnName, String newValue) {
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        XmlElement rowElement = row.getElement();
        RepeatingElementsTable.TableColumn col = table.getColumn(columnName);
        if (col == null || rowElement == null) {
            return true;
        }
        if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            context.executeCommand(new SetAttributeCommand(rowElement, columnName, newValue));
        } else if (col.getType() == RepeatingElementsTable.ColumnType.CHILD_ELEMENT) {
            for (XmlNode child : rowElement.getChildren()) {
                if (child instanceof XmlElement childElement && childElement.getName().equals(columnName)) {
                    context.executeCommand(new SetElementTextCommand(childElement, newValue));
                    break;
                }
            }
        } else if (col.getType() == RepeatingElementsTable.ColumnType.TEXT_CONTENT) {
            context.executeCommand(new SetElementTextCommand(rowElement, newValue));
        }
        return true;
    }

    @Override
    public void sortTable(RepeatingElementsTable table, String columnName, boolean ascending) {
        context.executeCommand(new SortElementsCommand(table, columnName, ascending));
        table.setSortState(columnName, ascending);
    }

    // ==================== Serialization & menu ====================

    @Override
    public String serialize() {
        return context.serializeToString();
    }

    @Override
    public GridContextMenu<XmlNode> createContextMenu(Runnable refresh) {
        return new XmlGridContextMenu(context, refresh);
    }
}
