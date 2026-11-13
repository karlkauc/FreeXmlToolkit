package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlWindow;

/**
 * The Graphic tab of the {@link LargeFileView}: one {@link LargeXmlWindow} of the file, loaded into
 * memory and shown in the grid ({@link XmlGridView}), where it can be edited with undo/redo. Apply to
 * File writes the edited window back into the file; Discard drops the edits. Previous / Next move to
 * the neighbouring run of children.
 */
final class LargeFileWindowPane extends VBox {

    /** What the pane asks the viewer to do. */
    interface Host {
        void applyWindow();

        void discardWindow();

        void previousWindow();

        void nextWindow();
    }

    @FXML private Label windowLabel;
    @FXML private Label dirtyLabel;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private Button applyButton;
    @FXML private Button discardButton;
    @FXML private StackPane gridSlot;
    @FXML private Label messageLabel;

    private final Host host;
    private final XmlGridView grid = new XmlGridView();
    private final BooleanProperty dirty = new SimpleBooleanProperty();
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private XmlEditorContext context;
    private LargeXmlWindow window;
    private boolean hasPrevious;
    private boolean hasNext;

    LargeFileWindowPane(Host host) {
        this.host = host;
        FxmlViews.load(this, "/pages/views/LargeFileWindowPane.fxml");
        grid.setId("large-file-grid");
        grid.setVisible(false);
        gridSlot.getChildren().add(grid);
        grid.setOnModelChanged(this::modelChanged);
        dirtyLabel.visibleProperty().bind(dirty);
        dirtyLabel.managedProperty().bind(dirty);
        applyButton.disableProperty().bind(dirty.not().or(busy));
        discardButton.disableProperty().bind(dirty.not().or(busy));
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() instanceof javafx.scene.control.TextInputControl) {
                return; // an inline editor keeps its own undo
            }
            if (e.isShortcutDown() && e.getCode() == KeyCode.Z && !e.isShiftDown()) {
                onUndo();
                e.consume();
            } else if (e.isShortcutDown() && (e.getCode() == KeyCode.Y || (e.getCode() == KeyCode.Z && e.isShiftDown()))) {
                onRedo();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.S) {
                if (dirty.get() && !busy.get()) {
                    host.applyWindow();
                }
                e.consume();
            }
        });
        updateButtons();
    }

    /** Shows a message instead of the grid (nothing chosen yet, loading, an error). */
    void showMessage(String text) {
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        grid.setVisible(false);
    }

    /**
     * Shows a window.
     *
     * @param context the window's document, already parsed
     */
    void show(LargeXmlWindow window, XmlEditorContext context, String label, boolean hasPrevious, boolean hasNext) {
        this.window = window;
        this.context = context;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
        windowLabel.setText(label);
        dirty.set(false);
        grid.setContext(context);
        if (grid.getCanvasView() != null) {
            grid.getCanvasView().expandAll(); // the run of children opens as a table
        }
        grid.setVisible(true);
        messageLabel.setVisible(false);
        updateButtons();
    }

    /** Forgets the window (after the file changed). */
    void clear(String message) {
        window = null;
        context = null;
        dirty.set(false);
        windowLabel.setText("");
        showMessage(message);
        updateButtons();
    }

    private void modelChanged() {
        dirty.set(context != null && context.canUndo());
        updateButtons();
    }

    private void updateButtons() {
        boolean none = context == null;
        previousButton.setDisable(none || !hasPrevious || busy.get());
        nextButton.setDisable(none || !hasNext || busy.get());
        undoButton.setDisable(none || !context.canUndo() || busy.get());
        redoButton.setDisable(none || !context.canRedo() || busy.get());
    }

    @FXML
    public void onUndo() {
        if (context != null && context.canUndo() && !busy.get()) {
            context.undo();
            refreshGrid();
        }
    }

    @FXML
    public void onRedo() {
        if (context != null && context.canRedo() && !busy.get()) {
            context.redo();
            refreshGrid();
        }
    }

    private void refreshGrid() {
        if (grid.getCanvasView() != null) {
            grid.getCanvasView().rebuild();
        }
        modelChanged();
    }

    @FXML
    public void onApply() {
        host.applyWindow();
    }

    @FXML
    public void onDiscard() {
        host.discardWindow();
    }

    @FXML
    public void onPrevious() {
        host.previousWindow();
    }

    @FXML
    public void onNext() {
        host.nextWindow();
    }

    /** Disables the actions while the window is written. */
    void setBusy(boolean value) {
        busy.set(value);
        grid.setDisable(value);
        updateButtons();
    }

    /** @return whether the window has edits that are not written yet */
    boolean isDirty() {
        return dirty.get();
    }

    /** @return the window shown, or {@code null} */
    LargeXmlWindow window() {
        return window;
    }

    /** @return the edited document (FX thread: the model is not thread-safe) */
    String editedText() {
        return context == null ? null : context.serializeToString();
    }

    // test access
    XmlEditorContext contextForTesting() {
        return context;
    }

    XmlGridView gridForTesting() {
        return grid;
    }

    String labelForTesting() {
        return windowLabel.getText();
    }

    String messageForTesting() {
        return messageLabel.isVisible() ? messageLabel.getText() : null;
    }

    /** Marks the window as edited after a test changed the model directly. */
    void modelChangedForTesting() {
        modelChanged();
    }
}
