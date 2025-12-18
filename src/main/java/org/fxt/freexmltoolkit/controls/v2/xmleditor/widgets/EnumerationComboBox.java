/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.util.List;
import java.util.function.Consumer;

/**
 * ComboBox widget for XSD enumeration types.
 *
 * <p>Displays all valid enumeration values in a dropdown for easy selection.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class EnumerationComboBox implements TypeAwareWidgetFactory.EditWidget {

    private final ComboBox<String> comboBox;
    private final List<String> enumerationValues;
    private final Consumer<String> onValueChange;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new EnumerationComboBox.
     *
     * @param enumerationValues the valid enumeration values
     * @param currentValue      the current selected value
     * @param onValueChange     callback when value changes
     */
    public EnumerationComboBox(List<String> enumerationValues, String currentValue,
                               Consumer<String> onValueChange) {
        this.enumerationValues = enumerationValues;
        this.onValueChange = onValueChange;

        this.comboBox = new ComboBox<>(FXCollections.observableArrayList(enumerationValues));
        this.comboBox.setEditable(true); // Allow typing to filter
        this.comboBox.setMaxWidth(Double.MAX_VALUE);

        // Set cell factory for better display
        comboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    // Highlight current selection
                    if (item.equals(currentValue)) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Set current value
        if (currentValue != null && enumerationValues.contains(currentValue)) {
            comboBox.setValue(currentValue);
        } else if (currentValue != null && !currentValue.isEmpty()) {
            // Value exists but not in enum - show warning
            comboBox.setValue(currentValue);
            validationResult = ValidationResult.warning(
                    "Value '" + currentValue + "' is not in the enumeration list");
            applyValidationStyle();
        }

        // Listen for changes
        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            validateAndNotify(newVal);
        });

        // Also listen to editor text changes for typed values
        comboBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            // Only validate when focus is lost or Enter is pressed
        });

        comboBox.getEditor().setOnAction(e -> {
            String typed = comboBox.getEditor().getText();
            validateAndNotify(typed);
        });

        // Apply initial styling
        comboBox.setStyle("-fx-min-width: 100px;");
    }

    private void validateAndNotify(String value) {
        if (value != null && !value.isEmpty() && !enumerationValues.contains(value)) {
            validationResult = ValidationResult.warning(
                    "Value '" + value + "' is not in the enumeration list");
        } else {
            validationResult = ValidationResult.valid();
        }
        applyValidationStyle();

        if (onValueChange != null && value != null) {
            onValueChange.accept(value);
        }
    }

    private void applyValidationStyle() {
        if (!validationResult.isValid() ||
                validationResult.severity() == ValidationSeverity.WARNING) {
            comboBox.setStyle("-fx-min-width: 100px; -fx-border-color: #ffc107; -fx-border-width: 2px;");
        } else {
            comboBox.setStyle("-fx-min-width: 100px; -fx-border-color: #28a745; -fx-border-width: 1px;");
        }
    }

    @Override
    public Node getNode() {
        return comboBox;
    }

    @Override
    public String getValue() {
        return comboBox.getValue();
    }

    @Override
    public void setValue(String value) {
        comboBox.setValue(value);
    }

    @Override
    public void focus() {
        comboBox.requestFocus();
        comboBox.show(); // Open the dropdown
    }

    @Override
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
