package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;

/**
 * Lists the session's breakpoints with Remove / Clear all; double-click jumps to the line,
 * {@code Delete} removes the selected one. Shown as the "Breakpoints" tab of the DEBUG pane;
 * {@link #setOnCountChanged} feeds the tab's badge.
 */
public class BreakpointsView extends VBox {

    private final ListView<Breakpoint> list = new ListView<>();
    private final DebugSession session;
    private IntConsumer onJumpToLine = line -> { };
    private java.util.function.Consumer<Breakpoint> onJumpToBreakpoint = bp -> { };
    private Runnable onChanged = () -> { };
    private Runnable onCountChanged = () -> { };

    public BreakpointsView(DebugSession session) {
        this.session = session;
        getStyleClass().add("fxt-debug-panel");

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
                    onJumpToBreakpoint.accept(bp);
                }
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
                e.consume();
            }
        });

        Button remove = DebugToolbar.action("Remove", "bi-dash-lg",
                "Remove the selected breakpoint (Delete)");
        remove.setOnAction(e -> removeSelected());
        Button clear = DebugToolbar.action("Clear all", "bi-trash",
                "Remove every breakpoint of the session");
        clear.setOnAction(e -> {
            session.clearBreakpoints();
            refresh();
            onChanged.run();
        });

        HBox toolbar = new HBox(remove, clear);
        toolbar.getStyleClass().add("fxt-debug-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(toolbar, list);
        refresh();
    }

    /** Re-reads the breakpoints from the session into the list. */
    public final void refresh() {
        List<Breakpoint> sorted = new ArrayList<>(session.getBreakpoints());
        sorted.sort(java.util.Comparator.comparing(Breakpoint::filePath)
                .thenComparingInt(Breakpoint::lineNumber));
        list.getItems().setAll(sorted);
        onCountChanged.run();
    }

    /** Removes the selected breakpoint from the session, if any (Remove button, Delete key). */
    public void removeSelected() {
        Breakpoint bp = list.getSelectionModel().getSelectedItem();
        if (bp != null) {
            session.removeBreakpoint(bp.filePath(), bp.lineNumber());
            refresh();
            onChanged.run();
        }
    }

    /** Selects the breakpoint at {@code index} (tests, keyboard flows). */
    public void select(int index) {
        list.getSelectionModel().clearAndSelect(index);
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }

    /** Breakpoint jump handler: receives the breakpoint so its own module can be opened. */
    public void setOnJumpToBreakpoint(java.util.function.Consumer<Breakpoint> handler) {
        this.onJumpToBreakpoint = handler == null ? bp -> { } : handler;
    }

    /** Invoked after the user removes/clears breakpoints (so the gutter can refresh). */
    public void setOnChanged(Runnable handler) {
        this.onChanged = handler == null ? () -> { } : handler;
    }

    /** Invoked after every change of the row count (tab badge). */
    public void setOnCountChanged(Runnable handler) {
        this.onCountChanged = handler == null ? () -> { } : handler;
    }

    /** The list, for tests that drive it with key events. */
    ListView<Breakpoint> list() {
        return list;
    }

    private static String shortName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
