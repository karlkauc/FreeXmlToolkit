package org.fxt.freexmltoolkit.controls.shell.editor;

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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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

    public EditorHost() {
        getStyleClass().add("fxt-editor-tabs");
        setCenter(tabPane);
        setupDragAndDrop();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT instanceof EditorTab et) {
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

    /** Creates an empty untitled document of the given type. */
    public OpenDocument newDocument(EditorFileType type) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(untitledName(), type), this::refreshSelectedNode);
        addTab(tab);
        tab.attachDirtyTracking();
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
        private org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext editorContext;
        private XsdNode currentSelection;
        private ViewMode viewMode = ViewMode.TEXT;
        private boolean dirtyTrackingAttached;
        private File schemaFile;

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

        /** Structured views (Tree/Graphic) currently apply to XSD only. */
        boolean supportsStructuredViews() {
            return document.getFileType() == EditorFileType.XSD;
        }

        void setViewMode(ViewMode mode) {
            ViewMode previous = this.viewMode;
            ViewMode target = (mode != ViewMode.TEXT && !supportsStructuredViews()) ? ViewMode.TEXT : mode;
            this.viewMode = target;
            this.currentSelection = null;
            // Build/refresh the model when entering a structured view from Text;
            // keep it (with its undo history) when switching Tree <-> Graphic.
            if (target != ViewMode.TEXT && (previous == ViewMode.TEXT || editorContext == null)) {
                parseModel();
            }
            switch (target) {
                case TEXT -> showOnly(view.getNode());
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
                treeView.setEditActions(new org.fxt.freexmltoolkit.controls.shell.schema.NodeEditActions() {
                    @Override
                    public void addElement(XsdNode parent, String name) {
                        if (EditorTab.this.addElement(parent, name)) {
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
                });
                contentStack.getChildren().add(treeView);
            }
        }

        private void ensureGraphic() {
            if (graphicView == null) {
                graphicView = new org.fxt.freexmltoolkit.controls.shell.schema.XsdGraphicView(node -> {
                    currentSelection = node;
                    selectionCallback.run();
                });
                contentStack.getChildren().add(graphicView);
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
