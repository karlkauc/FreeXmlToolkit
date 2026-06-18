package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Set;

import org.fxt.freexmltoolkit.domain.XmlTemplate;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * Create/edit dialog for a user (non-built-in) {@link XmlTemplate}: name, category,
 * description, file type and content. On OK it returns the populated template — a new
 * instance when {@code existing} is {@code null}, otherwise the same (mutated) instance
 * so its id/file stay stable. Persistence stays with the caller (the settings panel,
 * via {@code TemplateRepository.saveTemplateToFile}).
 */
public class TemplateEditDialog extends Dialog<XmlTemplate> {

    private static final EditorFileType[] TYPES = {
            EditorFileType.XML, EditorFileType.XSD, EditorFileType.XSLT,
            EditorFileType.SCHEMATRON, EditorFileType.JSON
    };

    private final TextField nameField = new TextField();
    private final TextField categoryField = new TextField();
    private final TextField descriptionField = new TextField();
    private final ComboBox<EditorFileType> typeBox = new ComboBox<>();
    private final TextArea contentArea = new TextArea();
    private final XmlTemplate existing;

    public TemplateEditDialog(XmlTemplate existing) {
        this.existing = existing;
        setTitle(existing == null ? "New Template" : "Edit Template");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(620);

        typeBox.getItems().setAll(TYPES);
        typeBox.setButtonCell(typeCell());
        typeBox.setCellFactory(lv -> typeCell());
        typeBox.setMaxWidth(Double.MAX_VALUE);
        categoryField.setPromptText("Custom");
        contentArea.setPrefRowCount(14);
        contentArea.setStyle("-fx-font-family: 'monospace';");

        prefill();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        int row = 0;
        grid.add(new Label("Name:"), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label("Category:"), 0, row);
        grid.add(categoryField, 1, row++);
        grid.add(new Label("Description:"), 0, row);
        grid.add(descriptionField, 1, row++);
        grid.add(new Label("File type:"), 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(new Label("Content:"), 0, row);
        grid.add(contentArea, 1, row++);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(contentArea, Priority.ALWAYS);
        GridPane.setVgrow(contentArea, Priority.ALWAYS);
        getDialogPane().setContent(grid);

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        Runnable validate = () -> ok.setDisable(
                nameField.getText().isBlank() || contentArea.getText().isBlank());
        nameField.textProperty().addListener((o, a, b) -> validate.run());
        contentArea.textProperty().addListener((o, a, b) -> validate.run());
        validate.run();

        setResultConverter(button -> button == ButtonType.OK ? buildTemplate() : null);
    }

    private void prefill() {
        if (existing != null) {
            nameField.setText(orEmpty(existing.getName()));
            categoryField.setText(orEmpty(existing.getCategory()));
            descriptionField.setText(orEmpty(existing.getDescription()));
            contentArea.setText(orEmpty(existing.getContent()));
            typeBox.getSelectionModel().select(typeOf(existing));
        } else {
            typeBox.getSelectionModel().select(EditorFileType.XML);
            categoryField.setText("Custom");
        }
    }

    private XmlTemplate buildTemplate() {
        XmlTemplate t = existing != null ? existing : new XmlTemplate();
        t.setName(nameField.getText().trim());
        String cat = categoryField.getText();
        t.setCategory(cat == null || cat.isBlank() ? "Custom" : cat.trim());
        t.setDescription(descriptionField.getText() == null ? "" : descriptionField.getText().trim());
        t.setContent(contentArea.getText());
        t.setBuiltIn(false);
        EditorFileType type = typeBox.getValue() == null ? EditorFileType.XML : typeBox.getValue();
        t.setFileExtensions(Set.of(type.primaryExtension()));
        return t;
    }

    /** Derives the editor file type of an existing template from its declared extensions, else its content. */
    private static EditorFileType typeOf(XmlTemplate template) {
        Set<String> exts = template.getFileExtensions();
        if (exts != null && !exts.isEmpty()) {
            EditorFileType type = EditorFileType.fromFileName("x." + exts.iterator().next());
            if (type != EditorFileType.OTHER) {
                return type;
            }
        }
        return NewFileDialog.inferType(template.getContent());
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static ListCell<EditorFileType> typeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(EditorFileType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }
}
