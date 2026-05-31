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

/**
 * The file-type-aware center of the Unified shell: a tab pane of open documents.
 * Each tab embeds an {@link EditorView} chosen by file type (XML family vs JSON),
 * reusing the existing editor widgets. File reads run on the shared executor so
 * the UI thread is never blocked; the active document, caret and schema are
 * observable for the Explorer panel, inspector and status bar.
 */
public class EditorHost extends BorderPane {

    private final TabPane tabPane = new TabPane();
    private final ObservableList<OpenDocument> openDocuments = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper activeCaret = new ReadOnlyIntegerWrapper(this, "activeCaret", 0);
    private final ReadOnlyObjectWrapper<File> activeSchema = new ReadOnlyObjectWrapper<>(this, "activeSchema", null);
    private final ReadOnlyObjectWrapper<ViewMode> activeViewMode =
            new ReadOnlyObjectWrapper<>(this, "activeViewMode", ViewMode.TEXT);
    private final ReadOnlyObjectWrapper<XsdNode> activeSelectedNode =
            new ReadOnlyObjectWrapper<>(this, "activeSelectedNode", null);
    /** The most recently active editor tab — insertion target when a tool tab is in front. */
    private EditorTab lastEditorTab;

    private final EditorWelcomePane welcomePane = new EditorWelcomePane(
            this::newDocument, this::openFileChooser, this::openFile);

