package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.service.DragDropService;

/**
 * The file-type-aware center of the Unified shell: a tab pane of open documents.
 * Each tab embeds an {@link EditorView} chosen by file type (XML family vs JSON),
 * reusing the existing editor widgets. File reads run on the shared executor so
 * the UI thread is never blocked; the active document, caret and schema are
 * observable for the Explorer panel, inspector and status bar.
 */
public class EditorHost extends BorderPane {

    private final TabPane tabPane = new TabPane();
    /** Tab pane plus an overlaid view-mode switch pinned to the top-right of the tab header. */
    private final javafx.scene.layout.StackPane tabArea = new javafx.scene.layout.StackPane();
    private final ObservableList<OpenDocument> openDocuments = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper activeCaret = new ReadOnlyIntegerWrapper(this, "activeCaret", 0);
    private final ReadOnlyObjectWrapper<File> activeSchema = new ReadOnlyObjectWrapper<>(this, "activeSchema", null);
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
     * @param summary      a short human-readable label (e.g. "Valid", "Well-formed", "3 problem(s)")
     */
    public record ValidationStatus(ValidationState state, int problemCount, String summary) {
        public static final ValidationStatus NONE =
                new ValidationStatus(ValidationState.NOT_VALIDATED, 0, "Not validated");
    }
    /** The most recently active editor tab — insertion target when a tool tab is in front. */
    private EditorTab lastEditorTab;

    /** Handler for Welcome/Dashboard action keys (activity ids, open-folder); set by the shell. */
    private java.util.function.Consumer<String> welcomeActionHandler = key -> { };

