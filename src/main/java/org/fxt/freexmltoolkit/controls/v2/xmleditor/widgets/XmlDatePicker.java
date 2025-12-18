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

import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/**
 * DatePicker widget for XSD date types.
 *
 * <p>Uses ISO 8601 format (YYYY-MM-DD) as required by XML Schema.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlDatePicker implements TypeAwareWidgetFactory.EditWidget {

    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DatePicker datePicker;
    private final Consumer<String> onValueChange;
    private final boolean includeTime;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new XmlDatePicker.
     *
     * @param currentValue  the current value in ISO format (YYYY-MM-DD)
     * @param onValueChange callback when value changes
     * @param includeTime   whether to include time component (not used yet)
     */
    public XmlDatePicker(String currentValue, Consumer<String> onValueChange, boolean includeTime) {
        this.onValueChange = onValueChange;
        this.includeTime = includeTime;

        this.datePicker = new DatePicker();
        this.datePicker.setEditable(true);
        this.datePicker.setPrefWidth(150);

        // Set converter for ISO format
        datePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                if (date == null) {
                    return "";
                }
                return date.format(ISO_DATE_FORMAT);
            }

            @Override
            public LocalDate fromString(String string) {
                if (string == null || string.isEmpty()) {
                    return null;
                }
                try {
                    return LocalDate.parse(string, ISO_DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    // Try parsing with common alternative formats
                    try {
                        return LocalDate.parse(string, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    } catch (DateTimeParseException e2) {
                        try {
                            return LocalDate.parse(string, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                        } catch (DateTimeParseException e3) {
                            return null;
                        }
                    }
                }
            }
        });

        // Set initial value
        if (currentValue != null && !currentValue.isEmpty()) {
            try {
                // Handle date with timezone suffix
                String dateOnly = currentValue;
                if (currentValue.contains("T")) {
                    dateOnly = currentValue.substring(0, currentValue.indexOf("T"));
                } else if (currentValue.length() > 10) {
                    dateOnly = currentValue.substring(0, 10);
                }
                LocalDate date = LocalDate.parse(dateOnly, ISO_DATE_FORMAT);
                datePicker.setValue(date);
            } catch (DateTimeParseException e) {
                validationResult = ValidationResult.warning("Invalid date format: " + currentValue);
                applyValidationStyle();
            }
        }

        // Listen for changes
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String isoDate = newVal.format(ISO_DATE_FORMAT);
                validationResult = ValidationResult.valid();
                applyValidationStyle();
                if (onValueChange != null) {
                    onValueChange.accept(isoDate);
                }
            }
        });

        // Also handle manual text input
        datePicker.getEditor().setOnAction(e -> {
            String text = datePicker.getEditor().getText();
            LocalDate parsed = datePicker.getConverter().fromString(text);
            if (parsed != null) {
                datePicker.setValue(parsed);
                validationResult = ValidationResult.valid();
            } else if (!text.isEmpty()) {
                validationResult = ValidationResult.warning("Invalid date format. Use YYYY-MM-DD");
            }
            applyValidationStyle();
        });

        // Style
        datePicker.setStyle("-fx-border-color: #ced4da; -fx-border-radius: 3;");
    }

    private void applyValidationStyle() {
        if (!validationResult.isValid() ||
                validationResult.severity() == ValidationSeverity.WARNING) {
            datePicker.setStyle("-fx-border-color: #ffc107; -fx-border-width: 2px; -fx-border-radius: 3;");
        } else {
            datePicker.setStyle("-fx-border-color: #28a745; -fx-border-width: 1px; -fx-border-radius: 3;");
        }
    }

    @Override
    public Node getNode() {
        return datePicker;
    }

    @Override
    public String getValue() {
        LocalDate value = datePicker.getValue();
        return value != null ? value.format(ISO_DATE_FORMAT) : "";
    }

    @Override
    public void setValue(String value) {
        if (value == null || value.isEmpty()) {
            datePicker.setValue(null);
            return;
        }
        try {
            String dateOnly = value;
            if (value.contains("T")) {
                dateOnly = value.substring(0, value.indexOf("T"));
            }
            LocalDate date = LocalDate.parse(dateOnly, ISO_DATE_FORMAT);
            datePicker.setValue(date);
            validationResult = ValidationResult.valid();
        } catch (DateTimeParseException e) {
            validationResult = ValidationResult.warning("Invalid date format: " + value);
        }
        applyValidationStyle();
    }

    @Override
    public void focus() {
        datePicker.requestFocus();
        datePicker.show();
    }

    @Override
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
