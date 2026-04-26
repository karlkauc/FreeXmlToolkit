package org.fxt.freexmltoolkit.debugger.ui;

import java.util.function.BiConsumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Table listing every active breakpoint. Each row offers an enable toggle
 * and a delete button. Double-clicking jumps the editor to the breakpoint line.
 */
public class BreakpointsPanel extends TableView<Breakpoint> {

    private final ObservableList<Breakpoint> data = FXCollections.observableArrayList();
    private DebugSession session;
    private Runnable onChanged;
    private BiConsumer<String, Integer> onJump;

    @SuppressWarnings("unchecked")
    public BreakpointsPanel() {
        TableColumn<Breakpoint, String> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().lineNumber())));
        lineCol.setPrefWidth(60);

        TableColumn<Breakpoint, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().filePath().isEmpty() ? "<unsaved>" : shortFilePath(c.getValue().filePath())));
        fileCol.setPrefWidth(180);

        TableColumn<Breakpoint, Boolean> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setPrefWidth(70);
        enabledCol.setCellFactory(col -> new TableCell<>() {
            private final CheckBox box = new CheckBox();
            {
                box.selectedProperty().addListener((obs, was, is) -> {
                    Breakpoint bp = getTableView().getItems().get(getIndex());
                    Breakpoint updated = bp.withEnabled(is);
                    data.set(getIndex(), updated);
                    if (session != null) {
                        session.removeBreakpoint(bp.filePath(), bp.lineNumber());
                        session.addBreakpoint(updated);
                    }
                    if (onChanged != null) onChanged.run();
                });
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Breakpoint bp = getTableView().getItems().get(getIndex());
                box.setSelected(bp.enabled());
                setGraphic(box);
            }
        });

        TableColumn<Breakpoint, Void> deleteCol = new TableColumn<>("");
        deleteCol.setPrefWidth(36);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            {
                FontIcon fi = new FontIcon("bi-trash");
                fi.setIconSize(12);
                btn.setGraphic(fi);
                btn.getStyleClass().add("btn-flat");
                btn.setOnAction(e -> {
                    Breakpoint bp = getTableView().getItems().get(getIndex());
                    if (session != null) session.removeBreakpoint(bp.filePath(), bp.lineNumber());
                    data.remove(bp);
                    if (onChanged != null) onChanged.run();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        getColumns().addAll(lineCol, fileCol, enabledCol, deleteCol);
        setItems(data);
        setPlaceholder(new javafx.scene.control.Label("No breakpoints set. Click the gutter to add one."));
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2 && onJump != null) {
                Breakpoint bp = getSelectionModel().getSelectedItem();
                if (bp != null) onJump.accept(bp.filePath(), bp.lineNumber());
            }
        });
    }

    public void bind(DebugSession session) {
        this.session = session;
    }

    public void setOnChanged(Runnable handler) {
        this.onChanged = handler;
    }

    public void setOnJump(BiConsumer<String, Integer> handler) {
        this.onJump = handler;
    }

    /** Replaces all rows from the session. Safe from any thread. */
    public void refresh() {
        Runnable r = () -> {
            data.clear();
            if (session != null) data.addAll(session.getBreakpoints());
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private static String shortFilePath(String full) {
        int slash = Math.max(full.lastIndexOf('/'), full.lastIndexOf('\\'));
        return slash >= 0 ? full.substring(slash + 1) : full;
    }
}
