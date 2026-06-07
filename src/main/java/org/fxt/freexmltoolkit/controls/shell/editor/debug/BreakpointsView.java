package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;

/** Lists the session's breakpoints with remove/clear; double-click jumps to the line. */
public class BreakpointsView extends VBox {

    private final ListView<Breakpoint> list = new ListView<>();
    private final DebugSession session;
    private IntConsumer onJumpToLine = line -> { };
    private Runnable onChanged = () -> { };

    public BreakpointsView(DebugSession session) {
        this.session = session;
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("BREAKPOINTS");
        title.getStyleClass().add("fxt-side-panel-title");

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Breakpoint bp, boolean empty) {
                super.updateItem(bp, empty);
                setText(empty || bp == null ? null : "line " + bp.lineNumber()
                        + (bp.filePath().isEmpty() ? "" : "  " + shortName(bp.filePath())));
            }
        });
        list.setPlaceholder(new Label("No breakpoints."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Breakpoint bp = list.getSelectionModel().getSelectedItem();
                if (bp != null) {
                    onJumpToLine.accept(bp.lineNumber());
                }
            }
        });

        Button remove = new Button("Remove");
        remove.getStyleClass().add("fxt-tool-button");
        remove.setOnAction(e -> {
            Breakpoint bp = list.getSelectionModel().getSelectedItem();
            if (bp != null) {
                session.removeBreakpoint(bp.filePath(), bp.lineNumber());
                refresh();
                onChanged.run();
            }
        });
        Button clear = new Button("Clear all");
        clear.getStyleClass().add("fxt-tool-button");
        clear.setOnAction(e -> {
            session.clearBreakpoints();
            refresh();
            onChanged.run();
        });

        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, list, new HBox(8, remove, clear));
        refresh();
    }

    /** Re-reads the breakpoints from the session into the list. */
    public final void refresh() {
        List<Breakpoint> sorted = new ArrayList<>(session.getBreakpoints());
        sorted.sort(java.util.Comparator.comparingInt(Breakpoint::lineNumber));
        list.getItems().setAll(sorted);
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }

    /** Invoked after the user removes/clears breakpoints (so the gutter can refresh). */
    public void setOnChanged(Runnable handler) {
        this.onChanged = handler == null ? () -> { } : handler;
    }

    private static String shortName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
