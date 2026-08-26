package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.EntrySource;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

/** Add / edit one Schema Library mapping. Returns the entry (with the original id when editing). */
public class SchemaLibraryEntryDialog extends Dialog<SchemaLibraryEntry> {

    private final TextField namespace = new TextField();
    private final TextField location = new TextField();
    private final ComboBox<SchemaKind> kind = new ComboBox<>();
    private final TextField description = new TextField();
    private final TextField rootElement = new TextField();
    private final Label error = new Label();

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
        SchemaLibraryEntry editing = asNew ? null : existing;
        SchemaLibraryEntry prefill = existing;

        setTitle(editing == null ? "Add Schema Mapping" : "Edit Schema Mapping");
        setHeaderText(editing == null
                ? "Map a namespace (or JSON $schema URI) to a local schema file or URL."
                : "Change the mapping for " + (editing.namespace().isEmpty() ? editing.rootElement() : editing.namespace()));
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        kind.getItems().addAll(SchemaKind.values());
        kind.setValue(SchemaKind.XSD);
        namespace.setPromptText("http://www.example.org/ns  (empty for no-namespace XSD)");
        location.setPromptText("/path/to/schema.xsd or https://…/schema.xsd");
        rootElement.setPromptText("document element local name (optional, no-namespace XSD only)");
        error.getStyleClass().add("fxt-lib-error");
        error.setWrapText(true);

        Button browse = new Button("Browse…", new IconifyIcon("bi-folder2-open"));
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select schema file");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Schemas", "*.xsd", "*.json", "*.dtd"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File f = fc.showOpenDialog(getOwner());
            if (f != null) {
                location.setText(f.getAbsolutePath());
                library.entryFromFile(Path.of(f.getAbsolutePath())).ifPresent(pre -> {
                    if (namespace.getText().isBlank()) namespace.setText(pre.namespace());
                    kind.setValue(pre.kind());
                });
            }
        });
        HBox locationRow = new HBox(6, location, browse);
        HBox.setHgrow(location, Priority.ALWAYS);

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Namespace / $schema:"), namespace);
        grid.addRow(1, new Label("Location:"), locationRow);
        grid.addRow(2, new Label("Kind:"), kind);
        grid.addRow(3, new Label("Root element:"), rootElement);
        grid.addRow(4, new Label("Description:"), description);
        grid.add(error, 0, 5, 2, 1);
        GridPane.setHgrow(namespace, Priority.ALWAYS);
        GridPane.setHgrow(locationRow, Priority.ALWAYS);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(560);

        if (prefill != null) {
            namespace.setText(prefill.namespace());
            location.setText(prefill.location());
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

    private SchemaLibraryEntry build(SchemaLibraryEntry editing) {
        String id = editing != null ? editing.id() : UUID.randomUUID().toString();
        return new SchemaLibraryEntry(id, namespace.getText(), location.getText().trim(), kind.getValue(),
                editing != null ? editing.source() : EntrySource.USER,
                editing == null || editing.enabled(), description.getText(), rootElement.getText());
    }
}
