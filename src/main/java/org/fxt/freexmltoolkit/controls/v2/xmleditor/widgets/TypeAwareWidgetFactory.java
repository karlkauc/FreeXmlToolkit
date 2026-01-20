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
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.XsdType;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Factory for creating type-aware editing widgets based on XSD type information.
 *
 * <p>This factory analyzes the XSD type and facets to create the most appropriate
 * editing widget for each value type:</p>
 * <ul>
 *   <li>Enumerations → ComboBox</li>
 *   <li>Boolean → Toggle button</li>
 *   <li>Date/DateTime → DatePicker</li>
 *   <li>Integer/Decimal → Spinner</li>
 *   <li>Pattern-constrained → TextField with validation</li>
 *   <li>Others → TextField with length validation</li>
 * </ul>
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
                currentValue,
                onValueChange
        );
    }

    /**
     * Creates a widget based on the XSD type and facets.
     */
    private EditWidget createWidgetForType(XsdType xsdType,
                                           java.util.List<String> enumerationValues,
                                           Map<String, String> facets,
                                           String fixedValue,
                                           String defaultValue,
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

        // Check for enumeration first (overrides type)
        if (enumerationValues != null && !enumerationValues.isEmpty()) {
            logger.debug("Creating enumeration widget with {} values", enumerationValues.size());
            return new EnumerationComboBox(enumerationValues, effectiveValue, onValueChange);
        }

        // Create widget based on type
        return switch (xsdType) {
            case BOOLEAN -> {
                logger.debug("Creating boolean toggle widget");
                yield new BooleanToggle(effectiveValue, onValueChange);
            }
            case DATE -> {
                logger.debug("Creating date picker widget");
                yield new XmlDatePicker(effectiveValue, onValueChange, false);
            }
            case DATE_TIME -> {
                logger.debug("Creating datetime picker widget");
                yield new XmlDateTimePicker(effectiveValue, onValueChange);
            }
            case TIME -> {
                logger.debug("Creating time picker widget");
                yield new XmlTimePicker(effectiveValue, onValueChange);
            }
            case INTEGER -> {
                logger.debug("Creating integer spinner widget");
                yield new NumericSpinner(effectiveValue, onValueChange, facets, true);
            }
            case DECIMAL, FLOAT, DOUBLE -> {
                logger.debug("Creating decimal spinner widget");
                yield new NumericSpinner(effectiveValue, onValueChange, facets, false);
            }
            case ANY_URI -> {
                logger.debug("Creating URI widget");
                yield new UriTextField(effectiveValue, onValueChange);
            }
            default -> {
                // Check for pattern facet
                String pattern = facets != null ? facets.get("pattern") : null;
                if (pattern != null && !pattern.isEmpty()) {
                    logger.debug("Creating pattern-validated text field");
                    yield new PatternTextField(effectiveValue, onValueChange, pattern);
                }

                // Check for length constraints
                Integer minLength = parseFacetInt(facets, "minLength");
                Integer maxLength = parseFacetInt(facets, "maxLength");
                Integer length = parseFacetInt(facets, "length");

                if (length != null || minLength != null || maxLength != null) {
                    logger.debug("Creating length-validated text field");
                    yield new LengthValidatedField(effectiveValue, onValueChange, minLength, maxLength, length);
                }

                // Default text field
                yield createDefaultTextField(effectiveValue, onValueChange, facets);
            }
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
            this.textField.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
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
