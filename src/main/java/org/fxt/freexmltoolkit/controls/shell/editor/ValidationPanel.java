package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The Validation activity side panel: validates the active XML against its bound
 * XSD (see {@link EditorHost#activeSchemaProperty()}) and an optional Schematron
 * file, listing the problems; selecting one jumps to its line. Validation runs
 * off the UI thread via {@link ValidationRunner}. Supports continuous (debounced)
 * validation while typing, toggleable via the "Validate while typing" checkbox.
 */
public class ValidationPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> problems = FXCollections.observableArrayList();
    private final Label status = new Label("Not validated");
    private final Label schematronStatus = new Label("Schematron: none");
    private final CheckBox liveValidation = new CheckBox("Validate while typing");
    private final PauseTransition debounce = new PauseTransition(Duration.millis(600));
    private File jsonSchemaFile;

    public ValidationPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("VALIDATION");
        title.getStyleClass().add("fxt-side-panel-title");

        Button validate = new Button("Validate", icon("bi-check2-circle"));
        validate.getStyleClass().add("fxt-tool-button");
        validate.setOnAction(e -> revalidate());

        Button setSchematron = new Button("Schematron…", icon("bi-shield-check"));
        setSchematron.getStyleClass().add("fxt-tool-button");
        setSchematron.setOnAction(e -> chooseSchematron());

        Button setJsonSchema = new Button("JSON Schema…", icon("bi-braces-asterisk"));
        setJsonSchema.getStyleClass().add("fxt-tool-button");
        setJsonSchema.setOnAction(e -> chooseJsonSchema());

        Button batch = new Button("Batch…", icon("bi-files"));
        batch.getStyleClass().add("fxt-tool-button");
        batch.setOnAction(e -> chooseBatch());

        status.getStyleClass().add("fxt-placeholder-text");
        schematronStatus.getStyleClass().add("fxt-placeholder-text");
        refreshSchematronStatus();

        Label problemsLabel = new Label("PROBLEMS");
        problemsLabel.getStyleClass().add("fxt-side-panel-title");

        ListView<ValidationProblem> list = new ListView<>(problems);
        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("No problems"));
        list.setCellFactory(lv -> new ProblemCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.line() > 0) {
                editorHost.goToLine(newV.line());
            }
        });

        // Continuous (debounced) validation: re-validate shortly after the active
        // document changes (typing / tab switch / schema binding), when enabled.
        liveValidation.setSelected(true);
        liveValidation.setOnAction(e -> scheduleRevalidation());
        debounce.setOnFinished(e -> {
            if (liveValidation.isSelected()) {
                revalidate();
            }
        });
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> scheduleRevalidation());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> scheduleRevalidation());
        editorHost.activeSchemaProperty().addListener((obs, oldV, newV) -> scheduleRevalidation());

        Label toolsLabel = new Label("SCHEMATRON TOOLS");
        toolsLabel.getStyleClass().add("fxt-side-panel-title");
        Button templates = new Button("Rule Templates", icon("bi-collection"));
        templates.getStyleClass().add("fxt-tool-button");
        templates.setOnAction(e -> openSchematronTemplates());
        Button tester = new Button("Tester", icon("bi-play-circle"));
        tester.getStyleClass().add("fxt-tool-button");
        tester.setOnAction(e -> openSchematronTester());
        Button builder = new Button("Rule Builder", icon("bi-tools"));
        builder.getStyleClass().add("fxt-tool-button");
        builder.setOnAction(e -> openSchematronBuilder());

        getChildren().addAll(title, new HBox(6, validate, setSchematron), new HBox(6, setJsonSchema, batch),
                liveValidation, status, schematronStatus,
                toolsLabel, new HBox(6, templates, tester), new HBox(6, builder),
                problemsLabel, list);
    }

    /** Opens the Schematron rule-template library as a tool tab; inserts into the active editor. */
    public void openSchematronTemplates() {
        var library = new org.fxt.freexmltoolkit.controls.SchematronTemplateLibrary();
        library.setTemplateInsertCallback(editorHost::insertTextAtCaret);
        editorHost.openToolTab("Schematron Templates", "bi-collection", library);
    }

    /** Opens the Schematron tester as a tool tab, pre-loading the bound Schematron if any. */
    public void openSchematronTester() {
        var tester = new org.fxt.freexmltoolkit.controls.SchematronTester();
        File schematron = editorHost.getActiveSchematron();
        if (schematron != null) {
            tester.setSchematronFile(schematron);
        }
        editorHost.openToolTab("Schematron Tester", "bi-play-circle", tester);
    }

    /** Opens the visual Schematron rule builder as a tool tab. */
    public void openSchematronBuilder() {
        editorHost.openToolTab("Schematron Builder", "bi-tools",
                new org.fxt.freexmltoolkit.controls.SchematronVisualBuilder());
    }

    /** Schedules a debounced re-validation if live validation is on and the active doc is XML-family. */
    private void scheduleRevalidation() {
        if (liveValidation.isSelected() && isXmlFamilyActive()) {
            debounce.playFromStart();
        }
    }

    /** @return {@code true} if the active document is XML-family (not JSON / other). */
    private boolean isXmlFamilyActive() {
        return editorHost.getActiveDocument().map(d -> switch (d.getFileType()) {
            case XML, XSD, XSLT, SCHEMATRON -> true;
            default -> false;
        }).orElse(false);
    }

    /** Runs validation of the active document (XSD + optional Schematron), async. */
    public void revalidate() {
        if (editorHost.getActiveDocument().isEmpty()) {
            status.setText("No document open");
            problems.clear();
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        boolean json = editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
        File xsd = editorHost.activeSchemaProperty().get();
        File schematron = editorHost.getActiveSchematron();
        File jsonSchema = this.jsonSchemaFile;
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            List<ValidationProblem> result = json
                    ? ValidationRunner.validateJson(content, jsonSchema)
                    : ValidationRunner.run(content, xsd, schematron);
            Platform.runLater(() -> {
                problems.setAll(result);
                boolean hasSchema = json ? jsonSchema != null : (xsd != null || schematron != null);
                status.setText(result.isEmpty()
                        ? (hasSchema ? "Valid" : "Well-formed")
                        : result.size() + " problem(s)");
            });
        });
    }

    /** @return the number of problems currently shown (for tests/observers). */
    public int getProblemCount() {
        return problems.size();
    }

    /** Validates the given files against the bound XSD/Schematron and opens a report (async). */
    public void runBatch(java.util.List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        File xsd = editorHost.activeSchemaProperty().get();
        File schematron = editorHost.getActiveSchematron();
        status.setText("Validating " + files.size() + " file(s)…");
        FxtGui.executorService.submit(() -> {
            String report = ValidationRunner.batchReport(files, xsd, schematron);
            javafx.application.Platform.runLater(() -> {
                editorHost.openGeneratedDocument(report, EditorFileType.OTHER, "BatchReport.txt");
                status.setText("Batch report generated");
            });
        });
    }

    private void chooseBatch() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select XML files to validate");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XML", "*.xml"));
        java.util.List<File> files = chooser.showOpenMultipleDialog(
                getScene() != null ? getScene().getWindow() : null);
        runBatch(files);
    }

    private void chooseSchematron() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Schematron");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Schematron", "*.sch", "*.schematron"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.setActiveSchematron(file);
            refreshSchematronStatus();
        }
    }

    private void chooseJsonSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select JSON Schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Schema", "*.json"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            jsonSchemaFile = file;
            schematronStatus.setText("JSON Schema: " + file.getName());
            revalidate();
        }
    }

    /** Sets the JSON Schema used when validating a JSON document (for tests/observers). */
    public void setJsonSchema(File schema) {
        this.jsonSchemaFile = schema;
    }

    private void refreshSchematronStatus() {
        File schematron = editorHost.getActiveSchematron();
        schematronStatus.setText(schematron != null ? "Schematron: " + schematron.getName() : "Schematron: none");
    }

    private IconifyIcon icon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return icon;
    }

    /** Renders a problem as "[source] Ln N: message" with a severity icon. */
    private static final class ProblemCell extends ListCell<ValidationProblem> {
        @Override
        protected void updateItem(ValidationProblem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String line = item.line() > 0 ? "Ln " + item.line() + ": " : "";
            setText("[" + item.source() + "] " + line + item.message());
            boolean warning = "warning".equalsIgnoreCase(item.severity());
            IconifyIcon icon = new IconifyIcon(warning ? "bi-exclamation-triangle" : "bi-x-circle");
            icon.setIconSize(13);
            setGraphic(icon);
        }
    }
}
