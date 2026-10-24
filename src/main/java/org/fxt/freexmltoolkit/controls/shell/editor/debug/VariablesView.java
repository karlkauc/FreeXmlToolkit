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
    private Consumer<VariableBinding> onOpenValue = b -> { };
    private Consumer<VariableBinding> onCopyValue = b -> { };

    public VariablesView() {
        getStyleClass().add("fxt-debug-panel");

        table.getColumns().add(DebugTableColumns.col("Name", b -> b.name(), 110));
        table.getColumns().add(DebugTableColumns.valueColumn("Value", b -> b.value(), -1,
                b -> onOpenValue.accept(b)));
        table.getColumns().add(DebugTableColumns.col("Type", b -> b.type(), 100));
        table.getColumns().add(DebugTableColumns.col("Scope", b -> b.scope().name(), 70));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Not paused."));
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
                b -> onCopyValue.accept(b));
        table.setContextMenu(menu);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(table);
    }

    public void setVariables(List<VariableBinding> variables) {
        table.getItems().setAll(variables == null ? List.of() : variables);
    }

    public void clear() {
        table.getItems().clear();
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

    /** Opens the value of row {@code index} as a click would (tests). */
    void fireOpen(int index) {
        onOpenValue.accept(table.getItems().get(index));
    }
}
