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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Date and time picker for xs:dateTime and xs:dateTimeStamp.
 *
 * <p>Writes {@code YYYY-MM-DDThh:mm:ss} (always with seconds). A value the user does not change is written back
 * exactly as it was; a changed one keeps the original fractional seconds and timezone ({@code Z}, {@code ±hh:mm}),
 * shown next to the spinners. An empty or unreadable value is never replaced by today's date unless the user picks
 * something.</p>
 */
public class XmlDateTimePicker implements TypeAwareWidgetFactory.EditWidget {

    private final HBox container;
    private final DatePicker datePicker;
    private final Spinner<Integer> hourSpinner;
    private final Spinner<Integer> minuteSpinner;
    private final Spinner<Integer> secondSpinner;
    private final Label suffixLabel = new Label();
    private final Consumer<String> onValueChange;
    private final Function<String, ValidationResult> validator;
    private String original = "";
    private String fraction = "";
    private String zone = "";
    private boolean changed;
    private boolean updating;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new XmlDateTimePicker.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     */
    public XmlDateTimePicker(String currentValue, Consumer<String> onValueChange) {
        this(currentValue, onValueChange, null);
    }

    /**
     * Creates a new XmlDateTimePicker.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     * @param validator     the exact check of the declared type (may be {@code null})
     */
    public XmlDateTimePicker(String currentValue, Consumer<String> onValueChange,
                             Function<String, ValidationResult> validator) {
        this.onValueChange = onValueChange;
        this.validator = validator;

        container = new HBox(5);
        container.setAlignment(Pos.CENTER_LEFT);

        datePicker = new DatePicker();
        datePicker.setEditable(true);
        datePicker.setPrefWidth(130);
        datePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? XsdTemporal.formatDate(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                XsdTemporal.Parts parts = XsdTemporal.parseDate(string);
                return parts != null ? parts.date() : null;
            }
        });

        hourSpinner = createSpinner(23);
        minuteSpinner = createSpinner(59);
        secondSpinner = createSpinner(59);

        Label tLabel = new Label("T");
        tLabel.getStyleClass().addAll("fxt-text-md", "theme-text-muted");
        Label colon1 = new Label(":");
        Label colon2 = new Label(":");
        suffixLabel.getStyleClass().add("fxt-text-sm");

        load(currentValue);

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        hourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        minuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());
        secondSpinner.valueProperty().addListener((obs, oldVal, newVal) -> notifyChange());

        container.getChildren().addAll(datePicker, tLabel, hourSpinner, colon1, minuteSpinner, colon2,
                secondSpinner, suffixLabel);
    }

    private static Spinner<Integer> createSpinner(int max) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, 0));
        spinner.setEditable(true);
        spinner.setPrefWidth(55);
        return spinner;
    }

    private void load(String value) {
        updating = true;
        try {
            original = value == null ? "" : value;
            changed = false;
            XsdTemporal.Parts parts = XsdTemporal.parseDateTime(original);
            LocalTime time = parts != null ? parts.time() : LocalTime.MIDNIGHT;
            fraction = parts != null ? parts.fraction() : "";
            zone = parts != null ? parts.zone() : "";
            datePicker.setValue(parts != null ? parts.date() : null);
            hourSpinner.getValueFactory().setValue(time.getHour());
            minuteSpinner.getValueFactory().setValue(time.getMinute());
            secondSpinner.getValueFactory().setValue(time.getSecond());
            suffixLabel.setText(fraction + zone);
            validationResult = parts == null && !original.isBlank()
                    ? ValidationResult.error(I18n.t("XmlDateTimePicker.invalid-datetime-format", original))
                    : check();
        } finally {
            updating = false;
        }
        WidgetFeedback.show(container, validationResult);
    }

    private ValidationResult check() {
        String value = getValue();
        return validator != null && !value.isEmpty() ? validator.apply(value) : ValidationResult.valid();
    }

    private void notifyChange() {
        if (updating) {
            return;
        }
        changed = true;
        validationResult = check();
        WidgetFeedback.show(container, validationResult);
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
        if (!changed) {
            return original;
        }
        LocalDate date = datePicker.getValue();
        if (date == null) {
            // Only the time was changed: the date the user sees is empty, so today's is the one meant
            date = LocalDate.now();
        }
        return XsdTemporal.formatDate(date) + "T"
                + XsdTemporal.formatTime(value(hourSpinner), value(minuteSpinner), value(secondSpinner), fraction)
                + zone;
    }

    private static int value(Spinner<Integer> spinner) {
        return spinner.getValue() != null ? spinner.getValue() : 0;
    }

    @Override
    public void setValue(String value) {
        load(value);
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
