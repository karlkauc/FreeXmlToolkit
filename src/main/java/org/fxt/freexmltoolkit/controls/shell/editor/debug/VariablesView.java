package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
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

        table.getColumns().add(col("Name", b -> b.name(), 120));
        table.getColumns().add(col("Value", b -> b.value(), -1));
        table.getColumns().add(col("Type", b -> b.type(), 110));
        table.getColumns().add(col("Scope", b -> b.scope().name(), 80));
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

    private static TableColumn<VariableBinding, String> col(String title,
            java.util.function.Function<VariableBinding, String> value, double prefWidth) {
        TableColumn<VariableBinding, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
