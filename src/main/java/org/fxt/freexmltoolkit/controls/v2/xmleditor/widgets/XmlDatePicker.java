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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * DatePicker widget for xs:date.
 *
 * <p>Shows and writes the XSD form ({@code YYYY-MM-DD}); typed dates may also be {@code dd.MM.yyyy} or
 * {@code MM/dd/yyyy}. A value the user does not change is written back exactly as it was; a changed date keeps the
 * original timezone ({@code Z}, {@code ±hh:mm}). A value the picker cannot show (for example a malformed one) stays
 * as written until a date is picked.</p>
 */
public class XmlDatePicker implements TypeAwareWidgetFactory.EditWidget {

    private final DatePicker datePicker;
    private final Function<String, ValidationResult> validator;
    private String original;
    private String zone = "";
    private boolean changed;
    private boolean updating;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new XmlDatePicker.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     * @param includeTime   unused (xs:dateTime has its own widget)
     */
    public XmlDatePicker(String currentValue, Consumer<String> onValueChange, boolean includeTime) {
        this(currentValue, onValueChange, null);
    }

    /**
     * Creates a new XmlDatePicker.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     * @param validator     the exact check of the declared type (may be {@code null})
     */
    public XmlDatePicker(String currentValue, Consumer<String> onValueChange,
                         Function<String, ValidationResult> validator) {
        this.validator = validator;
        this.datePicker = new DatePicker();
        this.datePicker.setEditable(true);
        this.datePicker.setPrefWidth(150);
        datePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : XsdTemporal.formatDate(date);
            }

            @Override
            public LocalDate fromString(String string) {
                return parseTyped(string);
            }
        });

        load(currentValue);

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updating) {
                return;
            }
            changed = true;
            validate();
            if (onValueChange != null) {
                onValueChange.accept(getValue());
            }
        });
        datePicker.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updating && datePicker.isFocused() && !newVal.isBlank() && parseTyped(newVal) == null) {
                validationResult = ValidationResult.error(I18n.t("XmlDatePicker.invalid-date-format-use-yyyy"));
                WidgetFeedback.show(datePicker, validationResult);
            }
        });
    }

    private void load(String value) {
        updating = true;
        try {
            original = value == null ? "" : value;
            changed = false;
            XsdTemporal.Parts parts = XsdTemporal.parseDate(original);
            zone = parts != null ? parts.zone() : "";
            datePicker.setValue(parts != null ? parts.date() : null);
            if (parts == null && !original.isBlank()) {
                // Keep the text visible so it can be corrected
                datePicker.getEditor().setText(original);
            }
        } finally {
            updating = false;
        }
        validate();
    }

    /** A typed date: XSD form (with optional timezone), else dd.MM.yyyy or MM/dd/yyyy. */
    private static LocalDate parseTyped(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        XsdTemporal.Parts parts = XsdTemporal.parseDate(text);
        if (parts != null) {
            return parts.date();
        }
        for (String pattern : new String[] {"dd.MM.yyyy", "d.M.yyyy", "MM/dd/yyyy"}) {
            try {
                return LocalDate.parse(text.strip(), DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException e) {
                // next pattern
            }
        }
        return null;
    }

    private void validate() {
        String value = getValue();
        validationResult = validator != null && !value.isEmpty() ? validator.apply(value) : ValidationResult.valid();
        WidgetFeedback.show(datePicker, validationResult);
    }

    @Override
    public Node getNode() {
        return datePicker;
    }

    @Override
    public String getValue() {
        if (!changed) {
            return original;
        }
        LocalDate value = datePicker.getValue();
        return value != null ? XsdTemporal.formatDate(value) + zone : "";
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
