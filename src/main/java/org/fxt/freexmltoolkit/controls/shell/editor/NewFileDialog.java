package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

/**
 * Guided "New File" dialog: pick a file type, optionally a (type-filtered) template,
 * and for plain XML optionally a schema (from favorites / recents / a file picker)
 * plus whether to pre-fill mandatory nodes, and an optional save location.
 *
 * <p>Gathers settings only — content generation, document creation, schema binding
 * and saving stay in {@code UnifiedShellView}, reusing {@link SampleXmlRunner},
 * {@link TemplateRunner} and {@code EditorHost}.</p>
 */
public class NewFileDialog extends Dialog<NewFileDialog.Result> {

    /**
     * The outcome of the dialog.
     *
     * @param type              the chosen file type (never {@code null})
     * @param template          the chosen template, or {@code null} for none
     * @param schema            the chosen schema file, or {@code null} for none (XML only)
     * @param generateMandatory whether to pre-fill mandatory nodes (only relevant with a schema)
     * @param saveLocation      the chosen save location, or {@code null} to open untitled
     */
    public record Result(EditorFileType type, XmlTemplate template, File schema,
                         boolean generateMandatory, File saveLocation) {
    }

    /** File types offered for creation (everything except the catch-all {@link EditorFileType#OTHER}). */
    private static final EditorFileType[] CREATABLE = {
            EditorFileType.XML, EditorFileType.XSD, EditorFileType.XSLT,
            EditorFileType.SCHEMATRON, EditorFileType.JSON
    };

    private final ComboBox<EditorFileType> typeBox = new ComboBox<>();
    private final ComboBox<XmlTemplate> templateBox = new ComboBox<>();
    private final ComboBox<File> schemaSourceBox = new ComboBox<>();
    private final Button schemaBrowse = new Button("Browse…");
    private final CheckBox generateMandatory = new CheckBox("Pre-fill mandatory nodes (empty)");
    private final TextField saveLocation = new TextField();
    private final Button saveBrowse = new Button("Browse…");

    private final ObjectProperty<File> schemaFile = new SimpleObjectProperty<>(null);
    private final List<XmlTemplate> allTemplates;

    public NewFileDialog() {
        this(TemplateRunner.list());
    }

