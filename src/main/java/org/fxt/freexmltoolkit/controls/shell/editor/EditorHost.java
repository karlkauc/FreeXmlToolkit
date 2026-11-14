package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
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
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.telemetry.DocKind;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The file-type-aware center of the Unified shell: a tab pane of open documents.
 * Each tab embeds an {@link EditorView} chosen by file type (XML family vs JSON),
 * reusing the existing editor widgets. File reads run on the shared executor so
 * the UI thread is never blocked; the active document, caret and schema are
 * observable for the Explorer panel, inspector and status bar.
 * <p>
 * The host keeps the public API; cohesive parts live in package-private collaborators:
 * {@link EditorTab} (one document tab and its views), {@link EditorDebugSupport} (debugger
 * wiring), {@link EditorSchemaBinding} (schema binding and auto-detection),
 * {@link EditorSchemaLookup} (Inspector schema facts, Go to Definition) and
 * {@link EditorFileSync} (reading, saving, external changes).
 */
public class EditorHost extends BorderPane {

    static final Logger logger = LogManager.getLogger(EditorHost.class);
    final TabPane tabPane = new TabPane();
    /** Tab pane plus an overlaid view-mode switch pinned to the top-right of the tab header. */
    private final javafx.scene.layout.StackPane tabArea = new javafx.scene.layout.StackPane();
    /** The Text/Tree/Graphic view-mode switch; placed in the editor toolbar by the shell. */
    private javafx.scene.layout.Region viewSwitch;
    private final ObservableList<OpenDocument> openDocuments = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper activeCaret = new ReadOnlyIntegerWrapper(this, "activeCaret", 0);
    private final ReadOnlyIntegerWrapper schemaImportsRevision =
            new ReadOnlyIntegerWrapper(this, "schemaImportsRevision", 0);
    final ReadOnlyIntegerWrapper xmlSchemaProviderRevision =
            new ReadOnlyIntegerWrapper(this, "xmlSchemaProviderRevision", 0);
    private final ReadOnlyIntegerWrapper activeModelRevision =
            new ReadOnlyIntegerWrapper(this, "activeModelRevision", 0);
    final ReadOnlyObjectWrapper<File> activeSchema = new ReadOnlyObjectWrapper<>(this, "activeSchema", null);
    private final ReadOnlyObjectWrapper<SchemaStatus> activeSchemaStatus =
            new ReadOnlyObjectWrapper<>(this, "activeSchemaStatus", SchemaStatus.NONE);
    private final ReadOnlyObjectWrapper<SchemaSource> activeSchemaSource =
            new ReadOnlyObjectWrapper<>(this, "activeSchemaSource", SchemaSource.NONE);
    private final javafx.beans.property.ReadOnlyStringWrapper activeSchemaSourceDetail =
            new javafx.beans.property.ReadOnlyStringWrapper(this, "activeSchemaSourceDetail", null);
    final ReadOnlyObjectWrapper<ViewMode> activeViewMode =
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
    public enum SchemaSource { NONE, DECLARED, LIBRARY, CATALOG, PROJECT, MANUAL }

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
    java.util.function.BiConsumer<OpenDocument, String> documentLoadedHandler = (doc, content) -> { };

    /** Optional override for "new document" requests (e.g. the shell's guided New File dialog). */
    private Runnable newDocumentOverride;

    /** Reading, saving and watching the files behind the tabs. */
    final EditorFileSync fileSync = new EditorFileSync(this);

    private final EditorWelcomePane welcomePane = new EditorWelcomePane(
            this::requestNewDocument, this::openFileChooser, this::openFile,
            this::clearRecentFiles, this::fireWelcomeAction);

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
        fileSync.externalChangeMonitor.attachTo(this);
        // Tabs can be dragged left/right; the skin moves a tab by removing and re-adding it.
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        // Show the welcome empty-state while no document is open; swap to the tab
        // pane as soon as one opens, and back again when the last tab closes.
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> {
            // Abandon a removed tab's background schema work here rather than in setOnClosed:
            // this fires however the tab goes away, including programmatic removal. A tab that is
            // still in the list was only moved (drag reorder) and keeps working.
            while (c.next()) {
                for (Tab removed : c.getRemoved()) {
                    if (removed instanceof EditorTab et && !tabPane.getTabs().contains(et)) {
                        et.closed.set(true);
                    }
                }
                for (Tab added : c.getAddedSubList()) {
                    if (added.getContextMenu() == null) {
                        EditorTabMenu.install(added, tabMenuActions);
                    }
                    if (added instanceof EditorTab et && et.view instanceof JsonEditorView json) {
                        json.setContextActions(jsonTextActions);
                    }
                }
            }
            syncOpenDocumentOrder();
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
        return tab instanceof EditorTab et ? Optional.of(et.text()) : Optional.empty();
    }

