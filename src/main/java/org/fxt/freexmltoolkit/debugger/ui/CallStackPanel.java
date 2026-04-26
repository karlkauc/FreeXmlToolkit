package org.fxt.freexmltoolkit.debugger.ui;

import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import org.fxt.freexmltoolkit.debugger.DebugStackFrame;

/**
 * List of stack frames currently active at a pause point. Double-click a
 * frame to ask the controller to scroll the editor to that source line.
 */
public class CallStackPanel extends ListView<DebugStackFrame> {

    private final ObservableList<DebugStackFrame> data = FXCollections.observableArrayList();
    private Consumer<DebugStackFrame> frameDoubleClick;

    public CallStackPanel() {
        setItems(data);
        setPlaceholder(new javafx.scene.control.Label("Call stack appears at pause."));
        setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DebugStackFrame item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String loc = item.systemId().isEmpty() ? "" : item.systemId();
                    setText(item.description() + " (" + loc + ":" + item.lineNumber() + ")");
                }
            }
        });
        setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2 && frameDoubleClick != null) {
                DebugStackFrame sel = getSelectionModel().getSelectedItem();
                if (sel != null) frameDoubleClick.accept(sel);
            }
        });
    }

    public void setFrameDoubleClick(Consumer<DebugStackFrame> handler) {
        this.frameDoubleClick = handler;
    }

    public void setFrames(List<DebugStackFrame> frames) {
        Runnable r = () -> {
            data.clear();
            if (frames != null) data.addAll(frames);
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public void clear() {
        setFrames(List.of());
    }
}
