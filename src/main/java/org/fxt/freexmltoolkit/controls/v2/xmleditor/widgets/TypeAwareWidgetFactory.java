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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.control.TextField;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticStyle;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.SimpleTypeChecker;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.XsdType;

/**
 * Factory for creating type-aware editing widgets based on XSD type information.
 *
 * <p>This factory analyzes the XSD type and facets to create the most appropriate
 * editing widget for each value type:</p>
 * <ul>
 *   <li>Enumerations (also unions of enumerations) → ComboBox</li>
 *   <li>Boolean → Toggle button</li>
 *   <li>date / dateTime / time → pickers that keep timezone and fractional seconds</li>
 *   <li>integer family / decimal / float / double → Spinner in XSD lexical form</li>
 *   <li>Others → TextField with the format as hint</li>
 * </ul>
 *
 * <p>With a {@link SimpleTypeChecker} from the schema every widget flags values the declared type does not allow,
 * with the reason. Values are never blocked: the editor writes them and warns.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class TypeAwareWidgetFactory {

    private static final Logger logger = LogManager.getLogger(TypeAwareWidgetFactory.class);

    private final XmlSchemaProvider schemaProvider;

    /**
     * Creates a new TypeAwareWidgetFactory.
     *
     * @param schemaProvider the schema provider (can be null for no schema)
     */
    public TypeAwareWidgetFactory(XmlSchemaProvider schemaProvider) {
        this.schemaProvider = schemaProvider;
    }

    /**
     * Creates an appropriate editing widget for an element's text content.
     *
     * @param elementXPath  the XPath of the element
     * @param currentValue  the current value
     * @param onValueChange callback when value changes
     * @return the created widget
     */
    public EditWidget createElementWidget(String elementXPath, String currentValue,
                                          Consumer<String> onValueChange) {
        if (schemaProvider == null || !schemaProvider.hasSchema()) {
            return createDefaultTextField(currentValue, onValueChange, null);
        }

        var typeInfoOpt = schemaProvider.getElementTypeInfo(elementXPath);
        if (typeInfoOpt.isEmpty()) {
            return createDefaultTextField(currentValue, onValueChange, null);
        }

        var typeInfo = typeInfoOpt.get();
        return createWidgetForType(
                typeInfo.xsdType(),
                typeInfo.enumerationValues(),
                typeInfo.facets(),
                typeInfo.fixedValue(),
                typeInfo.defaultValue(),
                typeInfo.checker(),
                currentValue,
                onValueChange
        );
    }

    /**
     * Creates an appropriate editing widget for an attribute value.
     *
     * @param elementXPath  the XPath of the element
     * @param attributeName the attribute name
     * @param currentValue  the current value
     * @param onValueChange callback when value changes
     * @return the created widget
     */
    public EditWidget createAttributeWidget(String elementXPath, String attributeName,
                                            String currentValue, Consumer<String> onValueChange) {
        if (schemaProvider == null || !schemaProvider.hasSchema()) {
            return createDefaultTextField(currentValue, onValueChange, null);
        }

        var typeInfoOpt = schemaProvider.getAttributeTypeInfo(elementXPath, attributeName);
        if (typeInfoOpt.isEmpty()) {
            return createDefaultTextField(currentValue, onValueChange, null);
        }

        var typeInfo = typeInfoOpt.get();
        return createWidgetForType(
                typeInfo.xsdType(),
                typeInfo.enumerationValues(),
                typeInfo.facets(),
                typeInfo.fixedValue(),
                typeInfo.defaultValue(),
                typeInfo.checker(),
                currentValue,
                onValueChange
        );
    }

    /**
     * Creates a widget based on the XSD type and facets. With a checker every widget checks the value against the
     * exact declared type (all facets of all derivation steps); without one the widgets fall back to their own
     * checks.
     */
    private EditWidget createWidgetForType(XsdType xsdType,
                                           java.util.List<String> enumerationValues,
                                           Map<String, String> facets,
                                           String fixedValue,
                                           String defaultValue,
                                           SimpleTypeChecker checker,
                                           String currentValue,
                                           Consumer<String> onValueChange) {

        // If fixed value, create read-only widget
        if (fixedValue != null && !fixedValue.isEmpty()) {
            return createFixedValueWidget(fixedValue);
        }

        // Use default value if current is empty
        String effectiveValue = (currentValue == null || currentValue.isEmpty()) && defaultValue != null
                ? defaultValue
                : currentValue;
        Function<String, ValidationResult> check = checker != null ? checker::check : null;

        // Check for enumeration first (overrides type)
        if (enumerationValues != null && !enumerationValues.isEmpty()) {
            logger.debug("Creating enumeration widget with {} values", enumerationValues.size());
            return new EnumerationComboBox(enumerationValues, effectiveValue, onValueChange);
        }

        return switch (xsdType) {
            case BOOLEAN -> new BooleanToggle(effectiveValue, onValueChange);
            case DATE -> new XmlDatePicker(effectiveValue, onValueChange, check);
            case DATE_TIME -> new XmlDateTimePicker(effectiveValue, onValueChange, check);
            case TIME -> new XmlTimePicker(effectiveValue, onValueChange, check);
            case INTEGER -> new NumericSpinner(effectiveValue, onValueChange, facets, NumericSpinner.Kind.INTEGER, check);
            case DECIMAL -> new NumericSpinner(effectiveValue, onValueChange, facets, NumericSpinner.Kind.DECIMAL, check);
            case FLOAT, DOUBLE ->
                    new NumericSpinner(effectiveValue, onValueChange, facets, NumericSpinner.Kind.FLOATING, check);
            case ANY_URI -> new UriTextField(effectiveValue, onValueChange);
            case DURATION, G_YEAR, G_YEAR_MONTH, G_MONTH, G_MONTH_DAY, G_DAY, HEX_BINARY, BASE64_BINARY, QNAME ->
                    new ValidatedTextField(effectiveValue, onValueChange, check, formatHint(xsdType));
            default -> {
                if (check != null) {
                    // Pattern, lengths, whiteSpace and the lexical space of tokens/names/lists/unions at once
                    yield new ValidatedTextField(effectiveValue, onValueChange, check, null);
                }
                String pattern = facets != null ? facets.get("pattern") : null;
                if (pattern != null && !pattern.isEmpty() && !pattern.contains("\n")) {
                    yield new PatternTextField(effectiveValue, onValueChange, pattern);
                }
                Integer minLength = parseFacetInt(facets, "minLength");
                Integer maxLength = parseFacetInt(facets, "maxLength");
                Integer length = parseFacetInt(facets, "length");
                if (length != null || minLength != null || maxLength != null) {
                    yield new LengthValidatedField(effectiveValue, onValueChange, minLength, maxLength, length);
                }
                yield createDefaultTextField(effectiveValue, onValueChange, facets);
            }
        };
    }

    /** An example of the lexical form, shown while the field is empty. */
    static String formatHint(XsdType type) {
        return switch (type) {
            case DURATION -> "P1Y2M3DT4H5M6S";
            case G_YEAR -> "2024";
            case G_YEAR_MONTH -> "2024-05";
            case G_MONTH -> "--05";
            case G_MONTH_DAY -> "--12-24";
            case G_DAY -> "---15";
            case HEX_BINARY -> "0FB7";
            case BASE64_BINARY -> "SGVsbG8=";
            case QNAME -> "prefix:name";
            default -> null;
        };
    }

    /**
     * Creates a default text field widget.
     */
    private EditWidget createDefaultTextField(String currentValue, Consumer<String> onValueChange,
                                              Map<String, String> facets) {
        return new ValidatedTextField(currentValue, onValueChange, schemaProvider, facets);
    }

    /**
     * Creates a read-only widget for fixed values.
     */
    private EditWidget createFixedValueWidget(String fixedValue) {
        return new FixedValueWidget(fixedValue);
    }

    /**
     * Parses an integer facet value.
     */
    private Integer parseFacetInt(Map<String, String> facets, String facetName) {
        if (facets == null || !facets.containsKey(facetName)) {
            return null;
        }
        try {
            return Integer.parseInt(facets.get(facetName));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Interface for all edit widgets.
     */
    public interface EditWidget {
        /**
         * Gets the JavaFX node for this widget.
         *
         * @return The JavaFX node.
         */
        Node getNode();

        /**
         * Gets the current value.
         *
         * @return The current value.
         */
        String getValue();

        /**
         * Sets the value.
         *
         * @param value The value to set.
         */
        void setValue(String value);

        /**
         * Focuses the widget for editing.
         */
        void focus();

        /**
         * Gets the validation state.
         *
         * @return The validation result.
         */
        default ValidationResult getValidationResult() {
            return ValidationResult.valid();
        }

        /**
         * Checks if the widget is valid.
         *
         * @return True if valid, false otherwise.
         */
        default boolean isValid() {
            return getValidationResult().isValid();
        }
    }

    /**
     * Fixed value widget (read-only).
     */
    public static class FixedValueWidget implements EditWidget {
        private final TextField textField;
        private final String fixedValue;

        /**
         * Creates a new FixedValueWidget.
         *
         * @param fixedValue The fixed value.
         */
        public FixedValueWidget(String fixedValue) {
            this.fixedValue = fixedValue;
            this.textField = new TextField(fixedValue);
            this.textField.setEditable(false);
            SemanticStyle.style(textField, DesignTokens.ColorToken.TYPE_FRAME_BG, c -> "-fx-background-color: " + c + ";");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Node getNode() {
            return textField;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getValue() {
            return fixedValue;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setValue(String value) {
            // Fixed - cannot change
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void focus() {
            textField.requestFocus();
        }
    }
}
