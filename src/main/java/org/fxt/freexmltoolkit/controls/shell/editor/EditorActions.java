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

    /** The editor-level actions; used by the toolbar to drive enable/disable gating. */
    public enum EditorAction { VALIDATE, TRANSFORM, GENERATE_DOCS, TYPE_EDITOR, RUN_QUERY, RUN_TRANSFORM, RUN_PIPELINE }

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
                case XML, XSD, XSLT, SCHEMATRON, JSON, XPROC -> true;
                default -> false;
            };
            case TRANSFORM -> type == EditorFileType.XML;
            case GENERATE_DOCS, TYPE_EDITOR -> type == EditorFileType.XSD;
            case RUN_QUERY -> type == EditorFileType.XQUERY || type == EditorFileType.XPATH;
            case RUN_TRANSFORM -> type == EditorFileType.XSLT;
            case RUN_PIPELINE -> type == EditorFileType.XPROC;
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
        // Resolved on the worker thread: reconciles the binding with the schema location
        // declared in the buffer (added/changed/removed references take effect this run).
        var xsdSupplier = editorHost.schemaForValidation(content);
        File schematron = editorHost.getActiveSchematron();
        FxtGui.executorService.submit(() -> {
            File xsd = json ? null : xsdSupplier.get();
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
        File xslt = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window);
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
    // Run XPath/XQuery document
    // ---------------------------------------------------------------------------------------------

    /** Monotonic run id; a late async result is dropped if a newer run has started (FX-thread only). */
    private int queryRunGeneration;

    /**
     * Runs the active XPath/XQuery document against its resolved target — the
     * explicitly chosen open document or file-system file, defaulting to the most
     * recently active XML-family document ({@link EditorHost#resolveQueryTarget}) —
     * off the FX thread and shows the result in the docked OUTPUT panel — mirroring
     * {@code QueryConsole}/{@code TransformPanel}. No-op for non-query documents.
     */
    public void runActiveQuery() {
        EditorFileType type = activeFileType();
        if (type != EditorFileType.XQUERY && type != EditorFileType.XPATH) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String query = editorHost.getActiveText().orElse("");
        if (query.isBlank()) {
            out.showError("The query is empty.");
            return;
        }
        var doc = editorHost.getActiveDocument().orElseThrow();
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isEmpty()) {
            out.showError("Open an XML document first, or pick one via the Target dropdown — "
                    + "by default the query runs against the most recently active XML document.");
            return;
        }
        var target = resolved.get();
        boolean xquery = type == EditorFileType.XQUERY;
        out.showPending("Running query against " + target.displayName() + "…");
        String queryTarget = doc.getDisplayName();
        final int gen = ++queryRunGeneration;
        FxtGui.executorService.submit(() -> {
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    xquery ? org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XQUERY
                            : org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XPATH,
                    queryTarget);
            String xml;
            try {
                xml = target.loadXml();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (gen == queryRunGeneration) {
                        out.showFailure("Cannot read target file: " + e.getMessage());
                    }
                });
                return;
            }
            if (xquery) {
                String result = TransformRunner.runXQuery(xml, query, Map.of(),
                        XsltTransformationEngine.OutputFormat.XML);
                XQueryTableRunner.XQueryTable table = XQueryTableRunner.run(xml, query);
                boolean ok = !result.startsWith("ERROR");
                long elapsedMs = probe.finish(xml.length(), ok ? result.length() : -1, ok,
                        org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
                Platform.runLater(() -> {
                    if (gen == queryRunGeneration) {
                        out.showXQueryResult(result, table,
                                XsltTransformationEngine.OutputFormat.XML, elapsedMs);
                    }
                });
            } else {
                String result = TransformRunner.runXPath(xml, query);
                boolean ok = !result.startsWith("ERROR");
                long elapsedMs = probe.finish(xml.length(), ok ? result.length() : -1, ok,
                        org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
                Platform.runLater(() -> {
                    if (gen == queryRunGeneration) {
                        out.showQueryResult(result, elapsedMs);
                    }
                });
            }
        });
    }

    /**
     * Runs the active XSLT document against its resolved target (same resolution as
     * {@link #runActiveQuery()}: explicit target or the most recently active XML
     * document) with no parameters, the output format auto-detected from the
     * stylesheet, and shows the result in the docked OUTPUT panel. No-op for
     * non-XSLT documents.
     * <p>
     * Note: the transformation engine is string-based, so relative {@code doc()}
     * URIs are not resolved against a file-system target's directory.
     */
    public void runActiveTransform() {
        if (activeFileType() != EditorFileType.XSLT) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String xslt = editorHost.getActiveText().orElse("");
        if (xslt.isBlank()) {
            out.showError("The stylesheet is empty.");
            return;
        }
        var doc = editorHost.getActiveDocument().orElseThrow();
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isEmpty()) {
            out.showError("Open an XML document first, or pick one via the Target dropdown — "
                    + "by default the transform runs against the most recently active XML document.");
            return;
        }
        var target = resolved.get();
        var format = TransformRunner.detectXsltOutputFormat(xslt);
        out.showPending("Transforming " + target.displayName() + "…");
        String stylesheetName = doc.getDisplayName();
        final int gen = ++queryRunGeneration;
        FxtGui.executorService.submit(() -> {
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XSLT, stylesheetName);
            String xml;
            try {
                xml = target.loadXml();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (gen == queryRunGeneration) {
                        out.showFailure("Cannot read target file: " + e.getMessage());
                    }
                });
                return;
            }
            var fullResult = TransformRunner.xsltTransformResult(xml, xslt, Map.of(), format);
            String result = fullResult.isSuccess()
                    ? fullResult.getOutputContent()
                    : "ERROR: " + fullResult.getErrorMessage();
            if (fullResult.isSuccess()) {
                probe.phase("Compile", fullResult.getCompilationTime());
                probe.phase("Transform", fullResult.getTransformationTime());
            }
            long elapsedMs = probe.finish(xml.length(),
                    fullResult.isSuccess() ? result.length() : -1, fullResult.isSuccess(),
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            Platform.runLater(() -> {
                if (gen == queryRunGeneration) {
                    out.showTransformResult(result, format, elapsedMs);
                }
            });
        });
    }

    /**
     * Runs the active XProc pipeline via {@link XProcRunner}. The primary input is
     * the resolved target (same resolution as {@link #runActiveQuery()}), but unlike
     * XSLT a missing target is not an immediate error: self-contained pipelines
     * (no input ports, or ports with default bindings) are legal — the runner
     * decides. The pipeline's backing file (if saved) provides the base URI for
     * relative {@code href}s. No-op for non-XProc documents.
     */
    public void runActivePipeline() {
        if (activeFileType() != EditorFileType.XPROC) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String pipeline = editorHost.getActiveText().orElse("");
        if (pipeline.isBlank()) {
            out.showError("The pipeline is empty.");
            return;
        }
        var doc = editorHost.getActiveDocument().orElseThrow();
        var resolved = editorHost.resolveQueryTarget(doc);
        File pipelineFile = doc.getPath() != null ? doc.getPath().toFile() : null;
        out.showPending(resolved.map(t -> "Running pipeline against " + t.displayName() + "…")
                .orElse("Running pipeline…"));
        String pipelineName = doc.getDisplayName();
        final int gen = ++queryRunGeneration;
        FxtGui.executorService.submit(() -> {
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XPROC, pipelineName);
            String inputXmlText = resolved.map(EditorHost.ResolvedQueryTarget::xmlText).orElse(null);
            File inputXmlFile = resolved.map(EditorHost.ResolvedQueryTarget::file).orElse(null);
            XProcRunner.Result result =
                    XProcRunner.runPipeline(pipeline, pipelineFile, inputXmlText, inputXmlFile);
            boolean ok = !result.isError();
            long elapsedMs = probe.finish(inputXmlText != null ? inputXmlText.length() : -1,
                    ok ? result.text().length() : -1, ok,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result.text()));
            Platform.runLater(() -> {
                if (gen == queryRunGeneration) {
                    out.showTransformResult(result.text(), result.format(), elapsedMs);
                }
            });
        });
    }

    /** Ctrl+Enter dispatcher: transform for XSLT, pipeline for XProc, query otherwise. */
    public void runActive() {
        if (activeFileType() == EditorFileType.XSLT) {
            runActiveTransform();
        } else if (activeFileType() == EditorFileType.XPROC) {
            runActivePipeline();
        } else {
            runActiveQuery();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Generate Documentation
    // ---------------------------------------------------------------------------------------------

    /**
     * Opens the documentation generator in the editor area ({@link DocumentationView}):
     * the full option set (format, HTML rendering options, languages, output) with a
     * live progress log — same target as the Schema panel's ⋮ entry.
     *
     * @param window unused (kept for the toolbar wiring's signature)
     */
    public void generateDocsActive(Window window) {
        editorHost.openOrFocusToolTab("Documentation", "bi-file-earmark-text",
                () -> new DocumentationView(editorHost));
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
        switch (type) {
            case ERROR -> org.fxt.freexmltoolkit.util.DialogHelper.showError(title, null, message);
            case WARNING -> org.fxt.freexmltoolkit.util.DialogHelper.showWarning(title, null, message);
            default -> org.fxt.freexmltoolkit.util.DialogHelper.showInformation(title, null, message);
        }
    }
}
