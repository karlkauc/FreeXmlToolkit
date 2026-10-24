package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.DebugStackFrame;

/**
 * Read-only call stack list (innermost frame first). Template and function frames are
 * shown in full; the instructions nested inside them are dimmed. Double-clicking a frame
 * jumps the editor to its module and line; Enter or the context menu's Open Value… asks the host
 * to open the frame's context item and variables in the value viewer ({@link #setOnOpenValue}).
 * Selecting a frame switches the Variables table and the watches to that frame
 * ({@link #setOnFrameSelected}). Shown as the "Call Stack" tab of the DEBUG pane.
 */
public class CallStackView extends VBox {

    private final ListView<DebugStackFrame> list = new ListView<>();
    private IntConsumer onJumpToLine = line -> { };
    private Consumer<DebugStackFrame> onJumpToFrame = frame -> { };
    private Consumer<DebugStackFrame> onOpenValue = frame -> { };
    private Consumer<DebugStackFrame> onCopyValue = frame -> { };
    private Consumer<DebugStackFrame> onFrameSelected = frame -> { };

    public CallStackView() {
        getStyleClass().add("fxt-debug-panel");

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(DebugStackFrame frame, boolean empty) {
                super.updateItem(frame, empty);
                getStyleClass().remove("fxt-stack-instruction");
                if (empty || frame == null) {
                    setText(null);
                    return;
                }
                String file = frame.fileName();
                setText(frame.description() + "  (" + (file.isEmpty() ? "" : file + ":") + "line "
                        + frame.lineNumber() + ")");
                if (frame.kind() == DebugStackFrame.FrameKind.INSTRUCTION) {
                    getStyleClass().add("fxt-stack-instruction");
                }
            }
        });
        list.setPlaceholder(new Label("Not paused."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DebugStackFrame f = list.getSelectionModel().getSelectedItem();
                if (f != null) {
                    onJumpToLine.accept(f.lineNumber());
                    onJumpToFrame.accept(f);
                }
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, frame) -> {
            if (frame != null) {
                onFrameSelected.accept(frame);
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                DebugStackFrame f = list.getSelectionModel().getSelectedItem();
                if (f != null) {
                    onOpenValue.accept(f);
                    e.consume();
                }
            }
        });
        javafx.scene.control.ContextMenu menu = DebugValueMenu.create(() -> list.getSelectionModel().getSelectedItem(),
                f -> onOpenValue.accept(f), f -> onCopyValue.accept(f));
        javafx.scene.control.MenuItem explain = new javafx.scene.control.MenuItem("Explain Match…");
        explain.setId("debug-explain-match");
        explain.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-diagram-3"),
                org.fxt.freexmltoolkit.controls.theme.ActionColor.NAVIGATE));
        explain.setOnAction(e -> {
            DebugStackFrame f = list.getSelectionModel().getSelectedItem();
            if (isTemplateRule(f)) {
                onExplainMatch.accept(f);
            }
        });
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        menu.getItems().add(explain);
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING,
                e -> explain.setDisable(!isTemplateRule(list.getSelectionModel().getSelectedItem())));
        list.setContextMenu(menu);
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().add(list);
    }

    private Consumer<DebugStackFrame> onExplainMatch = frame -> { };

    /** Invoked with a template-rule frame for "Explain Match…" (context menu). */
    public void setOnExplainMatch(Consumer<DebugStackFrame> handler) {
        this.onExplainMatch = handler == null ? frame -> { } : handler;
    }

    /** @return whether {@code frame} is a template rule (a frame "Explain Match" works on). */
    static boolean isTemplateRule(DebugStackFrame frame) {
        return frame != null && frame.kind() == DebugStackFrame.FrameKind.TEMPLATE
                && frame.description().startsWith("template match=");
    }

    /** Invoked with the frame whose context item / variables should be opened (Enter, menu). */
    public void setOnOpenValue(Consumer<DebugStackFrame> handler) {
        this.onOpenValue = handler == null ? frame -> { } : handler;
    }

    /** Invoked with the frame whose context item should be copied (context menu). */
    public void setOnCopyValue(Consumer<DebugStackFrame> handler) {
        this.onCopyValue = handler == null ? frame -> { } : handler;
    }

    /** Opens frame {@code index} as Enter would (tests). */
    void fireOpen(int index) {
        onOpenValue.accept(list.getItems().get(index));
    }

    /** Shows the frames of a pause and selects the innermost one (which fires the selection handler). */
    public void setFrames(List<DebugStackFrame> frames) {
        list.getSelectionModel().clearSelection();
        list.getItems().setAll(frames == null ? List.of() : frames);
        if (!list.getItems().isEmpty()) {
            list.getSelectionModel().select(0);
        }
    }

    public void clear() {
        list.getSelectionModel().clearSelection();
        list.getItems().clear();
    }

    /** Invoked whenever a frame is selected (the Variables table follows the selected frame). */
    public void setOnFrameSelected(Consumer<DebugStackFrame> handler) {
        this.onFrameSelected = handler == null ? frame -> { } : handler;
    }

    /** Selects frame {@code index} (tests, keyboard flows). */
    public void selectFrame(int index) {
        list.getSelectionModel().select(index);
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    /** Line-only jump handler (kept for callers that only know the main stylesheet). */
    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }

    /** Frame jump handler: receives the frame so its own module can be opened. */
    public void setOnJumpToFrame(Consumer<DebugStackFrame> handler) {
        this.onJumpToFrame = handler == null ? frame -> { } : handler;
    }
}
