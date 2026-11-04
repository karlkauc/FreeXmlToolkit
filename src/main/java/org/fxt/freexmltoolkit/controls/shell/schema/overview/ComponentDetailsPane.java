package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeDocumentationsCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeNotationCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditSchemaReferenceCommand;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * "Details" tab of the {@link SchemaOverviewView}: edits what the Inspector does not cover
 * (namespace and location of imports/includes/redefines/overrides, notation identifiers, the
 * text of a schema-level annotation) and summarizes the other components with links to the
 * type editor and the Graphic view.
 *
 * @since 2.6
 */
final class ComponentDetailsPane extends VBox {

    @FXML private Label titleLabel;
    @FXML private GridPane referenceBox;
    @FXML private Label namespaceLabel;
    @FXML private TextField namespaceField;
    @FXML private TextField locationField;
    @FXML private Label resolutionLabel;
    @FXML private GridPane notationBox;
    @FXML private TextField publicField;
    @FXML private TextField systemField;
    @FXML private VBox annotationBox;
    @FXML private TextField languageField;
    @FXML private TextArea documentationArea;
    @FXML private VBox summaryBox;
    @FXML private Label summaryLabel;
    @FXML private Button typeEditorButton;
    @FXML private Button graphicButton;

    private final SchemaOverviewView overview;
    private XsdNode node;

    ComponentDetailsPane(SchemaOverviewView overview) {
        this.overview = overview;
        FxmlViews.load(this, "/pages/views/ComponentDetailsPane.fxml");
    }

    /**
     * Shows the details of a row.
     *
     * @return true when the tab has something to show
     */
    boolean show(XsdNode node) {
        this.node = node;
        boolean editable = node != null && !node.isFromInclude();
        showOnly(null);
        if (node == null) {
            titleLabel.setText("");
            return false;
        }
        titleLabel.setText(OverviewRows.kind(node) + (OverviewRows.name(node).isEmpty() ? "" : "  " + OverviewRows.name(node)));
        switch (node) {
            case XsdSchemaReference reference -> {
                showOnly(referenceBox);
                boolean isImport = reference instanceof XsdImport;
                namespaceLabel.setVisible(isImport);
                namespaceField.setVisible(isImport);
                namespaceField.setText(isImport && ((XsdImport) reference).getNamespace() != null
                        ? ((XsdImport) reference).getNamespace() : "");
                locationField.setText(reference.getSchemaLocation() == null ? "" : reference.getSchemaLocation());
                resolutionLabel.setText(reference.getResolutionError() != null
                        ? "Not resolved: " + reference.getResolutionError()
                        : reference.getResolvedPath() != null ? "Resolved to " + reference.getResolvedPath() : "");
                referenceBox.setDisable(!editable);
            }
            case XsdNotation notation -> {
                showOnly(notationBox);
                publicField.setText(notation.getPublicId() == null ? "" : notation.getPublicId());
                systemField.setText(notation.getSystemId() == null ? "" : notation.getSystemId());
                notationBox.setDisable(!editable);
            }
            case XsdAnnotation annotation -> {
                showOnly(annotationBox);
                List<XsdDocumentation> docs = annotation.getDocumentations();
                XsdDocumentation first = docs.isEmpty() ? null : docs.getFirst();
                languageField.setText(first != null && first.getLang() != null ? first.getLang() : "");
                documentationArea.setText(first != null ? first.getText() : "");
                annotationBox.setDisable(!editable);
            }
            default -> {
                showOnly(summaryBox);
                summaryLabel.setText(summary(node));
                boolean type = node instanceof XsdComplexType || node instanceof XsdSimpleType;
                typeEditorButton.setVisible(type);
                typeEditorButton.setManaged(type);
                graphicButton.setVisible(node instanceof XsdElement);
                graphicButton.setManaged(node instanceof XsdElement);
            }
        }
        return true;
    }

    private static String summary(XsdNode node) {
        String details = OverviewRows.details(node);
        String origin = OverviewRows.origin(node);
        StringBuilder text = new StringBuilder(details.isEmpty() ? OverviewRows.kind(node) : details);
        if (origin != null) {
            text.append("\nDeclared in the included file ").append(origin).append(" (read-only here).");
        }
        if (node instanceof XsdComment comment) {
            text = new StringBuilder(comment.getContent() == null ? "" : comment.getContent().strip());
        }
        return text.toString();
    }

    private void showOnly(Node section) {
        for (Node box : List.of(referenceBox, notationBox, annotationBox, summaryBox)) {
            box.setVisible(box == section);
            box.setManaged(box == section);
        }
    }

    @FXML
    public void onApplyReference() {
        if (node instanceof XsdSchemaReference reference) {
            EditSchemaReferenceCommand command = new EditSchemaReferenceCommand(reference,
                    namespaceField.getText(), locationField.getText());
            if (command.changesSomething()) {
                overview.run("Edit " + OverviewRows.kind(reference), command.check(), command);
            }
        }
    }

    @FXML
    public void onBrowse() {
        if (!(node instanceof XsdSchemaReference)) {
            return;
        }
        Path base = overview.host().documentPath();
        SchemaReferenceDialog.chooseSchemaFile(overview.window(), base).ifPresent(choice -> {
            locationField.setText(choice.location());
            if (node instanceof XsdImport && choice.targetNamespace() != null) {
                namespaceField.setText(choice.targetNamespace());
            }
            onApplyReference();
        });
    }

    @FXML
    public void onOpenReference() {
        if (node instanceof XsdSchemaReference reference) {
            overview.host().openReferencedFile(reference);
        }
    }

    @FXML
    public void onApplyNotation() {
        if (node instanceof XsdNotation notation) {
            ChangeNotationCommand command = new ChangeNotationCommand(notation, publicField.getText(), systemField.getText());
            overview.run("Edit Notation", command.check(), command);
        }
    }

    @FXML
    public void onApplyAnnotation() {
        if (node instanceof XsdAnnotation annotation && overview.getContext() != null) {
            List<XsdDocumentation> docs = new ArrayList<>(annotation.getDocumentations());
            String text = documentationArea.getText() == null ? "" : documentationArea.getText().strip();
            String lang = languageField.getText() == null || languageField.getText().isBlank()
                    ? null : languageField.getText().trim();
            XsdDocumentation first = docs.isEmpty() ? null : docs.removeFirst();
            if (!text.isEmpty()) {
                docs.addFirst(new XsdDocumentation(text, lang, first != null ? first.getSource() : null));
            }
            overview.run("Edit Annotation", null,
                    new ChangeDocumentationsCommand(overview.getContext(), annotation, docs));
        }
    }

    @FXML
    public void onOpenTypeEditor() {
        if (node != null) {
            overview.host().openTypeEditor(node.getName());
        }
    }

    @FXML
    public void onShowInGraphic() {
        if (node != null) {
            overview.host().showInGraphic(node);
        }
    }
}
