package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Edits one binding of a project: which files (a glob) get which schema (XSD, DTD, RELAX NG or JSON
 * Schema), which Schematron and which transformation scenario. Each of the three is optional, at
 * least one is needed. Used by File → Project → Bind in Project…, the Explorer and Project Settings.
 */
public class BindSchemaDialog extends Dialog<BindSchemaDialog.Request> {

    /** How many files the "matches" line counts at most. */
    private static final int COUNT_LIMIT = 1000;

    /**
     * @param files      the glob relative to the project folder
     * @param schema     the schema, or {@code null}
     * @param schematron the Schematron, or {@code null}
     * @param scenario   the scenario's name, or {@code null}
     */
    public record Request(String files, Path schema, Path schematron, String scenario) {

        /** @return the binding as stored in the project file (paths relative to it) */
        public FxtProject.SchemaBinding toBinding(FxtProject project) {
            return new FxtProject.SchemaBinding(files,
                    schema == null ? null : project.relativize(schema),
                    schematron == null ? null : project.relativize(schematron), scenario);
        }
    }

    @FXML private TextField filesField;
    @FXML private TextField schemaField;
    @FXML private TextField schematronField;
    @FXML private ComboBox<String> scenarioChoice;
    @FXML private Label matches;
    @FXML private Label error;

    private final FxtProject project;

    /**
     * @param files  the pattern to start with, or {@code null}
     * @param schema the schema to start with, or {@code null}
     */
    public BindSchemaDialog(FxtProject project, String files, Path schema) {
        this(project, new FxtProject.SchemaBinding(files, schema == null ? null : project.relativize(schema)));
    }

    /** Edits (a copy of) a binding; the scenarios offered are the project's. */
    public BindSchemaDialog(FxtProject project, FxtProject.SchemaBinding binding) {
        this(project, binding, project.scenarios().stream().map(s -> s.name).toList());
    }

    /**
     * Edits (a copy of) a binding.
     *
     * @param scenarioNames the scenarios offered (Project Settings passes its edited list)
     */
    public BindSchemaDialog(FxtProject project, FxtProject.SchemaBinding binding, List<String> scenarioNames) {
        DialogHelper.prepare(this);
        this.project = project;
        setTitle(I18n.t("BindSchemaDialog.bind-schema-in-project"));
        setHeaderText(I18n.t("BindSchemaDialog.validate-xml-files-of-with", project.name()));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/BindSchemaDialog.fxml"));
        List<String> scenarios = new ArrayList<>();
        scenarios.add("");
        scenarioNames.stream().filter(n -> n != null && !n.isBlank()).forEach(scenarios::add);
        scenarioChoice.getItems().setAll(scenarios);
        filesField.setText(binding.files == null ? "" : binding.files);
        schemaField.setText(binding.schema == null ? "" : binding.schema);
        schematronField.setText(binding.schematron == null ? "" : binding.schematron);
        scenarioChoice.setValue(binding.scenario == null ? "" : binding.scenario);
        filesField.textProperty().addListener((obs, o, n) -> {
            validate();
            countMatches();
        });
        schemaField.textProperty().addListener((obs, o, n) -> validate());
        schematronField.textProperty().addListener((obs, o, n) -> validate());
        scenarioChoice.valueProperty().addListener((obs, o, n) -> validate());
        validate();
        countMatches();
        setResultConverter(button -> button == ButtonType.OK && validate() == null ? request() : null);
    }

    private Request request() {
        String scenario = scenarioChoice.getValue();
        return new Request(filesField.getText().strip(), path(schemaField), path(schematronField),
                scenario == null || scenario.isBlank() ? null : scenario);
    }

    /** @return the problem with the input, or {@code null} */
    String validate() {
        String problem = null;
        Path schema = path(schemaField);
        Path schematron = path(schematronField);
        boolean noScenario = scenarioChoice.getValue() == null || scenarioChoice.getValue().isBlank();
        if (filesField.getText() == null || filesField.getText().isBlank()) {
            problem = "Enter the files (a path or pattern).";
        } else if (!FxtProject.isValidPattern(filesField.getText().strip())) {
            problem = "The pattern is not valid.";
        } else if (!schemaField.getText().isBlank() && (schema == null || !Files.isRegularFile(schema))) {
            problem = "The schema file does not exist.";
        } else if (!schematronField.getText().isBlank() && (schematron == null || !Files.isRegularFile(schematron))) {
            problem = "The Schematron file does not exist.";
        } else if (schemaField.getText().isBlank() && schematronField.getText().isBlank() && noScenario) {
            problem = "Choose a schema, a Schematron or a scenario.";
        }
        boolean show = problem != null && !(schemaField.getText().isBlank() && schematronField.getText().isBlank()
                && noScenario);
        error.setText(problem == null ? "" : problem);
        error.setVisible(show);
        error.setManaged(show);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
        return problem;
    }

    /** Shows how many files of the project the pattern matches (at most {@value #COUNT_LIMIT} are counted). */
    private void countMatches() {
        String files = filesField.getText() == null ? "" : filesField.getText().strip();
        if (files.isEmpty()) {
            matches.setText("");
            return;
        }
        if (!FxtProject.isValidPattern(files)) {
            matches.setText(I18n.t("BindSchemaDialog.invalid-pattern"));
            return;
        }
        long generation = countGeneration.incrementAndGet();
        matches.setText(I18n.t("BindSchemaDialog.counting"));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            int count = project.filesMatching(files, COUNT_LIMIT).size();
            javafx.application.Platform.runLater(() -> {
                if (countGeneration.get() == generation) {
                    matches.setText(count >= COUNT_LIMIT ? I18n.t("BindSchemaDialog.matches-many", COUNT_LIMIT)
                            : I18n.t("BindSchemaDialog.matches", count));
                }
            });
        });
    }

    /** The newest count; older counts still running are not shown. */
    private final java.util.concurrent.atomic.AtomicLong countGeneration = new java.util.concurrent.atomic.AtomicLong();

    /** @return the "matches N files" text (tests) */
    String matchesText() {
        return matches.getText();
    }

    private Path path(TextField field) {
        String text = field.getText() == null ? "" : field.getText().strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            Path path = Path.of(text);
            return path.isAbsolute() ? path : project.resolve(text);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    @FXML
    public void onBrowseSchema() {
        browse(schemaField, I18n.t("BindSchemaDialog.schema"),
                new FileChooser.ExtensionFilter("Schema (XSD, DTD, RELAX NG, JSON Schema)",
                        "*.xsd", "*.dtd", "*.rng", "*.rnc", "*.json"));
    }

    @FXML
    public void onBrowseSchematron() {
        browse(schematronField, "Schematron", new FileChooser.ExtensionFilter("Schematron", "*.sch", "*.schematron"));
    }

    private void browse(TextField field, String title, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialDirectory(project.root().toFile());
        chooser.getExtensionFilters().add(filter);
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            field.setText(project.relativize(file.toPath()));
        }
    }

    // test access
    void fillForTesting(String files, String schema, String schematron, String scenario) {
        filesField.setText(files);
        schemaField.setText(schema);
        schematronField.setText(schematron);
        scenarioChoice.setValue(scenario);
    }

    Request requestForTesting() {
        return validate() == null ? request() : null;
    }
}
