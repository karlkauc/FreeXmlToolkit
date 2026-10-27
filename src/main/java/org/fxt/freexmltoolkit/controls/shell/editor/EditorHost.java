package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.telemetry.DocKind;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The file-type-aware center of the Unified shell: a tab pane of open documents.
 * Each tab embeds an {@link EditorView} chosen by file type (XML family vs JSON),
 * reusing the existing editor widgets. File reads run on the shared executor so
 * the UI thread is never blocked; the active document, caret and schema are
 * observable for the Explorer panel, inspector and status bar.
 */
public class EditorHost extends BorderPane {

    private static final Logger logger = LogManager.getLogger(EditorHost.class);
    private final TabPane tabPane = new TabPane();
    /** Tab pane plus an overlaid view-mode switch pinned to the top-right of the tab header. */
    private final javafx.scene.layout.StackPane tabArea = new javafx.scene.layout.StackPane();
    /** The Text/Tree/Graphic view-mode switch; placed in the editor toolbar by the shell. */
    private javafx.scene.layout.Region viewSwitch;
    private final ObservableList<OpenDocument> openDocuments = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper activeCaret = new ReadOnlyIntegerWrapper(this, "activeCaret", 0);
    private final ReadOnlyIntegerWrapper schemaImportsRevision =
            new ReadOnlyIntegerWrapper(this, "schemaImportsRevision", 0);
    private final ReadOnlyIntegerWrapper xmlSchemaProviderRevision =
            new ReadOnlyIntegerWrapper(this, "xmlSchemaProviderRevision", 0);
    private final ReadOnlyIntegerWrapper activeModelRevision =
            new ReadOnlyIntegerWrapper(this, "activeModelRevision", 0);
    private final ReadOnlyObjectWrapper<File> activeSchema = new ReadOnlyObjectWrapper<>(this, "activeSchema", null);
    private final ReadOnlyObjectWrapper<SchemaStatus> activeSchemaStatus =
            new ReadOnlyObjectWrapper<>(this, "activeSchemaStatus", SchemaStatus.NONE);
    private final ReadOnlyObjectWrapper<SchemaSource> activeSchemaSource =
            new ReadOnlyObjectWrapper<>(this, "activeSchemaSource", SchemaSource.NONE);
    private final javafx.beans.property.ReadOnlyStringWrapper activeSchemaSourceDetail =
            new javafx.beans.property.ReadOnlyStringWrapper(this, "activeSchemaSourceDetail", null);
    private final ReadOnlyObjectWrapper<ViewMode> activeViewMode =
            new ReadOnlyObjectWrapper<>(this, "activeViewMode", ViewMode.TEXT);
    private final ReadOnlyObjectWrapper<XsdNode> activeSelectedNode =
            new ReadOnlyObjectWrapper<>(this, "activeSelectedNode", null);
    private final ReadOnlyObjectWrapper<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode> activeXmlNode =
            new ReadOnlyObjectWrapper<>(this, "activeXmlNode", null);
    private final ReadOnlyObjectWrapper<org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode> activeJsonNode =
            new ReadOnlyObjectWrapper<>(this, "activeJsonNode", null);
    private final ReadOnlyObjectWrapper<ValidationStatus> validationStatus =
            new ReadOnlyObjectWrapper<>(this, "validationStatus", ValidationStatus.NONE);
    private final ObservableList<ValidationProblem> activeProblems = FXCollections.observableArrayList();

    /** Coarse validation outcome of the active document, surfaced in the inspector. */
    public enum ValidationState { NOT_VALIDATED, VALID, INVALID }

    /**
     * The active document's validation result.
     *
     * @param state        valid / invalid / not-yet-validated
     * @param problemCount the number of problems (0 when valid)
     * @param summary      a short human-readable label (e.g. "Valid", "Well-formed", "3 problems")
     */
    public record ValidationStatus(ValidationState state, int problemCount, String summary) {
        public static final ValidationStatus NONE =
                new ValidationStatus(ValidationState.NOT_VALIDATED, 0, "Not validated");
    }

    /**
     * Lifecycle of the active document's XSD binding, surfaced in the status bar so the user
     * can tell whether schema-aware IntelliSense is available yet.
     * <ul>
     *   <li>{@link #NONE} — no schema linked (or detection found no reference)</li>
     *   <li>{@link #LOADING} — the linked XSD is being detected/parsed in the background</li>
     *   <li>{@link #READY} — schema parsed; IntelliSense is available</li>
     *   <li>{@link #ERROR} — a schema reference exists but the XSD could not be loaded</li>
     * </ul>
     */
    public enum SchemaStatus { NONE, LOADING, READY, ERROR }

    /**
     * How the active document's schema binding was established, surfaced in the status bar
     * next to {@link SchemaStatus#READY} so a catalog- or library-resolved schema is
     * distinguishable from one the document declared itself or the user picked by hand.
     * <ul>
     *   <li>{@link #NONE} — no binding</li>
     *   <li>{@link #DECLARED} — loaded from the location the document itself declared
     *       ({@code xsi:schemaLocation} / JSON {@code $schema})</li>
     *   <li>{@link #CATALOG} — resolved through a registered XML catalog entry
     *       ({@code system}/{@code rewriteSystem}/{@code public}/{@code uri})</li>
     *   <li>{@link #LIBRARY} — resolved through a Schema Library mapping (user or bundled
     *       entry), by namespace or root element</li>
     *   <li>{@link #MANUAL} — bound by hand (Validation panel "Change", drag-and-drop,
     *       {@link #setSchemaForActiveDocument})</li>
     * </ul>
     */
    public enum SchemaSource { NONE, DECLARED, LIBRARY, CATALOG, MANUAL }

    /**
     * Outcome of a schema auto-detection run: the bound XSD (or null) plus the terminal
     * status, how the binding was resolved, and an optional human-readable detail
     * (catalog target / namespace / root element) for the status bar tooltip. A failed
     * detection keeps the source that was tried and why it failed (anonymous telemetry).
     */
    private record SchemaDetection(File schema, SchemaStatus status, SchemaSource source, String sourceDetail,
                                   String failure, String refKind) {
        static final SchemaDetection NOT_FOUND = new SchemaDetection(null, SchemaStatus.NONE, SchemaSource.NONE, null);
        /** {@link #failure} value: the referenced schema could not be resolved / reached. */
        static final String UNRESOLVED = "unresolved";
        /** {@link #failure} value: the schema file was found but is missing or not loadable. */
        static final String UNLOADABLE = "unloadable";

        SchemaDetection(File schema, SchemaStatus status, SchemaSource source, String sourceDetail) {
            this(schema, status, source, sourceDetail, null, null);
        }

        static SchemaDetection failed(SchemaSource source, String failure) {
            return failed(source, failure, null);
        }

        /** @param location the declared location, reduced to its {@link UsageEvents#referenceKind kind} */
        static SchemaDetection failed(SchemaSource source, String failure, String location) {
            return new SchemaDetection(null, SchemaStatus.ERROR, source, null, failure,
                    UsageEvents.referenceKind(location));
        }
    }

    /** A Schema Library / catalog resolution result, carrying provenance for the status bar. */
    private record LibraryHit(File file, SchemaSource source, String detail) { }
    /** The most recently active editor tab — insertion target when a tool tab is in front. */
    private EditorTab lastEditorTab;

    /** The most recently active XML-family tab — the run target for XPath/XQuery documents. */
    /** Most-recently-active-first history of XML-family tabs (query/transform target resolution). */
    private final java.util.ArrayDeque<EditorTab> xmlFamilyMru = new java.util.ArrayDeque<>();

    /**
     * Per-document explicit run target for query/XSLT documents (session-only; a missing
     * entry means {@link QueryTarget#AUTOMATIC}). OpenDocument identity equality applies.
     */
    private final java.util.Map<OpenDocument, QueryTarget> queryTargets = new java.util.HashMap<>();

    /** Handler running the active XPath/XQuery document (wired by the shell, fired on Ctrl+Enter). */
    private Runnable queryRunHandler;

    /** Handler for Welcome/Dashboard action keys (activity ids, open-folder); set by the shell. */
    private java.util.function.Consumer<String> welcomeActionHandler = key -> { };

    /** Called on the FX thread with each document read from disk and its content (e.g. the FundsXML hint). */
    private java.util.function.BiConsumer<OpenDocument, String> documentLoadedHandler = (doc, content) -> { };

    /** Optional override for "new document" requests (e.g. the shell's guided New File dialog). */
    private Runnable newDocumentOverride;

    private final EditorWelcomePane welcomePane = new EditorWelcomePane(
            this::requestNewDocument, this::openFileChooser, this::openFile,
            this::clearRecentFiles, this::fireWelcomeAction);

    /** Watches the open documents' files for changes made by other programs. */
    private final ExternalChangeMonitor externalChangeMonitor = new ExternalChangeMonitor(
            this::watchedDocuments, this::handleExternalChanges,
            task -> org.fxt.freexmltoolkit.FxtGui.executorService.execute(task));

    /** Asks what to do about an externally changed/deleted file; replaceable in tests. */
    private ExternalChangePrompt externalChangePrompt =
            ExternalChangePrompt.dialogs(() -> getScene() != null ? getScene().getWindow() : null);