    public EditorHost() {
        getStyleClass().add("fxt-editor-tabs");
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

    /** @return the schema root of the active structured view, or empty (Text mode / not parsed). */
    public Optional<XsdNode> getActiveSchemaRoot() {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
            if (et.viewMode == ViewMode.TREE && et.treeView != null && et.treeView.getRoot() != null) {
                return Optional.of(et.treeView.getRoot().getValue());
            }
            if (et.viewMode == ViewMode.GRAPHIC && et.graphicView != null
                    && et.graphicView.getSchemaRoot() != null) {
                return Optional.of(et.graphicView.getSchemaRoot());
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
        return org.fxt.freexmltoolkit.controls.shell.schema.TypeLibrary.collectNamedTypes(schema);
    }

    /** Reveals a named type in the Tree view (switching to Tree if needed). */
    public void revealTypeByName(String typeName) {
        if (typeName == null
                || !(tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
                || !et.supportsStructuredViews()) {
            return;
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

    /** Selects (reveals) the given node in the active structured view (Tree or Graphic). */
    public void selectNodeInActiveTree(XsdNode node) {
        withActive(et -> {
            if (et.viewMode == ViewMode.TREE && et.treeView != null) {
                et.treeView.selectNode(node);
            } else if (et.viewMode == ViewMode.GRAPHIC && et.graphicView != null) {
                et.graphicView.selectNode(node);
            }
        });
    }

    private void refreshSelectedNode() {
        XsdNode selected = null;
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode != ViewMode.TEXT) {
            selected = et.currentSelection;
        }
        activeSelectedNode.set(selected);
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
        EditorTab tab = new EditorTab(OpenDocument.forPath(path), this::refreshSelectedNode);
        addTab(tab);
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
            setCenter(welcomePane);
        } else {
            setCenter(tabPane);
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

    /** Creates an empty untitled document of the given type. */
    public OpenDocument newDocument(EditorFileType type) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(untitledName(), type), this::refreshSelectedNode);
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
        EditorTab tab = new EditorTab(OpenDocument.untitled(displayName, type), this::refreshSelectedNode);
        addTab(tab);
        tab.view.setText(content);
        tab.attachDirtyTracking();
        tab.document.setDirty(true);
        return tab.document;
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

    /** Renames the selected node via the command stack. */
    public boolean renameActiveNode(String newName) {
        return editActive(et -> et.renameNode(et.currentSelection, newName));
    }

    /** Changes the selected node's cardinality via the command stack. */
    public boolean changeActiveCardinality(int min, int max) {
        return editActive(et -> et.changeCardinality(et.currentSelection, min, max));
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

    /** Changes the selected node's type via the command stack. */
    public boolean changeActiveType(String newType) {
        return editActive(et -> et.changeType(et.currentSelection, newType));
    }

    private boolean editActive(java.util.function.Predicate<EditorTab> edit) {
        if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && et.viewMode != ViewMode.TEXT) {
            boolean ok = edit.test(et);
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

    /** Binds an XSD to the active document for schema-aware IntelliSense. @return success */
    public boolean setSchemaForActiveDocument(File xsd) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.view.supportsSchema() && et.view.loadSchema(xsd)) {
            et.schemaFile = xsd;
            et.view.invalidateIntelliSenseCache();
            activeSchema.set(xsd);
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

    /** Focuses the tab backing the given document, if open. */
    public void selectDocument(OpenDocument document) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document == document) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
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
            }
        });
    }

    private void loadAsync(EditorTab tab, Path path) {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                File autoXsd = tab.view.supportsSchema()
                        ? SchemaLocationResolver.resolveLocalXsd(content, path)
                        .filter(p -> tab.view.loadSchema(p.toFile()))
                        .map(Path::toFile).orElse(null)
                        : null;
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
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> tab.view.setText("Could not read " + path + ": " + e.getMessage()));
            }
        });
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
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            boolean done = false;
            if (dragboard.hasFiles()) {
                for (File file : dragboard.getFiles()) {
                    if (file.isFile()) {
                        openFile(file.toPath());
                    }
                }
                done = true;
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
        private final javafx.scene.layout.StackPane contentStack = new javafx.scene.layout.StackPane();
        private org.fxt.freexmltoolkit.controls.shell.schema.XsdTreeView treeView;
        private org.fxt.freexmltoolkit.controls.shell.schema.XsdGraphicView graphicView;
        private org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView jsonTreeView;
        private org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView xmlInstanceTreeView;
        private org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext;
        private XsdNode currentSelection;
        private ViewMode viewMode = ViewMode.TEXT;
        private boolean dirtyTrackingAttached;
        private File schemaFile;
        private File schematronFile;

        EditorTab(OpenDocument document, Runnable selectionCallback) {
            this.document = document;
            this.selectionCallback = selectionCallback;
            this.view = EditorViews.create(document.getFileType());
            contentStack.getChildren().add(view.getNode());
            setContent(contentStack);
            textProperty().bind(Bindings.createStringBinding(
                    () -> (document.isDirty() ? "● " : "") + document.getDisplayName(),
                    document.dirtyProperty(), document.displayNameProperty()));
            refreshIcon();
        }

        /** XSD-backed structured views (Tree/Graphic with the V2 model + commands). */
        boolean supportsStructuredViews() {
            return document.getFileType() == EditorFileType.XSD;
        }

        /**
         * Which view modes this document offers: every recognized type has a Tree
         * (XSD schema tree / JSON tree / XML-instance DOM tree); Graphic is XSD-only.
         */
        boolean supportsView(ViewMode mode) {
            return switch (mode) {
                case TEXT -> true;
                case TREE -> document.getFileType() != EditorFileType.OTHER;
                case GRAPHIC -> document.getFileType() == EditorFileType.XSD;
            };
        }

        void setViewMode(ViewMode mode) {
            ViewMode previous = this.viewMode;
            ViewMode target = supportsView(mode) ? mode : ViewMode.TEXT;
            this.viewMode = target;
            this.currentSelection = null;
            if (target == ViewMode.TEXT) {
                showOnly(view.getNode());
                return;
            }
            // JSON offers a read-only Tree view (no XSD model / commands).
            if (document.getFileType() == EditorFileType.JSON) {
                ensureJsonTree();
                renderJsonTree();
                showOnly(jsonTreeView);
                return;
            }
            // XML instances (incl. XSLT/Schematron) get a read-only DOM tree (Tree only).
            if (document.getFileType() != EditorFileType.XSD) {
                ensureXmlInstanceTree();
                xmlInstanceTreeView.setXml(view.getText());
                showOnly(xmlInstanceTreeView);
                return;
            }
            // XSD structured views: build/refresh the model when entering from Text;
            // keep it (with its undo history) when switching Tree <-> Graphic.
            if (previous == ViewMode.TEXT || editorContext == null) {
                parseModel();
            }
            switch (target) {
                case TREE -> {
                    ensureTree();
                    renderStructured();
                    showOnly(treeView);
                }
                case GRAPHIC -> {
                    ensureGraphic();
                    renderStructured();
                    showOnly(graphicView);
                }
                default -> showOnly(view.getNode());
            }
        }

        private void parseModel() {
            try {
                editorContext = new org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext(
                        new org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory().fromString(view.getText()));
            } catch (Exception e) {
                editorContext = null;
            }
        }

        private void renderStructured() {
            if (editorContext != null) {
                if (viewMode == ViewMode.TREE && treeView != null) {
                    treeView.setSchema(editorContext.getSchema());
                } else if (viewMode == ViewMode.GRAPHIC && graphicView != null) {
                    graphicView.setSchema(editorContext.getSchema());
                }
            } else if (viewMode == ViewMode.TREE && treeView != null) {
                treeView.setXsdFromText(view.getText());
            } else if (viewMode == ViewMode.GRAPHIC && graphicView != null) {
                graphicView.setXsdFromText(view.getText());
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
            if (!(node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element)) {
                return false;
            }
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSequenceCommand(element));
        }

        boolean addChoice(XsdNode node) {
            if (!(node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element)) {
                return false;
            }
            return executeAndApply(
                    new org.fxt.freexmltoolkit.controls.v2.editor.commands.AddChoiceCommand(element));
        }

        boolean changeType(XsdNode node, String newType) {
            if (editorContext == null || node == null || newType == null || newType.isBlank()) {
                return false;
            }
            return executeAndApply(new org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeTypeCommand(
                    editorContext, node, newType.trim()));
        }

        boolean undoStructured() {
            if (editorContext != null && editorContext.getCommandManager().undo()) {
                applyModelChange();
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
            String xml = new org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer()
                    .serialize(editorContext.getSchema());
            view.setText(xml);
            document.setDirty(true);
            currentSelection = null;
            renderStructured();
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

        private void ensureGraphic() {
            if (graphicView == null) {
                graphicView = new org.fxt.freexmltoolkit.controls.shell.schema.XsdGraphicView(node -> {
                    currentSelection = node;
                    selectionCallback.run();
                });
                graphicView.setEditActions(createEditActions()); // Tree-parity editing via right-click
                contentStack.getChildren().add(graphicView);
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
                contentStack.getChildren().add(jsonTreeView);
            }
        }

        private void ensureXmlInstanceTree() {
            if (xmlInstanceTreeView == null) {
                xmlInstanceTreeView = new org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView();
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
