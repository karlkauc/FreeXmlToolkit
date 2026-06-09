package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.VariableBinding;

/** Read-only table of the variables in scope at the current pause point. */
public class VariablesView extends VBox {

    private final TableView<VariableBinding> table = new TableView<>();

    public VariablesView() {
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("VARIABLES");
        title.getStyleClass().add("fxt-side-panel-title");

        table.getColumns().add(DebugTableColumns.col("Name", b -> b.name(), 120));
        table.getColumns().add(DebugTableColumns.col("Value", b -> b.value(), -1));
        table.getColumns().add(DebugTableColumns.col("Type", b -> b.type(), 110));
        table.getColumns().add(DebugTableColumns.col("Scope", b -> b.scope().name(), 80));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Not paused."));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(title, table);
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
