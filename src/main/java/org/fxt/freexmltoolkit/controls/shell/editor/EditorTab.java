package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.BoundSchemaWatch;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/** A tab bound to one {@link OpenDocument}, its editor view, and Text/Tree/Graphic modes. */
final class EditorTab extends Tab {
    final OpenDocument document;
    final EditorView view;
    private final Runnable selectionCallback;
    /** Opens the shell's type-editor tab for a named type (Graphic-view "Open in Type Editor"). */
    private final java.util.function.Consumer<String> openTypeEditorCallback;
    /** Navigates an XML-instance element to its XSD definition (Graphic/Tree "Go to Definition"). */
    private final java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode> goToDefinitionCallback;
    /** Shared shell clipboard (passed in: EditorTab holds no reference to its host). */
    private final org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard clipboard;
    private final javafx.scene.layout.StackPane contentStack = new javafx.scene.layout.StackPane();
    org.fxt.freexmltoolkit.controls.shell.schema.XsdTreeView treeView;
    org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView xsdGraphView;

    /**
     * The type frame the Graphic view's selection lies in: such a node belongs to a named type and
     * is read-only in place (it is changed in the type's editor).
     *
     * @return the frame, or null when the selection can be edited here
     */
    org.fxt.freexmltoolkit.controls.v2.view.TypeFrame selectionTypeFrame() {
        return viewMode == ViewMode.GRAPHIC && xsdGraphView != null && currentSelection != null
                ? xsdGraphView.lockingFrameOfSelection() : null;
    }

