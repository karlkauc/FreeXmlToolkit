package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.CompositeXmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.ConvertNodeTypeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetCDataTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetCommentTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetProcessingInstructionCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
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

    @Override
    public List<FlatRow> flatten(java.util.function.Predicate<String> groupAsTable) {
        return FlatRow.flatten(context.getDocument(), groupAsTable);
    }

    /**
     * Attaches RepeatingElementsTable instances to FlatRows that represent
     * repeating element groups (2+ siblings with the same tag name).
     */
    @Override
    public String collapsedPreview(FlatRow row, int maxChars) {
        if (row.getType() == FlatRow.RowType.GROUP) {
            return null;
        }
        return row.getModelNode() instanceof XmlNode node ? XmlNodePreview.of(node, maxChars) : null;
    }

    @Override
    public void attachTables(List<FlatRow> rows, Runnable onLayoutChanged) {
        for (FlatRow row : rows) {
            // flatten() already grouped the siblings; rescanning them per row cost tens of ms
            List<XmlElement> group = row.getRepeatingGroup();
            if (group != null && !group.isEmpty()) {
                // Built when the group is first expanded or drawn (FlatRow#setRepeatingTableFactory)
                String label = row.getLabel();
                int depth = row.getDepth();
                row.setRepeatingTableFactory(
                        () -> new RepeatingElementsTable(label, group, depth, onLayoutChanged));
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
        if (row == null || row.getType() == FlatRow.RowType.GROUP) {
            return null; // a sibling group is no node: menu actions must not hit the parent
        }
        return row.getModelNode() instanceof XmlNode node ? node : null;
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
        return switch (row.getType()) {
            case ELEMENT -> row.getModelNode() instanceof XmlElement && !row.isTableGroupOwner();
            case ATTRIBUTE -> row.getModelNode() instanceof XmlElement;
            case PROCESSING_INSTRUCTION -> row.getModelNode() instanceof XmlProcessingInstruction;
            default -> false;
        };
    }

    @Override
    public boolean canEditValue(FlatRow row) {
        if (row.getType() == FlatRow.RowType.DOCUMENT || row.getType() == FlatRow.RowType.GROUP
                || row.isTableGroupOwner()) {
            return false;
        }
        return !(row.getType() == FlatRow.RowType.ELEMENT
                && row.getModelNode() instanceof XmlElement el && el.hasElementChildren());
    }

    @Override
    public List<String> typeTargets(FlatRow row) {
        if (row.isTableGroupOwner() || row.getType() == FlatRow.RowType.GROUP) {
            return List.of();
        }
        List<ConvertNodeTypeCommand.Kind> kinds;
        if (row.getType() == FlatRow.RowType.ATTRIBUTE) {
            kinds = row.getModelNode() instanceof XmlElement ? ConvertNodeTypeCommand.targetsOfAttribute() : List.of();
        } else if (row.getModelNode() instanceof XmlNode node) {
            kinds = ConvertNodeTypeCommand.targetsOfNode(node);
        } else {
            kinds = List.of();
        }
        return kinds.stream().map(ConvertNodeTypeCommand.Kind::label).toList();
    }

    // ==================== Validation markers ====================

    @Override
    public int sourceLine(FlatRow row) {
        if (row.getType() == FlatRow.RowType.GROUP) {
            return 0;
        }
        return row.getModelNode() instanceof XmlElement el ? Math.max(0, el.getSourceLineNumber()) : 0;
    }

    @Override
    public int[] memberSourceLines(FlatRow row) {
        List<XmlElement> members = row.getRepeatingGroup();
        if (members == null) {
            return new int[0];
        }
        int[] lines = new int[members.size()];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Math.max(0, members.get(i).getSourceLineNumber());
        }
        return lines;
    }

    // ==================== Drag & drop ====================

    /** @return the node a row stands for when dragged: attributes and table owners are not draggable */
    private static XmlNode draggableNode(FlatRow row) {
        if (row == null || row.getType() == FlatRow.RowType.ATTRIBUTE || row.getType() == FlatRow.RowType.GROUP
                || row.isTableGroupOwner()) {
            return null;
        }
        return row.getModelNode() instanceof XmlNode node && node.getParent() instanceof XmlElement ? node : null;
    }

    @Override
    public String dragText(FlatRow row) {
        XmlNode node = draggableNode(row);
        return node != null ? node.serialize(0) : null;
    }

    @Override
    public boolean canDrag(FlatRow row) {
        return draggableNode(row) != null;
    }

    @Override
    public boolean canDrop(FlatRow dragged, FlatRow target, DropPosition position) {
        if (target == null || target.getType() == FlatRow.RowType.ATTRIBUTE
                || target.getType() == FlatRow.RowType.GROUP) {
            return false;
        }
        Object targetNode = target.getModelNode();
        if (position == DropPosition.VALUE) {
            return dragged == null && canEditValue(target);
        }
        if (position == DropPosition.INTO) {
            if (!(targetNode instanceof XmlElement el) || target.isTableGroupOwner() || el.hasNonWhitespaceTextContent()) {
                return false;
            }
        } else if (!(targetNode instanceof XmlNode tn) || !(tn.getParent() instanceof XmlElement)) {
            return false; // no siblings of the root / at document level
        }
        if (dragged == null) {
            return true;
        }
        XmlNode node = draggableNode(dragged);
        if (node == null || !(targetNode instanceof XmlNode tn)) {
            return false;
        }
        // Never into itself or one of its descendants
        return tn != node && !tn.isDescendantOf(node);
    }

    @Override
    public boolean dropRow(FlatRow dragged, FlatRow target, DropPosition position, boolean copy) {
        if (!canDrop(dragged, target, position)) {
            return false;
        }
        XmlNode node = draggableNode(dragged);
        XmlNode targetNode = (XmlNode) target.getModelNode();
        XmlElement parent;
        int index;
        if (position == DropPosition.INTO) {
            parent = (XmlElement) targetNode;
            index = -1;
        } else {
            parent = (XmlElement) targetNode.getParent();
            index = parent.indexOf(targetNode) + (position == DropPosition.AFTER ? 1 : 0);
        }
        if (copy) {
            XmlNode duplicate = node.deepCopy("");
            return context.executeCommand(new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.InsertNodeCommand(
                    parent, duplicate, index));
        }
        // MoveNodeCommand applies the index after removing the node from its old place
        if (index >= 0 && node.getParent() == parent && parent.indexOf(node) < index) {
            index--;
        }
        if (node.getParent() == parent && index == parent.indexOf(node)) {
            return false; // dropped onto its own place
        }
        return context.executeCommand(new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.MoveNodeCommand(
                node, parent, index));
    }

    @Override
    public boolean dropText(FlatRow target, DropPosition position, String text) {
        if (text == null || !canDrop(null, target, position)) {
            return false;
        }
        if (position == DropPosition.VALUE) {
            return commitRowEdit(target, false, text.strip());
        }
        XmlNode targetNode = (XmlNode) target.getModelNode();
        XmlElement parent = position == DropPosition.INTO ? (XmlElement) targetNode : (XmlElement) targetNode.getParent();
        int index = position == DropPosition.INTO ? -1
                : parent.indexOf(targetNode) + (position == DropPosition.AFTER ? 1 : 0);
        List<XmlNode> nodes = parseFragment(text);
        List<org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand> parts = new java.util.ArrayList<>();
        int at = index;
        for (XmlNode n : nodes) {
            parts.add(new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.InsertNodeCommand(parent, n, at));
            if (at >= 0) {
                at++;
            }
        }
        return !parts.isEmpty() && context.executeCommand(new CompositeXmlCommand("Drop " + parts.size() + " node(s)", parts));
    }

    /** Parses dropped text as an XML fragment (nodes); plain text becomes one text node. */
    static List<XmlNode> parseFragment(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("<")) {
            try {
                org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc =
                        new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser()
                                .parse("<fxt-fragment>" + trimmed + "</fxt-fragment>");
                XmlElement wrapper = doc.getRootElement();
                List<XmlNode> nodes = new java.util.ArrayList<>();
                for (XmlNode child : List.copyOf(wrapper.getChildren())) {
                    if (child instanceof XmlText t && t.isWhitespace()) {
                        continue;
                    }
                    nodes.add(child.deepCopy(""));
                }
                return nodes;
            } catch (Exception e) {
                // not well-formed: fall through to plain text
            }
        }
        return trimmed.isEmpty() ? List.of() : List.of(new XmlText(trimmed));
    }

    @Override
    public boolean changeType(FlatRow row, String targetLabel) {
        ConvertNodeTypeCommand.Kind target = null;
        for (ConvertNodeTypeCommand.Kind kind : ConvertNodeTypeCommand.Kind.values()) {
            if (kind.label().equals(targetLabel)) {
                target = kind;
            }
        }
        if (target == null || !typeTargets(row).contains(targetLabel)) {
            return false;
        }
        ConvertNodeTypeCommand cmd = row.getType() == FlatRow.RowType.ATTRIBUTE
                ? ConvertNodeTypeCommand.ofAttribute((XmlElement) row.getModelNode(), row.getLabel(), target)
                : ConvertNodeTypeCommand.ofNode((XmlNode) row.getModelNode(), target);
        return context.executeCommand(cmd);
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
            String name = newValue == null ? "" : newValue.trim();
            if (name.isEmpty()) {
                return true;
            }
            if (row.getType() == FlatRow.RowType.ATTRIBUTE && modelNode instanceof XmlElement owner) {
                if (!name.equals(row.getLabel())) {
                    context.executeCommand(new RenameAttributeCommand(owner, row.getLabel(), name));
                }
            } else if (modelNode instanceof XmlProcessingInstruction pi) {
                context.executeCommand(new SetProcessingInstructionCommand(pi, name, pi.getData()));
            } else if (modelNode instanceof XmlElement element) {
                context.executeCommand(new RenameNodeCommand(element, name));
            }
            return true;
        }
        if (modelNode instanceof XmlComment comment) {
            context.executeCommand(new SetCommentTextCommand(comment, newValue));
            return true;
        }
        if (modelNode instanceof XmlCData cdata) {
            context.executeCommand(new SetCDataTextCommand(cdata, newValue));
            return true;
        }
        if (modelNode instanceof XmlProcessingInstruction pi) {
            context.executeCommand(new SetProcessingInstructionCommand(pi, pi.getTarget(), newValue));
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
            XmlElement childElement = rowElement.getChildElement(columnName);
            if (childElement != null) {
                context.executeCommand(new SetElementTextCommand(childElement, newValue));
            } else if (newValue != null && !newValue.isEmpty()) {
                // The member lacks this column's element: create it with the typed text
                context.executeCommand(TableEditOps.createCellChild(rowElement, columnName, newValue));
            }
        } else if (col.getType() == RepeatingElementsTable.ColumnType.TEXT_CONTENT) {
            context.executeCommand(new SetElementTextCommand(rowElement, newValue));
        }
        return true;
    }

    @Override
    public boolean pasteCells(RepeatingElementsTable table, int startRow, int startCol,
                              List<List<String>> values) {
        CompositeXmlCommand cmd = TableEditOps.pasteCells(table, startRow, startCol, values);
        return !cmd.isEmpty() && context.executeCommand(cmd);
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
    public GridContextMenu<XmlNode> createContextMenu(Runnable refresh, GridContextMenu.ViewActions actions) {
        return new XmlGridContextMenu(context, refresh, actions);
    }

    /**
     * The positional XPath of the row's node; attribute rows add {@code /@name}, text,
     * comment, CDATA and PI rows add {@code /#<child index>} under their parent's path.
     */
    @Override
    public String stableKey(FlatRow row) {
        Object node = row.getModelNode();
        if (node instanceof XmlElement element) {
            String path = org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath.positional(element);
            return row.getType() == FlatRow.RowType.ATTRIBUTE ? path + "/@" + row.getLabel() : path;
        }
        if (node instanceof XmlNode xmlNode) {
            XmlNode parent = xmlNode.getParent();
            String parentPath = org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath.positional(parent);
            int index = parent instanceof XmlElement pe ? pe.indexOf(xmlNode)
                    : parent instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc
                    ? doc.getChildren().indexOf(xmlNode) : -1;
            return parentPath + "/#" + index;
        }
        return GridModelAdapter.super.stableKey(row);
    }
}
