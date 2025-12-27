/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.unified;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Properties pane for XSLT editor in the MultiFunctionalSidePane.
 * <p>
 * Provides configuration options for XSLT transformations:
 * <ul>
 *   <li>Output format (XML, HTML, Text, JSON)</li>
 *   <li>Output encoding (UTF-8, UTF-16, ISO-8859-1)</li>
 *   <li>Indent output option</li>
 *   <li>XSLT parameters table</li>
 *   <li>Performance statistics</li>
 * </ul>
 *
 * @since 2.0
 */
public class XsltOutputPane extends VBox {

    private static final Logger logger = LogManager.getLogger(XsltOutputPane.class);

    // Configuration controls
    private final ComboBox<String> outputFormatCombo;
    private final ComboBox<String> encodingCombo;
    private final CheckBox indentOutputCheck;

    // Parameters table
    private final TableView<XsltParameter> parametersTable;
    private final ObservableList<XsltParameter> parameters;

    // Performance stats
    private final Label executionTimeLabel;
    private final Label outputSizeLabel;
    private final Label memoryUsageLabel;

    // Callback
    private Runnable onConfigurationChanged;

    /**
     * Creates a new XSLT output configuration pane.
     */
    public XsltOutputPane() {
        super(12);
        setPadding(new Insets(12));
        getStyleClass().add("xslt-output-pane");

        // Output Format section
        outputFormatCombo = new ComboBox<>();
        outputFormatCombo.getItems().addAll("XML", "HTML", "Text", "XHTML");
        outputFormatCombo.setValue("XML");
        outputFormatCombo.setMaxWidth(Double.MAX_VALUE);
        outputFormatCombo.setOnAction(e -> notifyConfigurationChanged());

        // Encoding section
        encodingCombo = new ComboBox<>();
        encodingCombo.getItems().addAll("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII");
        encodingCombo.setValue("UTF-8");
        encodingCombo.setMaxWidth(Double.MAX_VALUE);
        encodingCombo.setOnAction(e -> notifyConfigurationChanged());

        // Indent option
        indentOutputCheck = new CheckBox("Indent Output");
        indentOutputCheck.setSelected(true);
        indentOutputCheck.setOnAction(e -> notifyConfigurationChanged());

        // Output Configuration section
        TitledPane outputConfigPane = createOutputConfigSection();

        // Parameters section
        parameters = FXCollections.observableArrayList();
        parametersTable = createParametersTable();
        TitledPane parametersPane = createParametersSection();

        // Performance section
        executionTimeLabel = new Label("--");
        outputSizeLabel = new Label("--");
        memoryUsageLabel = new Label("--");
        TitledPane performancePane = createPerformanceSection();

        // Add all sections
        getChildren().addAll(outputConfigPane, parametersPane, performancePane);

        logger.debug("XsltOutputPane created");
    }

