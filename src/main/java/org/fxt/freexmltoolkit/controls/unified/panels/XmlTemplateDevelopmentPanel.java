package org.fxt.freexmltoolkit.controls.unified.panels;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Template development panel for testing XML templates with parameters.
 * Provides a parameter table, template preview, and insert functionality.
 */
public class XmlTemplateDevelopmentPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlTemplateDevelopmentPanel.class);

    private final TableView<TemplateParameter> parameterTable;
    private final ObservableList<TemplateParameter> parameters;
    private final CodeArea previewArea;
    private final Label statusLabel;

    private java.util.function.Consumer<String> onInsertRequested;
    private Runnable onCloseRequested;

    public XmlTemplateDevelopmentPanel() {
        setSpacing(4);
        setPadding(new Insets(4));
        setMinHeight(150);
        setPrefHeight(250);

        this.parameters = FXCollections.observableArrayList();

        // Header
        HBox header = createHeader();

        // Parameter table
        parameterTable = createParameterTable();

        // Preview area
        previewArea = new CodeArea();
        previewArea.setEditable(false);

        // Split pane: parameters | preview
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.4);

        VBox paramSide = new VBox(4);
        Label paramLabel = new Label("Parameters");
        paramLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        HBox paramToolbar = new HBox(4);
        paramToolbar.setAlignment(Pos.CENTER_LEFT);
        Button addBtn = new Button("Add");
        addBtn.setStyle("-fx-font-size: 11px;");
        addBtn.setOnAction(e -> addParameter());
        Button removeBtn = new Button("Remove");
        removeBtn.setStyle("-fx-font-size: 11px;");
        removeBtn.setOnAction(e -> removeSelectedParameter());
        Button resetBtn = new Button("Reset");
        resetBtn.setStyle("-fx-font-size: 11px;");
        resetBtn.setOnAction(e -> parameters.clear());
        paramToolbar.getChildren().addAll(paramLabel, addBtn, removeBtn, resetBtn);

        VBox.setVgrow(parameterTable, Priority.ALWAYS);
        paramSide.getChildren().addAll(paramToolbar, parameterTable);

        VBox previewSide = new VBox(4);
        Label previewLabel = new Label("Generated XML Preview");
        previewLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        VBox.setVgrow(new VirtualizedScrollPane<>(previewArea), Priority.ALWAYS);
        previewSide.getChildren().addAll(previewLabel, new VirtualizedScrollPane<>(previewArea));

        splitPane.getItems().addAll(paramSide, previewSide);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Status
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        getChildren().addAll(header, splitPane);
    }

    private HBox createHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 4, 2, 4));

        FontIcon icon = new FontIcon("bi-file-earmark-ruled");
        icon.setIconSize(14);
        Label title = new Label("Template Development");
        title.setGraphic(icon);
        title.setStyle("-fx-font-weight: bold;");

        Button generateBtn = new Button("Generate");
        generateBtn.setStyle("-fx-font-size: 11px;");
        generateBtn.setGraphic(createIcon("bi-play-fill", 12));
        generateBtn.setOnAction(e -> generatePreview());

        Button insertBtn = new Button("Insert");
        insertBtn.setStyle("-fx-font-size: 11px;");
        insertBtn.setGraphic(createIcon("bi-box-arrow-in-down", 12));
        insertBtn.setOnAction(e -> {
            String preview = previewArea.getText();
            if (onInsertRequested != null && preview != null && !preview.isEmpty()) {
                onInsertRequested.accept(preview);
            }
        });

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button();
        closeBtn.setGraphic(createIcon("bi-x-lg", 14));
        closeBtn.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.run();
        });
        closeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        header.getChildren().addAll(title, generateBtn, insertBtn, statusLabel, spacer, closeBtn);

        return header;
    }

    @SuppressWarnings("unchecked")
    private TableView<TemplateParameter> createParameterTable() {
        TableView<TemplateParameter> table = new TableView<>(parameters);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<TemplateParameter, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cd -> cd.getValue().nameProperty());
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> e.getRowValue().setName(e.getNewValue()));
        nameCol.setPrefWidth(120);

        TableColumn<TemplateParameter, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(cd -> cd.getValue().valueProperty());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valueCol.setPrefWidth(200);

        TableColumn<TemplateParameter, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd -> cd.getValue().typeProperty());
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setOnEditCommit(e -> e.getRowValue().setType(e.getNewValue()));
        typeCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, valueCol, typeCol);

        return table;
    }

    private void addParameter() {
        parameters.add(new TemplateParameter("param" + (parameters.size() + 1), "", "string"));
    }

    private void removeSelectedParameter() {
        TemplateParameter selected = parameterTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            parameters.remove(selected);
        }
    }

    /**
     * Generates a preview by replacing ${paramName} placeholders in a template.
     */
    public void generatePreview() {
        // Simple template: generate XML from parameters
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<data>\n");
        for (TemplateParameter param : parameters) {
            String name = param.getName();
            String value = param.getValue();
            if (name != null && !name.isEmpty()) {
                sb.append("    <").append(name).append(">");
                sb.append(value != null ? escapeXml(value) : "");
                sb.append("</").append(name).append(">\n");
            }
        }
        sb.append("</data>");

        previewArea.replaceText(sb.toString());
        statusLabel.setText("Generated " + parameters.size() + " parameter(s)");
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Sets a callback for the "Insert" button action.
     */
    public void setOnInsertRequested(java.util.function.Consumer<String> handler) {
        this.onInsertRequested = handler;
    }

    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }

    private FontIcon createIcon(String literal, int size) {
        FontIcon fi = new FontIcon(literal);
        fi.setIconSize(size);
        return fi;
    }

    /**
     * A single template parameter with name, value, and type.
     */
    public static class TemplateParameter {
        private final SimpleStringProperty name;
        private final SimpleStringProperty value;
        private final SimpleStringProperty type;

        public TemplateParameter(String name, String value, String type) {
            this.name = new SimpleStringProperty(name);
            this.value = new SimpleStringProperty(value);
            this.type = new SimpleStringProperty(type);
        }

        public String getName() { return name.get(); }
        public void setName(String n) { name.set(n); }
        public SimpleStringProperty nameProperty() { return name; }

        public String getValue() { return value.get(); }
        public void setValue(String v) { value.set(v); }
        public SimpleStringProperty valueProperty() { return value; }

        public String getType() { return type.get(); }
        public void setType(String t) { type.set(t); }
        public SimpleStringProperty typeProperty() { return type; }
    }
}
