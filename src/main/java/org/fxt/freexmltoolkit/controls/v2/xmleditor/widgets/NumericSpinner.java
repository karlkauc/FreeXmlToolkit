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

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import javafx.scene.Node;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Spinner for the XSD numeric types (the integer family, decimal, float, double).
 *
 * <p>The text is the value: it is written in XSD's lexical form, never rounded, clamped or formatted with the
 * user's locale. A comma typed as decimal separator becomes a point ({@code 3,5} → {@code 3.5}); float and double
 * also take exponents, {@code INF}, {@code -INF} and {@code NaN}; an empty value stays empty. The arrows step by 1,
 * or by the smallest fraction {@code fractionDigits} allows, with exact decimal arithmetic and within the inclusive
 * and exclusive bounds. Invalid values are flagged with the reason, not blocked.</p>
 */
public class NumericSpinner implements TypeAwareWidgetFactory.EditWidget {

    /** The number family. */
    public enum Kind {
        /** xs:integer and its derivations: digits only. */
        INTEGER,
        /** xs:decimal: digits with an optional fraction. */
        DECIMAL,
        /** xs:float and xs:double: also exponents and the special values. */
        FLOATING
    }

    private static final Pattern INTEGER_CHARS = Pattern.compile("[+-]?\\d*");
    private static final Pattern DECIMAL_CHARS = Pattern.compile("[+-]?[\\d.,]*");
    private static final Pattern FLOATING_CHARS = Pattern.compile("[+-]?[\\d.,eE+\\-INFa]*");

    private final Spinner<String> spinner;
    private final Kind kind;
    private final Map<String, String> facets;
    private final Function<String, ValidationResult> validator;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new NumericSpinner.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     * @param facets        the facets (bounds, total/fractionDigits; may be {@code null})
     * @param integerOnly   {@code true} for the integer family, {@code false} for decimal
     */
    public NumericSpinner(String currentValue, Consumer<String> onValueChange,
                          Map<String, String> facets, boolean integerOnly) {
        this(currentValue, onValueChange, facets, integerOnly ? Kind.INTEGER : Kind.DECIMAL, null);
    }