    private final EditorWelcomePane welcomePane = new EditorWelcomePane(
            this::newDocument, this::openFileChooser, this::openFile,
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

        // The view-mode switch (Text/Tree/Graphic/Grid) lives here, overlaid on the top-right of
        // the tab header — not in the editor toolbar — so the toolbar stays a single-row action
        // band and the per-document view mode reads as part of the document's tab strip. CSS
        // (.fxt-editor-tabpane > .tab-header-area) reserves matching right padding so the tabs and
        // their overflow button never slide underneath it.
        javafx.scene.layout.Region viewSwitch = buildViewSwitch();
        javafx.scene.layout.StackPane.setAlignment(viewSwitch, javafx.geometry.Pos.TOP_RIGHT);
        javafx.scene.layout.StackPane.setMargin(viewSwitch, new javafx.geometry.Insets(5, 8, 0, 0));
        tabArea.getChildren().setAll(tabPane, viewSwitch);

        setupDragAndDrop();
        // Show the welcome empty-state while no document is open; swap to the tab
        // pane as soon as one opens, and back again when the last tab closes.
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> updateCenter());
        updateCenter();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT instanceof EditorTab et) {
                lastEditorTab = et;
                activeCaret.set(et.view.getCodeArea().getCaretPosition());
                activeSchema.set(et.schemaFile);
                activeViewMode.set(et.viewMode);
            } else {
                activeSchema.set(null);
            }
            // A different document's last result must not linger; validation re-runs
            // (continuous, or via Validate/F8) for the newly active document.
            validationStatus.set(ValidationStatus.NONE);
            activeProblems.clear();
            refreshSelectedNode();
        });
        // Delete key removes the selected node in structured views (not in Text mode).
        addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE
                    && activeViewMode.get() != ViewMode.TEXT && deleteActiveNode()) {
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

    /** @return the text of the given (possibly non-active) open document, or empty if closed. */
    public Optional<String> getDocumentText(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab editorTab && editorTab.document == document) {
                return Optional.of(editorTab.view.getText());
            }
        }
        return Optional.empty();
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

    /** @return the active document's current view mode (Text/Tree/Graphic). */
    public ReadOnlyObjectProperty<ViewMode> activeViewModeProperty() {
        return activeViewMode.getReadOnlyProperty();
    }

    /** Switches the active document to the given view mode (structured modes apply to XSD). */
    public void setActiveViewMode(ViewMode mode) {
        withActive(et -> {
            et.setViewMode(mode);
            activeViewMode.set(et.viewMode);
            refreshSelectedNode();
        });
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
        XsdNode schema = getActiveSchemaRoot().orElse(null);
        if (schema == null) {
            var doc = getActiveDocument();
            if (doc.isPresent() && doc.get().getFileType() == EditorFileType.XSD) {
                try {
                    schema = new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory()
                            .fromString(getActiveText().orElse(""));
                } catch (Exception ignored) {
                    return java.util.List.of();
                }
            }
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
        return org.fxt.freexmltoolkit.controls.shell.schema.TypeLibrary.collectNamedTypes(schema);
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
                boolean isType =
                        child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.SIMPLE_TYPE
                        || child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.COMPLEX_TYPE;
                if (isType && typeName.equals(child.getName())) {
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

    /** Selects (reveals) the given node in the active structured view (Tree or Graphic). */
    public void selectNodeInActiveTree(XsdNode node) {
        withActive(et -> {
            if (et.viewMode == ViewMode.TREE && et.treeView != null) {
                et.treeView.selectNode(node);
            } else if (et.viewMode == ViewMode.GRAPHIC && et.xsdGraphView != null) {
                et.xsdGraphView.selectModelNode(node);
            }
        });
    }

    private void refreshSelectedNode() {
        XsdNode selected = null;
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode xmlSelected = null;
        org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode jsonSelected = null;
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            if (et.viewMode != ViewMode.TEXT) {
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
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
        EditorTab tab = new EditorTab(OpenDocument.forPath(path), this::refreshSelectedNode, nodeClipboard);
        addTab(tab);
        // Wire "Go to Definition": open the bound XSD and reveal the element (no-op for non-XML views).
        tab.view.setGoToDefinitionHandler(req ->
                openXsdAndReveal(req.xsdFile().toPath(), req.elementName()));
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .addLastOpenFile(path.toFile());
        } catch (Throwable ignored) {
            // properties service unavailable — no recent-files persistence
        }
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
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    /**
     * Opens a side-by-side diff comparing the active document (left) against
     * {@code rightFile} (right), reusing the existing {@link org.fxt.freexmltoolkit.controls.diff.DiffView}.
     * Saving the left side writes back to the active document (and its file, if any).
     *
     * @return the created diff tab, or {@code null} if no document is active
     */
    public org.fxt.freexmltoolkit.controls.diff.DiffView openDiffWithFile(File rightFile) {
        if (!(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab left)) {
            return null;
        }
        String leftName = left.document.getDisplayName();
        String leftText = left.view.getText();
        java.util.function.Consumer<String> leftSave = newText -> {
            left.view.setText(newText);
            Path path = left.document.getPath();
            if (path != null) {
                try {
                    Files.writeString(path, newText, StandardCharsets.UTF_8);
                    left.document.setDirty(false);
                } catch (IOException e) {
                    // surface via the document staying dirty; the diff view reports save errors itself
                }
            }
        };
        org.fxt.freexmltoolkit.controls.diff.DiffView diff =
                new org.fxt.freexmltoolkit.controls.diff.DiffView(leftName, leftText, leftSave, rightFile);
        tabPane.getTabs().add(diff);
        tabPane.getSelectionModel().select(diff);
        return diff;
    }

    /**
     * Opens an in-app preview of {@code pdf} as a new tab (lazy page rendering).
     *
     * @return the preview control, or {@code null} if the PDF cannot be read
     */
    public PdfPreview openPdfPreview(File pdf) {
        try {
            PdfPreview preview = new PdfPreview(pdf);
            Tab tab = new Tab(pdf.getName(), preview);
            tab.setGraphic(new IconifyIcon("bi-file-earmark-pdf"));
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            return preview;
        } catch (IOException e) {
            return null;
        }
    }

    /** Shows the welcome empty-state when no tab is open, the tab pane otherwise. */
    private void updateCenter() {
        if (tabPane.getTabs().isEmpty()) {
            welcomePane.setRecentFiles(recentFiles());
            welcomePane.setStats(welcomeStats());
            setCenter(welcomePane);
        } else {
            setCenter(tabArea);
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
            });
        };
        sync.run();
        activeViewMode.addListener((obs, oldV, newV) -> sync.run());
        activeTabProperty().addListener((obs, oldV, newV) -> sync.run());
        return box;
    }

    /** @return the dashboard counters (recent / favorites / templates / saved queries), best-effort. */
    private EditorWelcomePane.WelcomeStats welcomeStats() {
        int recent = recentFiles().size();
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
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpResponse<String> response = client.send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                String name = urlFileName(url);
                EditorFileType type = EditorFileType.fromFileName(name);
                Platform.runLater(() -> openGeneratedDocument(body, type, name));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert =
                            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Open from URL");
                    alert.setHeaderText("Could not fetch the URL");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
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

    /** Creates an empty untitled document of the given type. */
    public OpenDocument newDocument(EditorFileType type) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(untitledName(), type), this::refreshSelectedNode, nodeClipboard);
        addTab(tab);
        tab.attachDirtyTracking();
        return tab.document;
    }

    /**
     * Opens generated content (e.g. a generated XSD, flattened schema, sample
     * data) as a new untitled, dirty document.
     *
     * @return the new document
     */
    public OpenDocument openGeneratedDocument(String content, EditorFileType type, String displayName) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(displayName, type), this::refreshSelectedNode, nodeClipboard);
        addTab(tab);
        tab.view.setText(content);
        tab.attachDirtyTracking();
        tab.document.setDirty(true);
        return tab.document;
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
                return true;
            }
        }
        return false;
    }

    // ----- saving / editing ------------------------------------------------

    /** Saves the active document to its current path (must be titled). @return success */
    public boolean saveActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et && !et.document.isUntitled() && write(et, et.document.getPath());
    }

    /** Saves the active document to {@code target} (Save As). */
    public boolean saveActiveAs(Path target) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && write(et, target)) {
            et.document.setPath(target);
            et.refreshIcon();
            return true;
        }
        return false;
    }

    /** Saves all titled, dirty documents. @return the number of files written. */
    public int saveAll() {
        int saved = 0;
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && et.document.isDirty()
                    && write(et, et.document.getPath())) {
                saved++;
            }
        }
        return saved;
    }

    /** Undo: command stack in structured (Tree/Graphic) mode, editor undo in Text mode. */
    public void undoActive() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode != ViewMode.TEXT && et.editorContext != null) {
            if (et.undoStructured()) {
                refreshSelectedNode();
            }
        } else {
            withActive(et -> et.view.undo());
        }
    }

    public void redoActive() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode != ViewMode.TEXT && et.editorContext != null) {
            if (et.redoStructured()) {
                refreshSelectedNode();
            }
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

    /** Changes the selected node's documentation text. */
    public boolean changeActiveDocumentation(String documentation) {
        return editActivePreservingSelection(et -> et.changeDocumentation(et.currentSelection, documentation));
    }

    /** Changes the selected XSD node's {@code xs:appinfo} content via the command stack. */
    public boolean changeActiveAppinfo(String appinfo) {
        return editActivePreservingSelection(et -> et.changeAppinfo(et.currentSelection, appinfo));
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

    /** Pretty-prints the active document (XML or JSON). @return {@code true} if reformatted. */
    public boolean formatActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return false;
        }
        try {
            String text = et.view.getText();
            String formatted = et.document.getFileType() == EditorFileType.JSON
                    ? new org.fxt.freexmltoolkit.service.JsonService().formatJson(text, 2)
                    : org.fxt.freexmltoolkit.service.XmlService.prettyFormat(text, 2);
            if (formatted != null && !formatted.isBlank()) {
                et.view.setText(formatted);
                et.document.setDirty(true);
                return true;
            }
        } catch (Exception ignored) {
            // invalid content: leave the text untouched
        }
        return false;
    }

    /** Minifies the active document (XML: drop inter-tag whitespace; JSON: compact). @return success */
    public boolean minifyActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return false;
        }
        try {
            String text = et.view.getText();
            String minified = et.document.getFileType() == EditorFileType.JSON
                    ? new org.fxt.freexmltoolkit.service.JsonService().compactJson(text)
                    : text.replaceAll(">\\s+<", "><").strip();
            if (minified != null && !minified.isBlank()) {
                et.view.setText(minified);
                et.document.setDirty(true);
                return true;
            }
        } catch (Exception ignored) {
            // invalid content: leave the text untouched
        }
        return false;
    }

    /** @return the active editor's code area, or {@code null} (no editor tab in front). */
    public org.fxmisc.richtext.CodeArea getActiveCodeArea() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.view.getCodeArea() : null;
    }

    /** @return the active editor tab's view, or {@code null} if no editor tab is in front. */
    EditorView activeEditorView() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab instanceof EditorTab et ? et.view : null;
    }

    /** Binds an XSD to the active document for schema-aware IntelliSense. @return success */
    public boolean setSchemaForActiveDocument(File xsd) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.view.supportsSchema() && et.view.loadSchema(xsd)) {
            et.schemaFile = xsd;
            et.view.invalidateIntelliSenseCache();
            activeSchema.set(xsd);
            loadXmlSchemaProviderAsync(et, xsd);
            return true;
        }
        return false;
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
        setActiveViewMode(ViewMode.TEXT);
        withActive(et -> {
            var codeArea = et.view.getCodeArea();
            int paragraphs = codeArea.getParagraphs().size();
            int paragraph = Math.max(0, Math.min(line - 1, Math.max(0, paragraphs - 1)));
            codeArea.moveTo(paragraph, 0);
            codeArea.requestFollowCaret();
            et.view.getNode().requestFocus();
        });
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
        return tab instanceof EditorTab et && et.view.setExtraGutterFactory(factory);
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
                return et.view.setExtraGutterFactory(factory);
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

    /**
     * Launches an interactive XSLT debug session: opens the stylesheet as an editor
     * document, attaches a breakpoint gutter, and opens the Debug tool tab.
     */
    public void startXsltDebug(java.io.File xsltFile, String xml,
            java.util.Map<String, Object> parameters,
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format) {
        // A Debug session mutates the singleton transformation-engine state; only one at a time.
        // If a Debug tab is already open, bring it to the front instead of starting a second.
        for (Tab t : tabPane.getTabs()) {
            if (t.getContent() instanceof org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugView) {
                tabPane.getSelectionModel().select(t);
                return;
            }
        }

        // 1. Open (or focus) the stylesheet as an editor document.
        openFile(xsltFile);
        OpenDocument xsltDoc = getActiveDocument().orElse(null);
        if (xsltDoc == null) {
            return;
        }

        // Read the stylesheet up front: if it cannot be read, surface a clear error and abort
        // rather than silently running Saxon on an empty stylesheet.
        final String xsltContent;
        try {
            xsltContent = java.nio.file.Files.readString(
                    xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            javafx.scene.control.Alert alert =
                    new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("XSLT Debug");
            alert.setHeaderText("Could not start the debugger");
            alert.setContentText("Could not read stylesheet: " + e.getMessage());
            if (getScene() != null) {
                alert.initOwner(getScene().getWindow());
            }
            alert.showAndWait();
            return;
        }

        // 2. Build the debug controller + gutter and attach the gutter to the XSLT tab.
        var controller = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController();
        var gutter = new org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory(
                controller.getSession(), v -> refreshGutterFor(xsltDoc));
        gutter.setFilePath(xsltFile.getAbsolutePath());
        setGutterFactoryFor(xsltDoc, gutter);

        // 3. Open the Debug tool tab; wire current-line + show-in-editor back to this host.
        var debugView = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugView(
                controller,
                xsltFile.getAbsolutePath(),
                () -> getActiveText().orElse(xml),
                line -> { gutter.setCurrentExecutionLine(line); refreshGutterFor(xsltDoc); },
                () -> {
                    selectDocument(xsltDoc);
                    int line = controller.getSession().getPausedSnapshot() != null
                            ? controller.getSession().getPausedSnapshot().lineNumber() : 1;
                    if (line > 0) {
                        goToLine(line);
                    }
                });
        // Call-stack / breakpoint double-click must jump to the selected frame's own line,
        // not the current pause line.
        debugView.getCallStackView().setOnJumpToLine(line -> {
            selectDocument(xsltDoc);
            if (line > 0) {
                goToLine(line);
            }
        });
        debugView.getBreakpointsView().setOnJumpToLine(line -> {
            selectDocument(xsltDoc);
            if (line > 0) {
                goToLine(line);
            }
        });
        Tab debugTab = openToolTab("Debug", "bi-bug", debugView);
        // Closing the Debug tab stops the session, detaches the listener and the gutter.
        debugTab.setOnClosed(e -> {
            controller.stop();
            controller.close();
            debugView.dispose();
            setGutterFactoryFor(xsltDoc, null);
            refreshGutterFor(xsltDoc);
        });

        // 4. Start the run (any breakpoints set via the gutter are already on the session).
        controller.start(xml, xsltContent, parameters, format);
    }

    // ----- internals -------------------------------------------------------

    private void withActive(java.util.function.Consumer<EditorTab> action) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            action.accept(et);
        }
    }

    private void addTab(EditorTab tab) {
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        openDocuments.add(tab.document);
        tab.setOnClosed(e -> openDocuments.remove(tab.document));
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

    private void loadAsync(EditorTab tab, Path path) {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                File autoXsd = detectSchemaFor(tab, path);
                Platform.runLater(() -> {
                    tab.view.setText(content);
                    tab.document.setDirty(false);
                    tab.attachDirtyTracking();
                    if (autoXsd != null) {
                        tab.schemaFile = autoXsd;
                        tab.view.invalidateIntelliSenseCache();
                        if (tab.isSelected()) {
                            activeSchema.set(autoXsd);
                        }
                        loadXmlSchemaProviderAsync(tab, autoXsd);
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> tab.view.setText("Could not read " + path + ": " + e.getMessage()));
            }
        });
    }

    /**
     * Auto-detects and binds the XSD for an opened XML document, matching the legacy
     * editor: reuses {@link org.fxt.freexmltoolkit.service.XmlService#loadSchemaFromXMLFile()},
     * which resolves both {@code xsi:schemaLocation} (namespaced) and
     * {@code xsi:noNamespaceSchemaLocation}, local and remote (http/https, downloaded and
     * cached) references. A fresh service instance keeps detection per-tab and stateless.
     * Runs on the loader thread (remote download must not block the UI).
     *
     * @return the resolved, schema-bound XSD file, or {@code null} if none was found
     */
    private File detectSchemaFor(EditorTab tab, Path path) {
        if (!tab.view.supportsSchema()) {
            return null;
        }
        try {
            org.fxt.freexmltoolkit.service.XmlService service =
                    new org.fxt.freexmltoolkit.service.XmlServiceImpl();
            service.setCurrentXmlFile(path.toFile());
            if (service.loadSchemaFromXMLFile()) {
                File xsd = service.getCurrentXsdFile();
                if (xsd != null && xsd.exists() && tab.view.loadSchema(xsd)) {
                    return xsd;
                }
            }
        } catch (Exception e) {
            // detection is best-effort; a malformed document simply binds no schema
        }
        return null;
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
                var service = new org.fxt.freexmltoolkit.service.XsdDocumentationService();
                service.setXsdFilePath(xsd.getAbsolutePath());
                service.processXsd(Boolean.TRUE);
                var adapter = new org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter();
                adapter.setXsdDocumentationData(service.xsdDocumentationData);
                Platform.runLater(() -> {
                    tab.xmlSchemaProvider = adapter;
                    if (tab.xmlEditorContext != null) {
                        tab.xmlEditorContext.setSchemaProvider(adapter);
                    }
                    if (tab.isSelected()) {
                        refreshSelectedNode();
                    }
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
        if (map.containsKey(xpath)) {
            return map.get(xpath);
        }
        String norm = xpath.startsWith("/") ? xpath : "/" + xpath;
        if (map.containsKey(norm)) {
            return map.get(norm);
        }
        String local = localXmlName(xpath);
        if (local != null) {
            for (var entry : map.entrySet()) {
                if (entry.getKey().endsWith("/" + local)) {
                    return entry.getValue();
                }
            }
        }
        return null;
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

    private boolean write(EditorTab tab, Path target) {
        try {
            Files.writeString(target, tab.view.getText(), StandardCharsets.UTF_8);
            tab.document.setDirty(false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String untitledName() {
        return "Untitled " + (openDocuments.size() + 1);
    }

    private void setupDragAndDrop() {
        setOnDragOver(event -> {
            var dragboard = event.getDragboard();
            if (dragboard.hasFiles()
                    && DragDropService.hasFilesWithExtensions(dragboard.getFiles(), DragDropService.ALL_XML_RELATED)) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            boolean done = false;
            if (dragboard.hasFiles()) {
                for (File file : DragDropService.filterByExtensions(dragboard.getFiles(),
                        DragDropService.ALL_XML_RELATED)) {
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

    private void confirmCloseIfDirty(EditorTab tab, javafx.event.Event closeEvent) {
        if (!tab.document.isDirty()) {
            return;
        }
        tabPane.getSelectionModel().select(tab);
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Save changes to " + tab.document.getDisplayName() + "?");
        alert.setContentText("Your changes will be lost if you don't save them.");
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
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null && write(tab, file.toPath())) {
            tab.document.setPath(file.toPath());
            tab.refreshIcon();
            return true;
        }
        return false;
    }

    /** A tab bound to one {@link OpenDocument}, its editor view, and Text/Tree/Graphic modes. */
    private static final class EditorTab extends Tab {
        private final OpenDocument document;
        private final EditorView view;
        private final Runnable selectionCallback;
        /** Shared shell clipboard (passed in because EditorTab is a static nested class). */
        private final org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard clipboard;
        private final javafx.scene.layout.StackPane contentStack = new javafx.scene.layout.StackPane();
        private org.fxt.freexmltoolkit.controls.shell.schema.XsdTreeView treeView;
        private org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView xsdGraphView;
        private org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView jsonTreeView;
        private org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView xmlInstanceTreeView;
        private XmlGridView xmlGridView;
        private org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext;
        private XsdNode currentSelection;
        private org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode currentXmlSelection;
        private org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode currentJsonSelection;
        private ViewMode viewMode = ViewMode.TEXT;
        private boolean dirtyTrackingAttached;
        /** Editor text the current {@link #editorContext} was parsed from (P2: avoid needless re-parse). */
        private String lastParsedText;
        /** Shared XML-instance model+command context across Text/Tree/Grid (mirrors {@link #editorContext}). */
        private org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext xmlEditorContext;
        /** Editor text {@link #xmlEditorContext} was parsed from (avoid needless re-parse; detect external edits). */
        private String lastParsedXmlText;
        /** Coalesces bursts of model-change events into a single round-trip (P1). */
        private final javafx.animation.PauseTransition roundTripDebounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
        /** Coalesces caret movement into a single Text-view caret&rarr;node resolution. */
        private final javafx.animation.PauseTransition xmlCaretDebounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        private File schemaFile;
        private File schematronFile;
        /** XSD-derived, read-only schema info for XML-instance nodes (lazily built off-thread). */
        private org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider xmlSchemaProvider;

        EditorTab(OpenDocument document, Runnable selectionCallback,
                org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard clipboard) {
            this.document = document;
            this.selectionCallback = selectionCallback;
            this.clipboard = clipboard;
            this.view = EditorViews.create(document.getFileType());
            contentStack.getChildren().add(view.getNode());
            setContent(contentStack);
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
                case TREE -> document.getFileType() != EditorFileType.OTHER;
                case GRAPHIC -> switch (document.getFileType()) {
                    case XSD, XML, XSLT, SCHEMATRON -> true;
                    default -> false;
                };
            };
        }

        void setViewMode(ViewMode mode) {
            // Persist any pending (debounced) graphical edit before switching, so the
            // text we read below — and the new view — see the latest model state.
            flushPendingRoundTrip();
            ViewMode target = supportsView(mode) ? mode : ViewMode.TEXT;
            this.viewMode = target;
            this.currentSelection = null;
            this.currentXmlSelection = null;
            this.currentJsonSelection = null;
            if (target == ViewMode.TEXT) {
                showOnly(view.getNode());
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
            // JSON offers a read-only Tree view (no XSD model / commands).
            if (document.getFileType() == EditorFileType.JSON) {
                ensureJsonTree();
                renderJsonTree();
                showOnly(jsonTreeView);
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
            if (editorContext == null || !java.util.Objects.equals(view.getText(), lastParsedText)) {
                parseModel();
            }
        }

        private void parseModel() {
            try {
                var factory = new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory();
                java.nio.file.Path path = document.getPath();
                // Resolve xs:include / xs:import relative to the file's directory when on disk, so
                // the Type Library and facet/type resolution see externally defined types. The
                // included nodes are tagged isFromInclude (preserveIncludeStructure) and are
                // excluded again on round-trip so the include structure is never flattened.
                org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = path != null
                        ? factory.fromStringWithSchemaFile(view.getText(), path, path.getParent())
                        : factory.fromString(view.getText());
                editorContext = new org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext(schema);
                lastParsedText = view.getText();
                // A new context means a new schema: ensureCanvasGraphic() rebuilds the
                // Graphic view (it compares getEditorContext() and discards the stale one).
            } catch (Exception e) {
                editorContext = null;
                lastParsedText = null;
            }
        }

        /**
         * (Re)parses the shared XML-instance model, only when missing or when the text changed since
         * the last parse — so the model, its undo history and the current selection survive view
         * switches (the XML counterpart of {@link #ensureModelParsed()}).
         */
        private void ensureXmlModelParsed() {
            if (xmlEditorContext == null || !java.util.Objects.equals(view.getText(), lastParsedXmlText)) {
                parseXmlModel();
            }
        }

        private void parseXmlModel() {
            String text = view.getText();
            if (text == null || text.isBlank()) {
                xmlEditorContext = null;
                lastParsedXmlText = null;
                return;
            }
            try {
                // Use the line-aware streaming parser so XmlElement.getSourceLineNumber() is
                // populated — the Text view maps the caret line to a model node from it.
                var doc = new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization
                        .StreamingXmlParser().parse(text);
                var ctx = new org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext(doc);
                if (xmlSchemaProvider != null) {
                    ctx.setSchemaProvider(xmlSchemaProvider);
                }
                xmlEditorContext = ctx;
                lastParsedXmlText = text;
            } catch (Exception e) {
                xmlEditorContext = null;
                lastParsedXmlText = null;
            }
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
                lastParsedXmlText = current; // already in sync
                return;
            }
            view.replaceTextRegion(region[0], region[1], xml.substring(region[0], region[2]));
            lastParsedXmlText = view.getText();
            document.setDirty(true);
        }

        /** Current text the {@link #caretMapDoc} was parsed from (line numbers match THIS text). */
        private String caretMapText;
        /** Transient line-aware parse of the CURRENT text, used only to map a caret line to a node path. */
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
            ensureXmlModelParsed();
            org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode resolved = null;
            if (xmlEditorContext != null && xmlEditorContext.getDocument() != null) {
                String text = view.getText();
                if (!java.util.Objects.equals(text, caretMapText)) {
                    try {
                        caretMapDoc = new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization
                                .StreamingXmlParser().parse(text);
                    } catch (Exception e) {
                        caretMapDoc = null;
                    }
                    caretMapText = text;
                }
                if (caretMapDoc != null && caretMapDoc.getRootElement() != null) {
                    int line = view.getCodeArea().getCurrentParagraph() + 1;
                    var target = findElementByLine(caretMapDoc.getRootElement(), line);
                    java.util.List<Integer> path = indexPath(caretMapDoc.getRootElement(), target);
                    resolved = navigate(xmlEditorContext.getDocument().getRootElement(), path);
                }
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
            ensureModelParsed();
            XsdNode resolved = null;
            if (editorContext != null && editorContext.getSchema() != null) {
                String text = view.getText();
                if (!java.util.Objects.equals(text, caretMapText)) {
                    try {
                        caretMapDoc = new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization
                                .StreamingXmlParser().parse(text);
                    } catch (Exception e) {
                        caretMapDoc = null;
                    }
                    caretMapText = text;
                }
                if (caretMapDoc != null && caretMapDoc.getRootElement() != null) {
                    int line = view.getCodeArea().getCurrentParagraph() + 1;
                    var target = findElementByLine(caretMapDoc.getRootElement(), line);
                    java.util.List<Integer> path = xsdSourceIndexPath(caretMapDoc.getRootElement(), target);
                    resolved = navigateXsd(editorContext.getSchema(), path);
                }
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
                treeView.setXsdFromText(view.getText());
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
            if (parent == null || name == null || name.isBlank()) {
                return false;
            }
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddElementCommand(parent, name.trim()));
        }

        boolean addComment(XsdNode parent, String content) {
            if (parent == null || content == null || content.isBlank()) {
                return false;
            }
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddCommentCommand(parent, content.trim()));
        }

        boolean addContainerElement(XsdNode parent, String name) {
            if (parent == null || name == null || name.isBlank()) {
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

        boolean addAttribute(XsdNode parent, String name) {
            if (parent == null || name == null || name.isBlank()) {
                return false;
            }
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddAttributeCommand(parent, name.trim()));
        }

        boolean addSequence(XsdNode node) {
            return node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element
                    && executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSequenceCommand(element));
        }

        boolean addChoice(XsdNode node) {
            return node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element
                    && executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddChoiceCommand(element));
        }

        boolean addAll(XsdNode node) {
            return node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element
                    && executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddAllCommand(element));
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
            if (target == null || !clipboard.hasContent()) {
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
            return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeTypeCommand(
                    editorContext, node, newType.trim()));
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
            if (editorContext == null || currentSelection == null || type == null || value == null) {
                return false;
            }
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
                return;
            }
            view.replaceTextRegion(region[0], region[1], xml.substring(region[0], region[2]));
            // The text now mirrors the model, so it must not look like an external edit (P2).
            lastParsedText = view.getText();
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

        private void ensureXmlInstanceTree() {
            if (xmlInstanceTreeView == null) {
                xmlInstanceTreeView = new org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView();
                xmlInstanceTreeView.setOnSelectionChanged(node -> {
                    currentXmlSelection = node;
                    selectionCallback.run();
                });
                contentStack.getChildren().add(xmlInstanceTreeView);
            }
        }

        /** Parses the current JSON text into the tree; an invalid document shows an empty tree. */
        private void renderJsonTree() {
            try {
                jsonTreeView.setDocument(
                        org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory.parse(view.getText()));
            } catch (Exception e) {
                jsonTreeView.setDocument(new org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument());
            }
        }

        private void showOnly(javafx.scene.Node node) {
            for (javafx.scene.Node child : contentStack.getChildren()) {
                boolean visible = child == node;
                child.setVisible(visible);
                child.setManaged(visible);
            }
        }

        void attachDirtyTracking() {
            if (dirtyTrackingAttached) {
                return;
            }
            dirtyTrackingAttached = true;
            view.getCodeArea().textProperty().addListener((obs, oldV, newV) -> document.setDirty(true));
        }

        void refreshIcon() {
            setGraphic(new IconifyIcon(document.getFileType().icon()));
        }
    }
}
