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
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/**
 * Time picker widget for XSD time types.
 *
 * <p>Uses ISO 8601 format (HH:mm:ss) as required by XML Schema.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlTimePicker implements TypeAwareWidgetFactory.EditWidget {

    private static final DateTimeFormatter ISO_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_TIME;

    private final HBox container;
    private final Spinner<Integer> hourSpinner;
    private final Spinner<Integer> minuteSpinner;
    private final Spinner<Integer> secondSpinner;
    private final Consumer<String> onValueChange;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new XmlTimePicker.
     *
     * @param currentValue  the current value in ISO format (HH:mm:ss)
     * @param onValueChange callback when value changes
     */
    public XmlTimePicker(String currentValue, Consumer<String> onValueChange) {
        this.onValueChange = onValueChange;

        container = new HBox(3);
        container.setAlignment(Pos.CENTER_LEFT);

        // Hour spinner
        hourSpinner = new Spinner<>();
        hourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 0));
        hourSpinner.setEditable(true);
        hourSpinner.setPrefWidth(60);
        styleSpinner(hourSpinner);

        // Minute spinner
        minuteSpinner = new Spinner<>();
        minuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        minuteSpinner.setEditable(true);
        minuteSpinner.setPrefWidth(60);
        styleSpinner(minuteSpinner);

        // Second spinner
        secondSpinner = new Spinner<>();
        secondSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        secondSpinner.setEditable(true);
        secondSpinner.setPrefWidth(60);
        styleSpinner(secondSpinner);

        // Labels
        Label colonLabel1 = new Label(":");
        colonLabel1.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        Label colonLabel2 = new Label(":");
        colonLabel2.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        // Parse initial value
        if (currentValue != null && !currentValue.isEmpty()) {
            try {
                LocalTime time = parseTime(currentValue);
                hourSpinner.getValueFactory().setValue(time.getHour());
                minuteSpinner.getValueFactory().setValue(time.getMinute());
                secondSpinner.getValueFactory().setValue(time.getSecond());
            } catch (DateTimeParseException e) {
                validationResult = ValidationResult.warning("Invalid time format: " + currentValue);
            }
        }

        // Listen for changes
        hourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        minuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        secondSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());

        container.getChildren().addAll(
                hourSpinner, colonLabel1,
                minuteSpinner, colonLabel2,
                secondSpinner
        );
    }

    private LocalTime parseTime(String value) {
        // Handle time with timezone suffix
        String timeOnly = value;
        if (value.contains("+") || value.contains("Z")) {
            int idx = value.indexOf("+");
            if (idx < 0) idx = value.indexOf("Z");
            if (idx > 0) timeOnly = value.substring(0, idx);
        }
        return LocalTime.parse(timeOnly, ISO_TIME_FORMAT);
    }

    private void styleSpinner(Spinner<Integer> spinner) {
        spinner.setStyle("-fx-border-color: #ced4da; -fx-border-radius: 3;");
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
        int hours = hourSpinner.getValue() != null ? hourSpinner.getValue() : 0;
        int minutes = minuteSpinner.getValue() != null ? minuteSpinner.getValue() : 0;
        int seconds = secondSpinner.getValue() != null ? secondSpinner.getValue() : 0;

        LocalTime time = LocalTime.of(hours, minutes, seconds);
        return time.format(ISO_TIME_FORMAT);
    }

    @Override
    public void setValue(String value) {
        if (value == null || value.isEmpty()) {
            hourSpinner.getValueFactory().setValue(0);
            minuteSpinner.getValueFactory().setValue(0);
            secondSpinner.getValueFactory().setValue(0);
            return;
        }
        try {
            LocalTime time = parseTime(value);
            hourSpinner.getValueFactory().setValue(time.getHour());
            minuteSpinner.getValueFactory().setValue(time.getMinute());
            secondSpinner.getValueFactory().setValue(time.getSecond());
            validationResult = ValidationResult.valid();
        } catch (DateTimeParseException e) {
            validationResult = ValidationResult.warning("Invalid time format: " + value);
        }
    }

    @Override
    public void focus() {
        hourSpinner.requestFocus();
    }

    @Override
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
