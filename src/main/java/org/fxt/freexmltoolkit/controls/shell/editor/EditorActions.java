package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * Editor-level document actions surfaced on the Unified shell's top toolbar so the
 * user can validate, transform, generate XSD documentation and open the type editor
 * for the active document <em>without</em> switching the left Activity Bar.
 * <p>
 * Each action reuses the existing UI-free runners (so the logic stays in one place)
 * and opens its result as a standard tool tab via {@link EditorHost#openToolTab}.
 * Anything potentially slow runs off the FX thread on {@link FxtGui#executorService}.
 * The toolbar buttons are type-gated against the active document's
 * {@link EditorFileType} via {@link #applicableFor(EditorFileType, EditorAction)}.
 */
public final class EditorActions {

    /** The four editor-level actions; used by the toolbar to drive enable/disable gating. */
    public enum EditorAction { VALIDATE, TRANSFORM, GENERATE_DOCS, TYPE_EDITOR }

    private final EditorHost editorHost;

    public EditorActions(EditorHost editorHost) {
        this.editorHost = editorHost;
    }

    /**
     * @return {@code true} if {@code action} applies to a document of {@code type}
     *         (the gate used to enable/disable the corresponding toolbar button).
     *         A {@code null} type (no active document) disables every action.
     */
    public static boolean applicableFor(EditorFileType type, EditorAction action) {
        if (type == null) {
            return false;
        }
        return switch (action) {
            case VALIDATE -> switch (type) {
                case XML, XSD, XSLT, SCHEMATRON, JSON -> true;
                default -> false;
            };
            case TRANSFORM -> type == EditorFileType.XML;
            case GENERATE_DOCS, TYPE_EDITOR -> type == EditorFileType.XSD;
        };
    }

    /** @return the active document's file type, or {@code null} when no document is open. */
    public EditorFileType activeFileType() {
        return editorHost.getActiveDocument().map(OpenDocument::getFileType).orElse(null);
    }

    // ---------------------------------------------------------------------------------------------
    // Validate
    // ---------------------------------------------------------------------------------------------

    /**
     * Validates the active document and shows the problems (or a "valid / well-formed"
     * message) in a tool tab — mirroring {@code ValidationPanel.revalidate()}. JSON
     * documents are checked for well-formedness (no JSON schema is bound from the
     * toolbar); every other text type validates against the bound XSD / Schematron.
     * Also publishes the result to {@link EditorHost#setValidationStatus} so the status
     * bar / inspector badge stay consistent.
     */
    public void validateActive() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        if (content.isBlank()) {
            editorHost.setValidationStatus(EditorHost.ValidationState.NOT_VALIDATED, 0, "Not validated");
            editorHost.openToolTab("Validation", "bi-check2-circle",
                    messageRegion("Empty document — nothing to validate."));
            return;
        }
        boolean json = editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
        File xsd = editorHost.activeSchemaProperty().get();
        File schematron = editorHost.getActiveSchematron();
        FxtGui.executorService.submit(() -> {
            // v1: a JSON Schema bound via the Validation panel is not surfaced here — toolbar
            // JSON validation is well-formedness only (the schema lives in ValidationPanel state).
            List<ValidationProblem> result = json
                    ? ValidationRunner.validateJson(content, null)
                    : ValidationRunner.run(content, xsd, schematron);
            boolean hasSchema = !json && (xsd != null || schematron != null);
            Platform.runLater(() -> {
                String summary = result.isEmpty()
                        ? (hasSchema ? "Valid" : "Well-formed")
                        : result.size() + " problem(s)";
                editorHost.setValidationStatus(
                        result.isEmpty() ? EditorHost.ValidationState.VALID : EditorHost.ValidationState.INVALID,
                        result.size(), summary);
                editorHost.openToolTab("Validation", "bi-check2-circle",
                        validationResultRegion(result, summary));
            });
        });
    }

    private static Region validationResultRegion(List<ValidationProblem> problems, String summary) {
        if (problems.isEmpty()) {
            return messageRegion(summary + " — no problems found.");
        }
        StringBuilder sb = new StringBuilder(summary).append('\n').append('\n');
        for (ValidationProblem p : problems) {
            String line = p.line() > 0 ? "Ln " + p.line() + ": " : "";
            sb.append('[').append(p.source()).append("] ").append(line).append(p.message()).append('\n');
        }
        return textRegion(sb.toString());
    }

    // ---------------------------------------------------------------------------------------------
    // Transform with XSLT
    // ---------------------------------------------------------------------------------------------

    /**
     * Prompts for an XSLT stylesheet, transforms the active XML with it (no parameters,
     * XML output — mirroring {@code TransformPanel.transform()}) off the FX thread, and
     * shows the output (or the error) in a tool tab.
     *
     * @param window the owner window for the file chooser (may be {@code null})
     */
    public void transformActiveWithXslt(Window window) {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Transform with XSLT…");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSLT", "*.xsl", "*.xslt"));
        File xslt = chooser.showOpenDialog(window);
        if (xslt == null) {
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        Map<String, Object> params = Map.of();
        XsltTransformationEngine.OutputFormat format = XsltTransformationEngine.OutputFormat.XML;
        FxtGui.executorService.submit(() -> {
            String output;
            try {
                String xsltContent = Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                XsltTransformationResult result =
                        TransformRunner.transformForReport(xml, xsltContent, params, format);
                output = result.isSuccess()
                        ? result.getOutputContent()
                        : "ERROR: " + result.getErrorMessage();
            } catch (Exception e) {
                output = "ERROR: " + e.getMessage();
            }
            String finalOutput = output;
            Platform.runLater(() -> editorHost.openToolTab(
                    "Transform: " + xslt.getName(), "bi-arrow-left-right", textRegion(finalOutput)));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Generate Documentation
    // ---------------------------------------------------------------------------------------------

    /**
     * Generates XSD documentation for the active schema — mirrors
     * {@code TypeLibraryPanel.generateDocumentationForActive()}: a format choice
     * (HTML / PDF / Word) and an output location, then runs the matching
     * {@link DocumentationRunner} export off the FX thread and reports the result.
     *
     * @param window the owner window for the choosers (may be {@code null})
     */
    public void generateDocsActive(Window window) {
        File xsd = activeXsdFileOrAlert("Generate Documentation");
        if (xsd == null) {
            return;
        }
        ChoiceDialog<String> formatDialog = new ChoiceDialog<>("HTML", "HTML", "PDF", "Word");
        formatDialog.initOwner(window);
        formatDialog.setTitle("Generate Documentation");
        formatDialog.setHeaderText("Choose the documentation format");
        formatDialog.setContentText("Format:");
        var format = formatDialog.showAndWait();
        if (format.isEmpty()) {
            return;
        }
        String baseName = xsd.getName().replaceFirst("\\.[^.]+$", "");
        File target;
        java.util.function.Function<File, String> export;
        switch (format.get()) {
            case "PDF" -> {
                target = chooseSaveFile(window, baseName + ".pdf", "PDF", "*.pdf");
                export = out -> DocumentationRunner.exportPdf(xsd, out);
            }
            case "Word" -> {
                target = chooseSaveFile(window, baseName + ".docx", "Word", "*.docx");
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
        FxtGui.executorService.submit(() -> {
            String result = export.apply(output);
            Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(Alert.AlertType.ERROR, "Generate Documentation", result);
                } else {
                    alert(Alert.AlertType.INFORMATION, "Generate Documentation",
                            "Documentation generated:\n" + result.substring(3).trim());
                }
            });
        });
    }

    /**
     * Resolves the active document to an XSD file on disk (its saved path, or a temp
     * copy of the current text for an unsaved schema). Shows an alert and returns
     * {@code null} when the active document is not an XSD. Mirrors
     * {@code TypeLibraryPanel.activeXsdFileOrAlert}.
     * <p>
     * Note: a document that HAS a path but unsaved edits is documented from its
     * on-disk (last-saved) version, not the current buffer — this keeps relative
     * {@code xs:include}/{@code xs:import} resolution anchored at the real file
     * location. Save before generating to document the latest edits.
     */
    private File activeXsdFileOrAlert(String title) {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty() || docOpt.get().getFileType() != EditorFileType.XSD) {
            alert(Alert.AlertType.WARNING, title, "Open an XSD schema first.");
            return null;
        }
        try {
            java.nio.file.Path path = docOpt.get().getPath();
            if (path != null) {
                return path.toFile();
            }
            File tmp = File.createTempFile("fxt-schema-", ".xsd");
            tmp.deleteOnExit();
            Files.writeString(tmp.toPath(), editorHost.getActiveText().orElse(""));
            return tmp;
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, title, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Open Type Editor
    // ---------------------------------------------------------------------------------------------

    /**
     * Lets the user pick one of the active XSD's named types and opens it in a focused
     * type-editor tab via {@link EditorHost#openTypeEditorTab(String)}. Shows an info
     * message when the schema declares no named types.
     */
    public void openTypeEditorActive() {
        var types = editorHost.getActiveNamedTypes();
        List<String> names = types.stream()
                .map(org.fxt.freexmltoolkit.controls.v2.model.XsdNode::getName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();
        if (names.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Open Type Editor",
                    "The active schema declares no named types.");
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Open Type Editor");
        dialog.setHeaderText("Choose a named type to edit");
        dialog.setContentText("Type:");
        dialog.showAndWait().ifPresent(editorHost::openTypeEditorTab);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private File chooseSaveFile(Window window, String initialName, String label, String glob) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save documentation as " + label);
        fc.setInitialFileName(initialName);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, glob));
        return fc.showSaveDialog(window);
    }

    private static Region messageRegion(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("fxt-placeholder-text");
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.getStyleClass().add("fxt-tool-result");
        return box;
    }

    private static Region textRegion(String text) {
        TextArea area = new TextArea(text == null ? "" : text);
        area.setEditable(false);
        area.getStyleClass().add("fxt-transform-output");
        VBox box = new VBox(area);
        box.getStyleClass().add("fxt-tool-result");
        VBox.setVgrow(area, Priority.ALWAYS);
        return box;
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
