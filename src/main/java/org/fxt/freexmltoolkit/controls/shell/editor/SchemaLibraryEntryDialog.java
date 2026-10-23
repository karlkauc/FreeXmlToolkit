package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.domain.EntrySource;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.util.DialogHelper;

/** Add / edit one Schema Library mapping. Returns the entry (with the original id when editing). */
public class SchemaLibraryEntryDialog extends Dialog<SchemaLibraryEntry> {

    @FXML private TextField namespace;
    @FXML private TextField locationField;
    @FXML private ComboBox<SchemaKind> kind;
    @FXML private TextField description;
    @FXML private TextField rootElement;
    @FXML private Label error;
    private final SchemaLibraryService library;

    /** Add ({@code existing == null}) or edit ({@code existing != null}, id and source are preserved). */
    public SchemaLibraryEntryDialog(SchemaLibraryService library, SchemaLibraryEntry existing) {
        this(library, existing, false);
    }

    /**
     * @param existing when {@code asNew} is {@code true}, prefills the fields from {@code existing}
     *                 (e.g. a prefill computed via {@link SchemaLibraryService#entryFromFile(Path)})
     *                 but the dialog builds a brand-new USER entry with a fresh id; when {@code asNew}
     *                 is {@code false}, {@code existing} is the entry being edited in place (or
     *                 {@code null} to add a fresh entry from scratch).
     * @param asNew    {@code true} to prefill-only (add), {@code false} to edit {@code existing} in place
     */
    public SchemaLibraryEntryDialog(SchemaLibraryService library, SchemaLibraryEntry existing, boolean asNew) {
        // "asNew" prefills the fields from `existing` but the OK result is a brand-new USER entry.
        this.library = library;
        SchemaLibraryEntry editing = asNew ? null : existing;
        SchemaLibraryEntry prefill = existing;

        setTitle(editing == null ? "Add Schema Mapping" : "Edit Schema Mapping");
        setHeaderText(editing == null
                ? "Map a namespace (or JSON $schema URI) to a local schema file or URL."
                : "Change the mapping for " + (editing.namespace().isEmpty() ? editing.rootElement() : editing.namespace()));
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SchemaLibraryEntryDialog.fxml"));
        kind.getItems().addAll(SchemaKind.values());
        kind.setValue(SchemaKind.XSD);
        getDialogPane().setPrefWidth(560);

        if (prefill != null) {
            namespace.setText(prefill.namespace());
            locationField.setText(prefill.location());
            kind.setValue(prefill.kind());
            description.setText(prefill.description());
            rootElement.setText(prefill.rootElement() == null ? "" : prefill.rootElement());
        }

        // Validate on OK without closing when invalid.
        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                SchemaLibraryServiceImpl.validate(build(editing));
                error.setText("");
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            }
        });
        setResultConverter(bt -> bt == ButtonType.OK ? build(editing) : null);
    }

    /** Picks a schema file and prefills namespace and kind from it. */
    @FXML
    public void onBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select schema file");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Schemas", "*.xsd", "*.json", "*.dtd"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File f = fc.showOpenDialog(getOwner());
        if (f != null) {
            locationField.setText(f.getAbsolutePath());
            library.entryFromFile(Path.of(f.getAbsolutePath())).ifPresent(pre -> {
                if (namespace.getText().isBlank()) namespace.setText(pre.namespace());
                kind.setValue(pre.kind());
            });
        }
    }

    private SchemaLibraryEntry build(SchemaLibraryEntry editing) {
        String id = editing != null ? editing.id() : UUID.randomUUID().toString();
        return new SchemaLibraryEntry(id, namespace.getText(), locationField.getText().trim(), kind.getValue(),
                editing != null ? editing.source() : EntrySource.USER,
                editing == null || editing.enabled(), description.getText(), rootElement.getText());
    }
}
