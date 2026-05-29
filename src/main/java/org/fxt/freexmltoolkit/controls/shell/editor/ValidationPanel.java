package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

import java.io.File;
import java.util.List;

/**
 * The Validation activity side panel: validates the active XML against its bound
 * XSD (see {@link EditorHost#activeSchemaProperty()}) and an optional Schematron
 * file, listing the problems; selecting one jumps to its line. Validation runs
 * off the UI thread via {@link ValidationRunner}.
 */
public class ValidationPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> problems = FXCollections.observableArrayList();
    private final Label status = new Label("Not validated");
    private final Label schematronStatus = new Label("Schematron: none");

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

        getChildren().addAll(title, new HBox(6, validate, setSchematron), status, schematronStatus,
                problemsLabel, list);
    }

    /** Runs validation of the active document (XSD + optional Schematron), async. */
    public void revalidate() {
        if (editorHost.getActiveDocument().isEmpty()) {
            status.setText("No document open");
            problems.clear();
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        File xsd = editorHost.activeSchemaProperty().get();
        File schematron = editorHost.getActiveSchematron();
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            List<ValidationProblem> result = ValidationRunner.run(xml, xsd, schematron);
            Platform.runLater(() -> {
                problems.setAll(result);
                status.setText(result.isEmpty()
                        ? (xsd != null || schematron != null ? "Valid" : "Well-formed")
                        : result.size() + " problem(s)");
            });
        });
    }

    /** @return the number of problems currently shown (for tests/observers). */
    public int getProblemCount() {
        return problems.size();
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
