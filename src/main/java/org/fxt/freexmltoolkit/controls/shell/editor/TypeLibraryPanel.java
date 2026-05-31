package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * The Schema activity side panel: lists the active XSD's top-level named types
 * (Type Library). Selecting a type reveals it in the Tree view. Bound to the
 * {@link EditorHost}; refreshes on tab / view-mode changes.
 */
public class TypeLibraryPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<XsdNode> types = FXCollections.observableArrayList();
    private String selectedTypeName;

    public TypeLibraryPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("SCHEMA");
        title.getStyleClass().add("fxt-side-panel-title");

        ListView<XsdNode> list = new ListView<>(types);
        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("No named types"));
        list.setCellFactory(lv -> new TypeCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedTypeName = newV != null ? newV.getName() : null;
            if (selectedTypeName != null) {
                editorHost.revealTypeByName(selectedTypeName);
            }
        });

        javafx.scene.layout.VBox actions = new javafx.scene.layout.VBox(4,
                actionButton("Generate XSD from XML", "bi-magic", this::generateXsdFromActive),
                actionButton("Flatten Schema", "bi-layers", this::flattenActive),
                actionButton("Statistics", "bi-bar-chart", this::statisticsActive),
                actionButton("Generate Sample XML", "bi-filetype-xml", this::generateSampleXmlForActive),
                actionButton("Generate Documentation", "bi-file-earmark-text", this::generateDocumentationForActive));

        Label typesLabel = new Label("TYPES");
        typesLabel.getStyleClass().add("fxt-side-panel-title");

        getChildren().addAll(title, actions, typesLabel, list,
                actionButton("Find Usage", "bi-search", this::findUsageOfSelectedType));

        refresh();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refresh());
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> refresh());
    }

    /**
     * Finds where the selected named type is used and opens a report (async).
     * Reuses {@link TypeUsageRunner} (which reuses {@code TypeUsageFinder}).
     */
    public void findUsageOfSelectedType() {
        String typeName = selectedTypeName;
        if (typeName == null || typeName.isBlank() || editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String xsd = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            java.util.List<String> usages = TypeUsageRunner.findUsages(xsd, typeName);
            StringBuilder report = new StringBuilder("Usages of type '").append(typeName).append("'\n");
            report.append("=".repeat(report.length() - 1)).append('\n');
            if (usages.isEmpty()) {
                report.append("\nNo usages found.\n");
            } else {
                report.append('\n').append(usages.size()).append(" usage(s):\n\n");
                usages.forEach(u -> report.append("  • ").append(u).append('\n'));
            }
            javafx.application.Platform.runLater(() ->
                    editorHost.openGeneratedDocument(report.toString(), EditorFileType.OTHER,
                            "Usages-" + typeName + ".txt"));
        });
    }

    /** Infers an XSD from the active XML document and opens it as a new tab (async). */
    public void generateXsdFromActive() {
        runAsync(SchemaActionRunner::generateXsdFromXml, EditorFileType.XSD, "Generated.xsd");
    }

    /** Flattens the active XSD (resolves includes) and opens the result (async). */
    public void flattenActive() {
        var doc = editorHost.getActiveDocument();
        java.nio.file.Path baseDir = doc.map(OpenDocument::getPath)
                .map(java.nio.file.Path::getParent).orElse(null);
        runAsync(content -> SchemaActionRunner.flatten(content, baseDir),
                EditorFileType.XSD, "Flattened.xsd");
    }

    /** Collects statistics for the active XSD and opens a text report (async). */
    public void statisticsActive() {
        runAsync(SchemaActionRunner::statistics, EditorFileType.OTHER, "Statistics.txt");
    }

    /**
     * Exports XSD documentation for the active schema: asks for a format
     * (HTML / PDF / Word) and an output location, then generates it off the UI
     * thread via {@link DocumentationRunner} and reports the result.
     */
    public void generateDocumentationForActive() {
        File xsd = activeXsdFileOrAlert("Generate Documentation");
        if (xsd == null) {
            return;
        }

        javafx.scene.control.ChoiceDialog<String> formatDialog =
                new javafx.scene.control.ChoiceDialog<>("HTML", "HTML", "PDF", "Word");
        formatDialog.setTitle("Generate Documentation");
        formatDialog.setHeaderText("Choose the documentation format");
        formatDialog.setContentText("Format:");
        var format = formatDialog.showAndWait();
        if (format.isEmpty()) {
            return;
        }

        var window = getScene() != null ? getScene().getWindow() : null;
        String baseName = xsd.getName().replaceFirst("\\.[^.]+$", "");
        File target;
        java.util.function.Function<File, String> export;
        switch (format.get()) {
            case "PDF" -> {
                target = chooseFile(window, baseName + ".pdf", "PDF", "*.pdf");
                export = out -> DocumentationRunner.exportPdf(xsd, out);
            }
            case "Word" -> {
                target = chooseFile(window, baseName + ".docx", "Word", "*.docx");
                export = out -> DocumentationRunner.exportWord(xsd, out);
            }
            default -> {
                javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
                dc.setTitle("Choose output directory for HTML documentation");
                target = dc.showDialog(window);
                export = out -> DocumentationRunner.exportHtml(xsd, out);
            }
        }
        if (target == null) {
            return;
        }

        final File output = target;
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String result = export.apply(output);
            javafx.application.Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(javafx.scene.control.Alert.AlertType.ERROR, "Generate Documentation", result);
                } else {
                    alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                            "Generate Documentation", "Documentation generated:\n" + result.substring(3).trim());
                    openInDesktop(output.isDirectory() ? new File(output, "index.html") : output);
                }
            });
        });
    }

    /**
     * Generates a sample XML instance from the active schema (off the UI thread)
     * and opens it as a new editor tab.
     */
    public void generateSampleXmlForActive() {
        File xsd = activeXsdFileOrAlert("Generate Sample XML");
        if (xsd == null) {
            return;
        }
        var options = new SampleXmlOptionsDialog().showAndWait();
        if (options.isEmpty()) {
            return;
        }
        boolean mandatoryOnly = options.get().mandatoryOnly();
        int maxOccurrences = options.get().maxOccurrences();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String result = SampleXmlRunner.generate(xsd, mandatoryOnly, maxOccurrences);
            javafx.application.Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(javafx.scene.control.Alert.AlertType.ERROR, "Generate Sample XML", result);
                } else {
                    editorHost.openGeneratedDocument(result, EditorFileType.XML, "Sample.xml");
                }
            });
        });
    }

    /**
     * Resolves the active document to an XSD file on disk (its saved path, or a
     * temp copy of the current text for unsaved schemas). Shows an alert and
     * returns {@code null} when the active document is not an XSD.
     */
    private File activeXsdFileOrAlert(String title) {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty() || docOpt.get().getFileType() != EditorFileType.XSD) {
            alert(javafx.scene.control.Alert.AlertType.WARNING, title, "Open an XSD schema first.");
            return null;
        }
        try {
            java.nio.file.Path path = docOpt.get().getPath();
            if (path != null) {
                return path.toFile();
            }
            File tmp = File.createTempFile("fxt-schema-", ".xsd");
            tmp.deleteOnExit();
            java.nio.file.Files.writeString(tmp.toPath(), editorHost.getActiveText().orElse(""));
            return tmp;
        } catch (Exception e) {
            alert(javafx.scene.control.Alert.AlertType.ERROR, title, e.getMessage());
            return null;
        }
    }

    private File chooseFile(javafx.stage.Window window, String initialName, String label, String glob) {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save documentation as " + label);
        fc.setInitialFileName(initialName);
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(label, glob));
        return fc.showSaveDialog(window);
    }

    private static void openInDesktop(File file) {
        try {
            if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
            }
        } catch (Throwable ignored) {
            // best-effort preview; never fail the action because the OS can't open it
        }
    }

    private void alert(javafx.scene.control.Alert.AlertType type, String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void runAsync(java.util.function.Function<String, String> action,
                          EditorFileType outputType, String outputName) {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String result = action.apply(content);
            javafx.application.Platform.runLater(() -> {
                if (!result.startsWith("ERROR:")) {
                    editorHost.openGeneratedDocument(result, outputType, outputName);
                }
            });
        });
    }

    private javafx.scene.control.Button actionButton(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        javafx.scene.control.Button button = new javafx.scene.control.Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void refresh() {
        types.setAll(editorHost.getActiveNamedTypes());
    }

    /** Renders a named type with its node-type icon. */
    private static final class TypeCell extends ListCell<XsdNode> {
        @Override
        protected void updateItem(XsdNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.getName());
            IconifyIcon icon = new IconifyIcon(XsdNodeLabels.icon(item.getNodeType()));
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
