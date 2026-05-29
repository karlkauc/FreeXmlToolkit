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
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.List;

/**
 * The Validation activity side panel: validates the active XML document against
 * its bound XSD (see {@link EditorHost#activeSchemaProperty()}) and lists the
 * problems; selecting a problem jumps to its line. Reuses {@link XmlService}.
 * <p>
 * Schematron and batch validation are follow-up increments.
 */
public class ValidationPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<SAXParseException> problems = FXCollections.observableArrayList();
    private final Label status = new Label("Not validated");

    public ValidationPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("VALIDATION");
        title.getStyleClass().add("fxt-side-panel-title");

        Button validate = new Button("Validate", icon("bi-check2-circle"));
        validate.getStyleClass().add("fxt-tool-button");
        validate.setOnAction(e -> revalidate());

        status.getStyleClass().add("fxt-placeholder-text");

        Label problemsLabel = new Label("PROBLEMS");
        problemsLabel.getStyleClass().add("fxt-side-panel-title");

        ListView<SAXParseException> list = new ListView<>(problems);
        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("No problems"));
        list.setCellFactory(lv -> new ProblemCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getLineNumber() > 0) {
                editorHost.goToLine(newV.getLineNumber());
            }
        });

        getChildren().addAll(title, new HBox(validate), status, problemsLabel, list);
    }

    /** Runs validation of the active document against its bound schema (async). */
    public void revalidate() {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            status.setText("No document open");
            problems.clear();
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        File xsd = editorHost.activeSchemaProperty().get();
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            List<SAXParseException> result;
            try {
                result = ServiceRegistry.get(XmlService.class).validateText(xml, xsd);
            } catch (Throwable t) {
                result = List.of();
            }
            List<SAXParseException> errors = result;
            Platform.runLater(() -> {
                problems.setAll(errors);
                status.setText(errors.isEmpty()
                        ? (xsd != null ? "Valid against " + xsd.getName() : "Well-formed")
                        : errors.size() + " problem(s)");
            });
        });
    }

    /** @return the number of problems currently shown (for tests/observers). */
    public int getProblemCount() {
        return problems.size();
    }

    private IconifyIcon icon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return icon;
    }

    /** Renders a problem as "Ln N: message". */
    private static final class ProblemCell extends ListCell<SAXParseException> {
        @Override
        protected void updateItem(SAXParseException item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText("Ln " + item.getLineNumber() + ": " + item.getMessage());
            IconifyIcon icon = new IconifyIcon("bi-exclamation-triangle");
            icon.setIconSize(13);
            setGraphic(icon);
        }
    }
}
