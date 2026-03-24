package org.fxt.freexmltoolkit.controls.unified.xslt;

import java.util.HashMap;
import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel for managing XSLT transformation parameters.
 * Shows a compact parameter table with Name, Value, and Type columns.
 */
public class XsltParametersPanel extends VBox {

    private final TableView<XsltParameter> parameterTable;
    private final ObservableList<XsltParameter> parameters;

    public XsltParametersPanel() {
        setSpacing(4);
        setPadding(new Insets(4));

        parameters = FXCollections.observableArrayList();

        // Header
        HBox header = new HBox(4);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Parameters");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Button addBtn = new Button();
        FontIcon addIcon = new FontIcon("bi-plus-circle");
        addIcon.setIconSize(12);
        addBtn.setGraphic(addIcon);
        addBtn.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; -fx-cursor: hand;");
        addBtn.setOnAction(e -> addParameter());

        Button removeBtn = new Button();
        FontIcon removeIcon = new FontIcon("bi-dash-circle");
        removeIcon.setIconSize(12);
        removeBtn.setGraphic(removeIcon);
        removeBtn.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> removeSelectedParameter());

        header.getChildren().addAll(title, addBtn, removeBtn);

        // Table
        parameterTable = createTable();
        parameterTable.setPrefHeight(100);
        parameterTable.setMaxHeight(150);

        getChildren().addAll(header, parameterTable);
    }

    @SuppressWarnings("unchecked")
    private TableView<XsltParameter> createTable() {
        TableView<XsltParameter> table = new TableView<>(parameters);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No parameters defined"));

        TableColumn<XsltParameter, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cd -> cd.getValue().nameProperty());
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> e.getRowValue().setName(e.getNewValue()));

        TableColumn<XsltParameter, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(cd -> cd.getValue().valueProperty());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));

        table.getColumns().addAll(nameCol, valueCol);
        return table;
    }

    private void addParameter() {
        parameters.add(new XsltParameter("param" + (parameters.size() + 1), ""));
    }

    private void removeSelectedParameter() {
        XsltParameter selected = parameterTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            parameters.remove(selected);
        }
    }

    /**
     * Gets the parameters as a Map for XSLT transformation.
     */
    public Map<String, Object> getParametersAsMap() {
        Map<String, Object> map = new HashMap<>();
        for (XsltParameter p : parameters) {
            if (p.getName() != null && !p.getName().isEmpty()) {
                map.put(p.getName(), p.getValue());
            }
        }
        return map;
    }

    /**
     * Returns whether there are any parameters defined.
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    /**
     * A single XSLT parameter.
     */
    public static class XsltParameter {
        private final SimpleStringProperty name;
        private final SimpleStringProperty value;

        public XsltParameter(String name, String value) {
            this.name = new SimpleStringProperty(name);
            this.value = new SimpleStringProperty(value);
        }

        public String getName() { return name.get(); }
        public void setName(String n) { name.set(n); }
        public SimpleStringProperty nameProperty() { return name; }

        public String getValue() { return value.get(); }
        public void setValue(String v) { value.set(v); }
        public SimpleStringProperty valueProperty() { return value; }
    }
}
