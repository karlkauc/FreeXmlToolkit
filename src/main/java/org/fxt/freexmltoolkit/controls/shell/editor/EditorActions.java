package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

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

    /**
     * Status of a clean run without any schema: only well-formedness was checked. Not a
     * green "Valid" - the text says that no schema took part.
     */
    public static final String WELL_FORMED_NO_SCHEMA = "Well-formed · no schema bound";

    /** @return "1 problem" / "n problems" — the shared wording of validation counts */
    public static String problemCount(int count) {
        return count + (count == 1 ? " problem" : " problems");
    }

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
     * documents validate against the bound JSON Schema (or well-formedness only when
     * none is bound); every other text type validates against the bound XSD /
     * Schematron. Also publishes the result to {@link EditorHost#setValidationStatus}
     * so the status bar / inspector badge stay consistent.
     */
    public void validateActive() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        if (content.isBlank()) {
            editorHost.setValidationStatus(EditorHost.ValidationState.NOT_VALIDATED, 0, "Not validated");
            editorHost.openToolTab(I18n.t("EditorActions.validation"), "bi-check2-circle",
                    messageRegion("Empty document — nothing to validate."));
            return;
        }
        boolean json = editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
        // Resolved on the worker thread: reconciles the binding with the schema location
        // declared in the buffer (added/changed/removed references take effect this run).
        var schemaSupplier = editorHost.schemaForValidation(content);
        File schematron = editorHost.getActiveSchematron();
        var docKind = editorHost.getActiveDocument().map(d -> d.getFileType().docKind()).orElse(null);
        java.nio.file.Path documentPath = editorHost.getActiveDocument().map(OpenDocument::getPath).orElse(null);
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            File schema = schemaSupplier.get();
            ValidationRunner.RunResult runResult = json
                    ? new ValidationRunner.RunResult(ValidationRunner.validateJson(content, schema), null)
                    : ValidationRunner.runWithReport(content, schema, schematron, null, documentPath);
            List<ValidationProblem> result = runResult.problems();
            boolean hasSchema = json ? schema != null : (schema != null || schematron != null);
            UsageEvents.validated(UsageEvents.schemaKind(json, schema != null, !json && schematron != null),
                    docKind, content, result.size(), t0, runResult.failure(), false);
            Platform.runLater(() -> {
                String summary = result.isEmpty()
                        ? (hasSchema ? "Valid" : WELL_FORMED_NO_SCHEMA)
                        : problemCount(result.size());
                editorHost.setValidationStatus(
                        result.isEmpty() ? EditorHost.ValidationState.VALID : EditorHost.ValidationState.INVALID,
                        result.size(), summary);
                editorHost.openToolTab(I18n.t("EditorActions.validation"), "bi-check2-circle",
                        validationResultRegion(result, summary));
                if (runResult.failed()) {
                    // the validate event already carries the failure; the dialog explains it
                    org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorActions.validation"),
                            I18n.t("EditorActions.validation-could-not-run"),
                            I18n.t("EditorActions.check-that-the-bound-schema"),
                            runResult.error(), false);
                }
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
        whenTargetCurrent(this::runActiveQueryNow);
    }

    private void runActiveQueryNow() {
        EditorFileType type = activeFileType();
        if (type != EditorFileType.XQUERY && type != EditorFileType.XPATH) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String query = editorHost.getActiveText().orElse("");
        if (query.isBlank()) {
            out.showError(I18n.t("EditorActions.the-query-is-empty"));
            return;
        }
        var doc = editorHost.getActiveDocument().orElseThrow();
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isEmpty()) {
            out.showError(I18n.t("EditorActions.open-an-xml-document-first"));
            return;
        }
        var target = resolved.get();
        boolean xquery = type == EditorFileType.XQUERY;
        out.showPending(I18n.t("EditorActions.running-query-against", target.displayName()));
        String queryTarget = doc.getDisplayName();
        final int gen = ++queryRunGeneration;
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
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
                        out.showFailure(I18n.t("EditorActions.cannot-read-target-file", e.getMessage()));
                    }
                });
                return;
            }
            if (xquery) {
                TransformRunner.QueryRun run = TransformRunner.xquery(xml, query, Map.of(),
                        XsltTransformationEngine.OutputFormat.XML);
                String result = run.text();
                XQueryTableRunner.XQueryTable table = XQueryTableRunner.run(xml, query);
                boolean ok = !result.startsWith("ERROR");
                UsageEvents.xqueryExecuted(t0, run.failure());
                long elapsedMs = probe.finish(xml.length(), ok ? result.length() : -1, ok,
                        org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
                Platform.runLater(() -> {
                    if (gen == queryRunGeneration) {
                        out.showXQueryResult(result, table,
                                XsltTransformationEngine.OutputFormat.XML, elapsedMs);
                    }
                });
            } else {
                TransformRunner.QueryRun run = TransformRunner.xpath(xml, query);
                String result = run.text();
                boolean ok = !result.startsWith("ERROR");
                UsageEvents.xpathExecuted(t0, run.failure());
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
        whenTargetCurrent(this::runActiveTransformNow);
    }

    private void runActiveTransformNow() {
        if (activeFileType() != EditorFileType.XSLT) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String xslt = editorHost.getActiveText().orElse("");
        if (xslt.isBlank()) {
            out.showError(I18n.t("EditorActions.the-stylesheet-is-empty"));
            return;
        }
        var doc = editorHost.getActiveDocument().orElseThrow();
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isEmpty()) {
            out.showError(I18n.t("EditorActions.open-an-xml-document-first-2"));
            return;
        }
        var target = resolved.get();
        var format = TransformRunner.detectXsltOutputFormat(xslt);
        out.showPending(I18n.t("EditorActions.transforming", target.displayName()));
        String stylesheetName = doc.getDisplayName();
        // The editor text runs, but under the file's location so relative imports/includes resolve
        String xsltSystemId = doc.getPath() != null ? doc.getPath().toUri().toString() : null;
        String xmlBaseUri = target.file() != null ? target.file().toURI().toString() : null;
        // The target's schema (its open tab's binding), so the result's own tab can get one too
        java.io.File openTargetSchema = editorHost.schemaFileOf(target.file());
        final int gen = ++queryRunGeneration;
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XSLT, stylesheetName);
            String xml;
            try {
                xml = target.loadXml();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (gen == queryRunGeneration) {
                        out.showFailure(I18n.t("EditorActions.cannot-read-target-file", e.getMessage()));
                    }
                });
                return;
            }
            var fullResult = TransformRunner.xsltTransformResult(xml, xmlBaseUri, xslt, xsltSystemId, Map.of(), format);
            String result = fullResult.isSuccess()
                    ? fullResult.getOutputContent()
                    : "ERROR: " + fullResult.getErrorMessage();
            if (fullResult.isSuccess()) {
                probe.phase("Compile", fullResult.getCompilationTime());
                probe.phase("Transform", fullResult.getTransformationTime());
            }
            UsageEvents.xsltTransformed(1, t0, false, fullResult.getErrorPhase(),
                    TransformRunner.failureOf(fullResult), xml.length());
            long elapsedMs = probe.finish(xml.length(),
                    fullResult.isSuccess() ? result.length() : -1, fullResult.isSuccess(),
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            TransformOutputPanel.ResultSource source = fullResult.isSuccess()
                    ? TransformPanel.resultSource(target.file() != null ? target.file().toPath() : null,
                            openTargetSchema, openTargetSchema == null ? xml : null)
                    : null;
            Platform.runLater(() -> {
                if (gen == queryRunGeneration) {
                    out.showTransformResult(result, format, elapsedMs, source);
                }
            });
        });
    }

    /**
     * Starts the XSLT debugger for the active XSLT document against the resolved Run target
     * (the same resolution as {@link #runActiveTransform()}). The stylesheet must be saved:
     * the debugger reads it from disk so breakpoints map to the file's lines and relative
     * includes resolve. No-op for non-XSLT documents.
     */
    public void debugActiveTransform() {
        whenTargetCurrent(this::debugActiveTransformNow);
    }

    private void debugActiveTransformNow() {
        if (activeFileType() == EditorFileType.XQUERY) {
            debugActiveQuery();
            return;
        }
        if (activeFileType() == EditorFileType.SCHEMATRON) {
            debugActiveSchematron();
            return;
        }
        if (activeFileType() != EditorFileType.XSLT) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        var doc = editorHost.getActiveDocument().orElseThrow();
        if (doc.getPath() == null) {
            out.showError(I18n.t("EditorActions.save-the-stylesheet-first-the"));
            return;
        }
        // A saved debug configuration of this stylesheet decides input, parameters and format.
        var config = org.fxt.freexmltoolkit.debugger.DebugLaunchConfig.load(doc.getPath().toAbsolutePath().toString());
        if (config.isPresent()) {
            String error = editorHost.debugWithConfig(doc.getPath().toFile(), config.get());
            if (error != null) {
                out.showError(error);
            }
            return;
        }
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isEmpty()) {
            out.showError(I18n.t("EditorActions.open-an-xml-document-first-3"));
            return;
        }
        String xml;
        try {
            xml = resolved.get().loadXml();
        } catch (Exception e) {
            out.showFailure(I18n.t("EditorActions.cannot-read-target-file", e.getMessage()));
            return;
        }
        String xslt = editorHost.getActiveText().orElse("");
        if (resolved.get().file() != null) {
            editorHost.setDebugInputFile(resolved.get().file().toPath());
        }
        editorHost.startXsltDebug(doc.getPath().toFile(), xml, Map.of(),
                TransformRunner.detectXsltOutputFormat(xslt));
    }

    /**
     * Debugs the active (saved) XQuery against the Run target - the same target as Run Query; a
     * query without a target runs without a context item.
     */
    public void debugActiveQuery() {
        whenTargetCurrent(this::debugActiveQueryNow);
    }

    private void debugActiveQueryNow() {
        if (activeFileType() != EditorFileType.XQUERY) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        var doc = editorHost.getActiveDocument().orElseThrow();
        if (doc.getPath() == null) {
            out.showError(I18n.t("EditorActions.save-the-query-first-breakpoints"));
            return;
        }
        String xml = "";
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isPresent()) {
            try {
                xml = resolved.get().loadXml();
            } catch (Exception e) {
                out.showFailure(I18n.t("EditorActions.cannot-read-target-file", e.getMessage()));
                return;
            }
            if (resolved.get().file() != null) {
                editorHost.setDebugInputFile(resolved.get().file().toPath());
            }
        }
        editorHost.startXsltDebug(doc.getPath().toFile(), xml, Map.of(),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML);
    }

    /**
     * Runs the active XQuery under the profiler against the Run target (a query without a target
     * runs without a context item): the result goes to the OUTPUT panel, the profile opens in a
     * "Profile: …" tab ({@link ProfileTabs}). The editor text runs, under the file's URI when saved.
     */
    public void profileActiveQuery() {
        whenTargetCurrent(this::profileActiveQueryNow);
    }

    private void profileActiveQueryNow() {
        if (activeFileType() != EditorFileType.XQUERY) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String query = editorHost.getActiveText().orElse("");
        if (query.isBlank()) {
            out.showError(I18n.t("EditorActions.the-query-is-empty"));
            return;
        }
        var doc = editorHost.getActiveDocument().orElseThrow();
        String querySystemId = doc.getPath() == null ? null : doc.getPath().toUri().toString();
        var target = editorHost.resolveQueryTarget(doc).orElse(null);
        out.showPending(I18n.t("EditorActions.profiling-query-against",
                target == null ? "-" : target.displayName()));
        String title = I18n.t("TransformPanel.profile", doc.getDisplayName());
        final int gen = ++queryRunGeneration;
        FxtGui.executorService.submit(() -> {
            String xml = "";
            String xmlUri = null;
            if (target != null) {
                try {
                    xml = target.loadXml();
                } catch (Exception e) {
                    Platform.runLater(() -> out.showFailure(I18n.t("EditorActions.cannot-read-target-file", e.getMessage())));
                    return;
                }
                xmlUri = target.file() == null ? null : target.file().toURI().toString();
            }
            String input = xml;
            String inputUri = xmlUri;
            java.util.function.Supplier<org.fxt.freexmltoolkit.service.XsltTransformationEngine.ProfiledTransformation> measure =
                    () -> TransformRunner.profileXQuery(input, inputUri, query, querySystemId, Map.of(),
                            XsltTransformationEngine.OutputFormat.XML, 1);
            var profiled = measure.get();
            Platform.runLater(() -> {
                if (gen != queryRunGeneration) {
                    return;
                }
                var result = profiled.result();
                if (!result.isSuccess() || profiled.profile() == null) {
                    out.showFailure(I18n.t("TransformPanel.profiling-failed", result.getErrorMessage()));
                    return;
                }
                out.showTransformResult(result.getOutputContent(), result.getOutputFormat(),
                        result.getTransformationTime(), null);
                ProfileTabs.show(editorHost, title, profiled.profile(), result, measure,
                        message -> out.showFailure(I18n.t("TransformPanel.profiling-failed", message)), null);
            });
        });
    }

    /**
     * Debugs the active (saved) Schematron against the Run target: SchXslt compiles it to XSLT,
     * the debugger runs that and shows every stop at its {@code .sch} line. Needs an XML target.
     */
    public void debugActiveSchematron() {
        whenTargetCurrent(this::debugActiveSchematronNow);
    }

    private void debugActiveSchematronNow() {
        if (activeFileType() != EditorFileType.SCHEMATRON) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        var doc = editorHost.getActiveDocument().orElseThrow();
        if (doc.getPath() == null) {
            out.showError(I18n.t("EditorActions.save-the-schematron-first-it"));
            return;
        }
        var resolved = editorHost.resolveQueryTarget(doc);
        if (resolved.isEmpty()) {
            out.showError(I18n.t("EditorActions.open-an-xml-document-first-4"));
            return;
        }
        String xml;
        try {
            xml = resolved.get().loadXml();
        } catch (Exception e) {
            out.showFailure(I18n.t("EditorActions.cannot-read-target-file", e.getMessage()));
            return;
        }
        if (resolved.get().file() != null) {
            editorHost.setDebugInputFile(resolved.get().file().toPath());
        }
        editorHost.startXsltDebug(doc.getPath().toFile(), xml, Map.of(),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML);
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
        whenTargetCurrent(this::runActivePipelineNow);
    }

    private void runActivePipelineNow() {
        if (activeFileType() != EditorFileType.XPROC) {
            return;
        }
        TransformOutputPanel out = editorHost.transformOutputPanel();
        String pipeline = editorHost.getActiveText().orElse("");
        if (pipeline.isBlank()) {
            out.showError(I18n.t("EditorActions.the-pipeline-is-empty"));
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
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XPROC, pipelineName);
            String inputXmlText = resolved.map(EditorHost.ResolvedQueryTarget::xmlText).orElse(null);
            File inputXmlFile = resolved.map(EditorHost.ResolvedQueryTarget::file).orElse(null);
            XProcRunner.Result result =
                    XProcRunner.runPipeline(pipeline, pipelineFile, inputXmlText, inputXmlFile);
            boolean ok = !result.isError();
            UsageEvents.xprocExecuted(t0, ok ? null : result.failure() != null ? result.failure()
                    : org.fxt.freexmltoolkit.service.telemetry.QueryFailure.UNKNOWN);
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
        DialogHelper.choose("Open Type Editor", "Choose a named type to edit", "Type:", names.get(0), names)
                .ifPresent(editorHost::openTypeEditorTab);
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

    /**
     * Runs {@code action} once the Run target's text holds every grid edit: a large XML document
     * writes deferred edits into its text off the FX thread first (serializing and splitting
     * 170 MB froze the UI for seconds when a run read the text synchronously). At once otherwise.
     */
    private void whenTargetCurrent(Runnable action) {
        var doc = editorHost.getActiveDocument().orElse(null);
        if (doc == null) {
            action.run();
            return;
        }
        editorHost.whenQueryTargetCurrent(doc).thenRun(action).exceptionally(e -> {
            org.apache.logging.log4j.LogManager.getLogger(EditorActions.class).error("Run action failed", e);
            return null;
        });
    }
}
