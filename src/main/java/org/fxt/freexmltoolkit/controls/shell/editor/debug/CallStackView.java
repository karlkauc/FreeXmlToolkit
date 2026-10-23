package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.DebugStackFrame;

/**
 * Read-only call stack list (innermost frame first). Template and function frames are
 * shown in full; the instructions nested inside them are dimmed. Double-clicking a frame
 * jumps the editor to its module and line.
 */
public class CallStackView extends VBox {

    private final ListView<DebugStackFrame> list = new ListView<>();
    private IntConsumer onJumpToLine = line -> { };
    private Consumer<DebugStackFrame> onJumpToFrame = frame -> { };

    public CallStackView() {
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("CALL STACK");
        title.getStyleClass().add("fxt-side-panel-title");

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
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, list);
    }

    public void setFrames(List<DebugStackFrame> frames) {
        list.getItems().setAll(frames == null ? List.of() : frames);
    }

    public void clear() {
        list.getItems().clear();
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
