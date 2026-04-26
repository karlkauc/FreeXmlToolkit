package org.fxt.freexmltoolkit.debugger.ui;

import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fxt.freexmltoolkit.debugger.VariableBinding;

/**
 * Table view that lists all variable bindings visible at the current pause point.
 */
public class VariablesPanel extends TableView<VariableBinding> {

    private final ObservableList<VariableBinding> data = FXCollections.observableArrayList();

    @SuppressWarnings("unchecked")
    public VariablesPanel() {
        // VariableBinding is a Java record; record accessors don't follow the
        // get* JavaBean convention that PropertyValueFactory expects, so we
        // wire each column to the record accessor directly.
        TableColumn<VariableBinding, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(120);

        TableColumn<VariableBinding, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().value()));
        valueCol.setPrefWidth(200);

        TableColumn<VariableBinding, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type()));
        typeCol.setPrefWidth(110);

        TableColumn<VariableBinding, String> scopeCol = new TableColumn<>("Scope");
        scopeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().scope().name()));
        scopeCol.setPrefWidth(80);

        getColumns().addAll(nameCol, valueCol, typeCol, scopeCol);
        setItems(data);
        setPlaceholder(new javafx.scene.control.Label("No variables — start a debug session and pause."));
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    /** Replaces the current rows. Safe to call from any thread. */
    public void setBindings(List<VariableBinding> bindings) {
        Runnable r = () -> {
            data.clear();
            if (bindings != null) data.addAll(bindings);
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public void clear() {
        setBindings(List.of());
    }
}