    public EditorHost() {
        getStyleClass().add("fxt-editor-tabs");
        // The style class on the TabPane itself (not this BorderPane) so CSS can target the
        // tab header / tabs directly.
        tabPane.getStyleClass().add("fxt-editor-tabpane");
        // Cap each tab's width relative to the available tab-pane width: narrow screens (e.g. a
        // 1512px MacBook) stay compact, while wide screens (1920px+) show fuller titles before
        // ellipsizing. Clamped so tabs never get tiny or absurdly wide.
        tabPane.tabMaxWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(170.0, Math.min(340.0, tabPane.getWidth() * 0.22)),
                tabPane.widthProperty()));

        // The view-mode switch (Text/Tree/Graphic) is built here but placed by the shell at the
        // right end of the full-width editor toolbar (Figma "future" layout) via getViewSwitch();
        // the tab header therefore only carries the tabs.
        viewSwitch = buildViewSwitch();
        tabArea.getChildren().setAll(tabPane);

        setupDragAndDrop();
        externalChangeMonitor.attachTo(this);
        // Show the welcome empty-state while no document is open; swap to the tab
        // pane as soon as one opens, and back again when the last tab closes.
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> {
            // Abandon a removed tab's background schema work here rather than in setOnClosed:
            // this fires however the tab goes away, including programmatic removal.
            while (c.next()) {
                for (Tab removed : c.getRemoved()) {
                    if (removed instanceof EditorTab et) {
                        et.closed.set(true);
                    }
                }
            }
            UsageEvents.openTabsChanged(tabPane.getTabs().size());
            updateCenter();
        });
        updateCenter();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT instanceof EditorTab et) {
                lastEditorTab = et;
                if (isXmlFamily(et.document.getFileType())) {
                    xmlFamilyMru.remove(et);
                    xmlFamilyMru.addFirst(et);
                }
                activeCaret.set(et.view.getCodeArea().getCaretPosition());
                activeSchema.set(et.schemaFile);
                activeSchemaStatus.set(et.schemaStatus);
                activeSchemaSource.set(et.schemaSource);
                activeSchemaSourceDetail.set(et.schemaSourceDetail);
                activeViewMode.set(et.viewMode);
                UsageEvents.activeDocumentChanged(et.document, et.document.getFileType().docKind(),
                        et.viewMode != null ? et.viewMode.name().toLowerCase(java.util.Locale.ROOT) : null,
                        docShapeOf(et.view.getCodeArea()), docLengthOf(et.view.getCodeArea()));
            } else {
                UsageEvents.activeDocumentChanged(null, null, null);
                activeSchema.set(null);
                activeSchemaStatus.set(SchemaStatus.NONE);
                activeSchemaSource.set(SchemaSource.NONE);
                activeSchemaSourceDetail.set(null);
            }
            // A different document's last result must not linger; validation re-runs
            // (continuous, or via Validate/F8) for the newly active document.
            validationStatus.set(ValidationStatus.NONE);
            activeProblems.clear();
            rebuildQuickFixGutter(java.util.List.of());
            refreshSelectedNode();
        });
        // Delete key removes the selected node in structured views (not in Text mode).
        addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE
                    && activeViewMode.get().isStructured() && deleteActiveNode()) {
                event.consume();
            }
            // Alt+Enter / Ctrl+. opens the quick-fix chooser for the caret line.
            if (((event.getCode() == javafx.scene.input.KeyCode.ENTER && event.isAltDown())
                    || (event.getCode() == javafx.scene.input.KeyCode.PERIOD && event.isControlDown()))
                    && showQuickFixPopupAtCaret()) {
                event.consume();
            }
        });
    }

    // ----- observable state ------------------------------------------------

    /** @return the open documents, in tab order (for the Explorer "Open Editors" list). */
    public ObservableList<OpenDocument> getOpenDocuments() {
        return openDocuments;
    }

    /** @return {@code true} when no document is open. */
    public boolean isEmpty() {
        return tabPane.getTabs().isEmpty();
    }

    /** @return the document of the selected tab, or empty if none. */
    public Optional<OpenDocument> getActiveDocument() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? Optional.of(et.document) : Optional.empty();
    }

    /** @return the text of the active editor, or empty if no tab is open. */
    public Optional<String> getActiveText() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? Optional.of(et.view.getText()) : Optional.empty();
    }

    /**
     * @return the active document's length in characters without building its text (O(1); for the
     *         status bar, which updates on every caret move), or -1 if no editor tab is active
     */
    public int getActiveTextLength() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.view.getCodeArea().getLength() : -1;
    }

    /** @return the text of the given (possibly non-active) open document, or empty if closed. */
    public Optional<String> getDocumentText(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document == document) {
                return Optional.of(editorTab.view.getText());
            }
        }
        return Optional.empty();
    }

    /** Whether the type is an XML-family instance/schema a query can sensibly run against. */
    private static boolean isXmlFamily(EditorFileType type) {
        return switch (type) {
            case XML, XSD, XSLT, SCHEMATRON, XPROC -> true;
            default -> false;
        };
    }

    /**
     * The most recently active XML-family document — the target an XPath/XQuery
     * document runs against. Falls back to the first open XML-family tab when the
     * remembered one was closed.
     *
     * @return the target document, or empty when no XML-family document is open
     */
    public Optional<OpenDocument> getLastXmlFamilyDocument() {
        return mostRecentXmlFamilyDocument(null);
    }

    /**
     * The most recently active XML-family document excluding {@code except}, walking the
     * activation history (closed tabs are pruned) and falling back to tab order. Central to
     * Automatic target resolution: when the active document is itself XML-family (an XSLT
     * or XProc tab), the previously active document is the natural target.
     *
     * @param except a document to skip (usually the query document itself), or {@code null}
     */
    private Optional<OpenDocument> mostRecentXmlFamilyDocument(OpenDocument except) {
        xmlFamilyMru.removeIf(tab -> !tabPane.getTabs().contains(tab));
        for (EditorTab tab : xmlFamilyMru) {
            if (tab.document != except) {
                return Optional.of(tab.document);
            }
        }
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && isXmlFamily(et.document.getFileType())
                    && et.document != except) {
                return Optional.of(et.document);
            }
        }
        return Optional.empty();
    }

    /** @return the open XML-family documents (valid query/transform targets), in tab order. */
    public java.util.List<OpenDocument> getOpenXmlFamilyDocuments() {
        return openDocuments.stream().filter(d -> isXmlFamily(d.getFileType())).toList();
    }

    /** @return the explicit run target of {@code queryDoc}, or {@link QueryTarget#AUTOMATIC}. */
    public QueryTarget getQueryTarget(OpenDocument queryDoc) {
        return queryTargets.getOrDefault(queryDoc, QueryTarget.AUTOMATIC);
    }

    /** Sets the explicit run target of {@code queryDoc}; {@code Automatic} clears the entry. */
    public void setQueryTarget(OpenDocument queryDoc, QueryTarget target) {
        if (target == null || target instanceof QueryTarget.Automatic) {
            queryTargets.remove(queryDoc);
        } else {
            queryTargets.put(queryDoc, target);
        }
    }

    /**
     * A resolved run target: either the in-memory text of an open document
     * ({@code xmlText != null}) or a file still to be read from disk
     * ({@code file != null}). Exactly one of the two is set.
     */
    public record ResolvedQueryTarget(String displayName, String xmlText, File file) {
        /** Returns the in-memory text, or reads the file — call off the FX thread. */
        public String loadXml() throws IOException {
            return xmlText != null ? xmlText : Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Resolves the XML target the given query/XSLT document runs against: its explicit
     * {@link QueryTarget} if one is set (an open document falls back to Automatic when it
     * was closed in the meantime — the stale entry is dropped), otherwise the most
     * recently active XML-family document. The document is never its own Automatic
     * target — an active XSLT stylesheet is XML-family and would otherwise always
     * transform itself.
     *
     * @return the resolved target, or empty when nothing is available
     */
    public Optional<ResolvedQueryTarget> resolveQueryTarget(OpenDocument queryDoc) {
        QueryTarget target = getQueryTarget(queryDoc);
        if (target instanceof QueryTarget.FsFile fs) {
            return Optional.of(new ResolvedQueryTarget(fs.file().getName(), null, fs.file()));
        }
        if (target instanceof QueryTarget.OpenDoc od) {
            Optional<String> text = getDocumentText(od.document());
            if (text.isPresent()) {
                return Optional.of(new ResolvedQueryTarget(od.document().getDisplayName(), text.get(), null));
            }
            // The chosen document was closed (possibly without onClosed firing) — drop
            // the stale entry and fall back to Automatic.
            queryTargets.remove(queryDoc);
        }
        return mostRecentXmlFamilyDocument(queryDoc).flatMap(doc -> getDocumentText(doc)
                .map(text -> new ResolvedQueryTarget(doc.getDisplayName(), text, null)));
    }

    /**
     * Registers the handler that runs the active XPath/XQuery/XSLT document (the shell
     * wires this to the toolbar's Run Query / Run Transform actions); fired by the
     * query editor — and XSLT editors — on Ctrl+Enter.
     */
    public void setQueryRunHandler(Runnable handler) {
        this.queryRunHandler = handler;
    }

    /** @return the selected-tab property (for observers). */
    public ReadOnlyObjectProperty<Tab> activeTabProperty() {
        return tabPane.getSelectionModel().selectedItemProperty();
    }

    /** @return the active editor caret offset; changes on caret movement and tab switches. */
    public ReadOnlyIntegerProperty activeCaretProperty() {
        return activeCaret.getReadOnlyProperty();
    }

    /** @return the XSD currently bound to the active document for IntelliSense, or {@code null}. */
    public ReadOnlyObjectProperty<File> activeSchemaProperty() {
        return activeSchema.getReadOnlyProperty();
    }

    /** @return the active document's schema-binding lifecycle (IntelliSense availability). */
    public ReadOnlyObjectProperty<SchemaStatus> activeSchemaStatusProperty() {
        return activeSchemaStatus.getReadOnlyProperty();
    }

    /** @return how the active document's schema binding was established (declared/catalog/library/manual). */
    public ReadOnlyObjectProperty<SchemaSource> activeSchemaSourceProperty() {
        return activeSchemaSource.getReadOnlyProperty();
    }

    /**
     * @return a human-readable detail for {@link #activeSchemaSourceProperty()} — the
     *         resolved catalog target for {@link SchemaSource#CATALOG}, the matched
     *         namespace/root element for {@link SchemaSource#LIBRARY}, or {@code null}.
     */
    public javafx.beans.property.ReadOnlyStringProperty activeSchemaSourceDetailProperty() {
        return activeSchemaSourceDetail.getReadOnlyProperty();
    }

    /**
     * @return a counter that increases whenever remote {@code xs:import}s that were missing
     *         from the schema cache have been downloaded in the background; views that list
     *         the active schema's types refresh on it.
     */
    public ReadOnlyIntegerProperty schemaImportsRevisionProperty() {
        return schemaImportsRevision.getReadOnlyProperty();
    }

    /**
     * @return a counter that increases when a background parse has brought the active document's model
     *         up to date with its text (XSD/XML Text view); views that read the model refresh on it
     */
    public ReadOnlyIntegerProperty activeModelRevisionProperty() {
        return activeModelRevision.getReadOnlyProperty();
    }

    /**
     * @return a counter that increases whenever an XML document's schema provider has finished
     *         loading in the background; schema-derived views (the inspector) refresh on it, since
     *         the selected node itself does not change.
     */
    public ReadOnlyIntegerProperty xmlSchemaProviderRevisionProperty() {
        return xmlSchemaProviderRevision.getReadOnlyProperty();
    }

    /**
     * Runs on the FX thread after a background download made skipped remote imports
     * resolvable: re-parses the XSD tabs that were parsed without them, then signals observers.
     */
    private void onRemoteImportsAvailable() {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.remoteImportsMissing && et.lastParsedText != null) {
                et.onRemoteImportsAvailable(et.lastParsedText);
            }
        }
        schemaImportsRevision.set(schemaImportsRevision.get() + 1);
    }

    /** @return the active document's current view mode (Text/Tree/Graphic). */
    public ReadOnlyObjectProperty<ViewMode> activeViewModeProperty() {
        return activeViewMode.getReadOnlyProperty();
    }

    /** @return the Text/Tree/Graphic view-mode switch, for placement in the editor toolbar. */
    public javafx.scene.layout.Region getViewSwitch() {
        return viewSwitch;
    }

    /** Switches the active document to the given view mode (structured modes apply to XSD). */
    public void setActiveViewMode(ViewMode mode) {
        withActive(et -> {
            ViewMode before = et.viewMode;
            long t0 = System.nanoTime();
            et.setViewMode(mode);
            activeViewMode.set(et.viewMode);
            refreshSelectedNode();
            if (et.viewMode != before && et.viewMode != null) {
                String modeName = et.viewMode.name().toLowerCase(java.util.Locale.ROOT);
                var kind = et.document.getFileType().docKind();
                // The document's length (not Files.size: a stat on the FX thread can stall on network drives)
                long size = et.view.getCodeArea().getLength();
                // Measured on the next pulse, so layout/CSS work queued by the switch is included.
                Platform.runLater(() -> UsageEvents.viewModeChanged(modeName, kind, size, t0));
            }
        });
    }

    /**
     * Switches the tab showing {@code document} to the given view mode without
     * changing the tab selection (e.g. a transform result tab to Preview).
     */
    public void setViewModeFor(OpenDocument document, ViewMode mode) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                et.setViewMode(mode);
                if (tab.isSelected()) {
                    activeViewMode.set(et.viewMode);
                    refreshSelectedNode();
                }
                return;
            }
        }
    }

    /** @return the node selected in the active structured (Tree/Graphic) view, or {@code null}. */
    public ReadOnlyObjectProperty<XsdNode> activeSelectedNodeProperty() {
        return activeSelectedNode.getReadOnlyProperty();
    }

    /** @return the XML-instance node selected in the active Grid view, or {@code null}. */
    public ReadOnlyObjectProperty<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode>
            activeXmlNodeProperty() {
        return activeXmlNode.getReadOnlyProperty();
    }

    /** @return the JSON node selected in the active Tree view, or {@code null}. */
    public ReadOnlyObjectProperty<org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode>
            activeJsonNodeProperty() {
        return activeJsonNode.getReadOnlyProperty();
    }

    /** @return the active document's validation status (for the inspector badge). */
    public ReadOnlyObjectProperty<ValidationStatus> validationStatusProperty() {
        return validationStatus.getReadOnlyProperty();
    }

    /** Publishes a validation result for the active document (called by the Validation panel). */
    public void setValidationStatus(ValidationState state, int problemCount, String summary) {
        validationStatus.set(new ValidationStatus(state, problemCount, summary));
    }

    /**
     * @return the active document's validation problems (read-only by convention) —
     * the data behind the PROBLEMS panel below the editor. Cleared on tab switch.
     */
    public ObservableList<ValidationProblem> getActiveProblems() {
        return activeProblems;
    }

    /** Publishes the active document's validation problems (called by the Validation panel). */
    public void setActiveProblems(java.util.List<ValidationProblem> problems) {
        activeProblems.setAll(problems);
        rebuildQuickFixGutter(problems);
    }

    // ----- quick fixes (SQF) ----------------------------------------------

    /** Orchestrates applying SQF quick fixes to the active document. */
    private final org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixController quickFixController =
            new org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixController(this);
    private final org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixPopup quickFixPopup =
            new org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixPopup();
    /** Problems with fixes on the active document, indexed by 1-based line (gutter lightbulbs). */
    private final java.util.Map<Integer, java.util.List<ValidationProblem>> fixableProblemsByLine =
            new java.util.HashMap<>();

    /** @return the quick-fix orchestrator (panels wire their context menus to it) */
    public org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixController getQuickFixController() {
        return quickFixController;
    }

    /**
     * Replaces a text region of the active editor (one native undo entry).
     *
     * @return {@code true} when an editor tab accepted the edit
     */
    public boolean replaceActiveTextRegion(int start, int end, String replacement) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            et.view.replaceTextRegion(start, end, replacement);
            return true;
        }
        return false;
    }

    /**
     * Replaces a text region of the (possibly non-active) open document at
     * {@code path} — one native undo entry in that document's editor. Used by
     * Replace-in-Files so open documents are edited through their buffer instead
     * of being overwritten on disk behind the editor's back.
     *
     * @return {@code true} when an open editor tab for {@code path} accepted the edit
     */
    public boolean replaceDocumentTextRegion(java.nio.file.Path path, int start, int end,
                                             String replacement) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                et.view.replaceTextRegion(start, end, replacement);
                return true;
            }
        }
        return false;
    }

    /** @return the buffer text of the open document at {@code path}, or empty if not open. */
    public Optional<String> getOpenDocumentText(java.nio.file.Path path) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                return Optional.of(et.view.getText());
            }
        }
        return Optional.empty();
    }

    /**
     * Highlights search-match ranges (start offset → length) in the active editor,
     * on top of its syntax highlighting. The search panes call this when results
     * arrive and clear it when the query changes.
     */
    public void setActiveSearchHighlights(java.util.Map<Integer, Integer> ranges) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            et.view.setSearchMatchRanges(ranges);
        }
    }

    /** Removes the search-match highlight overlay from the active editor. */
    public void clearActiveSearchHighlights() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            et.view.clearSearchMatches();
        }
    }

    /** @return whether the document at {@code path} is open with unsaved changes. */
    public boolean isDocumentDirty(java.nio.file.Path path) {
        for (OpenDocument doc : openDocuments) {
            if (path.equals(doc.getPath()) && doc.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /** Bumped by {@link #revealProblems()}; the PROBLEMS dock expands and focuses on each change. */
    private final javafx.beans.property.IntegerProperty problemsRevealRequests =
            new javafx.beans.property.SimpleIntegerProperty(this, "problemsRevealRequests");

    /** Asks the PROBLEMS dock below the editor to expand and take focus (e.g. from the Validation panel). */
    public void revealProblems() {
        problemsRevealRequests.set(problemsRevealRequests.get() + 1);
    }

    /** @return the reveal counter the PROBLEMS dock listens to */
    public javafx.beans.property.ReadOnlyIntegerProperty problemsRevealRequestsProperty() {
        return problemsRevealRequests;
    }

    /** Bumped by {@link #requestQueryConsole()}; the shell opens the Query Console on each change. */
    private final javafx.beans.property.IntegerProperty queryConsoleRequests =
            new javafx.beans.property.SimpleIntegerProperty(this, "queryConsoleRequests");

    /** Asks the shell to open the Query Console (e.g. from the Transform panel's pointer). */
    public void requestQueryConsole() {
        queryConsoleRequests.set(queryConsoleRequests.get() + 1);
    }

    /** @return the request counter the shell listens to */
    public javafx.beans.property.ReadOnlyIntegerProperty queryConsoleRequestsProperty() {
        return queryConsoleRequests;
    }

    /** The answer to "save before running a tool that reads the file from disk?". */
    public enum SaveBeforeTool { SAVE, CONTINUE, CANCEL }

    /** Test hook replacing the dialog: (file name, tool name) → answer; {@code null} shows the dialog. */
    private java.util.function.BiFunction<String, String, SaveBeforeTool> saveBeforeToolPrompt;

    void setSaveBeforeToolPrompt(java.util.function.BiFunction<String, String, SaveBeforeTool> prompt) {
        this.saveBeforeToolPrompt = prompt;
    }

    /**
     * Guards a tool that reads {@code file} from disk (sample XML, documentation, signing):
     * when the file is open with unsaved changes, asks whether to save first so the tool
     * does not silently work on the old state.
     *
     * @param file     the file the tool will read; {@code null} or not open → nothing to ask
     * @param toolName the tool's name for the dialog (e.g. "Generate Sample XML")
     * @return {@code true} to run the tool (saved, or the user chose to continue anyway),
     *         {@code false} when the user cancelled or saving failed
     */
    public boolean ensureSavedBeforeTool(java.nio.file.Path file, String toolName) {
        if (file == null) {
            return true;
        }
        EditorTab dirty = null;
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && file.equals(et.document.getPath()) && et.document.isDirty()) {
                dirty = et;
                break;
            }
        }
        if (dirty == null) {
            return true;
        }
        String name = dirty.document.getDisplayName();
        SaveBeforeTool choice;
        if (saveBeforeToolPrompt != null) {
            choice = saveBeforeToolPrompt.apply(name, toolName);
        } else {
            javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION, "Unsaved Changes",
                    "Save changes to " + name + " first?",
                    toolName + " reads the saved file. Changes that are only in the editor would not be included.");
            javafx.scene.control.ButtonType save = new javafx.scene.control.ButtonType(
                    "Save and Continue", javafx.scene.control.ButtonBar.ButtonData.YES);
            javafx.scene.control.ButtonType proceed = new javafx.scene.control.ButtonType(
                    "Use Saved Version", javafx.scene.control.ButtonBar.ButtonData.NO);
            javafx.scene.control.ButtonType cancel = new javafx.scene.control.ButtonType(
                    "Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(save, proceed, cancel);
            var answer = alert.showAndWait();
            choice = answer.isEmpty() || answer.get() == cancel ? SaveBeforeTool.CANCEL
                    : answer.get() == save ? SaveBeforeTool.SAVE : SaveBeforeTool.CONTINUE;
        }
        return switch (choice) {
            case SAVE -> write(dirty, file);
            case CONTINUE -> true;
            case CANCEL -> false;
        };
    }

    /** Rebuilds the line → fixable-problems index and the lightbulb gutter contribution. */
    private void rebuildQuickFixGutter(java.util.List<ValidationProblem> problems) {
        fixableProblemsByLine.clear();
        for (ValidationProblem problem : problems) {
            if (problem.hasFixes() && problem.line() > 0) {
                fixableProblemsByLine.computeIfAbsent(problem.line(), l -> new java.util.ArrayList<>())
                        .add(problem);
            }
        }
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return;
        }
        if (fixableProblemsByLine.isEmpty()) {
            setGutterContributor(et, "quickfix", null);
        } else {
            int offered = 0;
            for (java.util.List<ValidationProblem> line : fixableProblemsByLine.values()) {
                for (ValidationProblem problem : line) {
                    offered += problem.fixes().size();
                }
            }
            // once per document per session (throttled in UsageEvents)
            UsageEvents.quickFixOffered(et.document, et.document.getFileType().docKind(), offered,
                    UsageEvents.QUICK_FIX_VIA_GUTTER);
            setGutterContributor(et, "quickfix",
                    new org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixGutterFactory(
                            this::quickFixSuggestionsAt,
                            (line, fixes, x, y) -> showQuickFixPopupForLine(line, x, y)));
        }
        et.view.refreshGutter();
    }

    private java.util.List<org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion> quickFixSuggestionsAt(int line) {
        java.util.List<ValidationProblem> problems = fixableProblemsByLine.get(line);
        if (problems == null) {
            return java.util.List.of();
        }
        java.util.List<org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion> fixes = new java.util.ArrayList<>();
        for (ValidationProblem problem : problems) {
            fixes.addAll(problem.fixes());
        }
        return fixes;
    }

    /** Opens the quick-fix chooser for a line's fixable problems at a screen position. */
    private void showQuickFixPopupForLine(int line, double screenX, double screenY) {
        java.util.List<ValidationProblem> problems = fixableProblemsByLine.get(line);
        if (problems == null || problems.isEmpty() || getScene() == null) {
            return;
        }
        java.util.Map<org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion, ValidationProblem> owner =
                new java.util.IdentityHashMap<>();
        java.util.List<org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion> fixes = new java.util.ArrayList<>();
        for (ValidationProblem problem : problems) {
            for (var fix : problem.fixes()) {
                owner.put(fix, problem);
                fixes.add(fix);
            }
        }
        quickFixPopup.setOnFixSelected(fix -> quickFixController.applyFix(owner.get(fix), fix));
        quickFixPopup.show(fixes, getScene().getWindow(), screenX, screenY);
    }

    /**
     * Opens the quick-fix chooser for the caret line (Alt+Enter / Ctrl+Period).
     *
     * @return {@code true} when fixes exist on the caret line and the popup opened
     */
    private boolean showQuickFixPopupAtCaret() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return false;
        }
        var codeArea = et.view.getCodeArea();
        int line = codeArea.getCurrentParagraph() + 1;
        if (!fixableProblemsByLine.containsKey(line)) {
            return false;
        }
        var bounds = codeArea.getCaretBounds().orElse(null);
        if (bounds == null) {
            return false;
        }
        showQuickFixPopupForLine(line, bounds.getMinX(), bounds.getMaxY() + 2);
        return true;
    }

    /** @return the schema root of the active structured view, or empty (Text mode / not parsed). */
    public Optional<XsdNode> getActiveSchemaRoot() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            if (et.viewMode == ViewMode.TREE && et.treeView != null && et.treeView.getRoot() != null) {
                return Optional.of(et.treeView.getRoot().getValue());
            }
            if (et.viewMode == ViewMode.GRAPHIC && et.editorContext != null
                    && et.editorContext.getSchema() != null) {
                return Optional.of(et.editorContext.getSchema());
            }
        }
        return Optional.empty();
    }

    /** @return the top-level named types of the active XSD (from the model or a parse of the text). */
    public java.util.List<XsdNode> getActiveNamedTypes() {
        return org.fxt.freexmltoolkit.controls.shell.schema.TypeLibrary
                .collectNamedTypes(resolveActiveSchemaForLibrary());
    }

    /** @return the top-level (global) element declarations of the active XSD. */
    public java.util.List<XsdNode> getActiveGlobalElements() {
        return org.fxt.freexmltoolkit.controls.shell.schema.TypeLibrary
                .collectGlobalElements(resolveActiveSchemaForLibrary());
    }

    /** Resolves the active XSD's schema root for the Type Library (model, text parse, or tool-tab source). */
    private XsdNode resolveActiveSchemaForLibrary() {
        XsdNode schema = getActiveSchemaRoot().orElse(null);
        if (schema == null && tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab active
                && active.document.getFileType() == EditorFileType.XSD) {
            // Text view: the tab's shared model, brought up to date off the FX thread (the parse
            // resolves relative xs:import/xs:include against the file's directory, issue #36).
            // Until a newer parse lands, the last model is listed; activeModelRevision then
            // tells the views to refresh. Parsing the text here on every call froze the UI
            // for seconds per caret move on large schemas.
            active.ensureModelParsedAsync(() -> { });
            return active.editorContext != null ? active.editorContext.getSchema() : null;
        }
        if (schema == null && !(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab)) {
            // A tool tab (e.g. a type editor) is active — keep listing the structured XSD tab's types.
            EditorTab et = resolveStructuredEditorTab();
            if (et != null) {
                et.ensureModelParsed();
                if (et.editorContext != null) {
                    schema = et.editorContext.getSchema();
                }
            }
        }
        return schema;
    }

    /** Reveals a named type in the Tree view (switching to the XSD tab + Tree if needed). */
    public void revealTypeByName(String typeName) {
        if (typeName == null) {
            return;
        }
        EditorTab et = resolveStructuredEditorTab();
        if (et == null) {
            return;
        }
        // A type-editor tool tab may be focused — bring the XSD document tab to the front first.
        if (tabPane.getSelectionModel().getSelectedItem() != et) {
            tabPane.getSelectionModel().select(et);
        }
        setActiveViewMode(ViewMode.TREE);
        getActiveSchemaRoot().ifPresent(root -> {
            for (XsdNode child : root.getChildren()) {
                // Named types and global element declarations (the Type Library lists both).
                boolean revealable =
                        child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.SIMPLE_TYPE
                        || child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.COMPLEX_TYPE
                        || child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.ELEMENT;
                if (revealable && typeName.equals(child.getName())) {
                    selectNodeInActiveTree(child);
                    return;
                }
            }
        });
    }

    /**
     * Opens an XSD file in the shell and, once it has loaded and parsed, switches to the
     * Graphic view and selects the named element (preferred) or type. Bridge replacing the
     * retired legacy XSD editor's {@code navigateToElementInGraphView}, used by the XML
     * editor's "go to schema definition". Navigation is deferred until the async load
     * completes; if {@code elementName} is blank the file is just opened.
     */
    public void openXsdAndReveal(java.nio.file.Path path, String elementName) {
        if (path == null) {
            return;
        }
        openFile(path);
        if (elementName != null && !elementName.isBlank()) {
            revealInGraphAfterLoad(path, elementName, 80);
        }
    }

    private void revealInGraphAfterLoad(java.nio.file.Path path, String name, int attemptsLeft) {
        var doc = getActiveDocument();
        boolean loaded = doc.isPresent() && path.equals(doc.get().getPath())
                && getActiveText().map(t -> !t.isBlank()).orElse(false);
        if (loaded) {
            setActiveViewMode(ViewMode.GRAPHIC);
            XsdNode node = findNodeByName(getActiveSchemaRoot().orElse(null), name);
            if (node != null) {
                selectNodeInActiveTree(node);
                return;
            }
        }
        if (attemptsLeft > 0) {
            // Space out retries so the async file load + model parse can complete
            // (a tight runLater chain would exhaust all attempts in milliseconds).
            javafx.animation.PauseTransition pause =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
            pause.setOnFinished(e -> revealInGraphAfterLoad(path, name, attemptsLeft - 1));
            pause.play();
        }
    }

    /** Depth-first search for a named node, preferring elements over types; cycle-safe. */
    private static XsdNode findNodeByName(XsdNode root, String name) {
        if (root == null || name == null) {
            return null;
        }
        XsdNode fallback = null;
        java.util.Set<XsdNode> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<XsdNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode n = stack.pop();
            if (!visited.add(n)) {
                continue;
            }
            if (name.equals(n.getName())) {
                if (n.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.ELEMENT) {
                    return n;
                }
                if (fallback == null) {
                    fallback = n;
                }
            }
            for (XsdNode c : n.getChildren()) {
                stack.push(c);
            }
        }
        return fallback;
    }

    /**
     * Reveals, in the active Graphic view, the card at an instance path (the model particle of
     * every step from the global root element down, see {@code XsdInstanceLocator.Located#particlePath()}),
     * expanding the diagram along that path; in the Tree view the last particle is revealed.
     */
    public void revealPathInActiveGraph(java.util.List<XsdNode> particlePath) {
        if (particlePath == null || particlePath.isEmpty()) {
            return;
        }
        withActive(et -> {
            if (et.viewMode == ViewMode.GRAPHIC && et.xsdGraphView != null) {
                et.xsdGraphView.revealModelPath(particlePath);
            } else if (et.viewMode == ViewMode.TREE && et.treeView != null) {
                et.treeView.revealNode(particlePath.get(particlePath.size() - 1));
            }
        });
    }

    /** Selects (reveals) the given node in the active structured view (Tree or Graphic). */
    public void selectNodeInActiveTree(XsdNode node) {
        withActive(et -> {
            if (et.viewMode == ViewMode.TREE && et.treeView != null) {
                et.treeView.revealNode(node);
            } else if (et.viewMode == ViewMode.GRAPHIC && et.xsdGraphView != null) {
                et.xsdGraphView.selectModelNode(node);
            }
        });
    }

    /**
     * Reveals, in the Tree view, the live-model node whose schema XPath
     * ({@link XsdNode#getXPath()}, e.g. {@code /xs:schema/xs:complexType[@name='T']/xs:sequence})
     * equals {@code xpath}. Used by reports that analyzed a <em>fresh</em> parse of the
     * document and therefore cannot navigate by node identity. Unnamed siblings (two
     * {@code xs:sequence} under one parent) share an XPath; the first match wins.
     *
     * @return true if a node was found and selected
     */
    public boolean revealSchemaNodeByXPath(String xpath) {
        if (xpath == null || xpath.isBlank()) {
            return false;
        }
        EditorTab et = resolveStructuredEditorTab();
        if (et == null) {
            return false;
        }
        if (tabPane.getSelectionModel().getSelectedItem() != et) {
            tabPane.getSelectionModel().select(et);
        }
        setActiveViewMode(ViewMode.TREE);
        XsdNode root = getActiveSchemaRoot().orElse(null);
        XsdNode match = findNodeByXPath(root, xpath);
        if (match == null) {
            return false;
        }
        selectNodeInActiveTree(match);
        return true;
    }

    /** Depth-first search for the first node whose {@code getXPath()} equals {@code xpath}; cycle-safe. */
    private static XsdNode findNodeByXPath(XsdNode root, String xpath) {
        if (root == null) {
            return null;
        }
        java.util.Set<XsdNode> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<XsdNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode n = stack.pop();
            if (!visited.add(n)) {
                continue;
            }
            if (xpath.equals(n.getXPath())) {
                return n;
            }
            // Children are pushed in reverse so document order is searched first.
            java.util.List<XsdNode> children = n.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return null;
    }

    private void refreshSelectedNode() {
        XsdNode selected = null;
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode xmlSelected = null;
        org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode jsonSelected = null;
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            if (et.viewMode.isStructured()) {
                jsonSelected = et.currentJsonSelection;
            }
            // XSD and XML nodes may be selected from any view, including the Text view (caret-driven),
            // so the inspector can edit their properties without leaving the text editor.
            selected = et.currentSelection;
            xmlSelected = et.currentXmlSelection;
        }
        activeSelectedNode.set(selected);
        activeXmlNode.set(xmlSelected);
        activeJsonNode.set(jsonSelected);
    }

    /** @return {@code true} if the active document supports Tree/Graphic views (XSD). */
    public boolean activeSupportsStructuredViews() {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.supportsStructuredViews();
    }

    /** @return {@code true} if the active document offers the given view mode. */
    public boolean activeSupportsView(ViewMode mode) {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.supportsView(mode);
    }

    /** @return the active editor caret as {@code [line, column]} (1-based), or {@code [1,1]}. */
    public int[] getActiveCaretLineColumn() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            var codeArea = et.view.getCodeArea();
            return new int[]{codeArea.getCurrentParagraph() + 1, codeArea.getCaretColumn() + 1};
        }
        return new int[]{1, 1};
    }

    // ----- opening / creating ---------------------------------------------

    /** Opens {@code path} (or focuses it if already open) and loads its content asynchronously. */
    public void openFile(Path path) {
        openFile(path, true);
    }

    /**
     * Opens {@code path} and loads its content asynchronously. With {@code select = false} the
     * tab opens behind the active one (an already open tab stays where it is), so a file a side
     * panel picked from the favorites lands in the editor without changing the active document.
     */
    public void openFile(Path path, boolean select) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                if (select) {
                    tabPane.getSelectionModel().select(tab);
                }
                return;
            }
        }
        rememberRecentFile(path); // before addTab: open-document listeners read the list
        EditorTab tab = new EditorTab(OpenDocument.forPath(path), this::refreshSelectedNode, nodeClipboard,
                this::openNamedTypeEditor, this::goToXmlDefinition);
        addTab(tab, select);
        // HTML files open rendered; the preview fills once the async load lands.
        if (tab.document.getFileType() == EditorFileType.HTML) {
            tab.setViewMode(ViewMode.PREVIEW);
            if (select) {
                activeViewMode.set(tab.viewMode); // addTab just selected this tab
            }
        }
        installInputNodeBreakpointMenu(tab);
        // Wire "Go to Definition": open the bound XSD and reveal the element (no-op for non-XML views).
        tab.view.setGoToDefinitionHandler(req ->
                openXsdAndReveal(req.xsdFile().toPath(), req.elementName()));
        loadAsync(tab, path);
    }

    /** Convenience overload of {@link #openFile(Path)} for {@link File} call sites. */
    public void openFile(File file) {
        openFile(file.toPath());
    }

    /**
     * Inserts {@code text} at the caret of the active editor — or, if a non-editor
     * tab (e.g. a tool) is in front, the most recently active editor.
     */
    public void insertTextAtCaret(String text) {
        if (text == null) {
            return;
        }
        EditorTab target = tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                ? et : lastEditorTab;
        if (target != null) {
            target.view.getCodeArea().replaceSelection(text);
        }
    }

    /**
     * Opens an arbitrary tool UI (e.g. a Schematron tool) as a closable tab.
     *
     * @return the created tab
     */
    public Tab openToolTab(String title, String iconLiteral, javafx.scene.layout.Region content) {
        Tab tab = new Tab(title, content);
        tab.setGraphic(new IconifyIcon(iconLiteral));
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        return tab;
    }

    /**
     * Focuses the tool tab with the given title if one is already open, else opens
     * a new one with the supplied content. Used by singleton tool views (e.g. the
     * favorites manager) whose owning side panel is recreated on every activity
     * switch and therefore cannot hold the tab reference itself.
     */
    public Tab openOrFocusToolTab(String title, String iconLiteral,
                                  java.util.function.Supplier<javafx.scene.layout.Region> content) {
        for (Tab tab : tabPane.getTabs()) {
            if (!(tab instanceof EditorTab) && title.equals(tab.getText())) {
                tabPane.getSelectionModel().select(tab);
                return tab;
            }
        }
        return openToolTab(title, iconLiteral, content.get());
    }

    /**
     * Opens (or focuses) the singleton "Execution Statistics" developer tool tab —
     * the history of recorded operations with their resource consumption.
     */
    public void openExecutionStats() {
        Tab tab = openOrFocusToolTab("Execution Statistics", "bi-speedometer2",
                ExecutionStatsView::new);
        if (tab.getContent() instanceof ExecutionStatsView view) {
            tab.setOnClosed(e -> view.dispose());
            view.refreshRecordingState(); // the flag may have changed in Settings meanwhile
        }
    }

    /** @return whether a (non-document) tool tab with the given title is open. */
    public boolean hasToolTab(String title) {
        for (Tab tab : tabPane.getTabs()) {
            if (!(tab instanceof EditorTab) && title.equals(tab.getText())) {
                return true;
            }
        }
        return false;
    }

    /** @return whether the given tab is still open in this host. */
    public boolean containsTab(Tab tab) {
        return tab != null && tabPane.getTabs().contains(tab);
    }

    /** Brings the given (already open) tab to the front. */
    public void selectTab(Tab tab) {
        if (containsTab(tab)) {
            tabPane.getSelectionModel().select(tab);
        }
    }

    /** @return whether the active document's file is still being read from disk */
    public boolean isActiveDocumentLoading() {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et && et.loading;
    }

    /** Brings the tab whose content is {@code content} (e.g. a tool page) to the front, if open. */
    public void selectTabShowing(javafx.scene.Node content) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getContent() == content) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
    }

    /** Open type-editor tabs, keyed by type name, so a type is edited in at most one tab. */
    private final java.util.Map<String, Tab> openTypeTabs = new java.util.HashMap<>();

    /** Shell-wide clipboard for copy/cut/paste of XSD nodes across the structured views. */
    private final org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard nodeClipboard =
            new org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard();

    /**
     * Opens a named XSD type in a dedicated editor tab: a 5-panel form for a simple type, a
     * focused graphical editor for a complex type. Both are backed by the active document's live
     * {@link org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext}; edits round-trip to the
     * XSD text (the simple-type form edits the shared model directly; the complex-type editor
     * merges its changes back on Save). Re-opening a type re-selects its existing tab.
     *
     * @return the opened (or re-selected) tab, or {@code null} if no such named type exists
     */
    /**
     * Opens the type-editor tab for a named type, triggered from a Graphic-view context-menu
     * action ("Open in Type Editor"). Anonymous inline types have no name and therefore no tab
     * target, so the request is ignored. Deferred to the FX pulse so the originating context menu
     * has fully closed first.
     */
    private void openNamedTypeEditor(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        javafx.application.Platform.runLater(() -> openTypeEditorTab(typeName));
    }

    public Tab openTypeEditorTab(String typeName) {
        if (typeName == null) {
            return null;
        }
        // Re-select an already-open tab for this type — works even when a tool tab is active.
        Tab existing = openTypeTabs.get(typeName);
        if (existing != null && tabPane.getTabs().contains(existing)) {
            tabPane.getSelectionModel().select(existing);
            return existing;
        }
        EditorTab et = resolveStructuredEditorTab();
        if (et == null) {
            return null;
        }
        et.ensureModelParsed();
        var ctx = et.editorContext;
        if (ctx == null || ctx.getSchema() == null) {
            return null;
        }
        XsdNode type = findNamedType(ctx.getSchema(), typeName);
        Tab tab;
        if (type instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
            var view = new org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypeEditorView(simpleType, ctx);
            // The form edits the shared model live; round-trip each change to the XSD text.
            view.setOnChangeCallback(et::scheduleRoundTrip);
            view.setOnSaveCallback(et::scheduleRoundTrip);
            view.setOnFindUsageCallback(() -> revealTypeByName(typeName));
            tab = openToolTab("Type: " + typeName, "bi-braces-asterisk", view);
            Tab created = tab;
            view.setOnCloseCallback(() -> tabPane.getTabs().remove(created));
        } else if (type instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
            tab = openToolTab("Type: " + typeName, "bi-diagram-3",
                    buildComplexTypeEditor(complexType, ctx.getSchema(), et));
        } else {
            return null;
        }
        // Round-trip edits made on the shared model for this tab's lifetime, independent of the
        // XSD tab's current view mode (the standing graphic listener only fires in GRAPHIC mode).
        java.beans.PropertyChangeListener roundTrip = evt -> et.scheduleRoundTrip();
        ctx.getSchema().addPropertyChangeListener(roundTrip);
        tab.setOnClosed(e -> {
            ctx.getSchema().removePropertyChangeListener(roundTrip);
            openTypeTabs.remove(typeName);
        });
        openTypeTabs.put(typeName, tab);
        return tab;
    }

    /** The XSD source tab for type editing: the active one if structured, else the first such tab. */
    private EditorTab resolveStructuredEditorTab() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab active
                && active.supportsStructuredViews()) {
            return active;
        }
        for (Tab t : tabPane.getTabs()) {
            if (t instanceof EditorTab e && e.supportsStructuredViews()) {
                return e;
            }
        }
        return null;
    }

    private XsdNode findNamedType(XsdNode schema, String name) {
        for (XsdNode child : schema.getChildren()) {
            var t = child.getNodeType();
            boolean isType = t == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.SIMPLE_TYPE
                    || t == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.COMPLEX_TYPE;
            if (isType && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Wraps the V2 complex-type graphical editor with a small Save toolbar. The editor works on an
     * isolated virtual schema; Save merges its changes back into the main schema (which then
     * round-trips to text via the tab's shared-schema listener).
     */
    private javafx.scene.layout.Region buildComplexTypeEditor(
            org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType,
            org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema, EditorTab et) {
        var view = new org.fxt.freexmltoolkit.controls.v2.editor.views.ComplexTypeEditorView(complexType, schema);
        // Slim the embedded graph to the shell's minimal chrome (breadcrumb + zoom pill) — the
        // embedded properties panel stays (it edits the type's nodes; the shell inspector is for
        // the document, not this tool tab).
        if (view.getGraphView() != null) {
            view.getGraphView().useMinimalChrome();
        }
        javafx.scene.control.Button save = new javafx.scene.control.Button("Save to schema",
                new IconifyIcon("bi-save"));
        save.getStyleClass().add("fxt-tool-button");
        save.setOnAction(e -> {
            if (view.save()) {
                et.scheduleRoundTrip();
            }
        });
        javafx.scene.control.ToolBar bar = new javafx.scene.control.ToolBar(save);
        bar.getStyleClass().add("fxt-editor-actionbar");
        javafx.scene.layout.BorderPane wrapper = new javafx.scene.layout.BorderPane(view);
        wrapper.setTop(bar);
        return wrapper;
    }

    /** Shows a file chooser and opens the chosen file (used by the welcome empty-state). */
    public void openFileChooser() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(EditorFileType.fileChooserFilters());
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    /**
     * Opens a side-by-side diff comparing the active document (left) against
     * {@code rightFile} (right), reusing the existing {@link org.fxt.freexmltoolkit.controls.diff.DiffView}.
     * Saving the left side writes back to the active document (and its file, if any).
     *
     * @return completes on the FX thread with the created diff tab (the file is read and the documents
     *         compared off the FX thread), or with {@code null} if no document is active
     */
    public CompletableFuture<org.fxt.freexmltoolkit.controls.diff.DiffView> openDiffWithFile(File rightFile) {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab left)) {
            return CompletableFuture.completedFuture(null);
        }
        String leftName = left.document.getDisplayName();
        String leftText = left.view.getText();
        java.util.function.Consumer<String> leftSave = newText -> {
            left.view.setText(newText);
            Path path = left.document.getPath();
            if (path != null) {
                // Through write() so the disk stamp follows; a failure surfaces via the document
                // staying dirty (the diff view reports save errors itself).
                write(left, path);
            }
        };
        return org.fxt.freexmltoolkit.controls.diff.DiffView.open(leftName, leftText, leftSave, rightFile)
                .thenApply(diff -> {
                    tabPane.getTabs().add(diff);
                    tabPane.getSelectionModel().select(diff);
                    return diff;
                });
    }

    /**
     * Opens an in-app preview of {@code pdf} as a new tab (lazy page rendering).
     *
     * @return completes on the FX thread with the preview control, or {@code null} if the PDF cannot be read
     */
    public CompletableFuture<PdfPreview> openPdfPreview(File pdf) {
        // Counting the pages loads the whole PDF (and PDFBox on first use): do it off the FX thread
        CompletableFuture<PdfPreview> result = new CompletableFuture<>();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            int pages;
            try {
                pages = PdfPreviewRenderer.pageCount(pdf);
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> result.complete(null));
                return;
            }
            Platform.runLater(() -> {
                PdfPreview preview = new PdfPreview(pdf, pages);
                Tab tab = new Tab(pdf.getName(), preview);
                tab.setGraphic(new IconifyIcon("bi-file-earmark-pdf"));
                tabPane.getTabs().add(tab);
                tabPane.getSelectionModel().select(tab);
                result.complete(preview);
            });
        });
        return result;
    }

    /** Shows the welcome empty-state when no tab is open, the tab pane otherwise. */
    /**
     * Above this many paragraphs the longest line is not measured: the shape is read on the FX
     * thread right after a stall, and walking millions of paragraphs stalled it once more.
     */
    static final int DOC_SHAPE_MAX_SCANNED_LINES = 50_000;

    /**
     * Measures a code area for {@code ui_stall} telemetry (size and line lengths, no content).
     * Holds the area only weakly so a closed tab is never kept alive by the telemetry context.
     * The longest line is reported as -1 (unknown) for documents with more than
     * {@value #DOC_SHAPE_MAX_SCANNED_LINES} lines.
     */
    static java.util.function.Supplier<UsageEvents.DocShape> docShapeOf(
            org.fxmisc.richtext.CodeArea area) {
        java.lang.ref.WeakReference<org.fxmisc.richtext.CodeArea> ref = new java.lang.ref.WeakReference<>(area);
        return () -> {
            org.fxmisc.richtext.CodeArea a = ref.get();
            if (a == null) {
                return null;
            }
            int lines = a.getParagraphs().size();
            if (lines > DOC_SHAPE_MAX_SCANNED_LINES) {
                return new UsageEvents.DocShape(a.getLength(), lines, -1);
            }
            int maxLen = 0;
            for (int p = 0; p < lines; p++) {
                maxLen = Math.max(maxLen, a.getParagraphLength(p));
            }
            return new UsageEvents.DocShape(a.getLength(), lines, maxLen);
        };
    }

    /**
     * Length of a code area in characters for out-of-memory telemetry: a single field read, safe
     * from any thread. Holds the area only weakly; {@code -1} once it is gone.
     */
    static java.util.function.LongSupplier docLengthOf(org.fxmisc.richtext.CodeArea area) {
        java.lang.ref.WeakReference<org.fxmisc.richtext.CodeArea> ref = new java.lang.ref.WeakReference<>(area);
        return () -> {
            org.fxmisc.richtext.CodeArea a = ref.get();
            return a == null ? -1 : a.getLength();
        };
    }

    private void updateCenter() {
        if (tabPane.getTabs().isEmpty()) {
            setCenter(welcomePane);
            refreshWelcomeData();
        } else {
            setCenter(tabArea);
        }
    }

    /** Bumped per welcome refresh (FX thread); an older result is dropped. */
    private long welcomeRequest;

    /**
     * Collects the dashboard's data off the FX thread - recent files (each stat-ed), favorites, the
     * template repository, saved queries, the FundsXML cache - and shows it once ready. Reading all of it
     * on the FX thread delayed the shell's first frame, and an unreachable network path in the recent
     * files froze it for many seconds.
     */
    private void refreshWelcomeData() {
        long request = ++welcomeRequest;
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            java.util.List<File> recent = recentFiles();
            java.util.Map<File, Long> modified = new java.util.HashMap<>();
            for (File file : recent) {
                modified.put(file, file.lastModified());
            }
            EditorWelcomePane.WelcomeStats stats = welcomeStats(recent.size());
            boolean fundsXml = fundsXmlContentAvailable();
            Platform.runLater(() -> {
                if (request != welcomeRequest || !tabPane.getTabs().isEmpty()) {
                    return;
                }
                welcomePane.setRecentFiles(recent, modified);
                welcomePane.setStats(stats);
                welcomePane.setFundsXmlVisible(fundsXml);
            });
        });
    }

    /**
     * Refreshes the welcome dashboard if it is currently showing — e.g. after a
     * FundsXML background download finished so its quick-access section appears.
     */
    public void refreshWelcome() {
        if (tabPane.getTabs().isEmpty()) {
            updateCenter();
        }
    }

    /** Whether the FundsXML extension is enabled and content is cached (best-effort). */
    private static boolean fundsXmlContentAvailable() {
        try {
            return FundsXmlRunner.isEnabled() && !FundsXmlRunner.installedVersions().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Builds the segmented view-mode switch (Text / Tree / Graphic, each with its icon —
     * per the Figma mockup). Buttons reflect the active view mode and disable for modes
     * the active document does not support. The control is kept at its preferred size so
     * the overlay {@link javafx.scene.layout.StackPane} positions it top-right instead of
     * stretching it across the tab area.
     */
    private javafx.scene.layout.Region buildViewSwitch() {
        var group = new javafx.scene.control.ToggleGroup();
        var buttons = new java.util.EnumMap<ViewMode, javafx.scene.control.ToggleButton>(ViewMode.class);
        javafx.scene.layout.HBox box = new javafx.scene.layout.HBox();
        box.getStyleClass().add("fxt-view-switch");
        box.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        box.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        for (var mode : ViewMode.values()) {
            var icon = new org.fxt.freexmltoolkit.controls.icons.IconifyIcon(mode.icon());
            icon.setIconSize(13);
            javafx.scene.control.ToggleButton button =
                    new javafx.scene.control.ToggleButton(mode.label(), icon);
            button.setToggleGroup(group);
            button.getStyleClass().add("fxt-view-seg");
            button.setFocusTraversable(false);
            button.setOnAction(e -> setActiveViewMode(mode));
            buttons.put(mode, button);
            box.getChildren().add(button);
        }

        Runnable sync = () -> {
            ViewMode active = activeViewMode.get();
            boolean hasDoc = getActiveDocument().isPresent();
            buttons.forEach((mode, button) -> {
                button.setSelected(mode == active);
                button.setDisable(!hasDoc || !activeSupportsView(mode));
                if (mode == ViewMode.PREVIEW) {
                    // Preview exists for HTML only: hide the segment instead of showing it
                    // permanently disabled for every other document.
                    boolean shown = hasDoc && activeSupportsView(mode);
                    button.setVisible(shown);
                    button.setManaged(shown);
                }
            });
        };
        sync.run();
        activeViewMode.addListener((obs, oldV, newV) -> sync.run());
        activeTabProperty().addListener((obs, oldV, newV) -> sync.run());
        return box;
    }

    /** @return the dashboard counters (recent / favorites / templates / saved queries), best-effort. */
    private EditorWelcomePane.WelcomeStats welcomeStats() {
        return welcomeStats(recentFiles().size());
    }

    private EditorWelcomePane.WelcomeStats welcomeStats(int recent) {
        int favorites = safeCount(() -> org.fxt.freexmltoolkit.service.FavoritesService.getInstance()
                .getAllFavorites().size());
        int templates = safeCount(() -> TemplateRunner.list().size());
        int queries = safeCount(() -> {
            var favorites2 = org.fxt.freexmltoolkit.service.FavoritesService.getInstance();
            return favorites2.getSavedXPathQueries().size() + favorites2.getSavedXQueryQueries().size();
        });
        return new EditorWelcomePane.WelcomeStats(recent, favorites, templates, queries);
    }

    private static int safeCount(java.util.concurrent.Callable<Integer> counter) {
        try {
            return counter.call();
        } catch (Throwable t) {
            return 0;
        }
    }

    private java.util.List<File> recentFiles() {
        try {
            return org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .getLastOpenFiles();
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * Sets the handler for Welcome/Dashboard tool/action keys (activity ids and
     * {@code open-folder}); the shell wires this to its activity selection. The
     * {@code from-url} action is handled internally by {@link #openFromUrl()}.
     */
    public void setWelcomeActionHandler(java.util.function.Consumer<String> handler) {
        this.welcomeActionHandler = handler != null ? handler : key -> { };
    }

    /** Sets the handler told about every document read from disk, with its content (FX thread). */
    public void setDocumentLoadedHandler(java.util.function.BiConsumer<OpenDocument, String> handler) {
        this.documentLoadedHandler = handler != null ? handler : (doc, content) -> { };
    }

    private void fireWelcomeAction(String key) {
        if ("from-url".equals(key)) {
            openFromUrl();
        } else {
            welcomeActionHandler.accept(key);
        }
    }

    /** Clears the recent-files list (and refreshes the welcome list). */
    private void clearRecentFiles() {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .clearLastOpenFiles();
        } catch (Throwable ignored) {
            // service not available (e.g. isolated tests): just clear the displayed list
        }
        welcomePane.setRecentFiles(java.util.List.of());
        welcomePane.setStats(welcomeStats());
    }

    /** Prompts for a URL and opens its fetched content as a new document (Welcome "From URL"). */
    public void openFromUrl() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Open from URL");
        dialog.setHeaderText("Fetch and open a document from a URL");
        dialog.setContentText("URL:");
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().map(String::trim).filter(s -> !s.isBlank()).ifPresent(this::fetchUrlAsync);
    }

    private void fetchUrlAsync(String url) {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                // Go through the ConnectionService so the proxy / authentication / SSL settings
                // from the Settings page apply (a bare HttpClient gets HTTP 407 behind an
                // authenticating proxy).
                String body = org.fxt.freexmltoolkit.di.ServiceRegistry
                        .get(org.fxt.freexmltoolkit.service.ConnectionService.class)
                        .getTextContentFromURL(java.net.URI.create(url));
                String name = urlFileName(url);
                EditorFileType type = EditorFileType.fromFileName(name);
                Platform.runLater(() -> openGeneratedDocument(body, type, name, UsageEvents.SOURCE_URL));
            } catch (Exception e) {
                Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showActionError(
                        "Open from URL", "Could not fetch \"" + url + "\".",
                        org.fxt.freexmltoolkit.util.DialogHelper.Remedies.URL_FETCH, e));
            }
        });
    }

    private static String urlFileName(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? "Untitled" : name;
    }

    /**
     * Routes a Welcome/Dashboard "New" request through the shell's guided New File
     * dialog when one was registered via {@link #setNewDocumentHandler(Runnable)},
     * otherwise creates a blank document of the requested type.
     */
    private void requestNewDocument(EditorFileType type) {
        if (newDocumentOverride != null) {
            newDocumentOverride.run();
        } else {
            newDocument(type);
        }
    }

    /**
     * Registers a custom handler for "new document" requests originating from the
     * Welcome/Dashboard page (e.g. the shell's guided New File dialog), so all new-file
     * entry points share one flow. {@code null} restores the default blank-document behavior.
     *
     * @param handler the handler to run, or {@code null} for the default
     */
    public void setNewDocumentHandler(Runnable handler) {
        this.newDocumentOverride = handler;
    }

    /** Creates an empty untitled document of the given type. */
    public OpenDocument newDocument(EditorFileType type) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(untitledName(), type), this::refreshSelectedNode,
                nodeClipboard, this::openNamedTypeEditor, this::goToXmlDefinition);
        addTab(tab);
        tab.attachDirtyTracking();
        UsageEvents.fileOpened(type != null ? type.docKind() : null, -1, UsageEvents.SOURCE_NEW);
        return tab.document;
    }

    /**
     * Opens generated content (e.g. a generated XSD, flattened schema, sample
     * data) as a new untitled, dirty document.
     *
     * @return the new document
     */
    public OpenDocument openGeneratedDocument(String content, EditorFileType type, String displayName) {
        return openGeneratedDocument(content, type, displayName, UsageEvents.SOURCE_GENERATED);
    }

    /**
     * Like {@link #openGeneratedDocument(String, EditorFileType, String)}, with the usage-statistics
     * source ({@code UsageEvents.SOURCE_*}) — e.g. a New File dialog passes {@code SOURCE_NEW}.
     */
    public OpenDocument openGeneratedDocument(String content, EditorFileType type, String displayName,
                                              String usageSource) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(displayName, type), this::refreshSelectedNode,
                nodeClipboard, this::openNamedTypeEditor, this::goToXmlDefinition);
        addTab(tab);
        tab.view.setText(content);
        tab.attachDirtyTracking();
        tab.document.setDirty(true);
        // character count as a cheap size proxy (no encoding on the FX thread)
        reportGeneratedOpen(type != null ? type.docKind() : null, content, usageSource);
        return tab.document;
    }

    /**
     * Records {@code file_open} for a generated/downloaded document. The element count scans
     * the text, so XML-family documents are reported from the worker pool, never on the FX
     * thread; everything else (and a pool that no longer accepts work) is reported right away.
     */
    private static void reportGeneratedOpen(DocKind kind, String content, String usageSource) {
        long chars = content != null ? content.length() : -1;
        if (content != null && UsageEvents.countsElements(kind)) {
            try {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> UsageEvents.fileOpened(
                        kind, chars, usageSource, 0, UsageEvents.elementCount(kind, content)));
                return;
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // shutting down: report without the element count
            }
        }
        UsageEvents.fileOpened(kind, chars, usageSource);
    }

    /** The lazily created transform OUTPUT panel in the bottom dock, see {@link TransformOutputPanel}. */
    private TransformOutputPanel transformOutput;
    /** The lazily created dock below the editor tabs hosting OUTPUT and the DEBUG pane. */
    private EditorBottomDock bottomDock;

    /**
     * The dock below the editor tabs (spans the editor column only — the inspector stays
     * full height). Created lazily, hidden until a panel is shown.
     */
    public EditorBottomDock bottomDock() {
        if (bottomDock == null) {
            bottomDock = new EditorBottomDock();
            setBottom(bottomDock);
        }
        return bottomDock;
    }

    /**
     * The transform OUTPUT panel in the bottom dock. Created lazily on first access and
     * kept across activity switches so results survive them.
     */
    public TransformOutputPanel transformOutputPanel() {
        if (transformOutput == null) {
            transformOutput = new TransformOutputPanel(this);
            bottomDock().register(TransformOutputPanel.DOCK_KEY, transformOutput, "OUTPUT");
        }
        return transformOutput;
    }

    /**
     * Runs {@code xsltFile} over a single {@code xmlFile} off the UI thread and shows the
     * result in the docked {@link TransformOutputPanel} (HTML preview when applicable). The
     * output format is auto-detected from the stylesheet's {@code xsl:output}. Used by the
     * Explorer's one-click transform; mirrors {@link TransformPanel#transform()}.
     *
     * @param xmlFile  the XML input file
     * @param xsltFile the stylesheet to apply (also remembered as a recent stylesheet)
     */
    public void runXsltPreview(File xmlFile, File xsltFile) {
        TransformOutputPanel out = transformOutputPanel();
        if (xsltFile == null || !xsltFile.isFile()) {
            out.showError("Select an XSLT stylesheet first.");
            return;
        }
        if (xmlFile == null || !xmlFile.isFile()) {
            out.showError("Select an XML file to transform.");
            return;
        }
        rememberRecentXslt(xsltFile);
        out.showPending("Transforming…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XSLT,
                    xsltFile.getName() + " (preview)");
            String result;
            long inputChars = -1;
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format =
                    org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML;
            org.fxt.freexmltoolkit.service.XsltTransformationResult fullResult = null;
            try {
                String xml = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
                inputChars = xml.length();
                String xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
                format = TransformRunner.detectXsltOutputFormat(xsltContent);
                fullResult = TransformRunner.xsltTransformResult(xml, xsltContent, java.util.Map.of(), format);
                result = fullResult.isSuccess()
                        ? fullResult.getOutputContent()
                        : "ERROR: " + fullResult.getErrorMessage();
                if (fullResult.isSuccess()) {
                    probe.phase("Compile", fullResult.getCompilationTime());
                    probe.phase("Transform", fullResult.getTransformationTime());
                }
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            boolean ok = !result.startsWith("ERROR");
            UsageEvents.xsltTransformed(1, t0, ok, false,
                    fullResult != null ? fullResult.getErrorCode() : null,
                    fullResult != null ? fullResult.getErrorPhase() : null, inputChars);
            long elapsedMs = probe.finish(inputChars, ok ? result.length() : -1, ok,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            String finalResult = result;
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat finalFormat = format;
            Platform.runLater(() -> out.showTransformResult(finalResult, finalFormat, elapsedMs));
        });
    }

    /**
     * Opens a {@link BatchTransformView} tool tab pre-loaded with {@code xmlFiles} and the given
     * stylesheet, then starts the batch run. Used by the Explorer when several XML files are
     * selected; reuses the existing batch tooling and its "Save All…" action.
     *
     * @param xmlFiles the XML input files
     * @param xsltFile the stylesheet to apply over each file
     */
    public void openBatchTransform(java.util.List<File> xmlFiles, File xsltFile) {
        TransformOutputPanel out = transformOutputPanel();
        if (xsltFile == null || !xsltFile.isFile()) {
            out.showError("Select an XSLT stylesheet first.");
            return;
        }
        if (xmlFiles == null || xmlFiles.isEmpty()) {
            out.showError("Select XML files to transform.");
            return;
        }
        rememberRecentXslt(xsltFile);
        String xsltContent;
        org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format;
        try {
            xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
            format = TransformRunner.detectXsltOutputFormat(xsltContent);
        } catch (IOException e) {
            out.showError("Could not read stylesheet: " + e.getMessage());
            return;
        }
        var view = new org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView(
                org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView.Kind.XSLT,
                xsltContent, java.util.Map.of(), format);
        view.addInputFiles(xmlFiles);
        openToolTab("Batch", "bi-collection", view);
        view.run();
    }

    /** Records {@code path} in the recent-files list (File → Recent, Explorer, Welcome; best-effort). */
    private static void rememberRecentFile(Path path) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .addLastOpenFile(path.toFile());
        } catch (Throwable ignored) {
            // properties service unavailable — no recent-files persistence
        }
    }

    /** Records {@code xsltFile} in the shared recent-stylesheet store (best-effort). */
    private static void rememberRecentXslt(File xsltFile) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .addRecentXsltFile(xsltFile);
        } catch (Throwable ignored) {
            // properties service unavailable — no recent-files persistence
        }
    }

    /**
     * Replaces the text of a still-open generated document (e.g. a re-run transform
     * result) without changing the tab selection.
     *
     * @return {@code true} if the document's tab is still open and was updated
     */
    public boolean updateGeneratedDocument(OpenDocument document, String content) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document == document) {
                editorTab.view.setText(content);
                editorTab.refreshPreviewIfActive();
                return true;
            }
        }
        return false;
    }

    // ----- saving / editing ------------------------------------------------

    /**
     * Saves the active document to its current path (must be titled). The file is written on a worker
     * thread (a large file on a network drive blocked the UI for seconds).
     *
     * @return completes on the FX thread with whether the file was written
     */
    public CompletableFuture<Boolean> saveActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et) || et.document.isUntitled()) {
            return CompletableFuture.completedFuture(false);
        }
        return writeAsync(et, et.document.getPath()).thenApply(saved -> {
            if (saved) {
                UsageEvents.fileSaved(et.document.getFileType().docKind(), 1);
            }
            return saved;
        });
    }

    /**
     * Saves the active document to {@code target} (Save As), written on a worker thread.
     *
     * @return completes on the FX thread with whether the file was written
     */
    public CompletableFuture<Boolean> saveActiveAs(Path target) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return CompletableFuture.completedFuture(false);
        }
        Path normalized = withTypeExtension(et, target);
        if (normalized == null) {
            return CompletableFuture.completedFuture(false);
        }
        return writeAsync(et, relocateGeneratedSchemaReference(et, normalized)).thenApply(saved -> {
            if (saved) {
                et.document.setPath(normalized);
                et.refreshIcon();
                installBreakpointGutter(et);
                rememberRecentFile(normalized);
                UsageEvents.fileSaved(et.document.getFileType().docKind(), 1);
            }
            return saved;
        });
    }

    /**
     * Saves all titled, dirty documents, written on worker threads.
     *
     * @return completes on the FX thread with the number of files written
     */
    public CompletableFuture<Integer> saveAll() {
        java.util.List<CompletableFuture<Boolean>> writes = new java.util.ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && et.document.isDirty()) {
                writes.add(writeAsync(et, et.document.getPath()));
            }
        }
        CompletableFuture<Integer> result = new CompletableFuture<>();
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            int saved = (int) writes.stream().filter(w -> Boolean.TRUE.equals(w.getNow(false))).count();
            UsageEvents.fileSaved(null, saved);
            result.complete(saved);
        });
        return result;
    }

    /**
     * Undo: the model command stack in structured (Tree/Graphic) mode — XSD, XML instance
     * or JSON, whichever is active and has history — editor undo otherwise.
     */
    public void undoActive() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode.isStructured() && et.editorContext != null) {
            if (et.undoStructured()) {
                refreshSelectedNode();
            }
        } else if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode.isStructured() && et.jsonEditorContext != null && et.jsonEditorContext.canUndo()) {
            undoJson();
        } else if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode.isStructured() && et.xmlEditorContext != null && et.xmlEditorContext.canUndo()) {
            undoXml();
        } else {
            withActive(et -> et.view.undo());
        }
    }

    public void redoActive() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode.isStructured() && et.editorContext != null) {
            if (et.redoStructured()) {
                refreshSelectedNode();
            }
        } else if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode.isStructured() && et.jsonEditorContext != null && et.jsonEditorContext.canRedo()) {
            redoJson();
        } else if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode.isStructured() && et.xmlEditorContext != null && et.xmlEditorContext.canRedo()) {
            redoXml();
        } else {
            withActive(et -> et.view.redo());
        }
    }

    /** Deletes the selected node (Tree/Graphic) via the command stack. @return success */
    public boolean deleteActiveNode() {
        return editActive(et -> et.deleteNode(et.currentSelection));
    }

    /** Adds a child element under the selected node via the command stack. */
    public boolean addElementToActive(String name) {
        return editActive(et -> et.addElement(et.currentSelection, name));
    }

    /**
     * Asks for a new top-level simple or complex type of the active XSD and adds it, then opens
     * its type editor tab.
     *
     * @param kind the preselected kind
     * @return whether a type was added
     */
    public boolean newTypeInActive(org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand.Kind kind) {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.document.getFileType() == EditorFileType.XSD && et.promptGlobalType(kind);
    }

    /** Adds a container element (inline complexType + sequence) under the selected node. */
    public boolean addContainerElementToActive(String name) {
        return editActive(et -> et.addContainerElement(et.currentSelection, name));
    }

    /** Renames the selected node via the command stack. */
    public boolean renameActiveNode(String newName) {
        return editActivePreservingSelection(et -> et.renameNode(et.currentSelection, newName));
    }

    /** Changes the selected node's cardinality via the command stack. */
    public boolean changeActiveCardinality(int min, int max) {
        return editActivePreservingSelection(et -> et.changeCardinality(et.currentSelection, min, max));
    }

    /** Adds an attribute to the selected node via the command stack. */
    public boolean addAttributeToActive(String name) {
        return editActive(et -> et.addAttribute(et.currentSelection, name));
    }

    /** Adds a sequence compositor to the selected element via the command stack. */
    public boolean addSequenceToActive() {
        return editActive(et -> et.addSequence(et.currentSelection));
    }

    /** Adds a choice compositor to the selected element via the command stack. */
    public boolean addChoiceToActive() {
        return editActive(et -> et.addChoice(et.currentSelection));
    }

    /** Adds an {@code <!-- … -->} comment as a child of the selected XSD node via the command stack. */
    public boolean addCommentToActive(String content) {
        return editActive(et -> et.addComment(et.currentSelection, content));
    }

    /** Adds an all compositor to the selected element via the command stack. */
    public boolean addAllToActive() {
        return editActive(et -> et.addAll(et.currentSelection));
    }

    /** Duplicates the selected node (as a sibling) via the command stack. */
    public boolean duplicateActiveNode() {
        return editActive(et -> et.duplicate(et.currentSelection));
    }

    /** Moves the selected node up among its siblings via the command stack. */
    public boolean moveActiveNodeUp() {
        return editActive(et -> et.moveNode(et.currentSelection, -1));
    }

    /** Moves the selected node down among its siblings via the command stack. */
    public boolean moveActiveNodeDown() {
        return editActive(et -> et.moveNode(et.currentSelection, 1));
    }

    /** Copies the selected node to the shell clipboard (no model change). @return success */
    public boolean copyActiveNode() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.currentSelection != null) {
            nodeClipboard.copy(et.currentSelection);
            return true;
        }
        return false;
    }

    /** Cuts the selected node to the shell clipboard (the original is removed on paste). */
    public boolean cutActiveNode() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.currentSelection != null) {
            nodeClipboard.cut(et.currentSelection);
            return true;
        }
        return false;
    }

    /** Pastes the clipboard's node under the selected node via the command stack. */
    public boolean pasteIntoActive() {
        return editActive(et -> et.pasteNode(et.currentSelection));
    }

    /** @return whether the shell clipboard currently holds a node to paste. */
    public boolean canPaste() {
        return nodeClipboard.hasContent();
    }

    /** Changes the selected node's type via the command stack. */
    public boolean changeActiveType(String newType) {
        return editActivePreservingSelection(et -> et.changeType(et.currentSelection, newType));
    }

    /** Changes the selected attribute's use (required/optional/prohibited). */
    public boolean changeActiveUse(String use) {
        return editActivePreservingSelection(et -> et.changeUse(et.currentSelection, use));
    }

    /** Changes the selected node's form (qualified/unqualified). */
    public boolean changeActiveForm(String form) {
        return editActivePreservingSelection(et -> et.changeForm(et.currentSelection, form));
    }

    /** Changes the selected element's constraints (nillable / abstract / fixed). */
    public boolean changeActiveConstraints(boolean nillable, boolean abstractFlag, String fixed) {
        return editActivePreservingSelection(
                et -> et.changeConstraints(et.currentSelection, nillable, abstractFlag, fixed));
    }

    /** Changes the selected element's substitution group. */
    public boolean changeActiveSubstitutionGroup(String substitutionGroup) {
        return editActivePreservingSelection(
                et -> et.changeSubstitutionGroup(et.currentSelection, substitutionGroup));
    }

    /** Changes the selected element/attribute's {@code default} value via the command stack. */
    public boolean changeActiveDefaultValue(String defaultValue) {
        return editActivePreservingSelection(et -> et.changeDefaultValue(et.currentSelection, defaultValue));
    }

    /** Changes the selected element/complex-type's {@code block} attribute via the command stack. */
    public boolean changeActiveBlock(String block) {
        return editActivePreservingSelection(et -> et.changeBlock(et.currentSelection, block));
    }

    /** Changes the selected complex type's {@code final} attribute via the command stack. */
    public boolean changeActiveFinal(String finalValue) {
        return editActivePreservingSelection(et -> et.changeFinal(et.currentSelection, finalValue));
    }

    /** Toggles the selected complex type's {@code mixed} content flag via the command stack. */
    public boolean changeActiveMixed(boolean mixed) {
        return editActivePreservingSelection(et -> et.changeMixed(et.currentSelection, mixed));
    }

    /** Changes the selected element/attribute's {@code ref} attribute via the command stack. */
    public boolean changeActiveRef(String ref) {
        return editActivePreservingSelection(et -> et.changeRef(et.currentSelection, ref));
    }

    /** Changes the selected {@code xs:any}/{@code xs:anyAttribute} wildcard properties via the command stack. */
    public boolean changeActiveWildcard(String namespace,
            org.fxt.freexmltoolkit.controls.v2.model.XsdAny.ProcessContents processContents) {
        return editActivePreservingSelection(et -> et.changeWildcard(et.currentSelection, namespace, processContents));
    }

    /** Changes the selected node's documentation text. */
    public boolean changeActiveDocumentation(String documentation) {
        return editActivePreservingSelection(et -> et.changeDocumentation(et.currentSelection, documentation));
    }

    /** Changes the selected XSD node's {@code xs:appinfo} content via the command stack. */
    public boolean changeActiveAppinfo(String appinfo) {
        return editActivePreservingSelection(et -> et.changeAppinfo(et.currentSelection, appinfo));
    }

    /**
     * Changes the selected XSD node's {@code xs:appinfo} from a structured
     * {@link org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo} via the command stack.
     * Preserves complex (raw-XML) appinfo entries that the string form cannot round-trip.
     */
    public boolean changeActiveAppinfo(org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo appinfo) {
        return editActivePreservingSelection(et -> et.changeAppinfo(et.currentSelection, appinfo));
    }

    /** Changes a schema-level attribute (target namespace / version / form defaults) via the command stack. */
    public boolean changeActiveSchemaProperty(
            org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeSchemaPropertyCommand.Property property,
            String value) {
        return editActivePreservingSelection(et -> et.changeSchemaProperty(et.currentSelection, property, value));
    }

    /** @return the schema root of the active XSD document, as an {@code XsdSchema}, if any. */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.model.XsdSchema> getActiveSchema() {
        return getActiveSchemaRoot()
                .filter(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema)
                .map(n -> (org.fxt.freexmltoolkit.controls.v2.model.XsdSchema) n);
    }

    /** Replaces the selected XSD node's per-language {@code xs:documentation} entries. */
    public boolean changeActiveDocumentations(
            java.util.List<org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation> docs) {
        return editActivePreservingSelection(et -> et.changeDocumentations(et.currentSelection, docs));
    }

    /** Edits the selected XSD comment node's content via the command stack. */
    public boolean changeActiveComment(String content) {
        return editActivePreservingSelection(et -> et.editComment(et.currentSelection, content));
    }

    /** Deletes an identity-constraint / assertion node (key/keyref/unique/assert) via the command stack. */
    public boolean deleteConstraintNode(XsdNode node) {
        return editActivePreservingSelection(et -> et.deleteNode(node));
    }

    /** Outcome of {@link #removeUnusedSchemaComponents}. */
    public record ComponentRemoval(int removed, java.util.List<String> skippedFromInclude, java.util.List<String> notFound) {
    }

    /**
     * Deletes global schema components (types, groups, attribute groups) from {@code doc} as one
     * undoable command — the Schema Analysis "Remove unused…" action. Works whichever tab is
     * selected: the target is located by document identity, not by the active tab. Components
     * declared in {@code xs:include}d files are skipped (the serializer never writes inlined
     * include content back), as are names that no longer exist in the live model.
     *
     * @param doc  the open XSD document to edit
     * @param keys the components to delete
     * @return the outcome, or empty when the document is not open, cannot be parsed, or uses
     *         {@code xs:redefine}/{@code xs:override} (their reference semantics are not modeled)
     */
    public Optional<ComponentRemoval> removeUnusedSchemaComponents(
            OpenDocument doc,
            java.util.List<org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey> keys) {
        if (doc == null || keys == null || keys.isEmpty()) {
            return Optional.empty();
        }
        EditorTab target = null;
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == doc) {
                target = et;
                break;
            }
        }
        if (target == null) {
            return Optional.empty();
        }
        target.ensureModelParsed();
        if (target.editorContext == null) {
            return Optional.empty();
        }
        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = target.editorContext.getSchema();
        java.util.Map<org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey, XsdNode> globals =
                new java.util.HashMap<>();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRedefine
                    || child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdOverride) {
                return Optional.empty();
            }
            var key = org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.keyOf(child);
            if (key != null) {
                globals.putIfAbsent(key, child);
            }
        }
        java.util.List<XsdNode> nodes = new java.util.ArrayList<>();
        java.util.List<String> skipped = new java.util.ArrayList<>();
        java.util.List<String> notFound = new java.util.ArrayList<>();
        for (var key : keys) {
            XsdNode node = globals.get(key);
            if (node == null) {
                notFound.add(key.localName());
            } else if (node.isFromInclude()) {
                skipped.add(key.localName());
            } else {
                nodes.add(node);
            }
        }
        if (nodes.isEmpty()) {
            return Optional.of(new ComponentRemoval(0, skipped, notFound));
        }
        var command = new org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodesCommand(
                nodes, "Remove " + nodes.size() + " unused component" + (nodes.size() == 1 ? "" : "s"));
        boolean ok = target.executeAndApply(command);
        return Optional.of(new ComponentRemoval(ok ? nodes.size() : 0, skipped, notFound));
    }

    /** Adds an identity constraint (key/keyref/unique) to the selected element via the command stack. */
    public boolean addActiveIdentityConstraint(
            org.fxt.freexmltoolkit.controls.v2.editor.commands.AddIdentityConstraintCommand.Kind kind,
            String name, String selectorXpath, java.util.List<String> fieldXpaths, String refer) {
        return editActivePreservingSelection(
                et -> et.addIdentityConstraint(et.currentSelection, kind, name, selectorXpath, fieldXpaths, refer));
    }

    /** Adds an {@code xs:import}/{@code xs:include} to the selected schema root via the command stack. */
    public boolean addActiveSchemaReference(
            org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSchemaReferenceCommand.Kind kind,
            String namespace, String schemaLocation) {
        return editActivePreservingSelection(
                et -> et.addSchemaReference(et.currentSelection, kind, namespace, schemaLocation));
    }

    /** Removes an {@code xs:import}/{@code xs:include} directive node via the command stack. */
    public boolean removeActiveSchemaReference(XsdNode node) {
        return editActivePreservingSelection(et -> et.deleteNode(node));
    }

    /** Edits a facet's value on the selected node's restriction (Type &amp; Facets table). */
    public boolean editActiveFacet(org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet, String newValue) {
        return editActivePreservingSelection(et -> et.editFacet(facet, newValue));
    }

    /** Deletes a facet (incl. pattern/enumeration/assertion) from the selected node's restriction. */
    public boolean deleteActiveFacet(org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet) {
        return editActivePreservingSelection(et -> et.deleteFacet(facet));
    }

    /** Adds a facet of the given type/value to the selected node's restriction. */
    public boolean addActiveFacet(org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType type, String value) {
        return editActivePreservingSelection(et -> et.addFacet(type, value));
    }

    // ----- XML instance editing (Grid selection) ---------------------------

    /** Renames the selected XML element via the XML command stack. */
    public boolean renameActiveXmlNode(String name) {
        return editActiveXml(el ->
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand(el, name));
    }

    /** Sets the selected XML element's text content. */
    public boolean setActiveXmlElementText(String text) {
        return editActiveXml(el ->
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand(el, text));
    }

    /** Sets (adds or updates) an attribute on the selected XML element. */
    public boolean setActiveXmlAttribute(String name, String value) {
        return editActiveXml(el ->
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand(el, name, value));
    }

    /** Removes an attribute from the selected XML element. */
    public boolean removeActiveXmlAttribute(String name) {
        return editActiveXml(el ->
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RemoveAttributeCommand(el, name));
    }

    /** Renames an attribute key on the selected XML element (value + position preserved). */
    public boolean renameActiveXmlAttribute(String oldName, String newName) {
        return editActiveXml(el ->
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameAttributeCommand(
                        el, oldName, newName));
    }

    /** Sets the selected XML element's namespace prefix + URI (maintaining the xmlns declaration). */
    public boolean setActiveElementNamespace(String prefix, String uri) {
        return editActiveXml(el ->
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementNamespaceCommand(
                        el, prefix, uri));
    }

    /** Sets the selected comment node's text. */
    public boolean setActiveCommentText(String text) {
        return editActiveXmlNode(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment c
                ? new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetCommentTextCommand(c, text) : null);
    }

    /** Sets the selected CDATA section's content. */
    public boolean setActiveCDataText(String text) {
        return editActiveXmlNode(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData c
                ? new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetCDataTextCommand(c, text) : null);
    }

    /** Sets the selected text node's content. */
    public boolean setActiveTextContent(String text) {
        return editActiveXmlNode(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText t
                ? new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetTextCommand(t, text) : null);
    }

    /** Sets the selected processing instruction's target + data. */
    public boolean setActiveProcessingInstruction(String target, String data) {
        return editActiveXmlNode(n ->
                n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction pi
                        ? new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands
                                .SetProcessingInstructionCommand(pi, target, data) : null);
    }

    /** Sets the selected document's XML declaration (version / encoding / standalone). */
    public boolean setActiveXmlDeclaration(String version, String encoding, Boolean standalone) {
        return editActiveXmlNode(n ->
                n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc
                        ? new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands
                                .SetXmlDeclarationCommand(doc, version, encoding, standalone) : null);
    }

    /** Undoes the last XML-instance edit on the shared context and round-trips to text. */
    public boolean undoXml() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et && et.undoXml()) {
            activeXmlNode.set(null);
            refreshSelectedNode();
            rebuildActiveXmlTree();
            return true;
        }
        return false;
    }

    /** Redoes the last undone XML-instance edit on the shared context and round-trips to text. */
    public boolean redoXml() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et && et.redoXml()) {
            activeXmlNode.set(null);
            refreshSelectedNode();
            rebuildActiveXmlTree();
            return true;
        }
        return false;
    }

    /** Undoes the last JSON edit on the shared context and round-trips to text. */
    public boolean undoJson() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et && et.undoJson()) {
            activeJsonNode.set(null);
            refreshSelectedNode();
            rebuildActiveJsonGrid();
            return true;
        }
        return false;
    }

    /** Redoes the last undone JSON edit on the shared context and round-trips to text. */
    public boolean redoJson() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et && et.redoJson()) {
            activeJsonNode.set(null);
            refreshSelectedNode();
            rebuildActiveJsonGrid();
            return true;
        }
        return false;
    }

    /** Rebuilds the JSON grid rows from the shared model (the tree listens to the context itself). */
    private void rebuildActiveJsonGrid() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode == ViewMode.GRAPHIC && et.jsonGridView != null) {
            et.jsonGridView.rebuild();
        }
    }

    /** Runs a command on the selected JSON node via the JSON command stack + round-trip. */
    private boolean editActiveJson(
            java.util.function.Function<org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode,
                    org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand> factory) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.currentJsonSelection != null) {
            var command = factory.apply(et.currentJsonSelection);
            if (command == null || !et.editJson(command)) {
                return false;
            }
            // Same node object edited in place: force the inspector to re-read it.
            activeJsonNode.set(null);
            refreshSelectedNode();
            rebuildActiveJsonGrid();
            return true;
        }
        return false;
    }

    /** Renames the selected JSON object property (Inspector); rejects blank/duplicate keys. */
    public boolean renameActiveJsonKey(String newKey) {
        String key = newKey == null ? "" : newKey.trim();
        return !key.isEmpty() && editActiveJson(node -> node.getParent() instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject
                && !key.equals(node.getKey())
                ? new org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand(node, key) : null);
    }

    /**
     * Sets the selected JSON primitive's value from text, keeping its type (numbers and
     * booleans are parsed; unparsable text is rejected).
     */
    public boolean setActiveJsonValue(String text) {
        return editActiveJson(node -> {
            if (!(node instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive primitive)) {
                return null;
            }
            Object value;
            try {
                value = org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonValueParser.parse(text, primitive.getNodeType());
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (java.util.Objects.equals(value, primitive.getValue())) {
                return null;
            }
            return new org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand(primitive, value);
        });
    }

    /** Changes the selected JSON value's type (Inspector): primitive conversions or a fresh empty node. */
    public boolean changeActiveJsonType(org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType type) {
        return editActiveJson(node -> {
            if (type == null || node.getNodeType() == type
                    || node.getParent() == null
                    || node.getParent() instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument) {
                return null;
            }
            boolean primitiveTarget = type != org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType.OBJECT
                    && type != org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType.ARRAY;
            if (node instanceof org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive primitive && primitiveTarget) {
                return new org.fxt.freexmltoolkit.controls.jsoneditor.commands.ChangeValueTypeCommand(primitive, type);
            }
            org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode replacement = switch (type) {
                case NUMBER -> new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive(java.math.BigInteger.ZERO);
                case BOOLEAN -> new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive(Boolean.FALSE);
                case NULL -> org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive.nullValue();
                case OBJECT -> new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject();
                case ARRAY -> new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray();
                default -> new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive("");
            };
            return new org.fxt.freexmltoolkit.controls.jsoneditor.commands.ReplaceNodeCommand(node, replacement);
        });
    }

    /** Rebuilds the XML Tree from the shared model (undo/redo may have changed structure). */
    private void rebuildActiveXmlTree() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode == ViewMode.TREE && et.xmlInstanceTreeView != null) {
            et.xmlInstanceTreeView.setModel(et.xmlEditorContext);
        }
    }

    /** Refreshes the XML Tree cells in place (labels reflect edits) when the Tree view is active. */
    private void refreshActiveXmlTree() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode == ViewMode.TREE && et.xmlInstanceTreeView != null) {
            et.xmlInstanceTreeView.refresh();
        }
    }

    /** Runs a command on the selected XML node (any type) via the XML command stack + round-trip. */
    private boolean editActiveXmlNode(
            java.util.function.Function<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode,
                    org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand> factory) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.currentXmlSelection != null) {
            var command = factory.apply(et.currentXmlSelection);
            if (command == null || !et.editXml(command)) {
                return false;
            }
            activeXmlNode.set(null);
            refreshSelectedNode();
            refreshActiveXmlTree();
            return true;
        }
        return false;
    }

    private boolean editActiveXml(
            java.util.function.Function<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement,
                    org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand> factory) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.currentXmlSelection
                        instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement el) {
            boolean ok = et.editXml(factory.apply(el));
            if (ok) {
                // Same node object edited in place: force the inspector to re-read it.
                activeXmlNode.set(null);
                refreshSelectedNode();
                refreshActiveXmlTree();
            }
            return ok;
        }
        return false;
    }

    private boolean editActive(java.util.function.Predicate<EditorTab> edit) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            boolean ok = edit.test(et);
            if (ok) {
                // applyModelChange clears the selection; in the Text view re-resolve the node at the
                // caret so the inspector keeps showing it (Tree/Graphic re-render their selection).
                if (et.viewMode == ViewMode.TEXT && et.document.getFileType() == EditorFileType.XSD) {
                    et.resolveCaretXsdNode();
                }
                refreshSelectedNode();
            }
            return ok;
        }
        return false;
    }

    /**
     * Like {@link #editActive} but keeps the same node selected after the edit. Property
     * edits (rename, type, cardinality, use, …) preserve the model node, so the inspector
     * should keep showing it instead of clearing (which {@code applyModelChange} would cause).
     */
    private boolean editActivePreservingSelection(java.util.function.Predicate<EditorTab> edit) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            XsdNode node = et.currentSelection;
            boolean ok = edit.test(et);
            if (ok && node != null) {
                et.reselect(node);
            }
            if (ok) {
                refreshSelectedNode();
            }
            return ok;
        }
        return false;
    }

    /**
     * Pretty-prints the active document (XML or JSON) off the FX thread: formatting a multi-MB document
     * took seconds. The result replaces the text only if nothing was typed meanwhile.
     *
     * @return completes on the FX thread with whether the text was formatted
     */
    public CompletableFuture<Boolean> formatActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return CompletableFuture.completedFuture(false);
        }
        boolean json = et.document.getFileType() == EditorFileType.JSON;
        return transformActiveText(et, text -> json
                ? new org.fxt.freexmltoolkit.service.JsonService().formatJson(text, 2)
                : org.fxt.freexmltoolkit.service.XmlService.prettyFormat(text, 2))
                .thenApply(done -> {
                    UsageEvents.formatted(et.document.getFileType().docKind(), done);
                    return done;
                });
    }

    /**
     * Minifies the active document (XML: drop inter-tag whitespace; JSON: compact) off the FX thread.
     *
     * @return completes on the FX thread with whether the text was minified
     */
    public CompletableFuture<Boolean> minifyActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return CompletableFuture.completedFuture(false);
        }
        boolean json = et.document.getFileType() == EditorFileType.JSON;
        return transformActiveText(et, text -> json
                ? new org.fxt.freexmltoolkit.service.JsonService().compactJson(text)
                : text.replaceAll(">\\s+<", "><").strip());
    }

    /** A whole-text rewrite (format/minify) that may fail on invalid content. */
    @FunctionalInterface
    private interface TextRewrite {
        String apply(String text) throws Exception;
    }

    /**
     * Applies {@code rewrite} to the tab's text on a worker thread (the tab shows its loading overlay
     * if it takes a while) and puts the result into the editor on the FX thread, unless the text was
     * edited meanwhile or the rewrite failed or produced nothing.
     */
    private CompletableFuture<Boolean> transformActiveText(EditorTab et, TextRewrite rewrite) {
        String text = et.view.getText();
        long revision = et.textRevision;
        et.beginLoading();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String rewritten;
            try {
                rewritten = rewrite.apply(text);
            } catch (Exception e) {
                rewritten = null; // invalid content: leave the text untouched
            }
            String output = rewritten;
            Platform.runLater(() -> {
                et.endLoading();
                boolean apply = output != null && !output.isBlank() && et.textRevision == revision
                        && !isAbandoned(et);
                if (apply) {
                    et.view.setText(output);
                    et.document.setDirty(true);
                }
                result.complete(apply);
            });
        });
        return result;
    }

    /** @return the active editor's code area, or {@code null} (no editor tab in front). */
    public org.fxmisc.richtext.CodeArea getActiveCodeArea() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.view.getCodeArea() : null;
    }

    /**
     * The active structured view as a target for the shell's search bar: the XSD
     * tree/diagram in Tree/Graphic mode, the instance grid for XML-family files in
     * Graphic mode. {@code null} in Text mode (the search bar binds the code area
     * instead) and for views without search support (XML/JSON trees).
     *
     * @return the active view's search target, or {@code null}
     */
    public org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget getActiveSearchTarget() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return null;
        }
        boolean xsd = et.document.getFileType() == EditorFileType.XSD;
        boolean json = et.document.getFileType() == EditorFileType.JSON;
        return switch (et.viewMode) {
            // Preview has no structured search target; Ctrl+F binds the (hidden) code area.
            case TEXT, PREVIEW -> null;
            case TREE -> xsd ? et.treeView : (json ? et.jsonTreeView : null);
            case GRAPHIC -> xsd ? et.xsdGraphView
                    : json ? (et.jsonGridView != null ? et.jsonGridView.getSearchTarget() : null)
                    : (et.xmlGridView != null ? et.xmlGridView.getSearchTarget() : null);
        };
    }

    /** Test seam: the text the active tab's HTML preview last rendered, or {@code null}. */
    String activePreviewedTextForTest() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.lastPreviewedText : null;
    }

    /** @return the active editor tab's view, or {@code null} if no editor tab is in front. */
    EditorView activeEditorView() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.view : null;
    }

    /**
     * Binds a schema to the active document — an XSD (schema-aware IntelliSense +
     * validation) for the XML family, a JSON Schema (validation) for JSON documents.
     *
     * @return completes on the FX thread with {@code true} once bound (the schema is parsed off-thread)
     */
    public CompletableFuture<Boolean> setSchemaForActiveDocument(File xsd) {
        return bindSchemaToActiveDocument(xsd, SchemaRebindPolicy.SchemaBindingOrigin.MANUAL,
                SchemaSource.MANUAL, null, null);
    }

    /**
     * Binds the schema a generated document was produced from (Generate Sample XML). The
     * document's root element references that schema only by file name — resolvable once
     * saved next to it, but not for the unsaved tab — so the binding is established directly,
     * and validation works on the very first run. The binding counts as auto-detected from
     * the declared reference (not as a manual choice): the reconcile keeps it while the
     * declaration is unchanged, and re-detects when the user edits or the first save
     * relativizes the reference.
     *
     * @return completes on the FX thread with {@code true} once bound
     */
    public CompletableFuture<Boolean> bindGeneratedSchemaToActiveDocument(File xsd) {
        if (xsd == null) {
            return CompletableFuture.completedFuture(false);
        }
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return CompletableFuture.completedFuture(false);
        }
        // What the reconcile will sniff from the buffer (no base directory: untitled).
        String declared = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.XmlService.class)
                .getSchemaNameFromXmlContent(et.view.getText(), null)
                .orElse(null);
        return bindSchemaToActiveDocument(xsd, SchemaRebindPolicy.SchemaBindingOrigin.AUTO,
                SchemaSource.DECLARED, xsd.getAbsolutePath(), declared);
    }

    /**
     * Binds {@code xsd} (or clears the binding for {@code null}) on the active tab. Parsing a
     * schema can take seconds to minutes for large XSDs, so it runs on a worker thread while
     * the status bar shows LOADING; the FX thread only applies the result. Call on the FX thread.
     *
     * @return completes on the FX thread with {@code true} once the schema is bound, or
     *         {@code false} if it failed to load, the tab was closed, or a newer bind superseded it
     */
    private CompletableFuture<Boolean> bindSchemaToActiveDocument(File xsd,
                                                                  SchemaRebindPolicy.SchemaBindingOrigin origin,
                                                                  SchemaSource source, String sourceDetail,
                                                                  String declared) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et) || !et.view.supportsSchema()) {
            return CompletableFuture.completedFuture(false);
        }
        long request = et.schemaBindRequest.incrementAndGet();
        et.schemaBindingGen.incrementAndGet(); // supersede queued schema reconciles
        if (xsd == null) {
            et.view.clearSchema(); // loadSchema(null) is a no-op failure in the views
            et.schemaFile = null;
            et.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.NONE;
            applySchemaBindingUi(et, null, SchemaSource.NONE, null);
            return CompletableFuture.completedFuture(true);
        }
        publishSchemaStatus(et, SchemaStatus.LOADING);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            boolean bound = false;
            // Same per-tab lock as the detections: the Xerces DOM behind the schema pipeline is
            // not thread-safe, and holding it until the binding fields are set keeps a validation
            // reconcile from re-detecting over the user's choice in between.
            if (acquireSchemaDetection(et, () -> et.schemaBindRequest.get() != request)) {
                try {
                    if (et.schemaBindRequest.get() == request) {
                        bound = loadSchemaSafely(et, xsd);
                        // re-checked after the (long) parse: a newer bind or a clear may have arrived
                        if (bound && et.schemaBindRequest.get() == request) {
                            et.schemaFile = xsd;
                            et.schemaOrigin = origin;
                            if (origin == SchemaRebindPolicy.SchemaBindingOrigin.AUTO) {
                                // Behaves like a detection result: an unchanged declaration is
                                // KEEP-fast-pathed. Manual bindings leave the last detection alone
                                // (MANUAL never rebinds).
                                et.lastDetectedSchemaLocation = declared;
                            }
                        }
                    }
                } finally {
                    et.schemaDetecting.set(false);
                }
            }
            boolean applied = bound;
            Platform.runLater(() -> {
                if (isAbandoned(et) || et.schemaBindRequest.get() != request) {
                    result.complete(false); // closed, or a newer bind owns the status now
                    return;
                }
                if (applied) {
                    applySchemaBindingUi(et, xsd, source, sourceDetail);
                } else {
                    // the user picked a schema that failed to load — surface it in the status bar
                    publishSchemaStatus(et, SchemaStatus.ERROR);
                }
                result.complete(applied);
            });
        });
        return result;
    }

    /** FX-thread half of a successful bind or clear: caches, active-schema mirror, status, provider. */
    private void applySchemaBindingUi(EditorTab et, File xsd, SchemaSource source, String sourceDetail) {
        et.view.invalidateIntelliSenseCache();
        if (et.isSelected()) {
            activeSchema.set(xsd);
        }
        publishSchemaSource(et, xsd == null ? SchemaSource.NONE : source, xsd == null ? null : sourceDetail);
        publishSchemaStatus(et, xsd == null ? SchemaStatus.NONE : SchemaStatus.READY);
        loadXmlSchemaProviderAsync(et, xsd);
    }

    /** {@code view.loadSchema} on a worker thread; an unexpected failure counts as "not bound". */
    private static boolean loadSchemaSafely(EditorTab tab, File xsd) {
        try {
            return tab.view.loadSchema(xsd);
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError e) {
            logger.warn("Could not load schema {}: {}", xsd, e.toString());
            if (e instanceof OutOfMemoryError) {
                org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportError(e, "editor.schema_load");
            }
            return false;
        }
    }

    /**
     * Takes the tab's {@link EditorTab#schemaDetecting} lock on a worker thread, waiting for a
     * running detection (bounded, like {@link #redetectSchemaForActiveDocument()}).
     *
     * @param giveUp checked while waiting; {@code true} means the caller has been superseded
     * @return {@code true} if the lock is now held (release it with {@code schemaDetecting.set(false)})
     */
    private static boolean acquireSchemaDetection(EditorTab tab, java.util.function.BooleanSupplier giveUp) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        while (!tab.schemaDetecting.compareAndSet(false, true)) {
            if (giveUp.getAsBoolean() || System.nanoTime() > deadline || isAbandoned(tab)) {
                return false;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    // ----- navigation ------------------------------------------------------

    /** @return the Schematron file bound to the active document, or {@code null}. */
    public File getActiveSchematron() {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et ? et.schematronFile : null;
    }

    /** Binds a Schematron file to the active document for the Validation activity. */
    public void setActiveSchematron(File schematron) {
        withActive(et -> et.schematronFile = schematron);
    }

    /** Switches to Text mode and moves the caret to the given 1-based line (jump-to-problem). */
    public void goToLine(int line) {
        goToLine(line, 0);
    }

    /**
     * Switches to Text mode and moves the caret to the given 1-based line and column (a column
     * below 1 means the line start; one past the line end is clamped to it).
     */
    public void goToLine(int line, int column) {
        setActiveViewMode(ViewMode.TEXT);
        withActive(et -> {
            var codeArea = et.view.getCodeArea();
            int paragraphs = codeArea.getParagraphs().size();
            int paragraph = Math.max(0, Math.min(line - 1, Math.max(0, paragraphs - 1)));
            int col = Math.max(0, Math.min(column - 1, codeArea.getParagraphLength(paragraph)));
            codeArea.moveTo(paragraph, col);
            codeArea.requestFollowCaret();
            et.view.getNode().requestFocus();
        });
    }

    /**
     * Opens {@code path} (focusing an existing tab) and, once the text has loaded,
     * switches to Text mode and selects the char range {@code [start, end)} — the
     * jump-to-search-match navigation. When {@code expected} is non-null and the
     * text at that range no longer equals it (the file changed since the search),
     * the selection degrades to {@link #goToLine(int)} with {@code fallbackLine}.
     */
    public void openFileAndSelect(java.nio.file.Path path, int start, int end,
                                  String expected, int fallbackLine) {
        if (path == null) {
            return;
        }
        openFile(path);
        selectRangeAfterLoad(path, start, end, expected, fallbackLine, 80);
    }

    private void selectRangeAfterLoad(java.nio.file.Path path, int start, int end,
                                      String expected, int fallbackLine, int attemptsLeft) {
        var doc = getActiveDocument();
        String text = getActiveText().orElse(null);
        boolean loaded = doc.isPresent() && path.equals(doc.get().getPath())
                && text != null && !text.isEmpty();
        if (loaded) {
            setActiveViewMode(ViewMode.TEXT);
            boolean valid = start >= 0 && end >= start && end <= text.length()
                    && (expected == null || text.substring(start, end).equals(expected));
            withActive(et -> {
                var codeArea = et.view.getCodeArea();
                if (valid) {
                    codeArea.selectRange(start, end);
                    codeArea.requestFollowCaret();
                    et.view.getNode().requestFocus();
                }
            });
            if (!valid && fallbackLine > 0) {
                goToLine(fallbackLine);
            }
            return;
        }
        if (attemptsLeft > 0) {
            // Same spaced-retry pattern as revealInGraphAfterLoad: the file load is async.
            javafx.animation.PauseTransition pause =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
            pause.setOnFinished(e -> selectRangeAfterLoad(path, start, end,
                    expected, fallbackLine, attemptsLeft - 1));
            pause.play();
        }
    }

    /** Moves the active editor's caret to the given offset (e.g. jump-to-node). */
    public void moveActiveCaretTo(int position) {
        withActive(et -> {
            var codeArea = et.view.getCodeArea();
            codeArea.moveTo(Math.max(0, Math.min(position, codeArea.getLength())));
        });
    }

    /**
     * Attaches an extra gutter factory (breakpoint markers / execution arrow) to the
     * currently active editor tab.
     *
     * @return {@code true} if the active tab is an XML-family editor that accepted it.
     */
    public boolean setActiveEditorGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et && setGutterContributor(et, "xslt-debugger", factory);
    }

    /**
     * Registers/replaces/removes one feature's gutter contribution on a tab; the
     * editor's single extra-gutter slot receives the composite (or {@code null} when
     * no feature contributes, so the gutter column collapses as before).
     */
    private static boolean setGutterContributor(EditorTab et, String key,
            java.util.function.IntFunction<javafx.scene.Node> factory) {
        et.gutter.set(key, factory);
        return et.view.setExtraGutterFactory(et.gutter.isEmpty() ? null : et.gutter);
    }

    /** Tints the debugger's current execution line on a document's editor tab ({@code -1} clears). */
    public void setDebugLineFor(OpenDocument document, int line) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                et.view.setDebugCurrentLine(line);
                return;
            }
        }
    }

    /**
     * Shows the paused values after their declaration lines on a stylesheet's editor tab
     * ({@code null} clears): see {@link org.fxt.freexmltoolkit.debugger.InlineValues}.
     */
    private void setDebugInlineValuesFor(OpenDocument document,
            org.fxt.freexmltoolkit.debugger.PausedSnapshot snapshot) {
        EditorTab tab = editorTabOf(document);
        if (tab == null) {
            return;
        }
        java.util.Map<Integer, String> values = snapshot == null ? java.util.Map.of()
                : org.fxt.freexmltoolkit.debugger.InlineValues.compute(
                        getDocumentText(document).orElse(""), snapshot.lineNumber(), snapshot);
        tab.view.setDebugInlineValues(values);
    }

    /** Re-renders the gutter on a specific open document's editor tab (if open). */
    public void refreshGutterFor(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                et.view.refreshGutter();
                return;
            }
        }
    }

    /** Attaches a gutter factory to a specific open document's editor tab (if open). */
    public boolean setGutterFactoryFor(OpenDocument document,
            java.util.function.IntFunction<javafx.scene.Node> factory) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                return setGutterContributor(et, "xslt-debugger", factory);
            }
        }
        return false;
    }

    /** Focuses the tab backing the given document, if open. */
    public void selectDocument(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
    }

    // ----- XSLT debugger ----------------------------------------------------

    /** Key of the DEBUG pane in the bottom dock. */
    public static final String DEBUG_DOCK_KEY = "debug";

    /** A stylesheet module opened during a debug session, with its breakpoint gutter. */
    private record DebugModule(OpenDocument document, org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory gutter) { }

    /** Everything belonging to the one active debug session. */
    private static final class XsltDebugSession {
        final org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController controller;
        final org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane pane;
        final File mainFile;
        final String mainPath;
        /** Modules by normalised file path, the main stylesheet first. */
        final java.util.Map<String, DebugModule> modules = new java.util.LinkedHashMap<>();
        /** Normalised paths of the modules the main stylesheet includes/imports (static scan). */
        final java.util.Set<String> resolvedModules;
        /** Value popups installed on the module editors for this session. */
        final java.util.Map<OpenDocument, org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugHover> hovers =
                new java.util.HashMap<>();
        /** The run's input: its file (when it came from one) and/or its open document. */
        Path inputFile;
        OpenDocument inputDocument;
        /** The input document whose context-node line is marked, and that line. */
        OpenDocument markedInput;
        int markedInputLine;

        XsltDebugSession(org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController controller,
                         org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane pane, File mainFile,
                         java.util.Set<String> resolvedModules) {
            this.controller = controller;
            this.pane = pane;
            this.mainFile = mainFile;
            this.mainPath = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(mainFile.getAbsolutePath());
            this.resolvedModules = java.util.Set.copyOf(resolvedModules);
        }
    }

    private XsltDebugSession activeDebug;

    /** @return {@code true} while an XSLT debug session is alive (its pane is in the dock). */
    public boolean isDebugSessionActive() {
        return activeDebug != null;
    }

    /** @return the DEBUG pane of the active session, or {@code null}. */
    public org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane activeDebugPane() {
        return activeDebug == null ? null : activeDebug.pane;
    }

    /**
     * Routes the debugger keys (F5/F7/F8/Shift+F8 while paused) to the active session.
     *
     * @return {@code true} if the key was consumed by the debugger
     */
    public boolean handleDebugShortcut(javafx.scene.input.KeyEvent event) {
        return activeDebug != null && activeDebug.pane.handleShortcut(event);
    }

    /**
     * Launches an interactive XSLT debug session: opens the stylesheet as an editor
     * document with a breakpoint gutter, shows the DEBUG pane in the bottom dock (the
     * stylesheet stays visible above it) and starts the run — paused at the first
     * instruction unless breakpoints exist. Only one session at a time: starting another
     * ends the current one first.
     */
    public void startXsltDebug(java.io.File xsltFile, String xml,
            java.util.Map<String, Object> parameters,
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format) {
        startXsltDebug(xsltFile, xml, parameters, format, null);
    }

    /**
     * Like {@link #startXsltDebug(File, String, java.util.Map, org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat)},
     * additionally handing every finished run's result to {@code onFinished} (FX thread) —
     * the PDF/FOP panel uses it to pick up the XSL-FO a debugged stylesheet produced.
     */
    public void startXsltDebug(java.io.File xsltFile, String xml,
            java.util.Map<String, Object> parameters,
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format,
            java.util.function.Consumer<org.fxt.freexmltoolkit.service.XsltTransformationResult> onFinished) {
        if (activeDebug != null) {
            endXsltDebug();
        }

        // 1. Open (or focus) the stylesheet as an editor document.
        openFile(xsltFile);
        OpenDocument xsltDoc = getActiveDocument().orElse(null);
        if (xsltDoc == null) {
            return;
        }

        // Read the stylesheet up front: if it cannot be read, surface a clear error and abort
        // rather than silently running Saxon on an empty stylesheet.
        try {
            java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                    javafx.scene.control.Alert.AlertType.ERROR, "XSLT Debug",
                    "Could not start the debugger", "Could not read stylesheet: " + e.getMessage());
            if (getScene() != null) {
                alert.initOwner(getScene().getWindow());
            }
            alert.showAndWait();
            return;
        }

        // 2. Controller + session: restore the persisted breakpoints — the main stylesheet's
        //    (plus any legacy aggregate stored under it) and those set in the editor on the
        //    modules it includes — attach the gutter to the main module and to every module
        //    already open, and pause on entry when there is nothing to stop at.
        var controller = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController();
        // An .xq file is debugged as XQuery, a Schematron through its compiled XSLT (same
        // session, dock, gutter and watches).
        EditorFileType debugType = EditorFileType.fromFileName(xsltFile.getName());
        if (debugType == EditorFileType.XQUERY) {
            controller.setLanguage(org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController.Language.XQUERY);
        } else if (debugType == EditorFileType.SCHEMATRON) {
            controller.setLanguage(org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController.Language.SCHEMATRON);
        }
        var session = controller.getSession();
        for (org.fxt.freexmltoolkit.debugger.Breakpoint bp
                : org.fxt.freexmltoolkit.debugger.BreakpointStore.load(xsltFile.getAbsolutePath())) {
            session.addBreakpoint(bp);
        }
        java.util.Set<String> resolvedModules =
                org.fxt.freexmltoolkit.debugger.StylesheetModules.resolve(xsltFile.toPath());
        for (String module : resolvedModules) {
            mergeStoredBreakpoints(session, module);
        }
        org.fxt.freexmltoolkit.debugger.WatchStore.loadSymbols(xsltFile.getAbsolutePath())
                .forEach(session::addSymbolBreakpoint);
        controller.setPauseOnEntry(debugForcePauseOnEntry
                || session.getBreakpoints().isEmpty() && session.getSymbolBreakpoints().isEmpty());
        debugForcePauseOnEntry = false;
        if (debugRunToLine > 0) {
            controller.setRunToOnStart(debugRunToPath, debugRunToLine);
            debugRunToLine = 0;
        }

        Path inputFile = debugInputFile;
        debugInputFile = null;
        var pane = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane(controller);
        // The Template… dialog suggests the names declared in the stylesheet and its modules.
        pane.getBreakpointsView().setStylesheetSupplier(() -> stylesheetTexts(xsltFile, resolvedModules));
        // Smart Step Into: the calls on the paused line, from the editor text of the paused module.
        pane.setStepTargetSource(snap -> debugStepTargets(xsltFile, resolvedModules, snap));
        // Watches belong to the main stylesheet and come back with its next session.
        String watchKey = xsltFile.getAbsolutePath();
        pane.getWatchView().setWatches(org.fxt.freexmltoolkit.debugger.WatchStore.load(watchKey));
        pane.getWatchView().setOnWatchesChanged(
                list -> org.fxt.freexmltoolkit.debugger.WatchStore.save(watchKey, list));
        XsltDebugSession debug = new XsltDebugSession(controller, pane, xsltFile, resolvedModules);
        debug.inputFile = inputFile;
        debug.inputDocument = inputFile != null ? documentAt(inputFile) : documentWithText(xml, xsltDoc);
        lastLaunch = new LastLaunch(xsltFile, inputFile, debug.inputDocument,
                parameters == null ? java.util.Map.of() : new java.util.LinkedHashMap<>(parameters), format, onFinished);
        activeDebug = debug;
        attachDebugModule(debug, xsltDoc, debug.mainPath);
        for (Tab tab : new java.util.ArrayList<>(tabPane.getTabs())) {
            if (tab instanceof EditorTab et && et.document.getPath() != null) {
                String path = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(et.document.getPath().toString());
                if (resolvedModules.contains(path) && !debug.modules.containsKey(path)) {
                    attachDebugModule(debug, et.document, path);
                }
            }
        }

        // 3. Wire the pane back to this host.
        pane.setOnPaused(this::onDebugPaused);
        pane.setOnShowInput(() -> showDebugInput(debug));
        pane.setOnOpenDocument(uri -> {
            try {
                Path file = Path.of(java.net.URI.create(uri));
                if (java.nio.file.Files.isRegularFile(file)) {
                    openFile(file, true);
                }
            } catch (RuntimeException ignored) {
                // not a local file
            }
        });
        pane.setOnShowInEditor(() -> {
            var snap = session.getPausedSnapshot();
            if (snap != null) {
                jumpToDebugLocation(snap.filePath(), snap.lineNumber());
            } else {
                selectDocument(xsltDoc);
            }
        });
        pane.setOnJumpToBreakpoint(bp -> jumpToDebugLocation(bp.filePath(), bp.lineNumber()));
        pane.setOnJumpToFrame(frame -> jumpToDebugLocation(frame.filePath(), frame.lineNumber()));
        pane.setOnJumpToMessage(msg -> {
            if (msg.getLineNumber() > 0) {
                jumpToDebugLocation(moduleForMessage(debug, msg.getLocation()), msg.getLineNumber());
            }
        });
        pane.setOnBreakpointsChanged(() -> {
            debug.modules.values().forEach(m -> refreshGutterFor(m.document()));
            persistDebugBreakpoints(debug);
        });
        pane.setOnResult(result -> {
            showDebugResult(debug, result);
            if (onFinished != null && result != null) {
                onFinished.accept(result);
            }
        });
        pane.setOnClose(this::endXsltDebug);

        bottomDock().register(DEBUG_DOCK_KEY, pane, "DEBUG");
        bottomDock().show(DEBUG_DOCK_KEY);
        selectDocument(xsltDoc);

        // 4. Start the run: the stylesheet is re-read from disk on every (re)start.
        // The debugger runs what the editor shows: the open stylesheet's (and open modules')
        // text, saved or not — read on the FX thread at every (re)start. Breakpoints follow the
        // editor's lines, so they match.
        controller.setModuleTexts(() -> onFxThread(this::openStylesheetTexts));
        controller.start(xml, inputFile == null ? null : inputFile.toUri().toString(),
                () -> onFxThread(() -> stylesheetText(xsltFile, controller)),
                xsltFile.toURI().toString(), parameters, format);
    }

    /**
     * The text the debugger runs for the main stylesheet: the editor's when it is open (a note
     * goes to Messages when it has unsaved changes), else the file.
     */
    private String stylesheetText(File xsltFile,
                                  org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController controller) {
        OpenDocument doc = documentAt(xsltFile.toPath());
        if (doc != null && controller.getLanguage()
                == org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController.Language.SCHEMATRON) {
            // SchXslt compiles the file (and its includes) from disk
            if (doc.isDirty()) {
                controller.note("Debugging the saved file of " + doc.getDisplayName()
                        + " - save it to include the unsaved changes");
            }
            doc = null;
        }
        if (doc != null) {
            var text = getDocumentText(doc);
            if (text.isPresent() && !text.get().isEmpty()) {
                if (doc.isDirty()) {
                    controller.note("Debugging the editor text of " + doc.getDisplayName()
                            + " (unsaved changes included)");
                }
                return text.get();
            }
        }
        try {
            return java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The editor text of every open, saved XSLT document, by normalised path (FX thread). */
    private java.util.Map<String, String> openStylesheetTexts() {
        java.util.Map<String, String> texts = new java.util.HashMap<>();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.getPath() != null
                    && et.document.getFileType() == EditorFileType.XSLT) {
                getDocumentText(et.document).filter(t -> !t.isEmpty()).ifPresent(t -> texts.put(
                        org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(et.document.getPath().toString()), t));
            }
        }
        return texts;
    }

    /** Runs {@code work} on the FX thread and waits for it (directly when already there). */
    private static <T> T onFxThread(java.util.concurrent.Callable<T> work) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(work);
        javafx.application.Platform.runLater(task);
        try {
            return task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("The editor did not answer in time", e);
        }
    }

    /** @return the open document of {@code file}, or {@code null}. */
    private OpenDocument documentAt(Path file) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.getPath() != null
                    && et.document.getPath().toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) {
                return et.document;
            }
        }
        return null;
    }

    /** @return the open document (other than {@code except}) whose current text is {@code text}. */
    private OpenDocument documentWithText(String text, OpenDocument except) {
        if (text == null) {
            return null;
        }
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document != except
                    && getDocumentText(et.document).map(text::equals).orElse(false)) {
                return et.document;
            }
        }
        return null;
    }

    /**
     * Marks the paused context node's line in its input document (blue tint) without switching
     * tabs, and enables the pane's Show input. In-memory input maps to the open document the
     * text came from; a file input — or a document loaded with {@code doc()} — is opened behind
     * the stylesheet when it is not open yet.
     */
    private void markDebugInput(XsltDebugSession debug, org.fxt.freexmltoolkit.debugger.PausedSnapshot snapshot) {
        if (debug.markedInput != null) {
            setDebugInputLineFor(debug.markedInput, -1);
            debug.markedInput = null;
            debug.markedInputLine = 0;
        }
        debug.pane.setInputLocationAvailable(false);
        if (snapshot == null) {
            return;
        }
        var location = org.fxt.freexmltoolkit.debugger.InputLocation.of(snapshot.contextXdm()).orElse(null);
        if (location == null) {
            return;
        }
        OpenDocument doc = inputDocumentFor(debug, location.systemId());
        if (doc == null) {
            return;
        }
        debug.markedInput = doc;
        debug.markedInputLine = location.line();
        whenDocumentLoaded(doc, () -> {
            if (debug.markedInput == doc) {
                setDebugInputLineFor(doc, location.line());
            }
        }, 15);
        debug.pane.setInputLocationAvailable(true);
    }

    /** @return the open document for an input node's system id, opening a file behind if needed. */
    private OpenDocument inputDocumentFor(XsltDebugSession debug, String systemId) {
        String path = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(systemId);
        if (path.isEmpty()) {
            return debug.inputDocument;
        }
        if (debug.modules.containsKey(path) || path.equals(debug.mainPath)) {
            return null; // a node of the stylesheet itself (document('')) — not an input
        }
        Path file = Path.of(path);
        OpenDocument doc = documentAt(file);
        if (doc == null && java.nio.file.Files.isRegularFile(file)) {
            openFile(file, false);
            doc = documentAt(file);
        }
        if (doc != null && debug.inputFile != null
                && file.toAbsolutePath().normalize().equals(debug.inputFile.toAbsolutePath().normalize())) {
            debug.inputDocument = doc;
        }
        return doc;
    }

    private void setDebugInputLineFor(OpenDocument document, int line) {
        EditorTab tab = editorTabOf(document);
        if (tab != null) {
            tab.view.setDebugInputLine(line);
        }
    }

    /** Runs {@code action} once {@code document}'s text is loaded (a just-opened file loads async). */
    private void whenDocumentLoaded(OpenDocument document, Runnable action, int attemptsLeft) {
        boolean loaded = getDocumentText(document).map(t -> !t.isEmpty()).orElse(false);
        if (loaded || attemptsLeft <= 0) {
            action.run();
            return;
        }
        var retry = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        retry.setOnFinished(e -> whenDocumentLoaded(document, action, attemptsLeft - 1));
        retry.play();
    }

    /** Shows the paused context node: selects its input document and moves the caret there. */
    private void showDebugInput(XsltDebugSession debug) {
        if (debug.markedInput == null) {
            return;
        }
        selectDocument(debug.markedInput);
        jumpWhenLoaded(debug.markedInput, debug.markedInputLine, 10);
        setDebugInputLineFor(debug.markedInput, debug.markedInputLine);
    }

    /** @return the 1-based line marked as the paused context node in {@code document}, or 0 (tests). */
    /** @return the inline values shown on a document's editor tab (tests). */
    java.util.Map<Integer, String> debugInlineValuesOf(OpenDocument document) {
        EditorTab tab = editorTabOf(document);
        return tab == null ? java.util.Map.of() : tab.view.getDebugInlineValues();
    }

    int debugInputLineOf(OpenDocument document) {
        XsltDebugSession debug = activeDebug;
        return debug != null && debug.markedInput == document ? debug.markedInputLine : 0;
    }

    /** Ends the active debug session: stops the run, detaches gutters/highlights, removes the pane. */
    public void endXsltDebug() {
        XsltDebugSession debug = activeDebug;
        if (debug == null) {
            return;
        }
        activeDebug = null;
        debug.hovers.values().forEach(org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugHover::uninstall);
        debug.hovers.clear();
        if (debug.markedInput != null) {
            setDebugInputLineFor(debug.markedInput, -1);
        }
        persistDebugBreakpoints(debug);
        debug.controller.stop();
        debug.controller.close();
        debug.pane.dispose();
        for (DebugModule module : debug.modules.values()) {
            setDebugLineFor(module.document(), -1);
            setDebugInlineValuesFor(module.document(), null);
            EditorTab tab = editorTabOf(module.document());
            if (tab != null) {
                // The session gutter goes; the file's own (persisted) gutter takes over again.
                setGutterContributor(tab, "xslt-debugger", null);
                installBreakpointGutter(tab);
                tab.view.refreshGutter();
            }
        }
        bottomDock().remove(DEBUG_DOCK_KEY);
    }

    /**
     * Gives a saved XSLT document the standalone breakpoint gutter, backed by the file's
     * persisted breakpoints ({@link org.fxt.freexmltoolkit.debugger.FileBreakpoints}), so
     * breakpoints can be set before the debugger starts. No-op for other file types, unsaved
     * documents, and modules a running debug session already owns; a module of the running
     * session that is opened now joins the session instead (its gutter talks to the run).
     */
    private void installBreakpointGutter(EditorTab et) {
        EditorFileType type = et.document.getFileType();
        if ((type != EditorFileType.XSLT && type != EditorFileType.XQUERY && type != EditorFileType.SCHEMATRON)
                || et.document.getPath() == null) {
            return;
        }
        followEditsWithBreakpoints(et); // whichever gutter the tab carries, its breakpoints follow edits
        String path = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(et.document.getPath().toString());
        if (activeDebug != null) {
            if (activeDebug.modules.containsKey(path)) {
                return;
            }
            if (activeDebug.resolvedModules.contains(path)) {
                attachDebugModule(activeDebug, et.document, path);
                return;
            }
        }
        var gutter = new org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory(
                org.fxt.freexmltoolkit.debugger.FileBreakpoints.persistent(et.document.getPath()),
                v -> et.view.refreshGutter());
        gutter.setFilePath(path);
        if (setGutterContributor(et, "xslt-debugger", gutter)) {
            et.view.refreshGutter();
        }
    }

    private static final String BREAKPOINT_SHIFT_KEY = "fxt.breakpointShift";

    /**
     * Lets the breakpoints of an XSLT tab follow its edits: inserted or deleted lines move the
     * breakpoints below them, a deleted breakpoint line removes its breakpoint — in the file's
     * own store or, during a session, in the session. Whole-text replacements (loading,
     * reloading, reformatting) are ignored. Installed once per tab.
     */
    private void followEditsWithBreakpoints(EditorTab et) {
        var area = et.view.getCodeArea();
        if (area == null || area.getProperties().containsKey(BREAKPOINT_SHIFT_KEY)) {
            return;
        }
        area.getProperties().put(BREAKPOINT_SHIFT_KEY, Boolean.TRUE);
        area.plainTextChanges().subscribe(change -> {
            var gutter = breakpointGutterOf(et);
            if (gutter == null || gutter.getTarget() == null) {
                return;
            }
            String removed = change.getRemoved();
            String inserted = change.getInserted();
            int lengthBefore = area.getLength() - inserted.length() + removed.length();
            if (removed.length() == lengthBefore) {
                return; // the whole text was replaced (load, reload, format): no edit to follow
            }
            var shift = new org.fxt.freexmltoolkit.debugger.LineShift(
                    lineOf(area.getText(), change.getPosition()), columnOf(area.getText(), change.getPosition()),
                    org.fxt.freexmltoolkit.debugger.LineShift.newlines(removed),
                    org.fxt.freexmltoolkit.debugger.LineShift.newlines(inserted));
            if (shift.isNeutral() || !gutter.getTarget().shiftLines(gutter.getFilePath(), shift)) {
                return;
            }
            et.view.refreshGutter();
            XsltDebugSession debug = activeDebug;
            if (debug != null && gutter.getTarget() == debug.controller.getSession()) {
                debug.pane.getBreakpointsView().refresh();
                persistDebugBreakpoints(debug);
            }
        });
    }

    /** 1-based line of {@code offset} in {@code text}. */
    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 0-based column of {@code offset} in {@code text}. */
    static int columnOf(String text, int offset) {
        int end = Math.min(offset, text.length());
        if (end <= 0) {
            return 0;
        }
        int start = text.lastIndexOf('\n', end - 1);
        return end - (start + 1);
    }

    /** @return whether F9 / Toggle Breakpoint applies: the active tab is a saved XSLT document. */
    public boolean canToggleBreakpoint() {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && breakpointGutterOf(et) != null;
    }

    /**
     * Toggles the breakpoint on the caret line of the active XSLT document (F9 / Tools → Toggle
     * Breakpoint) — the standalone or the session gutter, whichever is installed.
     *
     * @return {@code true} if a breakpoint was set or removed
     */
    public boolean toggleBreakpointAtCaret() {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return false;
        }
        var gutter = breakpointGutterOf(et);
        if (gutter == null) {
            return false;
        }
        gutter.toggle(et.view.getCodeArea().getCurrentParagraph() + 1);
        return true;
    }

    /**
     * Run to Cursor (Ctrl+F10 / Tools → Run to Cursor) in the active XSLT document: while a
     * debug session runs or is paused in one of its modules, runs to the caret line.
     *
     * @return {@code false} when there is no session to run (the caller may start one with
     *         {@link #setDebugRunToOnStart})
     */
    public boolean runToCursor() {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return false;
        }
        var gutter = breakpointGutterOf(et);
        if (gutter == null || activeDebug == null) {
            return false;
        }
        var session = activeDebug.controller.getSession();
        return session.runTo(gutter.getFilePath(), et.view.getCodeArea().getCurrentParagraph() + 1);
    }

    /** @return whether Run to Cursor applies: the active tab is a saved XSLT document. */
    public boolean canRunToCursor() {
        return canToggleBreakpoint();
    }

    /** @return the caret line (1-based) of the active XSLT document, or 0. */
    public int activeCaretLine() {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                ? et.view.getCodeArea().getCurrentParagraph() + 1 : 0;
    }

    /** How the last debug run was started — for Re-run Last Debug (Ctrl+Shift+F5). */
    private record LastLaunch(File xsltFile, Path inputFile, OpenDocument inputDocument,
                              java.util.Map<String, Object> parameters,
                              org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format,
                              java.util.function.Consumer<org.fxt.freexmltoolkit.service.XsltTransformationResult> onFinished) { }

    private LastLaunch lastLaunch;
    /** "Pause on entry" forced by a debug configuration for the next {@link #startXsltDebug}. */
    private boolean debugForcePauseOnEntry;

    /** @return whether a debug run was started in this app run (Re-run Last Debug applies). */
    public boolean canRerunLastDebug() {
        return lastLaunch != null;
    }

    /**
     * Re-runs the last debug run: same stylesheet, parameters and format, the input read again
     * (its file, or the open document it came from).
     *
     * @return an error message, or {@code null} when the debugger started
     */
    public String rerunLastDebug() {
        LastLaunch last = lastLaunch;
        if (last == null) {
            return "No debug run to repeat yet.";
        }
        String xml;
        if (last.inputFile() != null) {
            try {
                xml = java.nio.file.Files.readString(last.inputFile(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "Could not read the input " + last.inputFile() + ": " + e.getMessage();
            }
            setDebugInputFile(last.inputFile());
        } else if (last.inputDocument() != null && getOpenDocuments().contains(last.inputDocument())) {
            xml = getDocumentText(last.inputDocument()).orElse("");
        } else {
            return "The input document of the last debug run is no longer open.";
        }
        startXsltDebug(last.xsltFile(), xml, last.parameters(), last.format(), last.onFinished());
        return null;
    }

    /**
     * Starts the debugger for {@code xsltFile} with a debug configuration: its input file — or
     * the stylesheet's Run target —, parameters, output format (detected from {@code xsl:output}
     * when automatic) and pause on entry.
     *
     * @return an error message, or {@code null} when the debugger started
     */
    public String debugWithConfig(File xsltFile, org.fxt.freexmltoolkit.debugger.DebugLaunchConfig config) {
        String xslt;
        try {
            xslt = java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Could not read the stylesheet: " + e.getMessage();
        }
        String xml;
        Path input = null;
        if (!config.inputPath().isEmpty()) {
            input = Path.of(config.inputPath());
            try {
                xml = java.nio.file.Files.readString(input, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "Could not read the input " + input + ": " + e.getMessage();
            }
        } else {
            OpenDocument xsltDoc = documentAt(xsltFile.toPath());
            var target = xsltDoc == null ? Optional.<ResolvedQueryTarget>empty() : resolveQueryTarget(xsltDoc);
            if (target.isEmpty()) {
                return "Open an XML document first, or set an input file in the debug configuration.";
            }
            try {
                xml = target.get().loadXml();
            } catch (IOException e) {
                return "Could not read the input: " + e.getMessage();
            }
            if (target.get().file() != null) {
                input = target.get().file().toPath();
            }
        }
        var format = config.outputFormat().isEmpty()
                ? TransformRunner.detectXsltOutputFormat(xslt)
                : org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.valueOf(config.outputFormat());
        if (input != null) {
            setDebugInputFile(input);
        }
        debugForcePauseOnEntry = config.pauseOnEntry();
        startXsltDebug(xsltFile, xml, new java.util.LinkedHashMap<>(config.parameters()), format);
        return null;
    }

    /** The input file of the next {@link #startXsltDebug} (set by callers whose input is a file). */
    private Path debugInputFile;

    /**
     * Tells the next {@link #startXsltDebug} that its XML text was read from {@code file}, so the
     * paused context node can be shown in that file. Without it the input is matched to the open
     * document with the same text.
     */
    public void setDebugInputFile(Path file) {
        this.debugInputFile = file;
    }

    /** Pending "debug to cursor" target for the next {@link #startXsltDebug} (path, line). */
    private String debugRunToPath;
    private int debugRunToLine;

    /**
     * Arms the next {@link #startXsltDebug} to run to the caret line of the active XSLT
     * document ("Debug to Cursor"). Cleared with {@code setDebugRunToOnStart(null, 0)}.
     *
     * @return {@code false} when the active tab is no saved XSLT document
     */
    public boolean prepareDebugToCursor() {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return false;
        }
        var gutter = breakpointGutterOf(et);
        if (gutter == null) {
            return false;
        }
        setDebugRunToOnStart(gutter.getFilePath(), et.view.getCodeArea().getCurrentParagraph() + 1);
        return true;
    }

    /** Makes the next debug start run to {@code path:line} instead of pausing on entry. */
    public void setDebugRunToOnStart(String path, int line) {
        this.debugRunToPath = path;
        this.debugRunToLine = line;
    }

    private static org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory breakpointGutterOf(EditorTab et) {
        return et.gutter.get("xslt-debugger") instanceof org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory g
                ? g : null;
    }

    /** @return the editor tab showing {@code document}, or {@code null}. */
    private EditorTab editorTabOf(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                return et;
            }
        }
        return null;
    }

    /**
     * Adds the breakpoints stored for {@code path} (set in the editor before the session, or in
     * an earlier session) to {@code session}; a line the session already has is left alone.
     */
    private static void mergeStoredBreakpoints(org.fxt.freexmltoolkit.debugger.DebugSession session, String path) {
        java.util.Set<org.fxt.freexmltoolkit.debugger.Breakpoint> present = session.getBreakpoints();
        for (org.fxt.freexmltoolkit.debugger.Breakpoint bp
                : org.fxt.freexmltoolkit.debugger.BreakpointStore.load(path)) {
            if (!bp.filePath().equals(path)) {
                continue; // a legacy aggregate from when this module was debugged as main
            }
            boolean known = present.stream().anyMatch(
                    b -> b.filePath().equals(path) && b.lineNumber() == bp.lineNumber());
            if (!known) {
                session.addBreakpoint(bp);
            }
        }
    }

    /** Attaches a breakpoint gutter for {@code path} to {@code document} and records the module. */
    private void attachDebugModule(XsltDebugSession debug, OpenDocument document, String path) {
        if (!path.equals(debug.mainPath)) {
            // An include opened during the session: pick up the breakpoints set on it in the editor.
            mergeStoredBreakpoints(debug.controller.getSession(), path);
        }
        var gutter = new org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory(
                debug.controller.getSession(), v -> {
                    refreshGutterFor(document);
                    debug.pane.getBreakpointsView().refresh();
                    persistDebugBreakpoints(debug);
                });
        gutter.setFilePath(path);
        setGutterFactoryFor(document, gutter);
        debug.modules.put(path, new DebugModule(document, gutter));
        EditorTab tab = editorTabOf(document);
        if (tab != null && !debug.hovers.containsKey(document)) {
            debug.hovers.put(document, new org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugHover(
                    tab.view.getCodeArea(), debug.pane::currentScope, debug.controller::getWatchEvaluator,
                    debug.pane::openViewer).install());
        }
    }

    /**
     * Adds "Break When Processed" to an XML-family editor's context menu: shown while an XSLT
     * debug session runs (not on the session's stylesheets), it toggles an input-node
     * breakpoint on the element at the caret.
     */
    private void installInputNodeBreakpointMenu(EditorTab tab) {
        javafx.scene.control.ContextMenu menu = tab.view.getEditorContextMenu();
        if (menu == null) {
            return;
        }
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem("Break When Processed");
        item.setId("editor-ctx-break-when-processed");
        item.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-record-circle"),
                org.fxt.freexmltoolkit.controls.theme.ActionColor.DELETE));
        item.setOnAction(e -> toggleInputNodeBreakpoint(tab.document,
                tab.view.getCodeArea().getText(), tab.view.getCodeArea().getCaretPosition()));
        menu.getItems().add(0, item);
        javafx.scene.control.SeparatorMenuItem separator = new javafx.scene.control.SeparatorMenuItem();
        menu.getItems().add(1, separator);
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, e -> {
            XsltDebugSession debug = activeDebug;
            boolean show = debug != null && !isDebugModule(debug, tab.document);
            item.setVisible(show);
            separator.setVisible(show);
            if (show) {
                item.setText(inputNodeBreakpointAt(debug, tab.document, tab.view.getCodeArea().getText(),
                        tab.view.getCodeArea().getCaretPosition()) != null
                        ? "Remove Break When Processed" : "Break When Processed");
            }
        });
        item.setVisible(false);
        separator.setVisible(false);
    }

    private static boolean isDebugModule(XsltDebugSession debug, OpenDocument document) {
        return debug.modules.values().stream().anyMatch(m -> m.document() == document);
    }

    /** The input-node breakpoint for the element at {@code caret}, as it is or would be stored. */
    private static org.fxt.freexmltoolkit.debugger.SymbolBreakpoint inputNodeAt(OpenDocument document,
            String text, int caret) {
        var element = org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret.at(text, caret).orElse(null);
        if (element == null || element.startTagEnd() < 0) {
            return null;
        }
        int gt = element.startTagEnd();
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < gt; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int column = gt - lineStart + 2; // Saxon: the 1-based column just after the '>'
        String path = document.getPath() == null ? ""
                : org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(document.getPath().toString());
        return org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.inputNode(path, line, column, element.xpath());
    }

    private static org.fxt.freexmltoolkit.debugger.SymbolBreakpoint inputNodeBreakpointAt(XsltDebugSession debug,
            OpenDocument document, String text, int caret) {
        var wanted = inputNodeAt(document, text, caret);
        if (wanted == null) {
            return null;
        }
        return debug.controller.getSession().getSymbolBreakpoints().stream()
                .filter(s -> s.kind() == org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.Kind.INPUT_NODE
                        && s.inputLine() == wanted.inputLine() && s.inputColumn() == wanted.inputColumn()
                        && s.inputPath().equals(wanted.inputPath()))
                .findFirst().orElse(null);
    }

    /**
     * Toggles the input-node breakpoint on the element at {@code caret} of {@code document} in
     * the running debug session.
     *
     * @return {@code true} when one was added, {@code false} when removed or no session / element
     */
    boolean toggleInputNodeBreakpoint(OpenDocument document, String text, int caret) {
        XsltDebugSession debug = activeDebug;
        if (debug == null || document == null || text == null) {
            return false;
        }
        var session = debug.controller.getSession();
        var existing = inputNodeBreakpointAt(debug, document, text, caret);
        boolean added = false;
        if (existing != null) {
            session.removeSymbolBreakpoint(existing);
        } else {
            var created = inputNodeAt(document, text, caret);
            if (created == null) {
                return false;
            }
            session.addSymbolBreakpoint(created);
            added = true;
        }
        debug.pane.getBreakpointsView().refresh();
        persistDebugBreakpoints(debug);
        return added;
    }

    /** Writes the session's breakpoints back, each file under its own key (modules without any are cleared). */
    private void persistDebugBreakpoints(XsltDebugSession debug) {
        java.util.Set<String> files = new java.util.LinkedHashSet<>();
        files.add(debug.mainPath);
        files.addAll(debug.resolvedModules);
        files.addAll(debug.modules.keySet());
        org.fxt.freexmltoolkit.debugger.BreakpointStore.saveByFile(
                debug.controller.getSession().getBreakpoints(), files);
        org.fxt.freexmltoolkit.debugger.WatchStore.saveSymbols(debug.mainPath,
                debug.controller.getSession().getSymbolBreakpoints());
    }

    /** The text of the main stylesheet and its included / imported modules (unreadable ones skipped). */
    /** The Smart Step Into targets of the paused line (FX thread: reads the editor text). */
    private java.util.List<org.fxt.freexmltoolkit.debugger.StepTarget> debugStepTargets(File main,
            java.util.Set<String> modules, org.fxt.freexmltoolkit.debugger.PausedSnapshot snap) {
        String path = snap.filePath() == null || snap.filePath().isEmpty()
                ? main.getAbsolutePath() : snap.filePath();
        String text = null;
        OpenDocument doc = documentAt(Path.of(path));
        if (doc != null) {
            text = getDocumentText(doc).orElse(null);
        }
        if (text == null) {
            try {
                text = java.nio.file.Files.readString(Path.of(path), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return java.util.List.of();
            }
        }
        java.util.List<String> functions = org.fxt.freexmltoolkit.debugger.StylesheetSymbols
                .scan(stylesheetTexts(main, modules) + "\n" + text)
                .getOrDefault(org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.Kind.FUNCTION, java.util.List.of());
        return org.fxt.freexmltoolkit.debugger.StepTargets.find(text, snap.lineNumber(), functions);
    }

    private static String stylesheetTexts(File main, java.util.Set<String> modules) {
        StringBuilder sb = new StringBuilder();
        java.util.List<Path> files = new java.util.ArrayList<>();
        files.add(main.toPath());
        modules.forEach(m -> files.add(Path.of(m)));
        for (Path file : files) {
            try {
                sb.append(java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8)).append('\n');
            } catch (IOException | RuntimeException ignored) {
                // a module that cannot be read contributes no suggestions
            }
        }
        return sb.toString();
    }

    /**
     * Marks the paused location in the editor: the module's tab is selected (opened first
     * when Saxon paused inside an {@code xsl:include}/{@code xsl:import}), the line gets the
     * gutter arrow and the execution-line tint. {@code null} clears the markers (run moved on).
     */
    private void onDebugPaused(org.fxt.freexmltoolkit.debugger.PausedSnapshot snapshot) {
        XsltDebugSession debug = activeDebug;
        if (debug == null) {
            return;
        }
        String path = snapshot == null ? null : debugModulePath(debug, snapshot.filePath());
        for (java.util.Map.Entry<String, DebugModule> entry : debug.modules.entrySet()) {
            boolean here = snapshot != null && entry.getKey().equals(path);
            entry.getValue().gutter().setCurrentExecutionLine(here ? snapshot.lineNumber() : -1);
            setDebugLineFor(entry.getValue().document(), here ? snapshot.lineNumber() : -1);
            setDebugInlineValuesFor(entry.getValue().document(), here ? snapshot : null);
            refreshGutterFor(entry.getValue().document());
        }
        if (snapshot != null) {
            jumpToDebugLocation(path, snapshot.lineNumber());
        }
        markDebugInput(debug, snapshot);
    }

    /** @return the module path for a pause/jump location — the main stylesheet when it is unknown. */
    private static String debugModulePath(XsltDebugSession debug, String filePath) {
        return filePath == null || filePath.isEmpty() ? debug.mainPath : filePath;
    }

    /** @return the module whose file name appears in a message's "file:line" location, else the main one. */
    private static String moduleForMessage(XsltDebugSession debug, String location) {
        if (location != null) {
            // open modules first, then included ones not open yet (e.g. a finding in an sch:include)
            java.util.Set<String> candidates = new java.util.LinkedHashSet<>(debug.modules.keySet());
            candidates.addAll(debug.resolvedModules);
            for (String path : candidates) {
                String name = org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(path);
                if (!name.isEmpty() && location.startsWith(name)) {
                    return path;
                }
            }
        }
        return debug.mainPath;
    }

    /**
     * Selects (opening it if needed) the module at {@code filePath} and moves the caret to
     * {@code line}. A module opened for the first time loads asynchronously, so the jump is
     * retried briefly until its text is in.
     */
    private void jumpToDebugLocation(String filePath, int line) {
        XsltDebugSession debug = activeDebug;
        if (debug == null) {
            return;
        }
        String path = debugModulePath(debug, filePath);
        DebugModule module = debug.modules.get(path);
        if (module == null) {
            File file = new File(path);
            if (!file.isFile()) {
                return;
            }
            openFile(file);
            OpenDocument doc = getActiveDocument().orElse(null);
            if (doc == null) {
                return;
            }
            attachDebugModule(debug, doc, path);
            module = debug.modules.get(path);
            // A freshly opened include carries the current-line marker as well.
            var snap = debug.controller.getSession().getPausedSnapshot();
            if (snap != null && path.equals(debugModulePath(debug, snap.filePath()))) {
                module.gutter().setCurrentExecutionLine(snap.lineNumber());
                setDebugLineFor(doc, snap.lineNumber());
                setDebugInlineValuesFor(doc, snap);
            }
        }
        selectDocument(module.document());
        if (line > 0) {
            jumpWhenLoaded(module.document(), line, 10);
        }
    }

    private void jumpWhenLoaded(OpenDocument document, int line, int attemptsLeft) {
        boolean loaded = getActiveText().map(t -> !t.isEmpty()).orElse(false);
        if (loaded || attemptsLeft <= 0) {
            if (getActiveDocument().orElse(null) == document) {
                goToLine(line);
                refreshGutterFor(document);
            }
            return;
        }
        var retry = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        retry.setOnFinished(e -> jumpWhenLoaded(document, line, attemptsLeft - 1));
        retry.play();
    }

    /** Shows a debug run's result in OUTPUT without taking the DEBUG pane out of the front. */
    private void showDebugResult(XsltDebugSession debug, org.fxt.freexmltoolkit.service.XsltTransformationResult result) {
        if (activeDebug != debug || result == null) {
            return;
        }
        boolean stopped = !result.isSuccess() && result.getErrorMessage() != null
                && result.getErrorMessage().contains("stopped");
        if (stopped) {
            return;
        }
        TransformOutputPanel out = transformOutputPanel();
        if (result.isSuccess()) {
            out.showTransformResult(result.getOutputContent(), result.getOutputFormat(), result.getExecutionTime());
        } else {
            out.showError("ERROR: " + result.getErrorMessage());
        }
        bottomDock().select(DEBUG_DOCK_KEY);
        bottomDock().markUpdated(TransformOutputPanel.DOCK_KEY);
    }

    // ----- internals -------------------------------------------------------

    private void withActive(java.util.function.Consumer<EditorTab> action) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            action.accept(et);
        }
    }

    private void addTab(EditorTab tab) {
        addTab(tab, true);
    }

    /** Adds a tab; {@code select = false} opens it behind the active one (e.g. a schema edited from XML). */
    private void addTab(EditorTab tab, boolean select) {
        tab.remoteImportsCallback = this::onRemoteImportsAvailable;
        tab.modelInstalledCallback = () -> {
            if (tab.isSelected()) {
                activeModelRevision.set(activeModelRevision.get() + 1);
            }
        };
        tab.viewModeAppliedCallback = () -> {
            if (tab.isSelected()) {
                activeViewMode.set(tab.viewMode);
                refreshSelectedNode();
            }
        };
        tabPane.getTabs().add(tab);
        installBreakpointGutter(tab);
        if (select) {
            tabPane.getSelectionModel().select(tab);
        }
        // No-op for non-query views; the lazy indirection tolerates the handler
        // being registered after the first tabs open.
        tab.view.configureQuerySupport(
                () -> {
                    if (queryRunHandler != null) {
                        queryRunHandler.run();
                    }
                },
                // IntelliSense context: honor an explicit open-document target; a file-system
                // target falls back to the Automatic text (no disk IO on the FX thread).
                () -> resolveQueryTarget(tab.document)
                        .map(ResolvedQueryTarget::xmlText)
                        .filter(java.util.Objects::nonNull)
                        .orElseGet(() -> getLastXmlFamilyDocument()
                                .flatMap(this::getDocumentText).orElse("")));
        // Ctrl+Enter runs XSLT and XProc documents too (XmlEditorView is shared by
        // XML/XSD/Schematron, so the file type is checked at press time — Save As can change it).
        tab.view.getCodeArea().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            EditorFileType pressType = tab.document.getFileType();
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && e.isShortcutDown()
                    && (pressType == EditorFileType.XSLT || pressType == EditorFileType.XPROC)
                    && queryRunHandler != null) {
                queryRunHandler.run();
                e.consume();
            }
        });
        openDocuments.add(tab.document);
        tab.setOnClosed(e -> disposeTab(tab));
        tab.setOnCloseRequest(e -> confirmCloseIfDirty(tab, e));
        tab.view.getCodeArea().caretPositionProperty().addListener((obs, oldV, newV) -> {
            if (tab.isSelected()) {
                activeCaret.set(newV.intValue());
                // In the Text view, map the caret line to a model node so the inspector can show +
                // edit that node's properties (debounced). XSD resolves an XsdNode, other XML-family
                // files an XmlNode; JSON has no model-node caret mapping.
                if (tab.viewMode == ViewMode.TEXT && tab.document.getFileType() != EditorFileType.JSON) {
                    tab.xmlCaretDebounce.playFromStart();
                }
            }
        });
    }

    /** Drops the host's bookkeeping for a tab that was closed (by the user or programmatically). */
    private void disposeTab(EditorTab tab) {
        openDocuments.remove(tab.document);
        queryTargets.remove(tab.document);
        queryTargets.values().removeIf(t ->
                t instanceof QueryTarget.OpenDoc od && od.document() == tab.document);
    }

    /**
     * Closes a tab without the unsaved-changes prompt. Removing a tab from the pane does not
     * fire its {@code onClosed} handler, so the bookkeeping is dropped here.
     */
    private void closeTabNow(EditorTab tab) {
        tabPane.getTabs().remove(tab);
        disposeTab(tab);
    }

    /**
     * Closes the active tab the way its close button does (File &gt; Close Tab, Ctrl/Cmd+W): the
     * tab's close-request handler runs first, so a dirty document asks to save and can veto.
     *
     * @return {@code true} if a tab was closed; {@code false} without a closable active tab or
     *         when the close was cancelled
     */
    /**
     * Selects the tab {@code delta} positions after the active one (negative = before), wrapping
     * around at both ends — Ctrl+Tab / Ctrl+Shift+Tab. No-op with fewer than two tabs.
     */
    public void selectAdjacentTab(int delta) {
        var tabs = tabPane.getTabs();
        int count = tabs.size();
        if (count < 2) {
            return;
        }
        int current = tabPane.getSelectionModel().getSelectedIndex();
        int next = Math.floorMod((current < 0 ? 0 : current) + delta, count);
        tabPane.getSelectionModel().select(tabs.get(next));
    }

    public boolean closeActiveTab() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null || !tab.isClosable()) {
            return false;
        }
        // Source and target are set up front: fireEvent would otherwise dispatch a copy and
        // the veto (consume) of the close-request handler would be lost.
        javafx.event.Event request = new javafx.event.Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT);
        javafx.event.Event.fireEvent(tab, request);
        if (request.isConsumed()) {
            return false;
        }
        tabPane.getTabs().remove(tab);
        javafx.event.Event.fireEvent(tab, new javafx.event.Event(tab, tab, Tab.CLOSED_EVENT));
        return true;
    }

    /**
     * Records a tab's schema-binding status and mirrors it into {@link #activeSchemaStatus}
     * when the tab is front-most. Must be called on the FX thread (same publish pattern as
     * {@link #activeSchema}).
     */
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
    private static boolean isAbandoned(EditorTab tab) {
        return tab.closed.get() || org.fxt.freexmltoolkit.FxtGui.executorService.isShutdown();
    }

    /**
     * Counts schema bindings actually applied to a tab. Observable so tests can assert that a
     * detection abandoned mid-flight applies nothing; nothing in the UI reads it.
     */
    private final java.util.concurrent.atomic.AtomicInteger schemaBindingsApplied =
            new java.util.concurrent.atomic.AtomicInteger();

    /** @return how many schema bindings have been applied since this host was created */
    int schemaBindingsApplied() {
        return schemaBindingsApplied.get();
    }

    private void publishSchemaStatus(EditorTab tab, SchemaStatus status) {
        tab.schemaStatus = status;
        if (tab.isSelected()) {
            activeSchemaStatus.set(status);
        }
    }

    /**
     * Records how a tab's schema binding was established and mirrors it into
     * {@link #activeSchemaSource}/{@link #activeSchemaSourceDetail} when the tab is
     * front-most. Must be called on the FX thread (same publish pattern as {@link #activeSchema}).
     */
    private void publishSchemaSource(EditorTab tab, SchemaSource source, String detail) {
        tab.schemaSource = source;
        tab.schemaSourceDetail = detail;
        if (tab.isSelected()) {
            activeSchemaSource.set(source);
            activeSchemaSourceDetail.set(detail);
        }
    }

    private void loadAsync(EditorTab tab, Path path) {
        long openStart = System.nanoTime();
        tab.loading = true;
        tab.beginLoading();
        tab.schemaBindingGen.incrementAndGet(); // supersede queued schema reconciles
        if (tab.view.supportsSchema()) {
            publishSchemaStatus(tab, SchemaStatus.LOADING);
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long size = sizeOrUnknown(path); // a stat can stall: here, not on the FX thread
            long maxHeap = Runtime.getRuntime().maxMemory();
            switch (LargeFileGuard.check(size, maxHeap)) {
                case OK -> readAndShow(tab, path, openStart);
                case WARN -> Platform.runLater(() -> {
                    if (isAbandoned(tab)) {
                        return;
                    }
                    if (confirmLargeFile(path, size)) {
                        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> readAndShow(tab, path, openStart));
                    } else {
                        UsageEvents.fileOpenDeclined(tab.document.getFileType().docKind(), size, false);
                        closeTabNow(tab);
                    }
                });
                case REFUSE -> Platform.runLater(() -> {
                    UsageEvents.fileOpenDeclined(tab.document.getFileType().docKind(), size, true);
                    closeTabNow(tab);
                    org.fxt.freexmltoolkit.util.DialogHelper.showWarning("Open File", "File too large",
                            path.getFileName() + " is " + LargeFileGuard.formatSize(size)
                                    + ". With the current memory limit of " + LargeFileGuard.formatSize(maxHeap)
                                    + ", files up to about " + LargeFileGuard.formatSize(LargeFileGuard.largestOpenable(maxHeap))
                                    + " can be opened.\n\nRaise the limit under Settings > Memory and restart the application.");
                });
            }
        });
    }

    /** Asks before a large file is opened in the editor. Runs on the FX thread. */
    private boolean confirmLargeFile(Path path, long size) {
        return org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Open Large File",
                path.getFileName() + " is " + LargeFileGuard.formatSize(size),
                "Large files open slowly and make editing sluggish. Syntax highlighting, validation "
                        + "and the structured views may take a long time.\n\nOpen it anyway?",
                "Open Anyway", "Cancel");
    }

    /** Reads {@code path} on the calling worker thread and shows it in {@code tab}. */
    private void readAndShow(EditorTab tab, Path path, long openStart) {
        // Stamp taken BEFORE the read: a change landing mid-read is then caught by the
        // next external-change check instead of being silently missed.
        DiskStamp stamp = DiskStamp.of(path);
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Platform.runLater(() -> {
                tab.view.setText("Could not read " + path + ": " + e.getMessage());
                tab.endLoading();
                tab.document.setDiskStamp(stamp);
                tab.loading = false;
                publishSchemaStatus(tab, SchemaStatus.NONE);
            });
            return;
        }

        long fileSize = sizeOrUnknown(path); // here, not in the FX pulse below: a stat can stall
        // Also here: a linear scan of the text (XML family only, -1 otherwise)
        int elementCount = UsageEvents.elementCount(tab.document.getFileType().docKind(), content);
        // And the editor document of a large file (paragraph split + tree), seconds on the FX thread
        var prepared = tab.view.prepareText(content);
        // Show the document as soon as it has been read. Schema detection below can take
        // seconds - a remote xsi:schemaLocation (or a namespace the Schema Library resolves
        // remotely) is downloaded - and the editor must not sit empty for that long. The
        // schema has its own status, already published as LOADING above, so the pending
        // binding stays visible to the user.
        Platform.runLater(() -> {
            if (isAbandoned(tab)) {
                return;
            }
            if (prepared != null) {
                tab.view.setText(prepared);
            } else {
                tab.view.setText(content);
            }
            tab.refreshPreviewIfActive();
            tab.endLoading();
            tab.document.setDirty(false);
            tab.document.setDiskStamp(stamp);
            tab.loading = false;
            tab.attachDirtyTracking();
            UsageEvents.fileOpened(tab.document.getFileType().docKind(), fileSize,
                    UsageEvents.SOURCE_FILE, openStart, elementCount);
            documentLoadedHandler.accept(tab.document, content);
            Runnable after = tab.afterLoad;
            tab.afterLoad = null;
            if (after != null) {
                after.run();
            }
        });

        if (isAbandoned(tab)) {
            return; // closed (or shutting down) while the file was being read
        }

        SchemaDetection detected = null;
        // Same per-tab guard as redetectSchemaForActiveDocument: never run two
        // schema detections for one tab concurrently (Xerces DOM is not thread-safe).
        if (tab.schemaDetecting.compareAndSet(false, true)) {
            try {
                detected = detectSchemaFor(tab, content, path);
            } finally {
                tab.schemaDetecting.set(false);
            }
        }
        if (isAbandoned(tab)) {
            return; // closed while detecting - binding it would parse a schema for nothing
        }
        SchemaDetection detection = detected;
        Platform.runLater(() -> {
            if (isAbandoned(tab)) {
                return; // closed between the check above and this pulse
            }
            File autoXsd = detection != null ? detection.schema() : null;
            if (autoXsd != null) {
                tab.schemaFile = autoXsd;
                tab.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.AUTO;
                tab.view.invalidateIntelliSenseCache();
                if (tab.isSelected()) {
                    activeSchema.set(autoXsd);
                }
                publishSchemaSource(tab, detection.source(), detection.sourceDetail());
                loadXmlSchemaProviderAsync(tab, autoXsd);
                schemaBindingsApplied.incrementAndGet();
            }
            // Always publish a terminal status so LOADING can never stick. When the
            // CAS was lost (a concurrent detection ran), fall back to the tab's
            // current binding.
            publishSchemaStatus(tab, detection != null
                    ? detection.status()
                    : (tab.schemaFile != null ? SchemaStatus.READY : SchemaStatus.NONE));
            if (detection != null && tab.view.supportsSchema()) {
                UsageEvents.schemaBound(tab.document.getFileType().docKind(),
                        detection.source().name().toLowerCase(java.util.Locale.ROOT),
                        detection.status() != SchemaStatus.ERROR, detection.failure(), detection.refKind(),
                        openStart);
            }
        });
    }

    /**
     * Re-runs the schema auto-detection ({@code xsi:schemaLocation} /
     * {@code xsi:noNamespaceSchemaLocation}, local and remote) for the active document
     * when it has no schema bound yet — e.g. when the Validation panel opens and the
     * reference was added after the file was opened, or an earlier remote download
     * failed. Detection reads the live editor buffer, so unsaved (even untitled)
     * documents work. No-op when a schema is already bound, so an explicit user choice
     * is never overridden; the changed/removed-reference cases are handled by the
     * validation-time reconcile in {@link #schemaForValidation(String)}.
     */
    public void redetectSchemaForActiveDocument() {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab tab)) {
            return;
        }
        if (tab.schemaFile != null || !tab.view.supportsSchema()) {
            return;
        }
        String content = tab.view.getText();
        Path path = tab.document.getPath();
        long generation = tab.schemaBindingGen.incrementAndGet(); // supersede queued reconciles
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            if (isAbandoned(tab)) {
                return;
            }
            // One detection at a time per tab: the open-time detection (loadAsync) or a
            // second redetect may still be running, and the schema pipeline's Xerces
            // deferred DOM is not thread-safe (concurrent runs corrupted it).
            // Do NOT give up on a lost race - the holder may itself be superseded and bind
            // nothing, and no later task would retry; wait for it, then re-check with fresh
            // state (same rule as reconcileSchemaBinding). The wait is bounded so a stuck
            // holder can never pin a thread of the fixed-size pool for good: giving up then is
            // no worse than the unconditional skip this replaces. The bound has to clear the
            // slowest realistic holder - an open-time detection whose declared location points
            // at an unroutable host waits out the connect timeout, measured at ~35 s here.
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
            while (!tab.schemaDetecting.compareAndSet(false, true)) {
                if (tab.schemaBindingGen.get() != generation || System.nanoTime() > deadline
                        || isAbandoned(tab)) {
                    return; // superseded, abandoned, or the holder never released
                }
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (tab.schemaBindingGen.get() != generation) {
                // superseded: a fresher snapshot owns the binding now
                tab.schemaDetecting.set(false);
                return;
            }
            // LOADING only after the CAS succeeded — a lost CAS returns without any
            // completion path, and a stray LOADING would stick in the status bar.
            Platform.runLater(() -> publishSchemaStatus(tab, SchemaStatus.LOADING));
            SchemaDetection detection;
            try {
                if (tab.schemaFile != null) {
                    // the other detection won while we waited
                    detection = new SchemaDetection(null, SchemaStatus.READY, SchemaSource.NONE, null);
                } else {
                    detection = detectSchemaFor(tab, content, path);
                }
            } finally {
                tab.schemaDetecting.set(false);
            }
            if (isAbandoned(tab)) {
                return; // closed while detecting - binding it would parse a schema for nothing
            }
            SchemaDetection result = detection;
            Platform.runLater(() -> {
                if (isAbandoned(tab)) {
                    return; // closed between the check above and this pulse
                }
                File autoXsd = result.schema();
                if (autoXsd != null) {
                    tab.schemaFile = autoXsd;
                    tab.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.AUTO;
                    tab.view.invalidateIntelliSenseCache();
                    if (tab.isSelected()) {
                        activeSchema.set(autoXsd);
                    }
                    publishSchemaSource(tab, result.source(), result.sourceDetail());
                    loadXmlSchemaProviderAsync(tab, autoXsd);
                    schemaBindingsApplied.incrementAndGet();
                }
                // Terminal status in every branch, so LOADING never sticks.
                publishSchemaStatus(tab, result.status());
            });
        });
    }

    /**
     * Reconciles the active document's XSD binding with the schema location declared in
     * {@code content} (the live editor buffer) and returns a supplier the validation
     * worker calls to obtain the XSD to validate against. Call this on the FX thread —
     * it snapshots the active tab; resolve the supplier on a worker thread only, as a
     * changed remote reference may be downloaded there. An added, changed, or removed
     * {@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} therefore takes
     * effect on the very validation run that observes it. Policy (see
     * {@link SchemaRebindPolicy}): manual bindings are never overridden, and an
     * unchanged declaration costs no I/O.
     */
    public java.util.function.Supplier<File> schemaForValidation(String content) {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab tab)
                || !tab.view.supportsSchema()) {
            return () -> null;
        }
        Path path = tab.document.getPath();
        long generation = tab.schemaBindingGen.incrementAndGet();
        return () -> reconcileSchemaBinding(tab, content, path, generation);
    }

    /** Worker-thread body of {@link #schemaForValidation(String)}. @return the XSD to validate with */
    private File reconcileSchemaBinding(EditorTab tab, String content, Path path, long generation) {
        File baseDir = path != null && path.getParent() != null ? path.getParent().toFile() : null;
        String declared = declaredSchemaLocation(tab, content, baseDir);
        if (SchemaRebindPolicy.decideRebind(tab.schemaOrigin, declared, tab.lastDetectedSchemaLocation)
                == SchemaRebindPolicy.RebindAction.KEEP) {
            return tab.schemaFile; // fast path: unchanged declaration, no lock, no I/O
        }
        // Rebinding needed — take the per-tab detection CAS. Do NOT give up on a lost
        // race: the holder (e.g. a redetect) may itself be superseded and bind nothing,
        // and no later task would retry — wait for it, then re-decide with fresh state.
        while (!tab.schemaDetecting.compareAndSet(false, true)) {
            if (tab.schemaBindingGen.get() != generation) {
                return tab.schemaFile; // superseded while waiting: a fresher task owns this
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return tab.schemaFile;
            }
        }
        try {
            if (tab.schemaBindingGen.get() != generation) {
                return tab.schemaFile; // superseded: a fresher snapshot owns the binding now
            }
            // Re-decide under the lock: the previous holder may have already bound
            // (or cleared) exactly what this snapshot declares.
            switch (SchemaRebindPolicy.decideRebind(tab.schemaOrigin, declared, tab.lastDetectedSchemaLocation)) {
                case KEEP -> {
                    return tab.schemaFile;
                }
                case CLEAR -> {
                    tab.lastDetectedSchemaLocation = null;
                    tab.schemaFile = null;
                    tab.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.NONE;
                    Platform.runLater(() -> {
                        clearSchemaBindingUi(tab);
                        publishSchemaSource(tab, SchemaSource.NONE, null);
                        publishSchemaStatus(tab, SchemaStatus.NONE);
                    });
                    return null;
                }
                case DETECT -> {
                    // LOADING only after the CAS succeeded — a lost CAS has no
                    // completion path, and a stray LOADING would stick in the status bar.
                    Platform.runLater(() -> publishSchemaStatus(tab, SchemaStatus.LOADING));
                    SchemaDetection detection = detectSchemaFor(tab, content, path);
                    File xsd = detection.schema();
                    tab.schemaFile = xsd;
                    tab.schemaOrigin = xsd != null
                            ? SchemaRebindPolicy.SchemaBindingOrigin.AUTO
                            : SchemaRebindPolicy.SchemaBindingOrigin.NONE;
                    Platform.runLater(() -> {
                        if (xsd != null) {
                            tab.view.invalidateIntelliSenseCache();
                            if (tab.isSelected()) {
                                activeSchema.set(xsd);
                            }
                            publishSchemaSource(tab, detection.source(), detection.sourceDetail());
                            loadXmlSchemaProviderAsync(tab, xsd);
                        } else {
                            // Declared but unresolvable: drop the stale binding too —
                            // keeping it would silently validate against the wrong schema.
                            clearSchemaBindingUi(tab);
                            publishSchemaSource(tab, SchemaSource.NONE, null);
                        }
                        // Terminal status in every branch, so LOADING never sticks.
                        publishSchemaStatus(tab, detection.status());
                    });
                    return xsd;
                }
            }
            return tab.schemaFile; // unreachable
        } finally {
            tab.schemaDetecting.set(false);
        }
    }

    /**
     * Sniffs the schema location the buffer declares — a top-level {@code "$schema"}
     * member for JSON, {@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation}
     * for the XML family. When the XML family declares nothing, falls back to the Schema
     * Library resolution ({@link #schemaFromLibrary(String)}) so a library auto-binding is
     * treated as "declared" too and survives reconcile. Worker-thread safe (fresh,
     * stateless service instances).
     */
    private String declaredSchemaLocation(EditorTab tab, String content, File baseDir) {
        if (tab.document.getFileType() == EditorFileType.JSON) {
            var jsonService = new org.fxt.freexmltoolkit.service.JsonService();
            // A library-mapped raw $schema id (meta-schema ids included) is a "declaration"
            // too — see detectJsonSchemaFor. Without this, the filtered
            // getSchemaLocationFromJsonContent alone would report it as undeclared and the
            // reconcile would CLEAR the library binding on the very first validation run.
            // Gated on the auto-bind toggle (like the XML family's schemaFromLibrary), so
            // turning auto-bind off and reconciling clears a library binding here too.
            java.util.Optional<String> raw = jsonService.getRawSchemaIdFromJsonContent(content);
            if (raw.isPresent() && org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).isSchemaLibraryAutoBindEnabled()
                    && org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared()
                    .resolveJsonSchema(raw.get()).isPresent()) {
                return raw.get();
            }
            return jsonService.getSchemaLocationFromJsonContent(content).orElse(null);
        }
        String declared = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.XmlService.class)
                .getSchemaNameFromXmlContent(content, baseDir)
                .orElse(null);
        if (declared == null) {
            // No xsi:schemaLocation declared — a Schema Library auto-binding (see
            // detectSchemaFor) is not a "declaration" the reconcile can see otherwise,
            // so without this the very first validation would CLEAR it. Re-resolving
            // the library lookup here is deterministic for local entries (same path
            // every call), so it correctly short-circuits to KEEP once bound.
            LibraryHit fromLibrary = schemaFromLibrary(content);
            if (fromLibrary != null) {
                declared = fromLibrary.file().getAbsolutePath();
            }
        }
        return declared;
    }

    /** FX thread: clears the editor/IntelliSense side of a dropped schema binding. */
    private void clearSchemaBindingUi(EditorTab tab) {
        tab.view.clearSchema();
        tab.view.invalidateIntelliSenseCache();
        tab.xmlSchemaProvider = null;
        if (tab.xmlEditorContext != null) {
            tab.xmlEditorContext.setSchemaProvider(null);
        }
        if (tab.isSelected()) {
            activeSchema.set(null);
        }
    }

    /**
     * Auto-detects and binds the XSD declared in {@code content} (the document text,
     * usually the live editor buffer): resolves both {@code xsi:schemaLocation}
     * (namespaced) and {@code xsi:noNamespaceSchemaLocation}, local (relative to
     * {@code pathOrNull}'s directory when given) and remote (http/https, downloaded and
     * cached) references via {@link org.fxt.freexmltoolkit.service.XmlService}. A fresh
     * service instance keeps detection per-tab and stateless. Runs on a worker thread
     * (remote download must not block the UI).
     */
    private SchemaDetection detectSchemaFor(EditorTab tab, String content, Path pathOrNull) {
        if (!tab.view.supportsSchema()) {
            return SchemaDetection.NOT_FOUND;
        }
        if (tab.document.getFileType() == EditorFileType.JSON) {
            return detectJsonSchemaFor(tab, content, pathOrNull);
        }
        try {
            org.fxt.freexmltoolkit.service.XmlService service =
                    new org.fxt.freexmltoolkit.service.XmlServiceImpl();
            File baseDir = pathOrNull != null && pathOrNull.getParent() != null
                    ? pathOrNull.getParent().toFile() : null;
            java.util.Optional<String> declared = service.getSchemaNameFromXmlContent(content, baseDir);
            // Remember what the buffer declared (also on failure below) so the
            // validation-time reconcile can short-circuit unchanged documents.
            tab.lastDetectedSchemaLocation = declared.orElse(null);
            if (declared.isEmpty()) {
                // No xsi:schemaLocation — ask the Schema Library by root namespace / root element.
                LibraryHit fromLibrary = schemaFromLibrary(content);
                if (fromLibrary == null) {
                    return SchemaDetection.NOT_FOUND; // the document references no schema
                }
                tab.lastDetectedSchemaLocation = fromLibrary.file().getAbsolutePath();
                if (!tab.view.loadSchema(fromLibrary.file())) {
                    return SchemaDetection.failed(fromLibrary.source(), SchemaDetection.UNLOADABLE);
                }
                return new SchemaDetection(fromLibrary.file(), SchemaStatus.READY,
                        fromLibrary.source(), fromLibrary.detail());
            }
            // A reference exists. Schema Library / XML catalogs first: a `system` or
            // `rewriteSystem` catalog entry (or a user mapping) may redirect the declared
            // location to a local file, so an unreachable URL never has to be fetched.
            LibraryHit hit = schemaFromLibraryForDeclared(content, declared.get(), baseDir);
            File xsd;
            SchemaSource source;
            String detail;
            if (hit != null) {
                xsd = hit.file();
                source = hit.source();
                detail = hit.detail();
            } else {
                // From here on, every failure is an ERROR (the user expects IntelliSense
                // but won't get it), not a silent "No XSD".
                if (!service.loadSchemaFromLocation(declared.get())) {
                    // Unreachable location: the Schema Library may still know the
                    // document's namespace (user mapping, catalog `uri`, bundled entry).
                    LibraryHit fallback = schemaFromLibrary(content);
                    if (fallback == null) {
                        // referenced, but not resolvable
                        return SchemaDetection.failed(SchemaSource.DECLARED, SchemaDetection.UNRESOLVED,
                                declared.get());
                    }
                    xsd = fallback.file();
                    source = fallback.source();
                    detail = fallback.detail();
                } else {
                    xsd = service.getCurrentXsdFile();
                    source = SchemaSource.DECLARED;
                    detail = declared.get();
                }
            }
            if (xsd == null || !xsd.exists() || !tab.view.loadSchema(xsd)) {
                return SchemaDetection.failed(source, SchemaDetection.UNLOADABLE); // resolved, but missing or unparsable
            }
            return new SchemaDetection(xsd, SchemaStatus.READY, source, detail);
        } catch (Exception e) {
            // detection is best-effort; a malformed document simply binds no schema
            return SchemaDetection.NOT_FOUND;
        }
    }

    /**
     * Schema Library lookup for a document WITH a declared schema location: catalog
     * {@code system}/{@code rewriteSystem} entries and user mappings are matched against
     * the declared location (resolved against the document's directory), then the root
     * namespace is tried. Honours the auto-bind toggle and the offline rule. Returns null
     * on a miss. Worker-thread safe.
     */
    private static LibraryHit schemaFromLibraryForDeclared(String content, String declared, File baseDir) {
        try {
            if (!org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .isSchemaLibraryAutoBindEnabled()) {
                return null;
            }
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            String namespace = org.fxt.freexmltoolkit.service.XmlRootElementSniffer.sniff(content)
                    .map(r -> r.namespace()).filter(ns -> !ns.isEmpty()).orElse(null);
            String baseUri = baseDir != null ? baseDir.toURI().toString() : null;
            return org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                    .lookup(library, namespace, declared, baseUri, library.isRemoteDownloadAllowed())
                    .map(hit -> new LibraryHit(hit.file().toFile(),
                            hit.viaCatalog() ? SchemaSource.CATALOG : SchemaSource.LIBRARY, hit.detail()))
                    .orElse(null);
        } catch (Exception e) {
            return null;   // best-effort
        }
    }

    /**
     * Schema Library lookup for a document without a schema reference: the document
     * element's namespace (or, without a namespace, its local name) is mapped to an XSD.
     * Returns null when auto-binding is disabled or nothing matches. Worker-thread safe.
     *
     * <p>Auto-binding is a user-enabled feature, so a remote entry is downloaded on first
     * use — but it goes through the same offline rule as the resolver hooks
     * ({@link org.fxt.freexmltoolkit.service.SchemaLibraryLookup#materializeIfAllowed}), so
     * disabling remote downloads ({@code -Dfxt.schema.namespaceFallback=false}, as the test
     * suite does) restricts auto-binding to local and already-cached entries too.</p>
     */
    private static LibraryHit schemaFromLibrary(String content) {
        try {
            if (!org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .isSchemaLibraryAutoBindEnabled()) {
                return null;
            }
            var root = org.fxt.freexmltoolkit.service.XmlRootElementSniffer.sniff(content).orElse(null);
            if (root == null) return null;
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            boolean byRootElement = root.namespace().isEmpty();
            var entry = byRootElement
                    ? library.resolveByRootElement(root.localName())
                    : library.resolveNamespace(root.namespace(), org.fxt.freexmltoolkit.domain.SchemaKind.XSD);
            if (entry.isEmpty()) return null;
            File file = org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                    .materializeIfAllowed(library, entry.get(), library.isRemoteDownloadAllowed())
                    .map(java.nio.file.Path::toFile).orElse(null);
            if (file == null) return null;
            // resolveByRootElement never consults catalogs, so a root-element match is
            // always a plain library mapping; a namespace match may be catalog-sourced.
            SchemaSource source = !byRootElement
                    && entry.get().source() == org.fxt.freexmltoolkit.domain.EntrySource.CATALOG
                    ? SchemaSource.CATALOG : SchemaSource.LIBRARY;
            String detail = source == SchemaSource.CATALOG
                    ? entry.get().location()
                    : (byRootElement ? root.localName() : root.namespace());
            return new LibraryHit(file, source, detail);
        } catch (Exception e) {
            return null;   // best-effort
        }
    }

    /**
     * JSON counterpart of the XSD detection: binds the JSON Schema a top-level
     * {@code "$schema"} member declares — local (relative to the document's directory)
     * or remote (http/https, downloaded and cached). Meta-schema ids (json-schema.org)
     * are not bindings and detect as "no schema", unless the Schema Library maps them.
     * Runs on a worker thread.
     */
    private SchemaDetection detectJsonSchemaFor(EditorTab tab, String content, Path pathOrNull) {
        try {
            var service = new org.fxt.freexmltoolkit.service.JsonService();
            File baseDir = pathOrNull != null && pathOrNull.getParent() != null
                    ? pathOrNull.getParent().toFile() : null;
            // Schema Library first: a mapped $schema URI (meta-schema ids included) binds its local file.
            java.util.Optional<String> raw = service.getRawSchemaIdFromJsonContent(content);
            if (raw.isPresent() && org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).isSchemaLibraryAutoBindEnabled()) {
                var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
                var entry = library.resolveJsonSchema(raw.get());
                File mapped = entry.flatMap(e -> org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                                .materializeIfAllowed(library, e, library.isRemoteDownloadAllowed()))
                        .map(java.nio.file.Path::toFile).orElse(null);
                if (mapped != null) {
                    tab.lastDetectedSchemaLocation = raw.get();
                    SchemaSource source = entry.get().source() == org.fxt.freexmltoolkit.domain.EntrySource.CATALOG
                            ? SchemaSource.CATALOG : SchemaSource.LIBRARY;
                    if (!tab.view.loadSchema(mapped)) {
                        return SchemaDetection.failed(source, SchemaDetection.UNLOADABLE);
                    }
                    String detail = source == SchemaSource.CATALOG ? entry.get().location() : raw.get();
                    return new SchemaDetection(mapped, SchemaStatus.READY, source, detail);
                }
            }
            java.util.Optional<String> declared = service.getSchemaLocationFromJsonContent(content);
            // Remember what the buffer declared (also on failure below) so the
            // validation-time reconcile can short-circuit unchanged documents.
            tab.lastDetectedSchemaLocation = declared.orElse(null);
            if (declared.isEmpty()) {
                return SchemaDetection.NOT_FOUND; // the document references no schema
            }
            // A reference exists — from here on, every failure is an ERROR (the user
            // expects schema validation but won't get it), not a silent "No Schema".
            File schema = service.resolveJsonSchemaLocation(declared.get(), baseDir);
            if (schema == null || !schema.exists()) {
                return SchemaDetection.failed(SchemaSource.DECLARED, SchemaDetection.UNRESOLVED, declared.get());
            }
            if (!tab.view.loadSchema(schema)) {
                return SchemaDetection.failed(SchemaSource.DECLARED, SchemaDetection.UNLOADABLE);
            }
            return new SchemaDetection(schema, SchemaStatus.READY, SchemaSource.DECLARED, declared.get());
        } catch (Exception e) {
            // detection is best-effort; a malformed document simply binds no schema
            return SchemaDetection.NOT_FOUND;
        }
    }

    /**
     * Builds the read-only, XSD-derived schema provider for an XML-instance document off the
     * UI thread (parsing the XSD is expensive), then attaches it to the tab and — if the tab is
     * front-most — refreshes the inspector so the selected element shows its declared type and
     * documentation. Best-effort: a malformed/missing XSD simply binds no provider.
     */
    private void loadXmlSchemaProviderAsync(EditorTab tab, File xsd) {
        if (xsd == null || tab.document.getFileType() != EditorFileType.XML) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                // The editor view has usually just parsed this very XSD for IntelliSense: share that
                // model (read-only) instead of running the whole schema pipeline a second time.
                var data = tab.view instanceof XmlEditorView xmlView ? xmlView.loadedSchemaData(xsd) : null;
                if (data == null) {
                    var service = new org.fxt.freexmltoolkit.service.XsdDocumentationService();
                    service.setXsdFilePath(xsd.getAbsolutePath());
                    service.setEagerSampleData(false);
                    service.processXsd(Boolean.TRUE);
                    data = service.xsdDocumentationData;
                }
                var adapter = new org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter();
                adapter.setXsdDocumentationData(data);
                // The V2 model of the same XSD: lets the inspector show (and, through the schema's
                // own tab, edit) the declaration behind an element. Best-effort like the provider.
                org.fxt.freexmltoolkit.controls.v2.model.XsdSchema model = null;
                try {
                    model = XsdUiParser.parse(Files.readString(xsd.toPath(), StandardCharsets.UTF_8),
                            xsd.toPath(), null);
                } catch (Exception | LinkageError e) {
                    model = null;
                }
                var boundModel = model;
                Platform.runLater(() -> {
                    tab.boundSchemaModel = boundModel;
                    tab.xmlSchemaProvider = adapter;
                    if (tab.xmlEditorContext != null) {
                        tab.xmlEditorContext.setSchemaProvider(adapter);
                    }
                    // Binding the schema drops the caret-selected node; pick it up again so the
                    // inspector shows the schema facts without the user moving the caret.
                    tab.resolveCaretXmlNode();
                    if (tab.isSelected()) {
                        refreshSelectedNode();
                    }
                    xmlSchemaProviderRevision.set(xmlSchemaProviderRevision.get() + 1);
                });
            } catch (Exception e) {
                // schema-derived info is best-effort; ignore failures
            }
        });
    }

    /**
     * @return XSD-derived type/documentation for an XML-instance element at the given XPath, if
     * a schema is bound to the active document; otherwise empty.
     */
    public java.util.Optional<
            org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ElementTypeInfo>
            resolveActiveXmlElementInfo(String elementXPath) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null) {
            return et.xmlSchemaProvider.getElementTypeInfo(elementXPath);
        }
        return java.util.Optional.empty();
    }

    /** @return the schema-allowed child element (local) names for the element at the given XPath. */
    public java.util.List<String> resolveValidChildren(String elementXPath) {
        var map = activeXsdElementMap();
        var element = activeXsdElement(elementXPath);
        if (map == null || element == null) {
            return java.util.List.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        collectChildElementNames(element, map, out, new java.util.HashSet<>());
        return out.stream().distinct().toList();
    }

    /** Collects real child element names, descending through compositor nodes (sequence/choice/all). */
    private void collectChildElementNames(org.fxt.freexmltoolkit.domain.XsdExtendedElement element,
            java.util.Map<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> map,
            java.util.List<String> out, java.util.Set<String> visited) {
        if (element.getChildren() == null) {
            return;
        }
        for (String childXPath : element.getChildren()) {
            if (childXPath == null || childXPath.contains("@") || !visited.add(childXPath)) {
                continue; // attributes / cycles
            }
            String local = localXmlName(childXPath);
            if (isCompositorName(local)) {
                var child = map.get(childXPath);
                if (child != null) {
                    collectChildElementNames(child, map, out, visited);
                }
            } else if (local != null && !local.isBlank()) {
                out.add(local);
            }
        }
    }

    /** @return true for the synthetic compositor node names the documentation model inserts. */
    private static boolean isCompositorName(String name) {
        return name != null && name.matches("(?i)(SEQUENCE|CHOICE|ALL|GROUP)(_\\d+)?");
    }

    /**
     * @return the per-language schema documentation entries for the element at the given XPath
     * (lang is lowercase, {@code "default"} for entries without {@code xml:lang}); empty when
     * no schema is bound or the path cannot be resolved.
     */
    public java.util.List<org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo>
            resolveActiveXmlElementDocs(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        if (element == null || element.getDocumentations() == null) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(element.getDocumentations());
    }

    /** @return example/allowed values for the element at the given XPath (xs:example, else enumeration). */
    public java.util.List<String> resolveExampleValues(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        java.util.List<String> values = new java.util.ArrayList<>();
        if (element != null && element.getExampleValues() != null) {
            values.addAll(element.getExampleValues());
        }
        if (values.isEmpty()) {
            resolveActiveXmlElementInfo(elementXPath).ifPresent(info -> {
                if (info.enumerationValues() != null) {
                    values.addAll(info.enumerationValues());
                }
            });
        }
        return values.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    /**
     * @return base type and derivation ({@code restriction} / {@code extension}) of the schema type
     * of the XML element at the given XPath; empty when no schema is bound or the type is not derived
     */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation.Derivation>
            resolveActiveXmlElementDerivation(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        if (element == null) {
            return java.util.Optional.empty();
        }
        var restriction = element.getRestrictionInfo();
        if (restriction != null && restriction.base() != null && !restriction.base().isBlank()) {
            return java.util.Optional.of(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation
                    .Derivation(restriction.base(), "restriction"));
        }
        String extensionBase = element.getSimpleContentExtensionBase();
        if (extensionBase != null && !extensionBase.isBlank()) {
            return java.util.Optional.of(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation
                    .Derivation(extensionBase, "extension"));
        }
        return java.util.Optional.empty();
    }

    /**
     * @return the position of the XML element's declaration in its parent compositor
     * ({@code "2 of 6 · sequence"}); empty when no schema is bound or the parent is no compositor
     */
    public java.util.Optional<String> resolveActiveXmlElementOrder(String elementXPath) {
        var map = activeXsdElementMap();
        var element = activeXsdElement(elementXPath);
        if (map == null || element == null || element.getParentXpath() == null) {
            return java.util.Optional.empty();
        }
        var parent = map.get(element.getParentXpath());
        String compositor = parent == null ? null : localXmlName(parent.getCurrentXpath());
        if (parent == null || !isCompositorName(compositor) || parent.getChildren() == null) {
            return java.util.Optional.empty();
        }
        java.util.List<String> particles = parent.getChildren().stream()
                .filter(x -> x != null && !x.contains("@")).toList();
        int index = particles.indexOf(element.getCurrentXpath());
        if (index < 0) {
            return java.util.Optional.empty();
        }
        String kind = compositor.replaceAll("_\\d+$", "").toLowerCase(java.util.Locale.ROOT);
        return java.util.Optional.of(org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation
                .format(index + 1, particles.size(), kind));
    }

    /**
     * Checks a value against the schema facets/enumeration of the XML element at the given XPath.
     *
     * @return the validator's message ({@code null} when the value fits); empty when no schema is
     * bound to the active document
     */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult>
            validateActiveXmlElementValue(String elementXPath, String value) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null) {
            return java.util.Optional.of(et.xmlSchemaProvider.validateElementValue(elementXPath, value));
        }
        return java.util.Optional.empty();
    }

    /**
     * Schema facts of an XML-instance element beyond {@code ElementTypeInfo}: nillable, the
     * {@code @since}/{@code @deprecated} doc tags, list/union member types, raw appinfo entries and
     * the identity constraints / assertions / type alternatives declared on it.
     */
    public record XmlSchemaDetails(boolean nillable, String since, String deprecated, String listItemType,
            java.util.List<String> unionMemberTypes, java.util.List<String> appInfos,
            java.util.List<org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo> rules) {
    }

    /** @return the extra schema facts of the XML element at the given XPath; empty without a schema */
    public java.util.Optional<XmlSchemaDetails> resolveActiveXmlElementDetails(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        if (element == null) {
            return java.util.Optional.empty();
        }
        boolean nillable = element.getCurrentNode() instanceof org.w3c.dom.Element dom
                && "true".equals(dom.getAttribute("nillable").trim());
        var docInfo = element.getXsdDocInfo();
        java.util.List<org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo> rules =
                new java.util.ArrayList<>();
        if (element.getIdentityConstraints() != null) {
            for (var ic : element.getIdentityConstraints()) {
                String detail = "selector " + ic.getSelector() + " · field " + String.join(", ", ic.getFields())
                        + (ic.getRefer() != null && !ic.getRefer().isBlank() ? " · refer " + ic.getRefer() : "");
                rules.add(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo(
                        ic.getTypeDisplayName(), ic.getName(), detail, null));
            }
        }
        if (element.getAssertions() != null) {
            for (var as : element.getAssertions()) {
                rules.add(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo(
                        "Assertion", "", as.getTest(), null));
            }
        }
        if (element.getTypeAlternatives() != null) {
            for (var alt : element.getTypeAlternatives()) {
                rules.add(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo(
                        "Alternative", alt.getType(), alt.getDisplayCondition(), null));
            }
        }
        return java.util.Optional.of(new XmlSchemaDetails(nillable,
                docInfo == null ? null : docInfo.getSince(),
                docInfo == null ? null : docInfo.getDeprecated(),
                element.getListItemType(),
                element.getUnionMemberTypes() == null ? java.util.List.of() : java.util.List.copyOf(element.getUnionMemberTypes()),
                element.getGenericAppInfos() == null ? java.util.List.of() : java.util.List.copyOf(element.getGenericAppInfos()),
                rules));
    }

    /** @return schema facts of an attribute of the XML element at the given XPath; empty without a schema */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.AttributeTypeInfo>
            resolveActiveXmlAttributeInfo(String elementXPath, String attributeName) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null
                && attributeName != null) {
            try {
                return et.xmlSchemaProvider.getAttributeTypeInfo(elementXPath, attributeName);
            } catch (RuntimeException e) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    /** @return the attribute names the schema declares for the XML element at the given XPath */
    public java.util.List<String> resolveActiveXmlValidAttributes(String elementXPath) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null) {
            try {
                return et.xmlSchemaProvider.getValidAttributes(elementXPath);
            } catch (RuntimeException e) {
                return java.util.List.of();
            }
        }
        return java.util.List.of();
    }

    // ----- the bound XSD declaration behind an XML element (show + edit from the XML document) -----

    /**
     * The declaration in the bound XSD that governs an XML-instance element or attribute.
     *
     * @param located        particle/declaration/named type in {@code schema}
     * @param schema         the V2 model it was found in (the schema tab's live model when that tab
     *                       is open, else a read-only cache parsed when the schema was bound)
     * @param schemaFileName the bound XSD's file name, for display
     * @param editable       whether edits can be written into the bound XSD
     * @param readOnlyReason why not, when {@code editable} is false
     */
    public record XmlSchemaDeclaration(org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.Located located,
            org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema, String schemaFileName, boolean editable,
            String readOnlyReason) {
    }

    /**
     * @return the XSD declaration of the element at {@code elementXPath} (or of its attribute
     * {@code attributeName}) in the active XML document's bound schema; empty without a schema or
     * when the path cannot be mapped unambiguously
     */
    public java.util.Optional<XmlSchemaDeclaration> resolveActiveXmlSchemaDeclaration(String elementXPath,
            String attributeName) {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
                || et.document.getFileType() != EditorFileType.XML || et.schemaFile == null || elementXPath == null) {
            return java.util.Optional.empty();
        }
        EditorTab schemaTab = findTabFor(et.schemaFile.toPath());
        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = et.boundSchemaModel;
        if (schemaTab != null && !schemaTab.loading) {
            schemaTab.ensureModelParsed();
            schema = schemaTab.editorContext != null ? schemaTab.editorContext.getSchema() : null;
        }
        if (schema == null) {
            return java.util.Optional.empty();
        }
        var located = org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locate(schema,
                org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(elementXPath),
                attributeName);
        if (located.isEmpty()) {
            return java.util.Optional.empty();
        }
        String reason = located.get().foreign()
                ? "Read-only: declared in an included or imported schema."
                : schemaReadOnlyReason(et.schemaFile);
        return java.util.Optional.of(new XmlSchemaDeclaration(located.get(), schema, et.schemaFile.getName(),
                reason == null, reason));
    }

    /**
     * @return why a bound XSD must not be edited from an XML document, or {@code null}: a copy the
     * application manages (schema cache / library under {@code ~/.freeXmlToolkit}) would be
     * overwritten by the next download, and a read-only file cannot be saved
     */
    static String schemaReadOnlyReason(File xsd) {
        if (xsd == null) {
            return "No schema file.";
        }
        Path file = xsd.toPath().toAbsolutePath().normalize();
        Path appDir = Path.of(System.getProperty("user.home"), ".freeXmlToolkit").toAbsolutePath().normalize();
        if (file.startsWith(appDir)) {
            return "Read-only: a cached copy from the Schema Library.";
        }
        if (Files.exists(file) && !Files.isWritable(file)) {
            return "The schema file is read-only.";
        }
        return null;
    }

    /** One edit of a bound-XSD declaration, applied to the schema's own tab. */
    @FunctionalInterface
    public interface SchemaEdit {
        boolean apply(SchemaEditTarget target);
    }

    /**
     * The declaration being edited, in the bound XSD's tab: every change is a command on that tab's
     * stack (one undo step there, the tab turns dirty, the text follows by round-trip). Cardinality
     * and {@code use} go to the particle, everything else to the declaration; facets go to
     * {@code facetOwner} (the declaration, or its named simple type).
     */
    public static final class SchemaEditTarget {
        private final EditorTab tab;
        private final org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.Located located;

        private SchemaEditTarget(EditorTab tab,
                org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.Located located) {
            this.tab = tab;
            this.located = located;
        }

        /** @return the located nodes in the schema tab's live model */
        public org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.Located located() {
            return located;
        }

        public boolean changeType(String type) {
            return tab.changeType(located.declaration(), type);
        }

        public boolean changeCardinality(int min, int max) {
            return tab.changeCardinality(located.particle(), min, max);
        }

        public boolean changeUse(String use) {
            return tab.changeUse(located.particle(), use);
        }

        public boolean changeForm(String form) {
            return tab.changeForm(located.declaration(), form);
        }

        public boolean changeConstraints(boolean nillable, boolean abstractFlag, String fixed) {
            return tab.changeConstraints(located.declaration(), nillable, abstractFlag, fixed);
        }

        public boolean changeDefaultValue(String value) {
            return tab.changeDefaultValue(located.declaration(), value);
        }

        public boolean changeSubstitutionGroup(String group) {
            return tab.changeSubstitutionGroup(located.declaration(), group);
        }

        public boolean changeBlock(String block) {
            return tab.changeBlock(located.declaration(), block);
        }

        public boolean changeDocumentations(java.util.List<org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation> docs) {
            return tab.changeDocumentations(located.declaration(), docs);
        }

        public boolean changeAppinfo(org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo appinfo) {
            return tab.changeAppinfo(located.declaration(), appinfo);
        }

        public boolean changeAppinfo(String appinfo) {
            return tab.changeAppinfo(located.declaration(), appinfo);
        }

        /** @return the node whose facets are edited: the named simple type, or the declaration */
        public XsdNode facetOwner(boolean namedType) {
            return namedType && located.namedType() != null ? located.namedType() : located.declaration();
        }

        public boolean addFacet(boolean namedType, org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType type,
                String value) {
            return tab.addFacet(facetOwner(namedType), type, value);
        }

        /** Edits the facet matching {@code shown} (same kind and value) — {@code shown} may come from the cache. */
        public boolean editFacet(boolean namedType, org.fxt.freexmltoolkit.controls.v2.model.XsdFacet shown,
                String value) {
            var facet = matchingFacet(facetOwner(namedType), shown);
            return facet != null && tab.editFacet(facet, value);
        }

        public boolean deleteFacet(boolean namedType, org.fxt.freexmltoolkit.controls.v2.model.XsdFacet shown) {
            var facet = matchingFacet(facetOwner(namedType), shown);
            return facet != null && tab.deleteFacet(facet);
        }

        private static org.fxt.freexmltoolkit.controls.v2.model.XsdFacet matchingFacet(XsdNode owner,
                org.fxt.freexmltoolkit.controls.v2.model.XsdFacet shown) {
            if (owner == null || shown == null) {
                return null;
            }
            for (var facet : org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets.collect(owner)) {
                if (facet == shown) {
                    return facet;
                }
            }
            for (var facet : org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets.collect(owner)) {
                if (facet.getFacetType() == shown.getFacetType()
                        && java.util.Objects.equals(facet.getValue(), shown.getValue())) {
                    return facet;
                }
            }
            return null;
        }
    }

    /**
     * Applies {@code edit} to the bound-XSD declaration of the active XML document's element at
     * {@code elementXPath} (or its attribute). The schema opens in a background tab first when it
     * is not open yet, so the change lands on its undo stack and shows as unsaved there; the XML
     * tab keeps the focus. Asynchronous when the schema still has to be loaded.
     *
     * @param onDone receives whether the edit was applied (on the FX thread); may be {@code null}
     */
    public void editActiveXmlSchemaDeclaration(String elementXPath, String attributeName, SchemaEdit edit,
            java.util.function.Consumer<Boolean> onDone) {
        java.util.function.Consumer<Boolean> done = onDone != null ? onDone : ok -> { };
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab xmlTab)
                || xmlTab.schemaFile == null || edit == null) {
            done.accept(false);
            return;
        }
        File xsd = xmlTab.schemaFile;
        withSchemaTab(xsd.toPath(), schemaTab -> {
            schemaTab.ensureModelParsed();
            if (schemaTab.editorContext == null) {
                done.accept(false);
                return;
            }
            var located = org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locate(
                    schemaTab.editorContext.getSchema(),
                    org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(elementXPath),
                    attributeName);
            if (located.isEmpty() || located.get().foreign() || schemaReadOnlyReason(xsd) != null) {
                done.accept(false);
                return;
            }
            boolean ok = edit.apply(new SchemaEditTarget(schemaTab, located.get()));
            if (ok) {
                // The inspector re-reads the declaration from the schema tab's live model.
                xmlSchemaProviderRevision.set(xmlSchemaProviderRevision.get() + 1);
            }
            done.accept(ok);
        });
    }

    /**
     * Opens the bound XSD of the active XML document in front, in the Graphic view, expanded along
     * the instance path down to the card of the element at {@code elementXPath} (or its
     * attribute), and selects it — by structure, not by name, so same-named local declarations and
     * the several occurrences of a named type's children are told apart.
     */
    public void revealActiveXmlSchemaDeclaration(String elementXPath, String attributeName) {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab xmlTab)
                || xmlTab.schemaFile == null) {
            return;
        }
        java.util.List<String> path =
                org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(elementXPath);
        withSchemaTab(xmlTab.schemaFile.toPath(), schemaTab -> {
            tabPane.getSelectionModel().select(schemaTab);
            setActiveViewMode(ViewMode.GRAPHIC);
            if (schemaTab.editorContext == null) {
                return;
            }
            org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locate(
                    schemaTab.editorContext.getSchema(), path, attributeName)
                    .ifPresent(l -> revealPathInActiveGraph(l.particlePath()));
        });
    }

    /** @return the open editor tab of {@code path}, or {@code null} */
    private EditorTab findTabFor(Path path) {
        Path wanted = path.toAbsolutePath().normalize();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.getPath() != null
                    && wanted.equals(et.document.getPath().toAbsolutePath().normalize())) {
                return et;
            }
        }
        return null;
    }

    /** Runs {@code action} with the loaded tab of {@code path}, opening it behind the active tab if needed. */
    private void withSchemaTab(Path path, java.util.function.Consumer<EditorTab> action) {
        EditorTab open = findTabFor(path);
        if (open != null) {
            if (open.loading) {
                Runnable previous = open.afterLoad;
                open.afterLoad = () -> {
                    if (previous != null) {
                        previous.run();
                    }
                    action.accept(open);
                };
            } else {
                action.accept(open);
            }
            return;
        }
        rememberRecentFile(path);
        EditorTab tab = new EditorTab(OpenDocument.forPath(path), this::refreshSelectedNode, nodeClipboard,
                this::openNamedTypeEditor, this::goToXmlDefinition);
        addTab(tab, false);
        tab.view.setGoToDefinitionHandler(req ->
                openXsdAndReveal(req.xsdFile().toPath(), req.elementName()));
        tab.afterLoad = () -> action.accept(tab);
        loadAsync(tab, path);
    }

    /** After an XSD was saved: XML tabs bound to it reload their schema facts from the new file. */
    private void reloadXmlSchemasBoundTo(Path xsd) {
        Path saved = xsd.toAbsolutePath().normalize();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.schemaFile != null
                    && saved.equals(et.schemaFile.toPath().toAbsolutePath().normalize())) {
                loadXmlSchemaProviderAsync(et, et.schemaFile);
            }
        }
    }

    /** A place a schema component is used: the referring node and a short description. */
    public record SchemaUsage(XsdNode node, String name, String detail) {
    }

    /**
     * Where the given XSD node is used in the active schema. For a global component (named type,
     * global element/attribute, group) these are the places that reference it; for a local
     * declaration it is the global component that contains it. Builds a reference graph — call it
     * off the FX thread.
     */
    public java.util.List<SchemaUsage> resolveSchemaUsages(org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema,
            XsdNode node) {
        if (schema == null || node == null) {
            return java.util.List.of();
        }
        var key = org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.keyOf(node);
        boolean global = key != null && node.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
        java.util.List<SchemaUsage> out = new java.util.ArrayList<>();
        if (global) {
            var graph = org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.build(schema);
            for (var ref : graph.referencesTo(key)) {
                if (ref.isSelfReference() || ref.referrer() == null) {
                    continue;
                }
                XsdNode owner = ref.owner() != null && graph.component(ref.owner()) != null
                        ? graph.component(ref.owner()).node() : null;
                String name = ref.referrer().getName() != null && !ref.referrer().getName().isBlank()
                        ? ref.referrer().getName()
                        : owner != null ? owner.getName() : ref.referrer().getNodeType().name();
                out.add(new SchemaUsage(ref.referrer(), name, usageDetail(ref.referrer(), owner)));
            }
        } else {
            for (XsdNode n = node.getParent(); n != null; n = n.getParent()) {
                if (n.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema
                        && n.getName() != null && !n.getName().isBlank()) {
                    out.add(new SchemaUsage(n, n.getName(), org.fxt.freexmltoolkit.controls.shell.schema
                            .XsdNodeLabels.kindLabel(n.getNodeType())));
                    break;
                }
            }
        }
        return out;
    }

    private static String usageDetail(XsdNode referrer, XsdNode owner) {
        String kind = org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels.kindLabel(referrer.getNodeType());
        return owner != null && owner != referrer && owner.getName() != null
                ? kind + " in " + owner.getName() : kind;
    }

    /** Adds a new child element with the given name to the selected XML element. */
    public boolean addActiveXmlChildElement(String name) {
        return editActiveXml(el -> new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.AddElementCommand(
                el, new org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement(name)));
    }

    /** @return the active document's XSD element map (xpath → extended element), or {@code null}. */
    private java.util.Map<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> activeXsdElementMap() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et
                && et.xmlSchemaProvider
                        instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter adapter
                && adapter.getXsdDocumentationData() != null) {
            return adapter.getXsdDocumentationData().getExtendedXsdElementMap();
        }
        return null;
    }

    private org.fxt.freexmltoolkit.domain.XsdExtendedElement activeXsdElement(String xpath) {
        var map = activeXsdElementMap();
        if (map == null || xpath == null) {
            return null;
        }
        // Full-path resolution lives in the adapter: predicate-free exact match, then a
        // step-wise walk that tolerates the synthetic compositor segments (SEQUENCE_n/
        // CHOICE_n/…) the documentation model inserts into map keys, and an
        // unambiguous-only name fallback. Never guess among same-named declarations —
        // two distinct elements (e.g. "Account" under AssetDetails vs. under Position)
        // must NOT be conflated.
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et
                && et.xmlSchemaProvider
                        instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter adapter) {
            return adapter.resolveElement(xpath);
        }
        return null;
    }

    /**
     * Navigates a selected XML-instance element to its definition in the active document's bound
     * XSD: resolves the element's XPath against the schema, then opens/reveals it in the XSD editor.
     * Shared by the Graphic and Tree context menus (parallels the Text view's "Go to Definition").
     * No-op when no schema is bound or the node is not an element.
     */
    void goToXmlDefinition(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode node) {
        if (!(node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element)) {
            return;
        }
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) || et.schemaFile == null) {
            return;
        }
        String xpath = org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath.positional(node);
        org.fxt.freexmltoolkit.domain.XsdExtendedElement el = activeXsdElement(xpath);
        String elementName = (el != null) ? el.getElementName() : element.getName();
        openXsdAndReveal(et.schemaFile.toPath(), elementName);
    }

    /** @return the local element name from an XPath/child reference (last segment, no prefix/predicate). */
    private static String localXmlName(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String last = ref.substring(ref.lastIndexOf('/') + 1);
        int bracket = last.indexOf('[');
        if (bracket >= 0) {
            last = last.substring(0, bracket);
        }
        int colon = last.indexOf(':');
        return colon >= 0 ? last.substring(colon + 1) : last;
    }

    /** @return the file size in bytes, or {@code -1} if it cannot be determined (call off the FX thread). */
    private static long sizeOrUnknown(Path path) {
        try {
            return Files.size(path);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /**
     * {@link #write(EditorTab, Path)} off the FX thread: the text is taken now, written on a worker, and the
     * document marked clean afterwards only if nothing was typed meanwhile. Writes of one tab run in order.
     * While a write runs the tab is not checked for external changes, so the save itself is never
     * reported as one.
     *
     * @return completes on the FX thread with whether the file was written
     */
    private CompletableFuture<Boolean> writeAsync(EditorTab tab, Path target) {
        String text = tab.view.getText();
        long revision = tab.textRevision;
        tab.saving++;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        CompletableFuture<?> previous = tab.pendingWrite;
        tab.pendingWrite = result;
        previous.handle((ignored, error) -> null).thenRunAsync(() -> {
            DiskStamp stamp;
            try {
                Files.writeString(target, text, StandardCharsets.UTF_8);
                stamp = DiskStamp.of(target);
            } catch (IOException e) {
                stamp = null;
            }
            DiskStamp written = stamp;
            Platform.runLater(() -> {
                tab.saving--;
                if (written != null) {
                    if (tab.textRevision == revision) {
                        tab.document.setDirty(false);
                    }
                    tab.document.setDiskStamp(written);
                    if (tab.document.getFileType() == EditorFileType.XSD) {
                        reloadXmlSchemasBoundTo(target);
                    }
                }
                result.complete(written != null);
            });
        }, org.fxt.freexmltoolkit.FxtGui.executorService);
        return result;
    }

    private boolean write(EditorTab tab, Path target) {
        try {
            Files.writeString(target, tab.view.getText(), StandardCharsets.UTF_8);
            tab.document.setDirty(false);
            // Our own write must not be reported as an external change.
            tab.document.setDiskStamp(DiskStamp.of(target));
            if (tab.document.getFileType() == EditorFileType.XSD) {
                // XML documents bound to this schema pick up the saved declarations.
                reloadXmlSchemasBoundTo(target);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ----- external changes ------------------------------------------------

    /** The titled, fully loaded documents whose files are watched for external changes. */
    private java.util.List<ExternalChangeMonitor.Watched> watchedDocuments() {
        java.util.List<ExternalChangeMonitor.Watched> watched = new java.util.ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && !et.loading && et.saving == 0) {
                watched.add(new ExternalChangeMonitor.Watched(
                        et.document, et.document.getPath(), et.document.getDiskStamp()));
            }
        }
        return watched;
    }

    /**
     * Handles files that were changed or deleted on disk by another program: asks the user,
     * one document after the other, whether to reload/ignore (changed) or keep/close (deleted).
     * Results that were overtaken meanwhile - tab closed, saved, reloaded, saved under another
     * name - are dropped; the next check re-evaluates them.
     */
    private void handleExternalChanges(java.util.List<ExternalChangeMonitor.Change> changes) {
        for (ExternalChangeMonitor.Change change : changes) {
            EditorTab tab = null;
            for (Tab candidate : tabPane.getTabs()) {
                if (candidate instanceof EditorTab et && et.document == change.document()) {
                    tab = et;
                    break;
                }
            }
            if (tab == null || tab.loading || tab.saving > 0 || !change.path().equals(tab.document.getPath())
                    || !java.util.Objects.equals(change.known(), tab.document.getDiskStamp())) {
                continue;
            }
            OpenDocument document = tab.document;
            if (change.known() == null) {
                document.setDiskStamp(change.current()); // first known state: nothing to compare
                continue;
            }
            if (change.deleted()) {
                tabPane.getSelectionModel().select(tab);
                if (externalChangePrompt.fileDeleted(document, document.isDirty())
                        == ExternalChangePrompt.DeletedChoice.CLOSE) {
                    closeTabNow(tab);
                } else {
                    document.setDiskStamp(DiskStamp.MISSING);
                    document.setDirty(true); // the content now only exists in the editor
                }
                continue;
            }
            if (change.diskText() != null && change.diskText().equals(tab.view.getText())) {
                // Touched or rewritten with identical content: nothing to ask.
                document.setDiskStamp(change.current());
                document.setDirty(false);
                continue;
            }
            tabPane.getSelectionModel().select(tab);
            ExternalChangePrompt.ChangedChoice choice =
                    externalChangePrompt.fileChanged(document, document.isDirty());
            // Either way the user has seen this state of the file: do not ask about it again.
            document.setDiskStamp(change.current());
            if (choice == ExternalChangePrompt.ChangedChoice.RELOAD) {
                reloadFromDisk(tab, change.path());
            } else {
                document.setDirty(true); // editor and disk differ; saving overwrites the disk
            }
        }
    }

    /**
     * Replaces a tab's content with the file's current content, discarding unsaved changes.
     * The caret, the view mode and the schema binding are kept (a changed schema reference is
     * picked up by the validation-time reconcile). On a read failure the editor content stays.
     */
    private void reloadFromDisk(EditorTab tab, Path path) {
        tab.loading = true;
        tab.beginLoading();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            DiskStamp stamp = DiskStamp.of(path); // before the read, as in loadAsync
            String content;
            try {
                content = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Platform.runLater(() -> {
                    tab.endLoading();
                    tab.loading = false;
                    if (isAbandoned(tab)) {
                        return;
                    }
                    tab.document.setDirty(true); // still the old content, which is not on disk
                    org.fxt.freexmltoolkit.util.DialogHelper.showActionError("Reload Failed",
                            "Could not read " + path + ".",
                            "The editor still shows the previous content.", e);
                });
                return;
            }
            Platform.runLater(() -> {
                tab.endLoading();
                tab.loading = false;
                if (isAbandoned(tab)) {
                    return;
                }
                tab.replaceContentFromDisk(content);
                tab.document.setDirty(false);
                tab.document.setDiskStamp(stamp);
                if (tab.isSelected()) {
                    activeViewMode.set(tab.viewMode);
                    refreshSelectedNode();
                }
            });
        });
    }

    /** Test seam: answers external-change prompts without a blocking dialog. */
    void setExternalChangePrompt(ExternalChangePrompt prompt) {
        this.externalChangePrompt = java.util.Objects.requireNonNull(prompt, "prompt");
    }

    /** Test seam: checks the open documents' files now, regardless of window focus. */
    void checkExternalChangesNow() {
        externalChangeMonitor.checkNow();
    }

    /** Test seam: whether an external-change check is running or being handled. */
    boolean isExternalChangeCheckRunning() {
        return externalChangeMonitor.isBusy();
    }

    private String untitledName() {
        return "Untitled " + (openDocuments.size() + 1);
    }

    private void setupDragAndDrop() {
        setOnDragOver(event -> {
            var dragboard = event.getDragboard();
            if (dragboard.hasFiles() && DragDropService.hasFilesWithExtensions(
                    dragboard.getFiles(), EditorFileType.openableExtensions())) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            boolean done = false;
            if (dragboard.hasFiles()) {
                for (File file : DragDropService.filterByExtensions(dragboard.getFiles(),
                        EditorFileType.openableExtensions())) {
                    try {
                        openFile(file.toPath());
                        done = true;
                    } catch (Exception ex) {
                        org.apache.logging.log4j.LogManager.getLogger(EditorHost.class)
                                .warn("Could not open dropped file '{}': {}", file.getName(), ex.getMessage());
                    }
                }
            }
            event.setDropCompleted(done);
            event.consume();
        });
    }

    /** The user's answer to the exit prompt for unsaved documents. */
    public enum ExitChoice { SAVE_ALL, DISCARD, CANCEL }

    /**
     * Asks about unsaved documents before the application closes (window button, File → Exit,
     * Ctrl/Cmd+Q, macOS Quit): one dialog listing them with Save All / Don't Save / Cancel.
     *
     * @return whether the application may close now
     */
    public boolean confirmExit() {
        return confirmExit(this::promptExit);
    }

    /**
     * @param prompter asks the user about the given unsaved documents (shown only when there are any)
     * @return whether the application may close now
     */
    boolean confirmExit(java.util.function.Function<java.util.List<OpenDocument>, ExitChoice> prompter) {
        java.util.List<EditorTab> dirty = new java.util.ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.isDirty()) {
                dirty.add(et);
            }
        }
        if (dirty.isEmpty()) {
            return true;
        }
        tabPane.getSelectionModel().select(dirty.get(0));
        ExitChoice choice = prompter.apply(dirty.stream().map(et -> et.document).toList());
        if (choice == null || choice == ExitChoice.CANCEL) {
            return false;
        }
        if (choice == ExitChoice.SAVE_ALL) {
            for (EditorTab tab : dirty) {
                tabPane.getSelectionModel().select(tab);
                boolean ok = tab.document.isUntitled() ? saveTabAs(tab) : write(tab, tab.document.getPath());
                if (!ok) {
                    return false; // a cancelled Save As or a failed write keeps the application open
                }
            }
        }
        return true;
    }

    private ExitChoice promptExit(java.util.List<OpenDocument> documents) {
        boolean one = documents.size() == 1;
        String names = documents.stream().map(OpenDocument::getDisplayName)
                .collect(java.util.stream.Collectors.joining("\n"));
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Unsaved Changes",
                one ? "Save changes to " + documents.get(0).getDisplayName() + " before quitting?"
                        : "Save changes to " + documents.size() + " documents before quitting?",
                (one ? "" : names + "\n\n") + "Your changes will be lost if you don't save them.");
        javafx.scene.control.ButtonType save = new javafx.scene.control.ButtonType(
                one ? "Save" : "Save All", javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType discard =
                new javafx.scene.control.ButtonType("Don't Save", javafx.scene.control.ButtonBar.ButtonData.NO);
        javafx.scene.control.ButtonType cancel =
                new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }
        var choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            return ExitChoice.CANCEL;
        }
        return choice.get() == save ? ExitChoice.SAVE_ALL : ExitChoice.DISCARD;
    }

    private void confirmCloseIfDirty(EditorTab tab, javafx.event.Event closeEvent) {
        if (!tab.document.isDirty()) {
            return;
        }
        tabPane.getSelectionModel().select(tab);
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Unsaved Changes",
                "Save changes to " + tab.document.getDisplayName() + "?",
                "Your changes will be lost if you don't save them.");
        javafx.scene.control.ButtonType save =
                new javafx.scene.control.ButtonType("Save", javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType discard =
                new javafx.scene.control.ButtonType("Don't Save", javafx.scene.control.ButtonBar.ButtonData.NO);
        javafx.scene.control.ButtonType cancel =
                new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);

        var choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            closeEvent.consume();
        } else if (choice.get() == save) {
            boolean ok = tab.document.isUntitled() ? saveTabAs(tab) : write(tab, tab.document.getPath());
            if (!ok) {
                closeEvent.consume();
            }
        }
        // 'Don't Save' falls through and the tab closes.
    }

    private boolean saveTabAs(EditorTab tab) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save As");
        tab.document.getFileType().configureSaveChooser(chooser, tab.document.getDisplayName());
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        Path target = file == null ? null : withTypeExtension(tab, file.toPath());
        if (target != null && write(tab, relocateGeneratedSchemaReference(tab, target))) {
            tab.document.setPath(target);
            tab.refreshIcon();
            installBreakpointGutter(tab);
            rememberRecentFile(target);
            return true;
        }
        return false;
    }

    /**
     * Appends the document type's extension to a Save-As target that has none (a new XML
     * document saved as {@code foo} becomes {@code foo.xml}). The native chooser only checked
     * the bare name for an existing file, so an appended name that already exists is confirmed
     * here.
     *
     * @return the target to write, or {@code null} if the user declined to replace a file
     */
    private Path withTypeExtension(EditorTab tab, Path target) {
        Path normalized = tab.document.getFileType().withDefaultExtension(target);
        if (normalized == null || normalized.equals(target) || !java.nio.file.Files.exists(normalized)) {
            return normalized;
        }
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Replace File",
                normalized.getFileName() + " already exists.",
                "Do you want to replace it?");
        javafx.scene.control.ButtonType replace =
                new javafx.scene.control.ButtonType("Replace", javafx.scene.control.ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(replace, javafx.scene.control.ButtonType.CANCEL);
        return alert.showAndWait().filter(replace::equals).isPresent() ? normalized : null;
    }

    /**
     * First save of a generated document (Generate Sample XML): its root element references
     * the bound schema by bare file name, which only resolves next to the schema. Saving it
     * anywhere else rewrites that reference to a path relative to {@code target}'s folder,
     * so the file validates from where it lands — also in other tools and after a restart.
     * Titled documents and hand-written references are never touched
     * (see {@link org.fxt.freexmltoolkit.service.SampleSchemaLocation#relocate}).
     *
     * @return {@code target}, for chaining into {@link #write(EditorTab, Path)}
     */
    private Path relocateGeneratedSchemaReference(EditorTab tab, Path target) {
        File xsd = tab.schemaFile;
        if (xsd != null && tab.document.isUntitled() && target != null && target.getParent() != null) {
            org.fxt.freexmltoolkit.service.SampleSchemaLocation
                    .relocate(tab.view.getText(), xsd, target.getParent())
                    .ifPresent(tab.view::setText);
        }
        return target;
    }

    /** A tab bound to one {@link OpenDocument}, its editor view, and Text/Tree/Graphic modes. */
    private static final class EditorTab extends Tab {
        private final OpenDocument document;
        private final EditorView view;
        private final Runnable selectionCallback;
        /** Opens the shell's type-editor tab for a named type (Graphic-view "Open in Type Editor"). */
        private final java.util.function.Consumer<String> openTypeEditorCallback;
        /** Navigates an XML-instance element to its XSD definition (Graphic/Tree "Go to Definition"). */
        private final java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode> goToDefinitionCallback;
        /** Shared shell clipboard (passed in because EditorTab is a static nested class). */
        private final org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard clipboard;
        private final javafx.scene.layout.StackPane contentStack = new javafx.scene.layout.StackPane();
        private org.fxt.freexmltoolkit.controls.shell.schema.XsdTreeView treeView;
        private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView xsdGraphView;
        private org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView jsonTreeView;
        private org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView xmlInstanceTreeView;
        private XmlGridView xmlGridView;
        private JsonGridView jsonGridView;
        /** The read-only HTML Preview (a WebView, or a text fallback without javafx.scene.web). */
        private javafx.scene.Node htmlPreviewNode;
        private java.util.function.Consumer<String> htmlPreviewUpdater;
        /** Text the preview last rendered — skips redundant {@code loadContent} calls. */
        private String lastPreviewedText;
        private org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext;
        private XsdNode currentSelection;
        private org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode currentXmlSelection;
        private org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode currentJsonSelection;
        private ViewMode viewMode = ViewMode.TEXT;
        private boolean dirtyTrackingAttached;
        /** True while the file is being (re)read; the external-change check skips the tab. */
        private boolean loading;
        /** Writes in flight ({@link #writeAsync}); the external-change check skips the tab meanwhile. */
        private int saving;
        /** The last write started for this tab: the next one waits for it, so writes land in order. */
        private CompletableFuture<?> pendingWrite = CompletableFuture.completedFuture(null);
        /** Editor text the current {@link #editorContext} was parsed from (P2: avoid needless re-parse). */
        private String lastParsedText;
        /**
         * Counts the editor's text changes. The {@code last*Revision} fields record which revision a model
         * was parsed from, so "is the model current?" is a number comparison instead of building and
         * comparing the whole document text on every caret move.
         */
        private long textRevision;
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
        private boolean remoteImportsMissing;
        /** Host callback for remote imports that became available after a background download. */
        private Runnable remoteImportsCallback;
        /** Host callback after a background parse installed a fresh model (FX thread). */
        private Runnable modelInstalledCallback = () -> { };
        /** Host callback after a view switch that waited for a background parse has been applied. */
        private Runnable viewModeAppliedCallback = () -> { };
        /** Shared XML-instance model+command context across Text/Tree/Grid (mirrors {@link #editorContext}). */
        private org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext xmlEditorContext;
        /** Shared JSON model+command context across Text/Tree/Grid (mirrors {@link #xmlEditorContext}). */
        private org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext jsonEditorContext;
        /** Parser message of the last failed JSON parse (shown by the grid placeholder). */
        private String lastJsonParseError;
        /** Coalesces bursts of model-change events into a single round-trip (P1). */
        private final javafx.animation.PauseTransition roundTripDebounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
        /** Coalesces caret movement into a single Text-view caret&rarr;node resolution. */
        private final javafx.animation.PauseTransition xmlCaretDebounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        /** Read on validation worker threads (schema reconcile) — hence volatile. */
        private volatile File schemaFile;
        /** V2 model of {@link #schemaFile} for an XML tab (read-only cache; edits go to the XSD's tab). */
        private org.fxt.freexmltoolkit.controls.v2.model.XsdSchema boundSchemaModel;
        /** Runs once after the async file load has put the text into the editor (background tabs). */
        private Runnable afterLoad;
        /** How {@link #schemaFile} was bound; MANUAL bindings survive auto-redetection. */
        private volatile SchemaRebindPolicy.SchemaBindingOrigin schemaOrigin =
                SchemaRebindPolicy.SchemaBindingOrigin.NONE;
        /**
         * Raw declared location the last auto-detection saw ({@code null} = none declared).
         * Recorded on failures too, so an unresolvable URL is not re-attempted on every
         * validation/debounce cycle — only when the declared value actually changes.
         */
        private volatile String lastDetectedSchemaLocation;
        /** Written on the loader thread, read on the FX thread (tab switch) — hence volatile. */
        private volatile SchemaStatus schemaStatus = SchemaStatus.NONE;
        /** How {@link #schemaFile} was resolved (declared/catalog/library/manual); status-bar detail. */
        private volatile SchemaSource schemaSource = SchemaSource.NONE;
        /** Tooltip detail for {@link #schemaSource} — catalog target, or matched namespace/root element. */
        private volatile String schemaSourceDetail;
        private File schematronFile;
        /** Composes the debugger's and the quick-fix feature's gutter contributions. */
        private final CompositeGutterFactory gutter = new CompositeGutterFactory();
        /**
         * Guards against CONCURRENT schema detections for this tab (open-time
         * detection vs. the Validation panel's redetect): the schema pipeline
         * (Xerces deferred DOM in XsdDocumentationService) is not thread-safe.
         */
        private final java.util.concurrent.atomic.AtomicBoolean schemaDetecting =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        /**
         * Set once the tab has been closed. Background schema work polls it so a detection that
         * is already running stops before its next stage and never applies its result to a tab
         * nobody can see - which would otherwise go on to parse the whole schema
         * ({@link #loadXmlSchemaProviderAsync}) for a discarded document.
         */
        private final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        /**
         * Generation of the schema binding, bumped on the FX thread by every entry point
         * that establishes newer truth (file open, redetect, manual bind, validation
         * reconcile). A queued validation task whose buffer snapshot has been superseded
         * observes a newer generation and must not rebind/clear — without this, a stale
         * task could revert a binding that a fresher detection just established.
         */
        private final java.util.concurrent.atomic.AtomicLong schemaBindingGen =
                new java.util.concurrent.atomic.AtomicLong();
        /**
         * Counts explicit binds ({@link #bindSchemaToActiveDocument}). Unlike
         * {@link #schemaBindingGen}, validation reconciles do not bump it, so a bind whose schema
         * is still parsing is only superseded by a newer bind, never by a validation run.
         */
        private final java.util.concurrent.atomic.AtomicLong schemaBindRequest =
                new java.util.concurrent.atomic.AtomicLong();
        /** XSD-derived, read-only schema info for XML-instance nodes (lazily built off-thread). */
        private org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider xmlSchemaProvider;
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
                default -> showOnly(view.getNode());
            }
        }

        /** (Re)parses the model only when missing or when the text changed since the last parse (P2). */
        private void ensureModelParsed() {
            if ((editorContext == null || lastParsedRevision != textRevision)
                    && failedXsdParseRevision != textRevision) { // a known failure is not parsed again
                parseModel();
            }
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
                // A new context means a new schema: ensureCanvasGraphic() rebuilds the
                // Graphic view (it compares getEditorContext() and discards the stale one).
            } catch (Exception e) {
                editorContext = null;
                lastParsedText = null;
                lastParsedRevision = -1;
                failedXsdParseRevision = textRevision; // the same text fails again: do not re-parse it
                remoteImportsMissing = false;
            }
        }

        /**
         * Re-parses the model once remote imports skipped by {@link #parseModel()} are in the
         * schema cache. Skipped when the text changed meanwhile (the next parse picks the cache
         * up anyway) or when there is undo history a re-parse would discard.
         */
        private void onRemoteImportsAvailable(String parsedText) {
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
                    parsed = null;
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
        private void resolveCaretXmlNode() {
            if (viewMode != ViewMode.TEXT || document.getFileType() == EditorFileType.XSD
                    || document.getFileType() == EditorFileType.JSON) {
                return;
            }
            // Both parses (shared model, caret map) run off the FX thread; the mapping below is cheap.
            ensureXmlModelParsedAsync(() -> ensureCaretMapAsync(this::applyCaretXmlNode));
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
        private void resolveCaretXsdNode() {
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
            if (viewMode != ViewMode.TREE || treeView == null) {
                return;
            }
            if (editorContext != null) {
                treeView.setSchema(editorContext.getSchema());
            } else {
                treeView.setXsdFromText(view.getText(), document.getPath());
            }
        }

        private boolean executeAndApply(
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
            Boolean replace = org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.resolve(ruleWindow(), "Add " + capitalize(kind),
                    org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.addCompositor(element, kind));
            if (replace == null) {
                return false;
            }
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
            Boolean replace = org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.resolve(ruleWindow(), "Change Type", org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules.changeType(node, type));
            if (replace == null) {
                return false;
            }
            return executeAndApply(replace
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
                if (java.util.Objects.equals(attribute.getFixed(),
                        org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFixedValueCommand.normalize(fixed))) {
                    return true;
                }
                return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFixedValueCommand(
                        editorContext, node, fixed));
            }
            if (!(node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement)) {
                return false;
            }
            return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeConstraintsCommand(
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
        private void scheduleRoundTrip() {
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
            if (xsdGraphView != null) {
                contentStack.getChildren().remove(xsdGraphView);
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
    }
}
