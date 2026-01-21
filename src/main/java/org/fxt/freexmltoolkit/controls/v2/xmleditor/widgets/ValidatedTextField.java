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
import javafx.scene.control.Tooltip;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Default text field widget with optional schema-based validation.
 *
 * <p>Used when no more specific widget type is appropriate.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class ValidatedTextField implements TypeAwareWidgetFactory.EditWidget {

    private final TextField textField;
    private final Consumer<String> onValueChange;
    private final XmlSchemaProvider schemaProvider;
    private final Map<String, String> facets;
    private String elementXPath;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new ValidatedTextField.
     *
     * @param currentValue   the current value
     * @param onValueChange  callback when value changes
     * @param schemaProvider the schema provider for validation (can be null)
     * @param facets         the facets map (can be null)
     */
    public ValidatedTextField(String currentValue, Consumer<String> onValueChange,
                              XmlSchemaProvider schemaProvider, Map<String, String> facets) {
        this.onValueChange = onValueChange;
        this.schemaProvider = schemaProvider;
        this.facets = facets;

        this.textField = new TextField(currentValue != null ? currentValue : "");

        // Listen for changes
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            validate(newVal);
            if (onValueChange != null) {
                onValueChange.accept(newVal);
            }
        });

        // Validate on focus lost
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                validate(textField.getText());
            }
        });

        // Apply initial style
        textField.setStyle("-fx-background-radius: 3; -fx-border-radius: 3; " +
                "-fx-border-color: #ced4da; -fx-border-width: 1px;");
    }

    /**
     * Sets the element XPath for validation context.
     *
     * @param elementXPath the XPath to the element being edited
     */
    public void setElementXPath(String elementXPath) {
        this.elementXPath = elementXPath;
    }

    private void validate(String value) {
        // If we have a schema provider and element path, use it for validation
        if (schemaProvider != null && elementXPath != null) {
            validationResult = schemaProvider.validateElementValue(elementXPath, value);
        } else if (facets != null && !facets.isEmpty()) {
            // Manual facet validation
            validationResult = validateWithFacets(value);
        } else {
            validationResult = ValidationResult.valid();
        }

        applyValidationStyle();
    }

    private ValidationResult validateWithFacets(String value) {
        if (value == null) {
            value = "";
        }

        // Check whiteSpace facet
        String whiteSpace = facets.get("whiteSpace");
        if ("collapse".equals(whiteSpace)) {
            value = value.trim().replaceAll("\\s+", " ");
        } else if ("replace".equals(whiteSpace)) {
            value = value.replaceAll("[\\t\\n\\r]", " ");
        }

        return ValidationResult.valid();
    }

    private void applyValidationStyle() {
        String baseStyle = "-fx-background-radius: 3; -fx-border-radius: 3;";

        if (!validationResult.isValid()) {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #dc3545; -fx-border-width: 2px; -fx-background-color: #ffe6e6;");
            Tooltip.install(textField, new Tooltip(validationResult.errorMessage()));
        } else if (validationResult.severity() == ValidationSeverity.WARNING) {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #ffc107; -fx-border-width: 2px; -fx-background-color: #fff8e1;");
            Tooltip.install(textField, new Tooltip(validationResult.errorMessage()));
        } else {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #ced4da; -fx-border-width: 1px;");
            textField.setTooltip(null);
        }
    }

    @Override
    public Node getNode() {
        return textField;
    }

    @Override
    public String getValue() {
        return textField.getText();
    }

    @Override
    public void setValue(String value) {
        textField.setText(value);
        validate(value);
    }

    @Override
    public void focus() {
        textField.requestFocus();
        textField.selectAll();
    }

    @Override
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
