package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The file-type-aware center of the Unified shell: a tab pane of open documents,
 * each edited in a reused {@link XmlCodeEditorV2} (RichTextFX) text view.
 * <p>
 * UI rebuild Phase 3 (Text view). Reuses the existing editor widget and schema
 * provider rather than building a new editor. File reads run on the shared
 * executor and the UI thread is never blocked; the active document and the open
 * document list are observable for the Explorer panel and inspector.
 */
public class EditorHost extends BorderPane {

    private final TabPane tabPane = new TabPane();
    private final ObservableList<OpenDocument> openDocuments = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper activeCaret = new ReadOnlyIntegerWrapper(this, "activeCaret", 0);
    private final ReadOnlyObjectWrapper<File> activeSchema = new ReadOnlyObjectWrapper<>(this, "activeSchema", null);

    public EditorHost() {
        getStyleClass().add("fxt-editor-tabs");
        setCenter(tabPane);
        setupDragAndDrop();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT instanceof EditorTab et) {
                activeCaret.set(et.editor.getCodeArea().getCaretPosition());
                activeSchema.set(et.schemaFile);
            } else {
                activeSchema.set(null);
            }
        });
    }

    /**
     * @return the caret offset of the active editor; changes on caret movement
     * (incl. typing) and on tab switches, so observers (e.g. the inspector) can
     * react. The text is fetched on demand via {@link #getActiveText()}.
     */
    public ReadOnlyIntegerProperty activeCaretProperty() {
        return activeCaret.getReadOnlyProperty();
    }

    /** @return the XSD currently bound to the active document for IntelliSense, or {@code null}. */
    public ReadOnlyObjectProperty<File> activeSchemaProperty() {
        return activeSchema.getReadOnlyProperty();
    }

    /**
     * Binds an XSD to the active document for schema-aware IntelliSense.
     *
     * @return {@code true} if the schema loaded successfully
     */
    public boolean setSchemaForActiveDocument(File xsd) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.schemaProvider.loadSchema(xsd)) {
            et.schemaFile = xsd;
            et.editor.invalidateIntelliSenseCache();
            activeSchema.set(xsd);
            return true;
        }
        return false;
    }

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
        return tab instanceof EditorTab et ? Optional.of(et.editor.getText()) : Optional.empty();
    }

    /** @return the selected-tab property (for observers). */
    public ReadOnlyObjectProperty<Tab> activeTabProperty() {
        return tabPane.getSelectionModel().selectedItemProperty();
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

    /** Moves the active editor's caret to the given offset (e.g. jump-to-node). */
    public void moveActiveCaretTo(int position) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            var codeArea = et.editor.getCodeArea();
            codeArea.moveTo(Math.max(0, Math.min(position, codeArea.getLength())));
        }
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
     * Opens {@code path} in a new tab (or focuses it if already open) and loads
     * its content asynchronously.
     */
    public void openFile(Path path) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab et && path.equals(et.document.getPath())) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
        EditorTab tab = new EditorTab(OpenDocument.forPath(path));
        addTab(tab);
        loadAsync(tab, path);
    }

    /** Creates an empty untitled document of the given type. */
    public OpenDocument newDocument(EditorFileType type) {
        EditorTab tab = new EditorTab(OpenDocument.untitled(untitledName(), type));
        addTab(tab);
        tab.attachDirtyTracking();
        return tab.document;
    }

    /**
     * Saves the active document to its current path (must be titled).
     *
     * @return {@code true} on success
     */
    public boolean saveActive() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && !et.document.isUntitled()) {
            return write(et, et.document.getPath());
        }
        return false;
    }

    /** Saves the active document to {@code target} (Save As). */
    public boolean saveActiveAs(Path target) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et) {
            if (write(et, target)) {
                et.document.setPath(target);
                et.refreshIcon();
                return true;
            }
        }
        return false;
    }

    private void addTab(EditorTab tab) {
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        openDocuments.add(tab.document);
        tab.setOnClosed(e -> openDocuments.remove(tab.document));
        tab.editor.getCodeArea().caretPositionProperty().addListener((obs, oldV, newV) -> {
            if (tab.isSelected()) {
                activeCaret.set(newV.intValue());
            }
        });
    }

    private void loadAsync(EditorTab tab, Path path) {
        FxtGui.executorService.submit(() -> {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                // Best-effort: resolve + parse a locally referenced XSD off the UI thread.
                File autoXsd = SchemaLocationResolver.resolveLocalXsd(content, path)
                        .filter(p -> tab.schemaProvider.loadSchema(p.toFile()))
                        .map(java.nio.file.Path::toFile)
                        .orElse(null);
                Platform.runLater(() -> {
                    tab.editor.setText(content);
                    tab.document.setDirty(false);
                    tab.attachDirtyTracking();
                    if (autoXsd != null) {
                        tab.schemaFile = autoXsd;
                        tab.editor.invalidateIntelliSenseCache();
                        if (tab.isSelected()) {
                            activeSchema.set(autoXsd);
                        }
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> tab.editor.setText("<!-- Could not read " + path + ": " + e.getMessage() + " -->"));
            }
        });
    }

    private boolean write(EditorTab tab, Path target) {
        try {
            Files.writeString(target, tab.editor.getText(), StandardCharsets.UTF_8);
            tab.document.setDirty(false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String untitledName() {
        int n = openDocuments.size() + 1;
        return "Untitled " + n;
    }

    /** A tab bound to one {@link OpenDocument} and its reused text editor. */
    private static final class EditorTab extends Tab {
        private final OpenDocument document;
        private final MutableXmlSchemaProvider schemaProvider = new MutableXmlSchemaProvider();
        private final XmlCodeEditorV2 editor;
        private boolean dirtyTrackingAttached;
        private File schemaFile;

        EditorTab(OpenDocument document) {
            this.document = document;
            this.editor = new XmlCodeEditorV2(schemaProvider);
            setContent(editor);
            textProperty().bind(Bindings.createStringBinding(
                    () -> (document.isDirty() ? "● " : "") + document.getDisplayName(),
                    document.dirtyProperty(), document.displayNameProperty()));
            refreshIcon();
        }

        void attachDirtyTracking() {
            if (dirtyTrackingAttached) {
                return;
            }
            dirtyTrackingAttached = true;
            editor.getCodeArea().textProperty().addListener((obs, oldV, newV) -> document.setDirty(true));
        }

        void refreshIcon() {
            setGraphic(new IconifyIcon(document.getFileType().icon()));
        }
    }
}
