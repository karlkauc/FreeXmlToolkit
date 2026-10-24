package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.VariableBinding;

/**
 * Read-only table of the variables in scope at the current pause point. Shown as the
 * "Variables" tab of the DEBUG pane; the tab header is its title. A click on a value (or Enter,
 * or the context menu's Open Value…) asks the host to open the full value
 * ({@link #setOnOpenValue}); Copy Value asks it to copy the full value ({@link #setOnCopyValue}).
 */
public class VariablesView extends VBox {

    private final TableView<VariableBinding> table = new TableView<>();
    /** Names the selected outer call stack frame; hidden while the innermost frame is shown. */
    private final Label frameInfo = new Label();
    private final Label placeholder = new Label("Not paused.");
    private Consumer<VariableBinding> onOpenValue = b -> { };
    private Consumer<VariableBinding> onCopyValue = b -> { };
    private Consumer<VariableBinding> onAddToWatches = b -> { };

    public VariablesView() {
        getStyleClass().add("fxt-debug-panel");

        table.getColumns().add(DebugTableColumns.col("Name", b -> b.name(), 110));
        table.getColumns().add(DebugTableColumns.valueColumn("Value", b -> b.value(), -1,
                b -> onOpenValue.accept(b)));
        table.getColumns().add(DebugTableColumns.col("Type", b -> b.type(), 100));
        table.getColumns().add(DebugTableColumns.col("Scope", b -> b.scope().name(), 70));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(placeholder);
        frameInfo.getStyleClass().add("fxt-debug-frame-info");
        frameInfo.setMaxWidth(Double.MAX_VALUE);
        frameInfo.setVisible(false);
        frameInfo.setManaged(false);
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                VariableBinding b = table.getSelectionModel().getSelectedItem();
                if (b != null) {
                    onOpenValue.accept(b);
                    e.consume();
                }
            }
        });
        ContextMenu menu = DebugValueMenu.create(
                () -> table.getSelectionModel().getSelectedItem(), b -> onOpenValue.accept(b),
                b -> onCopyValue.accept(b), b -> onAddToWatches.accept(b), VariablesView::isWatchable);
        table.setContextMenu(menu);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(frameInfo, table);
    }

    public void setVariables(List<VariableBinding> variables) {
        table.getItems().setAll(variables == null ? List.of() : variables);
    }

    public void clear() {
        table.getItems().clear();
        placeholder.setText("Not paused.");
        setFrameInfo(null);
    }

    /**
     * Names the call stack frame whose variables are shown; {@code null} hides the banner
     * (the innermost frame, i.e. the pause itself).
     */
    public void setFrameInfo(String text) {
        boolean show = text != null && !text.isBlank();
        frameInfo.setText(show ? text : "");
        frameInfo.setVisible(show);
        frameInfo.setManaged(show);
    }

    /** @return the frame banner text, or {@code null} while hidden. */
    public String getFrameInfo() {
        return frameInfo.isVisible() ? frameInfo.getText() : null;
    }

    /** Text shown when the table has no rows. */
    public void setPlaceholderText(String text) {
        placeholder.setText(text);
    }

    /** @return the name of the row at {@code index} (tests). */
    public String getNameAt(int index) {
        return table.getItems().get(index).name();
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    /** Invoked with the row whose full value should be opened (click on a value, Enter, menu). */
    public void setOnOpenValue(Consumer<VariableBinding> handler) {
        this.onOpenValue = handler == null ? b -> { } : handler;
    }

    /** Invoked with the row whose full value should be copied (context menu). */
    public void setOnCopyValue(Consumer<VariableBinding> handler) {
        this.onCopyValue = handler == null ? b -> { } : handler;
    }

    /** Invoked with the row to add as a watch expression (context menu "Add to Watches"). */
    public void setOnAddToWatches(Consumer<VariableBinding> handler) {
        this.onAddToWatches = handler == null ? b -> { } : handler;
    }

    /**
     * @return whether the row's name is a valid watch expression: {@code .}, {@code $name},
     *         {@code position()}, {@code last()} — not an unnamed slot ({@code $$slot3})
     */
    static boolean isWatchable(VariableBinding b) {
        String n = b == null ? null : b.name();
        return n != null && (n.equals(".") || n.equals("position()") || n.equals("last()")
                || (n.startsWith("$") && !n.startsWith("$$") && n.length() > 1));
    }

    /** Adds row {@code index} as a watch, as the context menu would (tests). */
    void fireAddToWatches(int index) {
        onAddToWatches.accept(table.getItems().get(index));
    }

    /** Opens the value of row {@code index} as a click would (tests). */
    void fireOpen(int index) {
        onOpenValue.accept(table.getItems().get(index));
    }
}