    /**
     * @return the active document's length in characters without building its text (O(1); for the
     *         status bar, which updates on every caret move), or -1 if no editor tab is active
     */
    public int getActiveTextLength() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.view.getCodeArea().getLength() : -1;
    }

    /**
     * @return the active editor's text as shown, without writing deferred grid edits into it first
     *         (caret-based readers: a caret offset refers to this text), or empty if no tab is open
     */
    public Optional<String> getActiveViewText() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? Optional.of(et.view.getText()) : Optional.empty();
    }

    /**
     * Completes (on the FX thread) once the open document {@code queryDoc} runs against holds every
     * deferred grid edit in its text: written back off the FX thread, at once when nothing waits
     * or the target is a file.
     */
    public java.util.concurrent.CompletableFuture<Void> whenQueryTargetCurrent(OpenDocument queryDoc) {
        OpenDocument targetDoc = getQueryTarget(queryDoc) instanceof QueryTarget.OpenDoc od
                ? od.document() : mostRecentXmlFamilyDocument(queryDoc).orElse(null);
        return whenDocumentTextCurrent(targetDoc);
    }

    /**
     * Completes (on the FX thread) once the open {@code document}'s text holds every deferred grid
     * edit (written back off the FX thread); at once when nothing waits or it is not open.
     */
    public java.util.concurrent.CompletableFuture<Void> whenDocumentTextCurrent(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (document != null && tab instanceof EditorTab editorTab && editorTab.document == document) {
                return editorTab.xmlTextCurrentAsync();
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    /** @return the text of the given (possibly non-active) open document, or empty if closed. */
    public Optional<String> getDocumentText(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document == document) {
                return Optional.of(editorTab.text());
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
        // The Graphic (grid) view of the active document marks the rows with problems too
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab active) {
            if (active.xmlGridView != null) {
                active.xmlGridView.setProblems(problems);
            }
            active.view.setProblems(problems);
            if (active.jsonGridView != null) {
                active.jsonGridView.setProblems(problems, active.text());
            }
        }
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

    /**
     * Replaces the whole text of an open document (saved or not) with {@code text} — one native undo entry in its
     * editor; the document need not be the active one.
     *
     * @return {@code true} when an open editor tab for {@code document} accepted the edit
     */
    public boolean replaceDocumentText(OpenDocument document, String text) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                et.view.replaceTextRegion(0, et.text().length(), text);
                return true;
            }
        }
        return false;
    }

    /** @return the buffer text of the open document at {@code path}, or empty if not open. */
    public Optional<String> getOpenDocumentText(java.nio.file.Path path) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                return Optional.of(et.text());
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

    /** @return a key of the active document's content (changes with every edit), or {@code ""} */
    public String activeRevisionKey() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.revisionKey() : "";
    }

    /**
     * Shows a query hit in the active document's current view (Text, Tree or Graphic), every hit
     * highlighted, without switching the view.
     */
    public void revealQueryHits(java.util.List<org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch> hits,
                                int current) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            et.revealQueryHits(hits, current);
        }
    }

    /** Removes query-hit highlights from every open document. */
    public void clearQueryHits() {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et) {
                et.clearQueryHits();
            }
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
            case SAVE -> fileSync.write(dirty, file);
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
            if ((et.viewMode == ViewMode.GRAPHIC || et.viewMode == ViewMode.OVERVIEW) && et.editorContext != null
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
                if (!et.isModelCurrent()) {
                    // Off the FX thread: the parse downloads remote imports. The XSD tab is not the
                    // selected one, so its own model callback does not bump activeModelRevision.
                    et.ensureModelParsedAsync(() -> activeModelRevision.set(activeModelRevision.get() + 1));
                }
                if (et.editorContext != null) {
                    schema = et.editorContext.getSchema();
                }
            }
        }
        return schema;
    }

    void refreshSelectedNode() {
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
        if (EditorFileType.fromFileName(path.getFileName() == null ? null : path.getFileName().toString())
                == EditorFileType.OTHER) {
            // An unknown extension: the first bytes decide (XML in a .config / .xaml / .gpx file opens
            // as XML). Read off the FX thread - the file may be on a slow network drive.
            if (sniffing.add(path)) {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                    EditorFileType type = EditorFileType.sniffFile(path);
                    Platform.runLater(() -> {
                        sniffing.remove(path);
                        openFile(path, select, type);
                    });
                });
            }
            return;
        }
        openFile(path, select, null);
    }

    /** Files of unknown extension whose first bytes are being read before their tab opens. */
    private final java.util.Set<Path> sniffing = new java.util.HashSet<>();

    private void openFile(Path path, boolean select, EditorFileType contentType) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                if (select) {
                    tabPane.getSelectionModel().select(tab);
                }
                return;
            }
        }
        rememberRecentFile(path); // before addTab: open-document listeners read the list
        OpenDocument document = contentType == null ? OpenDocument.forPath(path) : OpenDocument.forPath(path, contentType);
        EditorTab tab = new EditorTab(document, this::refreshSelectedNode, nodeClipboard,
                this::openNamedTypeEditor, schemaLookup::goToXmlDefinition);
        addTab(tab, select);
        // HTML files open rendered; the preview fills once the async load lands.
        if (tab.document.getFileType() == EditorFileType.HTML) {
            tab.setViewMode(ViewMode.PREVIEW);
            if (select) {
                activeViewMode.set(tab.viewMode); // addTab just selected this tab
            }
        }
        debugSupport.installInputNodeBreakpointMenu(tab);
        // Wire "Go to Definition": open the bound XSD and reveal the element (no-op for non-XML views).
        tab.view.setGoToDefinitionHandler(schemaLookup::goToDefinition);
        fileSync.loadAsync(tab, path);
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
     * Opens a file in the Large File Viewer (any size; read from disk on demand), or focuses the
     * viewer already showing it. The viewer releases the file when its tab closes; a window with edits
     * that are not written yet asks first.
     */
    public LargeFileView openLargeFileViewer(Path path) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getContent() instanceof LargeFileView view && view.path().equals(path)) {
                tabPane.getSelectionModel().select(tab);
                return view;
            }
        }
        LargeFileView view = new LargeFileView(path);
        view.setDocumentOpener((name, text) -> openGeneratedDocument(text, EditorFileType.XML, name));
        Tab tab = openToolTab(path.getFileName() + " (viewer)", "bi-eye", view);
        tab.setTooltip(new javafx.scene.control.Tooltip(path + " - Large File Viewer"));
        tab.setOnCloseRequest(e -> {
            if (!view.confirmClose()) {
                e.consume();
            }
        });
        view.setCloser(() -> {
            if (tabPane.getTabs().remove(tab)) {
                view.close();
            }
        });
        tab.setOnClosed(e -> view.close());
        UsageEvents.command("large_file_viewer", UsageEvents.VIA_TOOLBAR);
        return view;
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
    /** @return the open tabs that show no document (tool tabs: reports, views, diffs) */
    public java.util.List<Tab> getToolTabs() {
        return tabPane.getTabs().stream().filter(t -> !(t instanceof EditorTab)).toList();
    }

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

    /** Open type-editor tabs, keyed by schema document and type name: one tab per type. */
    private final java.util.Map<TypeTabKey, Tab> openTypeTabs = new java.util.HashMap<>();

    /** A type of one schema document (equal type names of two schemas are different types). */
    private record TypeTabKey(EditorTab source, String typeName) {
    }

    /** Shell-wide clipboard for copy/cut/paste of XSD nodes across the structured views. */
    final org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard nodeClipboard =
            new org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard();

    /**
     * Opens the type-editor tab for a named type, triggered from a Graphic-view context-menu
     * action ("Open in Type Editor"). Anonymous inline types have no name and therefore no tab
     * target, so the request is ignored. Deferred to the FX pulse so the originating context menu
     * has fully closed first.
     */
    void openNamedTypeEditor(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        javafx.application.Platform.runLater(() -> openTypeEditorTab(typeName));
    }

    /**
     * Opens a named XSD type in a dedicated editor tab: a 5-panel form for a simple type, a
     * focused graphical editor for a complex type. Both are backed by the active document's live
     * {@link org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext}; edits round-trip to the
     * XSD text (the simple-type form edits the shared model directly; the complex-type editor
     * merges its changes back on Save). Re-opening a type re-selects its existing tab.
     *
     * @return the opened (or re-selected) tab, or {@code null} if no such named type exists
     */
    public Tab openTypeEditorTab(String typeName) {
        if (typeName == null) {
            return null;
        }
        // A type tab in front: its type belongs to that tab's schema document
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        EditorTab et = openTypeTabs.entrySet().stream()
                .filter(e -> e.getValue() == selected)
                .map(e -> e.getKey().source())
                .filter(source -> tabPane.getTabs().contains(source))
                .findFirst().orElseGet(this::resolveStructuredEditorTab);
        return et == null ? null : openTypeEditorTab(et, typeName);
    }

    /**
     * Opens a named type of the given schema document in its type-editor tab (or re-selects it).
     * Types used inside a complex type's editor open through here with that editor's document, so
     * the lookup stays in the right schema when several are open.
     */
    Tab openTypeEditorTab(EditorTab et, String typeName) {
        if (et == null || typeName == null) {
            return null;
        }
        TypeTabKey key = new TypeTabKey(et, typeName);
        Tab existing = openTypeTabs.get(key);
        if (existing != null && tabPane.getTabs().contains(existing)) {
            tabPane.getSelectionModel().select(existing);
            return existing;
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
            view.setOnFindUsageCallback(() -> schemaLookup.revealTypeByName(typeName));
            tab = openToolTab(I18n.t("EditorHost.type", typeName), "bi-braces-asterisk", view);
            Tab created = tab;
            view.setOnCloseCallback(() -> closeTypeTab(created));
        } else if (type instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
            // Name, abstract and mixed are commands of the schema document (its undo stack)
            var view = new org.fxt.freexmltoolkit.controls.v2.editor.views.ComplexTypeEditorView(
                    complexType, ctx.getSchema(), ctx);
            tab = openToolTab(I18n.t("EditorHost.type", typeName), "bi-diagram-3", buildComplexTypeEditor(view, et));
            Tab created = tab;
            view.setOnCloseCallback(() -> closeTypeTab(created));
            // Types used inside this type open in their own tabs (deferred: the context menu
            // that asked must close first)
            view.setOpenTypeEditorCallback(name ->
                    javafx.application.Platform.runLater(() -> openTypeEditorTab(et, name)));
            created.addEventHandler(Tab.CLOSED_EVENT, e -> view.dispose());
        } else {
            return null;
        }
        // Round-trip edits made on the shared model for this tab's lifetime, independent of the
        // XSD tab's current view mode (the standing graphic listener only fires in GRAPHIC mode).
        java.beans.PropertyChangeListener roundTrip = evt -> et.scheduleRoundTrip();
        ctx.getSchema().addPropertyChangeListener(roundTrip);
        tab.setOnClosed(e -> {
            ctx.getSchema().removePropertyChangeListener(roundTrip);
            openTypeTabs.remove(key);
        });
        openTypeTabs.put(key, tab);
        return tab;
    }

    /** The XSD source tab for type editing: the active one if structured, else the first such tab. */
    EditorTab resolveStructuredEditorTab() {
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
    /**
     * Closes a type editor tab the way its × does: the tab's close handlers run (they detach the
     * round-trip listener and forget the tab), which {@code getTabs().remove} alone skips.
     */
    private void closeTypeTab(Tab tab) {
        if (tabPane.getTabs().remove(tab)) {
            javafx.event.Event.fireEvent(tab, new javafx.event.Event(Tab.CLOSED_EVENT));
        }
    }

    private javafx.scene.layout.Region buildComplexTypeEditor(
            org.fxt.freexmltoolkit.controls.v2.editor.views.ComplexTypeEditorView view, EditorTab et) {
        // Slim the embedded graph to the shell's minimal chrome (breadcrumb + zoom pill) — the
        // embedded properties panel stays (it edits the type's nodes; the shell inspector is for
        // the document, not this tool tab).
        if (view.getGraphView() != null) {
            view.getGraphView().useMinimalChrome();
        }
        javafx.scene.control.Button save = new javafx.scene.control.Button(I18n.t("EditorHost.save-to-schema"),
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
        chooser.setTitle(I18n.t("EditorHost.open-file"));
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
        String leftText = left.text();
        if (refuseTooLargeToCompare(leftText.length(), rightFile.length())) {
            return CompletableFuture.completedFuture(null);
        }
        java.util.function.Consumer<String> leftSave = newText -> {
            left.view.setText(newText);
            Path path = left.document.getPath();
            if (path != null) {
                // Through write() so the disk stamp follows; a failure surfaces via the document
                // staying dirty (the diff view reports save errors itself).
                fileSync.write(left, path);
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
     * Opens a scenario check's expected result (left; saving it updates the expected file) next to the
     * actual result (right, a temporary file), compared as XML with the scenario's options.
     *
     * @return completes on the FX thread with the diff tab, or with {@code null} when refused
     */
    public CompletableFuture<org.fxt.freexmltoolkit.controls.diff.DiffView> openScenarioDiff(
            Path expected, String actual, String actualName,
            org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options options) {
        String expectedText;
        File actualFile;
        try {
            expectedText = java.nio.file.Files.readString(expected, java.nio.charset.StandardCharsets.UTF_8);
            String suffix = actualName.contains(".") ? actualName.substring(actualName.lastIndexOf('.')) : ".xml";
            actualFile = java.nio.file.Files.createTempFile("fxt-actual-", suffix).toFile();
            actualFile.deleteOnExit();
            java.nio.file.Files.writeString(actualFile.toPath(), actual == null ? "" : actual,
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorHost.compare"),
                    "The expected and the actual result could not be opened.",
                    org.fxt.freexmltoolkit.util.DialogHelper.Remedies.FILE_UNREADABLE, e);
            return CompletableFuture.completedFuture(null);
        }
        if (refuseTooLargeToCompare(expectedText.length(), actualFile.length())) {
            return CompletableFuture.completedFuture(null);
        }
        java.util.function.Consumer<String> saveExpected = text -> {
            try {
                java.nio.file.Files.writeString(expected, text, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        return org.fxt.freexmltoolkit.controls.diff.DiffView.open(expected.getFileName().toString(), expectedText,
                        saveExpected, actualFile)
                .thenApply(diff -> {
                    tabPane.getTabs().add(diff);
                    tabPane.getSelectionModel().select(diff);
                    diff.compareAsXml(options);
                    return diff;
                });
    }

    /** Shows why two documents are not compared when they are too large; @return whether refused */
    private static boolean refuseTooLargeToCompare(long leftChars, long rightChars) {
        if (!org.fxt.freexmltoolkit.controls.diff.DiffView.tooLargeToCompare(leftChars, rightChars)) {
            return false;
        }
        org.fxt.freexmltoolkit.util.DialogHelper.showWarning(I18n.t("EditorHost.compare"), I18n.t("EditorHost.files-too-large-to-compare"),
                org.fxt.freexmltoolkit.controls.diff.DiffView.tooLargeMessage(leftChars, rightChars));
        return true;
    }

    /**
     * Opens a side-by-side diff of two files (Explorer "Compare files…" with two selected files).
     * When {@code leftFile} is open in an editor tab, that tab is the left side (unsaved edits
     * included, saving goes through the editor); otherwise both sides are read from disk.
     *
     * @return completes on the FX thread with the created diff tab
     */
    public CompletableFuture<org.fxt.freexmltoolkit.controls.diff.DiffView> openDiffOfFiles(File leftFile, File rightFile) {
        Path leftPath = leftFile.toPath();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && leftPath.equals(et.document.getPath()) && !et.loading) {
                tabPane.getSelectionModel().select(tab);
                return openDiffWithFile(rightFile);
            }
        }
        if (refuseTooLargeToCompare(leftFile.length(), rightFile.length())) {
            return CompletableFuture.completedFuture(null);
        }
        return org.fxt.freexmltoolkit.controls.diff.DiffView.openFiles(leftFile, rightFile)
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
                org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.pdf_preview");
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
            java.util.List<Path> projects = org.fxt.freexmltoolkit.service.project.ProjectService.getInstance().recent();
            Platform.runLater(() -> {
                if (request != welcomeRequest || !tabPane.getTabs().isEmpty()) {
                    return;
                }
                welcomePane.setRecentProjects(projects, ProjectActions::openProject);
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
                if (mode == ViewMode.PREVIEW || mode == ViewMode.OVERVIEW) {
                    // Preview exists for HTML only, Overview for XSD only: hide the segment instead
                    // of showing it permanently disabled for every other document.
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
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "editor.recent.clear"); // absent registry in tests is not reported by the no-op service
            // service not available (e.g. isolated tests): just clear the displayed list
        }
        welcomePane.setRecentFiles(java.util.List.of());
        welcomePane.setStats(welcomeStats());
    }

    /** Prompts for a URL and opens its fetched content as a new document (Welcome "From URL"). */
    public void openFromUrl() {
        javafx.scene.control.TextInputDialog dialog = org.fxt.freexmltoolkit.util.DialogHelper.createTextInputDialog("");
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
            } catch (org.fxt.freexmltoolkit.service.ConnectionService.FetchFailedException
                     | IllegalArgumentException e) {
                // An unreachable URL / an error page / a malformed URL: the user's input or the network,
                // shown with its reason but not reported as an application error
                Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showActionError(
                        I18n.t("EditorHost.open-from-url"), I18n.t("EditorHost.could-not-fetch", url),
                        org.fxt.freexmltoolkit.util.DialogHelper.Remedies.URL_FETCH, e.getMessage(), false));
            } catch (Exception e) {
                Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showActionError(
                        I18n.t("EditorHost.open-from-url"), I18n.t("EditorHost.could-not-fetch", url),
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
                nodeClipboard, this::openNamedTypeEditor, schemaLookup::goToXmlDefinition);
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
        return openGeneratedDocument(content, type, displayName, usageSource, null);
    }

    /**
     * Opens a transform result in its own tab. {@code source} tells where the result came from:
     * relative schema locations in it resolve against the transform input's folder, and when the
     * result declares no reachable schema it inherits the input's schema if that declares the
     * result's root element - so the tab gets validation and schema-aware IntelliSense.
     *
     * @param source the transform's input and its schema, or {@code null}
     */
    public OpenDocument openGeneratedDocument(String content, EditorFileType type, String displayName,
                                              TransformOutputPanel.ResultSource source) {
        return openGeneratedDocument(content, type, displayName, UsageEvents.SOURCE_GENERATED, source);
    }

    private OpenDocument openGeneratedDocument(String content, EditorFileType type, String displayName,
                                               String usageSource, TransformOutputPanel.ResultSource source) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(displayName, type), this::refreshSelectedNode,
                nodeClipboard, this::openNamedTypeEditor, schemaLookup::goToXmlDefinition);
        addTab(tab);
        // character count as a cheap size proxy (no encoding on the FX thread)
        reportGeneratedOpen(type != null ? type.docKind() : null, content, usageSource);
        if (content != null && content.length() >= org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2.PREPARE_THRESHOLD_CHARS) {
            // A large result (a transform output of 100+ MB) builds its editor document off the
            // FX thread, like a large file on open: setText froze the UI for seconds.
            tab.loading = true;
            tab.beginLoading();
            try {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                    var prepared = tab.view.prepareText(content);
                    Platform.runLater(() -> showGeneratedText(tab, content, prepared, displayName, source));
                });
            } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
                showGeneratedText(tab, content, null, displayName, source);
            }
            return tab.document;
        }
        showGeneratedText(tab, content, null, displayName, source);
        return tab.document;
    }

    /** Shows a generated text in its tab (prepared off the FX thread when large) and detects its schema. */
    private void showGeneratedText(EditorTab tab, String content, org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2.PreparedText prepared,
                                   String displayName, TransformOutputPanel.ResultSource source) {
        if (tab.closed.get()) {
            return;
        }
        if (prepared != null) {
            tab.view.setText(prepared);
        } else {
            tab.view.setText(content);
        }
        tab.endLoading();
        tab.loading = false;
        tab.attachDirtyTracking();
        tab.document.setDirty(true);
        // a generated document never runs the detection on open that a file gets
        detectGeneratedSchema(tab, displayName, source);
    }

    /** Starts the schema detection of a generated tab (stand-in location next to the transform input). */
    private void detectGeneratedSchema(EditorTab tab, String displayName, TransformOutputPanel.ResultSource source) {
        if (source != null && source.inputPath() != null && displayName != null) {
            tab.schemaBasePath = source.inputPath().resolveSibling(displayName);
        }
        schemaBinding.detectGeneratedAsync(tab, source != null ? source.inputSchema() : null);
    }

    /** @return the schema bound to the open {@code document}'s tab, or {@code null} */
    public File schemaFileOf(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document == document) {
                return editorTab.schemaFile;
            }
        }
        return null;
    }

    /** @return the schema bound to the open tab of {@code file}, or {@code null} when it is not open / unbound */
    public File schemaFileOf(File file) {
        if (file == null) {
            return null;
        }
        Path wanted = file.toPath().toAbsolutePath().normalize();
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document.getPath() != null
                    && editorTab.document.getPath().toAbsolutePath().normalize().equals(wanted)) {
                return editorTab.schemaFile;
            }
        }
        return null;
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
            out.showError(I18n.t("EditorHost.select-an-xslt-stylesheet-first"));
            return;
        }
        if (xmlFile == null || !xmlFile.isFile()) {
            out.showError(I18n.t("EditorHost.select-an-xml-file-to"));
            return;
        }
        rememberRecentXslt(xsltFile);
        out.showPending(I18n.t("EditorHost.transforming"));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XSLT,
                    xsltFile.getName() + " (preview)");
            String result;
            long inputChars = -1;
            TransformOutputPanel.ResultSource previewSource = null;
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format =
                    org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML;
            org.fxt.freexmltoolkit.service.XsltTransformationResult fullResult = null;
            try {
                String xml = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
                previewSource = TransformPanel.resultSource(xmlFile.toPath(), null, xml);
                inputChars = xml.length();
                String xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
                format = TransformRunner.detectXsltOutputFormat(xsltContent);
                fullResult = TransformRunner.xsltTransformResult(xml, xmlFile.toURI().toString(),
                        xsltContent, xsltFile.toURI().toString(), java.util.Map.of(), format,
                        org.fxt.freexmltoolkit.service.standards.StandardsSettings.current().forTool(
                                org.fxt.freexmltoolkit.service.standards.StandardsSettings.TOOL_TRANSFORM, null).xslt());
                result = fullResult.isSuccess()
                        ? fullResult.getOutputContent()
                        : "ERROR: " + fullResult.getErrorMessage();
                if (fullResult.isSuccess()) {
                    probe.phase("Compile", fullResult.getCompilationTime());
                    probe.phase("Transform", fullResult.getTransformationTime());
                }
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
                fullResult = org.fxt.freexmltoolkit.service.XsltTransformationResult.error(e.getMessage(), e);
            }
            boolean ok = !result.startsWith("ERROR");
            UsageEvents.xsltTransformed(1, t0, false, fullResult != null ? fullResult.getErrorPhase() : null,
                    TransformRunner.failureOf(fullResult), inputChars);
            long elapsedMs = probe.finish(inputChars, ok ? result.length() : -1, ok,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            String finalResult = result;
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat finalFormat = format;
            TransformOutputPanel.ResultSource finalSource = previewSource;
            Platform.runLater(() -> out.showTransformResult(finalResult, finalFormat, elapsedMs, finalSource));
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
            out.showError(I18n.t("EditorHost.select-an-xslt-stylesheet-first"));
            return;
        }
        if (xmlFiles == null || xmlFiles.isEmpty()) {
            out.showError(I18n.t("EditorHost.select-xml-files-to-transform"));
            return;
        }
        rememberRecentXslt(xsltFile);
        String xsltContent;
        org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format;
        try {
            xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
            format = TransformRunner.detectXsltOutputFormat(xsltContent);
        } catch (IOException e) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportError(e, "editor.batch_stylesheet");
            out.showError(I18n.t("EditorHost.could-not-read-stylesheet", e.getMessage()));
            return;
        }
        var view = new org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView(
                org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView.Kind.XSLT,
                xsltContent, xsltFile.toURI().toString(), java.util.Map.of(), format);
        view.addInputFiles(xmlFiles);
        openToolTab(I18n.t("EditorHost.batch"), "bi-collection", view);
        view.run();
    }

    /** Records {@code path} in the recent-files list (File → Recent, Explorer, Welcome; best-effort). */
    static void rememberRecentFile(Path path) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .addLastOpenFile(path.toFile());
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "editor.recent.add"); // absent registry in tests is not reported by the no-op service
            // properties service unavailable — no recent-files persistence
        }
    }

    /** Records {@code xsltFile} in the shared recent-stylesheet store (best-effort). */
    private static void rememberRecentXslt(File xsltFile) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .addRecentXsltFile(xsltFile);
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "editor.recent.add"); // absent registry in tests is not reported by the no-op service
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
        return updateGeneratedDocument(document, content, null);
    }

    /**
     * As {@link #updateGeneratedDocument(OpenDocument, String)} for a transform result; a tab without
     * a schema yet runs the detection again (see
     * {@link #openGeneratedDocument(String, EditorFileType, String, TransformOutputPanel.ResultSource)}).
     * A bound schema is kept - the validation-time reconcile follows changed declarations.
     */
    public boolean updateGeneratedDocument(OpenDocument document, String content,
                                           TransformOutputPanel.ResultSource source) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document == document) {
                editorTab.view.setText(content);
                editorTab.refreshPreviewIfActive();
                if (source != null && editorTab.schemaFile == null && editorTab.document.getPath() == null) {
                    detectGeneratedSchema(editorTab, editorTab.document.getDisplayName(), source);
                }
                return true;
            }
        }
        return false;
    }

    // ----- saving / editing ------------------------------------------------

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
        return editActiveAddingContent(et -> et.addElement(et.currentSelection, name));
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
        return editActiveAddingContent(et -> et.addContainerElement(et.currentSelection, name));
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
        return editActiveAddingContent(et -> et.addAttribute(et.currentSelection, name));
    }

    /** Adds a sequence compositor to the selected element via the command stack. */
    public boolean addSequenceToActive() {
        return editActiveAddingContent(et -> et.addSequence(et.currentSelection));
    }

    /** Adds a choice compositor to the selected element via the command stack. */
    public boolean addChoiceToActive() {
        return editActiveAddingContent(et -> et.addChoice(et.currentSelection));
    }

    /** Adds an {@code <!-- … -->} comment as a child of the selected XSD node via the command stack. */
    public boolean addCommentToActive(String content) {
        return editActiveAddingContent(et -> et.addComment(et.currentSelection, content));
    }

    /** Adds an all compositor to the selected element via the command stack. */
    public boolean addAllToActive() {
        return editActiveAddingContent(et -> et.addAll(et.currentSelection));
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
                && et.currentSelection != null && !refuseLockedSelection(et)) {
            nodeClipboard.cut(et.currentSelection);
            return true;
        }
        return false;
    }

    /** Pastes the clipboard's node under the selected node via the command stack. */
    public boolean pasteIntoActive() {
        return editActiveAddingContent(et -> et.pasteNode(et.currentSelection));
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
            if (refuseLockedSelection(et)) {
                return false;
            }
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
     * Like {@link #editActive} for edits that add content under the selected node: refused when the
     * Graphic view shows that content as a named type's frame (it would go into the type).
     */
    private boolean editActiveAddingContent(java.util.function.Predicate<EditorTab> edit) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            var frame = et.selectionContentTypeFrame();
            if (frame != null) {
                String label = et.currentSelection != null ? et.currentSelection.getName() : null;
                org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.showBlocked(
                        getScene() == null ? null : getScene().getWindow(), "Add",
                        org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck.blocked(
                                frame.contentLockReason(label)));
                return false;
            }
        }
        return editActive(edit);
    }

    /**
     * Refuses an edit of a node the Graphic view shows inside a type frame: it belongs to a named
     * type and is changed in that type's editor. Explains why.
     *
     * @return true when the edit must not run
     */
    private boolean refuseLockedSelection(EditorTab et) {
        var frame = et.selectionTypeFrame();
        if (frame == null) {
            return false;
        }
        String label = et.currentSelection != null ? et.currentSelection.getName() : null;
        org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs.showBlocked(
                getScene() == null ? null : getScene().getWindow(), "Edit",
                org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck.blocked(frame.lockReason(label)));
        return true;
    }

    /**
     * The named type the active document's selection belongs to when the Graphic view shows it in a
     * type frame (read-only in place).
     *
     * @return the frame, or empty when the selection can be edited here
     */
    public Optional<org.fxt.freexmltoolkit.controls.v2.view.TypeFrame> activeSelectionTypeFrame() {
        return tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                ? Optional.ofNullable(et.selectionTypeFrame()) : Optional.empty();
    }

    /**
     * Like {@link #editActive} but keeps the same node selected after the edit. Property
     * edits (rename, type, cardinality, use, …) preserve the model node, so the inspector
     * should keep showing it instead of clearing (which {@code applyModelChange} would cause).
     */
    private boolean editActivePreservingSelection(java.util.function.Predicate<EditorTab> edit) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            if (refuseLockedSelection(et)) {
                return false;
            }
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
        int jsonIndent = org.fxt.freexmltoolkit.service.JsonService.configuredIndent();
        // JSON: comments, JSON5 spelling and trailing commas survive (only whitespace changes)
        return transformActiveText(et, "format", text -> json
                ? org.fxt.freexmltoolkit.service.json.JsonFormatter.format(text, jsonIndent)
                : org.fxt.freexmltoolkit.service.XmlService.prettyFormat(text, 2));
    }

    /**
     * @return the JSON Schema model bound to the active JSON document (completion, hover,
     *         inspector), empty when none is bound or the active document is no JSON
     */
    public java.util.Optional<org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver> getActiveJsonSchema() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.view instanceof JsonEditorView json) {
            return java.util.Optional.ofNullable(json.getSchemaResolver());
        }
        return java.util.Optional.empty();
    }

    /**
     * Generates a sample instance of a JSON Schema and opens it in a new tab: the active JSON
     * document when it is a JSON Schema, else the schema bound to it. Asks whether to fill in the
     * required properties only.
     */
    public void generateJsonSampleForActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et) || et.document.getFileType() != EditorFileType.JSON) {
            return;
        }
        String title = I18n.t("EditorHost.json-sample-title");
        String text = et.text();
        boolean isSchema = new org.fxt.freexmltoolkit.service.JsonService().isJsonSchemaDocument(text);
        var bound = getActiveJsonSchema().orElse(null);
        if (!isSchema && bound == null) {
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError(title,
                    I18n.t("EditorHost.json-sample-no-schema"), I18n.t("EditorHost.json-sample-bind"));
            return;
        }
        String requiredOnly = I18n.t("EditorHost.json-sample-required-only");
        String all = I18n.t("EditorHost.json-sample-all");
        var choice = org.fxt.freexmltoolkit.util.DialogHelper.createChoiceDialog(title,
                I18n.t("EditorHost.json-sample-header"), I18n.t("EditorHost.json-sample-content"),
                requiredOnly, java.util.List.of(requiredOnly, all)).showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        var options = new org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaSampleGenerator.Options(
                requiredOnly.equals(choice.get()), 8, 1);
        java.nio.file.Path file = et.document.getPath();
        String baseName = file != null ? file.getFileName().toString().replaceFirst("(\\.schema)?\\.json[c5]?$", "")
                : "sample";
        int indent = org.fxt.freexmltoolkit.service.JsonService.configuredIndent();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            try {
                var resolver = isSchema
                        ? new org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver(file != null
                                ? org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaDocument.load(file.toFile())
                                : org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaDocument.of(text,
                                        java.net.URI.create("mem:/schema.json")))
                        : bound;
                var sample = org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaSampleGenerator.generate(resolver, options);
                String json = org.fxt.freexmltoolkit.service.json.JsonFormatter.format(
                        new com.google.gson.GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(sample),
                        indent) + "\n";
                UsageEvents.sampleGenerated(org.fxt.freexmltoolkit.service.telemetry.DocKind.JSON, "json", 1, t0, null);
                Platform.runLater(() -> openGeneratedDocument(json, EditorFileType.JSON, baseName + "-sample.json"));
            } catch (Exception e) {
                var failure = org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e);
                UsageEvents.sampleGenerated(org.fxt.freexmltoolkit.service.telemetry.DocKind.JSON, "json", 1, t0, failure);
                Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showActionError(title,
                        I18n.t("EditorHost.json-sample-failed"), I18n.t("EditorHost.json-sample-check-schema"), e));
            }
        });
    }

    /** Runs the shell's validation of the active document (F8); set by the shell. */
    private Runnable validateHandler = () -> { };

    /**
     * Sets what "Validate" in an editor's context menu does (the shell's F8 action).
     *
     * @param handler validates the active document
     */
    public void setValidateHandler(Runnable handler) {
        this.validateHandler = handler != null ? handler : () -> { };
    }

    /** Validates the active document the way F8 does (Validation panel, problems in the editor). */
    public void runValidation() {
        validateHandler.run();
    }

    /** The JSON text editor's context-menu actions (they act on the active document). */
    private final org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonContextMenuManager.JsonContextActions
            jsonTextActions = new org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonContextMenuManager
                    .JsonContextActions() {
                @Override
                public void formatJson() {
                    formatActive();
                }

                @Override
                public void minifyJson() {
                    minifyActive();
                }

                @Override
                public void validateJson() {
                    validateHandler.run();
                }

                @Override
                public void sortKeys(boolean recursive) {
                    sortJsonKeysActive(recursive);
                }

                @Override
                public boolean canSortKeys() {
                    return true;
                }
            };

    /**
     * Sorts the object members of the active JSON document by key, off the FX thread, keeping
     * comments with their members, and formats it (one undo step).
     *
     * @param recursive whether nested objects are sorted too (else only the top-level object)
     * @return completes on the FX thread with whether the text was rewritten
     */
    public CompletableFuture<Boolean> sortJsonKeysActive(boolean recursive) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et) || et.document.getFileType() != EditorFileType.JSON) {
            return CompletableFuture.completedFuture(false);
        }
        int jsonIndent = org.fxt.freexmltoolkit.service.JsonService.configuredIndent();
        return transformActiveText(et, recursive ? "sort_keys_recursive" : "sort_keys",
                text -> org.fxt.freexmltoolkit.service.json.JsonFormatter.sortKeys(text, recursive, jsonIndent));
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
        return transformActiveText(et, "minify", text -> json
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
    private CompletableFuture<Boolean> transformActiveText(EditorTab et, String mode, TextRewrite rewrite) {
        String text = et.text();
        long revision = et.textRevision;
        var kind = et.document.getFileType().docKind();
        et.beginLoading();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String rewritten = null;
            org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure = null;
            try {
                rewritten = rewrite.apply(text);
            } catch (Exception e) {
                // invalid content (invalid_input) or a rewrite bug: the text stays untouched
                failure = org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e);
            }
            String output = rewritten;
            var outcome = failure;
            Platform.runLater(() -> {
                et.endLoading();
                boolean apply = output != null && !output.isBlank() && et.textRevision == revision
                        && !EditorTab.isAbandoned(et);
                if (apply) {
                    et.view.setText(output);
                    et.document.setDirty(true);
                    UsageEvents.formatted(kind, mode, null);
                } else if (outcome != null) {
                    UsageEvents.formatted(kind, mode, outcome);
                } else {
                    UsageEvents.formatSkipped(kind, mode);
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
            // The Overview filters its rows with its own field.
            case TEXT, PREVIEW, OVERVIEW -> null;
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

    // ----- schema binding --------------------------------------------------

    /** Schema binding, auto-detection and the XSD-derived provider of XML tabs. */
    final EditorSchemaBinding schemaBinding = new EditorSchemaBinding(this);

    /**
     * Binds a schema to the active document — an XSD (schema-aware IntelliSense +
     * validation) for the XML family, a JSON Schema (validation) for JSON documents.
     *
     * @return completes on the FX thread with {@code true} once bound (the schema is parsed off-thread)
     */
    public CompletableFuture<Boolean> setSchemaForActiveDocument(File xsd) {
        return schemaBinding.setSchemaForActiveDocument(xsd);
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
        return schemaBinding.bindGeneratedSchemaToActiveDocument(xsd);
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
        schemaBinding.redetectSchemaForActiveDocument();
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
        return schemaBinding.schemaForValidation(content);
    }
    /** @return how many schema bindings have been applied since this host was created */
    int schemaBindingsApplied() {
        return schemaBinding.schemaBindingsApplied();
    }

    // ----- navigation ------------------------------------------------------

    /**
     * @return the Schematron bound to the active document: the one chosen for it, else the one the
     *         open project binds to its file, else {@code null}
     */
    public File getActiveSchematron() {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return null;
        }
        if (et.schematronFile != null) {
            return et.schematronFile;
        }
        return org.fxt.freexmltoolkit.service.project.ProjectService.getInstance()
                .assignmentFor(et.document.getPath()).schematron() instanceof java.nio.file.Path p ? p.toFile() : null;
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
     * Shows a validation problem at {@code line} in the view the user is in: the Text view moves
     * the caret there ({@link #goToLine(int, int)}), Tree and Graphic select the node of the line
     * (the grid opens tables and cells on the way and highlights it), an XSD's views likewise.
     *
     * @param message the problem's message (an attribute it names is revealed in the grid)
     */
    public void revealProblem(int line, int column, String message) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et) || et.viewMode == ViewMode.TEXT || !et.viewMode.isStructured()
                || line < 1) {
            goToLine(line, column);
            return;
        }
        et.revealLine(line, message);
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
    static boolean setGutterContributor(EditorTab et, String key,
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

    /** Debugger wiring (sessions, breakpoint gutters, inline values, launch configurations). */
    final EditorDebugSupport debugSupport = new EditorDebugSupport(this);

    /** @return {@code true} while an XSLT debug session is alive (its pane is in the dock). */
    public boolean isDebugSessionActive() {
        return debugSupport.isDebugSessionActive();
    }

    /** @return the DEBUG pane of the active session, or {@code null}. */
    public org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane activeDebugPane() {
        return debugSupport.activeDebugPane();
    }

    /**
     * Routes the debugger keys (F5/F7/F8/Shift+F8 while paused) to the active session.
     *
     * @return {@code true} if the key was consumed by the debugger
     */
    public boolean handleDebugShortcut(javafx.scene.input.KeyEvent event) {
        return debugSupport.handleDebugShortcut(event);
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
        debugSupport.startXsltDebug(xsltFile, xml, parameters, format);
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
        debugSupport.startXsltDebug(xsltFile, xml, parameters, format, onFinished);
    }

    /** Ends the active debug session: stops the run, detaches gutters/highlights, removes the pane. */
    public void endXsltDebug() {
        debugSupport.endXsltDebug();
    }

    /** @return whether F9 / Toggle Breakpoint applies: the active tab is a saved XSLT document. */
    public boolean canToggleBreakpoint() {
        return debugSupport.canToggleBreakpoint();
    }

    /**
     * Toggles the breakpoint on the caret line of the active XSLT document (F9 / Tools → Toggle
     * Breakpoint) — the standalone or the session gutter, whichever is installed.
     *
     * @return {@code true} if a breakpoint was set or removed
     */
    public boolean toggleBreakpointAtCaret() {
        return debugSupport.toggleBreakpointAtCaret();
    }

    /**
     * Run to Cursor (Ctrl+F10 / Tools → Run to Cursor) in the active XSLT document: while a
     * debug session runs or is paused in one of its modules, runs to the caret line.
     *
     * @return {@code false} when there is no session to run (the caller may start one with
     *         {@link #setDebugRunToOnStart})
     */
    public boolean runToCursor() {
        return debugSupport.runToCursor();
    }

    /** @return whether Run to Cursor applies: the active tab is a saved XSLT document. */
    public boolean canRunToCursor() {
        return debugSupport.canRunToCursor();
    }

    /** @return the caret line (1-based) of the active XSLT document, or 0. */
    public int activeCaretLine() {
        return debugSupport.activeCaretLine();
    }

    /** @return whether a debug run was started in this app run (Re-run Last Debug applies). */
    public boolean canRerunLastDebug() {
        return debugSupport.canRerunLastDebug();
    }

    /**
     * Re-runs the last debug run: same stylesheet, parameters and format, the input read again
     * (its file, or the open document it came from).
     *
     * @return an error message, or {@code null} when the debugger started
     */
    public String rerunLastDebug() {
        return debugSupport.rerunLastDebug();
    }

    /**
     * Starts the debugger for {@code xsltFile} with a debug configuration: its input file — or
     * the stylesheet's Run target —, parameters, output format (detected from {@code xsl:output}
     * when automatic) and pause on entry.
     *
     * @return an error message, or {@code null} when the debugger started
     */
    public String debugWithConfig(File xsltFile, org.fxt.freexmltoolkit.debugger.DebugLaunchConfig config) {
        return debugSupport.debugWithConfig(xsltFile, config);
    }

    /**
     * Tells the next {@link #startXsltDebug} that its XML text was read from {@code file}, so the
     * paused context node can be shown in that file. Without it the input is matched to the open
     * document with the same text.
     */
    public void setDebugInputFile(Path file) {
        debugSupport.setDebugInputFile(file);
    }

    /**
     * Arms the next {@link #startXsltDebug} to run to the caret line of the active XSLT
     * document ("Debug to Cursor"). Cleared with {@code setDebugRunToOnStart(null, 0)}.
     *
     * @return {@code false} when the active tab is no saved XSLT document
     */
    public boolean prepareDebugToCursor() {
        return debugSupport.prepareDebugToCursor();
    }

    /** Makes the next debug start run to {@code path:line} instead of pausing on entry. */
    public void setDebugRunToOnStart(String path, int line) {
        debugSupport.setDebugRunToOnStart(path, line);
    }

    /**
     * Toggles the input-node breakpoint on the element at {@code caret} of {@code document} in
     * the running debug session.
     *
     * @return {@code true} when one was added, {@code false} when removed or no session / element
     */
    boolean toggleInputNodeBreakpoint(OpenDocument document, String text, int caret) {
        return debugSupport.toggleInputNodeBreakpoint(document, text, caret);
    }

    /** @return the inline values shown on a document's editor tab (tests). */
    java.util.Map<Integer, String> debugInlineValuesOf(OpenDocument document) {
        return debugSupport.debugInlineValuesOf(document);
    }

    int debugInputLineOf(OpenDocument document) {
        return debugSupport.debugInputLineOf(document);
    }

    // ----- internals -------------------------------------------------------

    /** Moves the keyboard focus into the active tab's content (e.g. after a bottom panel closed). */
    public void focusActiveEditor() {
        var tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && tab.getContent() != null) {
            if (tab instanceof EditorTab et && et.viewMode == ViewMode.TEXT) {
                et.view.getNode().requestFocus();
            } else {
                tab.getContent().requestFocus();
            }
        }
    }

    void withActive(java.util.function.Consumer<EditorTab> action) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            action.accept(et);
        }
    }

    void addTab(EditorTab tab) {
        addTab(tab, true);
    }

    /** Adds a tab; {@code select = false} opens it behind the active one (e.g. a schema edited from XML). */
    void addTab(EditorTab tab, boolean select) {
        tab.remoteImportsCallback = this::onRemoteImportsAvailable;
        tab.modelInstalledCallback = () -> {
            if (tab.isSelected()) {
                activeModelRevision.set(activeModelRevision.get() + 1);
            }
        };
        tab.showInGraphicCallback = node -> {
            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                setActiveViewMode(ViewMode.GRAPHIC);
                tab.reselect(node);
                refreshSelectedNode();
            }
        };
        tab.openFileCallback = this::openFile;
        tab.viewModeAppliedCallback = () -> {
            if (tab.isSelected()) {
                activeViewMode.set(tab.viewMode);
                refreshSelectedNode();
            }
        };
        tabPane.getTabs().add(tab);
        debugSupport.installBreakpointGutter(tab);
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
        tab.setOnCloseRequest(e -> fileSync.confirmCloseIfDirty(tab, e));
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
    void closeTabNow(EditorTab tab) {
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
        return closeTab(tabPane.getSelectionModel().getSelectedItem());
    }

    /**
     * Closes {@code tab} the way its close button does: a dirty document asks to save and can veto.
     *
     * @return {@code true} if the tab was closed
     */
    boolean closeTab(Tab tab) {
        if (tab == null || !tab.isClosable() || !tabPane.getTabs().contains(tab)) {
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
     * Closes every closable tab except {@code keep}, one after the other; stops at the first
     * tab whose unsaved-changes prompt is cancelled.
     */
    void closeOtherTabs(Tab keep) {
        for (Tab tab : new java.util.ArrayList<>(tabPane.getTabs())) {
            if (tab != keep && tab.isClosable() && !closeTab(tab)) {
                return;
            }
        }
    }

    /** Keeps {@link #openDocuments} in tab order after tabs were added, removed or dragged. */
    private void syncOpenDocumentOrder() {
        java.util.List<OpenDocument> inTabOrder = tabPane.getTabs().stream()
                .filter(EditorTab.class::isInstance)
                .map(t -> ((EditorTab) t).document)
                .filter(openDocuments::contains)
                .toList();
        if (inTabOrder.size() == openDocuments.size() && !inTabOrder.equals(openDocuments)) {
            FXCollections.sort(openDocuments, java.util.Comparator.comparingInt(inTabOrder::indexOf));
        }
    }

    /** Called with a document's file by the tab menu's "Reveal in Explorer Panel" (set by the shell). */
    public void setRevealInExplorerHandler(java.util.function.Consumer<Path> handler) {
        this.revealInExplorerHandler = handler;
    }

    /** Test seam: decides whether a dirty document may be reloaded (discarding its changes). */
    void setReloadConfirmer(java.util.function.Predicate<OpenDocument> confirmer) {
        this.reloadConfirmer = java.util.Objects.requireNonNull(confirmer, "confirmer");
    }

    /** Test seam: the tab menu's entries for {@code tab}, in their current state. */
    java.util.List<javafx.scene.control.MenuItem> tabMenuItems(Tab tab) {
        return EditorTabMenu.items(tab, tabMenuActions);
    }

    /** Test seam: the open tabs in order. */
    java.util.List<Tab> tabsForTest() {
        return tabPane.getTabs();
    }

    private java.util.function.Consumer<Path> revealInExplorerHandler;

    private java.util.function.Predicate<OpenDocument> reloadConfirmer = this::confirmReload;

    private boolean confirmReload(OpenDocument doc) {
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Reload from Disk",
                "Reload " + doc.getDisplayName() + "?",
                "Your unsaved changes will be lost.");
        javafx.scene.control.ButtonType reload =
                new javafx.scene.control.ButtonType("Reload", javafx.scene.control.ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(reload, javafx.scene.control.ButtonType.CANCEL);
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }
        return alert.showAndWait().filter(reload::equals).isPresent();
    }

    /** The editor tab's actions behind {@link EditorTabMenu}; each targets the right-clicked tab. */
    private final EditorTabMenu.Actions tabMenuActions = new EditorTabMenu.Actions() {
        @Override
        public OpenDocument documentOf(Tab tab) {
            return tab instanceof EditorTab et ? et.document : null;
        }

        @Override
        public boolean hasOtherClosableTabs(Tab tab) {
            return tabPane.getTabs().stream().anyMatch(t -> t != tab && t.isClosable());
        }

        @Override
        public void reload(Tab tab) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && !et.loading
                    && (!et.document.isDirty() || reloadConfirmer.test(et.document))) {
                fileSync.reloadFromDisk(et, et.document.getPath());
            }
        }

        @Override
        public void close(Tab tab) {
            closeTab(tab);
        }

        @Override
        public void closeOthers(Tab tab) {
            closeOtherTabs(tab);
        }

        @Override
        public void save(Tab tab) {
            if (tab instanceof EditorTab et) {
                if (et.document.isUntitled()) {
                    fileSync.saveTabAs(et);
                } else {
                    fileSync.saveTab(et);
                }
            }
        }

        @Override
        public void saveAs(Tab tab) {
            if (tab instanceof EditorTab et) {
                fileSync.saveTabAs(et);
            }
        }

        @Override
        public void copyPath(Tab tab) {
            if (tab instanceof EditorTab et && !et.document.isUntitled()) {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(et.document.getPath().toAbsolutePath().toString());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        }

        @Override
        public void revealInFileManager(Tab tab) {
            if (tab instanceof EditorTab et && !et.document.isUntitled()) {
                Path path = et.document.getPath().toAbsolutePath();
                org.fxt.freexmltoolkit.util.DesktopActions.reveal(path.toFile(), e ->
                        org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorHost.reveal-failed"),
                                I18n.t("EditorHost.could-not-show-in-the", path),
                                I18n.t("EditorHost.open-the-folder-manually"), e));
            }
        }

        @Override
        public void revealInExplorer(Tab tab) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && revealInExplorerHandler != null) {
                revealInExplorerHandler.accept(et.document.getPath().toAbsolutePath());
            }
        }
    };

    /**
     * Records a tab's schema-binding status and mirrors it into {@link #activeSchemaStatus}
     * when the tab is front-most. Must be called on the FX thread (same publish pattern as
     * {@link #activeSchema}).
     */
    void publishSchemaStatus(EditorTab tab, SchemaStatus status) {
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
    void publishSchemaSource(EditorTab tab, SchemaSource source, String detail) {
        tab.schemaSource = source;
        tab.schemaSourceDetail = detail;
        if (tab.isSelected()) {
            activeSchemaSource.set(source);
            activeSchemaSourceDetail.set(detail);
        }
    }

    // ----- schema lookups and navigation (Inspector, Go to Definition, reveal) -----

    /** XSD facts for the Inspector, the bound declaration behind XML elements, schema navigation. */
    private final EditorSchemaLookup schemaLookup = new EditorSchemaLookup(this);

    /**
     * @return XSD-derived type/documentation for an XML-instance element at the given XPath, if
     * a schema is bound to the active document; otherwise empty.
     */
    public java.util.Optional<
            org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ElementTypeInfo>
            resolveActiveXmlElementInfo(String elementXPath) {
        return schemaLookup.resolveActiveXmlElementInfo(elementXPath);
    }

    /**
     * @return the per-language schema documentation entries for the element at the given XPath
     * (lang is lowercase, {@code "default"} for entries without {@code xml:lang}); empty when
     * no schema is bound or the path cannot be resolved.
     */
    public java.util.List<org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo>
            resolveActiveXmlElementDocs(String elementXPath) {
        return schemaLookup.resolveActiveXmlElementDocs(elementXPath);
    }

    /**
     * @return base type and derivation ({@code restriction} / {@code extension}) of the schema type
     * of the XML element at the given XPath; empty when no schema is bound or the type is not derived
     */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation.Derivation>
            resolveActiveXmlElementDerivation(String elementXPath) {
        return schemaLookup.resolveActiveXmlElementDerivation(elementXPath);
    }

    /**
     * Checks a value against the schema facets/enumeration of the XML element at the given XPath.
     *
     * @return the validator's message ({@code null} when the value fits); empty when no schema is
     * bound to the active document
     */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult>
            validateActiveXmlElementValue(String elementXPath, String value) {
        return schemaLookup.validateActiveXmlElementValue(elementXPath, value);
    }

    /** @return schema facts of an attribute of the XML element at the given XPath; empty without a schema */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.AttributeTypeInfo>
            resolveActiveXmlAttributeInfo(String elementXPath, String attributeName) {
        return schemaLookup.resolveActiveXmlAttributeInfo(elementXPath, attributeName);
    }

    /**
     * @return value types for exporting the active XML document to Excel, from its bound XSD;
     * empty when the active document is no XML tab with a schema (FX thread)
     */
    public java.util.Optional<org.fxt.freexmltoolkit.service.SpreadsheetTypeResolver> activeSpreadsheetTypeResolver() {
        return schemaLookup.activeSpreadsheetTypeResolver();
    }

    /** Reveals a named type in the Tree view (switching to the XSD tab + Tree if needed). */
    public void revealTypeByName(String typeName) {
        schemaLookup.revealTypeByName(typeName);
    }

    /**
     * Opens an XSD file in the shell and, once it has loaded and parsed, switches to the
     * Graphic view and selects the named element (preferred) or type. Bridge replacing the
     * retired legacy XSD editor's {@code navigateToElementInGraphView}, used by the XML
     * editor's "go to schema definition". Navigation is deferred until the async load
     * completes; if {@code elementName} is blank the file is just opened.
     */
    public void openXsdAndReveal(java.nio.file.Path path, String elementName) {
        schemaLookup.openXsdAndReveal(path, elementName);
    }

    /**
     * Reveals, in the active Graphic view, the card at an instance path (the model particle of
     * every step from the global root element down, see {@code XsdInstanceLocator.Located#particlePath()}),
     * expanding the diagram along that path; in the Tree view the last particle is revealed.
     */
    public void revealPathInActiveGraph(java.util.List<XsdNode> particlePath) {
        schemaLookup.revealPathInActiveGraph(particlePath);
    }

    /** Selects (reveals) the given node in the active structured view (Tree or Graphic). */
    public void selectNodeInActiveTree(XsdNode node) {
        schemaLookup.selectNodeInActiveTree(node);
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
        return schemaLookup.revealSchemaNodeByXPath(xpath);
    }

    /** @return the schema-allowed child element (local) names for the element at the given XPath. */
    public java.util.List<String> resolveValidChildren(String elementXPath) {
        return schemaLookup.resolveValidChildren(elementXPath);
    }

    /** @return example/allowed values for the element at the given XPath (xs:example, else enumeration). */
    public java.util.List<String> resolveExampleValues(String elementXPath) {
        return schemaLookup.resolveExampleValues(elementXPath);
    }

    /**
     * @return the position of the XML element's declaration in its parent compositor
     * ({@code "2 of 6 · sequence"}); empty when no schema is bound or the parent is no compositor
     */
    public java.util.Optional<String> resolveActiveXmlElementOrder(String elementXPath) {
        return schemaLookup.resolveActiveXmlElementOrder(elementXPath);
    }

    /** @return the extra schema facts of the XML element at the given XPath; empty without a schema */
    public java.util.Optional<XmlSchemaDetails> resolveActiveXmlElementDetails(String elementXPath) {
        return schemaLookup.resolveActiveXmlElementDetails(elementXPath);
    }

    /** @return the attribute names the schema declares for the XML element at the given XPath */
    public java.util.List<String> resolveActiveXmlValidAttributes(String elementXPath) {
        return schemaLookup.resolveActiveXmlValidAttributes(elementXPath);
    }

    /**
     * @return the XSD declaration of the element at {@code elementXPath} (or of its attribute
     * {@code attributeName}) in the active XML document's bound schema; empty without a schema or
     * when the path cannot be mapped unambiguously
     */
    public java.util.Optional<XmlSchemaDeclaration> resolveActiveXmlSchemaDeclaration(String elementXPath,
            String attributeName) {
        return schemaLookup.resolveActiveXmlSchemaDeclaration(elementXPath, attributeName);
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
        schemaLookup.editActiveXmlSchemaDeclaration(elementXPath, attributeName, edit, onDone);
    }

    /**
     * Opens the bound XSD of the active XML document in front, in the Graphic view, expanded along
     * the instance path down to the card of the element at {@code elementXPath} (or its
     * attribute), and selects it — by structure, not by name, so same-named local declarations and
     * the several occurrences of a named type's children are told apart.
     */
    public void revealActiveXmlSchemaDeclaration(String elementXPath, String attributeName) {
        schemaLookup.revealActiveXmlSchemaDeclaration(elementXPath, attributeName);
    }

    /**
     * Where the given XSD node is used in the active schema. For a global component (named type,
     * global element/attribute, group) these are the places that reference it; for a local
     * declaration it is the global component that contains it. Builds a reference graph — call it
     * off the FX thread.
     */
    public java.util.List<SchemaUsage> resolveSchemaUsages(org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema,
            XsdNode node) {
        return schemaLookup.resolveSchemaUsages(schema, node);
    }

    /**
     * Navigates a selected XML-instance element to its definition in the active document's bound
     * XSD: resolves the element's XPath against the schema, then opens/reveals it in the XSD editor.
     * Shared by the Graphic and Tree context menus (parallels the Text view's "Go to Definition").
     * No-op when no schema is bound or the node is not an element.
     */
    void goToXmlDefinition(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode node) {
        schemaLookup.goToXmlDefinition(node);
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

        SchemaEditTarget(EditorTab tab,
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

    /** A place a schema component is used: the referring node and a short description. */
    public record SchemaUsage(XsdNode node, String name, String detail) {
    }

    /** Adds a new child element with the given name to the selected XML element. */
    public boolean addActiveXmlChildElement(String name) {
        return editActiveXml(el -> new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.AddElementCommand(
                el, new org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement(name)));
    }

    // ----- saving and external changes --------------------------------------

    /**
     * Saves the active document to its current path (must be titled). The file is written on a worker
     * thread (a large file on a network drive blocked the UI for seconds).
     *
     * @return completes on the FX thread with whether the file was written
     */
    public CompletableFuture<Boolean> saveActive() {
        return fileSync.saveActive();
    }

    /**
     * Saves the active document to {@code target} (Save As), written on a worker thread.
     *
     * @return completes on the FX thread with whether the file was written
     */
    public CompletableFuture<Boolean> saveActiveAs(Path target) {
        return fileSync.saveActiveAs(target);
    }

    /**
     * Saves all titled, dirty documents, written on worker threads.
     *
     * @return completes on the FX thread with the number of files written
     */
    public CompletableFuture<Integer> saveAll() {
        return fileSync.saveAll();
    }

    /** Test seam: answers external-change prompts without a blocking dialog. */
    void setExternalChangePrompt(ExternalChangePrompt prompt) {
        fileSync.setExternalChangePrompt(prompt);
    }

    /** Test seam: checks the open documents' files now, regardless of window focus. */
    void checkExternalChangesNow() {
        fileSync.checkExternalChangesNow();
    }

    /** Test seam: whether an external-change check is running or being handled. */
    boolean isExternalChangeCheckRunning() {
        return fileSync.isExternalChangeCheckRunning();
    }

    /**
     * Asks about unsaved documents before the application closes (window button, File → Exit,
     * Ctrl/Cmd+Q, macOS Quit): one dialog listing them with Save All / Don't Save / Cancel.
     *
     * @return whether the application may close now
     */
    public boolean confirmExit() {
        return fileSync.confirmExit();
    }

    /**
     * @param prompter asks the user about the given unsaved documents (shown only when there are any)
     * @return whether the application may close now
     */
    boolean confirmExit(java.util.function.Function<java.util.List<OpenDocument>, ExitChoice> prompter) {
        return fileSync.confirmExit(prompter);
    }

    // ----- external changes ------------------------------------------------

    /**
     * The files behind the schema facts of an XML document.
     *
     * @param schema    the bound schema these belong to; a different binding makes them obsolete
     * @param stamps    the schema file and the files it pulls in, as read; a value can be
     *                  {@code null} when the file's state is undeterminable
     * @param checksums the content checksums of those files
     */
    record BoundSchemaWatch(File schema, java.util.Map<Path, DiskStamp> stamps,
                                    java.util.Map<Path, Long> checksums) {
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

    /** @return the editor tab showing {@code document}, or {@code null}. */
    EditorTab editorTabOf(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                return et;
            }
        }
        return null;
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
}