    /**
     * Creates the output configuration section.
     */
    private TitledPane createOutputConfigSection() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(8));

        // Format row
        HBox formatRow = new HBox(8);
        formatRow.setAlignment(Pos.CENTER_LEFT);
        Label formatLabel = new Label("Format:");
        formatLabel.setMinWidth(70);
        HBox.setHgrow(outputFormatCombo, Priority.ALWAYS);
        formatRow.getChildren().addAll(formatLabel, outputFormatCombo);

        // Encoding row
        HBox encodingRow = new HBox(8);
        encodingRow.setAlignment(Pos.CENTER_LEFT);
        Label encodingLabel = new Label("Encoding:");
        encodingLabel.setMinWidth(70);
        HBox.setHgrow(encodingCombo, Priority.ALWAYS);
        encodingRow.getChildren().addAll(encodingLabel, encodingCombo);

        content.getChildren().addAll(formatRow, encodingRow, indentOutputCheck);

        TitledPane pane = new TitledPane("Output Configuration", content);
        FontIcon icon = new FontIcon("bi-gear");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    /**
     * Creates the parameters table.
     */
    @SuppressWarnings("unchecked")
    private TableView<XsltParameter> createParametersTable() {
        TableView<XsltParameter> table = new TableView<>();
        table.setEditable(true);
        table.setPlaceholder(new Label("No parameters defined"));
        table.setPrefHeight(120);

        // Name column
        TableColumn<XsltParameter, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> e.getRowValue().setName(e.getNewValue()));
        nameCol.setPrefWidth(80);

        // Value column
        TableColumn<XsltParameter, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valueCol.setPrefWidth(100);

        // Type column
        TableColumn<XsltParameter, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setCellFactory(column -> new ComboBoxTableCell<>("xs:string", "xs:integer", "xs:boolean", "xs:date", "xs:decimal"));
        typeCol.setOnEditCommit(e -> e.getRowValue().setType(e.getNewValue()));
        typeCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, valueCol, typeCol);
        table.setItems(parameters);

        return table;
    }

    /**
     * Creates the parameters section with table and add/remove buttons.
     */
    private TitledPane createParametersSection() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(8));

        // Toolbar for add/remove
        HBox toolbar = new HBox(4);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button addBtn = new Button();
        FontIcon addIcon = new FontIcon("bi-plus-circle");
        addIcon.setIconSize(14);
        addBtn.setGraphic(addIcon);
        addBtn.setTooltip(new Tooltip("Add Parameter"));
        addBtn.setOnAction(e -> addParameter());

        Button removeBtn = new Button();
        FontIcon removeIcon = new FontIcon("bi-dash-circle");
        removeIcon.setIconSize(14);
        removeBtn.setGraphic(removeIcon);
        removeBtn.setTooltip(new Tooltip("Remove Selected"));
        removeBtn.setOnAction(e -> removeSelectedParameter());
        removeBtn.disableProperty().bind(parametersTable.getSelectionModel().selectedItemProperty().isNull());

        Button clearBtn = new Button();
        FontIcon clearIcon = new FontIcon("bi-trash");
        clearIcon.setIconSize(14);
        clearBtn.setGraphic(clearIcon);
        clearBtn.setTooltip(new Tooltip("Clear All"));
        clearBtn.setOnAction(e -> parameters.clear());

        toolbar.getChildren().addAll(addBtn, removeBtn, clearBtn);

        content.getChildren().addAll(toolbar, parametersTable);
        VBox.setVgrow(parametersTable, Priority.ALWAYS);

        TitledPane pane = new TitledPane("Parameters", content);
        FontIcon icon = new FontIcon("bi-sliders");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    /**
     * Creates the performance statistics section.
     */
    private TitledPane createPerformanceSection() {
        VBox content = new VBox(6);
        content.setPadding(new Insets(8));

        // Execution time
        HBox timeRow = createStatRow("Execution Time:", executionTimeLabel, "bi-stopwatch");

        // Output size
        HBox sizeRow = createStatRow("Output Size:", outputSizeLabel, "bi-file-earmark-text");

        // Memory usage
        HBox memoryRow = createStatRow("Memory Used:", memoryUsageLabel, "bi-memory");

        content.getChildren().addAll(timeRow, sizeRow, memoryRow);

        TitledPane pane = new TitledPane("Performance", content);
        FontIcon icon = new FontIcon("bi-speedometer2");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(false);

        return pane;
    }

    /**
     * Creates a row for displaying a statistic.
     */
    private HBox createStatRow(String label, Label valueLabel, String iconLiteral) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(14);
        icon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));

        Label nameLabel = new Label(label);
        nameLabel.setMinWidth(100);
        nameLabel.setStyle("-fx-text-fill: #6c757d;");

        valueLabel.setStyle("-fx-font-weight: bold;");

        row.getChildren().addAll(icon, nameLabel, valueLabel);
        return row;
    }

    /**
     * Adds a new parameter to the table.
     */
    private void addParameter() {
        XsltParameter param = new XsltParameter("param" + (parameters.size() + 1), "", "xs:string");
        parameters.add(param);
        parametersTable.getSelectionModel().select(param);
        notifyConfigurationChanged();
    }

    /**
     * Removes the selected parameter.
     */
    private void removeSelectedParameter() {
        XsltParameter selected = parametersTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            parameters.remove(selected);
            notifyConfigurationChanged();
        }
    }

    /**
     * Notifies the callback that configuration has changed.
     */
    private void notifyConfigurationChanged() {
        if (onConfigurationChanged != null) {
            onConfigurationChanged.run();
        }
    }

    // ==================== Public API ====================

    /**
     * Gets the selected output format.
     */
    public XsltTransformationEngine.OutputFormat getOutputFormat() {
        String format = outputFormatCombo.getValue();
        return switch (format) {
            case "HTML" -> XsltTransformationEngine.OutputFormat.HTML;
            case "Text" -> XsltTransformationEngine.OutputFormat.TEXT;
            case "XHTML" -> XsltTransformationEngine.OutputFormat.XHTML;
            default -> XsltTransformationEngine.OutputFormat.XML;
        };
    }

    /**
     * Sets the output format.
     */
    public void setOutputFormat(String format) {
        outputFormatCombo.setValue(format);
    }

    /**
     * Gets the selected encoding.
     */
    public String getEncoding() {
        return encodingCombo.getValue();
    }

    /**
     * Sets the encoding.
     */
    public void setEncoding(String encoding) {
        encodingCombo.setValue(encoding);
    }

    /**
     * Checks if indent output is enabled.
     */
    public boolean isIndentOutput() {
        return indentOutputCheck.isSelected();
    }

    /**
     * Sets the indent output option.
     */
    public void setIndentOutput(boolean indent) {
        indentOutputCheck.setSelected(indent);
    }

    /**
     * Gets all parameters as a map.
     */
    public Map<String, Object> getParametersAsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (XsltParameter param : parameters) {
            if (param.getName() != null && !param.getName().isEmpty()) {
                map.put(param.getName(), convertValue(param.getValue(), param.getType()));
            }
        }
        return map;
    }

    /**
     * Converts a parameter value to the appropriate type.
     */
    private Object convertValue(String value, String type) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        return switch (type) {
            case "xs:integer" -> {
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "xs:decimal" -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "xs:boolean" -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    /**
     * Sets the configuration change callback.
     */
    public void setOnConfigurationChanged(Runnable callback) {
        this.onConfigurationChanged = callback;
    }

    /**
     * Updates the performance statistics.
     */
    public void updatePerformanceStats(long executionTimeMs, long outputBytes, long memoryBytes) {
        executionTimeLabel.setText(executionTimeMs + " ms");

        if (outputBytes < 1024) {
            outputSizeLabel.setText(outputBytes + " bytes");
        } else if (outputBytes < 1024 * 1024) {
            outputSizeLabel.setText(String.format("%.1f KB", outputBytes / 1024.0));
        } else {
            outputSizeLabel.setText(String.format("%.2f MB", outputBytes / (1024.0 * 1024)));
        }

        if (memoryBytes < 1024) {
            memoryUsageLabel.setText(memoryBytes + " bytes");
        } else if (memoryBytes < 1024 * 1024) {
            memoryUsageLabel.setText(String.format("%.1f KB", memoryBytes / 1024.0));
        } else {
            memoryUsageLabel.setText(String.format("%.2f MB", memoryBytes / (1024.0 * 1024)));
        }
    }

    /**
     * Clears the performance statistics.
     */
    public void clearPerformanceStats() {
        executionTimeLabel.setText("--");
        outputSizeLabel.setText("--");
        memoryUsageLabel.setText("--");
    }

    // ==================== Inner Classes ====================

    /**
     * Model class for XSLT parameters.
     */
    public static class XsltParameter {
        private final StringProperty name;
        private final StringProperty value;
        private final StringProperty type;

        public XsltParameter(String name, String value, String type) {
            this.name = new SimpleStringProperty(name);
            this.value = new SimpleStringProperty(value);
            this.type = new SimpleStringProperty(type);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public String getValue() {
            return value.get();
        }

        public void setValue(String value) {
            this.value.set(value);
        }

        public StringProperty valueProperty() {
            return value;
        }

        public String getType() {
            return type.get();
        }

        public void setType(String type) {
            this.type.set(type);
        }

        public StringProperty typeProperty() {
            return type;
        }
    }

    /**
     * Custom table cell with ComboBox for type selection.
     */
    private static class ComboBoxTableCell<S, T> extends TableCell<S, T> {
        private ComboBox<T> comboBox;
        private final T[] items;

        @SafeVarargs
        public ComboBoxTableCell(T... items) {
            this.items = items;
        }

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createComboBox();
                setText(null);
                setGraphic(comboBox);
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem() != null ? getItem().toString() : null);
            setGraphic(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (comboBox != null) {
                        comboBox.setValue(item);
                    }
                    setText(null);
                    setGraphic(comboBox);
                } else {
                    setText(item != null ? item.toString() : null);
                    setGraphic(null);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void createComboBox() {
            comboBox = new ComboBox<>();
            comboBox.getItems().addAll(items);
            comboBox.setValue(getItem());
            comboBox.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
            comboBox.setOnAction(e -> {
                commitEdit(comboBox.getValue());
            });
        }
    }
}
