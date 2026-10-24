package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.VariableBinding;

/**
 * Read-only table of the variables in scope at the current pause point. Shown as the
 * "Variables" tab of the DEBUG pane; the tab header is its title.
 */
public class VariablesView extends VBox {

    private final TableView<VariableBinding> table = new TableView<>();

    public VariablesView() {
        getStyleClass().add("fxt-debug-panel");

        table.getColumns().add(DebugTableColumns.col("Name", b -> b.name(), 110));
        table.getColumns().add(DebugTableColumns.colWithTooltip("Value", b -> b.value(), -1));
        table.getColumns().add(DebugTableColumns.col("Type", b -> b.type(), 100));
        table.getColumns().add(DebugTableColumns.col("Scope", b -> b.scope().name(), 70));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Not paused."));
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
}
