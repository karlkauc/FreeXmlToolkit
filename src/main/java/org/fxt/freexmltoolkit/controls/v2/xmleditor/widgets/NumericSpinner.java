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
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Spinner widget for XSD numeric types (integer, decimal, float, double).
 *
 * <p>Supports facets like minInclusive, maxInclusive, totalDigits, fractionDigits.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class NumericSpinner implements TypeAwareWidgetFactory.EditWidget {

    private final Spinner<Double> spinner;
    private final Consumer<String> onValueChange;
    private final boolean integerOnly;
    private final Double minValue;
    private final Double maxValue;
    private final Integer totalDigits;
    private final Integer fractionDigits;
    private ValidationResult validationResult = ValidationResult.valid();

    // Pattern for numeric input
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d*");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d*\\.?\\d*");

    /**
     * Creates a new NumericSpinner.
     *
     * @param currentValue  the current value as string
     * @param onValueChange callback when value changes
     * @param facets        the facets map (minInclusive, maxInclusive, etc.)
     * @param integerOnly   true for integer types, false for decimal/float/double
     */
    public NumericSpinner(String currentValue, Consumer<String> onValueChange,
                          Map<String, String> facets, boolean integerOnly) {
        this.onValueChange = onValueChange;
        this.integerOnly = integerOnly;

        // Parse facets
        this.minValue = parseFacetDouble(facets, "minInclusive");
        this.maxValue = parseFacetDouble(facets, "maxInclusive");
        this.totalDigits = parseFacetInt(facets, "totalDigits");
        this.fractionDigits = parseFacetInt(facets, "fractionDigits");

        // Determine bounds
        double min = minValue != null ? minValue : (integerOnly ? Integer.MIN_VALUE : -Double.MAX_VALUE);
        double max = maxValue != null ? maxValue : (integerOnly ? Integer.MAX_VALUE : Double.MAX_VALUE);
        double step = integerOnly ? 1 : 0.1;

        // Parse initial value
        double initial = 0;
        if (currentValue != null && !currentValue.isEmpty()) {
            try {
                initial = Double.parseDouble(currentValue);
            } catch (NumberFormatException e) {
                validationResult = ValidationResult.warning("Invalid number: " + currentValue);
            }
        }

        // Clamp initial value to bounds
        initial = Math.max(min, Math.min(max, initial));

        // Create spinner
        spinner = new Spinner<>();
        SpinnerValueFactory.DoubleSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);

        // Format for display
        if (integerOnly) {
            valueFactory.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Double value) {
                    return value != null ? String.valueOf(value.longValue()) : "0";
                }

                @Override
                public Double fromString(String string) {
                    if (string == null || string.isEmpty()) return 0.0;
                    try {
                        return (double) Long.parseLong(string);
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
            });
        } else {
            // Use appropriate decimal format based on fractionDigits
            int decimals = fractionDigits != null ? fractionDigits : 2;
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(decimals);
            df.setMinimumFractionDigits(0);
            df.setGroupingUsed(false);

            valueFactory.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Double value) {
                    return value != null ? df.format(value) : "0";
                }

                @Override
                public Double fromString(String string) {
                    if (string == null || string.isEmpty()) return 0.0;
                    try {
                        return df.parse(string).doubleValue();
                    } catch (ParseException e) {
                        return 0.0;
                    }
                }
            });
        }

        spinner.setValueFactory(valueFactory);
        spinner.setEditable(true);
        spinner.setPrefWidth(120);

        // Add text formatter for input validation
        TextField editor = spinner.getEditor();
        Pattern pattern = integerOnly ? INTEGER_PATTERN : DECIMAL_PATTERN;
        editor.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || pattern.matcher(newText).matches()) {
                return change;
            }
            return null;
        }));

        // Listen for changes
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                validate(newVal);
                if (onValueChange != null) {
                    onValueChange.accept(formatValue(newVal));
                }
            }
        });

        // Style
        applyValidationStyle();
    }

    private Double parseFacetDouble(Map<String, String> facets, String name) {
        if (facets == null || !facets.containsKey(name)) return null;
        try {
            return Double.parseDouble(facets.get(name));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseFacetInt(Map<String, String> facets, String name) {
        if (facets == null || !facets.containsKey(name)) return null;
        try {
            return Integer.parseInt(facets.get(name));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validate(Double value) {
        if (minValue != null && value < minValue) {
            validationResult = ValidationResult.warning("Value must be >= " + formatValue(minValue));
        } else if (maxValue != null && value > maxValue) {
            validationResult = ValidationResult.warning("Value must be <= " + formatValue(maxValue));
        } else if (totalDigits != null) {
            String strVal = formatValue(value).replace("-", "").replace(".", "");
            if (strVal.length() > totalDigits) {
                validationResult = ValidationResult.warning("Value cannot have more than " + totalDigits + " digits");
            } else {
                validationResult = ValidationResult.valid();
            }
        } else {
            validationResult = ValidationResult.valid();
        }
        applyValidationStyle();
    }

    private String formatValue(Double value) {
        if (integerOnly) {
            return String.valueOf(value.longValue());
        } else if (fractionDigits != null) {
            return String.format("%." + fractionDigits + "f", value);
        } else {
            // Remove trailing zeros
            BigDecimal bd = BigDecimal.valueOf(value);
            return bd.stripTrailingZeros().toPlainString();
        }
    }

    private void applyValidationStyle() {
        if (!validationResult.isValid() ||
                validationResult.severity() == ValidationSeverity.WARNING) {
            spinner.setStyle("-fx-border-color: #ffc107; -fx-border-width: 2px;");
        } else {
            spinner.setStyle("-fx-border-color: #28a745; -fx-border-width: 1px;");
        }
    }

    @Override
    public Node getNode() {
        return spinner;
    }

    @Override
    public String getValue() {
        Double value = spinner.getValue();
        return value != null ? formatValue(value) : "0";
    }

    @Override
    public void setValue(String value) {
        if (value == null || value.isEmpty()) {
            spinner.getValueFactory().setValue(0.0);
            return;
        }
        try {
            double parsed = Double.parseDouble(value);
            spinner.getValueFactory().setValue(parsed);
            validate(parsed);
        } catch (NumberFormatException e) {
            validationResult = ValidationResult.warning("Invalid number: " + value);
            applyValidationStyle();
        }
    }

    @Override
    public void focus() {
        spinner.requestFocus();
        spinner.getEditor().selectAll();
    }

    @Override
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