    NewFileDialog(List<XmlTemplate> templates) {
        this.allTemplates = templates == null ? List.of() : templates;
        setTitle("New File");
        setHeaderText("Choose what to create");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // --- File type ---
        typeBox.getItems().setAll(CREATABLE);
        typeBox.getSelectionModel().select(EditorFileType.XML);
        typeBox.setButtonCell(typeCell());
        typeBox.setCellFactory(lv -> typeCell());
        typeBox.setMaxWidth(Double.MAX_VALUE);

        // --- Template ---
        templateBox.setButtonCell(templateCell());
        templateBox.setCellFactory(lv -> templateCell());
        templateBox.setMaxWidth(Double.MAX_VALUE);

        // --- Schema source (favorites + recents) ---
        schemaSourceBox.setPromptText("From favorites / recent…");
        schemaSourceBox.setButtonCell(fileCell());
        schemaSourceBox.setCellFactory(lv -> fileCell());
        schemaSourceBox.setMaxWidth(Double.MAX_VALUE);
        schemaSourceBox.getItems().setAll(collectSchemaSources());
        schemaSourceBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                schemaFile.set(n);
            }
        });
        schemaBrowse.setOnAction(e -> browseSchema());
        generateMandatory.setSelected(true);

        // --- Save location ---
        saveLocation.setPromptText("(optional) leave empty to open as untitled");
        HBox.setHgrow(saveLocation, Priority.ALWAYS);
        saveBrowse.setOnAction(e -> browseSaveLocation());

        // --- Layout ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        int row = 0;
        grid.add(new Label("File type:"), 0, row);
        grid.add(typeBox, 1, row++, 2, 1);

        grid.add(new Label("Template:"), 0, row);
        grid.add(templateBox, 1, row++, 2, 1);

        Label schemaLabel = new Label("Schema:");
        grid.add(schemaLabel, 0, row);
        grid.add(schemaSourceBox, 1, row);
        grid.add(schemaBrowse, 2, row++);
        GridPane.setHgrow(schemaSourceBox, Priority.ALWAYS);

        TextField schemaPath = new TextField();
        schemaPath.setEditable(false);
        schemaPath.setPromptText("no schema selected");
        schemaFile.addListener((obs, o, n) -> schemaPath.setText(n == null ? "" : n.getAbsolutePath()));
        grid.add(schemaPath, 1, row++, 2, 1);

        grid.add(generateMandatory, 1, row++, 2, 1);

        grid.add(new Label("Save to:"), 0, row);
        grid.add(saveLocation, 1, row);
        grid.add(saveBrowse, 2, row++);

        getDialogPane().setContent(grid);

        // --- Dynamic enable/disable & template filtering ---
        typeBox.valueProperty().addListener((obs, o, n) -> {
            refreshTemplates();
            updateEnablement();
            updateSaveExtension();
        });
        templateBox.valueProperty().addListener((obs, o, n) -> updateEnablement());
        schemaFile.addListener((obs, o, n) -> updateEnablement());

        refreshTemplates();
        updateEnablement();

        setResultConverter(button -> button == ButtonType.OK ? currentResult() : null);
    }

    /** @return the result reflecting the current control state. */
    public Result currentResult() {
        EditorFileType type = typeBox.getValue();
        XmlTemplate template = templateBox.getValue();
        boolean xmlNoTemplate = type == EditorFileType.XML && template == null;
        File schema = xmlNoTemplate ? schemaFile.get() : null;
        boolean mandatory = schema != null && generateMandatory.isSelected();
        String loc = saveLocation.getText();
        File save = (loc == null || loc.isBlank()) ? null : new File(loc.trim());
        return new Result(type, template, schema, mandatory, save);
    }

    // ===================== helpers =====================

    private void refreshTemplates() {
        EditorFileType type = typeBox.getValue();
        List<XmlTemplate> filtered = new ArrayList<>();
        filtered.add(null); // "— None —"
        for (XmlTemplate t : allTemplates) {
            if (matchesType(t, type)) {
                filtered.add(t);
            }
        }
        templateBox.setItems(FXCollections.observableArrayList(filtered));
        templateBox.getSelectionModel().select(0); // None
    }

    private void updateEnablement() {
        EditorFileType type = typeBox.getValue();
        boolean hasTemplate = templateBox.getValue() != null;
        boolean schemaApplicable = type == EditorFileType.XML && !hasTemplate;
        schemaSourceBox.setDisable(!schemaApplicable);
        schemaBrowse.setDisable(!schemaApplicable);
        generateMandatory.setDisable(!schemaApplicable || schemaFile.get() == null);
    }

    private void updateSaveExtension() {
        // Adjust the prompt only; the actual extension is enforced via the file chooser.
        EditorFileType type = typeBox.getValue();
        saveLocation.setPromptText("(optional) ." + type.primaryExtension() + " — empty = untitled");
    }

    private void browseSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSD Schema");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSD Schema", "*.xsd"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getOwnerWindow());
        if (file != null) {
            if (!schemaSourceBox.getItems().contains(file)) {
                schemaSourceBox.getItems().add(file);
            }
            schemaSourceBox.getSelectionModel().select(file);
            schemaFile.set(file);
        }
    }

    private void browseSaveLocation() {
        EditorFileType type = typeBox.getValue();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save New File As");
        String ext = type.primaryExtension();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(type.label() + " files", "*." + ext));
        chooser.setInitialFileName("untitled." + ext);
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getOwnerWindow());
        if (file != null) {
            saveLocation.setText(file.getAbsolutePath());
        }
    }

    private javafx.stage.Window getOwnerWindow() {
        return getDialogPane().getScene() != null ? getDialogPane().getScene().getWindow() : null;
    }

    /** Collects candidate schema files from XSD favorites and recent files (deduplicated). */
    private List<File> collectSchemaSources() {
        Set<File> files = new LinkedHashSet<>();
        try {
            for (FileFavorite fav : FavoritesService.getInstance().getFavoritesByType(FileFavorite.FileType.XSD)) {
                if (fav.getFilePath() != null) {
                    File f = new File(fav.getFilePath());
                    if (f.isFile()) {
                        files.add(f);
                    }
                }
            }
        } catch (Exception ignored) {
            // favorites are best-effort
        }
        try {
            PropertiesService props = PropertiesServiceImpl.getInstance();
            for (File f : props.getLastOpenFiles()) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".xsd")) {
                    files.add(f);
                }
            }
        } catch (Exception ignored) {
            // recents are best-effort
        }
        return new ArrayList<>(files);
    }

    /**
     * Heuristic: does the template fit the given file type? Built-in templates rarely
     * declare {@code fileExtensions}, so we fall back to inspecting the content's root.
     */
    static boolean matchesType(XmlTemplate template, EditorFileType type) {
        if (template == null || type == null) {
            return false;
        }
        Set<String> exts = template.getFileExtensions();
        if (exts != null && !exts.isEmpty()) {
            for (String e : exts) {
                if (type.extensions().contains(e.toLowerCase(Locale.ROOT).replace(".", ""))) {
                    return true;
                }
            }
            return false;
        }
        return inferType(template.getContent()) == type;
    }

    /** Infers the file type of raw content by looking at its root construct. */
    static EditorFileType inferType(String content) {
        if (content == null) {
            return EditorFileType.XML;
        }
        String c = content.trim();
        String lower = c.toLowerCase(Locale.ROOT);
        if (c.startsWith("{") || c.startsWith("[")) {
            return EditorFileType.JSON;
        }
        if (lower.contains("<xs:schema") || lower.contains("<xsd:schema")) {
            return EditorFileType.XSD;
        }
        if (lower.contains("<xsl:stylesheet") || lower.contains("<xsl:transform")) {
            return EditorFileType.XSLT;
        }
        if (lower.contains("http://purl.oclc.org/dsdl/schematron")
                || lower.contains("<sch:schema") || lower.contains("<iso:schema")) {
            return EditorFileType.SCHEMATRON;
        }
        return EditorFileType.XML;
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

    private static ListCell<XmlTemplate> templateCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(XmlTemplate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else if (item == null) {
                    setText("— None —");
                } else {
                    String cat = item.getCategory();
                    setText(item.getName() + (cat == null || cat.isBlank() ? "" : "  (" + cat + ")"));
                }
            }
        };
    }

    private static ListCell<File> fileCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                if (item != null) {
                    setTooltip(new javafx.scene.control.Tooltip(item.getAbsolutePath()));
                }
            }
        };
    }
}
