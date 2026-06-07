package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.DebugStackFrame;

/** Read-only call stack list; double-clicking a frame jumps the editor to its line. */
public class CallStackView extends VBox {

    private final ListView<DebugStackFrame> list = new ListView<>();
    private IntConsumer onJumpToLine = line -> { };

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
                setText(empty || frame == null ? null
                        : frame.description() + "  (line " + frame.lineNumber() + ")");
            }
        });
        list.setPlaceholder(new Label("Not paused."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DebugStackFrame f = list.getSelectionModel().getSelectedItem();
                if (f != null) {
                    onJumpToLine.accept(f.lineNumber());
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

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }
}