    /**
     * Creates a new NumericSpinner.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     * @param facets        the facets (bounds, total/fractionDigits; may be {@code null})
     * @param kind          the number family
     * @param validator     the exact check of the declared type; {@code null} = lexical form and bounds only
     */
    public NumericSpinner(String currentValue, Consumer<String> onValueChange, Map<String, String> facets,
                          Kind kind, Function<String, ValidationResult> validator) {
        this.kind = kind;
        this.facets = facets == null ? Map.of() : Map.copyOf(facets);
        this.validator = validator;

        spinner = new Spinner<>();
        LexicalValueFactory factory = new LexicalValueFactory();
        factory.setValue(currentValue == null ? "" : currentValue);
        spinner.setValueFactory(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(140);

        TextField editor = spinner.getEditor();
        editor.setText(factory.getValue());
        Pattern allowed = switch (kind) {
            case INTEGER -> INTEGER_CHARS;
            case DECIMAL -> DECIMAL_CHARS;
            case FLOATING -> FLOATING_CHARS;
        };
        // Typing is limited to the family's characters; replacing the whole text (the current value, a paste over
        // everything) is not, so an unusual existing value is shown and kept rather than dropped
        editor.setTextFormatter(new TextFormatter<String>(change -> {
            boolean wholeText = change.getRangeStart() == 0 && change.getRangeEnd() == change.getControlText().length();
            return wholeText || allowed.matcher(change.getControlNewText().strip()).matches() ? change : null;
        }));
        editor.textProperty().addListener((obs, oldVal, newVal) -> {
            validate();
            if (onValueChange != null) {
                onValueChange.accept(getValue());
            }
        });
        validate();
    }

    /**
     * The value in XSD form: the editor's text (typed but not yet committed text counts), trimmed, with a comma
     * decimal separator turned into a point.
     */
    @Override
    public String getValue() {
        return normalize(spinner.getEditor().getText());
    }

    @Override
    public void setValue(String value) {
        spinner.getValueFactory().setValue(value == null ? "" : value);
        spinner.getEditor().setText(spinner.getValueFactory().getValue());
    }

    @Override
    public Node getNode() {
        return spinner;
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

    /** The number family of this spinner. */
    public Kind getKind() {
        return kind;
    }

    /**
     * Brings typed text into XSD form: trimmed; a single comma without a point is the decimal separator.
     *
     * @param text the typed text
     * @return the lexical value ({@code ""} for no value)
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String value = text.strip();
        if (value.indexOf(',') >= 0 && value.indexOf('.') < 0 && value.indexOf(',') == value.lastIndexOf(',')) {
            value = value.replace(',', '.');
        }
        return value;
    }

    private void validate() {
        String value = getValue();
        if (value.isEmpty()) {
            validationResult = ValidationResult.valid();
        } else if (validator != null) {
            validationResult = validator.apply(value);
        } else {
            validationResult = basicCheck(value);
        }
        WidgetFeedback.show(spinner, validationResult);
    }

    /** Lexical form and bounds, for spinners without the schema's exact check. */
    private ValidationResult basicCheck(String value) {
        BigDecimal number = parse(value);
        if (number == null) {
            boolean special = kind == Kind.FLOATING && (value.equals("INF") || value.equals("-INF")
                    || value.equals("+INF") || value.equals("NaN"));
            return special ? ValidationResult.valid()
                    : ValidationResult.error(I18n.t("NumericSpinner.invalid-number", value));
        }
        if (kind == Kind.INTEGER && number.stripTrailingZeros().scale() > 0) {
            return ValidationResult.error(I18n.t("NumericSpinner.invalid-number", value));
        }
        BigDecimal min = bound("minInclusive");
        BigDecimal max = bound("maxInclusive");
        BigDecimal minEx = bound("minExclusive");
        BigDecimal maxEx = bound("maxExclusive");
        if (min != null && number.compareTo(min) < 0 || minEx != null && number.compareTo(minEx) <= 0
                || max != null && number.compareTo(max) > 0 || maxEx != null && number.compareTo(maxEx) >= 0) {
            return ValidationResult.error(I18n.t("NumericSpinner.out-of-range", value));
        }
        return ValidationResult.valid();
    }

    private BigDecimal parse(String value) {
        if (kind != Kind.FLOATING && (value.indexOf('e') >= 0 || value.indexOf('E') >= 0)) {
            return null;
        }
        try {
            return new BigDecimal(value.startsWith("+") ? value.substring(1) : value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal bound(String facet) {
        String text = facets.get(facet);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The step of the arrows: 1, or 10^-fractionDigits for decimals with a fraction limit. */
    private BigDecimal step() {
        if (kind == Kind.DECIMAL) {
            try {
                int fd = Integer.parseInt(facets.getOrDefault("fractionDigits", "0").strip());
                if (fd > 0) {
                    return BigDecimal.ONE.movePointLeft(fd);
                }
            } catch (NumberFormatException e) {
                // step 1
            }
        }
        return BigDecimal.ONE;
    }

    /** Values are lexical strings; the arrows compute exactly and stay inside the bounds. */
    private final class LexicalValueFactory extends SpinnerValueFactory<String> {

        LexicalValueFactory() {
            setConverter(new StringConverter<>() {
                @Override
                public String toString(String value) {
                    return value == null ? "" : value;
                }

                @Override
                public String fromString(String text) {
                    return normalize(text);
                }
            });
        }

        @Override
        public void decrement(int steps) {
            stepBy(-steps);
        }

        @Override
        public void increment(int steps) {
            stepBy(steps);
        }

        private void stepBy(int steps) {
            String text = getValue();
            BigDecimal current = text == null || text.isEmpty() ? null : parse(normalize(text));
            if (current == null && text != null && !text.isEmpty()) {
                return; // not a number: leave the text for the user to correct
            }
            BigDecimal next = current == null ? startValue() : current.add(step().multiply(BigDecimal.valueOf(steps)));
            BigDecimal min = bound("minInclusive");
            BigDecimal max = bound("maxInclusive");
            BigDecimal minEx = bound("minExclusive");
            BigDecimal maxEx = bound("maxExclusive");
            if (min != null && next.compareTo(min) < 0 || minEx != null && next.compareTo(minEx) <= 0
                    || max != null && next.compareTo(max) > 0 || maxEx != null && next.compareTo(maxEx) >= 0) {
                return; // the next step would leave the allowed range
            }
            setValue(next.toPlainString());
            spinner.getEditor().setText(getValue());
        }

        /** The first value of an empty spinner: 0 when allowed, else the nearest bound. */
        private BigDecimal startValue() {
            BigDecimal min = bound("minInclusive");
            BigDecimal max = bound("maxInclusive");
            if (min != null && min.signum() > 0) {
                return min;
            }
            if (max != null && max.signum() < 0) {
                return max;
            }
            BigDecimal minEx = bound("minExclusive");
            if (minEx != null && minEx.signum() >= 0) {
                return minEx.add(step());
            }
            return BigDecimal.ZERO;
        }
    }
}
