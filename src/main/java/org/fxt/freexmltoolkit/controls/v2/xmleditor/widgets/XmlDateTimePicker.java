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

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/**
 * Combined date and time picker widget for XSD dateTime types.
 *
 * <p>Uses ISO 8601 format (YYYY-MM-DDTHH:mm:ss) as required by XML Schema.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlDateTimePicker implements TypeAwareWidgetFactory.EditWidget {

    private static final DateTimeFormatter ISO_DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final HBox container;
    private final DatePicker datePicker;
    private final Spinner<Integer> hourSpinner;
    private final Spinner<Integer> minuteSpinner;
    private final Spinner<Integer> secondSpinner;
    private final Consumer<String> onValueChange;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new XmlDateTimePicker.
     *
     * @param currentValue  the current value in ISO format (YYYY-MM-DDTHH:mm:ss)
     * @param onValueChange callback when value changes
     */
    public XmlDateTimePicker(String currentValue, Consumer<String> onValueChange) {
        this.onValueChange = onValueChange;

        container = new HBox(5);
        container.setAlignment(Pos.CENTER_LEFT);

        // Date picker
        datePicker = new DatePicker();
        datePicker.setEditable(true);
        datePicker.setPrefWidth(130);
        datePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(ISO_DATE_FORMAT) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string == null || string.isEmpty()) return null;
                try {
                    return LocalDate.parse(string, ISO_DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });

        // Time spinners
        hourSpinner = createSpinner(0, 23, 0);
        minuteSpinner = createSpinner(0, 59, 0);
        secondSpinner = createSpinner(0, 59, 0);

        // Labels
        Label tLabel = new Label("T");
        tLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: #6c757d;");
        Label colon1 = new Label(":");
        colon1.setStyle("-fx-font-weight: bold;");
        Label colon2 = new Label(":");
        colon2.setStyle("-fx-font-weight: bold;");

        // Parse initial value
        if (currentValue != null && !currentValue.isEmpty()) {
            try {
                LocalDateTime dateTime = parseDateTime(currentValue);
                datePicker.setValue(dateTime.toLocalDate());
                hourSpinner.getValueFactory().setValue(dateTime.getHour());
                minuteSpinner.getValueFactory().setValue(dateTime.getMinute());
                secondSpinner.getValueFactory().setValue(dateTime.getSecond());
            } catch (DateTimeParseException e) {
                validationResult = ValidationResult.warning("Invalid dateTime format: " + currentValue);
            }
        } else {
            datePicker.setValue(LocalDate.now());
        }

        // Listen for changes
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        hourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        minuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        secondSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());

        container.getChildren().addAll(
                datePicker, tLabel,
                hourSpinner, colon1,
                minuteSpinner, colon2,
                secondSpinner
        );
    }

    private Spinner<Integer> createSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial));
        spinner.setEditable(true);
        spinner.setPrefWidth(55);
        spinner.setStyle("-fx-border-color: #ced4da; -fx-border-radius: 3;");
        return spinner;
    }

    private LocalDateTime parseDateTime(String value) {
        // Handle timezone suffix
        String dtOnly = value;
        if (value.contains("+") || value.endsWith("Z")) {
            int idx = value.lastIndexOf("+");
            if (idx < 0) idx = value.lastIndexOf("Z");
            if (idx > 0) dtOnly = value.substring(0, idx);
        }
        return LocalDateTime.parse(dtOnly, ISO_DATE_TIME_FORMAT);
    }

    private void notifyChange() {
        validationResult = ValidationResult.valid();
        if (onValueChange != null) {
            onValueChange.accept(getValue());
        }
    }

    @Override
    public Node getNode() {
        return container;
    }

    @Override
    public String getValue() {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            date = LocalDate.now();
        }

        int hours = hourSpinner.getValue() != null ? hourSpinner.getValue() : 0;
        int minutes = minuteSpinner.getValue() != null ? minuteSpinner.getValue() : 0;
        int seconds = secondSpinner.getValue() != null ? secondSpinner.getValue() : 0;

        LocalDateTime dateTime = LocalDateTime.of(date, LocalTime.of(hours, minutes, seconds));
        return dateTime.format(ISO_DATE_TIME_FORMAT);
    }

    @Override
    public void setValue(String value) {
        if (value == null || value.isEmpty()) {
            datePicker.setValue(LocalDate.now());
            hourSpinner.getValueFactory().setValue(0);
            minuteSpinner.getValueFactory().setValue(0);
            secondSpinner.getValueFactory().setValue(0);
            return;
        }
        try {
            LocalDateTime dateTime = parseDateTime(value);
            datePicker.setValue(dateTime.toLocalDate());
            hourSpinner.getValueFactory().setValue(dateTime.getHour());
            minuteSpinner.getValueFactory().setValue(dateTime.getMinute());
            secondSpinner.getValueFactory().setValue(dateTime.getSecond());
            validationResult = ValidationResult.valid();
        } catch (DateTimeParseException e) {
            validationResult = ValidationResult.warning("Invalid dateTime format: " + value);
        }
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