    /**
     * The type frame that keeps content from being added under the Graphic view's selection: an
     * element whose content is a named type's frame adds new children to that type.
     *
     * @return the frame, or null when content can be added here
     */
    org.fxt.freexmltoolkit.controls.v2.view.TypeFrame selectionContentTypeFrame() {
        return viewMode == ViewMode.GRAPHIC && xsdGraphView != null && currentSelection != null
                ? xsdGraphView.contentLockingFrameOfSelection() : null;
    }
    org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView jsonTreeView;
    org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView xmlInstanceTreeView;
    XmlGridView xmlGridView;
    JsonGridView jsonGridView;
    /** The read-only HTML Preview (a WebView, or a text fallback without javafx.scene.web). */
    private javafx.scene.Node htmlPreviewNode;
    private java.util.function.Consumer<String> htmlPreviewUpdater;
    /** Text the preview last rendered — skips redundant {@code loadContent} calls. */
    String lastPreviewedText;
    org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext;
    XsdNode currentSelection;
    org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode currentXmlSelection;
    org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode currentJsonSelection;
    ViewMode viewMode = ViewMode.TEXT;
    private boolean dirtyTrackingAttached;
    /** True while the file is being (re)read; the external-change check skips the tab. */
    boolean loading;
    /** Writes in flight ({@link EditorFileSync#writeAsync}); the external-change check skips the tab meanwhile. */
    int saving;
    /** The last write started for this tab: the next one waits for it, so writes land in order. */
    CompletableFuture<?> pendingWrite = CompletableFuture.completedFuture(null);
    /** Editor text the current {@link #editorContext} was parsed from (P2: avoid needless re-parse). */
    String lastParsedText;
    /**
     * Counts the editor's text changes. The {@code last*Revision} fields record which revision a model
     * was parsed from, so "is the model current?" is a number comparison instead of building and
     * comparing the whole document text on every caret move.
     */
    long textRevision;
    private long lastParsedRevision = -1;
    private long lastParsedXmlRevision = -1;
    private long lastParsedJsonRevision = -1;
    private long caretMapRevision = -1;
    /** Revision a background XSD/XML model parse is running for (-1 = none), and who waits for it. */
    private long pendingXsdParseRevision = -1;
    private java.util.List<Runnable> pendingXsdParseCallbacks;
    private long pendingXmlParseRevision = -1;
    private java.util.List<Runnable> pendingXmlParseCallbacks;
    /** Revision whose XSD parse failed: not retried until the text changes. */
    private long failedXsdParseRevision = -1;
    private long failedXmlParseRevision = -1;
    private long failedJsonParseRevision = -1;
    /** Whether the last model parse left out remote imports that were not in the schema cache. */
    boolean remoteImportsMissing;
    /** Host callback for remote imports that became available after a background download. */
    Runnable remoteImportsCallback;
    /** Host callback after a background parse installed a fresh model (FX thread). */
    Runnable modelInstalledCallback = () -> { };
    /** The files the current XSD model pulls in and their state when it read them. */
    java.util.Map<Path, DiskStamp> dependencyStamps = new java.util.LinkedHashMap<>();
    /** The content checksums of those files, to tell a touched file from a changed one. */
    java.util.Map<Path, Long> dependencyChecksums = new java.util.HashMap<>();
    /** For an XML document: the bound schema's files as its schema facts were read. */
    BoundSchemaWatch boundSchemaWatch;
    /** Host callback after a view switch that waited for a background parse has been applied. */
    Runnable viewModeAppliedCallback = () -> { };
    /** Shared XML-instance model+command context across Text/Tree/Grid (mirrors {@link #editorContext}). */
    org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext xmlEditorContext;
    /** Shared JSON model+command context across Text/Tree/Grid (mirrors {@link #xmlEditorContext}). */
    org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext jsonEditorContext;
    /** Parser message of the last failed JSON parse (shown by the grid placeholder). */
    private String lastJsonParseError;
    /** Coalesces bursts of model-change events into a single round-trip (P1). */
    private final javafx.animation.PauseTransition roundTripDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
    /** Coalesces caret movement into a single Text-view caret&rarr;node resolution. */
    final javafx.animation.PauseTransition xmlCaretDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
    /** Read on validation worker threads (schema reconcile) — hence volatile. */
    volatile File schemaFile;
    /** V2 model of {@link #schemaFile} for an XML tab (read-only cache; edits go to the XSD's tab). */
    org.fxt.freexmltoolkit.controls.v2.model.XsdSchema boundSchemaModel;
    /** Runs once after the async file load has put the text into the editor (background tabs). */
    Runnable afterLoad;
    /** How {@link #schemaFile} was bound; MANUAL bindings survive auto-redetection. */
    volatile SchemaRebindPolicy.SchemaBindingOrigin schemaOrigin =
            SchemaRebindPolicy.SchemaBindingOrigin.NONE;
    /**
     * Raw declared location the last auto-detection saw ({@code null} = none declared).
     * Recorded on failures too, so an unresolvable URL is not re-attempted on every
     * validation/debounce cycle — only when the declared value actually changes.
     */
    volatile String lastDetectedSchemaLocation;
    /** Written on the loader thread, read on the FX thread (tab switch) — hence volatile. */
    volatile SchemaStatus schemaStatus = SchemaStatus.NONE;
    /** How {@link #schemaFile} was resolved (declared/catalog/library/manual); status-bar detail. */
    volatile SchemaSource schemaSource = SchemaSource.NONE;
    /** Tooltip detail for {@link #schemaSource} — catalog target, or matched namespace/root element. */
    volatile String schemaSourceDetail;
    File schematronFile;
    /** Composes the debugger's and the quick-fix feature's gutter contributions. */
    final CompositeGutterFactory gutter = new CompositeGutterFactory();
    /**
     * Guards against CONCURRENT schema detections for this tab (open-time
     * detection vs. the Validation panel's redetect): the schema pipeline
     * (Xerces deferred DOM in XsdDocumentationService) is not thread-safe.
     */
    final java.util.concurrent.atomic.AtomicBoolean schemaDetecting =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /**
     * Set once the tab has been closed. Background schema work polls it so a detection that
     * is already running stops before its next stage and never applies its result to a tab
     * nobody can see - which would otherwise go on to parse the whole schema
     * ({@link EditorSchemaBinding#loadXmlSchemaProviderAsync}) for a discarded document.
     */
    final java.util.concurrent.atomic.AtomicBoolean closed =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /**
     * Generation of the schema binding, bumped on the FX thread by every entry point
     * that establishes newer truth (file open, redetect, manual bind, validation
     * reconcile). A queued validation task whose buffer snapshot has been superseded
     * observes a newer generation and must not rebind/clear — without this, a stale
     * task could revert a binding that a fresher detection just established.
     */
    final java.util.concurrent.atomic.AtomicLong schemaBindingGen =
            new java.util.concurrent.atomic.AtomicLong();
    /**
     * Counts explicit binds ({@link EditorSchemaBinding#bindSchemaToActiveDocument}). Unlike
     * {@link #schemaBindingGen}, validation reconciles do not bump it, so a bind whose schema
     * is still parsing is only superseded by a newer bind, never by a validation run.
     */
    final java.util.concurrent.atomic.AtomicLong schemaBindRequest =
            new java.util.concurrent.atomic.AtomicLong();
    /** XSD-derived, read-only schema info for XML-instance nodes (lazily built off-thread). */
    org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider xmlSchemaProvider;
    /** Spinner overlay shown over the editor while the file is being read/schema-detected. */
    private final javafx.scene.layout.StackPane loadingOverlay = buildLoadingOverlay();
    /**
     * Delays showing {@link #loadingOverlay} until loading takes longer than this, so small
     * files that load near-instantly never flash the spinner.
     */
    private final javafx.animation.PauseTransition loadDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));

    EditorTab(OpenDocument document, Runnable selectionCallback,
            org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard clipboard,
            java.util.function.Consumer<String> openTypeEditorCallback,
            java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode> goToDefinitionCallback) {
        this.document = document;
        this.selectionCallback = selectionCallback;
        this.clipboard = clipboard;
        this.openTypeEditorCallback = openTypeEditorCallback;
        this.goToDefinitionCallback = goToDefinitionCallback;
        this.view = EditorViews.create(document.getFileType());
        view.getCodeArea().plainTextChanges().subscribe(change -> textRevision++);
        contentStack.getChildren().add(view.getNode());
        // Overlay sits ABOVE contentStack (not inside it) so showOnly() — which toggles
        // visibility of every contentStack child — never touches the spinner.
        setContent(new javafx.scene.layout.StackPane(contentStack, loadingOverlay));
        loadDelay.setOnFinished(e -> {
            loadingOverlay.setVisible(true);
            loadingOverlay.setManaged(true);
        });
        textProperty().bind(Bindings.createStringBinding(
                () -> (document.isDirty() ? "● " : "") + document.getDisplayName(),
                document.dirtyProperty(), document.displayNameProperty()));
        roundTripDebounce.setOnFinished(e -> roundTripModelToText());
        xmlCaretDebounce.setOnFinished(e -> {
            if (document.getFileType() == EditorFileType.XSD) {
                resolveCaretXsdNode();
            } else {
                resolveCaretXmlNode();
            }
        });
        refreshIcon();
    }

    /** XSD-backed structured views (Tree/Graphic with the V2 model + commands). */
    boolean supportsStructuredViews() {
        return document.getFileType() == EditorFileType.XSD;
    }

    /**
     * Which view modes this document offers: every recognized type has a Tree
     * (XSD schema tree / JSON tree / XML-instance DOM tree); Graphic covers the
     * XSD diagram for schemas and the instance grid for XML-family instances.
     */
    boolean supportsView(ViewMode mode) {
        return switch (mode) {
            case TEXT -> true;
            case PREVIEW -> document.getFileType() == EditorFileType.HTML;
            case OVERVIEW -> document.getFileType() == EditorFileType.XSD;
            case TREE -> switch (document.getFileType()) {
                // Query documents are not XML — the DOM tree view cannot parse them.
                // HTML output is often not well-formed XML (unclosed <br>, &nbsp;) either.
                case OTHER, XQUERY, XPATH, HTML -> false;
                default -> true;
            };
            case GRAPHIC -> switch (document.getFileType()) {
                case XSD, XML, XSLT, SCHEMATRON, JSON -> true;
                default -> false;
            };
        };
    }

    void setViewMode(ViewMode mode) {
        // Persist any pending (debounced) graphical edit before switching, so the
        // text we read below — and the new view — see the latest model state.
        flushPendingRoundTrip();
        ViewMode target = supportsView(mode) ? mode : ViewMode.TEXT;
        pendingViewMode = null;
        if (needsBackgroundParse(target)) {
            // A large document whose model is stale: parse it off the FX thread (parsing multi-MB
            // documents here froze the UI for seconds) and finish the switch once the model is there.
            // The loading overlay shows meanwhile; a newer switch request supersedes this one.
            pendingViewMode = target;
            beginLoading();
            Runnable finish = () -> {
                endLoading();
                if (pendingViewMode == target && !isAbandoned(this)) {
                    pendingViewMode = null;
                    setViewMode(target);
                    viewModeAppliedCallback.run();
                }
            };
            switch (document.getFileType()) {
                case XSD -> ensureModelParsedAsync(finish);
                case JSON -> ensureJsonModelParsedAsync(finish);
                default -> ensureXmlModelParsedAsync(finish);
            }
            return;
        }
        this.viewMode = target;
        this.currentSelection = null;
        this.currentXmlSelection = null;
        this.currentJsonSelection = null;
        if (target == ViewMode.TEXT) {
            showOnly(view.getNode());
            // The Tree/Graphic selection was just dropped: map the caret again, so the inspector
            // shows the node at the caret instead of nothing until the caret moves.
            if (document.getFileType() != EditorFileType.JSON) {
                xmlCaretDebounce.playFromStart();
            }
            return;
        }
        // HTML renders read-only in a WebView; editing happens in Text mode.
        if (target == ViewMode.PREVIEW) {
            ensureHtmlPreview();
            refreshHtmlPreview();
            showOnly(htmlPreviewNode);
            return;
        }
        // JSON: Tree and Graphic share ONE JsonEditorContext (model + undo history),
        // exactly like the XML instance views below.
        if (document.getFileType() == EditorFileType.JSON) {
            ensureJsonModelParsed();
            if (target == ViewMode.GRAPHIC) {
                ensureJsonGrid();
                if (jsonEditorContext != null) {
                    jsonGridView.setContext(jsonEditorContext);
                } else if (lastJsonParseError != null) {
                    jsonGridView.showParseError(lastJsonParseError);
                } else {
                    jsonGridView.setContext(null);
                }
                showOnly(jsonGridView);
            } else {
                ensureJsonTree();
                jsonTreeView.setContext(jsonEditorContext);
                showOnly(jsonTreeView);
            }
            return;
        }
        // For XML-family instances, Graphic is the editable XMLSpy-style grid
        // over the shared context (the XSD diagram below covers schemas).
        if (target == ViewMode.GRAPHIC && document.getFileType() != EditorFileType.XSD) {
            ensureXmlGrid();
            ensureXmlModelParsed();
            xmlGridView.setContext(xmlEditorContext);
            showOnly(xmlGridView);
            return;
        }
        // XML instances (incl. XSLT/Schematron) get a selectable Tree over the shared model.
        if (document.getFileType() != EditorFileType.XSD) {
            ensureXmlInstanceTree();
            ensureXmlModelParsed();
            xmlInstanceTreeView.setModel(xmlEditorContext);
            showOnly(xmlInstanceTreeView);
            return;
        }
        // XSD structured views share one model+context across Tree/Graphic; it is
        // (re)parsed only when missing or when the text changed since the last parse
        // (P2), so undo history survives switching modes — even via a Text detour.
        ensureModelParsed();
        switch (target) {
            case TREE -> {
                ensureTree();
                renderStructured();
                showOnly(treeView);
            }
            case GRAPHIC -> {
                ensureCanvasGraphic();
                if (xsdGraphView != null) {
                    showOnly(xsdGraphView);
                } else {
                    // Model did not parse: fall back to Text.
                    viewMode = ViewMode.TEXT;
                    showOnly(view.getNode());
                }
            }
            case OVERVIEW -> {
                ensureOverview();
                if (schemaOverview != null && editorContext != null) {
                    showOnly(schemaOverview);
                } else {
                    // Model did not parse: fall back to Text.
                    viewMode = ViewMode.TEXT;
                    showOnly(view.getNode());
                }
            }
            default -> showOnly(view.getNode());
        }
    }

    /** (Re)parses the model only when missing or when the text changed since the last parse (P2). */
    void ensureModelParsed() {
        if (!isModelCurrent()) {
            parseModel();
        }
    }

    /** Whether the XSD model matches the text, or the text is known not to parse (not parsed again). */
    boolean isModelCurrent() {
        return (editorContext != null && lastParsedRevision == textRevision)
                || failedXsdParseRevision == textRevision;
    }

    /** Above this size a structured view's stale model is parsed off the FX thread. */
    private static final int BACKGROUND_PARSE_THRESHOLD = 256 * 1024;

    /** The view mode a background parse will switch to, or {@code null}. */
    private ViewMode pendingViewMode;

    /** Whether switching to {@code target} has to parse a large document's model first. */
    private boolean needsBackgroundParse(ViewMode target) {
        if (target == ViewMode.TEXT || target == ViewMode.PREVIEW
                || view.getCodeArea().getLength() < BACKGROUND_PARSE_THRESHOLD) {
            return false;
        }
        return switch (document.getFileType()) {
            case XSD -> (editorContext == null || lastParsedRevision != textRevision)
                    && failedXsdParseRevision != textRevision;
            case JSON -> (jsonEditorContext == null || lastParsedJsonRevision != textRevision)
                    && failedJsonParseRevision != textRevision;
            default -> (xmlEditorContext == null || lastParsedXmlRevision != textRevision)
                    && failedXmlParseRevision != textRevision;
        };
    }

    private void parseModel() {
        try {
            // Resolve xs:include / xs:import relative to the file's directory when on disk, so
            // the Type Library and facet/type resolution see externally defined types. The
            // included nodes are tagged isFromInclude (preserveIncludeStructure) and are
            // excluded again on round-trip so the include structure is never flattened.
            // Remote imports come from the schema cache only (this runs on the FX thread);
            // missing ones are downloaded in the background and trigger one re-parse.
            String parsedText = view.getText();
            XsdUiParser.Result parsed = XsdUiParser.parseDetailed(
                    parsedText, document.getPath(), remoteImportsCallback);
            editorContext = new org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext(parsed.schema());
            lastParsedText = parsedText;
            lastParsedRevision = textRevision;
            remoteImportsMissing = parsed.remoteImportsMissing();
            dependencyStamps = new java.util.LinkedHashMap<>(parsed.dependencyStamps());
            dependencyChecksums = new java.util.HashMap<>(parsed.dependencyChecksums());
            // A new context means a new schema: ensureCanvasGraphic() rebuilds the
            // Graphic view (it compares getEditorContext() and discards the stale one).
        } catch (Exception e) {
            editorContext = null;
            lastParsedText = null;
            lastParsedRevision = -1;
            failedXsdParseRevision = textRevision; // the same text fails again: do not re-parse it
            remoteImportsMissing = false;
            dependencyStamps = new java.util.LinkedHashMap<>();
            dependencyChecksums = new java.util.HashMap<>();
        }
    }

    /**
     * Rebuilds the model because files it pulls in changed. The text is the same, so unlike a
     * reload of the document itself unsaved edits survive; pending diagram edits are written
     * to the text first. A large schema parses in the background through the usual path.
     */
    void reparseFromDependencies() {
        flushPendingRoundTrip();
        lastParsedText = null;
        lastParsedRevision = -1;
        failedXsdParseRevision = -1;
        if (viewMode == ViewMode.TEXT) {
            ensureModelParsedAsync(() -> { });
            return;
        }
        setViewMode(viewMode);
        if (isModelCurrent()) { // otherwise the background parse reports it when done
            modelInstalledCallback.run();
            viewModeAppliedCallback.run();
        }
    }

    /**
     * Re-parses the model once remote imports skipped by {@link #parseModel()} are in the
     * schema cache. Skipped when the text changed meanwhile (the next parse picks the cache
     * up anyway) or when there is undo history a re-parse would discard.
     */
    void onRemoteImportsAvailable(String parsedText) {
        if (isAbandoned(this) || !java.util.Objects.equals(lastParsedText, parsedText)
                || !java.util.Objects.equals(view.getText(), parsedText)) {
            return;
        }
        if (editorContext != null && editorContext.getCommandManager().canUndo()) {
            return;
        }
        lastParsedText = null;
        lastParsedRevision = -1;
        if (viewMode != ViewMode.TEXT) {
            setViewMode(viewMode);
        } else {
            // Text view: re-parse in the background now, so the listings (Type Library, inspector)
            // pick the imported types up via activeModelRevision without waiting for a caret move.
            ensureModelParsedAsync(() -> { });
        }
    }

    /**
     * (Re)parses the shared XML-instance model, only when missing or when the text changed since
     * the last parse — so the model, its undo history and the current selection survive view
     * switches (the XML counterpart of {@link #ensureModelParsed()}).
     */
    private void ensureXmlModelParsed() {
        if ((xmlEditorContext == null || lastParsedXmlRevision != textRevision)
                && failedXmlParseRevision != textRevision) { // a known failure is not parsed again
            parseXmlModel();
        }
    }

    private void parseXmlModel() {
        String text = view.getText();
        if (text == null || text.isBlank()) {
            xmlEditorContext = null;
            lastParsedXmlRevision = -1;
            return;
        }
        try {
            installXmlModel(parseXmlDocument(text), textRevision);
        } catch (Exception e) {
            xmlEditorContext = null;
            lastParsedXmlRevision = -1;
            failedXmlParseRevision = textRevision; // the same text fails again: do not re-parse it
        }
    }

    /** Line-aware parse (XmlElement.getSourceLineNumber is set: the Text view maps the caret line to a node). */
    private static org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument parseXmlDocument(String text)
            throws Exception {
        return new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser().parse(text);
    }

    /** Makes {@code doc} the shared XML model (fresh context: schema provider and Go to Definition wired). */
    private void installXmlModel(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc,
                                 long revision) {
        var ctx = new org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext(doc);
        if (xmlSchemaProvider != null) {
            ctx.setSchemaProvider(xmlSchemaProvider);
        }
        ctx.setGoToDefinitionHandler(goToDefinitionCallback);
        xmlEditorContext = ctx;
        lastParsedXmlRevision = revision;
    }

    /**
     * Brings the XSD model up to date with the text without blocking the FX thread: when it is stale, a
     * snapshot is parsed on a worker and installed on the FX thread if the text is still at that
     * revision; then {@code onCurrent} runs (on the FX thread). If the text moved on meanwhile, the
     * parse chases the newest revision. Runs {@code onCurrent} at once when the model is current or
     * the current text is known not to parse. Must be called on the FX thread.
     */
    void ensureModelParsedAsync(Runnable onCurrent) {
        if ((editorContext != null && lastParsedRevision == textRevision)
                || failedXsdParseRevision == textRevision) {
            onCurrent.run();
            return;
        }
        long revision = textRevision;
        if (pendingXsdParseRevision == revision) {
            pendingXsdParseCallbacks.add(onCurrent);
            return;
        }
        java.util.List<Runnable> waiting = new java.util.ArrayList<>();
        if (pendingXsdParseCallbacks != null) {
            waiting.addAll(pendingXsdParseCallbacks); // an older parse is superseded by this one
        }
        waiting.add(onCurrent);
        pendingXsdParseRevision = revision;
        pendingXsdParseCallbacks = waiting;
        String text = view.getText();
        java.nio.file.Path path = document.getPath();
        Runnable remoteCallback = remoteImportsCallback;
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            XsdUiParser.Result parsed;
            try {
                parsed = XsdUiParser.parseDetailed(text, path, remoteCallback);
            } catch (Exception | LinkageError e) {
                parsed = null; // the Tree/Graphic view shows a placeholder; a parser bug is counted
                org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.parse_xsd");
            }
            XsdUiParser.Result result = parsed;
            Platform.runLater(() -> {
                if (pendingXsdParseRevision != revision) {
                    return; // superseded: the newer parse runs the callbacks
                }
                java.util.List<Runnable> callbacks = pendingXsdParseCallbacks;
                pendingXsdParseRevision = -1;
                pendingXsdParseCallbacks = null;
                if (isAbandoned(this)) {
                    return;
                }
                if (textRevision != revision) {
                    callbacks.forEach(this::ensureModelParsedAsync); // edited meanwhile: chase it
                    return;
                }
                if (!(editorContext != null && lastParsedRevision == revision)) { // not parsed sync meanwhile
                    if (result != null) {
                        editorContext = new org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext(
                                result.schema());
                        lastParsedText = text;
                        lastParsedRevision = revision;
                        remoteImportsMissing = result.remoteImportsMissing();
                        dependencyStamps = new java.util.LinkedHashMap<>(result.dependencyStamps());
                        dependencyChecksums = new java.util.HashMap<>(result.dependencyChecksums());
                    } else {
                        failedXsdParseRevision = revision;
                    }
                    modelInstalledCallback.run();
                }
                callbacks.forEach(Runnable::run);
            });
        });
    }

    /** XML counterpart of {@link #ensureModelParsedAsync(Runnable)} for the shared instance model. */
    void ensureXmlModelParsedAsync(Runnable onCurrent) {
        if ((xmlEditorContext != null && lastParsedXmlRevision == textRevision)
                || failedXmlParseRevision == textRevision) {
            onCurrent.run();
            return;
        }
        long revision = textRevision;
        if (pendingXmlParseRevision == revision) {
            pendingXmlParseCallbacks.add(onCurrent);
            return;
        }
        java.util.List<Runnable> waiting = new java.util.ArrayList<>();
        if (pendingXmlParseCallbacks != null) {
            waiting.addAll(pendingXmlParseCallbacks);
        }
        waiting.add(onCurrent);
        pendingXmlParseRevision = revision;
        pendingXmlParseCallbacks = waiting;
        String text = view.getText();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument parsed;
            try {
                parsed = text == null || text.isBlank() ? null : parseXmlDocument(text);
            } catch (Exception | LinkageError e) {
                parsed = null;
                org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.parse_xml");
            }
            var doc = parsed;
            Platform.runLater(() -> {
                if (pendingXmlParseRevision != revision) {
                    return;
                }
                java.util.List<Runnable> callbacks = pendingXmlParseCallbacks;
                pendingXmlParseRevision = -1;
                pendingXmlParseCallbacks = null;
                if (isAbandoned(this)) {
                    return;
                }
                if (textRevision != revision) {
                    callbacks.forEach(this::ensureXmlModelParsedAsync);
                    return;
                }
                if (!(xmlEditorContext != null && lastParsedXmlRevision == revision)) {
                    if (doc != null) {
                        installXmlModel(doc, revision);
                    } else {
                        xmlEditorContext = null;
                        lastParsedXmlRevision = -1;
                        failedXmlParseRevision = revision;
                    }
                    modelInstalledCallback.run();
                }
                callbacks.forEach(Runnable::run);
            });
        });
    }

    /** Serializes the shared XML model back into the editor text via a minimal diff (caret/scroll preserved). */
    private void roundTripXmlModelToText() {
        if (xmlEditorContext == null) {
            return;
        }
        String xml = xmlEditorContext.serializeToString();
        String current = view.getText();
        int[] region = TextDiff.minimalReplaceRegion(current, xml);
        if (region[0] == region[1] && region[0] == region[2]) {
            lastParsedXmlRevision = textRevision;
            return;
        }
        view.replaceTextRegion(region[0], region[1], xml.substring(region[0], region[2]));
        lastParsedXmlRevision = textRevision;
        document.setDirty(true);
    }

    /**
     * JSON counterpart of {@link #ensureXmlModelParsedAsync(Runnable)}: parses the text into a fresh
     * {@link org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext} on a worker and installs
     * it on the FX thread if the text is unchanged, then runs {@code onCurrent}. FX thread.
     */
    void ensureJsonModelParsedAsync(Runnable onCurrent) {
        if (jsonEditorContext != null && lastParsedJsonRevision == textRevision) {
            onCurrent.run();
            return;
        }
        long revision = textRevision;
        String text = view.getText();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext ctx = null;
            String error = null;
            if (text != null && !text.isBlank()) {
                try {
                    ctx = new org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext();
                    ctx.loadDocumentFromString(text);
                } catch (Exception e) {
                    ctx = null;
                    error = e.getMessage();
                }
            }
            var parsed = ctx;
            String parseError = error;
            Platform.runLater(() -> {
                if (isAbandoned(this)) {
                    return;
                }
                if (textRevision != revision) {
                    ensureJsonModelParsedAsync(onCurrent); // edited meanwhile: chase it
                    return;
                }
                if (!(jsonEditorContext != null && lastParsedJsonRevision == revision)) {
                    jsonEditorContext = parsed;
                    lastParsedJsonRevision = parsed != null ? revision : -1;
                    lastJsonParseError = parseError;
                    if (parsed == null) {
                        failedJsonParseRevision = revision;
                    }
                }
                onCurrent.run();
            });
        });
    }

    /** JSON counterpart of {@link #ensureXmlModelParsed()}: re-parses only when the text changed. */
    private void ensureJsonModelParsed() {
        if ((jsonEditorContext == null || lastParsedJsonRevision != textRevision)
                && failedJsonParseRevision != textRevision) { // a known failure is not parsed again
            parseJsonModel();
        }
    }

    private void parseJsonModel() {
        String text = view.getText();
        lastJsonParseError = null;
        if (text == null || text.isBlank()) {
            jsonEditorContext = null;
            return;
        }
        try {
            var ctx = new org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext();
            ctx.loadDocumentFromString(text);
            jsonEditorContext = ctx;
            lastParsedJsonRevision = textRevision;
        } catch (Exception e) {
            // Invalid JSON while typing is expected; the views show a placeholder.
            org.apache.logging.log4j.LogManager.getLogger(EditorHost.class)
                    .debug("JSON structured views: could not parse current text", e);
            jsonEditorContext = null;
            lastParsedJsonRevision = -1;
            failedJsonParseRevision = textRevision; // the same text fails again: do not re-parse it
            lastJsonParseError = e.getMessage();
        }
    }

    /** Serializes the shared JSON model back into the editor text via a minimal diff (caret/scroll preserved). */
    private void roundTripJsonModelToText() {
        if (jsonEditorContext == null) {
            return;
        }
        String json = jsonEditorContext.serializeToString();
        String current = view.getText();
        int[] region = TextDiff.minimalReplaceRegion(current, json);
        if (region[0] == region[1] && region[0] == region[2]) {
            lastParsedJsonRevision = textRevision;
            return;
        }
        view.replaceTextRegion(region[0], region[1], json.substring(region[0], region[2]));
        lastParsedJsonRevision = textRevision;
        document.setDirty(true);
    }

    /** Transient line-aware parse of the text at {@link #caretMapRevision}, used only to map a caret line to a node path. */
    private org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument caretMapDoc;

    /**
     * Resolves the model element at the current text caret and publishes it as the active XML
     * node, so the inspector shows it editable. The shared model's source line numbers go stale
     * after a round-trip reformats the text, so the caret line is mapped against a transient
     * line-aware parse of the CURRENT text (cached by text); the resulting structural index path
     * is then navigated in the shared model — which keeps node identity and undo history intact.
     * Falls back to no selection (the inspector's read-only caret/XPath view) when the document is
     * malformed or the caret is not inside an element. XML-instance files only.
     */
    void resolveCaretXmlNode() {
        EditorFileType type = document.getFileType();
        if (viewMode != ViewMode.TEXT || type == EditorFileType.XSD || !type.isXmlFamily()) {
            return; // plain text, HTML, XQuery, JSON …: not an XML instance, nothing to map
        }
        // Both parses (shared model, caret map) run off the FX thread; the mapping below is cheap.
        ensureXmlModelParsedAsync(() -> {
            if (failedXmlParseRevision == textRevision) {
                applyCaretXmlNode(); // malformed: no node; a caret-map parse would only fail again
                return;
            }
            ensureCaretMapAsync(this::applyCaretXmlNode);
        });
    }

    /**
     * Parses the line-aware {@link #caretMapDoc} for the current text on a worker and installs it on
     * the FX thread if the text is still unchanged, then runs {@code onCurrent}. Runs it at once when
     * the caret map is already current. FX thread.
     */
    private void ensureCaretMapAsync(Runnable onCurrent) {
        if (caretMapRevision == textRevision) {
            onCurrent.run();
            return;
        }
        long revision = textRevision;
        String text = view.getText();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument parsed;
            try {
                parsed = parseXmlDocument(text);
            } catch (Exception | LinkageError e) {
                parsed = null;
                org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.parse_xml");
            }
            var doc = parsed;
            Platform.runLater(() -> {
                if (isAbandoned(this) || textRevision != revision) {
                    return; // edited meanwhile: the caret debounce asks again
                }
                caretMapDoc = doc;
                caretMapRevision = revision;
                onCurrent.run();
            });
        });
    }

    /** FX-thread half of {@link #resolveCaretXmlNode()}: maps the caret line with current models. */
    private void applyCaretXmlNode() {
        if (viewMode != ViewMode.TEXT) {
            return;
        }
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode resolved = null;
        if (xmlEditorContext != null && xmlEditorContext.getDocument() != null
                && caretMapDoc != null && caretMapDoc.getRootElement() != null) {
            int line = view.getCodeArea().getCurrentParagraph() + 1;
            var target = findElementByLine(caretMapDoc.getRootElement(), line);
            java.util.List<Integer> path = indexPath(caretMapDoc.getRootElement(), target);
            resolved = navigate(xmlEditorContext.getDocument().getRootElement(), path);
        }
        currentXmlSelection = resolved;
        selectionCallback.run();
    }

    /**
     * @return the child-element index path from {@code root} to {@code target} (empty list if
     * {@code target == root}), or {@code null} if {@code target} is null or not under {@code root}.
     */
    private static java.util.List<Integer> indexPath(
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement root,
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement target) {
        if (target == null || root == null) {
            return null;
        }
        java.util.LinkedList<Integer> path = new java.util.LinkedList<>();
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement cur = target;
        while (cur != null && cur != root) {
            if (!(cur.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement pe)) {
                return null;
            }
            int idx = pe.getChildElements().indexOf(cur);
            if (idx < 0) {
                return null;
            }
            path.addFirst(idx);
            cur = pe;
        }
        return cur == root ? path : null;
    }

    /** Navigates the child-element index {@code path} from {@code root}; {@code null} if out of range. */
    private static org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement navigate(
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement root,
            java.util.List<Integer> path) {
        if (root == null || path == null) {
            return null;
        }
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement cur = root;
        for (int idx : path) {
            var kids = cur.getChildElements();
            if (idx < 0 || idx >= kids.size()) {
                return null;
            }
            cur = kids.get(idx);
        }
        return cur;
    }

    /**
     * XSD counterpart of {@link #resolveCaretXmlNode()}: resolves the {@link XsdNode} at the
     * current text caret and publishes it as the active selection, so the inspector shows it
     * editable in the Text view (Tree/Graphic already do). {@code XsdNode} carries no source line,
     * so the caret line is mapped against a transient line-aware parse of the CURRENT text (cached
     * by text), and the resulting structural index path is navigated in the shared XSD model —
     * keeping node identity and undo history intact. The XSD model is a 1:1 mirror of the
     * {@code xs:*} element tree except that {@code xs:annotation} is folded (no child node), so the
     * index path skips annotation elements on the source side and comment/annotation nodes on the
     * model side. Falls back to no selection (read-only caret/XPath) when malformed or off-element.
     */
    void resolveCaretXsdNode() {
        if (viewMode != ViewMode.TEXT || document.getFileType() != EditorFileType.XSD) {
            return;
        }
        // Both parses (XSD model, caret map) run off the FX thread; the mapping below is cheap.
        ensureModelParsedAsync(() -> ensureCaretMapAsync(this::applyCaretXsdNode));
    }

    /** FX-thread half of {@link #resolveCaretXsdNode()}: maps the caret line with current models. */
    private void applyCaretXsdNode() {
        if (viewMode != ViewMode.TEXT) {
            return;
        }
        XsdNode resolved = null;
        if (editorContext != null && editorContext.getSchema() != null
                && caretMapDoc != null && caretMapDoc.getRootElement() != null) {
            int line = view.getCodeArea().getCurrentParagraph() + 1;
            var target = findElementByLine(caretMapDoc.getRootElement(), line);
            java.util.List<Integer> path = xsdSourceIndexPath(caretMapDoc.getRootElement(), target);
            resolved = navigateXsd(editorContext.getSchema(), path);
        }
        currentSelection = resolved;
        selectionCallback.run();
    }

    /**
     * Child-index path from {@code root} to {@code target} counting only structural {@code xs:*}
     * elements (skipping {@code xs:annotation}/{@code documentation}/{@code appinfo}), so it aligns
     * with the XSD model's children. {@code null} if target is null/not under root or is itself an
     * annotation-family element (which has no model node).
     */
    private static java.util.List<Integer> xsdSourceIndexPath(
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement root,
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement target) {
        if (target == null || root == null) {
            return null;
        }
        java.util.LinkedList<Integer> path = new java.util.LinkedList<>();
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement cur = target;
        while (cur != null && cur != root) {
            if (!(cur.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement pe)) {
                return null;
            }
            int idx = -1;
            int i = 0;
            for (var child : pe.getChildElements()) {
                if (isXsdAnnotationLike(child)) {
                    continue;
                }
                if (child == cur) {
                    idx = i;
                    break;
                }
                i++;
            }
            if (idx < 0) {
                return null; // cur is an annotation-family element → no model node
            }
            path.addFirst(idx);
            cur = pe;
        }
        return cur == root ? path : null;
    }

    private static boolean isXsdAnnotationLike(
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e) {
        String n = e.getName();
        return "annotation".equals(n) || "documentation".equals(n) || "appinfo".equals(n);
    }

    /** Navigates the structural-child index {@code path} from {@code root}; {@code null} if out of range. */
    private static XsdNode navigateXsd(XsdNode root, java.util.List<Integer> path) {
        if (root == null || path == null) {
            return null;
        }
        XsdNode cur = root;
        for (int idx : path) {
            XsdNode next = null;
            int i = 0;
            for (XsdNode child : cur.getChildren()) {
                if (isNonStructuralXsd(child)) {
                    continue;
                }
                if (i == idx) {
                    next = child;
                    break;
                }
                i++;
            }
            if (next == null) {
                return null;
            }
            cur = next;
        }
        return cur;
    }

    /** Comment/annotation-family nodes are not counted when index-aligning with the source tree. */
    private static boolean isNonStructuralXsd(XsdNode n) {
        org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType t = n.getNodeType();
        return t == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.COMMENT
                || t == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.ANNOTATION
                || t == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.DOCUMENTATION
                || t == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.APPINFO;
    }

    /**
     * Finds the deepest element whose start tag begins on or before {@code line} (1-based) — the
     * element with the greatest {@code sourceLineNumber <= line}. Source line numbers are
     * populated by {@code StreamingXmlParser}. Returns {@code null} if none qualifies.
     */
    private static org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement findElementByLine(
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement root, int line) {
        if (root == null) {
            return null;
        }
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement[] best =
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement[1];
        collectBestByLine(root, line, best);
        return best[0];
    }

    private static void collectBestByLine(
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element, int line,
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement[] best) {
        int elementLine = element.getSourceLineNumber();
        if (elementLine > 0 && elementLine <= line
                && (best[0] == null || elementLine >= best[0].getSourceLineNumber())) {
            best[0] = element;
        }
        for (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode child : element.getChildren()) {
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement childElement) {
                collectBestByLine(childElement, line, best);
            }
        }
    }

    private void renderStructured() {
        // The Graphic view (Canvas XsdGraphView) is bound to the model at build
        // time and redraws itself from PropertyChangeEvents, so only the Tree
        // needs an explicit refresh here.
        if (viewMode == ViewMode.OVERVIEW && schemaOverview != null) {
            schemaOverview.setContext(editorContext);
            return;
        }
        if (viewMode != ViewMode.TREE || treeView == null) {
            return;
        }
        if (editorContext != null) {
            treeView.setSchema(editorContext.getSchema());
        } else {
            treeView.setXsdFromText(view.getText(), document.getPath());
        }
    }

    boolean executeAndApply(
            org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand command) {
        if (editorContext == null) {
            return false;
        }
        boolean ok = editorContext.getCommandManager().executeCommand(command);
        if (ok) {
            applyModelChange();
        }
        return ok;
    }

    /** Deletes the node via the command stack and round-trips the model to text. */
    boolean deleteNode(XsdNode node) {
        if (node == null || node.getParent() == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand(node));
    }

    boolean addElement(XsdNode parent, String name) {
        if (parent == null || name == null || name.isBlank()
                || !permits("Add Element", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.addElement(parent))) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddElementCommand(parent, name.trim()));
    }

    /**
     * Declares a new top-level type and opens it in its type editor tab.
     *
     * @return whether the type was added
     */
    boolean addGlobalType(org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand.Kind kind,
                          String name) {
        ensureModelParsed();
        if (editorContext == null || editorContext.getSchema() == null || kind == null
                || !permits("New Type", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules
                .addGlobalType(editorContext.getSchema(), name))) {
            return false;
        }
        String typeName = name.trim();
        boolean ok = executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand(
                editorContext.getSchema(), kind, typeName));
        if (ok && openTypeEditorCallback != null) {
            javafx.application.Platform.runLater(() -> openTypeEditorCallback.accept(typeName));
        }
        return ok;
    }

    /** Asks for kind and name of a new top-level type, then adds it ({@link #addGlobalType}). */
    boolean promptGlobalType(org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand.Kind kind) {
        ensureModelParsed();
        if (editorContext == null || editorContext.getSchema() == null) {
            return false;
        }
        return org.fxt.freexmltoolkit.controls.shell.schema.NewTypeDialog
                .ask(ruleWindow(), editorContext.getSchema(), kind)
                .map(answer -> addGlobalType(answer.kind(), answer.name()))
                .orElse(false);
    }

    boolean addComment(XsdNode parent, String content) {
        if (parent == null || content == null || content.isBlank()) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddCommentCommand(parent, content.trim()));
    }

    boolean addContainerElement(XsdNode parent, String name) {
        if (parent == null || name == null || name.isBlank()
                || !permits("Add Container Element", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.addElement(parent))) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddContainerElementCommand(
                        parent, name.trim()));
    }

    boolean renameNode(XsdNode node, String newName) {
        if (node == null || newName == null || newName.isBlank()) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.RenameNodeCommand(node, newName.trim()));
    }

    boolean changeCardinality(XsdNode node, int min, int max) {
        if (node == null || min < 0 || (max >= 0 && max < min)) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeCardinalityCommand(node, min, max));
    }

    boolean addElement(XsdNode parent, org.fxt.freexmltoolkit.controls.v2.editor.commands.NewElementSpec spec) {
        if (parent == null || spec == null
                || !permits("Add Element", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.newElement(parent, spec))) {
            return false;
        }
        return executeAndApply(org.fxt.freexmltoolkit.controls.v2.editor.commands.AddElementCommand.of(parent, spec));
    }

    boolean addAttribute(XsdNode parent, org.fxt.freexmltoolkit.controls.v2.editor.commands.NewAttributeSpec spec) {
        if (parent == null || spec == null
                || !permits("Add Attribute", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.newAttribute(parent, spec))) {
            return false;
        }
        return executeAndApply(org.fxt.freexmltoolkit.controls.v2.editor.commands.AddAttributeCommand.of(parent, spec));
    }

    boolean addAttribute(XsdNode parent, String name) {
        if (parent == null || name == null || name.isBlank()
                || !permits("Add Attribute", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.addAttribute(parent))) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddAttributeCommand(parent, name.trim()));
    }

    boolean addSequence(XsdNode node) {
        return addCompositor(node, "sequence");
    }

    boolean addChoice(XsdNode node) {
        return addCompositor(node, "choice");
    }

    boolean addAll(XsdNode node) {
        return addCompositor(node, "all");
    }

    /** Adds a compositor after checking the rules; a typed element may drop its type (confirmed). */
    private boolean addCompositor(XsdNode node, String kind) {
        if (!(node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element)) {
            return false;
        }
        var resolved = org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.resolve(ruleWindow(), "Add " + capitalize(kind),
                org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.addCompositor(element, kind));
        if (resolved.isEmpty()) {
            return false;
        }
        boolean replace = resolved.get();
        var command = switch (kind) {
            case "choice" -> org.fxt.freexmltoolkit.controls.v2.editor.commands.AddCompositorCommand.choice(element);
            case "all" -> org.fxt.freexmltoolkit.controls.v2.editor.commands.AddCompositorCommand.all(element);
            default -> org.fxt.freexmltoolkit.controls.v2.editor.commands.AddCompositorCommand.sequence(element);
        };
        return executeAndApply(replace ? command.replacingType() : command);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** @return whether an edit with this verdict may run as is (shows the reason when blocked). */
    private boolean permits(String title, org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck check) {
        if (check.isBlocked()) {
            org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.showBlocked(ruleWindow(), title, check);
            return false;
        }
        return true;
    }

    private javafx.stage.Window ruleWindow() {
        return getTabPane() != null && getTabPane().getScene() != null
                ? getTabPane().getScene().getWindow() : null;
    }

    boolean duplicate(XsdNode node) {
        if (node == null || node.getParent() == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.DuplicateNodeCommand(node));
    }

    /** Pastes the shell clipboard's node under {@code target} (cut originals are removed). */
    boolean pasteNode(XsdNode target) {
        if (target == null || !clipboard.hasContent()
                || !permits("Paste", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.paste(target, clipboard.getClipboardNode()))) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.PasteNodeCommand(clipboard, target));
    }

    /** Reorders a node among its siblings by {@code delta} (-1 = up, +1 = down). */
    boolean moveNode(XsdNode node, int delta) {
        if (node == null || node.getParent() == null) {
            return false;
        }
        XsdNode parent = node.getParent();
        int index = parent.getChildren().indexOf(node);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= parent.getChildren().size()) {
            return false; // already at the boundary
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.MoveNodeCommand(node, parent, target));
    }

    boolean changeType(XsdNode node, String newType) {
        if (editorContext == null || node == null || newType == null || newType.isBlank()) {
            return false;
        }
        String type = newType.trim();
        var resolved = org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.resolve(ruleWindow(), "Change Type",
                org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.changeType(node, type));
        return resolved.isPresent() && executeAndApply(resolved.get()
                ? org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeTypeCommand.replacingInlineType(editorContext, node, type)
                : new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeTypeCommand(editorContext, node, type));
    }

    /** @return whether {@code target} accepts the clipboard's node (for the context menu's Paste). */
    boolean canPasteInto(XsdNode target) {
        return clipboard.hasContent()
                && !org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.paste(target, clipboard.getClipboardNode()).isBlocked();
    }

    boolean changeUse(XsdNode node, String use) {
        if (editorContext == null || node == null || use == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeUseCommand(
                editorContext, node, use));
    }

    boolean changeForm(XsdNode node, String form) {
        if (editorContext == null || node == null || form == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFormCommand(
                editorContext, node, form));
    }

    boolean changeConstraints(XsdNode node, boolean nillable, boolean abstractFlag, String fixed) {
        if (editorContext == null || node == null) {
            return false;
        }
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
            // The Inspector shows "fixed" for attributes too; nillable/abstract do not apply there.
            // Leaving the field unchanged must not add an undo step.
            return java.util.Objects.equals(attribute.getFixed(),
                    org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFixedValueCommand.normalize(fixed))
                    || executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFixedValueCommand(
                            editorContext, node, fixed));
        }
        return node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement
                && executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeConstraintsCommand(
                        editorContext, node, nillable, abstractFlag, fixed));
    }

    boolean changeSubstitutionGroup(XsdNode node, String substitutionGroup) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeSubstitutionGroupCommand(
                        editorContext, node, substitutionGroup));
    }

    boolean changeDefaultValue(XsdNode node, String defaultValue) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeDefaultValueCommand(
                editorContext, node, defaultValue));
    }

    boolean changeBlock(XsdNode node, String block) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeBlockCommand(
                editorContext, node, block));
    }

    boolean changeFinal(XsdNode node, String finalValue) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFinalCommand(
                editorContext, node, finalValue));
    }

    boolean changeMixed(XsdNode node, boolean mixed) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeMixedCommand(
                editorContext, node, mixed));
    }

    boolean changeRef(XsdNode node, String ref) {
        if (editorContext == null || node == null || !permits("Change Reference", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.changeRef(node, ref))) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeRefCommand(
                editorContext, node, ref));
    }

    boolean changeWildcard(XsdNode node, String namespace,
            org.fxt.freexmltoolkit.controls.v2.model.XsdAny.ProcessContents processContents) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeWildcardCommand(
                editorContext, node, namespace, processContents));
    }

    boolean changeSchemaProperty(XsdNode node,
            org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeSchemaPropertyCommand.Property property,
            String value) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeSchemaPropertyCommand(
                editorContext, node, property, value));
    }

    boolean addIdentityConstraint(XsdNode node,
            org.fxt.freexmltoolkit.controls.v2.editor.commands.AddIdentityConstraintCommand.Kind kind,
            String name, String selectorXpath, java.util.List<String> fieldXpaths, String refer) {
        if (editorContext == null || node == null) {
            return false;
        }
        try {
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddIdentityConstraintCommand(
                            editorContext, node, kind, name, selectorXpath, fieldXpaths, refer));
        } catch (IllegalArgumentException e) {
            return false; // invalid input (missing name/selector/field) — ignored
        }
    }

    boolean addSchemaReference(XsdNode node,
            org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSchemaReferenceCommand.Kind kind,
            String namespace, String schemaLocation) {
        if (editorContext == null || node == null) {
            return false;
        }
        try {
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSchemaReferenceCommand(
                            editorContext, node, kind, namespace, schemaLocation));
        } catch (IllegalArgumentException e) {
            return false; // invalid input (missing schema location) — ignored
        }
    }

    boolean changeDocumentation(XsdNode node, String documentation) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeDocumentationCommand(
                        editorContext, node, documentation));
    }

    boolean changeAppinfo(XsdNode node, String appinfo) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeAppinfoCommand(
                        editorContext, node, appinfo));
    }

    boolean changeAppinfo(XsdNode node, org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo appinfo) {
        if (editorContext == null || node == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeAppinfoCommand(
                        editorContext, node, appinfo));
    }

    boolean changeDocumentations(XsdNode node,
            java.util.List<org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation> docs) {
        if (editorContext == null || node == null || docs == null) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeDocumentationsCommand(
                        editorContext, node, docs));
    }

    boolean editComment(XsdNode node, String content) {
        if (editorContext == null
                || !(node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComment comment)) {
            return false;
        }
        return executeAndApply(
                new org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCommentCommand(comment, content));
    }


    boolean editFacet(org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet, String newValue) {
        if (editorContext == null || facet == null || newValue == null) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.EditFacetCommand(
                facet, newValue));
    }

    boolean deleteFacet(org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet) {
        if (editorContext == null || facet == null
                || !(facet.getParent()
                        instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction)) {
            return false;
        }
        return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteFacetCommand(
                restriction, facet));
    }

    boolean addFacet(org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType type, String value) {
        return addFacet(currentSelection, type, value);
    }

    /** Adds a facet to {@code node}'s restriction (pattern/enumeration/assertion create one). */
    boolean addFacet(XsdNode node, org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType type, String value) {
        if (editorContext == null || node == null || type == null || value == null) {
            return false;
        }
        XsdNode currentSelection = node;
        org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction =
                org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets.findRestriction(currentSelection);
        if (restriction != null) {
            return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddFacetCommand(
                    restriction, type, value));
        }
        // No restriction yet: pattern/enumeration/assertion commands create one from the node.
        return switch (type) {
            case PATTERN -> executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddPatternCommand(
                            editorContext, currentSelection, value));
            case ENUMERATION -> executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddEnumerationCommand(
                            editorContext, currentSelection, value));
            case ASSERTION -> executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddAssertionCommand(
                            editorContext, currentSelection, value));
            default -> false;
        };
    }

    /** Restores selection to {@code node} after a property edit (re-selects it in the view). */
    void reselect(XsdNode node) {
        currentSelection = node;
        if (viewMode == ViewMode.TREE && treeView != null) {
            treeView.selectNode(node);
        } else if (viewMode == ViewMode.GRAPHIC && xsdGraphView != null) {
            xsdGraphView.selectModelNode(node);
        } else if (viewMode == ViewMode.OVERVIEW && schemaOverview != null) {
            schemaOverview.select(node);
        }
    }

    boolean undoStructured() {
        if (editorContext != null && editorContext.getCommandManager().undo()) {
            applyModelChange();
            return true;
        }
        return false;
    }

    /** Undo on the shared XML-instance context; round-trips the reverted model to text. */
    boolean undoXml() {
        if (xmlEditorContext != null && xmlEditorContext.undo()) {
            currentXmlSelection = null;
            roundTripXmlModelToText();
            return true;
        }
        return false;
    }

    /** Redo on the shared XML-instance context; round-trips the re-applied model to text. */
    boolean redoXml() {
        if (xmlEditorContext != null && xmlEditorContext.redo()) {
            currentXmlSelection = null;
            roundTripXmlModelToText();
            return true;
        }
        return false;
    }

    /** Undo on the shared JSON context; round-trips the reverted model to text. */
    boolean undoJson() {
        if (jsonEditorContext != null && jsonEditorContext.undo()) {
            currentJsonSelection = null;
            roundTripJsonModelToText();
            return true;
        }
        return false;
    }

    /** Redo on the shared JSON context; round-trips the re-applied model to text. */
    boolean redoJson() {
        if (jsonEditorContext != null && jsonEditorContext.redo()) {
            currentJsonSelection = null;
            roundTripJsonModelToText();
            return true;
        }
        return false;
    }

    /** Executes a JSON command on the shared context and round-trips to text. */
    boolean editJson(org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand command) {
        if (command == null) {
            return false;
        }
        ensureJsonModelParsed();
        if (jsonEditorContext == null || !jsonEditorContext.executeCommand(command)) {
            return false;
        }
        roundTripJsonModelToText();
        return true;
    }

    boolean redoStructured() {
        if (editorContext != null && editorContext.getCommandManager().redo()) {
            applyModelChange();
            return true;
        }
        return false;
    }

    private void applyModelChange() {
        roundTripModelToText();
        currentSelection = null;
        renderStructured();
    }

    /** Serializes the current model back into the editor text and marks it dirty. */
    private void roundTripModelToText() {
        if (editorContext == null) {
            return;
        }
        roundTripDebounce.stop(); // we are doing the work now; cancel any pending run
        var serializer = new org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer();
        // Keep the xs:include/xs:import directives but never write back the inlined external
        // content, so editing the main schema does not flatten its includes.
        serializer.setExcludeIncludedNodes(true);
        String xml = serializer.serialize(editorContext.getSchema());
        String current = view.getText();
        // Thin diff layer (P6): rewrite only the region that actually changed, instead of
        // replacing the whole document — preserves the editor caret/scroll and avoids
        // re-styling untouched text.
        int[] region = TextDiff.minimalReplaceRegion(current, xml);
        if (region[0] == region[1] && region[0] == region[2]) {
            lastParsedText = current; // already in sync; nothing to write
            lastParsedRevision = textRevision;
            return;
        }
        view.replaceTextRegion(region[0], region[1], xml.substring(region[0], region[2]));
        // The text now mirrors the model, so it must not look like an external edit (P2).
        lastParsedText = view.getText();
        lastParsedRevision = textRevision;
        document.setDirty(true);
    }

    /** Coalesces a burst of model-change events into a single debounced round-trip (P1). */
    void scheduleRoundTrip() {
        roundTripDebounce.playFromStart();
    }

    /** Runs a pending debounced round-trip immediately, if one is scheduled (P1). */
    private void flushPendingRoundTrip() {
        if (roundTripDebounce.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            roundTripModelToText();
        }
    }

    private void ensureTree() {
        if (treeView == null) {
            treeView = new org.fxt.freexmltoolkit.controls.shell.schema.XsdTreeView();
            treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                currentSelection = newV != null ? newV.getValue() : null;
                selectionCallback.run();
            });
            treeView.setEditActions(createEditActions());
            contentStack.getChildren().add(treeView);
        }
    }

    /**
     * (Re)builds the Canvas-based {@link org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView}
     * editor over the current {@link #editorContext}, sharing its command stack and
     * selection so graphical edits, inspector edits and undo/redo are unified. The view
     * is rebuilt whenever the model is re-parsed (a new context) and reused otherwise.
     */
    private void ensureCanvasGraphic() {
        if (editorContext == null) {
            return;
        }
        if (xsdGraphView != null && xsdGraphView.getEditorContext() == editorContext) {
            return; // already bound to the current model
        }
        org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView previous = xsdGraphView;
        if (previous != null) {
            contentStack.getChildren().remove(previous);
        }
        editorContext.setEditMode(true);
        org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView built =
                new org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView(editorContext);
        // The shell owns the inspector (right dock), so suppress the view's own
        // embedded properties panel to avoid a duplicate, overlapping panel.
        built.hideEmbeddedPropertiesPanel();
        // Match the Figma "Schema (graphical)" frame: no bulky second toolbar — just a
        // breadcrumb top-left and a small zoom pill at the bottom (the shell provides
        // the editor toolbar above).
        built.useMinimalChrome();
        // Wire the graphic view's "Open in Type Editor" context-menu actions to the shell's
        // type-editor tabs (named types only; anonymous inline types have no tab target).
        built.setOpenComplexTypeEditorCallback(ct -> openTypeEditorCallback.accept(ct.getName()));
        built.setOpenSimpleTypeEditorCallback(st -> openTypeEditorCallback.accept(st.getName()));
        built.getSelectionModel().addSelectionListener((oldSel, newSel) -> {
            var primary = built.getSelectionModel().getPrimarySelection();
            currentSelection = primary != null
                    && primary.getModelObject() instanceof XsdNode node ? node : null;
            selectionCallback.run();
        });
        // Round-trip graphical/internal edits (drag, right-click, keyboard) to the
        // text, coalescing bursts of model events into a single debounced run (P1).
        editorContext.getSchema().addPropertyChangeListener(evt -> {
            if (viewMode == ViewMode.GRAPHIC) {
                scheduleRoundTrip();
            }
        });
        xsdGraphView = built;
        contentStack.getChildren().add(xsdGraphView);
        if (previous != null) {
            built.adoptViewState(previous);
        }
    }

    /** The XSD Overview view (XMLSpy-style component table), built on first use. */
    org.fxt.freexmltoolkit.controls.shell.schema.overview.SchemaOverviewView schemaOverview;
    /** Host callback: switch to the Graphic view and select the node there. */
    java.util.function.Consumer<XsdNode> showInGraphicCallback = node -> { };
    /** Host callback: open a file in its own tab. */
    java.util.function.Consumer<Path> openFileCallback = path -> { };

    /**
     * Builds the Overview view on first use and binds it to the current model. Its edits run on
     * the shared {@link #editorContext}, so undo history is shared with Tree and Graphic.
     */
    private void ensureOverview() {
        if (editorContext == null) {
            return;
        }
        if (schemaOverview == null) {
            schemaOverview = new org.fxt.freexmltoolkit.controls.shell.schema.overview.SchemaOverviewView(
                    new org.fxt.freexmltoolkit.controls.shell.schema.overview.SchemaOverviewHost() {
                        @Override
                        public boolean execute(org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand command) {
                            return executeAndApply(command);
                        }

                        @Override
                        public void selectionChanged(XsdNode node) {
                            currentSelection = node;
                            selectionCallback.run();
                        }

                        @Override
                        public void openTypeEditor(String typeName) {
                            if (typeName != null && !typeName.isBlank()) {
                                openTypeEditorCallback.accept(typeName);
                            }
                        }

                        @Override
                        public void showInGraphic(XsdNode node) {
                            showInGraphicCallback.accept(node);
                        }

                        @Override
                        public void openReferencedFile(
                                org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaReference reference) {
                            Path target = referencedFile(reference);
                            if (target != null) {
                                openFileCallback.accept(target);
                            }
                        }

                        @Override
                        public Path documentPath() {
                            return document.getPath();
                        }

                        @Override
                        public javafx.stage.Window window() {
                            return ruleWindow();
                        }
                    });
            contentStack.getChildren().add(schemaOverview);
        }
        schemaOverview.setContext(editorContext);
    }

    /** @return the local file an import/include points to (resolved, or relative to this document), or null */
    private Path referencedFile(org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaReference reference) {
        if (reference.getResolvedPath() != null) {
            return reference.getResolvedPath();
        }
        String location = reference.getSchemaLocation();
        if (location == null || location.isBlank() || location.matches("(?i)https?:.*")) {
            return null;
        }
        try {
            Path base = document.getPath() != null ? document.getPath().getParent() : null;
            Path target = location.startsWith("file:") ? Path.of(java.net.URI.create(location))
                    : base != null ? base.resolve(location).normalize() : Path.of(location);
            return java.nio.file.Files.isRegularFile(target) ? target : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Editing actions shared by the Tree and Graphic views: run a command, then refresh selection. */
    private org.fxt.freexmltoolkit.controls.shell.schema.NodeEditActions createEditActions() {
        return new org.fxt.freexmltoolkit.controls.shell.schema.NodeEditActions() {
            @Override
            public void addElement(XsdNode parent, String name) {
                if (EditorTab.this.addElement(parent, name)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void newType(org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand.Kind kind) {
                if (EditorTab.this.promptGlobalType(kind)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addComment(XsdNode parent, String content) {
                if (EditorTab.this.addComment(parent, content)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addContainerElement(XsdNode parent, String name) {
                if (EditorTab.this.addContainerElement(parent, name)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addAttribute(XsdNode parent, String name) {
                if (EditorTab.this.addAttribute(parent, name)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addElement(XsdNode parent, org.fxt.freexmltoolkit.controls.v2.editor.commands.NewElementSpec spec) {
                if (EditorTab.this.addElement(parent, spec)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addAttribute(XsdNode parent, org.fxt.freexmltoolkit.controls.v2.editor.commands.NewAttributeSpec spec) {
                if (EditorTab.this.addAttribute(parent, spec)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addSequence(XsdNode element) {
                if (EditorTab.this.addSequence(element)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addChoice(XsdNode element) {
                if (EditorTab.this.addChoice(element)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void addAll(XsdNode element) {
                if (EditorTab.this.addAll(element)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void duplicate(XsdNode node) {
                if (EditorTab.this.duplicate(node)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void moveUp(XsdNode node) {
                if (EditorTab.this.moveNode(node, -1)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void moveDown(XsdNode node) {
                if (EditorTab.this.moveNode(node, 1)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void copy(XsdNode node) {
                if (node != null) {
                    clipboard.copy(node);
                }
            }

            @Override
            public void cut(XsdNode node) {
                if (node != null) {
                    clipboard.cut(node);
                }
            }

            @Override
            public void paste(XsdNode target) {
                if (EditorTab.this.pasteNode(target)) {
                    selectionCallback.run();
                }
            }

            @Override
            public boolean canPaste() {
                return clipboard.hasContent();
            }

            @Override
            public boolean canPasteInto(XsdNode target) {
                return EditorTab.this.canPasteInto(target);
            }

            @Override
            public void rename(XsdNode node, String newName) {
                if (EditorTab.this.renameNode(node, newName)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void changeType(XsdNode node, String newType) {
                if (EditorTab.this.changeType(node, newType)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void changeCardinality(XsdNode node, int min, int max) {
                if (EditorTab.this.changeCardinality(node, min, max)) {
                    selectionCallback.run();
                }
            }

            @Override
            public void delete(XsdNode node) {
                if (EditorTab.this.deleteNode(node)) {
                    selectionCallback.run();
                }
            }
        };
    }

    private void ensureJsonTree() {
        if (jsonTreeView == null) {
            jsonTreeView = new org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView();
            jsonTreeView.setOnSelectionChanged(node -> {
                currentJsonSelection = node;
                selectionCallback.run();
            });
            contentStack.getChildren().add(jsonTreeView);
        }
    }

    private void ensureXmlGrid() {
        if (xmlGridView == null) {
            xmlGridView = new XmlGridView();
            // Grid edits mutate the shared context directly; round-trip it via a minimal diff.
            xmlGridView.setOnModified(xml -> roundTripXmlModelToText());
            xmlGridView.setOnSelectionChanged(node -> {
                currentXmlSelection = node;
                selectionCallback.run();
            });
            contentStack.getChildren().add(xmlGridView);
        }
    }

    private void ensureJsonGrid() {
        if (jsonGridView == null) {
            jsonGridView = new JsonGridView();
            // Grid edits mutate the shared context directly; round-trip it via a minimal diff.
            jsonGridView.setOnModified(json -> roundTripJsonModelToText());
            jsonGridView.setOnSelectionChanged(node -> {
                currentJsonSelection = node;
                selectionCallback.run();
            });
            contentStack.getChildren().add(jsonGridView);
        }
    }

    /** Executes an XML-instance command on the shared context and round-trips to text. */
    boolean editXml(org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand command) {
        if (command == null) {
            return false;
        }
        ensureXmlModelParsed();
        if (xmlEditorContext == null || !xmlEditorContext.executeCommand(command)) {
            return false;
        }
        roundTripXmlModelToText();
        return true;
    }

    private void ensureHtmlPreview() {
        if (htmlPreviewNode != null) {
            return;
        }
        try {
            javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
            htmlPreviewUpdater = content ->
                    webView.getEngine().loadContent(content == null ? "" : content);
            htmlPreviewNode = webView;
        } catch (Throwable t) {
            // javafx.scene.web missing from the runtime: degrade to read-only text
            // (same guard as TransformOutputPanel.updateHtmlPreview).
            javafx.scene.control.TextArea fallback = new javafx.scene.control.TextArea();
            fallback.setEditable(false);
            htmlPreviewUpdater = fallback::setText;
            htmlPreviewNode = fallback;
        }
        contentStack.getChildren().add(htmlPreviewNode);
    }

    /** Re-renders the preview if the text changed since it was last rendered. */
    private void refreshHtmlPreview() {
        String text = view.getText();
        if (!java.util.Objects.equals(text, lastPreviewedText)) {
            htmlPreviewUpdater.accept(text);
            lastPreviewedText = text;
        }
    }

    /** Re-renders the preview after an external text update (async load, re-run transform). */
    void refreshPreviewIfActive() {
        if (viewMode == ViewMode.PREVIEW && htmlPreviewNode != null) {
            refreshHtmlPreview();
        }
    }

    private void ensureXmlInstanceTree() {
        if (xmlInstanceTreeView == null) {
            xmlInstanceTreeView = new org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView();
            xmlInstanceTreeView.setOnSelectionChanged(node -> {
                currentXmlSelection = node;
                selectionCallback.run();
            });
            xmlInstanceTreeView.setOnGoToDefinition(goToDefinitionCallback);
            contentStack.getChildren().add(xmlInstanceTreeView);
        }
    }

    /** Centered ProgressIndicator + "Loading …" label on a dimmed backdrop; hidden by default. */
    private static javafx.scene.layout.StackPane buildLoadingOverlay() {
        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setMaxSize(48, 48);
        javafx.scene.control.Label label = new javafx.scene.control.Label("Loading …");
        label.getStyleClass().add("fxt-editor-loading-label");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, spinner, label);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane(box);
        overlay.getStyleClass().add("fxt-editor-loading-overlay");
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }

    /** Arm the delayed spinner; it appears only if loading outlasts {@link #loadDelay}. */
    void beginLoading() {
        loadDelay.playFromStart();
    }

    /** Cancel a pending spinner and hide the overlay (loading finished or failed). */
    void endLoading() {
        loadDelay.stop();
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);
    }

    private void showOnly(javafx.scene.Node node) {
        for (javafx.scene.Node child : contentStack.getChildren()) {
            boolean visible = child == node;
            child.setVisible(visible);
            child.setManaged(visible);
        }
    }

    /**
     * Replaces the editor text with content re-read from disk: pending model edits are
     * dropped rather than written back over the new text, the caret stays where it was
     * (clamped), and a visible Tree/Graphic/Preview view is rebuilt from the new text.
     */
    void replaceContentFromDisk(String content) {
        roundTripDebounce.stop();
        int caret = view.getCodeArea().getCaretPosition();
        view.setText(content);
        view.getCodeArea().moveTo(Math.min(caret, view.getCodeArea().getLength()));
        view.getCodeArea().requestFollowCaret();
        lastParsedText = null;
        lastParsedRevision = -1;
        lastParsedXmlRevision = -1;
        lastParsedJsonRevision = -1;
        caretMapRevision = -1;
        if (viewMode != ViewMode.TEXT) {
            setViewMode(viewMode);
        }
        refreshPreviewIfActive();
    }

    void attachDirtyTracking() {
        if (dirtyTrackingAttached) {
            return;
        }
        dirtyTrackingAttached = true;
        // plainTextChanges, not textProperty: a ChangeListener there builds the whole text per keystroke
        view.getCodeArea().plainTextChanges().subscribe(change -> {
            if (!change.getInserted().equals(change.getRemoved())) {
                document.setDirty(true);
            }
        });
    }

    void refreshIcon() {
        setGraphic(new IconifyIcon(document.getFileType().icon()));
    }

    /**
     * Cooperative cancellation for a tab's background schema work.
     * <p>
     * Cancellation is cooperative by design: a detection already inside a blocking call still
     * runs to that call's own timeout - interrupting Xerces or a socket read mid-flight is not
     * safe - but nothing further is started, and no result is applied.
     *
     * @param tab the tab whose background work is checking in
     * @return true when that work should stop: the tab was closed, or the application is
     *         shutting down
     */
    static boolean isAbandoned(EditorTab tab) {
        return tab.closed.get() || org.fxt.freexmltoolkit.FxtGui.executorService.isShutdown();
    }
}
